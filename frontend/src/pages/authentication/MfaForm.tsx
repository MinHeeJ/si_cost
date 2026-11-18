import { useEffect, useRef, useState } from "react";
import {
  authenticate,
  session,
  type AuthenticationSession,
} from "./authenticationApi";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
export function MfaForm({
  state,
  onAuthenticated,
}: {
  state: AuthenticationSession;
  onAuthenticated(value: AuthenticationSession): void;
}) {
  const [code, setCode] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [now, setNow] = useState(Date.now());
  const [receivedAt, setReceivedAt] = useState(Date.now());
  const [current, setCurrent] = useState(state);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const seconds = Math.max(
    0,
    Math.ceil(
      (Date.parse(current.stageExpiresAt ?? current.serverTime) -
        Date.parse(current.serverTime) -
        (now - receivedAt)) /
        1000,
    ),
  );
  const inFlight = useRef(false);
  async function act(path: string) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError("");
    try {
      onAuthenticated(
        await authenticate(path, path === "mfa/verify" ? { code } : undefined),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "인증 요청에 실패했습니다.");
      if (
        e instanceof ApiError &&
        (path === "mfa/resend" || e.body.code === "MFA_EXPIRED")
      ) {
        try {
          const latest = await session();
          if (latest.nextStep === "MFA") {
            const received = Date.now();
            setCurrent(latest);
            setReceivedAt(received);
            setNow(received);
          } else onAuthenticated(latest);
        } catch {
          /* A terminal 401 is handled by the common boundary. */
        }
      }
    } finally {
      setCode("");
      inFlight.current = false;
      setBusy(false);
    }
  }
  useEffect(() => {
    const received = Date.now();
    setCurrent(state);
    setReceivedAt(received);
    setNow(received);
  }, [state]);
  return (
    <form
      data-testid="authentication-mfa-form"
      onSubmit={(e) => {
        e.preventDefault();
        if (!busy) void act("mfa/verify");
      }}
    >
      <h1>이메일 인증</h1>
      <p>{current.maskedEmail} 등록 이메일로 보낸 인증번호를 입력해 주세요.</p>

      {error && <ErrorBanner message={error} />}
      <p aria-live="off">
        남은 시간 {Math.floor(seconds / 60)}:
        {String(seconds % 60).padStart(2, "0")}
      </p>
      <label>
        인증번호
        <input
          data-testid="authentication-mfa-code-input"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          inputMode="numeric"
          pattern="[0-9]{6}"
          maxLength={6}
          autoComplete="one-time-code"
          required
          autoFocus
        />
      </label>
      <button
        data-testid="authentication-mfa-verify-button"
        className="authentication-primary"
        disabled={busy}
      >
        인증 확인
      </button>
      <button
        data-testid="authentication-mfa-resend-button"
        type="button"
        disabled={busy}
        onClick={() => void act("mfa/resend")}
      >
        다시 보내기
      </button>
      <button
        data-testid="authentication-mfa-restart-button"
        type="button"
        disabled={busy}
        onClick={() => void act("restart")}
      >
        처음으로
      </button>
    </form>
  );
}
