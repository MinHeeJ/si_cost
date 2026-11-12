import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type Key,
  type ReactNode,
} from "react";
import { FilterBar } from "../../shared/FilterBar";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { formatDecimal } from "../../shared/format";
import {
  getPeriodContext,
  getSharedCodes,
  getSharedPreferences,
  type PeriodContext,
  type PeriodValue,
  type SharedCodeLookup,
} from "../shared/sharedApi";
import {
  AssignmentChangeDialog,
  AssignmentBulkDialog,
  AssignmentError,
} from "./AssignmentDialogs";
import { AssignmentCreateForm } from "./AssignmentCreateForm";
import { AssignmentDrawer, kindName } from "./AssignmentDrawer";
import * as api from "./assignmentActionsApi";
import "./assignments.css";
import { B5Actions } from "../b5/B5UploadDialog";

export type AssignmentListActions = {
  year: number;
  filters: api.Filters;
  sort: { id: string; direction: "ASC" | "DESC" };
  amountUnit: string;
  readonly: boolean;
  refresh(): Promise<void>;
};
export type StaffingAssignmentsProps = {
  onDirtyChange?(dirty: boolean): void;
  renderActions?(context: AssignmentListActions): ReactNode;
};
export function StaffingAssignmentsScreen({
  onDirtyChange,
  renderActions,
}: StaffingAssignmentsProps = {}) {
  const [context, setContext] = useState<PeriodContext>(),
    [year, setYear] = useState<number>(),
    [error, setError] = useState<unknown>(),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    let live = true;
    void Promise.all([getPeriodContext(), getSharedPreferences()])
      .then(([c, p]) => {
        if (!live) return;
        setContext(c);
        const pref = p.items.find(
          (v) => v.type === "PERIOD" && v.key === "UI-12",
        )?.value as { type?: string; value?: string } | undefined;
        setYear(
          (old) =>
            old ??
            (pref?.type === "YEAR" && pref.value
              ? Math.max(
                  Number(c.cashBaseMonth.slice(0, 4)),
                  Math.min(
                    Number(pref.value),
                    Number(c.currentMonth.slice(0, 4)) + 1,
                  ),
                )
              : Number(c.currentMonth.slice(0, 4))),
        );
        setError(undefined);
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, [retry]);
  useEffect(() => {
    const changed = (e: Event) => {
      const d = (
        e as CustomEvent<{
          screenId: string;
          value: PeriodValue;
          context: PeriodContext;
        }>
      ).detail;
      if (d.screenId === "UI-12" && d.value.type === "YEAR") {
        setContext(d.context);
        setYear(Number(d.value.value));
      }
    };
    window.addEventListener("sicost:screen-period-changed", changed);
    return () =>
      window.removeEventListener("sicost:screen-period-changed", changed);
  }, []);
  return (
    <section
      data-screen-id="SCR-UI-12"
      data-testid="staffing-assignments-screen"
    >
      <AssignmentError error={error} retry={() => setRetry((v) => v + 1)} />
      {context && year !== undefined ? (
        <AssignmentsContent
          key={year}
          year={year}
          context={context}
          onDirtyChange={onDirtyChange}
          renderActions={renderActions}
        />
      ) : (
        <p role="status">배정 기간을 불러오는 중입니다.</p>
      )}
    </section>
  );
}
export function AssignmentsContent({
  year,
  context,
  onDirtyChange,
  renderActions,
}: StaffingAssignmentsProps & { year: number; context: PeriodContext }) {
  const [filters, setFilters] = useState<api.Filters>({ year }),
    [draft, setDraft] = useState({
      projectId: "",
      jobCode: "",
      warning: "",
      q: "",
      includeCancelled: false,
    }),
    [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(50),
    [sort, setSort] = useState({
      id: "projectName",
      direction: "ASC" as "ASC" | "DESC",
    });
  const [data, setData] = useState<api.Page<api.Assignment>>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>(),
    [projects, setProjects] = useState<api.ProjectOption[]>([]),
    [jobs, setJobs] = useState<SharedCodeLookup[]>([]),
    [selection, setSelection] = useState<api.Selection>({
      items: [],
      total: 0,
      protectedMonths: [],
    }),
    [selecting, setSelecting] = useState(false),
    [bulk, setBulk] = useState(false),
    [detail, setDetail] = useState<string>(),
    [detailVersion, setDetailVersion] = useState(0),
    [changing, setChanging] = useState<api.Assignment>(),
    [creating, setCreating] = useState(false),
    [dirty, setDirty] = useState(false),
    [leaving, setLeaving] = useState(false),
    [unit, setUnit] = useState("천원"),
    [dense, setDense] = useState(false);
  const generation = useRef(0),
    proceed = useRef<() => void>();
  const readonly = year < Number(context.currentMonth.slice(0, 4));
  const load = useCallback(async () => {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const value = await api.getAssignments({
        ...filters,
        page,
        size,
        sort: sort.id,
        direction: sort.direction,
      });
      if (version === generation.current) setData(value);
    } catch (e) {
      if (version === generation.current) setError(e);
      throw e;
    } finally {
      if (version === generation.current) setLoading(false);
    }
  }, [filters, page, size, sort]);
  useEffect(() => {
    void load().catch(() => {});
    return () => {
      generation.current++;
    };
  }, [load]);
  useEffect(() => {
    let live = true;
    void Promise.all([api.projectOptions(""), getSharedCodes("JOB")])
      .then(([p, j]) => {
        if (live) {
          setProjects(p.items);
          setJobs(j.items);
        }
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, []);
  const dirtyChanged = useCallback(
    (v: boolean) => {
      setDirty(v);
      onDirtyChange?.(v);
    },
    [onDirtyChange],
  );
  useEffect(() => {
    const guard = (e: Event) => {
      const d = (e as CustomEvent<{ screenId: string; proceed(): void }>)
        .detail;
      if (d.screenId === "UI-12" && dirty) {
        e.preventDefault();
        proceed.current = d.proceed;
        setLeaving(true);
      }
    };
    const before = (e: BeforeUnloadEvent) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("sicost:screen-period-changing", guard);
    window.addEventListener("beforeunload", before);
    return () => {
      window.removeEventListener("sicost:screen-period-changing", guard);
      window.removeEventListener("beforeunload", before);
    };
  }, [dirty]);
  function clearSelection() {
    setSelection({ items: [], total: 0, protectedMonths: [] });
  }
  async function select(keys: Key[], all = false) {
    setSelecting(true);
    setError(undefined);
    try {
      const result = await api.selectAssignments({
        year,
        scope: all ? "ALL" : "PAGE",
        filters,
        ids: keys.map(String),
      });
      setSelection(result);
    } catch (e) {
      setError(e);
    } finally {
      setSelecting(false);
    }
  }
  function selectionChange(keys: Key[]) {
    const previous = selection.items.filter((i) => keys.includes(i.id));
    const added = keys.filter((k) => !selection.items.some((i) => i.id === k));
    if (!added.length) {
      setSelection((old) => ({
        ...old,
        items: previous,
        total: previous.length,
      }));
      return;
    }
    setSelecting(true);
    void api
      .selectAssignments({
        year,
        scope: "PAGE",
        filters,
        ids: added.map(String),
      })
      .then((r) => {
        const items = [...previous, ...r.items];
        setSelection({
          items,
          total: items.length,
          protectedMonths: [
            ...new Set([...selection.protectedMonths, ...r.protectedMonths]),
          ],
        });
      })
      .catch(setError)
      .finally(() => setSelecting(false));
  }
  async function refreshed() {
    await load();
    setDetailVersion((v) => v + 1);
    clearSelection();
    window.dispatchEvent(
      new CustomEvent("sicost:assignments-committed", { detail: { year } }),
    );
  }
  function amount(value: string | null) {
    if (value === null) return "–";
    const digits = unit === "원" ? 0 : unit === "천원" ? 3 : 6;
    const padded = value.padStart(digits + 1, "0");
    return formatDecimal(
      digits ? `${padded.slice(0, -digits)}.${padded.slice(-digits)}` : padded,
    );
  }
  const columns: Column<api.Assignment>[] = [
    {
      id: "warnings",
      label: "경고",
      render: (r) => (
        <span
          className={r.warnings.length ? "sicost-badge sicost-tone-warn" : ""}
          title={r.warnings.map((w) => w.message).join("\n")}
        >
          {r.warnings.length ? "주의" : "–"}
        </span>
      ),
    },
    { id: "projectName", label: "프로젝트", render: (r) => r.projectName },
    {
      id: "developerName",
      label: "이름 (관리번호)",
      render: (r) => `${r.developerName} (${r.managementNo})`,
    },
    { id: "jobName", label: "직무", render: (r) => r.jobName },
    { id: "startDate", label: "투입일", render: (r) => r.startDate },
    { id: "endDate", label: "철수일", render: (r) => r.endDate },
    {
      id: "billingUnitPrice",
      label: `단가 (${unit})`,
      render: (r) => amount(r.billingUnitPrice),
      align: "right",
    },
    {
      id: "contractMm",
      label: "계약 월MM",
      render: (r) => r.contractMm,
      align: "right",
    },
    { id: "yearMm", label: "연간 MM", render: (r) => r.yearMm, align: "right" },
    {
      id: "extensionPossibility",
      label: "연장 가능성",
      render: (r) => kindName(r.extensionPossibility),
    },
    {
      id: "inputType",
      label: "투입구분",
      render: (r) => kindName(r.inputType),
    },
  ];
  columns.forEach((column) => {
    column.sortable = column.id !== "warnings";
  });
  if (creating)
    return (
      <AssignmentCreateForm
        year={year}
        today={context.currentDate}
        onClose={() => setCreating(false)}
        onDirtyChange={dirtyChanged}
        onSaved={async (row) => {
          setDetail(row.id);
          await refreshed();
        }}
      />
    );
  function resetFilters() {
    setDraft({
      projectId: "",
      jobCode: "",
      warning: "",
      q: "",
      includeCancelled: false,
    });
    setFilters({ year });
    setPage(1);
    clearSelection();
  }
  return (
    <div data-testid="assignments-content" className="assignments-content">
      <div className="assignment-head">
        <h1>투입 배정</h1>
        <div className="assignment-head-actions">
          <B5Actions
            year={year}
            readOnly={readonly}
            onApplied={refreshed}
            onDirtyChange={dirtyChanged}
          />
          <button
            type="button"
            data-testid="assignment-new"
            disabled={readonly || loading || Boolean(error)}
            onClick={() => setCreating(true)}
            className="sicost-primary"
          >
            신규 배정
          </button>
        </div>
      </div>
      {readonly && (
        <p className="sicost-banner sicost-tone-lock">
          지난 해·읽기 전용: 조회와 다운로드만 가능합니다.
        </p>
      )}
      <FilterBar
        testId="assignments-filter-form"
        className="assignment-filters"
        resetTestId="assignments-reset"
        searchTestId="assignments-query"
        onSubmit={() => {
          if (draft.q.trim().length > 100) {
            setError(new Error("검색어는 100자 이내로 입력해 주세요."));
            return;
          }
          setPage(1);
          clearSelection();
          setFilters({
            year,
            projectId: draft.projectId || undefined,
            jobCode: draft.jobCode || undefined,
            warning: draft.warning || undefined,
            q: draft.q.trim() || undefined,
            includeCancelled: draft.includeCancelled,
          });
        }}
        onReset={resetFilters}
      >
        <label className="sicost-field">
          프로젝트
          <select
            data-testid="assignments-project-filter"
            value={draft.projectId}
            onChange={(e) =>
              setDraft((v) => ({ ...v, projectId: e.target.value }))
            }
          >
            <option value="">전체</option>
            {projects.map((p) => (
              <option
                data-testid={`assignments-project-filter-option-${p.id.toLowerCase()}`}
                key={p.id}
                value={p.id}
              >
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label className="sicost-field">
          JOB
          <select
            data-testid="assignments-job-filter"
            value={draft.jobCode}
            onChange={(e) =>
              setDraft((v) => ({ ...v, jobCode: e.target.value }))
            }
          >
            <option value="">전체</option>
            {jobs.map((j) => (
              <option
                data-testid={`assignments-job-filter-option-${j.code.toLowerCase().replace(/_/g, "-")}`}
                key={j.code}
                value={j.code}
              >
                {j.name}
              </option>
            ))}
          </select>
        </label>
        <label className="sicost-field">
          경고
          <select
            data-testid="assignments-warning-filter"
            value={draft.warning}
            onChange={(e) =>
              setDraft((v) => ({ ...v, warning: e.target.value }))
            }
          >
            <option value="">전체</option>
            <option value="MM_OVER">MM초과</option>
            <option value="UNCONTRACTED">미계약</option>
          </select>
        </label>
        <label className="sicost-field">
          이름/관리번호
          <input
            data-testid="assignments-search"
            value={draft.q}
            onChange={(e) => setDraft((v) => ({ ...v, q: e.target.value }))}
          />
        </label>
        <label className="assignment-cancelled">
          <input
            data-testid="assignments-cancelled"
            type="checkbox"
            checked={draft.includeCancelled}
            onChange={(e) =>
              setDraft((v) => ({ ...v, includeCancelled: e.target.checked }))
            }
          />
          취소포함
        </label>
      </FilterBar>
      <AssignmentError
        error={error}
        retry={() => void load().catch(() => {})}
      />
      <div className="assignment-toolbar assignment-bar">
        {!readonly && selection.items.length > 0 ? (
          <div className="assignment-toolbar assignment-desktop assignment-selection">
            <button
              type="button"
              data-testid="assignments-page-selection"
              disabled={readonly || loading || selecting}
              onClick={() => void select(data?.items.map((r) => r.id) ?? [])}
            >
              페이지 전체
            </button>
            <button
              type="button"
              data-testid="assignments-all-selection"
              disabled={readonly || loading || selecting}
              onClick={() => void select([], true)}
            >
              조회전체 선택
            </button>
            <span>실제 선택 {selection.items.length}건</span>
            <button
              type="button"
              data-testid="assignment-bulk-open"
              disabled={readonly || selecting || !selection.items.length}
              onClick={() => setBulk(true)}
            >
              연장 가능성 변경 / 배정 취소
            </button>
            <button type="button" onClick={clearSelection}>
              선택 해제
            </button>
          </div>
        ) : (
          <strong>총 {data?.total ?? 0}건</strong>
        )}
        <div className="assignment-toolbar assignment-bar-actions">
          <button
            type="button"
            data-testid="assignments-refresh"
            onClick={() => void load().catch(() => {})}
          >
            다시 조회
          </button>
          <label>
            단위
            <select
              data-testid="assignments-unit"
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
            >
              <option>원</option>
              <option>천원</option>
              <option>백만원</option>
            </select>
          </label>
          <label>
            밀도
            <select
              data-testid="assignments-density"
              value={dense ? "좁게" : "넓게"}
              onChange={(e) => setDense(e.target.value === "좁게")}
            >
              <option>넓게</option>
              <option>좁게</option>
            </select>
          </label>
          {renderActions?.({
            year,
            filters,
            sort,
            amountUnit: unit,
            readonly,
            refresh: refreshed,
          })}
        </div>
      </div>
      <DataTable
        rows={data?.items ?? []}
        columns={columns}
        rowKey={(r) => r.id}
        loading={loading}
        empty="조회된 투입 배정이 없습니다."
        emptyAction={
          <button
            type="button"
            data-testid="assignments-empty-reset"
            onClick={resetFilters}
          >
            조건 초기화
          </button>
        }
        page={page}
        size={size}
        total={data?.total ?? 0}
        onPageChange={setPage}
        onSizeChange={(v) => {
          setSize(v);
          setPage(1);
          clearSelection();
        }}
        sort={sort}
        onSortChange={(id) => {
          setSort({
            id,
            direction:
              sort.id === id && sort.direction === "ASC" ? "DESC" : "ASC",
          });
          setPage(1);
          clearSelection();
        }}
        selectedKeys={selection.items.map((i) => i.id)}
        onSelectionChange={readonly ? undefined : selectionChange}
        onRowAction={(r) => setDetail(r.id)}
        dense={dense}
      />
      {detail && (
        <AssignmentDrawer
          key={`${detail}-${detailVersion}`}
          id={detail}
          year={year}
          readonly={readonly}
          onClose={() => setDetail(undefined)}
          onChange={(r) => setChanging(r)}
        />
      )}{" "}
      {changing && (
        <AssignmentChangeDialog
          assignment={changing}
          year={year}
          today={context.currentDate}
          onClose={() => setChanging(undefined)}
          onChanged={refreshed}
          onDirtyChange={dirtyChanged}
        />
      )}{" "}
      {bulk && (
        <AssignmentBulkDialog
          selection={selection}
          year={year}
          onClose={() => setBulk(false)}
          onChanged={refreshed}
        />
      )}
      <Dialog
        open={leaving}
        title="작성 중인 내용을 나가시겠습니까?"
        cancelLabel="계속 작성"
        confirmLabel="나가기"
        onCancel={() => {
          setLeaving(false);
          proceed.current = undefined;
        }}
        onConfirm={() => {
          setLeaving(false);
          setChanging(undefined);
          setCreating(false);
          dirtyChanged(false);
          proceed.current?.();
          proceed.current = undefined;
        }}
      >
        <p>확인 전까지 작성 내용과 현재 기간을 유지합니다.</p>
      </Dialog>
    </div>
  );
}
