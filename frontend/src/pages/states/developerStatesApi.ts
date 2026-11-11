import { request } from "../../shared/api";

export type DeveloperState = {
  sn: number;
  state: "LEAVE" | "WORK" | "RETIRE";
  startDate: string;
  endDate: string | null;
  reason: string | null;
  actorId: string;
  actorName: string;
  allowedActions: ("EDIT_END" | "CANCEL")[];
};
export type DeveloperStates = {
  items: DeveloperState[];
  hireDate: string | null;
  revision: string;
};
export type StateWarning = { field: string; message: string };
export type DeveloperStateCreated = { sn: number; warnings: StateWarning[] };
export type CreateDeveloperState = {
  state: DeveloperState["state"];
  startDate: string;
  endDate?: string | null;
  reason: string | null;
  expectedRevision: string;
};
const path = (id: string) => `/api/developers/${encodeURIComponent(id)}/states`;
export const getDeveloperStates = (id: string) =>
  request<DeveloperStates>(path(id));
export const createDeveloperState = (id: string, body: CreateDeveloperState) =>
  request<DeveloperStateCreated>(path(id), {
    method: "POST",
    body: JSON.stringify(body),
  });
export const putDeveloperStateEnd = (
  id: string,
  sn: number,
  body: { endDate: string | null; expectedRevision: string },
) =>
  request<{ changed: boolean }>(`${path(id)}/${sn}/end`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
export const saveDeveloperStateCancel = (
  id: string,
  sn: number,
  body: { reason: string | null; expectedRevision: string },
) =>
  request<{ changed: boolean }>(`${path(id)}/${sn}/cancel`, {
    method: "POST",
    body: JSON.stringify(body),
  });
