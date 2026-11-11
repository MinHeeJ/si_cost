import { request } from "../../shared/api";
import { getBusinessUserOptions } from "../businessusersops/businessUserOptionsApi";

export type BusinessRole = "PM" | "SALES_LDR";
export type BusinessUser = {
  userId: string;
  name: string;
  email: string;
  roles: string[];
  businessRoleIds: BusinessRole[];
  status: "USE" | "LOCK" | "STOP";
  lastLoginAt: string | null;
  revision: string;
};
export type BusinessUsersPage = {
  items: BusinessUser[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type BusinessUserFilters = {
  q?: string;
  businessRole?: BusinessRole;
  status?: BusinessUser["status"];
  page?: number;
  size?: 20 | 50 | 100;
  sort?: string;
  direction?: "ASC" | "DESC";
};
export type BusinessUserCreate = {
  userId: string;
  name: string;
  email: string;
  businessRoleIds: BusinessRole[];
};
export type BusinessUserEdit = Omit<BusinessUserCreate, "userId"> & {
  expectedRevision: string;
};
export type BusinessUserSaveResult = {
  changed: boolean;
  message: string;
  user: BusinessUser;
};
export type BusinessRoleOption = { roleId: BusinessRole; name: string };
export type BusinessUserHistory = {
  historyId: number;
  changeType: string;
  itemName: string;
  beforeValue: string | null;
  afterValue: string | null;
  reason: string | null;
  actorId: string;
  actorName: string;
  createdAt: string;
};
export type BusinessUserHistoryPage = {
  items: BusinessUserHistory[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export const getBusinessUsers = (filters: BusinessUserFilters = {}) => {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return request<BusinessUsersPage>(
    `/api/business-users${query.size ? `?${query}` : ""}`,
  );
};
export const getBusinessUser = (id: string) =>
  request<BusinessUser>(`/api/business-users/${encodeURIComponent(id)}`);
export const createBusinessUser = (body: BusinessUserCreate) =>
  request<BusinessUserSaveResult>("/api/business-users", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const putBusinessUser = (id: string, body: BusinessUserEdit) =>
  request<BusinessUserSaveResult>(
    `/api/business-users/${encodeURIComponent(id)}`,
    { method: "PUT", body: JSON.stringify(body) },
  );
export const getBusinessRoleOptions = () => getBusinessUserOptions("PM");
export const getBusinessUserHistory = (
  id: string,
  page = 1,
  size: 20 | 50 | 100 = 50,
) =>
  request<BusinessUserHistoryPage>(
    `/api/master-history?${new URLSearchParams({ targetType: "USER", targetId: id, page: String(page), size: String(size) })}`,
  );
export const notifyBusinessUsersCommitted = () =>
  window.dispatchEvent(new CustomEvent("sicost:business-users-committed"));
