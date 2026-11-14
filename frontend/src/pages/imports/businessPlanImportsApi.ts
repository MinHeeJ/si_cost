import {
  ApiError,
  request,
  type ApiEnvelope,
  type ApiErrorBody,
} from "../../shared/api";
import {
  downloadB7File,
  saveDownload,
  type UploadIssuesPage,
} from "../b7/b7Api";

export type ImportSummary = {
  totalRows: number;
  successRows: number;
  warningRows: number;
  errorRows: number;
  fileWarningCount: number;
};
export type ImportTarget = {
  salesTargetAmt: string | null;
  operatingProfitTargetAmt: string | null;
};
export type ImportChange = {
  month: string;
  rowNumber: number;
  closed: boolean;
  changed: boolean;
  before: ImportTarget | null;
  after: ImportTarget;
};
export type BusinessPlanValidation = {
  uploadId: string;
  status: "CHECKED";
  summary: ImportSummary;
  issues: UploadIssuesPage;
  expectedRevision: string;
  canApply: boolean;
  warnings: string[];
  year: number;
  changes: ImportChange[];
  requiresClosedMonthConfirmation: boolean;
  blankTargetMonths: string[];
};
export type ImportMode = "ALL" | "KEEP_CLOSED";
export type BusinessPlanApplied = {
  uploadId: string;
  status: "APPLIED";
  summary: ImportSummary;
  alreadyApplied: boolean;
  message: string;
  appliedAt: string;
  changedMonths: string[];
  unchangedMonths: string[] | null;
  keptMonths: string[] | null;
  warnings: string[];
  year: number;
  mode: ImportMode;
};
export type BusinessPlanUploadResult = {
  uploadId: string;
  formType: "B10";
  screenId: "UI-18";
  year: number;
  targetMonth: string;
  status: "CHECKED" | "APPLIED";
  summary: ImportSummary;
  appliedAt: string | null;
  changedMonths: string[];
  issues: UploadIssuesPage;
};
export const BUSINESS_PLAN_IMPORT_COMMITTED =
  "sicost:business-plan-import-committed";
async function upload<T>(
  path: string,
  file: File,
  year: number,
  context?: { uploadId: string; expectedRevision: string; mode: ImportMode },
): Promise<T> {
  const body = new FormData();
  body.append("file", file);
  body.append("year", String(year));
  if (context)
    for (const [key, value] of Object.entries(context)) body.append(key, value);
  const response = await fetch(path, {
    method: "POST",
    body,
    redirect: "error",
    headers: { Accept: "application/json" },
  });
  const envelope = (await response.json()) as ApiEnvelope<T> | ApiErrorBody;
  if (!response.ok) {
    if (envelope.success === false)
      throw new ApiError(response.status, envelope);
    throw new Error("서버 응답을 확인해 주세요.");
  }
  if (envelope.success !== true || !envelope.data)
    throw new Error("업로드 결과를 확인해 주세요.");
  return envelope.data;
}
export const createBusinessPlanImportValidation = (file: File, year: number) =>
  upload<BusinessPlanValidation>(
    "/api/business-plans/imports/validate",
    file,
    year,
  );
export const saveBusinessPlanImport = (
  file: File,
  year: number,
  uploadId: string,
  expectedRevision: string,
  mode: ImportMode,
) =>
  upload<BusinessPlanApplied>("/api/business-plans/imports/apply", file, year, {
    uploadId,
    expectedRevision,
    mode,
  });
export const getBusinessPlanImportTemplate = (year: number) =>
  downloadB7File(
    `/api/business-plans/imports/template?${new URLSearchParams({ year: String(year) })}`,
  );
export const getBusinessPlanUploadResult = (uploadId: string) =>
  request<BusinessPlanUploadResult>(
    `/api/uploads/${encodeURIComponent(uploadId)}/result`,
  );
export { saveDownload };
