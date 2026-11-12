import { request } from "../../shared/api";
export type ProjectStatus = "ING" | "PRE" | "END" | "LOSE";
export type ProjectValue = {
  id: string;
  name: string;
  customerId: string | null;
  contractType: "TK" | "MO" | "DP" | "IN";
  status: ProjectStatus;
  startDate: string;
  endDate: string;
  workplace: string | null;
  feature: string | null;
  estimateAmount: string | null;
  originalContractAmount: string | null;
  contractAmount: string | null;
  budgetAmount: string | null;
  teamExpenseAmount: string | null;
  projectExpenseAmount: string | null;
  budgetManual: "Y" | "N";
  lossDate: string | null;
  lossReason: string | null;
  lossMemo: string | null;
  opportunityId: string | null;
};
export type ProjectOption = Pick<
  ProjectValue,
  | "id"
  | "name"
  | "contractType"
  | "startDate"
  | "endDate"
  | "contractAmount"
  | "status"
> & {
  customerName: string | null;
  personCount: number;
  partName: string | null;
  distribution: {
    selectedStep: "EST" | "CTRT" | "PLAN" | null;
    hasTable: boolean;
    totalAmount: string;
    contractDifference: string;
    mismatchCount: number;
  } | null;
};
export type ProjectDefaults = {
  contractAmount: string;
  contractType: ProjectValue["contractType"];
  budgetAmount: string | null;
  targetMarginRate: string | null;
  teamExpenseAmount: string | null;
  projectExpenseAmount: string | null;
  basisMonth: string;
  warnings: string[];
};
export type LossAssignment = {
  assignmentId: string;
  developerId: string;
  managementNo: string;
  name: string;
  jobCode: string;
  jobName: string;
  startDate: string;
  endDate: string;
  contractMm: string;
  monthlyMmTotal: string;
  status: "ACT" | "CNCL";
};
export type ProjectLossPreview = {
  basisDate: string;
  currentPersonCount: number;
  futurePersonCount: number;
  currentAssignmentCount: number;
  futureAssignmentCount: number;
  currentAssignments: LossAssignment[];
  futureAssignments: LossAssignment[];
  previewToken: string;
  revision: string;
};
export type ProjectStatusResult = {
  value: ProjectValue;
  revision: string;
  changed: boolean;
};
export type ProgressRequest = { year: number; expectedRevision: string };
export type LossRequest = ProgressRequest & {
  lossReason: string;
  lossMemo: string | null;
  previewToken: string;
};
const projectPath = (id: string) => `/api/projects/${encodeURIComponent(id)}`;
export const getProjectDefaults = (
  contractAmount: string,
  contractType: ProjectValue["contractType"],
) =>
  request<ProjectDefaults>(
    `/api/projects/defaults?${new URLSearchParams({ contractAmount, contractType })}`,
  );
export const getProjectOptions = (purpose: "ASSIGNMENT" | "BILLING", q = "") =>
  request<{ items: ProjectOption[] }>(
    `/api/projects/options?${new URLSearchParams({ purpose, q })}`,
  );
export const getProjectLossPreview = (id: string) =>
  request<ProjectLossPreview>(`${projectPath(id)}/loss-preview`);
export const saveProjectProgress = (id: string, body: ProgressRequest) =>
  request<ProjectStatusResult>(`${projectPath(id)}/progress`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
export const saveProjectLoss = (id: string, body: LossRequest) =>
  request<ProjectStatusResult>(`${projectPath(id)}/loss`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
