import { request } from "../../shared/api";
import { downloadB7File, saveDownload } from "../b7/b7Api";
export type DashboardLink = { screenId: string; linkKey: string | null };
export type DashboardBasis = {
  basisMonth: string | null;
  basisDate: string;
  sourceService: string;
  observedAt: string;
  status: "AVAILABLE" | "EMPTY" | "UNCONFIRMED" | "FAILED";
  reasons: string[];
};
export type DashboardMetric = {
  metricCode:
    | "SALES"
    | "OPERATING_PROFIT"
    | "YTD_OPERATING_PROFIT"
    | "UTILIZATION"
    | "RECEIVABLE";
  value: string | null;
  unit: "KRW" | "PERCENT";
  previousValue: string | null;
  difference: string | null;
  changeRate: string | null;
  targetValue: string | null;
  achievementRate: string | null;
  profitRate: string | null;
  targetGapPercentagePoints: string | null;
  idlePersonCount: number | null;
  overdueCount: number | null;
  overdueAmount: string | null;
  basis: DashboardBasis;
  link: DashboardLink;
};
export type DashboardAction = {
  actionCode: string;
  count: number;
  urgent: boolean;
  amount: string | null;
  personCount: string | null;
  months: string[];
  withoutNextAssignmentCount: number | null;
  recommendedPlacementCount: number | null;
  reason: string;
  basis: DashboardBasis;
  link: DashboardLink;
};
export type DashboardAlert = {
  projectId: string;
  projectName: string;
  contractTypeCode: string;
  alertType: "LOW_MARGIN" | "BUDGET_WARNING" | "BUDGET_EXCEEDED";
  reason: string;
  marginRate: string | null;
  budgetConsumptionRate: string | null;
  basis: DashboardBasis;
  link: DashboardLink;
};
export type DashboardTrend = {
  month: string;
  salesAmount: string | null;
  salesCostAmount: string | null;
  salesTargetAmount: string | null;
};
export type DashboardSupply = {
  month: string;
  requiredPersons: string | null;
  availablePersons: string | null;
  differencePersons: string | null;
};
export type DashboardPart = {
  partId: string | null;
  partName: string;
  salesAmount: string | null;
  projectCostAmount: string | null;
  contributionMarginRate: string | null;
};
export type DashboardCash = {
  month: string;
  closingBalance: string | null;
  minimumCashAmount: string | null;
  shortage: boolean | null;
  unconfirmedReasons: string[];
  link: DashboardLink;
};
export type DashboardData = {
  selectedMonth: string;
  today: string;
  currentMonth: string;
  cashBaseMonth: string;
  lastClosedMonth: string | null;
  selectedMonthCloseStatus: string;
  provisional: boolean;
  observedAt: string;
  scope: "ALL";
  metrics: DashboardMetric[];
  actionsBasis: DashboardBasis;
  actions: DashboardAction[];
  actionTypeCount: number | null;
  urgentActionTypeCount: number | null;
  closeProgress: {
    basis: DashboardBasis;
    closeStatus: string;
    passedCheckCount: number | null;
    totalCheckCount: number;
    remainingChecks: {
      checkCode: string;
      status: string;
      count: number;
      reason: string;
    }[];
    snapshot: boolean;
    link: DashboardLink;
  };
  projectAlertsBasis: DashboardBasis;
  projectAlerts: DashboardAlert[];
  salesCostTrendBasis: DashboardBasis;
  salesCostTrend: DashboardTrend[];
  supplyBasis: DashboardBasis;
  supplyForecast: DashboardSupply[];
  salesPartsBasis: DashboardBasis;
  salesParts: DashboardPart[];
  salesPartExplanation: string;
  pipeline: {
    basis: DashboardBasis;
    opportunityCount: number | null;
    weightedExpectedAmount: string | null;
    startingSoon: {
      opportunityId: string;
      opportunityName: string;
      startMonth: string;
      winProbabilityRate: string;
      expectedAmount: string;
      link: DashboardLink;
    }[];
  };
  cashBasis: DashboardBasis;
  cashForecast: DashboardCash[];
};
export const getDashboard = (month?: string, signal?: AbortSignal) =>
  request<DashboardData>(
    `/api/dashboard${month ? `?${new URLSearchParams({ month })}` : ""}`,
    { signal },
  );
export async function getDashboardExport(month: string) {
  saveDownload(
    await downloadB7File(
      `/api/dashboard/export?${new URLSearchParams({ month })}`,
    ),
  );
}
