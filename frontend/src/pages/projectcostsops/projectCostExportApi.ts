import { request } from "../../shared/api";
import {
  analysisQuery,
  type AnalysisQuery,
  type Completeness,
} from "../apiops/projectAnalysisApi";
import {
  getProjectAnalysisDownload,
  type StatusDownloadOptions,
} from "../projectstatusboardops/projectStatusApi";

export type ProjectCostSummaryData = {
  month: string;
  total: number;
  cumulativeRevenue: string | null;
  cumulativeProfit: string | null;
  lowMarginCount: number;
  budgetOverCount: number;
  researchCost: string | null;
  unknownCostCount: number;
  unknownRevenueCount: number;
  unknownMarginCount: number;
  completeness: Completeness;
};
export function getProjectCostSummary(
  query: AnalysisQuery,
  signal?: AbortSignal,
) {
  const { month, projectId, contractType, status, alertOnly } = query;
  return request<ProjectCostSummaryData>(
    `/api/project-costs/summary?${analysisQuery({ month, projectId, contractType, status, alertOnly })}`,
    { signal },
  );
}
export const getProjectCostExport = (
  query: AnalysisQuery,
  options?: StatusDownloadOptions,
  signal?: AbortSignal,
) => getProjectAnalysisDownload("projectCost", query, options, signal);
