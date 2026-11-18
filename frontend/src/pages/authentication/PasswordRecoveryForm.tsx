import { useState } from "react";
import { request } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
export function PasswordRecoveryForm({ onClose }: { onClose(): void }) {
  const [userId, setId] = useState(""),
    [email, setEmail] = useState(""),
    [message, setMessage] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        if (busy) return;
        setBusy(true);
        setError("");
        try {
          const result = await request<{ message: string }>(
            "/api/auth/forgot-password",
            { method: "POST", body: JSON.stringify({ userId, email }) },
          );
          setMessage(result.message);
        } catch (failure) {
          setError(
            failure instanceof Error ? failure.message : "요청하지 못했습니다.",
          );
        } finally {
          setBusy(false);
        }
      }}
    >
      <h1>비밀번호 찾기</h1>
      <p>아이디와 등록 이메일을 입력해 주세요.</p>
      {error && <ErrorBanner message={error} />}
      {message && <p role="status">{message}</p>}
      <label>
        아이디
        <input
          value={userId}
          onChange={(e) => setId(e.target.value)}
          maxLength={30}
          required
        />
      </label>
      <label>
        등록 이메일
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          maxLength={100}
          required
        />
      </label>
      <button className="authentication-primary" disabled={busy}>
        임시 비밀번호 요청
      </button>
      <button type="button" onClick={onClose} disabled={busy}>
        로그인으로 돌아가기
      </button>
    </form>
  );
}
