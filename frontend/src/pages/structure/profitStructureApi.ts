import { request } from "../../shared/api";
import { downloadB7File, saveDownload } from "../b7/b7Api";
import type {
  PlanActual,
  BusinessPlanPerformanceData,
} from "../businessplansops/businessPlanPerformanceApi";
export type Aggregation = "MONTH" | "QUARTER" | "YTD";
export type Comparison = {
  metric: string;
  months: string[];
  comparisonMonths: string[];
  currentValue: string | null;
  comparisonValue: string | null;
  changeRate: string | null;
  percentagePointChange: string | null;
  comparable: boolean;
  availableMonthCount: number;
  requestedMonthCount: number;
  missingMonths: string[];
  direction: "ADVERSE" | "FAVORABLE" | "NEUTRAL" | "UNKNOWN";
};
export type RevenueGroup = {
  groupKind: string;
  groupId: string | null;
  groupName: string;
  other: boolean;
  salesAmt: string | null;
  allocatedLaborCostAmt: string | null;
  contributionAmt: string | null;
  revenueShare: string | null;
  contributionRate: string | null;
  positiveContributionShare: string | null;
  loss: boolean;
  showBar: boolean;
};
export type Composition = {
  visible: boolean;
  reason: string | null;
  complete: boolean;
  revenueDifference: string | null;
  contractTypes: RevenueGroup[];
  customers: RevenueGroup[];
  salesParts: RevenueGroup[];
};
export type StructureBucket = {
  key: string;
  months: string[];
  throughMonth: string;
  monthCount: number;
  amounts: Record<string, string | null>;
  completeness: PlanActual["completeness"];
  provisional: boolean;
  comparisons: Comparison[];
  composition: Composition;
  referenceRevenue: {
    visible: boolean;
    amount: string | null;
    complete: boolean;
    reason: string | null;
    issues: PlanActual["completeness"]["issues"];
  };
};
export type WaterfallStep = {
  metric: string;
  label: string;
  kind: "CHANGE" | "SUBTOTAL";
  amount: string | null;
  connected: boolean;
};
export type StructureData = {
  context: BusinessPlanPerformanceData["context"];
  months: PlanActual[];
  buckets: StructureBucket[];
  summary: {
    metric: string;
    amount: string | null;
    rate: string | null;
    previous: Comparison | null;
    previousYear: Comparison | null;
  }[];
  waterfall: WaterfallStep[];
  numericRows: {
    metric: string;
    label: string;
    isRate: boolean;
    bucketKeys: string[];
    bucketValues: (string | null)[];
  }[];
  calculationMethods: {
    metric: string;
    sources: string[];
    formula: string;
    referenceOnly: boolean;
    vatExcluded: true;
  }[];
  notices: string[];
};
export const getProfitLossStructure = (
  year: number,
  month: string,
  aggregation: Aggregation,
  signal?: AbortSignal,
) =>
  request<StructureData>(
    `/api/profit-loss/structure?${new URLSearchParams({ year: String(year), month, aggregation })}`,
    { signal },
  );
export async function downloadProfitLossStructure(
  year: number,
  month: string,
  aggregation: Aggregation,
  amountUnit: "WON" | "THOUSAND_WON" | "MILLION_WON",
) {
  saveDownload(
    await downloadB7File(
      `/api/profit-loss/structure/export?${new URLSearchParams({ year: String(year), month, aggregation, amountUnit })}`,
    ),
  );
}
