import { ConfirmDialog } from "./Dialog";

export const UNSAVED_MESSAGE =
  "저장하지 않은 내용이 있습니다. 나가면 입력한 내용이 사라집니다.";
export function UnsavedDialog({
  open,
  onContinue,
  onLeave,
}: {
  open: boolean;
  onContinue(): void;
  onLeave(): void;
}) {
  return (
    <ConfirmDialog
      open={open}
      title="저장하지 않고 나갈까요?"
      testId="unsaved-dialog"
      cancelTestId="unsaved-continue"
      confirmTestId="unsaved-leave"
      cancelLabel="계속 작성"
      confirmLabel="나가기"
      onCancel={onContinue}
      onConfirm={onLeave}
    >
      <p>{UNSAVED_MESSAGE}</p>
    </ConfirmDialog>
  );
}
