import { request } from "../../shared/api";
export type IssueActor = { userId: string; userName: string };
export type ProjectIssue = {
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
  registeredBy: IssueActor;
  registeredAt: string;
  modifiedBy: IssueActor | null;
  modifiedAt: string | null;
};
export type IssueFilter = {
  status: "OPEN_ACTIVE" | "OVERDUE" | "CLOSED" | "ALL";
  importance?: string;
  projectId?: string;
  page: number;
  size: 20 | 50 | 100;
};
export type ProjectIssuesPage = {
  items: ProjectIssue[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  openCount: number;
  baseDate: string;
};
export type IssueWrite = {
  year: number;
  expectedRevision: string;
  title: string;
  dueDate: string;
  importance: string;
  status: string;
  action: string | null;
};
export type IssueProgress = {
  projectId: string;
  year: number;
  progressRevision: string;
  readOnly: boolean;
  readOnlyReasons: string[];
  issues: ProjectIssue[];
};
export type IssueResult = {
  projectId: string;
  changed: boolean;
  message: string;
  progressRevision: string;
  progress: IssueProgress;
  memo: unknown;
  issue: ProjectIssue;
};
function query(filter: IssueFilter, board = false) {
  const q = new URLSearchParams({
    status: filter.status,
    page: String(filter.page),
    size: String(filter.size),
  });
  if (filter.importance) q.set("importance", filter.importance);
  if (board && filter.projectId) q.set("projectId", filter.projectId);
  return q;
}
export const getProjectIssues = (id: string, filter: IssueFilter) =>
  request<ProjectIssuesPage>(
    `/api/projects/${encodeURIComponent(id)}/issues?${query(filter)}`,
  );
export const getProjectStatusIssues = (filter: IssueFilter) =>
  request<ProjectIssuesPage>(
    `/api/project-status-board/issues?${query(filter, true)}`,
  );
export const getIssueProgress = (id: string, year: number) =>
  request<IssueProgress>(
    `/api/projects/${encodeURIComponent(id)}/progress?${new URLSearchParams({ year: String(year) })}`,
  );
export const createProjectIssue = (id: string, body: IssueWrite) =>
  request<IssueResult>(`/api/projects/${encodeURIComponent(id)}/issues`, {
    method: "POST",
    body: JSON.stringify(body),
  });
export const putProjectIssue = (
  id: string,
  issueId: number,
  body: IssueWrite,
) =>
  request<IssueResult>(
    `/api/projects/${encodeURIComponent(id)}/issues/${issueId}`,
    { method: "PUT", body: JSON.stringify(body) },
  );
