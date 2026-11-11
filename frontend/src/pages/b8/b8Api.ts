import {
  ApiError,
  type ApiEnvelope,
  type ApiErrorBody,
} from "../../shared/api";
import {
  downloadB7File,
  type B7Summary,
  type UploadIssuesPage,
} from "../b7/b7Api";
export { saveDownload } from "../b7/b7Api";
export type B8NumberRow = {
  rowNumber: number;
  managementNo: string;
  id?: string;
};
export type B8Validation = {
  uploadId: number;
  summary: B7Summary;
  canApply: boolean;
  issues: UploadIssuesPage;
  expectedNumbers: B8NumberRow[];
};
export type B8Applied = { summary: B7Summary; finalNumbers: B8NumberRow[] };
async function send<T>(
  path: string,
  file: File,
  uploadId?: number,
): Promise<T> {
  const body = new FormData();
  body.append("file", file);
  if (uploadId !== undefined) body.append("uploadId", String(uploadId));
  const response = await fetch(path, {
    method: "POST",
    body,
    redirect: "error",
  });
  const payload: unknown = await response.json();
  if (!response.ok) {
    const error = payload as ApiErrorBody;
    if (
      error.success === false &&
      typeof error.code === "string" &&
      typeof error.message === "string"
    )
      throw new ApiError(response.status, error);
    throw new Error("서버 요청에 실패했습니다.");
  }
  const envelope = payload as ApiEnvelope<T>;
  if (envelope.success !== true || !envelope.meta || !envelope.data)
    throw new Error("올바르지 않은 서버 응답입니다.");
  return envelope.data;
}
export const getB8Template = () => downloadB7File("/api/excel/B8/template");
export const createB8Validation = (file: File) =>
  send<B8Validation>("/api/excel/B8/validate", file);
export const saveB8Apply = (file: File, uploadId: number) =>
  send<B8Applied>("/api/excel/B8/apply", file, uploadId);
