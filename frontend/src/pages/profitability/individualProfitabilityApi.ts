import { request } from "../../shared/api";

export type ProfitabilityView = "CUMULATIVE" | "MONTHLY_AVERAGE";
export type ProfitabilitySort =
  | "name"
  | "companyName"
  | "employeeType"
  | "hireDate"
  | "utilization"
  | "mm"
  | "revenue"
  | "totalCost"
  | "profit"
  | "profitRate";
export type ProfitabilityQuery = {
  year?: number;
  view: ProfitabilityView;
  includeTurnkey: boolean;
  name?: string;
  employeeType?: string;
  companyId?: string;
  partId?: string;
  jobCode?: string;
  show: "ALL" | "LOSS" | "CONCURRENT";
  page: number;
  size: 20 | 50 | 100;
  sort?: ProfitabilitySort;
  direction?: "ASC" | "DESC";
};
export const defaultProfitabilityQuery = (): ProfitabilityQuery => ({
  view: "CUMULATIVE",
  includeTurnkey: true,
  show: "ALL",
  page: 1,
  size: 50,
});
export type ProfitabilityMetrics = Record<
  | "revenue"
  | "pay"
  | "allowance"
  | "companyInsurance"
  | "retirementReserve"
  | "personalCost"
  | "teamExpense"
  | "projectExpense"
  | "totalCost"
  | "profit"
  | "selfInsurance"
  | "withholdingTax"
  | "incomeTax"
  | "netPay"
  | "mm"
  | "profitRate",
  string | null
>;
export type ProfitabilityIssue = {
  personId: string;
  month: string;
  message: string;
  source: string;
  relatedScreen: string;
  sourceAmount: string | null;
};
export type ProfitabilityCompleteness = {
  costComplete: boolean;
  revenueComplete: boolean;
  estimated: boolean;
  allocationComplete: boolean;
  issues: ProfitabilityIssue[];
};
export type ProfitabilityMonthBasis = {
  month: string;
  assignmentMm: string;
  assignmentCount: number;
  averageMonthIncluded: boolean;
  employmentMonthIncluded: boolean;
  utilizedMonthIncluded: boolean;
  explanation: string;
};
export type ProfitabilityPerson = {
  personId: string;
  managementNo: string | null;
  name: string | null;
  companyName: string | null;
  companyId: string | null;
  personPartId: string | null;
  personPartName: string | null;
  position: string | null;
  employeeType: string;
  hireDate: string | null;
  jobCode: string | null;
  gradeCode: string | null;
  metrics: ProfitabilityMetrics;
  utilization: string | null;
  workedMonths: number;
  employmentMonths: number;
  utilizedMonths: number;
  turnkeyOnlyExcluded: boolean;
  monthBasis: ProfitabilityMonthBasis[];
  completeness: ProfitabilityCompleteness;
};
export type ProfitabilityContext = {
  period: {
    year: number;
    baseMonth: string;
    fromMonth: string | null;
    toMonth: string | null;
    calculatedAt: string;
    noData: boolean;
  };
  view: ProfitabilityView;
  includeTurnkey: boolean;
};
export type ProfitabilitySummaryData = {
  companyHeadcount: number;
  companyLeaveCount: number;
  averageHeadcount: string | null;
  averageUtilizedHeadcount: string | null;
  averageEarnedHeadcount: string | null;
  companyUtilization: string | null;
  companyEarnedRate: string | null;
  averageRevenue: string | null;
  averageCost: string | null;
  averageProfit: string | null;
  confirmedLossCount: number;
  unknownCostCount: number;
  unknownRevenueCount: number;
  validRevenuePersonCount: number;
};
export type ProfitabilityContribution = {
  jobCode: string | null;
  jobName: string;
  gradeCode: string | null;
  gradeName: string;
  personCount: number;
  unknownCount: number;
  averageProfit: string | null;
};
export type IndividualProfitabilityData = {
  items: ProfitabilityPerson[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  context: ProfitabilityContext;
  summary: ProfitabilitySummaryData;
  totals: ProfitabilityMetrics;
  contributions: ProfitabilityContribution[];
  unknownCount: number;
  estimatedCount: number;
  calculationBasis: string[];
};
export type MonthlyCostRow = {
  personId: string;
  projectId: string;
  assignmentId: string;
  month: string;
  assignmentMm: string;
  freeMm: string;
  personalRevenue: string | null;
  laborAllocated: string | null;
  teamExpense: string | null;
  projectExpense: string | null;
  directExpense: string | null;
  totalCost: string | null;
  profit: string | null;
  costClass: string;
  contractType: string | null;
  projectSalesPartId: string | null;
  fixed: string;
  projectName: string | null;
  assignmentJobCode: string | null;
  inputType: string | null;
  profitRate: string | null;
  estimated: boolean;
  costComplete: boolean;
  revenueComplete: boolean;
  rowGenerated: boolean;
  regUserId: string | null;
  regDttm: string | null;
  modUserId: string | null;
  modDttm: string | null;
};
export type MonthlyLaborSource = {
  personId: string;
  month: string;
  employeeType: string;
  directClass: string;
  source: string;
  laborCost: string | null;
  pay: string | null;
  allowance: string | null;
  companyInsurance: string | null;
  retirementReserve: string | null;
  selfInsurance: string | null;
  withholdingTax: string | null;
  incomeTax: string | null;
  netPay: string | null;
  estimated: boolean;
};
export type MonthlyPersonCost = {
  personId: string;
  month: string;
  rows: MonthlyCostRow[];
  laborSource: MonthlyLaborSource;
  completeness: ProfitabilityCompleteness;
  knownLaborAllocated: string | null;
  knownPersonalRevenue: string | null;
  assignmentMm: string;
  idleRowGenerated: boolean;
  indirectRowGenerated: boolean;
};
export type IndividualProfitabilityPersonData = {
  person: ProfitabilityPerson;
  context: ProfitabilityContext;
  months: MonthlyPersonCost[];
  issues: ProfitabilityIssue[];
  calculationBasis: string[];
};

export function profitabilityParameters(
  query: ProfitabilityQuery,
  detail = false,
): URLSearchParams {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (detail && (key === "page" || key === "size")) return;
    if (value !== undefined && value !== "")
      params.set(
        key,
        String(
          typeof value === "string" && key === "name" ? value.trim() : value,
        ),
      );
  });
  return params;
}
export const getIndividualProfitability = (
  query: ProfitabilityQuery,
  signal?: AbortSignal,
) =>
  request<IndividualProfitabilityData>(
    `/api/individual-profitability?${profitabilityParameters(query)}`,
    { signal, cache: "no-store" },
  );
export const getIndividualProfitabilityPerson = (
  personId: string,
  query: ProfitabilityQuery,
  signal?: AbortSignal,
) =>
  request<IndividualProfitabilityPersonData>(
    `/api/individual-profitability/${encodeURIComponent(personId)}?${profitabilityParameters(query, true)}`,
    { signal, cache: "no-store" },
  );
