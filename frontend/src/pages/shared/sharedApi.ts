import { request } from "../../shared/api";
export type MenuNode = {
  menuId: string;
  parentId: string | null;
  name: string;
  screenId: string | null;
  sortOrder: number;
  icon: string | null;
  pcOnly: boolean;
  children: MenuNode[];
};
export type MenusData = {
  items: MenuNode[];
  diagnostics: { menuId: string; message: string }[];
};
export type CurrentUser = {
  userId: string;
  name: string;
  roleIds: string[];
  developmentMode: boolean;
};
export type MonthClose = { month: string; status: string };
export type PeriodContext = {
  currentDate: string;
  currentMonth: string;
  timezone: string;
  cashBaseMonth: string;
  monthClose: MonthClose;
};
export type PeriodValue =
  | { type: "YEAR" | "YEAR_MONTH"; value: string }
  | { type: "YEAR_CUM"; year: string; month: string };
export type SharedPreference = {
  type: string;
  key: string;
  value: unknown;
  stored: boolean;
  revision: string | null;
  error?: { code: string; message: string };
};
export type HistoryTarget =
  | "GROUP"
  | "CODE"
  | "VALUE_CODE"
  | "OPER_STD"
  | "CODE_MAP";
export type CommonCodeHistory = {
  histId: number;
  occurredAt: string;
  target: string;
  groupId: string | null;
  entityKey: string;
  type: string;
  item: string;
  before: string | null;
  after: string | null;
  actor: string;
};
export type HistoryQuery = {
  targetType?: string;
  groupId?: string;
  page: number;
  size: 20 | 50 | 100;
};
export type HistoryPage = {
  items: CommonCodeHistory[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export const getMenus = () => request<MenusData>("/api/menus");
export const getCurrentUser = () => request<CurrentUser>("/api/current-user");
export const getPeriodContext = () =>
  request<PeriodContext>("/api/period-context");
export const getMonthClose = (from: string, to: string) =>
  request<{ items: MonthClose[] }>(
    `/api/month-close?${new URLSearchParams({ from, to })}`,
  );
export const getSharedPreferences = () =>
  request<{ items: SharedPreference[] }>("/api/me/preferences");
export const putSharedPreference = (
  type: string,
  key: string,
  value: unknown,
  expectedRevision: string | null,
) =>
  request<{ changed: boolean; preference: SharedPreference }>(
    `/api/me/preferences/${encodeURIComponent(type)}/${encodeURIComponent(key)}`,
    { method: "PUT", body: JSON.stringify({ value, expectedRevision }) },
  );
export const deleteSharedPreference = (
  type: string,
  key: string,
  expectedRevision: string,
) =>
  request<void>(
    `/api/me/preferences/${encodeURIComponent(type)}/${encodeURIComponent(key)}?${new URLSearchParams({ expectedRevision })}`,
    { method: "DELETE" },
  );
export function getCommonCodeHistory(query: HistoryQuery) {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  if (query.targetType) params.set("targetType", query.targetType);
  if (query.groupId) params.set("groupId", query.groupId);
  return request<HistoryPage>(`/api/common-code-history?${params}`);
}
export type SharedCodeLookup = {
  code: string;
  name: string;
  addVal1: string | null;
  addVal2: string | null;
  addVal3: string | null;
  codeVal: string | null;
  unitNm: string | null;
};
export const getSharedCodes = (group: string, includeInactive = false) =>
  request<{ items: SharedCodeLookup[] }>(
    `/api/codes/${encodeURIComponent(group)}?includeInactive=${includeInactive}`,
  );
export const getSharedParameter = (key: string, month?: string) =>
  request<{
    key: string;
    value: string;
    unitNm: string | null;
    effectiveMonth: string;
  }>(
    `/api/params/${encodeURIComponent(key)}${month ? `?${new URLSearchParams({ month })}` : ""}`,
  );
