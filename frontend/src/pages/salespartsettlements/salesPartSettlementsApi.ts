import { request } from "../../shared/api";

export type SettlementIssue = {
  code: string;
  message: string;
  field: string | null;
  developerId: string | null;
  assignmentId: string | null;
  projectId: string | null;
  month: string | null;
  source: string;
  blocking: boolean;
};
export type SettlementStandard = {
  leaderId: string | null;
  leaderName: string | null;
  distributionRate: string | null;
  salesExpenseAmount: string | null;
  effectiveMonth: string | null;
  valid: boolean;
};
export type SettlementTab = {
  partId: string;
  partName: string;
  eligible: boolean;
  historyOnly: boolean;
  status: "WORK" | "CNFM" | "AUTO";
  standard: SettlementStandard;
  issues: SettlementIssue[];
};
export type SettlementTabsResult = {
  month: string;
  baseMonth: string;
  currentMonth: string;
  parts: SettlementTab[];
  eligibleCount: number;
  confirmedCount: number;
  unconfirmedCount: number;
  issues: SettlementIssue[];
};
export type SettlementBasis = {
  paySource:
    | "START_MONTH_BASE"
    | "UPLOADED_PAY"
    | "VENDOR_SUPPLY"
    | "ESTIMATED_BASE"
    | "START_MONTH_MISSING_ZERO"
    | "UNKNOWN"
    | "SNAPSHOT_UNKNOWN";
  estimated: boolean;
  firstMonthMissingPay: boolean;
  firstInputDate: string | null;
  calendarDays: number | null;
  payableDays: number | null;
  basePayAmount: string | null;
  distributionRate: string | null;
  tkSources: string[];
  warnings: SettlementIssue[];
};
export type SettlementRow = {
  developerId: string;
  name: string;
  managementNo: string;
  employeeType: string;
  projectNames: string[];
  legacyProjectText: string | null;
  mm: string | null;
  unitPrice: string | null;
  payAmount: string | null;
  salesExpenseAmount: string | null;
  retirementAmount: string | null;
  costAmount: string | null;
  profitAmount: string | null;
  profitShareAmount: string | null;
  remark: string | null;
  basis: SettlementBasis;
  issues: SettlementIssue[];
};
export type SettlementTotals = Pick<
  SettlementRow,
  | "mm"
  | "unitPrice"
  | "payAmount"
  | "salesExpenseAmount"
  | "retirementAmount"
  | "costAmount"
  | "profitAmount"
> & {
  personCount: number;
  deficitPersonCount: number;
  personProfitShareAmount: string | null;
  cardAmount: string | null;
  partProfitAmount: string | null;
};
export type SettlementResult = {
  partId: string;
  month: string;
  status: "WORK" | "CNFM" | "AUTO";
  standard: SettlementStandard;
  cardAmount: string | null;
  revision: string;
  confirmedBy: string | null;
  confirmedByName: string | null;
  confirmedAt: string | null;
  lastCancellationReason: string | null;
  readOnly: boolean;
  readOnlyReasons: string[];
  canSaveCard: boolean;
  canConfirm: boolean;
  canCancel: boolean;
  summary: SettlementTotals;
  totals: SettlementTotals;
  items: SettlementRow[];
  pagination: { page: number; size: 20 | 50 | 100; total: number };
  issues: SettlementIssue[];
};
export type SettlementQuery = {
  month?: string;
  page: number;
  size: 20 | 50 | 100;
  sort:
    | "name"
    | "employeeType"
    | "mm"
    | "unitPrice"
    | "payAmount"
    | "costAmount"
    | "profitAmount"
    | "profitShareAmount";
  direction: "ASC" | "DESC";
};
export function getSalesPartSettlements(month?: string, signal?: AbortSignal) {
  return request<SettlementTabsResult>(
    `/api/sales-part-settlements${month ? `?${new URLSearchParams({ month })}` : ""}`,
    { signal },
  );
}
export function getSalesPartSettlement(
  partId: string,
  query: SettlementQuery,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query))
    if (value !== undefined) params.set(key, String(value));
  return request<SettlementResult>(
    `/api/sales-part-settlements/${encodeURIComponent(partId)}?${params}`,
    { signal },
  );
}
