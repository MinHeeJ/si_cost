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

export type FinancialUploadResult = {
  uploadId: string;
  formType: "B2";
  screenId: "UI-10";
  month: string;
  status: "CHECKED" | "APPLIED";
  summary: {
    totalRows: number;
    successRows: number;
    warningRows: number;
    errorRows: number;
    replaceCount: number;
    preserveCount: number;
  };
  canApply: boolean;
  expectedRevision: string;
  changed: boolean;
  appliedAt: string | null;
  issues: UploadIssuesPage;
  items: { rowNumber: number; itemSn: string }[];
};
async function upload(
  path: string,
  file: File,
  month: string,
  context?: { uploadId: string; expectedRevision: string },
) {
  const body = new FormData();
  body.append("file", file);
  body.append("month", month);
  if (context) {
    body.append("uploadId", context.uploadId);
    body.append("expectedRevision", context.expectedRevision);
  }
  const response = await fetch(path, {
    method: "POST",
    body,
    redirect: "error",
    headers: { Accept: "application/json" },
  });
  const envelope = (await response.json()) as
    | ApiEnvelope<FinancialUploadResult>
    | ApiErrorBody;
  if (!response.ok) {
    if (envelope.success === false)
      throw new ApiError(response.status, envelope);
    throw new Error("서버 응답을 확인해 주세요.");
  }
  if (
    envelope.success !== true ||
    !envelope.data ||
    typeof envelope.data.uploadId !== "string"
  )
    throw new Error("업로드 결과를 확인해 주세요.");
  return envelope.data;
}
export const createB2Validation = (file: File, month: string) =>
  upload("/api/excel/B2/validate", file, month);
export const saveB2Apply = (
  file: File,
  month: string,
  uploadId: string,
  expectedRevision: string,
) => upload("/api/excel/B2/apply", file, month, { uploadId, expectedRevision });
export const getFinancialUploadResult = (uploadId: string) =>
  request<FinancialUploadResult>(`/api/uploads/${uploadId}/result`);
export const getB2Template = (month: string) =>
  downloadB7File(`/api/excel/B2/template?${new URLSearchParams({ month })}`);
export { saveDownload };
