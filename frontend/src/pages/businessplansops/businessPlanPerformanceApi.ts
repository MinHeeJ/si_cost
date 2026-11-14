import {
  ApiError,
  request,
  type ApiEnvelope,
  type ApiErrorBody,
} from "../../shared/api";
import {
  downloadB7File,
  saveDownload,
  type B7Summary,
  type UploadIssuesPage,
} from "../b7/b7Api";
export type PlanActual = {
  month: string;
  sourceKind:
    | "MIGRATED"
    | "CLOSED_SNAPSHOT"
    | "CALCULATED"
    | "MISSING"
    | "FUTURE";
  closeStatus: string | null;
  fixed: boolean;
  migrated: boolean;
  provisional: boolean;
  amounts: Record<string, string | null>;
  completeness: {
    costComplete: boolean;
    revenueComplete: boolean;
    taxComplete: boolean;
    missing: string[];
    estimated: string[];
    sourceMonths: string[];
    issues: {
      month: string | null;
      metric: string;
      code: string;
      reason: string;
      estimated: boolean;
    }[];
    availableMonthCount: number;
    requestedMonthCount: number;
    missingMonths: string[];
  };
  sources: {
    month: string;
    sourceTable: string;
    sourceKey: string;
    metric: string;
    amount: string | null;
    estimated: boolean;
    reason: string | null;
  }[];
  calculatedAt: string;
};
export type PlanMonth = {
  month: string;
  salesTargetAmt: string | null;
  operatingProfitTargetAmt: string | null;
  salesActualAmt: string | null;
  operatingProfitActualAmt: string | null;
  salesAchievementRate: string | null;
  operatingMarginDifference: string | null;
  salesUnderTarget: boolean | null;
  operatingMarginAdverse: boolean | null;
  targetRegistered: boolean;
  actual: PlanActual;
};
export type PlanSummary = {
  metric:
    | "CUMULATIVE_SALES"
    | "CUMULATIVE_OPERATING_PROFIT"
    | "CURRENT_MONTH_SALES";
  actualAmt: string | null;
  targetAmt: string | null;
  achievementRate: string | null;
  underTarget: boolean | null;
};
export type BusinessPlanPerformanceData = {
  context: {
    year: number;
    month: string;
    baseMonth: string;
    nowMonth: string;
    timezone: "Asia/Seoul";
    snapshotAt: string;
    aggregation: "YTD" | "MONTH" | "QUARTER";
    amountUnit: "WON" | "THOUSAND_WON" | "MILLION_WON";
    readOnly: true;
  };
  targetStatus: "UNREGISTERED" | "ZERO_TARGETS" | "REGISTERED" | "INCOMPLETE";
  months: PlanMonth[];
  summary: PlanSummary[];
  notices: string[];
};
export type PlanTarget = {
  salesTargetAmt: string | null;
  operatingProfitTargetAmt: string | null;
};
export type PlanValidation = {
  uploadId: string;
  status: "CHECKED";
  summary: B7Summary;
  issues: UploadIssuesPage;
  expectedRevision: string;
  canApply: boolean;
  warnings: string[];
  year: number;
  changes: {
    month: string;
    rowNumber: number;
    closed: boolean;
    changed: boolean;
    before: PlanTarget | null;
    after: PlanTarget;
  }[];
  requiresClosedMonthConfirmation: boolean;
  blankTargetMonths: string[];
};
export type PlanApplied = {
  uploadId: string;
  status: "APPLIED";
  summary: B7Summary;
  alreadyApplied: boolean;
  message: string;
  appliedAt: string;
  changedMonths: string[];
  unchangedMonths: string[] | null;
  keptMonths: string[] | null;
  warnings: string[];
  year: number;
  mode: "ALL" | "KEEP_CLOSED";
};
export const BUSINESS_PLAN_COMMITTED = "sicost:business-plans-committed";
export const getBusinessPlanPerformance = (
  year: number,
  month: string,
  signal?: AbortSignal,
) =>
  request<BusinessPlanPerformanceData>(
    `/api/business-plans/performance?${new URLSearchParams({ year: String(year), month })}`,
    { signal },
  );
export async function downloadBusinessPlanTemplate(year: number) {
  saveDownload(
    await downloadB7File(
      `/api/business-plans/imports/template?${new URLSearchParams({ year: String(year) })}`,
    ),
  );
}
async function send<T>(
  operation: "validate" | "apply",
  file: File,
  fields: Record<string, string>,
): Promise<T> {
  const body = new FormData();
  body.append("file", file);
  Object.entries(fields).forEach(([key, value]) => body.append(key, value));
  const response = await fetch(`/api/business-plans/imports/${operation}`, {
    method: "POST",
    body,
    redirect: "error",
  });
  const payload = (await response.json()) as ApiEnvelope<T> | ApiErrorBody;
  if (!response.ok) {
    if (payload.success === false) throw new ApiError(response.status, payload);
    throw new Error("서버 요청에 실패했습니다.");
  }
  if (!payload.success || !payload.meta || !("data" in payload))
    throw new Error("올바르지 않은 서버 응답입니다.");
  return payload.data;
}
export const validateBusinessPlan = (file: File, year: number) =>
  send<PlanValidation>("validate", file, { year: String(year) });
export async function applyBusinessPlan(
  file: File,
  validation: PlanValidation,
  mode: "ALL" | "KEEP_CLOSED",
) {
  const result = await send<PlanApplied>("apply", file, {
    year: String(validation.year),
    uploadId: validation.uploadId,
    expectedRevision: validation.expectedRevision,
    mode,
  });
  window.dispatchEvent(new Event(BUSINESS_PLAN_COMMITTED));
  return result;
}
