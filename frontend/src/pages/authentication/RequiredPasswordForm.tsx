import { useState } from "react";
import { authenticate, type AuthenticationSession } from "./authenticationApi";
import { ErrorBanner } from "../../shared/Feedback";
export function RequiredPasswordForm({
  onAuthenticated,
}: {
  onAuthenticated(value: AuthenticationSession): void;
}) {
  const [newPassword, setNew] = useState(""),
    [confirmPassword, setConfirm] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  return (
    <form
      data-testid="authentication-required-password-form"
      onSubmit={async (e) => {
        e.preventDefault();
        if (busy) return;
        setBusy(true);
        setError("");
        try {
          onAuthenticated(
            await authenticate("password/required", {
              newPassword,
              confirmPassword,
            }),
          );
        } catch (failure) {
          setError(
            failure instanceof Error ? failure.message : "변경하지 못했습니다.",
          );
        } finally {
          setNew("");
          setConfirm("");
          setBusy(false);
        }
      }}
    >
      <h1>첫 비밀번호 변경</h1>
      <p>안전한 사용을 위해 임시 비밀번호를 변경해 주세요.</p>
      <p>
        8자 이상, 영문·숫자·특수문자를 포함해 UTF-8 72바이트 이하로 입력하세요.
        공백은 사용할 수 없습니다.
      </p>
      {error && <ErrorBanner message={error} />}
      <label>
        새 비밀번호
        <input
          data-testid="authentication-required-password-new-input"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={(e) => setNew(e.target.value)}
          required
          autoFocus
        />
      </label>
      <label>
        새 비밀번호 확인
        <input
          data-testid="authentication-required-password-confirm-input"
          type="password"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(e) => setConfirm(e.target.value)}
          required
        />
      </label>
      <button
        data-testid="authentication-required-password-submit-button"
        className="authentication-primary"
        disabled={busy}
      >
        변경 후 로그인
      </button>
    </form>
  );
}
