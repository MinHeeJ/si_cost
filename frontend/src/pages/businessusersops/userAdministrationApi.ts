import { request } from "../../shared/api";
import type { BusinessUserSaveResult } from "../businessusers/businessUsersApi";

export type AssignedPart = {
  partId: string;
  name: string;
  headOffice: boolean;
};
export type UserScopes = {
  userId: string;
  assignedParts: AssignedPart[];
  assignedProjects: { projectId: string; scopeSn: number }[];
  revision: string;
};
export type UserActivity = {
  logId: number;
  userId: string;
  actedAt: string;
  actionType: string;
  screenId: string;
  targetDescription: string;
};
export type UserActivities = {
  items: UserActivity[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type UserStateAction = "STOP" | "RESUME" | "UNLOCK";
export type UserStateItem = { userId: string; expectedRevision: string };
export type UserStateResult = {
  userId: string;
  outcome: "APPLIED" | "UNCHANGED" | "REJECTED";
  code: string;
  reason: string;
  changed: boolean;
  revision: string | null;
};
export type UserStateResults = {
  applied: number;
  unchanged: number;
  rejected: number;
  items: UserStateResult[];
};
const userPath = (id: string) =>
  `/api/business-users/${encodeURIComponent(id)}`;
export const getBusinessUserScopes = (id: string, signal?: AbortSignal) =>
  request<UserScopes>(`${userPath(id)}/scopes`, { signal, cache: "no-store" });
export const getBusinessUserAccessLogs = (
  id: string,
  page = 1,
  size: 20 | 50 | 100 = 50,
  signal?: AbortSignal,
) =>
  request<UserActivities>(
    `${userPath(id)}/access-logs?${new URLSearchParams({ page: String(page), size: String(size) })}`,
    { signal, cache: "no-store" },
  );
export const changeBusinessUserState = (
  id: string,
  action: UserStateAction,
  expectedRevision: string,
) =>
  request<BusinessUserSaveResult>(`${userPath(id)}/${action.toLowerCase()}`, {
    method: "POST",
    body: JSON.stringify({ expectedRevision }),
  });
export const saveBusinessUserStatusChanges = (
  action: "STOP" | "RESUME",
  items: UserStateItem[],
) =>
  request<UserStateResults>("/api/business-users/status-changes", {
    method: "POST",
    body: JSON.stringify({ action, items }),
  });
