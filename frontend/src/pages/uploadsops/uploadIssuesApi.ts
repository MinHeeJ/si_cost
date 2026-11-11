import { request } from "../../shared/api";
import {
  downloadB7File,
  saveDownload,
  type UploadIssue,
  type UploadIssuesPage,
} from "../b7/b7Api";

export type Severity = "" | "ERR" | "WARN";
export type { UploadIssue, UploadIssuesPage };
export function getUploadIssues(
  uploadId: number,
  severity: Severity,
  page: number,
  size: 20 | 50 | 100,
) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (severity) query.set("severity", severity);
  return request<UploadIssuesPage>(`/api/uploads/${uploadId}/issues?${query}`);
}
export function getUploadIssuesXlsx(uploadId: number, severity: Severity) {
  return downloadB7File(
    `/api/uploads/${uploadId}/issues.xlsx${severity ? `?severity=${severity}` : ""}`,
  );
}
export { saveDownload };
