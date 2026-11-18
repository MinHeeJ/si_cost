import { useState } from "react";
import { request } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { usePermission } from "../../shared/permission/PermissionBoundary";
export function PasswordResetAction({
  id,
  email,
  stopped,
  onChanged,
}: {
  id: string;
  email: string;
  stopped: boolean;
  onChanged(): void;
}) {
  const allowed = usePermission("UI-23", "UPD");
  const [open, setOpen] = useState(false),
    [pending, setPending] = useState(false),
    [message, setMessage] = useState("");
  if (!allowed) return null;
  const at = email.indexOf("@"),
    masked = at < 0 ? "***" : `${email.slice(0, 1)}***${email.slice(at)}`;
  return (
    <>
      <button
        data-testid="password-reset-open"
        type="button"
        disabled={stopped}
        onClick={() => setOpen(true)}
      >
        새 비밀번호 다시 발급
      </button>
      {message && <p role="status">{message}</p>}
      <Dialog
        open={open}
        title="비밀번호 초기화"
        onCancel={() => setOpen(false)}
        pending={pending}
        confirmLabel="새 비밀번호 발급"
        onConfirm={async () => {
          setPending(true);
          try {
            const result = await request<{
              mailStatus: string;
              passwordIssued: boolean;
              unlockRequired: boolean;
              message: string;
            }>(`/api/business-users/${encodeURIComponent(id)}/password-reset`, {
              method: "POST",
              body: JSON.stringify({ confirmed: true }),
            });
            setMessage(
              `${result.message}${result.unlockRequired ? " 계정은 잠긴 상태입니다. 별도 잠금 해제가 필요합니다." : ""}`,
            );
            setOpen(false);
            onChanged();
          } finally {
            setPending(false);
          }
        }}
      >
        <p>{masked} 등록 이메일로 새 임시 비밀번호를 발송합니다.</p>
        <p>
          이전 비밀번호와 모든 로그인 세션은 무효화됩니다. 발송 실패·접수 불명
          시 이전 비밀번호는 복구되지 않습니다.
        </p>
      </Dialog>
    </>
  );
}
