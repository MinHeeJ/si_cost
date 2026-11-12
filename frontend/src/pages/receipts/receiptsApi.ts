import { request } from "../../shared/api";

export type Receipt = {
  id: number;
  billId: string;
  date: string;
  amount: string;
  cancelled: boolean;
  cancelReason: string | null;
  cancelUserId: string | null;
  cancelledAt: string | null;
};
export type Bill = {
  id: string;
  projectId: string;
  projectName: string;
  customerName: string;
  contractType: string;
  order: number;
  billingMonth: string;
  billingDate: string;
  supplyAmount: string;
  vatRate: string;
  vatAmount: string;
  totalAmount: string;
  receiptAmount: string;
  unpaidAmount: string;
  dueDate: string;
  taxInvoiceNo: string | null;
  taxInvoiceWarning: string | null;
  status: "PAID" | "UNPAID" | "LATE" | "CNCL";
  cancelled: boolean;
  cancelReason: string | null;
  cancelUserId: string | null;
  cancelledAt: string | null;
  carryover: boolean;
  dunning: {
    elapsedDays: number;
    step: number;
    nextStep: number | null;
    nextDate: string | null;
    finalStep: boolean;
  } | null;
  quickActions: {
    copyAllowed: boolean;
    nextMonthAllowed: boolean;
    reason: string | null;
  };
  revision: string;
};
export type BillReceipts = {
  items: Receipt[];
  total: number;
  normalCount: number;
  revision: string;
};
export type BillDetail = {
  bill: Bill;
  receipts: BillReceipts;
  revision: string;
};
export type ReceiptResult = {
  changed: boolean;
  receipt: Receipt;
  bill: Bill;
  revision: string;
};
export type ReceiptCreate = {
  year: number;
  expectedRevision: string;
  date: string;
  amount: string;
};
export type ReceiptCancel = {
  year: number;
  expectedRevision: string;
  reason: string;
};
export const RECEIPTS_COMMITTED = "sicost:billing-committed";
const path = (id: string) => `/api/bills/${encodeURIComponent(id)}`;
export function getReceiptBill(id: string, year: number) {
  return request<BillDetail>(
    `${path(id)}?${new URLSearchParams({ year: String(year) })}`,
  );
}
export function getBillReceipts(id: string, year: number) {
  return request<BillReceipts>(
    `${path(id)}/receipts?${new URLSearchParams({ year: String(year) })}`,
  );
}
export function createBillReceipt(id: string, input: ReceiptCreate) {
  return request<ReceiptResult>(`${path(id)}/receipts`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
export function saveBillReceiptCancellation(
  id: string,
  receiptId: number,
  input: ReceiptCancel,
) {
  return request<ReceiptResult>(
    `${path(id)}/receipts/${encodeURIComponent(String(receiptId))}/cancel`,
    { method: "POST", body: JSON.stringify(input) },
  );
}
