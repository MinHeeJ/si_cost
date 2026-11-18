import { useId, useState } from "react";
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
  const [showPassword, setShowPassword] = useState(false);
  const passwordId = useId();
  return (
    <form
      data-testid="login-form"
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
          name="userId"
          data-testid="login-id-input"
          autoComplete="username"
          value={userId}
          maxLength={30}
          onChange={(e) => setUserId(e.target.value)}
          required
          autoFocus
        />
      </label>
      <div className="authentication-password-group">
        <label htmlFor={passwordId}>비밀번호</label>
        <span className="authentication-password-field">
          <input
            id={passwordId}
            name="password"
            data-testid="login-password-input"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <button
            type="button"
            className="authentication-password-toggle"
            data-testid="login-password-toggle"
            aria-label={showPassword ? "비밀번호 숨기기" : "비밀번호 보기"}
            aria-pressed={showPassword}
            onClick={() => setShowPassword((value) => !value)}
          >
            {showPassword ? "숨기기" : "보기"}
          </button>
        </span>
      </div>
      <button
        type="submit"
        data-testid="login-submit"
        className="authentication-primary"
        disabled={busy}
      >
        {busy ? "확인 중…" : "로그인"}
      </button>
      <button
        type="button"
        data-testid="login-recovery"
        onClick={onRecovery}
        disabled={busy}
      >
        비밀번호 찾기
      </button>
    </form>
  );
}
