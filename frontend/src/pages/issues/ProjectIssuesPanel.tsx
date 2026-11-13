import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  getPeriodContext,
  getSharedCodes,
  type SharedCodeLookup,
  type PeriodContext,
} from "../shared/sharedApi";
import { notifyProjectsCommitted, getProjects } from "../projects/projectsApi";
import {
  ProjectIssueDialog,
  type IssueProjectChoice,
} from "./ProjectIssueDialog";
import { ProjectIssueTable, type IssueColumnView } from "./ProjectIssueTable";
import * as api from "./projectIssuesApi";
import "./issues.css";
export type ProjectIssuesPanelProps = {
  projectId?: string;
  projectName?: string;
  year?: number;
  readOnly?: boolean;
  board?: boolean;
  onChanged?(): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
  columnView?: IssueColumnView;
  onColumnsChange?(): void;
};
const defaults: api.IssueFilter = { status: "OPEN_ACTIVE", page: 1, size: 50 };
export function ProjectIssuesPanel({
  projectId,
  projectName,
  year,
  readOnly = false,
  board = false,
  onChanged,
  onDirtyChange,
  columnView,
  onColumnsChange,
}: ProjectIssuesPanelProps) {
  const [filter, setFilter] = useState<api.IssueFilter>(defaults),
    [draft, setDraft] = useState<api.IssueFilter>(defaults);
  const [data, setData] = useState<api.ProjectIssuesPage>();
  const [period, setPeriod] = useState<PeriodContext>();
  const [codes, setCodes] = useState<SharedCodeLookup[]>([]);
  const [projects, setProjects] = useState<IssueProjectChoice[]>([]);
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true),
    [editing, setEditing] = useState<api.ProjectIssue | "new">();
  const [message, setMessage] = useState("");
  const [refreshFailed, setRefreshFailed] = useState(false);
  const generation = useRef(0);
  const load = useCallback(async () => {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const value = board
        ? await api.getProjectStatusIssues(filter)
        : await api.getProjectIssues(projectId!, filter);
      if (version === generation.current) setData(value);
    } catch (e) {
      if (version === generation.current) setError(e);
      throw e;
    } finally {
      if (version === generation.current) setLoading(false);
    }
  }, [board, projectId, filter]);
  useEffect(() => {
    void load().catch(() => {});
    return () => {
      generation.current++;
    };
  }, [load]);
  useEffect(() => {
    let live = true;
    void Promise.all([getPeriodContext(), getSharedCodes("IMPR")])
      .then(([p, c]) => {
        if (live) {
          setPeriod(p);
          setCodes(c.items);
        }
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, []);
  useEffect(() => {
    if (!board || !period) return;
    let live = true;
    async function choices() {
      const values: IssueProjectChoice[] = [];
      for (const status of ["ING", "PRE"] as const) {
        let page = 1,
          total = 0;
        do {
          const result = await getProjects({
            year: Number(period!.currentDate.slice(0, 4)),
            status,
            page,
            size: 100,
          });
          total = result.total;
          values.push(
            ...result.items
              .filter((p) => p.endDate >= period!.currentDate)
              .map((p) => ({ id: p.id, name: p.name })),
          );
          page++;
        } while ((page - 1) * 100 < total);
      }
      if (live) setProjects(values);
    }
    void choices().catch((e) => {
      if (live) setError(e);
    });
    return () => {
      live = false;
    };
  }, [board, period]);
  const selectedYear =
    year ?? (period ? Number(period.currentDate.slice(0, 4)) : undefined);
  const locked =
    readOnly ||
    !period ||
    selectedYear !== Number(period.currentDate.slice(0, 4));
  async function refresh() {
    await Promise.all([load(), onChanged?.()]);
    setRefreshFailed(false);
  }
  async function saved(result: api.IssueResult) {
    setEditing(undefined);
    onDirtyChange?.(false);
    notifyProjectsCommitted(result.projectId);
    setMessage(result.message);
    setRefreshFailed(false);
    try {
      await refresh();
    } catch {
      setRefreshFailed(true);
      setMessage("저장됐지만 다시 불러오지 못했습니다. 재조회만 해 주세요.");
    }
  }
  function apply(next: api.IssueFilter) {
    setFilter(next);
    setDraft(next);
  }
  return (
    <section
      className="project-issues"
      data-testid="project-issues-panel"
      data-screen-id={board ? "SCR-UI-31" : "SCR-UI-01"}
    >
      <header className="issue-toolbar">
        <h2>
          {board ? "이슈 모아 보기" : "이슈"} ({data?.openCount ?? "–"}){" "}
          {projectName}
        </h2>
        <button
          type="button"
          className="sicost-primary"
          data-testid="issue-create"
          disabled={locked || loading || refreshFailed}
          onClick={() => setEditing("new")}
        >
          이슈 등록
        </button>
      </header>
      {locked && (
        <p className="sicost-banner sicost-tone-lock">
          현재연도 진행·선투입 프로젝트만 편집할 수 있습니다. 종료·실주는 이력
          조회만 가능합니다.
        </p>
      )}
      <form
        className="issue-filters"
        onSubmit={(e) => {
          e.preventDefault();
          apply({ ...draft, page: 1 });
        }}
      >
        <label className="sicost-field">
          상태
          <select
            data-testid="issues-status-filter"
            value={draft.status}
            onChange={(e) =>
              setDraft((f) => ({
                ...f,
                status: e.target.value as api.IssueFilter["status"],
              }))
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
            data-testid="issues-importance-filter"
            value={draft.importance ?? ""}
            onChange={(e) =>
              setDraft((f) => ({
                ...f,
                importance: e.target.value || undefined,
              }))
            }
          >
            <option value="">전체</option>
            {codes.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        {board && (
          <label className="sicost-field">
            프로젝트
            <select
              data-testid="issues-project-filter"
              value={draft.projectId ?? ""}
              onChange={(e) =>
                setDraft((f) => ({
                  ...f,
                  projectId: e.target.value || undefined,
                }))
              }
            >
              <option value="">전체</option>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <button
          type="button"
          data-testid="issues-reset"
          disabled={loading}
          onClick={() => apply(defaults)}
        >
          초기화
        </button>
        <button type="submit" data-testid="issues-search" disabled={loading}>
          조회
        </button>
        <span>기준일 {data?.baseDate ?? "–"}</span>
      </form>
      {message && (
        <p
          role="status"
          className={`sicost-banner sicost-tone-${refreshFailed ? "warn" : "ok"}`}
        >
          {message}
        </p>
      )}
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "이슈를 조회하지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => void refresh().catch(() => {})}
        />
      )}
      {refreshFailed && (
        <button
          type="button"
          data-testid="issues-refresh-only"
          onClick={() =>
            void refresh()
              .then(() => setMessage("다시 조회했습니다."))
              .catch(() => {})
          }
        >
          재조회만
        </button>
      )}
      <ProjectIssueTable
        data={data}
        loading={loading}
        board={board}
        readOnly={locked}
        onEdit={(r) => {
          if (!refreshFailed) setEditing(r);
        }}
        onPageChange={(page) => apply({ ...filter, page })}
        onSizeChange={(size) => apply({ ...filter, page: 1, size })}
        view={columnView}
        onColumnsChange={onColumnsChange}
      />
      {editing && selectedYear && data && (
        <ProjectIssueDialog
          projectId={projectId}
          projectName={projectName}
          projects={projects}
          issue={editing === "new" ? undefined : editing}
          year={selectedYear}
          baseDate={data.baseDate}
          readOnly={locked}
          host={board ? "UI-31" : "UI-01"}
          onClose={() => setEditing(undefined)}
          onSaved={saved}
          onDirtyChange={onDirtyChange}
        />
      )}
    </section>
  );
}
