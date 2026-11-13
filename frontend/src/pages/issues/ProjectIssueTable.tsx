import { Link } from "react-router-dom";
import { DataTable, type Column } from "../../shared/DataTable";
import { StatusBadge } from "../../shared/Feedback";
import type { ProjectIssue, ProjectIssuesPage } from "./projectIssuesApi";

export const issueColumnIds = [
  "importance",
  "title",
  "projectName",
  "action",
  "registeredBy",
  "dueDate",
  "status",
  "registeredAt",
  "modifiedBy",
  "modifiedAt",
] as const;
export type IssueColumnId = (typeof issueColumnIds)[number];
export const issueColumnLabels: Record<IssueColumnId, string> = {
  importance: "중요도",
  title: "제목",
  projectName: "프로젝트",
  action: "조치",
  registeredBy: "담당자",
  dueDate: "기한",
  status: "상태",
  registeredAt: "등록일",
  modifiedBy: "수정자",
  modifiedAt: "수정일",
};
export type IssueColumnView = {
  order: IssueColumnId[];
  hidden: IssueColumnId[];
};
export function ProjectIssueTable({
  data,
  loading,
  board = false,
  readOnly = false,
  onEdit,
  onPageChange,
  onSizeChange,
  view,
  onColumnsChange,
}: {
  data?: ProjectIssuesPage;
  loading: boolean;
  board?: boolean;
  readOnly?: boolean;
  onEdit(issue: ProjectIssue): void;
  onPageChange(page: number): void;
  onSizeChange(size: 20 | 50 | 100): void;
  view?: IssueColumnView;
  onColumnsChange?(): void;
}) {
  const columns: Column<ProjectIssue>[] = [
    {
      id: "importance",
      label: "중요도",
      render: (r) => (
        <StatusBadge
          tone={
            r.importance === "HIGH"
              ? "bad"
              : r.importance === "MID"
                ? "warn"
                : "neutral"
          }
        >
          {r.importanceName}
        </StatusBadge>
      ),
    },
    {
      id: "title",
      label: "제목",
      render: (r) => (
        <button
          type="button"
          className="issue-link"
          data-testid={`issue-edit-${r.issueId}`}
          onClick={() => onEdit(r)}
        >
          {r.title}
          {readOnly ? " (조회)" : ""}
        </button>
      ),
    },
    ...(board
      ? [
          {
            id: "projectName",
            label: "프로젝트",
            render: (r: ProjectIssue) => (
              <Link
                data-testid={`issue-project-${r.issueId}`}
                data-screen-id="SCR-UI-31-B138-PROJECT-ISSUES"
                onClick={(e) => e.stopPropagation()}
                to={`/UI-01?${new URLSearchParams({ projectId: r.projectId, tab: "issues" })}`}
              >
                {r.projectName}
              </Link>
            ),
          },
        ]
      : []),
    { id: "action", label: "조치", render: (r) => r.action ?? "–" },
    {
      id: "registeredBy",
      label: "담당자",
      render: (r) => r.registeredBy.userName,
    },
    {
      id: "dueDate",
      label: "기한",
      render: (r) => (
        <span className={r.overdue ? "issue-overdue" : undefined}>
          {r.dueDate}
          {r.overdue && <StatusBadge tone="bad">기한 지남</StatusBadge>}
        </span>
      ),
    },
    {
      id: "status",
      label: "상태",
      render: (r) => (
        <StatusBadge
          tone={
            r.status === "CLOSE"
              ? "ok"
              : r.status === "ACT"
                ? "info"
                : "neutral"
          }
        >
          {r.statusName}
        </StatusBadge>
      ),
    },
    { id: "registeredAt", label: "등록일", render: (r) => r.registeredAt },
    {
      id: "modifiedBy",
      label: "수정자",
      render: (r) => r.modifiedBy?.userName ?? "–",
    },
    { id: "modifiedAt", label: "수정일", render: (r) => r.modifiedAt ?? "–" },
  ];
  const visible = view
    ? view.order
        .filter((id) => !view.hidden.includes(id))
        .flatMap((id) => columns.filter((c) => c.id === id))
    : columns;
  return (
    <DataTable
      testId="project-issues-table"
      rows={data?.items ?? []}
      rowKey={(r) => r.issueId}
      columns={visible}
      loading={loading}
      empty="등록된 이슈가 없습니다."
      page={data?.page ?? 1}
      size={data?.size ?? 50}
      total={data?.total ?? 0}
      onPageChange={onPageChange}
      onSizeChange={onSizeChange}
      onColumnsChange={onColumnsChange}
    />
  );
}
