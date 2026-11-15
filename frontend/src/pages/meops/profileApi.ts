import { request } from "../../shared/api";

export type MyProfile = {
  userId: string;
  userName: string;
  activeRoles: { roleId: string; roleName: string }[];
  parts: { partId: string; partName: string }[];
  basisMonth: string;
  editGuidance: string;
};

export const getMyProfile = () => request<MyProfile>("/api/me/profile");
