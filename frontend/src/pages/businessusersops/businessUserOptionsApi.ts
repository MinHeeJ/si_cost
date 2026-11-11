import { request } from "../../shared/api";

export type BusinessUserPurpose = "PM" | "PART_LEADER";
export type BusinessUserOption = {
  userId: string;
  name: string;
  label: string;
};
export type BusinessRoleOption = { roleId: "PM" | "SALES_LDR"; name: string };
export type BusinessUserOptions = {
  items: BusinessUserOption[];
  roleOptions: BusinessRoleOption[];
};
export const businessUsersCommittedEvent = "sicost:business-users-committed";
export function notifyBusinessUsersCommitted() {
  window.dispatchEvent(new Event(businessUsersCommittedEvent));
}
export function getBusinessUserOptions(
  purpose: BusinessUserPurpose,
  signal?: AbortSignal,
): Promise<BusinessUserOptions> {
  const query = new URLSearchParams({ purpose });
  return request(`/api/business-users/options?${query}`, {
    signal,
    cache: "no-store",
  });
}
