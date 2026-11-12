import { request } from "../../shared/api";

export type ProjectBudgetData = {
  budgetAmount: string | null;
  budgetManual: "Y" | "N";
  revision: string;
  autoPreviewAmount?: string | null;
  targetMarginRate?: string | null;
  basisMonth?: string;
};
export type SaveProjectBudgetRequest = {
  year: number;
  expectedRevision: string;
  budgetAmount: string;
};
export type SaveProjectBudgetResult = {
  value: ProjectBudgetData;
  revision: string;
  changed: boolean;
};
export const getProjectBudget = (id: string) =>
  request<ProjectBudgetData>(`/api/projects/${encodeURIComponent(id)}/budget`);
export const saveProjectBudget = (id: string, body: SaveProjectBudgetRequest) =>
  request<SaveProjectBudgetResult>(
    `/api/projects/${encodeURIComponent(id)}/budget`,
    {
      method: "PUT",
      body: JSON.stringify(body),
    },
  );
