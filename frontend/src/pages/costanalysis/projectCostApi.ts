import { request } from "../../shared/api";
export type MissingReason = {
  code: string;
  message: string;
  month: string | null;
  personId: string | null;
  assignmentId: string | null;
  metric: string;
};
export type Completeness = {
  costComplete: boolean;
  revenueComplete: boolean;
  estimated: boolean;
  issues: MissingReason[];
  unknownProjectCount: number;
  rangeStartMonth: string;
  rangeEndMonth: string;
  missingBeforeMonth: string | null;
};
export type CostRow = {
  projectId: string;
  projectName: string;
  contractType: "TK" | "MO" | "DP" | "IN";
  status: "ING" | "END";
  actualMm: string;
  cumulativeCost: string | null;
  cumulativeRevenue: string | null;
  cumulativeProfit: string | null;
  cumulativeMargin: string | null;
  budgetUsage: string | null;
  forecastMargin: string | null;
  monthlyMargin: string | null;
  targetGap: string | null;
  targetMargin: string | null;
  alerts: {
    kind: string;
    message: string;
    comparison: string;
    actualNumerator: string;
    actualDenominator: string;
    threshold: string;
  }[];
  budgetWarning: boolean | null;
};
export type SimulationDefaults = {
  enabled: boolean;
  reason: string | null;
  n: number;
  c: string | null;
  m: number;
  r: string | null;
};
export type Detail = {
  projectId: string;
  month: string;
  baseDate: string;
  sourceStatus: string;
  row: CostRow;
  simulationDefaults: SimulationDefaults;
  revenueExplanation: string;
  completeness: Completeness;
  forecast: null | {
    remainingMm: string;
    personCount: number;
    missingUnitCostPersonCount: number;
    knownRemainingCost: string;
    complete: boolean;
    people: {
      personId: string;
      personName: string;
      remainingMm: string;
      unitCostSource: string;
      sourceMonth: string | null;
      contribution: string;
    }[];
    issues: MissingReason[];
  };
};
export type MonthlyRow = {
  month: string;
  actualMm: string;
  laborAllocated: string | null;
  teamExpense: string | null;
  projectExpense: string | null;
  directExpense: string | null;
  additionalDirectExpense: string | null;
  totalCost: string | null;
  revenue: string | null;
  turnkeyReferenceRevenue: string | null;
  referenceMargin: string | null;
  fixed: boolean;
  completeness: Completeness;
};
export type Months = {
  projectId: string;
  month: string;
  items: MonthlyRow[];
  totalCost: string | null;
  completeness: Completeness;
};
export type AssignmentRow = {
  month: string;
  assignmentId: string;
  personId: string;
  personName: string;
  managementNumber: string | null;
  jobCode: string;
  jobName: string;
  startDate: string;
  endDate: string;
  assignmentMm: string;
  freeMm: string;
  laborAllocated: string | null;
  teamExpense: string | null;
  projectExpense: string | null;
  directExpense: string | null;
  personalPrice: string | null;
  inputType: string;
  assignmentStatus: string;
  planned: boolean;
  fixed: boolean;
  completeness: Completeness;
};
export type Assignments = {
  projectId: string;
  month: string;
  items: AssignmentRow[];
  completeness: Completeness;
};
export type Causes = {
  projectId: string;
  month: string;
  items: {
    kind: "EXCESS_MM" | "OVER_UNIT_COST" | "FREE_SUPPORT";
    amount: string | null;
    formula: string;
    issues: MissingReason[];
    targetScreenId: string;
    projectId: string;
  }[];
  actualProgress: string;
  progressExcess: string | null;
  remainder: string | null;
  showUnexplained: boolean;
  highlight: boolean;
  message: string;
  issues: MissingReason[];
};
export type SimulationInput = {
  month?: string;
  n?: number;
  c: string;
  m?: number;
  r?: string;
};
export type SimulationResult = {
  projectId: string;
  month: string;
  beforeMargin: string;
  afterMargin: string;
  differencePp: string;
  beforeTargetGap: string | null;
  afterTargetGap: string | null;
  virtualRevenue: string;
  virtualCost: string;
  saved: false;
  message: string;
  completeness: Completeness;
};
const path = (id: string) =>
  `/api/projects/${encodeURIComponent(id)}/cost-analysis`;
const query = (month?: string) =>
  month ? `?${new URLSearchParams({ month })}` : "";
export const getProjectCostAnalysis = (id: string, month?: string) =>
  request<Detail>(path(id) + query(month));
export const getProjectCostMonths = (id: string, month?: string) =>
  request<Months>(path(id) + "/months" + query(month));
export const getProjectCostAssignments = (id: string, month?: string) =>
  request<Assignments>(path(id) + "/assignments" + query(month));
export const getProjectCostCauses = (id: string, month?: string) =>
  request<Causes>(path(id) + "/causes" + query(month));
export const createProjectCostSimulation = (
  id: string,
  input: SimulationInput,
) =>
  request<SimulationResult>(path(id) + "/simulation", {
    method: "POST",
    body: JSON.stringify(input),
  });
