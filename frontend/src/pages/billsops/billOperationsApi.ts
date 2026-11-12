import { request } from "../../shared/api";

export type BillReference = {
  id: string;
  projectId: string;
  projectName: string;
  customerName: string;
  contractType: "MO" | "DP" | "TK" | "IN";
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
export type BillMutationResult = {
  changed: boolean;
  bill: BillReference;
  revision: string;
  warnings: string[];
};
export type BillEstimate = {
  projectId: string;
  month: string;
  supplyAmount: string | null;
  vatRate: string | null;
  defaultBillingDate: string;
  defaultDueDate: string;
  sources: {
    assignmentId: string;
    developerId: string;
    developerName: string;
    managementNo: string;
    month: string;
    appliedUnitPrice: string;
    mm: string;
    supplyAmount: string;
    protected: boolean;
  }[];
  warnings: string[];
};
export type BillCopyDraft = {
  sourceBillId: string;
  expectedRevision: string;
  year: number;
  projectId: string;
  billingMonth: string;
  supplyAmount: string | null;
  vatRate: string;
  billingDate: string;
  dueDate: string;
  taxInvoiceNo: null;
  warnings: string[];
};
export type SummaryCard = { amount: string; count: number; scope: string };
export type BillsSummary = {
  year: number;
  includeCarryover: boolean;
  billed: SummaryCard;
  currentMonth: SummaryCard;
  unpaid: SummaryCard;
  lateCount: number;
  lateScope: string;
};
export type BillSelection = {
  items: { billId: string; unpaidAmount: string; expectedRevision: string }[];
  total: number;
  excluded: { billId: string; reason: string }[];
  excludedCount: number;
  canSave: boolean;
};
export type SelectionFilters = {
  year: number;
  includeCarryover?: boolean;
  status?: BillReference["status"];
  month?: string;
  projectId?: string;
  sort?: string;
  direction?: "ASC" | "DESC";
  ids?: string[];
  excludeIds?: string[];
};
const query = (values: Record<string, unknown>) => {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (Array.isArray(value))
      value.forEach((item) => params.append(key, String(item)));
    else if (value !== undefined && value !== "")
      params.set(key, String(value));
  });
  return params.toString();
};
const billPath = (id: string) => `/api/bills/${encodeURIComponent(id)}`;
export const getBillEstimate = (projectId: string, month: string) =>
  request<BillEstimate>(`/api/bills/estimate?${query({ projectId, month })}`);
export const getBillsSummary = (year: number, includeCarryover = true) =>
  request<BillsSummary>(
    `/api/bills/summary?${query({ year, includeCarryover })}`,
  );
export const getBillSelection = (filters: SelectionFilters) =>
  request<BillSelection>(`/api/bills/selection?${query(filters)}`);
export const getBillCopyDraft = (id: string, year: number) =>
  request<BillCopyDraft>(`${billPath(id)}/copy-draft?${query({ year })}`);
export const saveBillCancellation = (
  id: string,
  body: { year: number; expectedRevision: string; reason: string },
) =>
  request<BillMutationResult>(`${billPath(id)}/cancel`, {
    method: "POST",
    body: JSON.stringify(body),
  });
export const createNextMonthBill = (
  id: string,
  body: { year: number; expectedRevision: string },
) =>
  request<BillMutationResult>(`${billPath(id)}/next-month`, {
    method: "POST",
    body: JSON.stringify(body),
  });
export type CopySubmission = {
  year: number;
  projectId: string;
  billingMonth: string;
  supplyAmount: string;
  vatRate: string;
  billingDate: string;
  dueDate: string;
  taxInvoiceNo: string | null;
  copyContext: { sourceBillId: string; expectedRevision: string };
  confirmDuplicate?: boolean;
  duplicateToken?: string;
};
export const createCopiedBill = (body: CopySubmission) =>
  request<BillMutationResult>("/api/bills", {
    method: "POST",
    body: JSON.stringify(body),
  });
