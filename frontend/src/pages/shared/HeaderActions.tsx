import { useEffect, useRef, useState } from "react";
import { Icon } from "../../shared/Icon";

export function HeaderActions() {
  const [open, setOpen] = useState<"ai" | "notice" | null>(null);
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
      <button
        className="sicost-icon-button"
        aria-label="알림"
        aria-expanded={open === "notice"}
        onClick={() => setOpen(open === "notice" ? null : "notice")}
      >
        <Icon name="bell" />
      </button>
      {open && (
        <div role="status" className="sicost-header-popover">
          <strong>{open === "ai" ? "AI 분석 도우미" : "알림"}</strong>
          <p>
            {open === "ai"
              ? "AI 기능은 준비 중입니다."
              : "알림 기능은 준비 중입니다."}
          </p>
        </div>
      )}
    </div>
  );
}
