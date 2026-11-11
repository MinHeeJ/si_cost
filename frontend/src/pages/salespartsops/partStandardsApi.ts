import { request } from "../../shared/api";

export type PartOption = {
  id: string;
  name: string;
  hqYn: "Y" | "N";
  firstEffectiveMonth: string | null;
};
export type PartDefaults = {
  effectiveMonth: string;
  minMonth: string;
  profitRate: string | null;
  salesExpenseAmount: string | null;
  defaultErrors: {
    source: "PART_DSTRB_RT" | "PART_SALES_EXPS";
    field: "profitRate" | "salesExpenseAmount";
    message: string;
  }[];
};
export type PartStandard = {
  effectiveMonth: string;
  endMonth: string | null;
  leaderUserId: string;
  leaderName: string;
  profitRate: string;
  salesExpenseAmount: string;
  status: "PAST" | "CURRENT" | "FUTURE";
};
export type PartDetail = {
  part: {
    id: string;
    name: string;
    useYn: "Y" | "N";
    currentMonth: string;
    currentStandard: PartStandard | null;
    scheduledChange: PartStandard | null;
    personCount: number;
    projectCount: number;
    revision: string;
  };
  standards: PartStandard[];
  minMonth: string;
  revision: string;
};
export type PartStandardSave = {
  effectiveMonth: string;
  leaderUserId: string;
  profitRate: string;
  salesExpenseAmount: string;
  expectedRevision: string;
};
export type PartStandardResult = {
  changed: boolean;
  message: string;
  part: PartDetail;
};
export const salesPartsCommittedEvent = "sicost:sales-parts-committed";
export function getSalesPartOptions(signal?: AbortSignal) {
  return request<{ items: PartOption[] }>("/api/sales-parts/options", {
    signal,
  });
}
export function getSalesPartDefaults(
  effectiveMonth?: string,
  signal?: AbortSignal,
) {
  const query =
    effectiveMonth === undefined
      ? ""
      : `?${new URLSearchParams({ effectiveMonth })}`;
  return request<PartDefaults>(`/api/sales-parts/defaults${query}`, { signal });
}
export function getPartStandardDetail(id: string, signal?: AbortSignal) {
  return request<PartDetail>(`/api/sales-parts/${encodeURIComponent(id)}`, {
    signal,
  });
}
export async function createSalesPartStandard(
  id: string,
  body: PartStandardSave,
) {
  const result = await request<PartStandardResult>(
    `/api/sales-parts/${encodeURIComponent(id)}/standards`,
    {
      method: "POST",
      body: JSON.stringify(body),
    },
  );
  window.dispatchEvent(new Event(salesPartsCommittedEvent));
  return result;
}
