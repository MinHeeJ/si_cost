import { ApiError, request } from "../../shared/api";
import { downloadFilename } from "../shared/downloadFilename";
import { getProjects } from "../projects/projectsApi";

export type Difference = "ALL" | "SHORT" | "EXCESS" | "MATCH";
export type ComparisonFilter = {
  year: number;
  projectId?: string;
  difference: Difference;
};
export type ComparisonRow = {
  projectId: string;
  projectName: string;
  jobCode: string;
  planMm: string;
  actualMm: string;
  differenceMm: string;
};
export type ComparisonPage = {
  items: ComparisonRow[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  year: number;
  totals: Pick<ComparisonRow, "planMm" | "actualMm" | "differenceMm">;
};
export type ComparisonQuery = ComparisonFilter & {
  page: number;
  size: 20 | 50 | 100;
  sort?: "differenceMm" | "projectId" | "jobCode";
  direction?: "ASC" | "DESC";
};
function query(values: object) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return params;
}
export const getStaffingComparison = (
  filters: ComparisonQuery,
  signal?: AbortSignal,
) =>
  request<ComparisonPage>(`/api/staffing-plan-comparison?${query(filters)}`, {
    signal,
  });
export async function comparisonProjects(year: number, signal?: AbortSignal) {
  const items: { id: string; name: string }[] = [];
  let page = 1;
  for (;;) {
    const result = await getProjects({ year, page, size: 100 }, signal);
    items.push(...result.items);
    if (page * result.size >= result.total || result.items.length === 0)
      return items;
    page++;
  }
}
export async function downloadStaffingComparison(
  filters: ComparisonFilter,
  amountUnit: "WON" | "THOUSAND" | "MILLION",
  signal: AbortSignal,
) {
  const response = await fetch(
    `/api/staffing-plan-comparison/export?${query({ year: filters.year, projectId: filters.projectId, difference: filters.difference, amountUnit })}`,
    {
      signal,
      redirect: "error",
      headers: {
        Accept:
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      },
    },
  );
  if (!response.ok) throw new ApiError(response.status, await response.json());
  if (
    !response.headers
      .get("Content-Type")
      ?.startsWith(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      )
  )
    throw new Error("엑셀 응답을 확인할 수 없습니다.");
  return {
    blob: await response.blob(),
    filename: downloadFilename(response, "계획대비투입.xlsx"),
  };
}
export function saveStaffingComparison(file: { blob: Blob; filename: string }) {
  const url = URL.createObjectURL(file.blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = file.filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
