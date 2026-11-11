import { downloadFilename } from "../shared/downloadFilename";
import { request } from "../../shared/api";
import { ApiError } from "../../shared/api";
import type { PeopleAreaPath } from "../../shared/areaClient";

export type CandidateFields = {
  name: string;
  employeeType: string;
  companyId: string | null;
  phone: string | null;
  email: string | null;
  consent: boolean;
  jobCode: string;
  subJobCode: string | null;
  gradeCode: string;
  homeAreaCode: string | null;
  desiredRate: string;
  availableDate: string;
  sourceCode: string;
  memo: string | null;
  skills: string[];
};
export type CandidateItem = {
  id: string;
  name: string;
  employeeType: string;
  companyName: string | null;
  job: string;
  grade: string;
  skills: string[];
  homeAddress: string | null;
  desiredRate: string;
  availableDate: string;
  contactStatus: "BEFORE" | "DONE" | "ITVW" | "HOLD" | "CTRT";
  lastContactDate: string | null;
  source: string;
  revision: string;
};
export type CandidateDetail = CandidateItem &
  CandidateFields & {
    addressPath: PeopleAreaPath;
    consentDate: string | null;
    consentUserId: string | null;
    consentUserName: string | null;
  };
export type CandidateFilters = {
  jobCode?: string;
  gradeCode?: string;
  employeeType?: string;
  contactStatus?: string;
  availability?: string;
  page: number;
  size: 20 | 50 | 100;
  sort?: string;
  direction?: "ASC" | "DESC";
};
export type CandidatesPage = {
  items: CandidateItem[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type CandidateCreated = {
  id: string;
  warnings?: { field: string; message: string }[];
};
export type CandidateCode = { code: string; name: string };
const path = (id: string) => `/api/candidates/${encodeURIComponent(id)}`;
export function getCandidates(filters: CandidateFilters, signal?: AbortSignal) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return request<CandidatesPage>(`/api/candidates?${query}`, { signal });
}
export const getCandidate = (id: string, signal?: AbortSignal) =>
  request<CandidateDetail>(path(id), { signal });
export const createCandidate = (body: CandidateFields) =>
  request<CandidateCreated>("/api/candidates", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const putCandidate = (
  id: string,
  body: CandidateFields & { expectedRevision: string },
) =>
  request<{ changed: boolean }>(path(id), {
    method: "PUT",
    body: JSON.stringify(body),
  });
export const getCandidateCodes = (group: string) =>
  request<{ items: CandidateCode[] }>(
    `/api/codes/${encodeURIComponent(group)}`,
  );
export const getCandidateDate = () =>
  request<{ currentDate: string }>("/api/period-context");
export async function downloadCandidates(
  filters: CandidateFilters,
  columns: string[],
  amountUnit: string,
) {
  const { page: _page, size: _size, sort, direction, ...conditions } = filters;
  const response = await fetch("/api/exports/master-data", {
    method: "POST",
    redirect: "error",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      view: "CANDIDATES",
      filters: conditions,
      columns,
      sort,
      direction,
      amountUnit,
    }),
  });
  if (!response.ok) throw new ApiError(response.status, await response.json());
  if (!response.headers.get("Content-Type")?.includes("spreadsheetml.sheet"))
    throw new Error("다운로드 형식이 올바르지 않습니다.");
  const url = URL.createObjectURL(await response.blob()),
    anchor = document.createElement("a");
  try {
    anchor.href = url;
    anchor.download = downloadFilename(response, "외부인력후보.xlsx");
    anchor.click();
  } finally {
    URL.revokeObjectURL(url);
  }
}
