import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge, type Tone } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { B8UploadActions } from "../b8/B8UploadDialog";
import { DeveloperForm } from "./DeveloperForm";
import { DeveloperDrawer } from "./DeveloperDrawer";
import * as api from "./peopleClient";
import { DeveloperStatusToolbar } from "../developersops/DeveloperStatusToolbar";
import { useDeveloperSelection } from "../developersops/useDeveloperSelection";
import type { DeveloperFilters as SelectionFilters } from "../developersops/peopleClient";
import { downloadFilename } from "../shared/downloadFilename";
import "./developers.css";

export type DeveloperPoolProps = {
  active?: boolean;
  dense?: boolean;
  pageSize?: 20 | 50 | 100;
  onDirtyChange?(dirty: boolean): void;
  columnView?: { order: string[]; hidden: string[] };
  onColumnsChange?(): void;
  onDensityChange?(): void;
  onExport?(filters: api.DeveloperFilters): void;
  renderActions?: ReactNode;
};
const columnsOrder = [
  "name",
  "companyName",
  "employeeType",
  "grade",
  "job",
  "status",
  "currentProjects",
  "withdrawalDate",
  "monthlyRate",
];
const tones: Record<string, Tone> = {
  INPUT: "info",
  WTHDR: "warn",
  IDLE: "bad",
  LEAVE: "lock",
  RETIRE: "lock",
};
export function DeveloperPoolScreen({
  active = true,
  dense = false,
  pageSize = 50,
  onDirtyChange,
  columnView,
  onColumnsChange,
  onDensityChange,
  onExport,
  renderActions,
}: DeveloperPoolProps) {
  const [mobile, setMobile] = useState(
    () => window.matchMedia?.("(max-width: 767px)").matches ?? false,
  );
  const defaults = {
    employeeType: "",
    jobCode: "",
    directClass: "",
    workStatus: "",
    q: "",
    includeStopped: false,
  };
  const [draft, setDraft] = useState(defaults),
    [filters, setFilters] = useState<api.DeveloperFilters>({
      page: 1,
      size: mobile ? 20 : pageSize,
    });
  // 선택 대상(전체)은 페이지와 무관하다. 서버의 선택 API는 page·size를 받지 않으므로 조건만 넘긴다.
  const { page: _page, size: _size, ...selectionConditions } = filters;
  const selection = useDeveloperSelection(
    selectionConditions as SelectionFilters,
    filters.size,
  );
  const [data, setData] = useState<api.DevelopersPage>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true);
  const [allCodes, setCodes] = useState<
    Record<string, { code: string; name: string }[]>
  >({});
  const [form, setForm] = useState<{ developer?: api.DeveloperDetail }>(),
    [id, setId] = useState<string>();
  const [notice, setNotice] = useState(""),
    [committed, setCommitted] = useState<string>(),
    [refreshKey, setRefreshKey] = useState(0);
  const [actionError, setActionError] = useState<unknown>(),
    [downloading, setDownloading] = useState(false);
  const sequence = useRef(0),
    scroll = useRef(0);
  useEffect(() => {
    const media = window.matchMedia?.("(max-width: 767px)");
    const update = () => {
      setMobile(media?.matches ?? false);
      setFilters((f) => ({
        ...f,
        page: 1,
        size: media?.matches ? 20 : pageSize,
      }));
    };
    media?.addEventListener?.("change", update);
    return () => media?.removeEventListener?.("change", update);
  }, [pageSize]);
  const load = useCallback(async () => {
    const version = ++sequence.current;
    setLoading(true);
    setError(undefined);
    try {
      const result = await api.getDevelopers(filters);
      if (version === sequence.current)
        setData((previous) =>
          mobile && filters.page > 1
            ? {
                ...result,
                items: [...(previous?.items ?? []), ...result.items],
              }
            : result,
        );
    } catch (e) {
      if (version === sequence.current) setError(e);
      throw e;
    } finally {
      if (version === sequence.current) setLoading(false);
    }
  }, [filters, mobile]);
  useEffect(() => {
    if (active) void load().catch(() => {});
    return () => {
      sequence.current++;
    };
  }, [load, active]);
  useEffect(() => {
    let current = true;
    void Promise.all(
      ["EMP_TYPE", "JOB", "DRCT_CLS", "WORK_STAT"].map(
        async (g) => [g, (await api.getPeopleCodes(g)).items] as const,
      ),
    )
      .then((entries) => {
        if (current) setCodes(Object.fromEntries(entries));
      })
      .catch((e) => {
        if (current) setActionError(e);
      });
    return () => {
      current = false;
    };
  }, []);
  const codes: Record<string, { code: string; name: string }[]> = {
    ...allCodes,
    WORK_STAT: (allCodes.WORK_STAT ?? []).filter((c) =>
      ["INPUT", "WTHDR", "IDLE", "LEAVE", "RETIRE"].includes(c.code),
    ),
  };
  function label(group: string, value: string) {
    return codes[group]?.find((c) => c.code === value)?.name ?? value;
  }
  /** 열 이름을 누르면 그 열 기준 오름차순, 같은 열을 다시 누르면 내림차순으로 바뀐다. */
  function sort(id: string) {
    setFilters((f) => ({
      ...f,
      page: 1,
      sort: id,
      direction:
        (f.sort ?? "name") === id && f.direction !== "DESC" ? "DESC" : "ASC",
    }));
  }
  function apply(reset = false) {
    const next = reset ? defaults : draft;
    if (reset) setDraft(defaults);
    setFilters({
      page: 1,
      size: mobile ? 20 : filters.size,
      employeeType: next.employeeType || undefined,
      jobCode: next.jobCode || undefined,
      directClass: next.directClass || undefined,
      workStatus: next.workStatus || undefined,
      q: next.q.trim() || undefined,
      includeStopped: next.includeStopped,
    });
  }
  function open(selected: string) {
    scroll.current = window.scrollY;
    setId(selected);
  }
  async function saved(selected: string, message: string) {
    setForm(undefined);
    setNotice(message);
    setCommitted(selected);
    setId(undefined);
    try {
      await Promise.all([load(), api.getDeveloper(selected)]);
      setId(selected);
      setRefreshKey((v) => v + 1);
      setCommitted(undefined);
    } catch {
      setNotice(
        `${message} 저장은 완료됐지만 조회하지 못했습니다. 다시 조회해 주세요.`,
      );
    }
  }
  async function retryCommitted() {
    if (!committed) return;
    try {
      await Promise.all([load(), api.getDeveloper(committed)]);
      setId(committed);
      setCommitted(undefined);
    } catch {
      setNotice("저장은 완료됐습니다. 다시 조회해 주세요.");
    }
  }
  async function exportList() {
    if (onExport) {
      onExport(filters);
      return;
    }
    if (downloading) return;
    setDownloading(true);
    setActionError(undefined);
    try {
      const {
        page: _page,
        size: _size,
        sort,
        direction,
        ...conditions
      } = filters;
      const response = await fetch("/api/exports/master-data", {
        method: "POST",
        redirect: "error",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          view: "DEVELOPERS",
          filters: conditions,
          sort,
          direction,
          columns: (columnView?.order ?? columnsOrder).filter(
            (c) => c === "name" || !columnView?.hidden.includes(c),
          ),
          amountUnit: "원",
        }),
      });
      if (!response.ok) {
        const body = await response.json();
        throw new ApiError(response.status, body);
      }
      if (
        !response.headers.get("Content-Type")?.includes("spreadsheetml.sheet")
      )
        throw new Error("다운로드 형식이 올바르지 않습니다.");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob),
        anchor = document.createElement("a");
      try {
        anchor.href = url;
        anchor.download = downloadFilename(response, "개발자목록.xlsx");
        anchor.click();
      } finally {
        URL.revokeObjectURL(url);
      }
    } catch (e) {
      setActionError(e);
    } finally {
      setDownloading(false);
    }
  }
  const cols: Column<api.DeveloperItem>[] = [
    {
      id: "name",
      label: "이름",
      render: (r) => (
        <>
          <button
            type="button"
            data-testid={`developer-name-${r.id.toLowerCase()}`}
            className="developer-link"
            onClick={(e) => {
              e.stopPropagation();
              open(r.id);
            }}
          >
            {r.name}
          </button>
          <small className="developer-sub">
            {r.managementNo} · {r.companyName ?? "자사"}
          </small>
        </>
      ),
    },
    {
      id: "companyName",
      label: "소속",
      render: (r) => r.companyName ?? "자사",
    },
    {
      id: "employeeType",
      label: "직원구분",
      render: (r) => (
        <StatusBadge tone="neutral">
          {label("EMP_TYPE", r.employeeType)}
        </StatusBadge>
      ),
    },
    { id: "grade", label: "등급", render: (r) => r.grade ?? "–" },
    { id: "job", label: "주 직무", render: (r) => r.job },
    {
      id: "status",
      label: "상태",
      render: (r) => (
        <>
          <StatusBadge tone={tones[r.status] ?? "neutral"}>
            {label("WORK_STAT", r.status)}
          </StatusBadge>
          {r.retirementScheduled && (
            <StatusBadge tone="warn">퇴사 예정</StatusBadge>
          )}
        </>
      ),
    },
    {
      id: "currentProjects",
      label: "현재 프로젝트",
      render: (r) => (
        <>
          {r.currentProjectLabel ?? "–"}
          {r.currentProjects.length > 0 && (
            <small className="developer-sub">이번 달 {r.monthlyMm} MM</small>
          )}
          {r.warnings.map((w, i) => (
            <span
              data-testid={`developer-mm-warning-${r.id.toLowerCase()}-${i}`}
              className="developer-mm-warning"
              key={i}
            >
              {w.message}
            </span>
          ))}
        </>
      ),
    },
    {
      id: "withdrawalDate",
      label: "철수 예정일",
      render: (r) => r.withdrawalDate ?? "–",
    },
    {
      id: "monthlyRate",
      label: "월 단가",
      align: "right",
      render: (r) =>
        r.monthlyRate === null
          ? "–"
          : r.monthlyRate.replace(/\B(?=(\d{3})+(?!\d))/g, ","),
    },
  ];
  // 현재 프로젝트를 뺀 나머지 열은 열 이름을 눌러 정렬한다. 아무것도 고르지 않았으면 이름 오름차순이다.
  const sortable = new Set([
    "name",
    "companyName",
    "employeeType",
    "grade",
    "job",
    "status",
    "withdrawalDate",
    "monthlyRate",
  ]);
  const activeSort = filters.sort ?? "name";
  const sortableCols = cols.map((c) =>
    sortable.has(c.id)
      ? {
          ...c,
          label: (
            <button
              type="button"
              className="developer-sort-header"
              data-testid={`developer-sort-${c.id.replace(/[A-Z]/g, (m) => `-${m.toLowerCase()}`)}`}
              aria-label={`${String(c.label)} 정렬`}
              onClick={() => sort(c.id)}
            >
              {c.label}
              {activeSort === c.id
                ? filters.direction === "DESC"
                  ? " ↓"
                  : " ↑"
                : ""}
            </button>
          ),
        }
      : c,
  );
  const visible = (columnView?.order ?? columnsOrder)
    .filter((k) => k === "name" || !columnView?.hidden.includes(k))
    .map((k) => sortableCols.find((c) => c.id === k))
    .filter((c): c is Column<api.DeveloperItem> => Boolean(c));
  return (
    <section
      className="developers-screen"
      data-testid="developers-screen"
      data-screen-id="SCR-UI-02"
      data-feature-id="SLICE-BASIC132-DEVELOPERS"
      hidden={!active}
    >
      {form ? (
        <DeveloperForm
          key={form.developer?.id ?? "new"}
          developer={form.developer}
          onClose={() => setForm(undefined)}
          onSaved={saved}
          onDirtyChange={onDirtyChange}
        />
      ) : (
        <>
          <header className="developer-toolbar">
            {/* 화면 제목은 공통 틀(SharedScreenFrame)이 그린다. 여기서 또 그리면 제목 블록이 두 번 나온다. */}
            <div />
            <div className="developer-actions">
              {!mobile && (
                <B8UploadActions
                  onApplied={async () => {
                    await load();
                    setRefreshKey((v) => v + 1);
                  }}
                  onDirtyChange={onDirtyChange}
                />
              )}
              <button
                type="button"
                className="sicost-primary"
                data-testid="developer-create-button"
                onClick={() => {
                  setId(undefined);
                  setForm({});
                }}
              >
                + 등록
              </button>
            </div>
          </header>
          <form
            className="developer-filters"
            data-testid="developer-filter-form"
            onSubmit={(e) => {
              e.preventDefault();
              apply();
            }}
          >
            {(
              [
                ["directClass", "직접/간접", "DRCT_CLS"],
                ["employeeType", "직원구분", "EMP_TYPE"],
                ["jobCode", "직무", "JOB"],
                ["workStatus", "상태", "WORK_STAT"],
              ] as const
            ).map(([key, title, group]) => (
              <label
                data-testid={`developer-filter-label-${key.toLowerCase()}`}
                className="sicost-field"
                key={key}
              >
                {title}
                <select
                  data-testid={`developer-filter-${key.toLowerCase()}`}
                  value={draft[key]}
                  onChange={(e) =>
                    setDraft((d) => ({ ...d, [key]: e.target.value }))
                  }
                >
                  <option value="">전체</option>
                  {(codes[group] ?? []).map((c) => (
                    <option
                      data-testid={`developer-filter-option-${group.toLowerCase().replace(/_/g, "-")}-${c.code.toLowerCase().replace(/_/g, "-")}`}
                      value={c.code}
                      key={c.code}
                    >
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
            ))}
            <label className="sicost-field">
              이름
              <input
                data-testid="developer-search-input"
                placeholder="이름 또는 관리번호"
                value={draft.q}
                maxLength={100}
                onChange={(e) => setDraft((d) => ({ ...d, q: e.target.value }))}
              />
            </label>
            <label className="developer-include-stopped">
              <input
                data-testid="developer-include-stopped"
                type="checkbox"
                checked={draft.includeStopped}
                onChange={(e) =>
                  setDraft((d) => ({
                    ...d,
                    includeStopped: e.target.checked,
                  }))
                }
              />
              사용 중지 포함
            </label>
            <div className="developer-filter-actions">
              <button
                type="button"
                data-testid="developer-reset-button"
                onClick={() => apply(true)}
              >
                초기화
              </button>
              <button
                type="submit"
                className="sicost-primary"
                data-testid="developer-search-button"
              >
                조회
              </button>
            </div>
          </form>
          {notice && (
            <p role="status" className="sicost-banner sicost-tone-info">
              {notice}
              {committed && (
                <button
                  type="button"
                  data-testid="developer-committed-retry"
                  onClick={() => void retryCommitted()}
                >
                  다시 조회
                </button>
              )}
            </p>
          )}
          {actionError != null && (
            <ErrorBanner
              message={
                actionError instanceof Error
                  ? actionError.message
                  : "요청하지 못했습니다."
              }
              requestId={
                actionError instanceof ApiError
                  ? actionError.body.requestId
                  : undefined
              }
            />
          )}
          <div className="developer-toolbar">
            <strong>총 {data?.total ?? 0}건</strong>
            <div className="developer-actions">
              {onColumnsChange && (
                <button
                  type="button"
                  data-testid="developer-columns-button"
                  onClick={onColumnsChange}
                >
                  열 설정
                </button>
              )}
              {onDensityChange && (
                <button
                  type="button"
                  data-testid="developer-density-button"
                  onClick={onDensityChange}
                >
                  좁게 보기
                </button>
              )}
              {!mobile && (
                <button
                  type="button"
                  data-testid="developer-export-button"
                  disabled={downloading}
                  onClick={() => void exportList()}
                >
                  엑셀 다운로드
                </button>
              )}
              {renderActions}
            </div>
          </div>
          {error != null && (
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "목록을 조회하지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={() => void load().catch(() => {})}
            />
          )}
          {!mobile && (
            <DeveloperStatusToolbar
              selection={selection}
              rows={(data?.items ?? []).map((r) => ({
                id: r.id,
                revision: r.revision,
                name: r.name,
                useYn: r.useYn,
              }))}
              total={data?.total ?? 0}
              onCommitted={async () => {
                await load();
                setRefreshKey((v) => v + 1);
              }}
              onRefresh={async () => {
                await load();
                setRefreshKey((v) => v + 1);
              }}
            />
          )}
          {!mobile ? (
            <DataTable
              rows={data?.items ?? []}
              columns={visible}
              rowKey={(r) => r.id}
              loading={loading}
              dense={dense}
              page={filters.page}
              size={filters.size}
              total={data?.total ?? 0}
              onPageChange={(page) => setFilters((f) => ({ ...f, page }))}
              onSizeChange={(size) =>
                setFilters((f) => ({ ...f, size, page: 1 }))
              }
              selectedKeys={[...selection.items.keys()]}
              onSelectionChange={(keys) => {
                const after = new Set(keys.map(String));
                (data?.items ?? []).forEach((r) => {
                  if (after.has(r.id) !== selection.items.has(r.id))
                    selection.toggle(
                      { id: r.id, revision: r.revision },
                      after.has(r.id),
                    );
                });
              }}
              onRowAction={(r) => open(r.id)}
            />
          ) : (
            <div className="developer-cards">
              {loading && <p role="status">불러오는 중입니다.</p>}
              {!loading && !data?.items.length && (
                <p className="sicost-empty">조회된 자료가 없습니다.</p>
              )}
              {data?.items.map((r) => (
                <article
                  className="developer-card"
                  key={r.id}
                  data-testid={`developer-card-${r.id.toLowerCase()}`}
                >
                  <button
                    data-testid={`developer-card-open-${r.id.toLowerCase()}`}
                    onClick={() => open(r.id)}
                  >
                    {r.name} · {r.managementNo}
                  </button>
                  <p>
                    {r.companyName ?? "자사"} · {r.job}
                  </p>
                  <StatusBadge tone={tones[r.status] ?? "neutral"}>
                    {label("WORK_STAT", r.status)}
                  </StatusBadge>
                  <p>
                    {r.currentProjectLabel ?? "–"} · {r.monthlyMm} MM
                  </p>
                </article>
              ))}
              {(data?.items.length ?? 0) < (data?.total ?? 0) && (
                <button
                  type="button"
                  data-testid="developer-more-button"
                  disabled={loading}
                  onClick={() =>
                    setFilters((f) => ({ ...f, page: f.page + 1 }))
                  }
                >
                  20개 더보기
                </button>
              )}
            </div>
          )}
        </>
      )}
      {id && !form && (
        <DeveloperDrawer
          id={id}
          label={label}
          refreshKey={refreshKey}
          onChanged={async () => {
            await load();
            setRefreshKey((value) => value + 1);
          }}
          onDirtyChange={onDirtyChange}
          onClose={() => {
            setId(undefined);
            window.scrollTo?.(0, scroll.current);
          }}
          onEdit={(developer) => {
            setId(undefined);
            setForm({ developer });
          }}
        />
      )}
    </section>
  );
}
