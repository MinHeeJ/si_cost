import { request } from "../../shared/api";
import { downloadB7File, saveDownload } from "../b7/b7Api";
import type { OperatingProfitData } from "../operating/operatingProfitApi";
import type { Composition } from "../structure/profitStructureApi";

export type NetProfitData = OperatingProfitData & {
  contributionMonth: string;
  contributions: Composition;
};
export const getNetProfitLoss = (
  year: number,
  month: string,
  signal?: AbortSignal,
) =>
  request<NetProfitData>(
    `/api/profit-loss/net?${new URLSearchParams({ year: String(year), month })}`,
    { signal },
  );
export async function downloadNetProfitLoss(
  year: number,
  month: string,
  amountUnit: "WON" | "THOUSAND_WON" | "MILLION_WON",
) {
  saveDownload(
    await downloadB7File(
      `/api/profit-loss/net/export?${new URLSearchParams({ year: String(year), month, amountUnit })}`,
    ),
  );
}
