import { useEffect, useRef, useState } from "react";
import { Icon } from "../../shared/Icon";
import type { NotificationBellProps } from "../notificationsops/NotificationBell";
import { ActivityCenter } from "../apiops/ActivityCenter";
import { AnalysisConversation } from "./AnalysisConversation";
import { useFeaturePermission } from "../../shared/permission/PermissionBoundary";
import type { PeriodContext } from "./sharedApi";

export function HeaderActions(props: NotificationBellProps = {}) {
  const canAnalyze = useFeaturePermission("analysisPanel");
  const [open, setOpen] = useState<"ai" | null>(null);
  const anchor = useRef<HTMLDivElement>(null);
  const closeAnalysis = useRef<HTMLButtonElement>(null);
  const [serverDate, setServerDate] = useState("");
  useEffect(() => {
    const update = (event: Event) => {
      const context = (event as CustomEvent<{ context?: PeriodContext }>).detail
        ?.context;
      if (context?.currentDate) setServerDate(context.currentDate);
    };
    window.addEventListener("sicost:screen-period-changed", update);
    return () =>
      window.removeEventListener("sicost:screen-period-changed", update);
  }, []);
  useEffect(() => {
    if (open !== "ai" || !canAnalyze) return;
    const previous = document.activeElement as HTMLElement | null;
    closeAnalysis.current?.focus();
    return () => {
      if (previous?.isConnected) previous.focus();
    };
  }, [open, canAnalyze]);
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
      {serverDate && (
        <span className="sicost-header-date">
          <Icon name="calendar" size={15} />
          기준일 {serverDate.replace(/-/g, ".")}
        </span>
      )}
      {canAnalyze && (
        <button
          className="sicost-ai-action"
          data-testid="header-analysis-open"
          aria-label="AI에게 묻기"
          aria-expanded={open === "ai"}
          onClick={() => setOpen(open === "ai" ? null : "ai")}
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
          data-testid="header-analysis-panel"
        >
          <header>
            <div>
              <strong>AI에게 묻기</strong>
              <small>지금 화면을 보면서 질문할 수 있습니다</small>
            </div>
            <button
              type="button"
              ref={closeAnalysis}
              data-testid="header-analysis-close"
              aria-label="AI 대화창 닫기"
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
