import { useEffect, useState } from "react";
import { ReminderDialog } from "./ReminderDialog";
import { DeliveryStatusPanel } from "./DeliveryStatusPanel";
import type { ReminderResult } from "./dunningDeliveryApi";

export function ReceivableDeliveryActions({
  billId,
  onChanged,
  onPanelChange,
}: {
  billId: string;
  onPanelChange?(open: boolean): void;
  onChanged?(): Promise<void> | void;
}) {
  const [open, setOpen] = useState<"reminder" | "deliveries">();
  useEffect(() => { onPanelChange?.(!!open); return () => onPanelChange?.(false); }, [open, onPanelChange]);
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
      <button
        type="button"
        data-testid={`reminder-open-${billId.toLowerCase()}`}
        onClick={() => setOpen("reminder")}
      >
        다시알림
      </button>
      <button
        type="button"
        data-testid={`deliveries-open-${billId.toLowerCase()}`}
        onClick={() => setOpen("deliveries")}
      >
        전달상태
      </button>
      {open === "reminder" && (
        <ReminderDialog
          key={billId}
          billId={billId}
          onClose={() => setOpen(undefined)}
          onAccepted={accepted}
        />
      )}
      {open === "deliveries" && (
        <DeliveryStatusPanel
          key={billId}
          billId={billId}
          focusSerial={serial}
          onClose={() => setOpen(undefined)}
          onRemind={() => setOpen("reminder")}
        />
      )}
    </span>
  );
}
