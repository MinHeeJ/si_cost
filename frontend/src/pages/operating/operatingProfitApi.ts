import { request } from "../../shared/api";
import { downloadB7File, saveDownload } from "../b7/b7Api";
import type {
  PlanActual,
  BusinessPlanPerformanceData,
} from "../businessplansops/businessPlanPerformanceApi";
import type {
  Comparison,
  StructureData,
} from "../structure/profitStructureApi";

export type OperatingStatementRow = {
  metric: string;
  label: string;
  depth: number;
  isRate: boolean;
  monthlyValues: (string | null)[];
  cumulativeValue: string | null;
  previousYearCumulativeValue: string | null;
  comparison: Comparison | null;
};
export type OperatingProfitData = {
  context: BusinessPlanPerformanceData["context"];
  months: PlanActual[];
  summary: StructureData["summary"];
  rows: OperatingStatementRow[];
  showPreviousYear: boolean;
  cumulativeProvisional: boolean;
  completeness: PlanActual["completeness"];
  notices: string[];
};
export const getOperatingProfitLoss = (
  year: number,
  month: string,
  signal?: AbortSignal,
) =>
  request<OperatingProfitData>(
    `/api/profit-loss/operating?${new URLSearchParams({ year: String(year), month })}`,
    { signal },
  );
export async function downloadOperatingProfitLoss(
  year: number,
  month: string,
  amountUnit: "WON" | "THOUSAND_WON" | "MILLION_WON",
) {
  saveDownload(
    await downloadB7File(
      `/api/profit-loss/operating/export?${new URLSearchParams({ year: String(year), month, amountUnit })}`,
    ),
  );
}
