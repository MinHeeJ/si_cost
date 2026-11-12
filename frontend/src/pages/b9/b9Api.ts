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
  type B7Summary,
} from "../b7/b7Api";

export type ProjectUploadSummary = B7Summary & { tableWarningCount: number };
export type ProjectUploadValidation = {
  uploadId: number;
  summary: ProjectUploadSummary;
  issues: UploadIssuesPage;
  canApply: boolean;
  revision: string | null;
  prospectiveComparison: unknown | null;
  uploadedTableTotal: string | null;
};
export type ProjectUploadResult = {
  uploadId: number;
  formType: "B9" | "B6";
  status: "CHECKED" | "APPLIED";
  projectId: string | null;
  step: "EST" | "CTRT" | "PLAN" | null;
  summary: ProjectUploadSummary;
  results: { rowNumber: number; id: string; name: string }[];
  revision: string | null;
  changed: boolean;
};
async function send<T>(
  path: string,
  file: File,
  year: number,
  uploadId?: number,
): Promise<T> {
  const body = new FormData();
  body.append("file", file);
  body.append("year", String(year));
  if (uploadId !== undefined) body.append("uploadId", String(uploadId));
  const response = await fetch(path, {
    method: "POST",
    body,
    redirect: "error",
    headers: { Accept: "application/json" },
  });
  const value: unknown = await response.json();
  if (!response.ok) {
    const error = value as ApiErrorBody;
    if (
      error.success === false &&
      typeof error.message === "string" &&
      typeof error.code === "string"
    )
      throw new ApiError(response.status, error);
    throw new Error("서버 요청에 실패했습니다. 파일과 검사 번호를 유지합니다.");
  }
  const envelope = value as ApiEnvelope<T>;
  if (envelope.success !== true || !envelope.meta || !envelope.data)
    throw new Error("올바르지 않은 서버 응답입니다.");
  return envelope.data;
}
export const createB9Validation = (file: File, year: number) =>
  send<ProjectUploadValidation>("/api/excel/B9/validate", file, year);
export const saveB9Apply = (file: File, uploadId: number, year: number) =>
  send<ProjectUploadResult>("/api/excel/B9/apply", file, year, uploadId);
export const getB9Template = () => downloadB7File("/api/excel/B9/template");
export const getUploadResult = (uploadId: number) =>
  request<ProjectUploadResult>(`/api/uploads/${uploadId}/result`);
export { saveDownload };
