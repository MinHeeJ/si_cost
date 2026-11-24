import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { FilterBar } from "../../shared/FilterBar";
import { formatDecimal } from "../../shared/format";
import { PeriodStatus } from "../../shared/PeriodStatus";
import {
  getPeriodContext,
  getSharedCodes,
  getSharedPreferences,
  type PeriodContext,
  type PeriodValue,
} from "../shared/sharedApi";
import { useSharedScreen } from "../shared/SharedScreenContext";
import * as api from "./staffingComparisonApi";
import "./staffingComparison.css";

export function StaffingPlanComparisonScreen() {
  const [context, setContext] = useState<PeriodContext>();
  const [year, setYear] = useState<number>();
  const [error, setError] = useState<unknown>();
  const [retry, setRetry] = useState(0);
  const [unit, setUnit] = useState<"WON" | "THOUSAND" | "MILLION">("THOUSAND");
  useEffect(() => {
    let live = true;
    void Promise.all([getPeriodContext(), getSharedPreferences()])
      .then(([ctx, prefs]) => {
        if (!live) return;
        setContext(ctx);
        const saved = prefs.items.find(
          (p) => p.type === "PERIOD" && p.key === "UI-11",
        )?.value as { type?: string; value?: string } | undefined;
        const current = Number(ctx.currentMonth.slice(0, 4));
        const selected =
          saved?.type === "YEAR" &&
          saved.value &&
          /^[0-9]{4}$/.test(saved.value)
            ? Number(saved.value)
            : current;
        setYear(
          (old) =>
            old ??
            Math.max(
              Number(ctx.cashBaseMonth.slice(0, 4)),
              Math.min(current + 1, selected),
            ),
        );
        const amount = prefs.items.find(
          (p) => p.type === "AMT_UNIT" && p.key === "*",
        )?.value;
        setUnit(
          amount === "원"
            ? "WON"
            : amount === "백만원"
              ? "MILLION"
              : "THOUSAND",
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
    const changed = (event: Event) => {
      const d = (
        event as CustomEvent<{
          screenId: string;
          value: PeriodValue;
          context: PeriodContext;
        }>
      ).detail;
      if (d?.screenId === "UI-11" && d.value.type === "YEAR") {
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
      data-testid="staffing-comparison-screen"
      className="staffing-comparison"
    >
      {error ? (
        <ComparisonError error={error} retry={() => setRetry((n) => n + 1)} />
      ) : context && year ? (
        <StaffingComparisonContent key={year} year={year} amountUnit={unit} />
      ) : (
        <p role="status">조회 기간을 불러오는 중입니다.</p>
      )}
    </section>
  );
}
export function StaffingComparisonContent({
  year,
  amountUnit = "THOUSAND",
}: {
  year: number;
  amountUnit?: "WON" | "THOUSAND" | "MILLION";
}) {
  const shared = useSharedScreen("UI-11");
  const [draft, setDraft] = useState({
    projectId: "",
    difference: "ALL" as api.Difference,
  });
  const [filter, setFilter] = useState<api.ComparisonFilter>({
    year,
    difference: "ALL",
  });
  const [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(shared.pageSize ?? 50);
  const [dense, setDense] = useState(shared.dense ?? false);
  const [sort, setSort] = useState<{
    id: "differenceMm" | "projectId" | "jobCode";
    direction: "ASC" | "DESC";
  }>({ id: "differenceMm", direction: "ASC" });
  const [data, setData] = useState<api.ComparisonPage>();
  const [projects, setProjects] = useState<{ id: string; name: string }[]>([]);
  const [jobs, setJobs] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>(),
    [optionsError, setOptionsError] = useState<unknown>();
  const [loading, setLoading] = useState(true),
    [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    void Promise.all([
      api.comparisonProjects(year, controller.signal),
      getSharedCodes("JOB", true),
    ])
      .then(([p, c]) => {
        if (controller.signal.aborted) return;
        setProjects(p);
        setJobs(Object.fromEntries(c.items.map((v) => [v.code, v.name])));
        setOptionsError(undefined);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setOptionsError(e);
      });
    return () => controller.abort();
  }, [year, reload]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void api
      .getStaffingComparison(
        { ...filter, page, size, sort: sort.id, direction: sort.direction },
        controller.signal,
      )
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [filter, page, size, sort, reload]);
  useEffect(() => {
    const refresh = () => setReload((n) => n + 1);
    const events = [
      "sicost:projects-committed",
      "sicost:assignments-committed",
      "sicost:project-plans-committed",
      "sicost:sales-committed",
      "sicost:staffing-committed",
    ];
    events.forEach((e) => window.addEventListener(e, refresh));
    return () => events.forEach((e) => window.removeEventListener(e, refresh));
  }, []);
  const columns: Column<api.ComparisonRow>[] = [
    {
      id: "projectId",
      label: "프로젝트",
      sortable: true,
      render: (r) => (
        <Link
          className="project-link"
          data-testid={`comparison-project-${r.projectId.toLowerCase()}-${r.jobCode.toLowerCase()}`}
          to={`/UI-01?${new URLSearchParams({ projectId: r.projectId, tab: "plans", year: String(year) })}`}
        >
          {r.projectName}
        </Link>
      ),
    },
    {
      id: "jobCode",
      label: "직무",
      sortable: true,
      render: (r) => jobs[r.jobCode] ?? r.jobCode,
    },
    {
      id: "planMm",
      label: "계획 MM",
      align: "right",
      render: (r) => formatDecimal(r.planMm),
    },
    {
      id: "actualMm",
      label: "실제·예정 MM",
      align: "right",
      render: (r) => formatDecimal(r.actualMm),
    },
    {
      id: "differenceMm",
      label: "차이",
      align: "right",
      sortable: true,
      render: (r) => <ComparisonDifference value={r.differenceMm} />,
    },
  ];
  function reset() {
    setDraft({ projectId: "", difference: "ALL" });
    setFilter({ year, difference: "ALL" });
    setPage(1);
  }
  return (
    <div data-testid="staffing-comparison-content">
      <PeriodStatus className="sicost-period-summary">
        <span className="sicost-period-summary-item">
          <span className="sicost-period-summary-label">조회 범위</span>
          <b>{year}-01 ~ {year}-12</b>
        </span>
        <span className="sicost-period-summary-item">
          계획과 실제·예정 투입을 비교합니다. 프로젝트 상세는 전체 계약기간의
          계획과 비교합니다.
        </span>
      </PeriodStatus>
      <FilterBar
        testId="comparison-filters"
        resetTestId="comparison-reset"
        searchTestId="comparison-search"
        onSubmit={() => {
          setFilter({
            year,
            projectId: draft.projectId || undefined,
            difference: draft.difference,
          });
          setPage(1);
        }}
        onReset={reset}
      >
        <label>
          프로젝트
          <select
            data-testid="comparison-project-filter"
            value={draft.projectId}
            onChange={(e) => setDraft({ ...draft, projectId: e.target.value })}
          >
            <option value="">전체</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          차이
          <select
            data-testid="comparison-difference-filter"
            value={draft.difference}
            onChange={(e) =>
              setDraft({
                ...draft,
                difference: e.target.value as api.Difference,
              })
            }
          >
            <option value="ALL">전체</option>
            <option value="SHORT">부족</option>
            <option value="EXCESS">초과</option>
            <option value="MATCH">맞음</option>
          </select>
        </label>
      </FilterBar>
      {optionsError != null && (
        <ComparisonError
          error={optionsError}
          retry={() => setReload((n) => n + 1)}
        />
      )}
      {error != null && (
        <ComparisonError error={error} retry={() => setReload((n) => n + 1)} />
      )}
      <div className="staffing-comparison-toolbar">
        <strong>총 {data?.total ?? 0}건</strong>
        <button
          data-testid="comparison-density"
          type="button"
          aria-pressed={dense}
          onClick={() => setDense((v) => !v)}
        >
          {dense ? "넓게 보기" : "좁게 보기"}
        </button>
        <StaffingComparisonDownload
          filter={filter}
          amountUnit={amountUnit}
          disabled={loading || error != null}
        />
      </div>
      {error == null && (
        <DataTable
          testId="staffing-comparison-table"
          rows={data?.items ?? []}
          columns={columns}
          rowKey={(r) => `${r.projectId}-${r.jobCode}`}
          loading={loading}
          empty="조건에 맞는 결과가 없습니다."
          emptyAction={<button type="button" data-testid="comparison-empty-reset" onClick={reset}>조건 초기화</button>}
          dense={dense}
          page={page}
          size={size}
          total={data?.total ?? 0}
          onPageChange={setPage}
          onSizeChange={(v) => {
            setSize(v);
            setPage(1);
            void shared.savePageSize?.(v).catch(setError);
          }}
          sort={sort}
          onSortChange={(id) => {
            setSort({
              id: id as typeof sort.id,
              direction:
                sort.id === id && sort.direction === "ASC" ? "DESC" : "ASC",
            });
            setPage(1);
          }}
          footer={
            data
              ? {
                  label: "필터 전체 합계",
                  render: (column) =>
                    column.id === "projectId" ? (
                      "필터 전체 합계"
                    ) : column.id === "planMm" ? (
                      formatDecimal(data.totals.planMm)
                    ) : column.id === "actualMm" ? (
                      formatDecimal(data.totals.actualMm)
                    ) : column.id === "differenceMm" ? (
                      <ComparisonDifference value={data.totals.differenceMm} />
                    ) : null,
                }
              : undefined
          }
        />
      )}
    </div>
  );
}
export function ComparisonDifference({ value }: { value: string }) {
  const negative = value.startsWith("-") && !/^-0(?:\.0+)?$/.test(value);
  const zero = /^-?0(?:\.0+)?$/.test(value);
  return (
    <StatusBadge tone={zero ? "ok" : negative ? "bad" : "warn"}>
      {zero
        ? "맞음"
        : `${negative ? "부족" : "초과"} ${formatDecimal(value.replace(/^[-+]/, ""))}`}
    </StatusBadge>
  );
}
function ComparisonError({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  return (
    <ErrorBanner
      message={
        error instanceof Error
          ? error.message
          : "조회에 실패했습니다. 다시 조회해 주세요."
      }
      requestId={error instanceof ApiError ? error.body.requestId : undefined}
      onRetry={retry}
    />
  );
}
export function StaffingComparisonDownload({
  filter,
  amountUnit,
  disabled,
}: {
  filter: api.ComparisonFilter;
  amountUnit: "WON" | "THOUSAND" | "MILLION";
  disabled?: boolean;
}) {
  const active = useRef<AbortController>();
  const [pending, setPending] = useState(false),
    [error, setError] = useState<unknown>(),
    [success, setSuccess] = useState(false);
  useEffect(() => () => active.current?.abort(), []);
  async function download() {
    if (window.innerWidth <= 767 || active.current) return;
    const controller = new AbortController();
    active.current = controller;
    setPending(true);
    setError(undefined);
    setSuccess(false);
    try {
      const file = await api.downloadStaffingComparison(
        filter,
        amountUnit,
        controller.signal,
      );
      if (!controller.signal.aborted) {
        api.saveStaffingComparison(file);
        setSuccess(true);
      }
    } catch (e) {
      if (!controller.signal.aborted) setError(e);
    } finally {
      if (active.current === controller) active.current = undefined;
      if (!controller.signal.aborted) setPending(false);
    }
  }
  return (
    <div
      className="staffing-comparison-download"
      data-testid="comparison-download"
    >
      <button
        data-testid="comparison-download-button"
        type="button"
        disabled={disabled || pending}
        onClick={() => void download()}
      >
        {pending ? "엑셀 생성 중…" : "엑셀 다운로드"}
      </button>
      {pending && (
        <button
          data-testid="comparison-download-cancel"
          type="button"
          onClick={() => {
            active.current?.abort();
            active.current = undefined;
            setPending(false);
          }}
        >
          취소
        </button>
      )}
      {error != null && <ComparisonError error={error} />}
      {success && <span role="status">필터 전체 결과를 내려받았습니다.</span>}
    </div>
  );
}
