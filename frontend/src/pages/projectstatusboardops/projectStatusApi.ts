import { ApiError, request, type ApiErrorBody } from "../../shared/api";
import { saveMasterDataDownload } from "../exportsops/masterDataExportApi";
import {
  analysisQuery,
  type AnalysisQuery,
} from "../apiops/projectAnalysisApi";
import type { AmountUnit } from "../../shared/format";
import type { ColumnView } from "../me/preferencesApi";

export type StatusIssue = {
  issueId: number;
  projectId: string;
  projectName: string;
  title: string;
  importance: string;
  importanceName: string;
  dueDate: string;
  status: string;
  statusName: string;
  action: string | null;
  overdue: boolean;
  registeredBy: { userId: string; userName: string };
  registeredAt: string;
  modifiedBy: { userId: string; userName: string } | null;
  modifiedAt: string | null;
};
export type StatusIssueQuery = {
  status: "OPEN_ACTIVE" | "OVERDUE" | "CLOSED" | "ALL";
  importance?: string;
  projectId?: string;
  page: number;
  size: 20 | 50 | 100;
};
export type StatusIssuesPage = {
  items: StatusIssue[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  openCount: number;
  baseDate: string;
};
export type StatusSummaryData = {
  baseDate: string;
  projectCount: number;
  normalCount: number;
  warningCount: number;
  dangerCount: number;
  openIssueCount: number;
  openHighIssueCount: number;
  overdueIssueCount: number;
  staleProjectCount: number;
  unknownCostProjectCount: number;
};
export const getProjectStatusIssues = (
  query: StatusIssueQuery,
  signal?: AbortSignal,
) =>
  request<StatusIssuesPage>(
    `/api/project-status-board/issues?${analysisQuery(query)}`,
    { signal },
  );
export function getProjectStatusBoardSummary(
  query: AnalysisQuery,
  signal?: AbortSignal,
) {
  const { light, contractType, partId } = query;
  return request<StatusSummaryData>(
    `/api/project-status-board/summary?${analysisQuery({ light, contractType, partId })}`,
    { signal },
  );
}
export type StatusDownloadOptions = Partial<ColumnView> & { unit?: AmountUnit };
export async function getProjectAnalysisDownload(
  view: "statusBoard" | "projectCost",
  query: AnalysisQuery,
  options: StatusDownloadOptions = {},
  signal?: AbortSignal,
) {
  const allowed =
    view === "statusBoard"
      ? ["light", "contractType", "partId", "sort", "direction"]
      : [
          "month",
          "projectId",
          "contractType",
          "status",
          "alertOnly",
          "sort",
          "direction",
        ];
  const params = new URLSearchParams();
  for (const key of allowed)
    if (query[key] !== undefined && query[key] !== "")
      params.set(key, String(query[key]));
  if (options.order) params.set("order", options.order.join(","));
  if (options.hidden) params.set("hidden", options.hidden.join(","));
  if (options.unit) params.set("unit", options.unit);
  const mime =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  const response = await fetch(
    `${view === "statusBoard" ? "/api/project-status-board/export" : "/api/project-costs/export"}?${params}`,
    {
      method: "GET",
      redirect: "error",
      cache: "no-store",
      signal,
      headers: { Accept: `${mime}, application/json` },
    },
  );
  const type = response.headers.get("Content-Type")?.split(";")[0].trim();
  if (type === "application/json") {
    const error = (await response.json()) as ApiErrorBody;
    if (
      error.success === false &&
      typeof error.message === "string" &&
      typeof error.code === "string"
    )
      throw new ApiError(response.status, error);
    throw new Error("다운로드 응답을 확인할 수 없습니다.");
  }
  if (!response.ok || type !== mime)
    throw new Error("파일을 내려받지 못했습니다. 다시 시도해 주세요.");
  let filename: string;
  try {
    filename = decodeURIComponent(
      /filename\*=UTF-8''([^;]+)/i.exec(
        response.headers.get("Content-Disposition") ?? "",
      )?.[1] ?? "",
    );
  } catch {
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  }
  const pattern =
    view === "statusBoard"
      ? /^프로젝트현황_[0-9]{8}_[0-9]{6}\.xlsx$/
      : /^프로젝트 원가_[0-9]{6}_[0-9]{8}_[0-9]{6}\.xlsx$/;
  if (!pattern.test(filename))
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  const blob = await response.blob();
  if (!blob.size) throw new Error("다운로드 파일이 비어 있습니다.");
  return { blob, filename };
}
export const saveProjectAnalysisDownload = saveMasterDataDownload;
