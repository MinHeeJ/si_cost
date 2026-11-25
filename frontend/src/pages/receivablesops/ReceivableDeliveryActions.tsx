import { usePermission } from "../../shared/permission/PermissionBoundary";
import { useEffect, useState } from "react";
import { ReminderDialog } from "./ReminderDialog";
import { DeliveryStatusPanel } from "./DeliveryStatusPanel";
import type { ReminderResult } from "./dunningDeliveryApi";

export function ReceivableDeliveryActions({
  billId,
  reminderLabel = "다시알림",
  deliveryLabel = "전달상태",
  onChanged,
  onPanelChange,
}: {
  billId: string;
  reminderLabel?: string;
  deliveryLabel?: string;
  onPanelChange?(open: boolean): void;
  onChanged?(): Promise<void> | void;
}) {
  const canUpdate = usePermission("UI-13", "UPD");
  const [open, setOpen] = useState<"reminder" | "deliveries">();
  const activePanel = open === "reminder" && !canUpdate ? undefined : open;
  useEffect(() => {
    onPanelChange?.(Boolean(activePanel));
    return () => onPanelChange?.(false);
  }, [activePanel, onPanelChange]);
  const [serial, setSerial] = useState<number>();
  async function accepted(result: ReminderResult) {
    await onChanged?.();
    setSerial(result.reservedSn);
    setOpen("deliveries");
    window.dispatchEvent(
      new CustomEvent("sicost:delivery-committed", { detail: { billId } }),
    );
  }
  return (
    <span
      data-testid={`receivable-delivery-actions-${billId.toLowerCase()}`}
      className="dunning-toolbar"
    >
      {canUpdate && (
        <button
          type="button"
          data-testid={`reminder-open-${billId.toLowerCase()}`}
          onClick={() => setOpen("reminder")}
        >
          {reminderLabel}
        </button>
      )}
      <button
        type="button"
        data-testid={`deliveries-open-${billId.toLowerCase()}`}
        onClick={() => setOpen("deliveries")}
      >
        {deliveryLabel}
      </button>
      {activePanel === "reminder" && (
        <ReminderDialog
          key={billId}
          billId={billId}
          onClose={() => setOpen(undefined)}
          onAccepted={accepted}
        />
      )}
      {activePanel === "deliveries" && (
        <DeliveryStatusPanel
          key={billId}
          billId={billId}
          focusSerial={serial}
          onClose={() => setOpen(undefined)}
          onRemind={canUpdate ? () => setOpen("reminder") : undefined}
        />
      )}
    </span>
  );
}
