import { useState } from "react";
import {
  NotificationBell,
  type NotificationBellProps,
} from "../notificationsops/NotificationBell";
import { AutomationRunsPanel } from "../automationrunsops/AutomationRunsPanel";

export function ActivityCenter(props: NotificationBellProps = {}) {
  const [automationOpen, setAutomationOpen] = useState(false);
  return (
    <div className="sicost-header-actions" data-testid="activity-center">
      <NotificationBell {...props} onOpenAutomation={() => setAutomationOpen(true)} />
      <button
        type="button"
        data-testid="activity-automation-open"
        aria-expanded={automationOpen}
        onClick={() => setAutomationOpen(true)}
      >
        자동처리상태
      </button>
      <AutomationRunsPanel
        open={automationOpen}
        onClose={() => setAutomationOpen(false)}
        onOpenReceivables={() => {
          void Promise.resolve(
            props.onOpenTarget?.({ screenId: "UI-13", linkKey: null }),
          ).then((opened) => {
            if (opened) setAutomationOpen(false);
          });
        }}
      />
    </div>
  );
}
