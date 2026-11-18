import { useEffect, useRef, useState } from "react";
import { Icon } from "../../shared/Icon";
import type { NotificationBellProps } from "../notificationsops/NotificationBell";
import { ActivityCenter } from "../apiops/ActivityCenter";
import { AnalysisConversation } from "./AnalysisConversation";
import { SessionIndicator } from "../authentication/SessionIndicator";
import { useFeaturePermission } from "../../shared/permission/PermissionBoundary";

export function HeaderActions(props: NotificationBellProps = {}) {
  const canAnalyze = useFeaturePermission("analysisPanel");
  const [open, setOpen] = useState<"ai" | null>(null);
  const anchor = useRef<HTMLDivElement>(null);
  const sessionMenu = useRef<HTMLDetailsElement>(null);
  useEffect(() => {
    const dismiss = (event: PointerEvent) => {
      if (!anchor.current?.contains(event.target as Node)) {
        setOpen(null);
        if (
          !(event.target as Element).closest(".sicost-dialog,.sicost-drawer") &&
          sessionMenu.current
        )
          sessionMenu.current.open = false;
      }
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
        if (
          event.key === "Escape" &&
          !(event.target as Element).closest(".sicost-dialog,.sicost-drawer")
        ) {
          setOpen(null);
          if (sessionMenu.current?.open) {
            sessionMenu.current.open = false;
            sessionMenu.current.querySelector("summary")?.focus();
          }
        }
      }}
    >
      <details
        ref={sessionMenu}
        onToggle={(event) => {
          if (event.currentTarget.open) setOpen(null);
        }}
        className="sicost-header-session"
        data-testid="workspace-session-menu"
      >
        <summary data-testid="workspace-session-toggle">
          <Icon name="clock" size={16} />
          <span>세션·계정</span>
        </summary>
        <div className="sicost-header-session-panel">
          <SessionIndicator />
        </div>
      </details>
      {canAnalyze && (
        <button
          className="sicost-ai-action"
          data-testid="workspace-ai-open"
          aria-label="AI에게 묻기"
          aria-expanded={open === "ai"}
          onClick={() => {
            if (sessionMenu.current) sessionMenu.current.open = false;
            setOpen(open === "ai" ? null : "ai");
          }}
        >
          <Icon name="sparkle" size={16} />
          <span>AI에게 묻기</span>
        </button>
      )}
      <ActivityCenter {...props} />
      {open && canAnalyze && (
        <div
          role="dialog"
          aria-label="AI에게 묻기 대화창"
          className="analysis-side-panel"
          data-testid="workspace-ai-panel"
        >
          <header>
            <div>
              <strong>AI에게 묻기</strong>
              <small>지금 화면을 보면서 질문할 수 있습니다</small>
            </div>
            <button
              type="button"
              aria-label="AI 대화창 닫기"
              data-testid="workspace-ai-close"
              onClick={() => setOpen(null)}
            >
              ×
            </button>
          </header>
          <AnalysisConversation compact />
        </div>
      )}
    </div>
  );
}
