import { request } from "../../shared/api";

export type CloseCheck = {
  code: string;
  name: string;
  status: "OK" | "WARN";
  count: number | null;
  description: string | null;
  checkedAt: string | null;
  linkScreenId: string | null;
  linkMonth: string;
  linkFilter: string;
};
export type CloseIssue = {
  code: string;
  message: string;
  month: string;
  sourceType?: string;
  sourceId?: string | null;
  field?: string | null;
};
export type CloseSnapshot = {
  table: string;
  rowCount: number;
  preservedIndependentFixedCount: number;
};
export type CloseHistory = {
  month: string;
  historySequence: number;
  action: "CLOSE" | "OPEN";
  actorId: string;
  actorName?: string | null;
  occurredAt: string;
  reason: string | null;
  remainingChecks: string | null;
};
export type MonthCloses = {
  month: string;
  status: "WORK" | "CLOSE";
  closedBy: string | null;
  closedByName?: string | null;
  closedAt: string | null;
  nextCloseMonth: string;
  latestCloseMonth: string | null;
  managementAllowed: boolean;
  canCheck: boolean;
  canClose: boolean;
  canReopen: boolean;
  revision: string;
  checks: CloseCheck[];
  checkMode: "CURRENT" | "CLOSE_SNAPSHOT";
  warningCount: number;
  history: {
    items: CloseHistory[];
    total: number;
    page: number;
    size: number;
    hasMore: boolean;
  };
  issues: CloseIssue[];
};
export type ClosePreview = {
  month: string;
  revision: string;
  canClose: boolean;
  checks: CloseCheck[];
  warningCount: number;
  remainingCheckNames: string[];
  parts: {
    partId: string;
    partName: string;
    status: string;
    willAutoConfirm: boolean;
    issues: CloseIssue[];
  }[];
  eligiblePartCount: number;
  autoConfirmCount: number;
  snapshots: CloseSnapshot[];
  usesEstimatedPayroll: boolean;
  cashImpact: {
    excludedUnpaidBills: string | null;
    excludedUnconfirmedTaxes: string | null;
    incomeBefore: string | null;
    incomeAfter: string | null;
    taxPaymentBefore: string | null;
    taxPaymentAfter: string | null;
    closingBalanceBefore: string | null;
    closingBalanceAfter: string | null;
  };
  blockingIssues: CloseIssue[];
};
export type ReopenPreview = {
  month: string;
  revision: string;
  canReopen: boolean;
  closedBy: string;
  closedAt: string;
  snapshots: CloseSnapshot[];
  settlementsPreserved: boolean;
  adjustments: {
    adjustmentId: string;
    targetMonth: string;
    applyMonth: string;
    amount: string;
    reason: string;
  }[];
  adjustmentCount: number;
  adjustmentAmount: string;
  adjustmentMonths: string[];
  blockingIssues: CloseIssue[];
};
export type CloseCommit = {
  month: string;
  status: "WORK" | "CLOSE";
  revision: string;
  operationId: string;
  history: CloseHistory;
  replayed: boolean;
  snapshots: CloseSnapshot[];
  settlementsPreserved: boolean;
  invalidatedQueries: string[];
};
const post = <T>(month: string, action: string, body: unknown) =>
  request<T>(`/api/month-closes/${encodeURIComponent(month)}/${action}`, {
    method: "POST",
    body: JSON.stringify(body),
  });
export const getMonthCloses = (month: string, page = 1, size = 50) =>
  request<MonthCloses>(
    `/api/month-closes?${new URLSearchParams({ month, page: String(page), size: String(size) })}`,
  );
export const saveMonthCloseChecks = (month: string, expectedRevision: string) =>
  post<{
    month: string;
    status: "WORK";
    revision: string;
    checks: CloseCheck[];
    checkedAt: string;
  }>(month, "checks", { expectedRevision });
export const createMonthClosePreview = (
  month: string,
  expectedRevision: string,
) => post<ClosePreview>(month, "preview", { expectedRevision });
export const createMonthCloseReopenPreview = (
  month: string,
  expectedRevision: string,
) => post<ReopenPreview>(month, "reopen-preview", { expectedRevision });
export const saveMonthClose = (
  month: string,
  body: {
    expectedRevision: string;
    operationId: string;
    acceptWarnings: boolean;
  },
) => post<CloseCommit>(month, "close", body);
export const saveMonthCloseReopen = (
  month: string,
  body: {
    expectedRevision: string;
    operationId: string;
    reason: string;
    acceptAdjustments: boolean;
  },
) => post<CloseCommit>(month, "reopen", body);
export const MONTH_CLOSE_COMMITTED = "sicost:month-close-committed";
export function publishMonthCloseCommit(month: string) {
  const detail = { month, cash: true };
  for (const name of [
    MONTH_CLOSE_COMMITTED,
    "sicost:payroll-committed",
    "sicost:assignments-committed",
    "sicost:financial-committed",
    "sicost:corporate-tax-committed",
    "sicost:settlements-committed",
    "sicost:projects-committed",
  ])
    window.dispatchEvent(new CustomEvent(name, { detail }));
}
