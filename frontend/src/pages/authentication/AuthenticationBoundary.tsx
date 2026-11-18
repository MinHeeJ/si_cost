import { useEffect, useRef, useState, type ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { ErrorBanner } from "../../shared/Feedback";
import { PermissionContext } from "../../shared/permission/PermissionBoundary";
import { LoginForm } from "./LoginForm";
import { MfaForm } from "./MfaForm";
import { RequiredPasswordForm } from "./RequiredPasswordForm";
import { PasswordRecoveryForm } from "./PasswordRecoveryForm";
import { PasswordChangeDialog } from "./PasswordChangeDialog";
import { SessionIndicator } from "./SessionIndicator";
import {
  session,
  capabilities,
  authenticate,
  authenticationChannel,
  clearAuthentication,
  type AuthenticationSession,
  type Capabilities,
} from "./authenticationApi";
import "./authentication.css";
export function AuthenticationBoundary({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthenticationSession>(),
    [permissions, setPermissions] = useState<Capabilities>(),
    [error, setError] = useState(""),
    [recovery, setRecovery] = useState(false),
    [version, setVersion] = useState(0),
    [recommend, setRecommend] = useState(false),
    [change, setChange] = useState(false);
  const generation = useRef(0),
    navigate = useNavigate(),
    location = useLocation();
  const currentPermissions = useRef<Capabilities>();
  const refreshing = useRef<Promise<void>>();
  const [recommendPending, setRecommendPending] = useState(false);
  async function chooseRecommendation(changePassword: boolean) {
    if (recommendPending) return;
    setRecommendPending(true);
    try {
      await authenticate("password/recommendation-dismissal");
      setRecommend(false);
      setChange(changePassword);
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "변경 안내 선택을 저장하지 못했습니다.",
      );
    } finally {
      setRecommendPending(false);
    }
  }
  const [mobile, setMobile] = useState(
    () => window.matchMedia?.("(max-width: 767px)")?.matches ?? false,
  );
  const usableScreens = permissions
    ? Object.keys(permissions.screens).filter(
        (id) => !mobile || !permissions.pcOnlyScreens?.includes(id),
      )
    : [];
  useEffect(() => {
    const media = window.matchMedia?.("(max-width: 767px)");
    const update = () => setMobile(media?.matches ?? false);
    media?.addEventListener("change", update);
    return () => media?.removeEventListener("change", update);
  }, []);
  async function apply(value: AuthenticationSession) {
    const token = ++generation.current;
    setState(value);
    setPermissions(undefined);
    setRecovery(false);
    if (value.nextStep === "AUTHENTICATED") {
      try {
        const result = await capabilities();
        if (token !== generation.current) return;
        currentPermissions.current = result;
        setPermissions(result);
        setVersion((v) => v + 1);
        setRecommend(value.passwordChangeRecommended === true);
      } catch (e) {
        if (token === generation.current)
          setError(
            e instanceof Error ? e.message : "권한을 확인하지 못했습니다.",
          );
      }
    } else navigate("/UI-22", { replace: true, state: null });
  }
  useEffect(() => {
    let live = true;
    void session()
      .then((value) => {
        if (live) void apply(value);
      })
      .catch((e) => {
        if (live) {
          setState({ nextStep: "LOGIN", serverTime: new Date().toISOString() });
          setError(
            e instanceof Error ? e.message : "세션을 확인하지 못했습니다.",
          );
        }
      });
    const clear = () => {
      generation.current++;
      currentPermissions.current = undefined;
      setPermissions(undefined);
      setState({ nextStep: "LOGIN", serverTime: new Date().toISOString() });
      setRecommend(false);
      setChange(false);
      setRecovery(false);
      navigate("/UI-22", { replace: true, state: null });
    };
    const required = (event: Event) => {
      clearAuthentication();
      const failure = (event as CustomEvent).detail;
      if (failure?.message) setError(failure.message);
      if (failure?.code === "AUTH_INCOMPLETE")
        void session()
          .then(apply)
          .catch(() => {});
    };
    const refresh = () => {
      if (refreshing.current) return;
      const token = ++generation.current,
        previous = currentPermissions.current;
      const pending = capabilities()
        .then((result) => {
          if (token !== generation.current) return;
          currentPermissions.current = result;
          // Unchanged grants are a denied operation, not a reason to replay every
          // mount-time GET in the otherwise permitted screen.
          if (JSON.stringify(previous) !== JSON.stringify(result)) {
            setPermissions(result);
            setChange(false);
            setRecommend(false);
            setVersion((v) => v + 1);
          }
        })
        .catch((e) => {
          if (token === generation.current)
            setError(
              e instanceof Error ? e.message : "권한을 확인하지 못했습니다.",
            );
        })
        .finally(() => {
          if (refreshing.current === pending) refreshing.current = undefined;
        });
      refreshing.current = pending;
    };
    const shared = (event: MessageEvent) => {
      if (event.data?.type === "clear") clearAuthentication(false);
    };
    window.addEventListener("sicost:authentication-required", required);
    window.addEventListener("sicost:authentication-cleared", clear);
    window.addEventListener("sicost:permission-changed", refresh);
    authenticationChannel?.addEventListener("message", shared);
    return () => {
      live = false;
      generation.current++;
      window.removeEventListener("sicost:authentication-required", required);
      window.removeEventListener("sicost:authentication-cleared", clear);
      window.removeEventListener("sicost:permission-changed", refresh);
      authenticationChannel?.removeEventListener("message", shared);
    };
  }, []);
  useEffect(() => {
    if (!permissions) return;
    const screens = usableScreens,
      first = screens.includes("UI-16") ? "UI-16" : screens[0];
    const active =
      location.hash.match(/^#(UI-\d{2})$/)?.[1] ?? location.pathname.slice(1);
    if (first && !screens.includes(active)) {
      if (active && active !== "UI-22")
        setError("이 화면을 볼 권한이 없습니다.");
      navigate(`/${first}`, { replace: true, state: null });
    } else if (location.hash && screens.includes(active)) {
      navigate(`/${active}`, { replace: true, state: null });
    }
  }, [permissions, location.pathname, location.hash, mobile]);
  if (!state) return <p role="status">인증 정보를 확인하는 중입니다.</p>;
  if (state.nextStep !== "AUTHENTICATED")
    return (
      <main data-testid="authentication-page" className="authentication-page">
        <section className="authentication-card">
          <div className="authentication-brand">
            SI COST<span>프로젝트 원가관리</span>
          </div>
          {error && <ErrorBanner message={error} />}
          {recovery ? (
            <PasswordRecoveryForm onClose={() => setRecovery(false)} />
          ) : state.nextStep === "MFA" ? (
            <MfaForm
              state={state}
              onAuthenticated={(value) => void apply(value)}
            />
          ) : state.nextStep === "CHANGE_PASSWORD" ? (
            <RequiredPasswordForm
              onAuthenticated={(value) => void apply(value)}
            />
          ) : (
            <LoginForm
              onAuthenticated={(value, notice) => {
                setError(notice ?? "");
                void apply(value);
              }}
              onRecovery={() => setRecovery(true)}
            />
          )}
        </section>
      </main>
    );
  if (!permissions)
    return (
      <main data-testid="authentication-permissions-loading">
        {error ? (
          <ErrorBanner message={error} />
        ) : (
          <p role="status">현재 권한을 확인하는 중입니다.</p>
        )}
      </main>
    );
  if (!Object.keys(permissions.screens).length)
    return (
      <main data-testid="authentication-no-permissions">
        <p>볼 수 있는 화면이 없습니다. 관리자에게 권한을 요청해 주세요.</p>
        <SessionIndicator logoutOnly />
      </main>
    );
  if (!usableScreens.length)
    return (
      <main data-testid="authentication-pc-required">
        <p>이 화면은 PC에서 이용해 주세요.</p>
        <SessionIndicator logoutOnly />
      </main>
    );
  if (!usableScreens.includes(location.pathname.slice(1)))
    return <p role="status">허용된 화면으로 이동하는 중입니다.</p>;
  return (
    <PermissionContext.Provider value={permissions}>
      {error && <ErrorBanner message={error} />}
      <div data-testid="authentication-content" key={version}>
        {children}
      </div>
      {recommend && (
        <div
          data-testid="authentication-password-recommendation-dialog"
          role="dialog"
          aria-label="비밀번호 변경 권장"
          className="authentication-recommend"
        >
          <p>
            {state.passwordChangedAt
              ? "비밀번호를 변경한 지90일이 지났습니다"
              : "변경일 정보 없음. 비밀번호 변경을 권장합니다."}
          </p>
          <button
            data-testid="authentication-password-recommendation-change-button"
            disabled={recommendPending}
            onClick={() => void chooseRecommendation(true)}
          >
            변경
          </button>
          <button
            data-testid="authentication-password-recommendation-later-button"
            disabled={recommendPending}
            onClick={() => void chooseRecommendation(false)}
          >
            나중에
          </button>
        </div>
      )}
      {change && (
        <PasswordChangeDialog
          changedAt={state.passwordChangedAt}
          onClose={() => setChange(false)}
        />
      )}
    </PermissionContext.Provider>
  );
}
