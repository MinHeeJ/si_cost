import { ScreenLoading } from "../../shared/ScreenLoading";
import { TableControlIcon } from "../../shared/Icon";
import { FieldHelp } from "../../shared/FieldHelp";
import { HelpTooltip } from "../../shared/HelpTooltip";

import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type Key,
  type ReactNode,
} from "react";
import { FilterBar } from "../../shared/FilterBar";
import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
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
import { AssignmentDrawer, kindName, unitPriceNote } from "./AssignmentDrawer";
import * as api from "./assignmentActionsApi";
import "./assignments.css";
import { B5Actions } from "../b5/B5UploadDialog";
import { AssignmentCandidatesPanel } from "../assignmentcandidatesops/AssignmentCandidatesPanel";

export type AssignmentListActions = {
  year: number;
  filters: api.Filters;
  sort: { id: string; direction: "ASC" | "DESC" };
  amountUnit: string;
  readonly: boolean;
  refresh(): Promise<void>;
};
export type StaffingAssignmentsProps = {
  initialMonth?: string;
  initialWarning?: string;
  entryAssignmentId?: string;
  entryHistory?: boolean;
  onDirtyChange?(dirty: boolean): void;
  renderActions?(context: AssignmentListActions): ReactNode;
};
export function StaffingAssignmentsScreen({
  onDirtyChange,
  renderActions,
  entryAssignmentId,
  entryHistory,
  initialMonth,
  initialWarning,
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
            (initialMonth ? Number(initialMonth.slice(0, 4)) : undefined) ??
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
          entryAssignmentId={entryAssignmentId}
          entryHistory={entryHistory}
          initialMonth={initialMonth}
          initialWarning={initialWarning}
        />
      ) : (
        <ScreenLoading message="배정 기간을 불러오는 중입니다." />
      )}
    </section>
  );
}
export function AssignmentsContent({
  year,
  context,
  onDirtyChange,
  renderActions,
  entryAssignmentId,
  entryHistory,
  initialMonth,
  initialWarning,
}: StaffingAssignmentsProps & { year: number; context: PeriodContext }) {
  const [filters, setFilters] = useState<api.Filters>({
      year,
      ...(initialMonth?.startsWith(`${year}-`) ? { month: initialMonth } : {}),
      ...(initialWarning ? { warning: initialWarning } : {}),
    }),
    [draft, setDraft] = useState({
      projectId: "",
      jobCode: "",
      warning: initialWarning ?? "",
      month: initialMonth?.startsWith(`${year}-`) ? initialMonth : "",
      q: "",
      includeCancelled: false,
    }),
    [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(50),
    [sort, setSort] = useState({
      id: "projectName",
      direction: "ASC" as "ASC" | "DESC",
    });
  const extraConditionsId = useId();
  const [extraConditionsOpen, setExtraConditionsOpen] = useState(
    Boolean(draft.month),
  );
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
    [dense, setDense] = useState(false),
    [columnsOpen, setColumnsOpen] = useState(false),
    [hiddenColumns, setHiddenColumns] = useState<string[]>([]);
  useEffect(() => {
    if (entryAssignmentId) setDetail(entryAssignmentId);
    return () => {
      if (entryAssignmentId) setDetail(undefined);
    };
  }, [entryAssignmentId]);
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
    const digits = 0;
    const padded = value.padStart(digits + 1, "0");
    return formatDecimal(
      digits ? `${padded.slice(0, -digits)}.${padded.slice(-digits)}` : padded,
    );
  }
  const columns: Column<api.Assignment>[] = [
    {
      id: "warnings",
      label: "경고",
      render: (r) =>
        r.warnings.length ? (
          <span>
            {r.warnings.map((w, index) => {
              const labels: Record<string, string> = {
                UNCONTRACTED: "미계약 선투입",
                MM_OVER: "⚠",
                PART_DIFFERENCE: "영업 파트 다름",
                EMPLOYEE_TYPE: "직원구분 경고",
                LEAVE: "휴직 기간 겹침",
                CLOSE_CROSS: "마감월 포함",
              };
              const label = labels[w.code] ?? "배정 경고";
              return (
                <HelpTooltip
                  key={`${w.code}-${index}`}
                  label={label}
                  trigger={<span title={w.message}>{label}</span>}
                  testId={`assignment-warning-${r.id.toLowerCase()}-${index}`}
                >
                  {w.message}
                </HelpTooltip>
              );
            })}
          </span>
        ) : (
          "–"
        ),
    },
    {
      id: "projectName",
      label: "프로젝트",
      render: (r) => (
        <button
          data-testid={`assignments-project-open-${Array.from(String(r.id), (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
          className="sicost-text-link"
          onClick={() => setDetail(r.id)}
        >
          {r.projectName}
        </button>
      ),
    },
    {
      id: "developerName",
      label: "이름",
      render: (r) => (
        <span className="sicost-person-cell">
          {r.developerName}
          <small>{r.managementNo}</small>
        </span>
      ),
    },
    { id: "jobName", label: "직무", render: (r) => r.jobName },
    { id: "startDate", label: "투입일", render: (r) => r.startDate },
    { id: "endDate", label: "철수일", render: (r) => r.endDate },
    {
      id: "billingUnitPrice",
      label: "단가",
      render: (r) =>
        unitPriceNote(
          r.inputType,
          r.billingUnitPrice,
          projects.find((p) => p.id === r.projectId)?.contractType,
        ) ?? amount(r.billingUnitPrice),
      align: "right",
    },
    {
      id: "contractMm",
      label: "월 MM",
      render: (r) => r.contractMm,
      align: "right",
    },
    {
      id: "yearMm",
      label: `${year}년 MM`,
      render: (r) => r.yearMm,
      align: "right",
    },
    {
      id: "extensionPossibility",
      label: "연장 가능성",
      render: (r) =>
        r.extensionPossibility ? (
          <span
            className={`sicost-badge ${["HIGH", "H"].includes(r.extensionPossibility) ? "sicost-tone-ok" : ["LOW", "L"].includes(r.extensionPossibility) ? "sicost-tone-bad" : "sicost-tone-neutral"}`}
          >
            {kindName(r.extensionPossibility)}
          </span>
        ) : (
          "–"
        ),
    },
    {
      id: "inputType",
      label: "투입 구분",
      render: (r) => (
        <span className="sicost-kind-chip">{kindName(r.inputType)}</span>
      ),
    },
  ];
  columns.forEach((column) => {
    column.sortable = column.id !== "warnings";
  });
  function resetFilters() {
    setExtraConditionsOpen(false);
    setDraft({
      projectId: "",
      jobCode: "",
      warning: "",
      month: "",
      q: "",
      includeCancelled: false,
    });
    setFilters({ year });
    setPage(1);
    clearSelection();
  }
  return (
    <div data-testid="assignments-content" className="assignments-content">
      <ScreenTitleActions>
        <div className="assignment-head">
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
              배정
            </button>
          </div>
        </div>
      </ScreenTitleActions>
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
            month: draft.month || undefined,
            q: draft.q.trim() || undefined,
            includeCancelled: draft.includeCancelled,
          });
        }}
        onReset={resetFilters}
      >
        <label className="sicost-field">
          <span className="assignment-filter-label">프로젝트</span>
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
          <span className="assignment-filter-label">직무</span>
          <FieldHelp label="직무" />
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
          <span className="assignment-filter-label">경고</span>
          <select
            data-testid="assignments-warning-filter"
            value={draft.warning}
            onChange={(e) =>
              setDraft((v) => ({ ...v, warning: e.target.value }))
            }
          >
            <option value="">전체</option>
            <option value="MM_OVER">MM초과</option>
            <option value="UNCONTRACTED">미계약 선투입</option>
          </select>
        </label>
        <label className="sicost-field">
          <span className="assignment-filter-label">이름</span>
          <input
            placeholder="이름 또는 관리번호"
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
          취소된 배정 포함
        </label>
        <button
          type="button"
          data-testid="assignments-extra-conditions-toggle"
          aria-controls={extraConditionsId}
          aria-expanded={extraConditionsOpen}
          onClick={() => setExtraConditionsOpen((open) => !open)}
        >
          추가 조건
        </button>
        <div
          id={extraConditionsId}
          className="assignment-extra-fields"
          hidden={!extraConditionsOpen}
        >
          <label className="sicost-field assignment-extra-month">
            <span className="assignment-filter-label">선택 월</span>
            <input
              type="month"
              data-testid="assignments-month-filter"
              value={draft.month}
              min={`${year}-01`}
              max={`${year}-12`}
              onChange={(e) =>
                setDraft((v) => ({ ...v, month: e.target.value }))
              }
            />
          </label>
        </div>
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
            <button
              data-testid="assignments-clear-selection"
              type="button"
              onClick={clearSelection}
            >
              선택 해제
            </button>
          </div>
        ) : (
          <strong>{loading ? "조회 중…" : <>총 {data?.total ?? 0}건</>}</strong>
        )}
        <div className="assignment-toolbar assignment-bar-actions">
          <button
            type="button"
            data-testid="assignments-columns"
            onClick={() => setColumnsOpen(true)}
          >
            <TableControlIcon name="columns" />
            열 설정
          </button>
          <button
            type="button"
            className="assignment-density-toggle"
            data-testid="assignments-density"
            aria-pressed={dense}
            onClick={() => setDense(!dense)}
          >
            <TableControlIcon name="rows" />
            좁게 보기
          </button>
          {renderActions?.({
            year,
            filters,
            sort,
            amountUnit: "원",
            readonly,
            refresh: refreshed,
          })}
        </div>
      </div>
      <DataTable
        rows={data?.items ?? []}
        columns={columns.filter((column) => !hiddenColumns.includes(column.id))}
        rowKey={(r) => r.id}
        loading={loading}
        empty="조건에 맞는 결과가 없습니다."
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
      <Dialog
        open={columnsOpen}
        title="현재 표 열 설정"
        onCancel={() => setColumnsOpen(false)}
        onConfirm={() => setColumnsOpen(false)}
        confirmLabel="완료"
      >
        <p>현재 작업 탭의 표시 열을 선택합니다.</p>
        {columns.map((column) => (
          <label
            data-testid={`assignments-column-option-${column.id.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
            className="assignment-column-option"
            key={column.id}
          >
            <input
              data-testid={`assignments-column-toggle-${column.id.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
              type="checkbox"
              checked={!hiddenColumns.includes(column.id)}
              disabled={["projectName", "developerName"].includes(column.id)}
              onChange={(event) =>
                setHiddenColumns((current) =>
                  event.target.checked
                    ? current.filter((id) => id !== column.id)
                    : [...current, column.id],
                )
              }
            />
            {column.label}
          </label>
        ))}
        <button
          data-testid="assignments-columns-reset"
          type="button"
          onClick={() => setHiddenColumns([])}
        >
          기본 열로 복원
        </button>
      </Dialog>
      {detail && (
        <AssignmentDrawer
          key={`${detail}-${detailVersion}`}
          id={detail}
          initialTab={
            entryHistory && detail === entryAssignmentId ? "history" : "info"
          }
          year={year}
          readonly={readonly}
          contractType={
            projects.find(
              (p) =>
                p.id === data?.items.find((r) => r.id === detail)?.projectId,
            )?.contractType
          }
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
      {creating && (
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
      <AssignmentCandidatesPanel
        year={year}
        today={context.currentDate}
        readOnly={readonly}
        onDirtyChange={onDirtyChange}
        onChanged={refreshed}
        onConfirmed={async (id) => {
          setDetail(id);
        }}
      />
    </div>
  );
}
