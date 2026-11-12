import { request } from "../../shared/api";

export type ReceiptBatchFilters = {
  year: number;
  status?: "UNPAID" | "PAID" | "LATE" | "CNCL";
  month?: string;
  projectId?: string;
  includeCarryover?: boolean;
  sort?:
    | "id"
    | "projectName"
    | "order"
    | "billingDate"
    | "supplyAmount"
    | "vatAmount"
    | "totalAmount"
    | "receiptAmount"
    | "unpaidAmount"
    | "dueDate"
    | "status"
    | "dunning";
  direction?: "ASC" | "DESC";
};
export type ReceiptBatchSelection = {
  items: { billId: string; unpaidAmount: string; expectedRevision: string }[];
  total: number;
  excluded: { billId: string; reason: string }[];
  excludedCount: number;
  canSave: boolean;
};
export type ReceiptBatchRequest = {
  year: number;
  date: string;
  items: { billId: string; amount: string; expectedRevision: string }[];
};
export type ReceiptBatchResult = {
  completedCount: number;
  totalAmount: string;
  items: {
    changed: boolean;
    receipt: {
      id: number;
      billId: string;
      date: string;
      amount: string;
      cancelled: boolean;
      cancelReason: string | null;
      cancelUserId: string | null;
      cancelledAt: string | null;
    };
    bill: {
      id: string;
      receiptAmount: string;
      unpaidAmount: string;
      revision: string;
      [key: string]: unknown;
    };
    revision: string;
  }[];
};
export function loadReceiptBatchSelection(
  filters: ReceiptBatchFilters,
  ids?: string[],
  signal?: AbortSignal,
) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined) params.set(key, String(value));
  });
  ids?.forEach((id) => params.append("ids", id));
  return request<ReceiptBatchSelection>(`/api/bills/selection?${params}`, {
    signal,
  });
}
export function createBatchReceipts(body: ReceiptBatchRequest) {
  return request<ReceiptBatchResult>("/api/receipts/batch", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
export const receiptBatchCommittedEvent = "sicost:receipt-batch-committed";
