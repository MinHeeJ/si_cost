import { useContext } from "react";
import { useFeaturePermission } from "../../shared/permission/PermissionBoundary";
import { SharedScreenContext } from "../shared/SharedScreenContext";

export function useClientWriteScope() {
  const managePartners = useFeaturePermission("managePartners");
  const { roleIds } = useContext(SharedScreenContext);
  return {
    managePartners,
    manageCustomers: managePartners || Boolean(roleIds?.includes("SALES_LDR")),
  };
}
