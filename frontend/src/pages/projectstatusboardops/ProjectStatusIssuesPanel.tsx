import { useEffect, useState, type ReactNode } from "react";
import {
  Link,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import { DataTable, type Column } from "../../shared/DataTable";
import { FilterBar } from "../../shared/FilterBar";
import { StatusBadge } from "../../shared/Feedback";
import {
  AnalysisFailure,
  useAnalysisLoad,
} from "../apiops/ProjectAnalysisParts";
import { getProjectStatusBoard } from "../apiops/projectAnalysisApi";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import * as api from "./projectStatusApi";
import "./projectStatus.css";

export type StatusIssueDialogHost = (
  issue: api.StatusIssue | null,
  close: () => void,
  committed: () => void,
) => ReactNode;
export function ProjectStatusIssuesPanel({
  onChanged,
  renderIssue,
  renderColumns,
}: {
  onChanged?(): void;
  renderIssue?: StatusIssueDialogHost;
  renderColumns?(): ReactNode;
}) {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate(),
    location = useLocation();
  const initial: api.StatusIssueQuery = {
    status:
      (params.get("issueStatus") as api.StatusIssueQuery["status"]) ||
      "OPEN_ACTIVE",
    importance: params.get("importance") || undefined,
    projectId: params.get("issueProjectId") || undefined,
    page: Number(params.get("issuePage") || 1),
    size: Number(params.get("issueSize") || 50) as 20 | 50 | 100,
  };
  const [query, setQuery] = useState(initial),
    [draft, setDraft] = useState(initial);
  const [projects, setProjects] = useState<
      { projectId: string; projectName: string }[]
    >([]),
    [importance, setImportance] = useState<SharedCodeLookup[]>([]),
    [optionError, setOptionError] = useState<unknown>();
  const [selected, setSelected] = useState<
    api.StatusIssue | null | undefined
  >();
  const [optionAttempt, setOptionAttempt] = useState(0);
  const result = useAnalysisLoad(
    (signal) => api.getProjectStatusIssues(query, signal),
    JSON.stringify(query),
  );
  useEffect(() => {
    let live = true;
    const controller = new AbortController();
    setOptionError(undefined);
    void Promise.all([
      getSharedCodes("IMPR"),
      (async () => {
        const first = await getProjectStatusBoard(
          { page: 1, size: 100 },
          controller.signal,
        );
        let rows = first.items;
        for (let page = 2; rows.length < first.total; page++) {
          const next = await getProjectStatusBoard(
            { page, size: 100 },
            controller.signal,
          );
          const additional = next.items.filter(
            (row) =>
              !rows.some((previous) => previous.projectId === row.projectId),
          );
          if (!additional.length) break;
          rows = [...rows, ...additional];
        }
        return rows;
      })(),
    ])
      .then(([codes, rows]) => {
        if (live) {
          setImportance(codes.items);
          setProjects(
            rows.map((row) => ({
              projectId: row.projectId,
              projectName: row.projectName,
            })),
          );
        }
      })
      .catch((e) => {
        if (live) setOptionError(e);
      });
    return () => {
      live = false;
      controller.abort();
    };
  }, [optionAttempt]);
  function apply(next: api.StatusIssueQuery) {
    setQuery(next);
    setDraft(next);
    const url = new URLSearchParams(params);
    for (const [key, value] of Object.entries({
      issueStatus: next.status,
      importance: next.importance,
      issueProjectId: next.projectId,
      issuePage: next.page,
      issueSize: next.size,
    })) {
      if (value === undefined || value === "") url.delete(key);
      else url.set(key, String(value));
    }
    setParams(url, { replace: true, preventScrollReset: true });
  }
  function open(issue: api.StatusIssue | null) {
    if (renderIssue) setSelected(issue);
    else if (issue)
      navigate(
        `/UI-01?${new URLSearchParams({ projectId: issue.projectId, tab: "issues", issueId: String(issue.issueId), returnTo: `${location.pathname}${location.search}` })}`,
      );
    else
      navigate(
        `/UI-01?${new URLSearchParams({ tab: "issues", action: "createIssue", returnTo: `${location.pathname}${location.search}` })}`,
      );
  }
  const columns: Column<api.StatusIssue>[] = [
    {
      id: "importance",
      label: "중요도",
      render: (row) => (
        <StatusBadge
          tone={
            row.importance === "HIGH"
              ? "bad"
              : row.importance === "MID"
                ? "warn"
                : "neutral"
          }
        >
          {row.importanceName}
        </StatusBadge>
      ),
    },
    {
      id: "title",
      label: "제목",
      render: (row) => (
        <button
          type="button"
          className="project-analysis-link"
          data-testid={`status-issue-edit-${row.issueId}`}
          onClick={(e) => {
            e.stopPropagation();
            open(row);
          }}
        >
          {row.title}
        </button>
      ),
    },
    {
      id: "projectName",
      label: "프로젝트",
      render: (row) => (
        <Link
          data-testid={`status-issue-project-${row.issueId}`}
          onClick={(e) => e.stopPropagation()}
          to={`/UI-01?${new URLSearchParams({ projectId: row.projectId, tab: "issues", returnTo: `${location.pathname}${location.search}` })}`}
        >
          {row.projectName}
        </Link>
      ),
    },
    { id: "action", label: "조치", render: (row) => row.action ?? "–" },
    {
      id: "registeredBy",
      label: "담당자",
      render: (row) => row.registeredBy.userName,
    },
    {
      id: "dueDate",
      label: "기한",
      render: (row) => (
        <>
          <span>{row.dueDate}</span>
          {row.overdue && <StatusBadge tone="bad">기한 지남</StatusBadge>}
        </>
      ),
    },
    {
      id: "status",
      label: "상태",
      render: (row) => (
        <StatusBadge
          tone={
            row.status === "CLOSE"
              ? "lock"
              : row.status === "ACT"
                ? "warn"
                : "info"
          }
        >
          {row.statusName}
        </StatusBadge>
      ),
    },
    {
      id: "registeredAt",
      label: "등록/수정정보",
      render: (row) => (
        <>
          {row.registeredAt}
          <small>
            {row.modifiedBy && `${row.modifiedBy.userName} · ${row.modifiedAt}`}
          </small>
        </>
      ),
    },
  ];
  return (
    <section
      role="tabpanel"
      className="project-analysis"
      data-screen-id="SCR-UI-31"
      data-slice-id="SLICE-002"
      data-testid="project-status-issues"
    >
      <div className="project-analysis-toolbar">
        <h2>이슈 모아 보기 ({result.data?.openCount ?? "–"})</h2>
        <button
          type="button"
          data-testid="status-issue-create"
          onClick={() => open(null)}
        >
          이슈 등록
        </button>
        {renderColumns?.()}
      </div>
      <FilterBar
        testId="status-issue-filters"
        resetTestId="status-issue-reset"
        searchTestId="status-issue-search"
        onSubmit={() => apply({ ...draft, page: 1 })}
        onReset={() => apply({ status: "OPEN_ACTIVE", page: 1, size: 50 })}
      >
        <label className="sicost-field">
          상태
          <select
            data-testid="status-issue-state"
            value={draft.status}
            onChange={(e) =>
              setDraft({
                ...draft,
                status: e.target.value as api.StatusIssueQuery["status"],
              })
            }
          >
            <option value="OPEN_ACTIVE">열림·조치 중</option>
            <option value="OVERDUE">기한 지남</option>
            <option value="CLOSED">종료</option>
            <option value="ALL">전체</option>
          </select>
        </label>
        <label className="sicost-field">
          중요도
          <select
            data-testid="status-issue-importance"
            value={draft.importance ?? ""}
            onChange={(e) =>
              setDraft({ ...draft, importance: e.target.value || undefined })
            }
          >
            <option value="">전체</option>
            {importance.map((code) => (
              <option key={code.code} value={code.code}>
                {code.name}
              </option>
            ))}
          </select>
        </label>
        <label className="sicost-field">
          프로젝트
          <select
            data-testid="status-issue-project"
            value={draft.projectId ?? ""}
            onChange={(e) =>
              setDraft({ ...draft, projectId: e.target.value || undefined })
            }
          >
            <option value="">전체</option>
            {projects.map((project) => (
              <option key={project.projectId} value={project.projectId}>
                {project.projectName}
              </option>
            ))}
          </select>
        </label>
      </FilterBar>
      <AnalysisFailure
        error={optionError}
        retry={() => setOptionAttempt((value) => value + 1)}
      />
      <AnalysisFailure error={result.error} retry={result.retry} />
      <DataTable<api.StatusIssue>
        testId="status-issue-table"
        rows={result.error ? [] : (result.data?.items ?? [])}
        columns={columns}
        rowKey={(row) => row.issueId}
        loading={result.loading}
        empty="조건에 맞는 이슈가 없습니다."
        page={query.page}
        size={query.size}
        total={result.data?.total ?? 0}
        onPageChange={(page) => apply({ ...query, page })}
        onSizeChange={(size) => apply({ ...query, size, page: 1 })}
        onRowAction={open}
      />
      {selected !== undefined &&
        renderIssue?.(
          selected,
          () => setSelected(undefined),
          () => {
            setSelected(undefined);
            result.retry();
            onChanged?.();
          },
        )}
    </section>
  );
}
