import { request } from "../../shared/api";

export type UserProjectListItem = {
  id: string;
  name: string;
  contractType: "TK" | "MO" | "DP" | "IN";
  startDate: string;
  endDate: string;
  contractAmount: string | null;
  status: "ING" | "PRE" | "END" | "LOSE";
  customerName: string | null;
  personCount: number;
  partName: string | null;
  distribution: {
    selectedStep: "EST" | "CTRT" | "PLAN" | null;
    hasTable: boolean;
    totalAmount: string;
    contractDifference: string;
    mismatchCount: number;
  } | null;
};
export type UserProjectsData = {
  userId: string;
  items: UserProjectListItem[];
  revision: string;
};
export type UserProjectPm = { userId: string; name: string; scopeSn: number };
export type UserProjectPmData = { items: UserProjectPm[]; revision: string };
export type UserProjectPmResult = {
  value: UserProjectPmData;
  revision: string;
  changed: boolean;
};
export type UserProjectSearch = {
  year: number;
  status: "ING" | "PRE";
  q?: string;
  page: number;
};
export type UserProjectSearchPage = {
  items: UserProjectListItem[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  year: number;
  unassignedCount: number;
};
export const userProjectsCommittedEvent = "sicost:user-projects-committed";
export const getBusinessUserProjects = (userId: string, signal?: AbortSignal) =>
  request<UserProjectsData>(
    `/api/business-users/${encodeURIComponent(userId)}/projects`,
    { signal, cache: "no-store" },
  );
export const getUserProjectPm = (projectId: string, signal?: AbortSignal) =>
  request<UserProjectPmData>(
    `/api/projects/${encodeURIComponent(projectId)}/pm`,
    { signal, cache: "no-store" },
  );
export const searchUserProjects = (
  filters: UserProjectSearch,
  signal?: AbortSignal,
) => {
  const query = new URLSearchParams({
    year: String(filters.year),
    status: filters.status,
    page: String(filters.page),
    size: "20",
  });
  if (filters.q?.trim()) query.set("q", filters.q.trim());
  return request<UserProjectSearchPage>(`/api/projects?${query}`, {
    signal,
    cache: "no-store",
  });
};
export const assignUserProject = (
  projectId: string,
  userId: string,
  expectedRevision: string,
  replaceConfirmed = false,
) =>
  request<UserProjectPmResult>(
    `/api/projects/${encodeURIComponent(projectId)}/pm`,
    {
      method: "PUT",
      body: JSON.stringify({ userId, expectedRevision, replaceConfirmed }),
    },
  );
export const detachUserProject = (
  projectId: string,
  userId: string,
  expectedRevision: string,
) =>
  request<UserProjectPmResult>(
    `/api/projects/${encodeURIComponent(projectId)}/pm`,
    {
      method: "DELETE",
      body: JSON.stringify({ userId, expectedRevision }),
    },
  );
