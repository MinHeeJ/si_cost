import { request } from "../shared/api";

export { request, ApiError } from "../shared/api";
export type { ApiEnvelope, ApiErrorBody } from "../shared/api";
export {
  getCurrentUser,
  getMenus,
  getPeriodContext,
  getMonthClose,
  getCommonCodeHistory,
  getSharedCodes as getCodes,
  getSharedParameter as getParameter,
} from "../pages/shared/sharedApi";
export {
  getPreferences,
  putPreference,
  deletePreference,
} from "../pages/me/preferencesApi";
export {
  getCodeGroups,
  createCodeGroup,
  getGroupCodes,
  saveGroupCodes,
  deleteGroupCode,
} from "../pages/codegroups/codeGroupsApi";
export {
  getCodeMappings,
  createCodeMapping,
  deleteCodeMappings,
} from "../pages/codemappings/codeMappingsApi";
export {
  getOperatingStandards,
  saveOperatingStandards,
  getOperatingStandardHistory,
} from "../pages/operatingstandards/operatingStandardsApi";

export const getHealth = () => request<void>("/api/health");

export {
  getBusinessUsers,
  getBusinessUser,
  createBusinessUser,
  putBusinessUser,
} from "../pages/businessusers/businessUsersApi";
export { getBusinessUserOptions } from "../pages/businessusersops/businessUserOptionsApi";
export {
  getClients,
  getClient,
  createClient,
  putClient,
} from "../pages/clients/clientsApi";
export {
  getClientOptions,
  getClientSelection,
  getClientAccount,
  getClientAssignments,
  saveClientBulkStatus,
} from "../pages/clientsops/clientActionsApi";
export {
  getSalesParts,
  getSalesPart,
  createSalesPart,
  putSalesPart,
} from "../pages/salesparts/salesPartsApi";
export {
  getSalesPartDefaults,
  getSalesPartOptions,
  createSalesPartStandard,
} from "../pages/salespartsops/partStandardsApi";
export { getMasterHistory } from "../pages/apiops/masterHistoryApi";
export {
  getB7Template,
  createB7Validation,
  saveB7Apply,
} from "../pages/b7/b7Api";
export {
  getUploadIssues,
  getUploadIssuesXlsx,
} from "../pages/uploadsops/uploadIssuesApi";
export { createMasterDataExport } from "../pages/exportsops/masterDataExportApi";
