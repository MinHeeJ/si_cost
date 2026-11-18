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
import { Brand } from "../../shared/Brand";
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
      <main className="authentication-page" data-testid="authentication-page">
        <aside className="lhero" aria-hidden="true">
          <div className="lh-mesh" />
          <div className="lh-holo" />
          <div className="lh-grain" />
          <div className="lh-top">
            <Brand size={34} wordmark={false} />
            <span>원가관리시스템</span>
          </div>
          <div className="lh-art">
            <div className="lh-card">
              <div className="lh-card-h">
                <span>
                  <i style={{ background: "#93C5FD" }} />
                  매출
                </span>
                <span>
                  <i style={{ background: "#5EEAD4" }} />
                  원가
                </span>
                <span>
                  <i style={{ background: "#FFC600" }} />
                  이익
                </span>
              </div>
              <svg viewBox="0 0 360 170" className="lh-svg">
                <defs>
                  <linearGradient
                    id="authentication-hero-area"
                    x1="0"
                    y1="0"
                    x2="0"
                    y2="1"
                  >
                    <stop offset="0" stopColor="#FFC600" stopOpacity=".35" />
                    <stop offset="1" stopColor="#FFC600" stopOpacity="0" />
                  </linearGradient>
                </defs>
                <path
                  d="M0 42H360M0 84H360M0 126H360"
                  stroke="rgba(255,255,255,.10)"
                />
                <rect
                  x="10"
                  y="130"
                  width="11"
                  height="38"
                  rx="3"
                  fill="rgba(147,197,253,0.28)"
                  style={{ animationDelay: "200ms" }}
                />
                <rect
                  x="22"
                  y="144.44"
                  width="7"
                  height="23.56"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "220ms" }}
                />
                <rect
                  x="39"
                  y="116"
                  width="11"
                  height="52"
                  rx="3"
                  fill="rgba(147,197,253,0.31)"
                  style={{ animationDelay: "245ms" }}
                />
                <rect
                  x="51"
                  y="135.76"
                  width="7"
                  height="32.24"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "265ms" }}
                />
                <rect
                  x="68"
                  y="122"
                  width="11"
                  height="46"
                  rx="3"
                  fill="rgba(147,197,253,0.34)"
                  style={{ animationDelay: "290ms" }}
                />
                <rect
                  x="80"
                  y="139.48"
                  width="7"
                  height="28.52"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "310ms" }}
                />
                <rect
                  x="97"
                  y="104"
                  width="11"
                  height="64"
                  rx="3"
                  fill="rgba(147,197,253,0.37)"
                  style={{ animationDelay: "335ms" }}
                />
                <rect
                  x="109"
                  y="128.32"
                  width="7"
                  height="39.68"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "355ms" }}
                />
                <rect
                  x="126"
                  y="110"
                  width="11"
                  height="58"
                  rx="3"
                  fill="rgba(147,197,253,0.40)"
                  style={{ animationDelay: "380ms" }}
                />
                <rect
                  x="138"
                  y="132.04"
                  width="7"
                  height="35.96"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "400ms" }}
                />
                <rect
                  x="155"
                  y="92"
                  width="11"
                  height="76"
                  rx="3"
                  fill="rgba(147,197,253,0.43)"
                  style={{ animationDelay: "425ms" }}
                />
                <rect
                  x="167"
                  y="120.88"
                  width="7"
                  height="47.12"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "445ms" }}
                />
                <rect
                  x="184"
                  y="96"
                  width="11"
                  height="72"
                  rx="3"
                  fill="rgba(147,197,253,0.46)"
                  style={{ animationDelay: "470ms" }}
                />
                <rect
                  x="196"
                  y="123.36"
                  width="7"
                  height="44.64"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "490ms" }}
                />
                <rect
                  x="213"
                  y="76"
                  width="11"
                  height="92"
                  rx="3"
                  fill="rgba(147,197,253,0.49)"
                  style={{ animationDelay: "515ms" }}
                />
                <rect
                  x="225"
                  y="110.96"
                  width="7"
                  height="57.04"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "535ms" }}
                />
                <rect
                  x="242"
                  y="80"
                  width="11"
                  height="88"
                  rx="3"
                  fill="rgba(147,197,253,0.52)"
                  style={{ animationDelay: "560ms" }}
                />
                <rect
                  x="254"
                  y="113.44"
                  width="7"
                  height="54.56"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "580ms" }}
                />
                <rect
                  x="271"
                  y="60"
                  width="11"
                  height="108"
                  rx="3"
                  fill="rgba(147,197,253,0.55)"
                  style={{ animationDelay: "605ms" }}
                />
                <rect
                  x="283"
                  y="101.04"
                  width="7"
                  height="66.96"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "625ms" }}
                />
                <rect
                  x="300"
                  y="64"
                  width="11"
                  height="104"
                  rx="3"
                  fill="rgba(147,197,253,0.58)"
                  style={{ animationDelay: "650ms" }}
                />
                <rect
                  x="312"
                  y="103.52"
                  width="7"
                  height="64.48"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "670ms" }}
                />
                <rect
                  x="329"
                  y="44"
                  width="11"
                  height="124"
                  rx="3"
                  fill="rgba(147,197,253,0.61)"
                  style={{ animationDelay: "695ms" }}
                />
                <rect
                  x="341"
                  y="91.12"
                  width="7"
                  height="76.88"
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ animationDelay: "715ms" }}
                />
                <path
                  d="M14 128 C60 120 80 112 110 104 S170 92 200 76 S270 52 346 26 L346 168 L14 168Z"
                  fill="url(#authentication-hero-area)"
                  className="lh-area"
                />
                <path
                  d="M14 128 C60 120 80 112 110 104 S170 92 200 76 S270 52 346 26"
                  fill="none"
                  stroke="#FFC600"
                  strokeWidth="2.4"
                  strokeLinecap="round"
                  className="lh-line"
                />
                <circle
                  cx="346"
                  cy="26"
                  r="5"
                  fill="#FFC600"
                  className="lh-dot"
                />
                <circle
                  cx="346"
                  cy="26"
                  r="11"
                  fill="none"
                  stroke="#FFC600"
                  strokeOpacity=".45"
                  className="lh-ring"
                />
              </svg>
            </div>
            <div className="lh-chip lh-c1">
              <b>마감</b>
              <span>월별 확정 현황</span>
            </div>
            <div className="lh-chip lh-c2">
              <b>배분</b>
              <span>투입 MM 기준</span>
            </div>
          </div>
          <div className="lh-copy">
            <h2>
              프로젝트 손익을
              <br />한 장부에서.
            </h2>
            <p>투입 · 원가 · 매출 · 마감을 하나의 흐름으로 관리합니다.</p>
          </div>
        </aside>
        <section
          className="authentication-card"
          data-testid="authentication-card"
        >
          <div className="authentication-brand">
            <Brand size={36} />
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
      <div key={version} data-testid="authentication-content">
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
            data-testid="authentication-password-recommendation-change"
            disabled={recommendPending}
            onClick={() => void chooseRecommendation(true)}
          >
            변경
          </button>
          <button
            data-testid="authentication-password-recommendation-later"
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
