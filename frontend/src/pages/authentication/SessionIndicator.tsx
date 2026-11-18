import { useEffect, useState } from "react";
import {
  authenticate,
  session,
  authenticationChannel,
  clearAuthentication,
  type AuthenticationSession,
} from "./authenticationApi";
import { PasswordChangeDialog } from "./PasswordChangeDialog";
import { ErrorBanner } from "../../shared/Feedback";
export function SessionIndicator({
  logoutOnly = false,
}: {
  logoutOnly?: boolean;
}) {
  const [state, setState] = useState<AuthenticationSession>(),
    [received, setReceived] = useState(performance.now()),
    [now, setNow] = useState(performance.now());
  const [change, setChange] = useState(false),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  function apply(value: AuthenticationSession) {
    const at = performance.now();
    setState(value);
    setReceived(at);
    setNow(at);
  }
  useEffect(() => {
    let live = true;
    const load = () => {
      void session()
        .then((value) => {
          if (live) apply(value);
        })
        .catch(() => {});
    };
    const timing = (value: { serverTime?: string; expiresAt?: string }) => {
      if (
        !live ||
        !value.serverTime ||
        !value.expiresAt ||
        !Number.isFinite(Date.parse(value.serverTime)) ||
        !Number.isFinite(Date.parse(value.expiresAt))
      )
        return;
      const at = performance.now();
      setState((previous) =>
        previous
          ? {
              ...previous,
              serverTime: value.serverTime!,
              expiresAt: value.expiresAt!,
            }
          : previous,
      );
      setReceived(at);
      setNow(at);
    };
    const header = (event: Event) => {
      const value = (
        event as CustomEvent<{ serverTime: string; expiresAt: string }>
      ).detail;
      timing(value);
      authenticationChannel?.postMessage({ type: "timing", ...value });
    };
    const shared = (event: MessageEvent) => {
      if (event.data?.type === "extended") load();
      else if (event.data?.type === "timing") timing(event.data);
    };
    load();
    const poll = setInterval(load, 30000),
      tick = setInterval(() => setNow(performance.now()), 1000);
    window.addEventListener("sicost:session-timing", header);
    authenticationChannel?.addEventListener("message", shared);
    return () => {
      live = false;
      clearInterval(poll);
      clearInterval(tick);
      window.removeEventListener("sicost:session-timing", header);
      authenticationChannel?.removeEventListener("message", shared);
    };
  }, []);
  const remaining = state?.expiresAt
    ? Math.max(
        0,
        Math.ceil(
          (Date.parse(state.expiresAt) -
            Date.parse(state.serverTime) -
            (now - received)) /
            1000,
        ),
      )
    : undefined;
  useEffect(() => {
    if (remaining !== 0) return;
    let live = true;
    // Another tab may have performed a successful business action after our last poll.
    // Only the server, not a stale browser countdown, decides that the session expired.
    void session()
      .then((value) => {
        if (live) apply(value);
      })
      .catch((e) => {
        if (live)
          setError(
            e instanceof Error ? e.message : "세션 상태를 확인하지 못했습니다.",
          );
      });
    return () => {
      live = false;
    };
  }, [remaining]);
  return (
    <div
      className="authentication-session"
      data-testid="authentication-session"
    >
      {error && <ErrorBanner message={error} />}
      {!logoutOnly && (
        <>
          <span>
            자동 로그아웃까지{" "}
            {remaining === undefined
              ? "–"
              : `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, "0")}`}
          </span>
          <button
            data-testid="authentication-session-extend"
            type="button"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setError("");
              try {
                apply(await authenticate("session/extend"));
                authenticationChannel?.postMessage({ type: "extended" });
              } catch (e) {
                setError(
                  e instanceof Error ? e.message : "연장하지 못했습니다.",
                );
              } finally {
                setBusy(false);
              }
            }}
          >
            연장
          </button>
          <button
            data-testid="authentication-session-password-change"
            type="button"
            onClick={() => setChange(true)}
          >
            비밀번호 변경
          </button>
        </>
      )}
      <button
        data-testid="authentication-session-logout"
        type="button"
        disabled={busy}
        onClick={async () => {
          if (
            !window.confirm("미저장 내용은 사라집니다. 로그아웃하시겠습니까?")
          )
            return;
          setBusy(true);
          try {
            await authenticate("logout");
            clearAuthentication();
          } catch (e) {
            setError(
              e instanceof Error
                ? e.message
                : "로그아웃 응답을 확인하지 못했습니다.",
            );
            // Audit can fail after the server has already discarded the session. Read
            // back its state; never retry the POST or leave old private DOM in that case.
            try {
              if ((await session()).nextStep === "LOGIN") clearAuthentication();
            } catch {
              /* A terminal 401 clears all consumers through the common transport. */
            }
          } finally {
            setBusy(false);
          }
        }}
      >
        로그아웃
      </button>
      {change && !logoutOnly && (
        <PasswordChangeDialog
          changedAt={state?.passwordChangedAt}
          onClose={() => setChange(false)}
        />
      )}
    </div>
  );
}
