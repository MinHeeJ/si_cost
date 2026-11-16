import { request } from "../../shared/api";

export type MenuPermission = {
  menuId: string;
  inquire: boolean;
  register: boolean;
  update: boolean;
  delete: boolean;
  upload: boolean;
  download: boolean;
  confirm: boolean;
};
export type CurrentPermissionSettings = {
  userId: string;
  roleIds: string[];
  menuPermissions: MenuPermission[];
  sensitiveView: boolean;
  rowScope: { all: boolean; partIds: string[]; projectIds: string[] };
  assignedParts: { partId: string; name: string; headOffice: boolean }[];
  assignedProjects: { projectId: string; scopeSn: number }[];
  developmentMode: true;
  accessEnforcement: false;
  settingsRevision: string;
};
export const getCurrentUserPermissionSettings = () =>
  request<CurrentPermissionSettings>("/api/current-user/permission-settings");
