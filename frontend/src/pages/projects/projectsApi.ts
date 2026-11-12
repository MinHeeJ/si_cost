import { request } from "../../shared/api";
import type { ProjectPlan } from "../plans/projectPlansApi";
export type ContractType = "TK" | "MO" | "DP" | "IN";
export type ProjectStatus = "ING" | "PRE" | "END" | "LOSE";
export type Project = {
  id: string;
  name: string;
  customerId: string | null;
  contractType: ContractType;
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
export type DistributionBadge = {
  selectedStep: "EST" | "CTRT" | "PLAN" | null;
  hasTable: boolean;
  totalAmount: string;
  contractDifference: string;
  mismatchCount: number;
};
export type ProjectListItem = Pick<
  Project,
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
  distribution: DistributionBadge | null;
};
export type ProjectPart = {
  effectiveMonth: string;
  partId: string | null;
  partName: string | null;
  reason: string | null;
  actor: string;
  scheduled: boolean;
};
export type Assignment = {
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
export type ProjectHistory = {
  historySn: number;
  date: string;
  itemCode: string;
  itemName: string;
  before: string | null;
  after: string | null;
  actor: string;
  registeredAt: string;
};
export type ProjectDetail = {
  project: Project;
  revision: string;
  effectiveStatus: ProjectStatus;
  readOnly: boolean;
  parts: ProjectPart[];
  pm: { userId: string; name: string; scopeSn: number }[];
  plans: ProjectPlan[];
  assignments: Assignment[];
  history: ProjectHistory[];
  actualMmTotal: string;
  directExpenseTotal: string;
};
export type ProjectSort =
  | "id"
  | "name"
  | "customerName"
  | "contractType"
  | "startDate"
  | "contractAmount"
  | "personCount"
  | "partName"
  | "status";
export type ProjectFilters = {
  year?: number;
  contractType?: ContractType;
  status?: ProjectStatus;
  partId?: string;
  q?: string;
  page: number;
  size: 20 | 50 | 100;
  sort?: ProjectSort;
  direction?: "ASC" | "DESC";
};
export type ProjectsPage = {
  items: ProjectListItem[];
  total: number;
  unassignedCount: number;
  page: number;
  size: 20 | 50 | 100;
  year: number;
};
export type ProjectFields = {
  year: number;
  name: string;
  customerId?: string | null;
  startDate: string;
  endDate: string;
  workplace?: string | null;
  feature?: string | null;
  estimateAmount?: string | null;
  contractAmount?: string | null;
  budgetAmount?: string | null;
  teamExpenseAmount?: string | null;
  projectExpenseAmount?: string | null;
  budgetMode?: "AUTO" | "MANUAL";
};
export type CreateProjectRequest = ProjectFields & {
  contractType: ContractType;
  initialStatus: "ING" | "PRE";
  partId?: string | null;
};
export type PutProjectRequest = ProjectFields & { expectedRevision: string };
export type ProjectDefaults = {
  contractAmount: string;
  contractType: ContractType;
  budgetAmount: string | null;
  targetMarginRate: string | null;
  teamExpenseAmount: string | null;
  projectExpenseAmount: string | null;
  basisMonth: string;
  warnings: string[];
};
export type SaveProjectResult = {
  project: Project;
  revision: string;
  changed: boolean;
  warnings: string[];
  defaults: ProjectDefaults;
};
export const PROJECTS_COMMITTED = "sicost:projects-committed";
export function notifyProjectsCommitted(id: string) {
  window.dispatchEvent(new CustomEvent(PROJECTS_COMMITTED, { detail: { id } }));
}
export function getProjects(filters: ProjectFilters, signal?: AbortSignal) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return request<ProjectsPage>(`/api/projects?${query}`, { signal });
}
export const getProject = (
  id: string,
  includeCancelled = false,
  signal?: AbortSignal,
) =>
  request<ProjectDetail>(
    `/api/projects/${encodeURIComponent(id)}?includeCancelled=${includeCancelled}`,
    { signal },
  );
export const createProject = (body: CreateProjectRequest) =>
  request<SaveProjectResult>("/api/projects", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const putProject = (id: string, body: PutProjectRequest) =>
  request<SaveProjectResult>(`/api/projects/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
export const getProjectDefaults = (
  contractAmount: string,
  contractType: ContractType,
  signal?: AbortSignal,
) =>
  request<ProjectDefaults>(
    `/api/projects/defaults?${new URLSearchParams({ contractAmount, contractType })}`,
    { signal },
  );
