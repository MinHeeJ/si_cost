import { request } from "../../shared/api";
import type { PeopleAreaPath } from "../../shared/areaClient";

export type Warning = { field: string; message: string };
export type DeveloperPrice = {
  effectiveMonth: string;
  endMonth: string | null;
  monthlyRate: string | null;
  monthlyPay: string | null;
};
export type DeveloperItem = {
  id: string;
  managementNo: string;
  name: string;
  companyId: string | null;
  companyName: string | null;
  employeeType: string;
  grade: string | null;
  job: string;
  status: string;
  useYn: "Y" | "N";
  retireDate: string | null;
  retirementScheduled: boolean;
  currentProjects: { id: string; name: string }[];
  currentProjectLabel: string | null;
  withdrawalDate: string | null;
  monthlyMm: string;
  mmOver: string;
  warnings: Warning[];
  monthlyRate: string | null;
  revision: string;
};
export type DeveloperDetail = DeveloperItem & {
  gradeCode: string | null;
  jobCode: string;
  subJobCode: string | null;
  positionCode: string | null;
  directClass: string;
  partId: string;
  hireDate: string | null;
  homeAreaCode: string | null;
  homeAddress: string | null;
  monthlyPay: string | null;
  skills: string[];
  addressPath: PeopleAreaPath;
  priceHistory: DeveloperPrice[];
  editPrice: DeveloperPrice | null;
};
export type DeveloperFields = {
  name: string;
  employeeType: string;
  companyId: string | null;
  jobCode: string;
  directClass: string;
  partId: string;
  gradeCode?: string | null;
  subJobCode?: string | null;
  positionCode?: string | null;
  hireDate?: string | null;
  monthlyRate?: string | null;
  monthlyPay?: string | null;
  homeAreaCode?: string | null;
  skills?: string[];
};
export type DeveloperFilters = {
  employeeType?: string;
  jobCode?: string;
  directClass?: string;
  workStatus?: string;
  q?: string;
  includeStopped?: boolean;
  page: number;
  size: 20 | 50 | 100;
  sort?: string;
  direction?: "ASC" | "DESC";
};
export type DevelopersPage = {
  items: DeveloperItem[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type DeveloperCreated = {
  id: string;
  managementNo: string;
  warnings: Warning[];
};
const path = (id: string) => `/api/developers/${encodeURIComponent(id)}`;
export function getDevelopers(filters: DeveloperFilters, signal?: AbortSignal) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return request<DevelopersPage>(`/api/developers?${query}`, { signal });
}
export const getDeveloper = (id: string, signal?: AbortSignal) =>
  request<DeveloperDetail>(path(id), { signal });
export const createDeveloper = (
  body: DeveloperFields & { managementNo?: string },
) =>
  request<DeveloperCreated>("/api/developers", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const putDeveloper = (
  id: string,
  body: DeveloperFields & { expectedRevision: string },
) =>
  request<{ changed: boolean }>(path(id), {
    method: "PUT",
    body: JSON.stringify(body),
  });
export const getPeopleCodes = (group: string) =>
  request<{ items: { code: string; name: string }[] }>(
    `/api/codes/${encodeURIComponent(group)}`,
  );
export type Assignment = {
  id: string;
  projectId: string;
  projectName: string;
  jobName: string;
  startDate: string;
  endDate: string;
  contractMm: string;
  assignmentStatus: string;
};
export type DeveloperState = {
  sn: number;
  state: string;
  startDate: string;
  endDate: string | null;
  reason: string | null;
  actorName: string;
  allowedActions: string[];
};
export const getDeveloperAssignments = (id: string, page = 1) =>
  request<{ items: Assignment[]; total: number; page: number; size: 50 }>(
    `${path(id)}/assignments?page=${page}&size=50`,
  );
export const getDeveloperStates = (id: string) =>
  request<{
    items: DeveloperState[];
    hireDate: string | null;
    revision: string;
  }>(`${path(id)}/states`);
