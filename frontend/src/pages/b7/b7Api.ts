import {
  ApiError,
  type ApiErrorBody,
  type ApiEnvelope,
} from "../../shared/api";

export type B7Summary = {
  totalRows: number;
  successRows: number;
  warningRows: number;
  errorRows: number;
  fileWarningCount: number;
};
export type UploadIssue = {
  sequence: number;
  severity: "ERR" | "WARN";
  rowNumber: number;
  columnName: string;
  inputValue: string;
  reason: string;
  candidates?: { id: string; name: string; businessNo: string | null }[];
};
export type UploadIssuesPage = {
  items: UploadIssue[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type B7Validation = {
  uploadId: number;
  status: "CHECKED" | "APPLIED";
  summary: B7Summary;
  canApply: boolean;
  issues: UploadIssuesPage;
};
export type B7Applied = {
  uploadId: number;
  status: "APPLIED";
  summary: B7Summary;
  appliedCount: number;
  appliedAt: string;
};
export type Download = { blob: Blob; filename: string };
async function response(path: string, options: RequestInit = {}) {
  const result = await fetch(path, { ...options, redirect: "error" });
  if (
    !result.ok ||
    result.headers.get("Content-Type")?.includes("application/json")
  ) {
    const body: unknown = await result.json();
    if (!result.ok) {
      const error = body as ApiErrorBody;
      if (
        error.success === false &&
        typeof error.code === "string" &&
        typeof error.message === "string"
      )
        throw new ApiError(result.status, error);
      throw new Error("서버 요청에 실패했습니다.");
    }
    return { result, body };
  }
  return { result, body: undefined };
}
async function multipart<T>(
  path: string,
  file: File,
  uploadId?: number,
): Promise<T> {
  const form = new FormData();
  form.append("file", file);
  if (uploadId !== undefined) form.append("uploadId", String(uploadId));
  const { body } = await response(path, { method: "POST", body: form });
  const envelope = body as ApiEnvelope<T>;
  if (envelope?.success !== true || !envelope.meta || !envelope.data)
    throw new Error("올바르지 않은 서버 응답입니다.");
  return envelope.data;
}
export const createB7Validation = (file: File) =>
  multipart<B7Validation>("/api/excel/B7/validate", file);
export const saveB7Apply = (file: File, uploadId: number) =>
  multipart<B7Applied>("/api/excel/B7/apply", file, uploadId);
export async function downloadB7File(
  path: string,
  options?: RequestInit,
): Promise<Download> {
  if (!path.startsWith("/api/") || path.includes("\r") || path.includes("\n"))
    throw new Error("API 상대 경로가 필요합니다.");
  const { result, body } = await response(path, options);
  if (
    body !== undefined ||
    !result.headers
      .get("Content-Type")
      ?.includes(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      )
  )
    throw new Error("엑셀 파일 응답을 확인해 주세요.");
  const disposition = result.headers.get("Content-Disposition") ?? "";
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const filename = encoded
    ? decodeURIComponent(encoded)
    : disposition.match(/filename="([^"]+)"/)?.[1];
  if (!filename || /[/\\\r\n]/.test(filename) || !filename.endsWith(".xlsx"))
    throw new Error("다운로드 파일명을 확인해 주세요.");
  return { blob: await result.blob(), filename };
}
export const getB7Template = () => downloadB7File("/api/excel/B7/template");
export const getB7IssuesFile = (uploadId: number) =>
  downloadB7File(`/api/uploads/${uploadId}/issues.xlsx`);
export async function getB7Issues(
  uploadId: number,
  page: number,
): Promise<UploadIssuesPage> {
  const { body } = await response(
    `/api/uploads/${uploadId}/issues?page=${page}&size=50`,
  );
  const envelope = body as ApiEnvelope<UploadIssuesPage>;
  if (envelope?.success !== true || !envelope.data)
    throw new Error("검사 문제를 불러오지 못했습니다.");
  return envelope.data;
}
export function saveDownload(download: Download) {
  const url = URL.createObjectURL(download.blob),
    link = document.createElement("a");
  try {
    link.href = url;
    link.download = download.filename;
    document.body.append(link);
    link.click();
  } finally {
    link.remove();
    URL.revokeObjectURL(url);
  }
}
