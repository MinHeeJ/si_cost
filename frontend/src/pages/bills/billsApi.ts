import { request } from "../../shared/api";

export type BillStatus = "PAID" | "UNPAID" | "LATE" | "CNCL";
export type Dunning = {
  elapsedDays: number;
  step: number;
  nextStep: number | null;
  nextDate: string | null;
  finalStep: boolean;
};
export type Bill = {
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
  status: BillStatus;
  cancelled: boolean;
  cancelReason: string | null;
  cancelUserId: string | null;
  cancelledAt: string | null;
  carryover: boolean;
  dunning: Dunning | null;
  quickActions: {
    copyAllowed: boolean;
    nextMonthAllowed: boolean;
    reason: string | null;
  };
  revision: string;
};
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
export type BillDetail = {
  bill: Bill;
  receipts: {
    items: Receipt[];
    total: number;
    normalCount: number;
    revision: string;
  };
  revision: string;
};
export type Totals = Pick<
  Bill,
  | "supplyAmount"
  | "vatAmount"
  | "totalAmount"
  | "receiptAmount"
  | "unpaidAmount"
>;
export type BillsPage = {
  items: Bill[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  totals: Totals;
};
export type Filters = {
  year: number;
  status?: BillStatus;
  month?: string;
  projectId?: string;
  includeCarryover: boolean;
};
export type Sort =
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
export type Query = Filters & {
  page: number;
  size: 20 | 50 | 100;
  sort?: Sort;
  direction?: "ASC" | "DESC";
};
export type Editable = Pick<
  Bill,
  "supplyAmount" | "vatRate" | "billingDate" | "dueDate" | "taxInvoiceNo"
>;
export type CheckContext = {
  projectId: string;
  month: string;
  checkRevision: string;
};
export type CopyContext = { sourceBillId: string; expectedRevision: string };
export type CreateBill = Editable & {
  year: number;
  projectId: string;
  billingMonth: string;
  confirmDuplicate?: boolean;
  duplicateToken?: string;
  checkContext?: CheckContext;
  copyContext?: CopyContext;
};
export type PutBill = Editable & {
  year: number;
  expectedRevision: string;
  checkContext?: CheckContext;
};
export type Mutation = {
  changed: boolean;
  bill: Bill;
  revision: string;
  warnings: string[];
};
export type Estimate = {
  projectId: string;
  month: string;
  supplyAmount: string | null;
  vatRate: string | null;
  defaultBillingDate: string;
  defaultDueDate: string;
  sources: {
    assignmentId: string;
    developerName: string;
    managementNo: string;
    appliedUnitPrice: string;
    mm: string;
    supplyAmount: string;
    protected: boolean;
  }[];
  warnings: string[];
};
export type ProjectOption = {
  id: string;
  name: string;
  customerName: string | null;
  contractType: string;
  status: string;
};
export type Summary = {
  year: number;
  includeCarryover: boolean;
  billed: Card;
  currentMonth: Card;
  unpaid: Card;
  lateCount: number;
  lateScope: string;
};
type Card = { amount: string; count: number; scope: string };
export type CopyDraft = Omit<Editable, "supplyAmount"> & {
  supplyAmount: string | null;
  sourceBillId: string;
  expectedRevision: string;
  year: number;
  projectId: string;
  billingMonth: string;
  warnings: string[];
};
export const BILLS_COMMITTED = "sicost:bills-committed";
export function params(values: object) {
  const p = new URLSearchParams();
  Object.entries(values).forEach(([k, v]) => {
    if (v !== undefined && v !== "") p.set(k, String(v));
  });
  return p;
}
export const getBills = (query: Query) =>
  request<BillsPage>(`/api/bills?${params(query)}`);
export const getBill = (id: string, year: number) =>
  request<BillDetail>(
    `/api/bills/${encodeURIComponent(id)}?${params({ year })}`,
  );
export const createBill = (body: CreateBill) =>
  request<Mutation>("/api/bills", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const putBill = (id: string, body: PutBill) =>
  request<Mutation>(`/api/bills/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
export const getSummary = (year: number, includeCarryover: boolean) =>
  request<Summary>(`/api/bills/summary?${params({ year, includeCarryover })}`);
export const getEstimate = (projectId: string, month: string) =>
  request<Estimate>(`/api/bills/estimate?${params({ projectId, month })}`);
export const getProjectOptions = () =>
  request<{ items: ProjectOption[] }>("/api/projects/options?purpose=BILLING");
export const getCopyDraft = (id: string, year: number) =>
  request<CopyDraft>(
    `/api/bills/${encodeURIComponent(id)}/copy-draft?${params({ year })}`,
  );
export const nextMonth = (bill: Bill, year: number) =>
  request<Mutation>(`/api/bills/${encodeURIComponent(bill.id)}/next-month`, {
    method: "POST",
    body: JSON.stringify({ year, expectedRevision: bill.revision }),
  });
