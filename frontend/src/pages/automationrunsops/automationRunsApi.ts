import { request } from "../../shared/api";

export type AutomationJobCode = "DAILY_OPERATIONS" | "RECEIVABLE_DUNNING";
export type AutomationRun = {
  jobCode: AutomationJobCode;
  runDate: string;
  state: "RUNNING" | "SUCCEEDED" | "FAILED";
  lastStep: number;
  startedAt: string;
  endedAt: string | null;
  errorMessage: string | null;
};
export type AutomationPage = {
  items: AutomationRun[];
  page: number;
  size: 20 | 50 | 100;
  total: number;
  hasMore: boolean;
  manualRunEnabled: boolean;
  observedAt: string;
};
export type AutomationCommandResult = {
  run: AutomationRun;
  accepted: boolean;
  completed: boolean;
};
export function getAutomationRuns(
  query: { page: number; size: 20 | 50 | 100 },
  signal?: AbortSignal,
  background = false,
) {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
    sort: "runDate,desc;jobCode,asc",
  });
  return request<AutomationPage>(`/api/automation-runs?${params}`, {
    signal,
    ...(background ? { headers: { "X-Background-Request": "true" } } : {}),
  });
}
export function createAutomationRunToday(jobCode: AutomationJobCode) {
  return request<AutomationCommandResult>(
    `/api/automation-runs/${encodeURIComponent(jobCode)}/run-today`,
    { method: "POST" },
  );
}
