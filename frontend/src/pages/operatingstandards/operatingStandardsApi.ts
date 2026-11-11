import { request } from "../../shared/api";
export type OperatingStandard = {
  key: string;
  name: string;
  value: string | null;
  unitNm: string | null;
  effectiveMonth: string | null;
  description: string | null;
  keyRevision: string;
};
export type StandardVersion = {
  key: string;
  value: string;
  unitNm: string | null;
  effectiveMonth: string;
  status: string;
  revision: string;
};
export type StandardHistory = { items: StandardVersion[]; keyRevision: string };
export type StandardChange = {
  key: string;
  value: string;
  expectedRevision: string;
};
export type StandardsSave = {
  effectiveMonth?: string;
  changes: StandardChange[];
};
export type StandardsResult = {
  changed: boolean;
  message?: string;
  items: OperatingStandard[];
};
export type StandardsPeriodContext = {
  currentMonth: string;
  cashBaseMonth: string;
};
export type StandardsMonthClose = {
  items: { month: string; status: string }[];
};
export const getOperatingStandards = (month?: string) =>
  request<{ items: OperatingStandard[] }>(
    `/api/operating-standards${month ? `?month=${encodeURIComponent(month)}` : ""}`,
  );
export const getOperatingStandardHistory = (key: string) =>
  request<StandardHistory>(
    `/api/operating-standards/${encodeURIComponent(key)}/history`,
  );
export const saveOperatingStandards = (body: StandardsSave) =>
  request<StandardsResult>("/api/operating-standards", {
    method: "PUT",
    body: JSON.stringify(body),
  });
export const getStandardsPeriodContext = () =>
  request<StandardsPeriodContext>("/api/period-context");
export const getStandardsMonthClose = (from: string, to: string) =>
  request<StandardsMonthClose>(
    `/api/month-close?${new URLSearchParams({ from, to })}`,
  );
export const OPERATING_STANDARDS_COMMITTED =
  "sicost:operating-standards-committed";
export function notifyOperatingStandardsCommitted(cash: boolean) {
  window.dispatchEvent(
    new CustomEvent(OPERATING_STANDARDS_COMMITTED, {
      detail: {
        cash,
        resources: cash
          ? ["operating-standards", "parameters", "period-context"]
          : ["operating-standards", "parameters"],
      },
    }),
  );
}
export const operatingStandardGroups = [
  ["DUN_D1", "DUN_D2", "DUN_D3"],
  ["EXT_PRBL_H", "EXT_PRBL_M", "EXT_PRBL_L", "EXT_PRBL_NULL"],
  ["STEP_PRBL_1", "STEP_PRBL_2", "STEP_PRBL_3", "STEP_PRBL_4"],
  ["BILL_MISS_RT", "BILL_MISS_MIN"],
  [
    "AI_SCORE_JOB",
    "AI_SCORE_SKILL",
    "AI_SCORE_CUST",
    "AI_SCORE_GRADE",
    "AI_SCORE_PERF",
    "AI_SCORE_COST",
    "AI_SCORE_AVAIL",
    "AI_SCORE_LOC",
  ],
];
export const isCashStandard = (key: string) =>
  key === "CASH_BASE_YM" || key === "CASH_BASE_AMT";
export function nextStandardMonth(month: string) {
  const [year, m] = month.split("-").map(Number);
  return m === 12
    ? `${String(year + 1).padStart(4, "0")}-01`
    : `${String(year).padStart(4, "0")}-${String(m + 1).padStart(2, "0")}`;
}
