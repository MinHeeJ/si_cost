import { useRef } from "react";
import { useLocation } from "react-router-dom";
import { SharedScreenFrame } from "./FollowUpScreen";
import { BillsScreen, type BillsScreenProps } from "../bills/BillsScreen";
export function BillingReceiptsScreen(props: BillsScreenProps = {}) {
  const location = useLocation();
  const incoming = location.state as {
    month?: string;
    section?: string;
    filter?: string;
    fromMonthClose?: boolean;
    fromCashFlow?: boolean;
    fromDashboard?: boolean;
    fromActivity?: boolean;
    fromNotification?: boolean;
    billId?: string;
  } | null;
  const entry = useRef<{
    key: string;
    month?: string;
    section?: string;
    filter?: string;
  }>();
  if (
    location.pathname === "/UI-13" &&
    (incoming?.fromMonthClose ||
      incoming?.fromCashFlow ||
      incoming?.fromDashboard ||
      incoming?.fromActivity) &&
    entry.current?.key !== location.key
  )
    entry.current = {
      key: location.key,
      month: incoming.month,
      section: incoming.section,
      filter: incoming.filter,
    };
  const month = entry.current?.month;
  return (
    <SharedScreenFrame
      key={entry.current?.key}
      screenId="UI-13"
      initialMonth={month}
    >
      <BillsScreen
        {...props}
        initialMonth={month}
        initialSection={entry.current?.section}
        initialMissingInvoiceNo={
          entry.current?.filter === "BILL_NUMBER_MISSING"
        }
        initialBillId={
          location.pathname === "/UI-13" &&
          incoming?.fromNotification &&
          /^B-\d{6}-\d{3}$/.test(incoming.billId ?? "")
            ? incoming.billId
            : undefined
        }
      />
    </SharedScreenFrame>
  );
}
