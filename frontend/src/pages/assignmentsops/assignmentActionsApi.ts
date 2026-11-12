import { request, type ApiErrorBody } from "../../shared/api";
export type Warning = {
  code: string;
  message: string;
  months: string[];
  assignmentIds: string[];
};
export type Page<T> = {
  items: T[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type Assignment = {
  id: string;
  projectId: string;
  projectName: string;
  developerId: string;
  developerName: string;
  managementNo: string;
  jobCode: string;
  jobName: string;
  startDate: string;
  endDate: string;
  contractMm: string;
  billingUnitPrice: string | null;
  inputType: string;
  contractDeveloperId: string | null;
  extensionPossibility: string | null;
  extensionCount: number;
  earlyWithdrawal: string;
  assignmentStatus: string;
  yearMm: string;
  revision: string;
  warnings: Warning[];
  protectedMonths: string[];
};
export type Month = {
  month: string;
  projectId: string;
  developerId: string;
  inputDays: number;
  contractMm: string;
  monthlyMm: string;
  appliedUnitPrice: string | null;
  salesAmount: string;
  fixed: string;
};
export type SelectionItem = { id: string; expectedRevision: string };
export type Selection = {
  items: SelectionItem[];
  total: number;
  protectedMonths: string[];
};
export type Filters = {
  year: number;
  projectId?: string;
  jobCode?: string;
  warning?: string;
  q?: string;
  includeCancelled?: boolean;
};
export type Create = {
  year: number;
  projectId: string;
  developerId: string;
  jobCode: string;
  startDate: string;
  endDate: string;
  contractMm?: string;
  billingUnitPrice?: string;
  inputType?: string;
  contractDeveloperId?: string | null;
};
type CommonChange = {
  year: number;
  expectedRevision: string;
  reasonCode?: string;
  memo?: string;
};
export type Change = CommonChange &
  (
    | { type: "EXT"; newEndDate: string }
    | { type: "EARLY"; withdrawalDate: string }
    | { type: "RATE"; applyMonth: string; newContractMm: string }
    | { type: "UPRC"; applyMonth: string; newUnitPrice: string }
    | { type: "EXTP"; applyDate: string; extensionPossibility: string | null }
    | { type: "KIND"; applyDate: string; inputType: "NEW" | "ADD" }
  );
export type Preview = {
  warnings: Warning[];
  affectedMonths: string[];
  protectedMonths: string[];
  months: Month[];
};
export type Candidate = {
  developerId: string;
  developerName: string;
  managementNo: string;
  lastAssignmentId: string;
  withdrawalDate: string;
  defaultUnitPrice: string | null;
};
export type SubPreview = {
  previewToken: string;
  withdrawalDate: string;
  candidates: SelectionItem[];
  warnings: Warning[];
  protectedMonths: string[];
  turnkeyNotice: string | null;
};
export type Withdrawal = {
  assignmentId: string;
  expectedRevision: string;
  withdrawalDate: string;
  reasonCode: string;
  memo?: string;
};
export type Save = {
  assignment: Assignment;
  changed: boolean;
  warnings: Warning[];
  protectedMonths: string[];
};
export type SubResult = Save & {
  withdrawnIds: string[];
  turnkeyNotice: string | null;
};
export type BulkRequest = {
  year: number;
  type: "EXTP" | "CNCL";
  items: SelectionItem[];
  extensionPossibility?: string | null;
  reasonCode?: string;
  memo?: string;
};
export type BulkResult = {
  requested: number;
  applied: number;
  noChange: number;
  rejected: number;
  items: {
    id: string;
    outcome: string;
    revision: string | null;
    error: ApiErrorBody | null;
    protectedMonths: string[];
  }[];
};
export type History = {
  sequence: number;
  type: string;
  applyDate: string;
  before: string | null;
  after: string | null;
  reasonCode: string | null;
  memo: string | null;
  newUnitPrice: string | null;
  newContractMm: string | null;
  retroactive: string;
  closeCross: string;
  corrected: string;
  correctionTargetSequence: number | null;
  actor: string;
  registeredAt: string;
};
const post = <T>(path: string, body: unknown) =>
  request<T>(path, { method: "POST", body: JSON.stringify(body) });
const query = (values: object) => {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(values))
    if (v !== undefined && v !== "") q.set(k, String(v));
  return q.toString();
};
export const getAssignments = (
  filters: Filters & {
    page?: number;
    size?: number;
    sort?: string;
    direction?: string;
  },
) => request<Page<Assignment>>(`/api/assignments?${query(filters)}`);
export const getAssignment = (id: string, year: number) =>
  request<Assignment>(
    `/api/assignments/${encodeURIComponent(id)}?${query({ year })}`,
  );
export const getMonths = (id: string, year: number, page = 1, size = 50) =>
  request<Page<Month>>(
    `/api/assignments/${encodeURIComponent(id)}/months?${query({ year, page, size })}`,
  );
export const getHistory = (id: string, year: number, type?: string, page = 1) =>
  request<Page<History>>(
    `/api/assignments/${encodeURIComponent(id)}/history?${query({ year, type, page, size: 50 })}`,
  );
export const preview = (body: Create | { id: string; change: Change }) =>
  post<Preview>("/api/assignments/preview", body);
export const createAssignment = (body: Create) =>
  post<Save>("/api/assignments", body);
export const changeAssignment = (id: string, body: Change) =>
  post<Save>(`/api/assignments/${encodeURIComponent(id)}/changes`, body);
export const selectAssignments = (body: {
  year: number;
  scope: "PAGE" | "ALL";
  filters: Filters;
  ids?: string[];
  excludedIds?: string[];
}) => post<Selection>("/api/assignments/selection", body);
export const saveBulk = (body: BulkRequest) =>
  post<BulkResult>("/api/assignments/bulk", body);
export const candidates = (projectId: string, developerId: string, page = 1) =>
  request<Page<Candidate>>(
    `/api/assignments/substitution-candidates?${query({ projectId, developerId, page, size: 50 })}`,
  );
export const substitutionPreview = (assignment: Create) =>
  post<SubPreview>("/api/assignments/substitution-preview", { assignment });
export const substitute = (
  assignment: Create,
  mode: "TOGETHER" | "ONLY",
  previewToken: string,
  withdrawals: Withdrawal[],
) =>
  post<SubResult>("/api/assignments/substitutions", {
    assignment,
    mode,
    previewToken,
    withdrawals,
  });
export const correctHistory = (
  id: string,
  sequence: number,
  body: {
    year: number;
    expectedRevision: string;
    correctContent: string;
    reasonCode: string;
  },
) =>
  post<unknown>(
    `/api/assignments/${encodeURIComponent(id)}/history/${sequence}/corrections`,
    body,
  );
export type ProjectOption = {
  id: string;
  name: string;
  contractType: string;
  status: string;
  startDate: string;
  endDate: string;
};
export type DeveloperOption = {
  id: string;
  name: string;
  managementNo: string;
  jobCode?: string;
};
export const projectOptions = (q: string) =>
  request<{ items: ProjectOption[] }>(
    `/api/projects/options?${query({ purpose: "ASSIGNMENT", q })}`,
  );
export const developerOptions = (q: string) =>
  request<Page<DeveloperOption>>(
    `/api/developers?${query({ directClass: "DRCT", q, page: 1, size: 50 })}`,
  );
