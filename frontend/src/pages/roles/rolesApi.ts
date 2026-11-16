import { request } from "../../shared/api";
import type { MenuPermission } from "../currentuserops/permissionSettingsApi";
import type { MasterHistoryPage } from "../apiops/masterHistoryApi";
export type DataScope = "ALL" | "PART" | "PROJ";
export type ManagedRole = {
  roleId: string;
  name: string;
  dataScope: DataScope;
  sensitiveView: boolean;
  systemRole: boolean;
  use: boolean;
  sortOrder: number;
  revision: string;
};
export type RolePermission = MenuPermission & {
  parentId: string;
  name: string;
  screenId: string;
  use: boolean;
};
export type ManagedRolesData = {
  items: ManagedRole[];
  developmentMode: true;
  accessEnforcement: false;
  settingsRevision: string;
};
export type ManagedRoleDetail = ManagedRole &
  Omit<ManagedRolesData, "items"> & { menuPermissions: RolePermission[] };
export type RoleSaveResult = {
  value: ManagedRoleDetail;
  revision: string;
  changed: boolean;
};
export type CreateRole =
  | { mode: "NEW"; name: string; dataScope: DataScope }
  | {
      mode: "COPY";
      name: string;
      sourceRoleId: string;
      sourceRevision: string;
    };
export type EditRole = {
  dataScope: DataScope;
  sensitiveView: boolean;
  menuPermissions: MenuPermission[];
  expectedRevision: string;
};
export type RoleMenu = {
  menuId: string;
  parentId: string | null;
  name: string;
  level: number;
  screenId: string | null;
  use: boolean;
};
export const getRoles = () => request<ManagedRolesData>("/api/roles");
export const getRole = (id: string) =>
  request<ManagedRoleDetail>(`/api/roles/${encodeURIComponent(id)}`);
export const createRole = (body: CreateRole) =>
  request<RoleSaveResult>("/api/roles", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const putRole = (id: string, body: EditRole) =>
  request<RoleSaveResult>(`/api/roles/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
export const getRoleMenus = () =>
  request<{ items: RoleMenu[] }>("/api/menu-management");
export const getRoleHistory = (
  id: string,
  page = 1,
  size: 20 | 50 | 100 = 50,
) =>
  request<MasterHistoryPage>(
    `/api/master-history?${new URLSearchParams({ targetType: "ROLE", targetId: id, page: String(page), size: String(size) })}`,
  );
export const stopRole = (id: string, expectedRevision: string) =>
  request<RoleSaveResult>(`/api/roles/${encodeURIComponent(id)}/stop`, {
    method: "POST",
    body: JSON.stringify({ expectedRevision }),
  });
export function roleInput(role: ManagedRoleDetail): EditRole {
  return {
    dataScope: role.dataScope,
    sensitiveView: role.sensitiveView,
    expectedRevision: role.revision,
    menuPermissions: role.menuPermissions.map(
      ({
        menuId,
        inquire,
        register,
        update,
        delete: remove,
        upload,
        download,
        confirm,
      }) => ({
        menuId,
        inquire,
        register,
        update,
        delete: remove,
        upload,
        download,
        confirm,
      }),
    ),
  };
}
