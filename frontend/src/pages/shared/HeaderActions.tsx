import { useEffect, useRef, useState } from "react";
import { Icon } from "../../shared/Icon";
import type { NotificationBellProps } from "../notificationsops/NotificationBell";
import { ActivityCenter } from "../apiops/ActivityCenter";

export function HeaderActions(props: NotificationBellProps = {}) {
  const [open, setOpen] = useState<"ai" | null>(null);
  const anchor = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const dismiss = (event: PointerEvent) => {
      if (!anchor.current?.contains(event.target as Node)) setOpen(null);
    };
    document.addEventListener("pointerdown", dismiss);
    return () => document.removeEventListener("pointerdown", dismiss);
  }, []);
  return (
    <div
      className="sicost-header-actions"
      ref={anchor}
      onClickCapture={(event) => {
        if ((event.target as Element).closest(".notification-anchor"))
          setOpen(null);
      }}
      onKeyDown={(event) => {
        if (event.key === "Escape") setOpen(null);
      }}
    >
      <button
        className="sicost-ai-action"
        aria-label="AI에게 묻기"
        aria-expanded={open === "ai"}
        onClick={() => setOpen(open === "ai" ? null : "ai")}
      >
        <Icon name="sparkle" size={16} />
        <span>AI에게 묻기</span>
      </button>
      <ActivityCenter {...props} />
      {open && (
        <div role="status" className="sicost-header-popover">
          <strong>AI 분석 도우미</strong>
          <p>AI 기능은 준비 중입니다.</p>
        </div>
      )}
    </div>
  );
}
