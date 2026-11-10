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
