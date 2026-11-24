import { useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { authenticate } from "./authenticationApi";
export function PasswordChangeDialog({
  changedAt,
  onClose,
}: {
  changedAt?: string | null;
  onClose(): void;
}) {
  const [currentPassword, setCurrent] = useState(""),
    [newPassword, setNew] = useState(""),
    [confirmPassword, setConfirm] = useState(""),
    [pending, setPending] = useState(false);
  return (
    <Dialog
      open
      title="비밀번호 변경"
      confirmLabel="변경"
      onCancel={onClose}
      pending={pending}
      onConfirm={async () => {
        setPending(true);
        try {
          await authenticate("password/change", {
            currentPassword,
            newPassword,
            confirmPassword,
          });
          onClose();
        } finally {
          setCurrent("");
          setNew("");
          setConfirm("");
          setPending(false);
        }
      }}
    >
      <p>마지막 변경일: {changedAt ?? "변경일 정보 없음"}</p>
      <p>
        90일마다 변경을 권장합니다. 8자 이상 영문·숫자·특수문자를 포함하고 공백
        없이 입력하세요.
      </p>
      <div className="authentication-password-fields">
        <label>
          <span>현재 비밀번호</span>
          <input
            data-testid="authentication-password-change-current-input"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrent(e.target.value)}
            required
          />
        </label>
        <label>
          <span>새 비밀번호</span>
          <input
            data-testid="authentication-password-change-new-input"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => setNew(e.target.value)}
            required
          />
        </label>
        <label>
          <span>새 비밀번호 확인</span>
          <input
            data-testid="authentication-password-change-confirm-input"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirm(e.target.value)}
            required
          />
        </label>
      </div>
    </Dialog>
  );
}
