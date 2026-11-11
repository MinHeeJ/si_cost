import { request } from "../../shared/api";
import {
  createMasterDataExport,
  partExportColumns,
  saveMasterDataDownload,
} from "../exportsops/masterDataExportApi";
export type SalesPartStandard = {
  effectiveMonth: string;
  endMonth: string | null;
  leaderUserId: string;
  leaderName: string;
  profitRate: string;
  salesExpenseAmount: string;
  status: "PAST" | "CURRENT" | "FUTURE";
};
export type SalesPart = {
  id: string;
  name: string;
  useYn: "Y" | "N";
  currentMonth: string;
  currentStandard: SalesPartStandard | null;
  scheduledChange: SalesPartStandard | null;
  personCount: number;
  projectCount: number;
  revision: string;
};
export type SalesPartDetail = {
  part: SalesPart;
  standards: SalesPartStandard[];
  minMonth: string;
  revision: string;
};
export type SalesPartsPage = {
  items: SalesPart[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type SalesPartCreate = {
  name: string;
  effectiveMonth: string;
  leaderUserId: string;
  profitRate: string;
  salesExpenseAmount: string;
};
export type SalesPartPatch = (
  | { name: string; useYn?: never }
  | { useYn: "Y" | "N"; name?: never }
) & { expectedRevision: string };
export type SalesPartSaveResult = {
  changed: boolean;
  message: string;
  part: SalesPartDetail;
};
export type SalesPartQuery = {
  q?: string;
  useYn?: "Y" | "N";
  page: number;
  size: 20 | 50 | 100;
  sort: string;
  direction: "ASC" | "DESC";
};
export type SalesPartDefaults = {
  effectiveMonth: string;
  minMonth: string;
  profitRate: string | null;
  salesExpenseAmount: string | null;
  defaultErrors: { source: string; field: string; message: string }[];
};
export const salesPartsCommittedEvent = "sicost:sales-parts-committed";
export function notifySalesPartsCommitted() {
  window.dispatchEvent(new Event(salesPartsCommittedEvent));
}
export function getSalesParts(query: SalesPartQuery, signal?: AbortSignal) {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return request<SalesPartsPage>(`/api/sales-parts?${params}`, {
    signal,
    cache: "no-store",
  });
}
export const getSalesPart = (id: string) =>
  request<SalesPartDetail>(`/api/sales-parts/${encodeURIComponent(id)}`, {
    cache: "no-store",
  });
export const createSalesPart = (body: SalesPartCreate) =>
  request<SalesPartSaveResult>("/api/sales-parts", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const putSalesPart = (id: string, body: SalesPartPatch) =>
  request<SalesPartSaveResult>(`/api/sales-parts/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
export function getSalesPartDefaults(month?: string) {
  return request<SalesPartDefaults>(
    `/api/sales-parts/defaults${month ? `?effectiveMonth=${encodeURIComponent(month)}` : ""}`,
    { cache: "no-store" },
  );
}
export async function downloadSalesParts(
  query: SalesPartQuery,
  amountUnit: "원" | "천원" | "백만원",
) {
  saveMasterDataDownload(
    await createMasterDataExport({
      view: "PARTS",
      filters: {
        ...(query.q ? { q: query.q } : {}),
        ...(query.useYn ? { useYn: query.useYn } : {}),
      },
      sort: query.sort,
      direction: query.direction,
      amountUnit,
      columns: partExportColumns,
    }),
  );
}
export function salesPartAmount(
  value: string | null | undefined,
  unit: "원" | "천원" | "백만원",
) {
  if (value == null) return "–";
  const scale = unit === "원" ? 0 : unit === "천원" ? 3 : 6;
  const digits = value.padStart(scale + 1, "0");
  return scale ? `${digits.slice(0, -scale)}.${digits.slice(-scale)}` : digits;
}
