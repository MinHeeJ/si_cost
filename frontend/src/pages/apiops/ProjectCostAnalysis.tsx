import { PeriodStatus } from "../../shared/PeriodStatus";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { FieldHelp } from "../../shared/FieldHelp";
import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { type Column } from "../../shared/DataTable";
import { AnalysisTable as DataTable } from "./AnalysisTable";
import { FilterBar } from "../../shared/FilterBar";
import { StatusBadge } from "../../shared/Feedback";
import { validMonth } from "../../shared/format";
import { ScreenPeriodBar } from "../shared/ScreenPeriodBar";
import { getPeriodContext, type PeriodContext } from "../shared/sharedApi";
import * as api from "./projectAnalysisApi";
import {
  AnalysisSummary,
  AnalysisCompleteness,
  AnalysisFailure,
  AnalysisProgress,
  analysisDisplay,
  useAnalysisLoad,
  useAnalysisSettings,
  analysisPageSize,
  type AnalysisSlots,
  type AnalysisUnit,
} from "./ProjectAnalysisParts";
import "./projectAnalysis.css";
import "../costanalysis/projectCost.css";
const fields = [
  ["cumulativeRevenue", "누적 매출", true],
  ["cumulativeProfit", "누적 이익", true],
  ["lowMarginCount", "저마진"],
  ["budgetOverCount", "예산 초과"],
  ["researchCost", "연구개발비", true],
] as const;
const financialColumns = [
  ["originalContractAmount", "원 계약금액"],
  ["contractAmount", "현재 계약금액"],
  ["planMm", "계획 MM"],
  ["actualMm", "실제 MM"],
  ["cumulativeRevenue", "누적 매출"],
  ["cumulativeCost", "누적 원가"],
  ["cumulativeProfit", "이익"],
  ["cumulativeMargin", "누적 마진율"],
  ["forecastMargin", "예상 최종 마진율"],
  ["monthlyMargin", "월간 마진율"],
  ["targetMargin", "목표 마진율"],
  ["targetGap", "목표 대비"],
  ["budgetAmount", "예산"],
  ["budgetUsage", "예산 소진율"],
  ["pricePerMm", "MM당 단가"],
  ["costPerMm", "MM당 원가"],
  ["profitPerMm", "MM당 이익"],
  ["freeMm", "무상지원 MM"],
  ["freeLaborCost", "무상 원가"],
] as const;
const columnDescriptions: Record<string, string> = {
  cumulativeRevenue:
    "턴키는 진행기준(원가÷예산), 월단위·파견은 귀속월 매출 누계",
  cumulativeCost:
    "인건비 배분 원가 + 직접 경비(팀비·프로젝트경비·추가 직접 경비)",
  forecastMargin:
    "턴키: (현재 계약금액 − 원가 누계 − 남은 기간 예상 원가) ÷ 현재 계약금액. 월단위·파견: 당월 마진율",
  budgetUsage:
    "원가 누계 ÷ 예산(프로젝트별 입력값, 기본값 계약금액 × (1 − 목표 마진율))",
};
function CostColumnHelp({ id, label }: { id: string; label: string }) {
  const tooltipId = useId();
  const [position, setPosition] = useState<{ left: number; top: number }>();
  const description = columnDescriptions[id];
  useEffect(() => {
    if (!position) return;
    const dismiss = () => setPosition(undefined);
    window.addEventListener("scroll", dismiss, true);
    window.addEventListener("resize", dismiss);
    return () => {
      window.removeEventListener("scroll", dismiss, true);
      window.removeEventListener("resize", dismiss);
    };
  }, [position]);
  if (!description) return <>{label}</>;
  const show = (target: HTMLElement) => {
    const rect = target.getBoundingClientRect();
    setPosition({
      left: Math.max(8, Math.min(rect.left, window.innerWidth - 368)),
      top: rect.bottom,
    });
  };
  return (
    <>
      {label}{" "}
      <span
        className="cost-column-help"
        data-testid={`project-cost-help-${id.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
        tabIndex={0}
        aria-label={`${label} 설명`}
        aria-describedby={position ? tooltipId : undefined}
        onMouseEnter={(event) => show(event.currentTarget)}
        onMouseLeave={() => setPosition(undefined)}
        onFocus={(event) => show(event.currentTarget)}
        onBlur={() => setPosition(undefined)}
        onKeyDown={(event) => {
          if (event.key === "Escape") {
            event.stopPropagation();
            setPosition(undefined);
          }
        }}
      >
        ⓘ
        {position &&
          createPortal(
            <span
              id={tooltipId}
              role="tooltip"
              className="cost-column-tooltip"
              style={position}
            >
              {description}
            </span>,
            document.body,
          )}
      </span>
    </>
  );
}
const sorts = new Set([
  "projectName",
  "contractAmount",
  "actualMm",
  "cumulativeRevenue",
  "cumulativeCost",
  "cumulativeProfit",
  "cumulativeMargin",
  "budgetUsage",
]);
const types: Record<string, string> = {
  TK: "턴키",
  MO: "월단위",
  DP: "파견",
  IN: "내부",
};
export type ProjectCostAnalysisProps = AnalysisSlots & {
  renderDetail?(
    projectId: string,
    month: string,
    onClose: () => void,
    unit: AnalysisUnit,
  ): ReactNode;
};
export function ProjectCostAnalysis({
  renderDetail,
  renderColumns,
  renderExport,
  columnView,
  dense,
  pageSize,
  onPageSizeChange,
  amountUnit,
  onAmountUnitChange,
  initialMonth,
  onMonthChange,
}: ProjectCostAnalysisProps = {}) {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const activePath = useRef(location.pathname);
  activePath.current = location.pathname;
  useEffect(() => {
    activePath.current = location.pathname;
    return () => {
      activePath.current = "";
    };
  }, [location.pathname]);
  const [sizeOverride, setSizeOverride] = useState<20 | 50 | 100>();
  const initial = Object.fromEntries(params.entries());
  const query: api.AnalysisQuery = Object.fromEntries(
    Object.entries(initial).filter(([key]) =>
      [
        "month",
        "projectId",
        "contractType",
        "status",
        "alertOnly",
        "page",
        "size",
        "sort",
        "direction",
      ].includes(key),
    ),
  );
  for (const key of ["page", "size"] as const)
    if (/^[1-9]\d*$/.test(initial[key] ?? "")) {
      const value = parseInt(initial[key], 10);
      if (
        Number.isSafeInteger(value) &&
        (key === "page" || [20, 50, 100].includes(value))
      )
        Object.assign(query, { [key]: value });
    }
  if (initial.alertOnly === "true" || initial.alertOnly === "false")
    query.alertOnly = initial.alertOnly === "true";
  if (pageSize !== undefined) query.size ??= analysisPageSize(pageSize);
  if (sizeOverride !== undefined)
    Object.assign(query, { size: sizeOverride, page: 1 });
  if (initialMonth !== undefined) query.month ??= initialMonth;
  const settings = useAnalysisSettings();
  const previousSize = useRef(pageSize);
  useEffect(() => {
    if (previousSize.current !== pageSize) {
      previousSize.current = pageSize;
      setSizeOverride(analysisPageSize(pageSize ?? 50));
    }
  }, [pageSize]);
  useEffect(() => {
    if (location.pathname === "/UI-04") setSizeOverride(undefined);
  }, [location.key]);
  const queryKey = api.analysisQuery(query);
  const [draft, setDraft] = useState(query),
    [context, setContext] = useState<PeriodContext>(),
    [unit, setUnit] = useState<AnalysisUnit>(amountUnit ?? "천원"),
    [options, setOptions] = useState<
      { projectId: string; projectName: string }[]
    >([]);
  useEffect(() => {
    if (amountUnit) setUnit(amountUnit);
  }, [amountUnit]);
  useEffect(() => {
    setDraft(query);
  }, [queryKey]);
  const result = useAnalysisLoad(
    (signal) => api.getProjectCosts(query, signal),
    queryKey,
  );
  const selected =
    params.get("detailProjectId") ??
    (params.has("costTab") ? params.get("projectId") : null);
  useEffect(() => {
    let live = true;
    void getPeriodContext()
      .then((value) => {
        if (live) setContext(value);
      })
      .catch(() => {});
    return () => {
      live = false;
    };
  }, []);
  const month = String(query.month ?? result.data?.month ?? "");
  const currentResult =
    !result.error && result.data?.month === month ? result.data : undefined;
  useEffect(() => {
    if (!month) return;
    const controller = new AbortController();
    let live = true;
    void (async () => {
      const first = await api.getProjectCosts(
        { month, size: 100, page: 1 },
        controller.signal,
      );
      let items = first.items;
      for (let page = 2; items.length < first.total; page++) {
        const next = await api.getProjectCosts(
          { month, size: 100, page },
          controller.signal,
        );
        const unseen = next.items.filter(
          (row) =>
            !items.some((previous) => previous.projectId === row.projectId),
        );
        if (!unseen.length) break;
        items = [...items, ...unseen];
      }
      if (live)
        setOptions(
          items.map((row) => ({
            projectId: row.projectId,
            projectName: row.projectName,
          })),
        );
    })().catch(() => {
      if (live) setOptions([]);
    });
    return () => {
      live = false;
      controller.abort();
    };
  }, [month]);
  function apply(next: api.AnalysisQuery) {
    setSizeOverride(undefined);
    setDraft(next);
    if (activePath.current !== "/UI-04") return;
    const url = new URLSearchParams(api.analysisQuery(next));
    if (selected) {
      url.set("detailProjectId", selected);
      const tab = params.get("costTab");
      if (tab) url.set("costTab", tab);
    }
    const returnTo = params.get("returnTo");
    if (returnTo) url.set("returnTo", returnTo);
    setParams(url, {
      replace: true,
      preventScrollReset: true,
      state: location.state,
    });
  }
  function detail(id: string | null) {
    const url = new URLSearchParams(params);
    if (id) url.set("detailProjectId", id);
    else {
      url.delete("detailProjectId");
      url.delete("costTab");
    }
    setParams(url, {
      replace: true,
      preventScrollReset: true,
      state: location.state,
    });
  }
  const change = (key: string, value: string) =>
    setDraft((valueBefore) => ({ ...valueBefore, [key]: value || undefined }));
  const columns: Column<api.CostRow>[] = [
    {
      id: "projectName",
      label: "프로젝트",
      sortable: true,
      render: (row) => (
        <>
          <span
            data-testid={`project-cost-open-${row.projectId.toLowerCase()}`}
          >
            {row.projectName}
          </span>
          <small>{row.projectId}</small>
          {!row.completeness.costComplete && (
            <StatusBadge tone="warn">원가 미확인</StatusBadge>
          )}
        </>
      ),
    },
    {
      id: "contractType",
      label: "유형",
      render: (row) => (
        <StatusBadge tone="neutral">
          {types[row.contractType] ?? row.contractType}
        </StatusBadge>
      ),
    },
    {
      id: "status",
      label: "상태",
      render: (row) => (
        <StatusBadge tone={row.status === "END" ? "lock" : "info"}>
          {row.status === "END" ? "종료" : "진행"}
        </StatusBadge>
      ),
    },
    ...financialColumns.map(
      ([id, label]): Column<api.CostRow> => ({
        id,
        label: <CostColumnHelp id={id} label={label} />,
        sortable: sorts.has(id),
        align: "right",
        render: (row) => (
          <span
            className={
              String(row[id] ?? "").startsWith("-")
                ? "analysis-negative"
                : undefined
            }
          >
            {id === "budgetUsage" ? (
              <>
                <AnalysisProgress label="소진율" value={row[id]} />
                {row.budgetWarning && (
                  <StatusBadge tone="warn">예산 주의</StatusBadge>
                )}
              </>
            ) : id === "planMm" && row[id] == null ? (
              "계획 없음"
            ) : id === "targetGap" ? (
              row[id] == null ? (
                "–"
              ) : (
                `${analysisDisplay(row[id])}%p`
              )
            ) : (
              analysisDisplay(
                row[id],
                /Margin|Usage/.test(id)
                  ? "rate"
                  : ["planMm", "actualMm", "freeMm", "targetGap"].includes(id)
                    ? "text"
                    : "amount",
                unit,
              )
            )}
          </span>
        ),
      }),
    ),
    {
      id: "alerts",
      label: "경보",
      render: (row) => (
        <>
          {row.contractType === "IN"
            ? "–"
            : row.alerts.map((alert) => (
                <span
                  data-testid={`project-cost-alert-${Array.from(`${row.projectId}-${alert.kind}`, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                  key={alert.kind}
                >
                  <HelpTooltip label={alert.message} trigger={<StatusBadge tone={alert.kind === "BUDGET_OVER" ? "bad" : "warn"}>{alert.message}</StatusBadge>}>
                    {alert.comparison} · {alert.actualNumerator} / {alert.actualDenominator} · 기준 {alert.threshold}
                  </HelpTooltip>
                </span>
              ))}
          {row.primaryCause && <small>{row.primaryCause}</small>}
        </>
      ),
    },
  ];
  const visibleColumns = columnView
    ? columnView.order.flatMap((id) => {
        const column = columns.find((value) => value.id === id);
        return column &&
          (id === "projectName" || !columnView.hidden.includes(id))
          ? [column]
          : [];
      })
    : columns;
  return (
    <div
      data-testid="project-cost-analysis"
      data-screen-id="SCR-UI-04"
      data-slice-id="SLICE-001"
      className="project-analysis project-cost-analysis"
    >
      {(context || currentResult) && (
        <div className="sicost-period-row">
          {context && (
            <ScreenPeriodBar
              screenId="UI-04"
              controlled={{
                month: month || context.currentMonth,
                minMonth:
                  validMonth(month) && month < context.cashBaseMonth
                    ? month
                    : context.cashBaseMonth,
                currentMonth: context.currentMonth,
                disabled: settings.pending,
                onChange: (value) =>
                  void settings.run(async () => {
                    await onMonthChange?.(value);
                    apply({
                      ...query,
                      month: value,
                      projectId: undefined,
                      page: 1,
                    });
                  }),
              }}
            />
          )}
          {currentResult && (
            <div className="sicost-period-status-slot">
              <PeriodStatus className="sicost-period-summary" inline>
                <span className="sicost-period-summary-item">
                  <span className="sicost-period-summary-label">집계 기간</span>
                  <b>
                    {currentResult.summary.completeness.rangeStartMonth} ~{" "}
                    {currentResult.summary.completeness.rangeEndMonth}
                  </b>
                </span>
                {currentResult.items.length > 0 && (
                  <span className="sicost-period-summary-item">
                    행을 누르면 월별 원가·시뮬레이션을 볼 수 있습니다.
                  </span>
                )}
              </PeriodStatus>
            </div>
          )}
        </div>
      )}
      <ScreenTitleActions>
        <div className="project-analysis-toolbar">
          <label>
            단위{" "}
            <select
              aria-label="금액 단위"
              data-testid="project-cost-unit"
              value={unit}
              disabled={settings.pending}
              onChange={(e) => {
                const value = e.target.value as AnalysisUnit;
                if (!onAmountUnitChange) {
                  setUnit(value);
                  return;
                }
                void settings.run(async () => {
                  await onAmountUnitChange?.(value);
                  setUnit(value);
                });
              }}
            >
              {["원", "천원", "백만원"].map((value) => (
                <option
                  data-testid={`project-cost-unit-option-${Array.from(value, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                  key={value}
                >
                  {value}
                </option>
              ))}
            </select>
          </label>
          {renderExport?.(query, unit)}
        </div>
      </ScreenTitleActions>
      {result.data && (
        <>
          <AnalysisSummary
            fields={fields}
            summary={result.data.summary}
            unit={unit}
          />
          <AnalysisCompleteness
            value={result.data.summary.completeness}
            showRange={false}
          />
        </>
      )}
      {renderColumns && (
        <div
          className="project-analysis-toolbar"
          data-testid="project-cost-column-toolbar"
        >
          {renderColumns()}
        </div>
      )}
      <FilterBar
        testId="project-cost-filters"
        resetTestId="project-cost-reset"
        searchTestId="project-cost-search"
        onSubmit={() => apply({ ...draft, page: 1 })}
        onReset={() =>
          apply({
            month: context?.currentMonth,
            page: 1,
            size: analysisPageSize(pageSize ?? 50),
          })
        }
      >
        <label className="sicost-field">
          프로젝트명
          <select
            data-testid="project-cost-project"
            value={String(draft.projectId ?? "")}
            onChange={(e) => change("projectId", e.target.value)}
          >
            <option value="">전체</option>
            {options.map((option) => (
              <option
                data-testid={`project-cost-project-option-${Array.from(option.projectId, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                key={option.projectId}
                value={option.projectId}
              >
                {option.projectName}
              </option>
            ))}
          </select>
        </label>
        <label className="sicost-field">
          계약유형
          <select
            data-testid="project-cost-type"
            value={String(draft.contractType ?? "")}
            onChange={(e) => change("contractType", e.target.value)}
          >
            <option value="">전체</option>
            {Object.entries(types).map(([id, label]) => (
              <option
                data-testid={`project-cost-type-option-${Array.from(id, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                key={id}
                value={id}
              >
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="sicost-field">
          상태
          <FieldHelp label="상태" /><select
            data-testid="project-cost-status"
            value={String(draft.status ?? "")}
            onChange={(e) => change("status", e.target.value)}
          >
            <option value="">전체</option>
            <option value="ING">진행</option>
            <option value="END">종료</option>
          </select>
        </label>
        <label className="sicost-field">
          <input
            type="checkbox"
            data-testid="project-cost-alert"
            checked={draft.alertOnly === true || draft.alertOnly === "true"}
            onChange={(e) =>
              setDraft({ ...draft, alertOnly: e.target.checked })
            }
          />
          경보 있는 것만
        </label>
      </FilterBar>
      <AnalysisFailure error={settings.error} retry={settings.clear} />
      <AnalysisFailure error={result.error} retry={result.retry} />
      <DataTable
        testId="project-cost-table"
        dense={dense}
        rows={result.error ? [] : (result.data?.items ?? [])}
        columns={visibleColumns}
        rowKey={(row) => row.projectId}
        onRowAction={(row) => detail(row.projectId)}
        mobileRowAction
        loading={result.loading}
        total={result.data?.total ?? 0}
        page={result.data?.page ?? 1}
        size={result.data?.size ?? 50}
        onPageChange={(page) => apply({ ...query, page })}
        onSizeChange={(size) => {
          void settings.run(async () => {
            if (window.innerWidth > 767) await onPageSizeChange?.(size);
            apply({ ...query, size, page: 1 });
          });
        }}
        sort={
          query.sort
            ? {
                id: String(query.sort),
                direction: query.direction === "DESC" ? "DESC" : "ASC",
              }
            : undefined
        }
        onSortChange={(id) =>
          apply({
            ...query,
            sort: id,
            direction:
              query.sort === id && query.direction !== "DESC" ? "DESC" : "ASC",
            page: 1,
          })
        }
        empty="조건에 맞는 프로젝트가 없습니다."
        emptyAction={
          <button
            data-testid="project-cost-empty-reset-button"
            type="button"
            onClick={() =>
              apply({
                month: context?.currentMonth,
                page: 1,
                size: analysisPageSize(pageSize ?? 50),
              })
            }
          >
            조건 초기화
          </button>
        }
      />
      {selected &&
        (renderDetail ? (
          renderDetail(selected, month, () => detail(null), unit)
        ) : (
          <section
            data-testid="project-cost-selection"
            className="sicost-banner sicost-tone-info"
          >
            <span>선택 프로젝트의 상세 원가·진행 이력을 확인하세요.</span>
            <Link
              data-testid="project-cost-project-info"
              to={`/UI-01?${new URLSearchParams({ projectId: selected, tab: "basic", returnTo: `/UI-04?${params}` })}`}
            >
              프로젝트 정보
            </Link>
            <button
              data-testid="project-cost-close"
              type="button"
              onClick={() => detail(null)}
            >
              닫기
            </button>
          </section>
        ))}
    </div>
  );
}
