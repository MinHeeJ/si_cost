import { request } from "../../shared/api";
import { notifyBusinessUsersCommitted } from "../businessusersops/businessUserOptionsApi";

export type RoleStopTarget = {
  roleId: string;
  name: string;
  systemRole: boolean;
  use: boolean;
  revision: string;
};
export type RoleStopDetail = RoleStopTarget & {
  dataScope: "ALL" | "PART" | "PROJ";
  sensitiveView: boolean;
  sortOrder: number;
  menuPermissions: {
    menuId: string;
    parentId: string;
    name: string;
    screenId: string;
    use: boolean;
    inquire: boolean;
    register: boolean;
    update: boolean;
    delete: boolean;
    upload: boolean;
    download: boolean;
    confirm: boolean;
  }[];
  developmentMode: true;
  accessEnforcement: false;
  settingsRevision: string;
};
export type RoleStopResult = {
  value: RoleStopDetail;
  revision: string;
  changed: boolean;
};
export const roleSettingsCommittedEvent = "sicost:role-settings-committed";
export async function saveRoleStop(roleId: string, expectedRevision: string) {
  const result = await request<RoleStopResult>(
    `/api/roles/${encodeURIComponent(roleId)}/stop`,
    { method: "POST", body: JSON.stringify({ expectedRevision }) },
  );
  if (result.changed) {
    notifyBusinessUsersCommitted();
    window.dispatchEvent(new Event(roleSettingsCommittedEvent));
  }
  return result;
}
