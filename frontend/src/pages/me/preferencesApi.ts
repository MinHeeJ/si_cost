import { request } from "../../shared/api";

export type PreferenceType =
  | "FAV"
  | "RECENT"
  | "MENU_FOLD"
  | "PAGE_SIZE"
  | "DENSE"
  | "COLS"
  | "AMT_UNIT"
  | "PERIOD";
export type ColumnView = { order: string[]; hidden: string[] };
export type ColumnPreferences = Partial<
  Record<
    "codes" | "valueCodes" | "mappings" | "standards" | "history",
    ColumnView
  >
>;
export type ScreenColumnPreferences = ColumnPreferences &
  Partial<
    Record<"users" | "clients" | "developers" | "candidates", ColumnView>
  >;
export type PeriodPreference =
  | { type: "YEAR" | "YEAR_MONTH"; value: string }
  | { type: "YEAR_CUM"; year: string; month: string };
export type PreferenceValue =
  | string[]
  | boolean
  | number
  | string
  | ScreenColumnPreferences
  | PeriodPreference
  | null;
export type Preference = {
  type: PreferenceType;
  key: string;
  value: PreferenceValue;
  revision: string | null;
  stored: boolean;
  error?: { code: string; message: string };
};
export type PreferenceSaveResult = {
  changed: boolean;
  preference: Preference;
  message?: string;
};
export interface PreferenceApi {
  getPreferences(): Promise<{ items: Preference[] }>;
  putPreference(
    type: PreferenceType,
    key: string,
    value: PreferenceValue,
    expectedRevision: string | null,
  ): Promise<PreferenceSaveResult>;
  deletePreference(
    type: PreferenceType,
    key: string,
    expectedRevision: string,
  ): Promise<void>;
}
const target = (type: PreferenceType, key: string) =>
  `/api/me/preferences/${encodeURIComponent(type)}/${encodeURIComponent(key)}`;
export const getPreferences: PreferenceApi["getPreferences"] = () =>
  request("/api/me/preferences");
export const putPreference: PreferenceApi["putPreference"] = (
  type,
  key,
  value,
  expectedRevision,
) =>
  request(target(type, key), {
    method: "PUT",
    body: JSON.stringify({ value, expectedRevision }),
  });
export const deletePreference: PreferenceApi["deletePreference"] = (
  type,
  key,
  expectedRevision,
) =>
  request(
    `${target(type, key)}?expectedRevision=${encodeURIComponent(expectedRevision)}`,
    { method: "DELETE" },
  );
export const preferencesApi: PreferenceApi = {
  getPreferences,
  putPreference,
  deletePreference,
};
