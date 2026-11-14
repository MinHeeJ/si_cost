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

export type MigrationSummary = {
  totalRows: number;
  successRows: number;
  warningRows: number;
  errorRows: number;
  fileWarningCount: number;
};
export type MigrationChange = {
  month: string;
  rowNumber: number;
  changed: boolean;
  before: Record<string, string | null> | null;
  after: Record<string, string | null>;
};
export type MigrationValidation = {
  uploadId: string;
  status: "CHECKED";
  summary: MigrationSummary;
  issues: UploadIssuesPage;
  expectedRevision: string;
  canApply: boolean;
  warnings: string[];
  allowedFromMonth: string;
  allowedThroughMonth: string;
  changes: MigrationChange[];
};
export type MigrationApplied = {
  uploadId: string;
  status: "APPLIED";
  summary: MigrationSummary;
  alreadyApplied: boolean;
  message: string;
  appliedAt: string;
  changedMonths: string[];
  unchangedMonths: string[] | null;
  keptMonths: string[] | null;
  warnings: string[];
};
export type MigrationUploadResult = {
  uploadId: string;
  formType: "B11";
  screenId: "UI-08";
  year: null;
  targetMonth: null;
  status: "CHECKED" | "APPLIED";
  summary: MigrationSummary;
  appliedAt: string | null;
  changedMonths: string[];
  issues: UploadIssuesPage;
};
export const HISTORICAL_PROFIT_LOSS_IMPORT_COMMITTED =
  "sicost:historical-profit-loss-import-committed";
async function upload<T>(
  path: string,
  file: File,
  context?: { uploadId: string; expectedRevision: string },
): Promise<T> {
  const body = new FormData();
  body.append("file", file);
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
export const createHistoricalProfitLossImportValidation = (file: File) =>
  upload<MigrationValidation>(
    "/api/profit-loss/migration/imports/validate",
    file,
  );
export const saveHistoricalProfitLossImport = (
  file: File,
  uploadId: string,
  expectedRevision: string,
) =>
  upload<MigrationApplied>("/api/profit-loss/migration/imports/apply", file, {
    uploadId,
    expectedRevision,
  });
export const getHistoricalProfitLossImportTemplate = () =>
  downloadB7File("/api/profit-loss/migration/imports/template");
export const getHistoricalProfitLossUploadResult = (uploadId: string) =>
  request<MigrationUploadResult>(
    `/api/uploads/${encodeURIComponent(uploadId)}/result`,
  );
export { saveDownload };
