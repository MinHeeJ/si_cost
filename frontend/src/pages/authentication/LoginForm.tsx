import { useState } from "react";
import {
  authenticate,
  session,
  type AuthenticationSession,
} from "./authenticationApi";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
export function LoginForm({
  onAuthenticated,
  onRecovery,
}: {
  onAuthenticated(value: AuthenticationSession, notice?: string): void;
  onRecovery(): void;
}) {
  const [userId, setUserId] = useState(""),
    [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <form
      data-testid="authentication-login-form"
      onSubmit={async (event) => {
        event.preventDefault();
        if (busy) return;
        setBusy(true);
        setError("");
        try {
          onAuthenticated(await authenticate("login", { userId, password }));
        } catch (e) {
          const message =
            e instanceof Error ? e.message : "로그인하지 못했습니다.";
          if (
            e instanceof ApiError &&
            ["MFA_DELIVERY_FAILED", "MAIL_NOT_CONFIGURED"].includes(e.body.code)
          ) {
            try {
              const pending = await session();
              if (pending.nextStep === "MFA") {
                onAuthenticated(pending, message);
                return;
              }
            } catch {
              /* The common boundary handles an invalidated proof. */
            }
          }
          setError(message);
        } finally {
          setPassword("");
          setBusy(false);
        }
      }}
    >
      <h1>로그인</h1>
      <p>등록된 계정으로 로그인해 주세요.</p>
      {error && <ErrorBanner message={error} />}
      <label>
        아이디
        <input
          data-testid="authentication-login-user-id-input"
          name="userId"
          autoComplete="username"
          value={userId}
          maxLength={30}
          onChange={(e) => setUserId(e.target.value)}
          required
          autoFocus
        />
      </label>
      <label>
        비밀번호
        <input
          data-testid="authentication-login-password-input"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
      </label>
      <button
        data-testid="authentication-login-submit-button"
        type="submit"
        className="authentication-primary"
        disabled={busy}
      >
        {busy ? "확인 중…" : "로그인"}
      </button>
      <button
        data-testid="authentication-login-recovery-button"
        type="button"
        onClick={onRecovery}
        disabled={busy}
      >
        비밀번호 찾기
      </button>
    </form>
  );
}
