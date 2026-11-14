import { request } from "../../shared/api";
export type AnalysisQuery = Record<
  string,
  string | number | boolean | undefined
> & { page?: number; size?: 20 | 50 | 100 };
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
export type CostAlert = {
  kind: "LOW_MARGIN" | "BUDGET_OVER";
  message: string;
  actualNumerator: string;
  actualDenominator: string;
  threshold: string;
  comparison: string;
  primaryCause: string | null;
};
export type CostRow = Record<string, unknown> & {
  projectId: string;
  projectName: string;
  contractType: string;
  status: string;
  alerts: CostAlert[];
  completeness: Completeness;
  budgetWarning: boolean | null;
  primaryCause: string | null;
};
export type CostPage = {
  month: string;
  baseDate: string;
  items: CostRow[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  summary: Record<string, unknown> & { completeness: Completeness };
};
export type SignalReason = {
  kind: string;
  light: "위험" | "주의";
  value: string;
  threshold: string;
  formula: string;
};
export type BoardRow = Record<string, unknown> & {
  projectId: string;
  projectName: string;
  contractType: string;
  sourceStatus: string;
  light: "위험" | "주의" | "정상";
  reasons: SignalReason[];
  costUnknown: boolean;
  pm: { userId: string; userName: string }[];
  currentStep: { stepName: string; status: string } | null;
  allStepsCompleted: boolean;
  stepsInitialized: boolean;
  planBasis: string;
  completeness: Completeness;
};
export type BoardPage = {
  baseDate: string;
  currentMonth: string;
  items: BoardRow[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  summary: Record<string, number | string>;
  staleProjects: {
    projectId: string;
    projectName: string;
    elapsedDays: number;
  }[];
  calculationNotice: string;
};
export function analysisQuery(query: AnalysisQuery) {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return params.toString();
}
export const getProjectCosts = (query: AnalysisQuery, signal?: AbortSignal) =>
  request<CostPage>(`/api/project-costs?${analysisQuery(query)}`, { signal });
export const getProjectStatusBoard = (
  query: AnalysisQuery,
  signal?: AbortSignal,
) =>
  request<BoardPage>(`/api/project-status-board?${analysisQuery(query)}`, {
    signal,
  });
