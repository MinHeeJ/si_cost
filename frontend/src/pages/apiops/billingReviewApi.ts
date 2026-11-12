import { request } from "../../shared/api";

export type ReviewPageSize = 20 | 50 | 100;
export type ReviewAmountUnit = "원" | "천원" | "백만원";
export type BillingSource = {
  assignmentId: string;
  developerId: string;
  developerName: string;
  managementNo: string;
  month: string;
  appliedUnitPrice: string;
  mm: string;
  supplyAmount: string;
  protected: boolean;
};
export type BillingCheckContext = {
  projectId: string;
  month: string;
  checkRevision: string;
};
export type BillingCheckRemedy = {
  mode: "CREATE" | "UPDATE" | "DETAIL_ONLY" | "NONE";
  projectId: string;
  month: string;
  billId: string | null;
  proposedSupplyAmount: string | null;
  expectedRevision: string | null;
  checkContext: BillingCheckContext;
  reason: string | null;
  relatedBillIds: string[];
};
export type BillingCheckRow = {
  projectId: string;
  projectName: string;
  contractType: "MO" | "DP";
  personCount: number;
  mm: string;
  estimatedAmount: string;
  billedAmount: string;
  differenceAmount: string;
  tolerance: string;
  billCount: number;
  verdict: "청구 없음" | "적게 청구" | "많이 청구" | "맞음";
  explanation: string;
  sources: BillingSource[];
  remedy: BillingCheckRemedy;
  checkRevision: string;
};
export type BillingChecksPage = {
  month: string;
  availableMonths: string[];
  defaultMonth: string | null;
  includesPlanned: boolean;
  summary: {
    projectCount: number;
    matchedCount: number;
    missingCount: number;
    differenceCount: number;
    missingSupplyAmount: string;
    tabCount: number;
  };
  items: BillingCheckRow[];
  total: number;
  page: number;
  size: ReviewPageSize;
  warnings: string[];
};
export type BillingDunning = {
  elapsedDays: number;
  step: number;
  nextStep: number | null;
  nextDate: string | null;
  finalStep: boolean;
};
export type BillingRecipientGroup = {
  step: number;
  recipients: { userId: string; name: string; roles: string[] }[];
  unassignedReasons: string[];
};
export type BillingDunningHistory = {
  billId: string;
  sn: number;
  step: number;
  sendDate: string;
  receiverDescription: string;
  sendMethodCode: string;
  resend: boolean;
};
export type Receivable = {
  billId: string;
  projectId: string;
  projectName: string;
  unpaidAmount: string;
  dueDate: string;
  dunning: BillingDunning;
  recipients: BillingRecipientGroup[];
  sentHistory: BillingDunningHistory[];
  sendNotice: string;
};
export type ReceivablesPage = {
  asOfDate: string;
  items: Receivable[];
  total: number;
  tabCount: number;
  page: number;
  size: ReviewPageSize;
  cards: {
    step: number;
    thresholdDays: string;
    count: number;
    recipientRoles: string[];
  }[];
};
export function getBillingChecks(
  query: { month?: string; page: number; size: ReviewPageSize },
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  if (query.month) params.set("month", query.month);
  return request<BillingChecksPage>(`/api/billing-checks?${params}`, {
    signal,
  });
}
export function getReceivables(
  query: { page: number; size: ReviewPageSize },
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  return request<ReceivablesPage>(`/api/receivables?${params}`, { signal });
}
