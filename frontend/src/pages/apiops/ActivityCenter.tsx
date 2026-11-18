import { useState } from "react";
import {
  NotificationBell,
  type NotificationBellProps,
} from "../notificationsops/NotificationBell";
import { AutomationRunsPanel } from "../automationrunsops/AutomationRunsPanel";
import { useFeaturePermission } from "../../shared/permission/PermissionBoundary";

export function ActivityCenter(props: NotificationBellProps = {}) {
  const canAutomation = useFeaturePermission("automationLogs");
  const [automationOpen, setAutomationOpen] = useState(false);
  return (
    <div className="sicost-header-actions" data-testid="activity-center">
      <NotificationBell
        {...props}
        onOpenAutomation={
          canAutomation ? () => setAutomationOpen(true) : undefined
        }
      />
      {canAutomation && (
        <button
          type="button"
          data-testid="activity-automation-open"
          aria-expanded={automationOpen}
          onClick={() => setAutomationOpen(true)}
        >
          자동처리상태
        </button>
      )}
      {canAutomation && (
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
      )}
    </div>
  );
}
