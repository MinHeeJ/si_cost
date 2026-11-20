import { useState, type ReactNode } from "react";
import { Dialog } from "./Dialog";
import { Icon } from "./Icon";
import "./screenGuide.css";

export function ScreenGuide({
  screenId,
  screenName,
  children,
}: {
  screenId: string;
  screenName: string;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);

  return (
    <>
      <button
        type="button"
        className="sicost-screen-guide"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen(true)}
      >
        <Icon name="info" size={15} />
        화면 안내
      </button>
      <Dialog
        open={open}
        title={`${screenName} 화면 안내`}
        onConfirm={close}
        onCancel={close}
        confirmLabel="닫기"
        hideCancel
      >
        <div className="sicost-screen-guide-content">{children}</div>
        <p className="sicost-screen-guide-id">화면 번호 {screenId}</p>
      </Dialog>
    </>
  );
}
