import { request } from "../../shared/api";

export type ProgressActor = { userId: string; userName: string };
export type ProgressStep = {
  projectId: string;
  stepSn: number;
  stepCode: string;
  stepName: string;
  weight: string;
  startDate: string;
  endDate: string;
  completedDate: string | null;
  status: "예정" | "진행중" | "지연" | "완료";
  registeredBy: ProgressActor;
  registeredAt: string;
  modifiedBy: ProgressActor | null;
  modifiedAt: string | null;
};
export type WeekMemo = {
  projectId: string;
  memoSn: number;
  writtenDate: string;
  thisWeek: string | null;
  nextWeek: string | null;
  request: string | null;
  actualProgress: number | null;
  registeredBy: ProgressActor;
  registeredAt: string;
  modifiedBy: ProgressActor | null;
  modifiedAt: string | null;
};
export type ProgressIssue = {
  issueId: number;
  projectId: string;
  projectName: string;
  title: string;
  importance: string;
  importanceName: string;
  dueDate: string;
  status: string;
  statusName: string;
  action: string | null;
  overdue: boolean;
  registeredBy: ProgressActor;
  registeredAt: string;
  modifiedBy: ProgressActor | null;
  modifiedAt: string | null;
};
export type ProgressReason = {
  kind: string;
  light: "위험" | "주의";
  value: string;
  threshold: string;
  formula: string;
};
export type ProgressCompleteness = {
  costComplete: boolean;
  revenueComplete: boolean;
  estimated: boolean;
  unknownProjectCount: number;
  rangeStartMonth: string;
  rangeEndMonth: string;
  missingBeforeMonth: string | null;
  issues: {
    code: string;
    message: string;
    month: string | null;
    personId: string | null;
    assignmentId: string | null;
    metric: string;
  }[];
};
export type ProgressStatus = {
  projectId: string;
  projectName: string;
  contractType: "TK" | "IN" | "MO" | "DP";
  sourceStatus: string;
  partId: string | null;
  partName: string | null;
  hq: boolean;
  pm: ProgressActor[];
  light: "위험" | "주의" | "정상";
  reasons: ProgressReason[];
  costUnknown: boolean;
  planProgress: string;
  actualProgress: string;
  costUsage: string | null;
  scheduleGap: string;
  costProgressGap: string | null;
  currentStep: ProgressStep | null;
  allStepsCompleted: boolean;
  stepsInitialized: boolean;
  planBasis: string;
  openIssueCount: number;
  overdueIssueCount: number;
  lastUpdatedDate: string | null;
  elapsedDays: number;
  updateWarning: boolean;
  baseDate: string;
  costMonth: string;
  completeness: ProgressCompleteness;
};
export type ProjectProgress = {
  projectId: string;
  year: number;
  progressRevision: string;
  readOnly: boolean;
  readOnlyReasons: string[];
  status: ProgressStatus;
  steps: ProgressStep[];
  memos: WeekMemo[];
  issues: ProgressIssue[];
  initializationMessage: string | null;
  calculationNotice: string;
};
export type ProgressWrite = { year: number; expectedRevision: string };
export type StepEndDate = { stepSn: number; endDate: string };
export type MemoWrite = ProgressWrite & {
  thisWeek?: string | null;
  nextWeek?: string | null;
  request?: string | null;
  actualProgress?: number | null;
};
export type ProgressMutation = {
  projectId: string;
  changed: boolean;
  message: string;
  progressRevision: string;
  progress: ProjectProgress;
  memo: WeekMemo | null;
  issue: ProgressIssue | null;
};
export type StepPreview = {
  projectId: string;
  progressRevision: string;
  steps: ProgressStep[];
};
export type ProgressHistoryEntry = {
  historyId: string;
  targetTable: string;
  targetKey: string;
  changeType: string;
  itemName: string;
  before: string | null;
  after: string | null;
  reason: string | null;
  registeredBy: ProgressActor;
  registeredAt: string;
};
export type ProgressHistoryPage = {
  projectId: string;
  items: ProgressHistoryEntry[];
  total: number;
  page: number;
  size: number;
};
function base(id: string) {
  return `/api/projects/${encodeURIComponent(id)}/progress`;
}
function send<T>(path: string, method: string, body: unknown) {
  return request<T>(path, { method, body: JSON.stringify(body) });
}
export function getProjectProgress(id: string, year?: number) {
  return request<ProjectProgress>(
    `${base(id)}${year === undefined ? "" : `?year=${year}`}`,
  );
}
export function getProjectProgressHistory(
  id: string,
  query: {
    targetTable?: string;
    targetKey?: string;
    page?: number;
    size?: number;
  } = {},
) {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined) params.set(key, String(value));
  });
  return request<ProgressHistoryPage>(
    `${base(id)}/history${params.size ? `?${params}` : ""}`,
  );
}
export function createProjectProgressSteps(id: string, body: ProgressWrite) {
  return send<ProgressMutation>(`${base(id)}/steps/initialize`, "POST", body);
}
export function createProjectStepDatesPreview(
  id: string,
  body: { year: number; steps: StepEndDate[] },
) {
  return send<StepPreview>(`${base(id)}/steps/preview`, "POST", body);
}
export function saveProjectStepDates(
  id: string,
  body: ProgressWrite & { steps: StepEndDate[] },
) {
  return send<ProgressMutation>(`${base(id)}/steps`, "PUT", body);
}
export function saveProjectStepCompletion(
  id: string,
  stepSn: number,
  body: ProgressWrite,
) {
  return send<ProgressMutation>(
    `${base(id)}/steps/${stepSn}/complete`,
    "POST",
    body,
  );
}
export function createProjectWeekMemo(id: string, body: MemoWrite) {
  return send<ProgressMutation>(`${base(id)}/week-memos`, "POST", body);
}
export function putProjectWeekMemo(
  id: string,
  memoSn: number,
  body: MemoWrite,
) {
  return send<ProgressMutation>(
    `${base(id)}/week-memos/${memoSn}`,
    "PUT",
    body,
  );
}
