import { HelpTooltip } from "../../shared/HelpTooltip";
import { FieldHelp } from "../../shared/FieldHelp";
import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  Link,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import { type Column } from "../../shared/DataTable";
import { AnalysisTable as DataTable } from "./AnalysisTable";
import { FilterBar } from "../../shared/FilterBar";
import { StatusBadge } from "../../shared/Feedback";
import { FinancialWarnings } from "../../shared/FinancialWarnings";
import { CalculationMethods } from "../../shared/CalculationMethods";
import { KpiStrip } from "../../shared/Surface";
import { CompactPeriodSummary } from "../../shared/PeriodStatus";
import {
  getSalesPartOptions,
  type PartOption,
} from "../salespartsops/partStandardsApi";
import * as api from "./projectAnalysisApi";
import { noWeeklyMemo, progressReasonText } from "../progress/progressFormat";
import { usePermission } from "../../shared/permission/PermissionBoundary";
import {
  AnalysisCompleteness,
  AnalysisFailure,
  AnalysisProgress,
  analysisDisplay,
  useAnalysisLoad,
  useAnalysisSettings,
  analysisPageSize,
  type AnalysisSlots,
} from "./ProjectAnalysisParts";
import "./projectAnalysis.css";
function ProjectBoardOverview({
  data,
  showBaseDate,
}: {
  data: api.BoardPage;
  showBaseDate: boolean;
}) {
  const { summary } = data;
  return (
    <>
      <CompactPeriodSummary
        items={[
          ...(showBaseDate ? [{ label: "기준일", value: data.baseDate }] : []),
          { label: "원가 기준월", value: data.currentMonth },
        ]}
      />
      <KpiStrip className="project-status-kpis">
        <section>
          <span>진행 프로젝트</span>
          <strong>{analysisDisplay(summary.projectCount)}</strong>
          <div className="project-status-kpi-badges">
            <StatusBadge tone="ok">
              정상 {analysisDisplay(summary.normalCount)}
            </StatusBadge>
            <StatusBadge tone="warn">
              주의 {analysisDisplay(summary.warningCount)}
            </StatusBadge>
            <StatusBadge tone="bad">
              <span data-testid="analysis-summary-danger-count">
                위험 {analysisDisplay(summary.dangerCount)}
              </span>
            </StatusBadge>
          </div>
        </section>
        <section>
          <span>열린 이슈</span>
          <strong>{analysisDisplay(summary.openIssueCount)}</strong>
          <div className="project-status-kpi-badges">
            <StatusBadge
              tone={Number(summary.openHighIssueCount) > 0 ? "warn" : "neutral"}
            >
              높음 {analysisDisplay(summary.openHighIssueCount)}
            </StatusBadge>
          </div>
        </section>
        <section>
          <span>기한 지난 이슈</span>
          <strong>{analysisDisplay(summary.overdueIssueCount)}</strong>
        </section>
        <section>
          <span>원가 미확인</span>
          <strong>{analysisDisplay(summary.unknownCostProjectCount)}</strong>
        </section>
      </KpiStrip>
    </>
  );
}
const types: Record<string, string> = {
  TK: "턴키",
  MO: "월단위",
  DP: "파견",
  IN: "내부",
};
const calculationMethods = [
  {
    metric: "일정 지연",
    formula: "계획 진척률 − 실적 진척률",
    reference: "10.0%p 이상 위험, 5.0%p 이상 주의",
  },
  {
    metric: "진척 대비 원가",
    formula: "턴키 원가 사용률 − 실적 진척률",
    reference: "15.0%p 이상 위험, 5.0%p 이상 주의",
  },
  {
    metric: "기한 지난 이슈",
    formula: "기준일 전에 기한이 지난 미종결 이슈 건수",
    reference: "1건 이상이면 주의, 그중 중요도 높음이 1건 이상이면 위험",
  },
  {
    metric: "주간 메모 경과일",
    formula: "기준일 − 마지막 메모 작성일",
    reference: "14일 초과 시 주의. 메모가 없으면 등록일과 계약 시작일 중 늦은 날부터 계산",
  },
] as const;
export type ProjectStatusBoardProps = AnalysisSlots & {
  renderIssues?(query: api.AnalysisQuery, onChanged: () => void): ReactNode;
};
export function ProjectStatusBoard({
  renderIssues,
  renderColumns,
  renderExport,
  columnView,
  dense,
  pageSize,
  onPageSizeChange,
}: ProjectStatusBoardProps = {}) {
  const canProjects = usePermission("UI-01", "INQ");
  const navigate = useNavigate();
  const canCosts = usePermission("UI-04", "INQ");
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
  const [query, setQuery] = useState<api.AnalysisQuery>(
    Object.assign(
      pageSize === undefined ? {} : { size: analysisPageSize(pageSize) },
      Object.fromEntries(
        [...params.entries()].filter(([key]) =>
          [
            "light",
            "contractType",
            "partId",
            "page",
            "size",
            "sort",
            "direction",
          ].includes(key),
        ),
      ),
    ),
  );
  const settings = useAnalysisSettings();
  const previousSize = useRef(pageSize);
  useEffect(() => {
    if (previousSize.current !== pageSize) {
      previousSize.current = pageSize;
      const next = {
        ...query,
        size: analysisPageSize(pageSize ?? 50),
        page: 1,
      };
      setQuery(next);
      setDraft(next);
    }
  }, [pageSize]);
  const [draft, setDraft] = useState(query),
    [parts, setParts] = useState<PartOption[]>([]),
    [tab, setTab] = useState(
      params.get("view") === "issues" ? "issues" : "board",
    );
  const result = useAnalysisLoad(
    (signal) => api.getProjectStatusBoard(query, signal),
    api.analysisQuery(query),
  );
  useEffect(() => {
    const controller = new AbortController();
    void getSalesPartOptions(controller.signal)
      .then((value) => setParts(value.items))
      .catch(() => {});
    return () => controller.abort();
  }, []);
  function apply(next: api.AnalysisQuery) {
    setQuery(next);
    setDraft(next);
    if (activePath.current !== "/UI-31") return;
    const url = new URLSearchParams(api.analysisQuery(next));
    for (const key of [
      "issueStatus",
      "importance",
      "issueProjectId",
      "issuePage",
      "issueSize",
    ]) {
      const value = params.get(key);
      if (value !== null) url.set(key, value);
    }
    url.set("view", tab);
    setParams(url, { replace: true, preventScrollReset: true });
  }
  function selectTab(next: string) {
    setTab(next);
    if (activePath.current !== "/UI-31") return;
    const url = new URLSearchParams(params);
    url.set("view", next);
    setParams(url, { replace: true, preventScrollReset: true });
  }
  const change = (key: string, value: string) =>
    setDraft((current) => ({ ...current, [key]: value || undefined }));
  const projectLink = (row: api.BoardRow) =>
    `/UI-01?${new URLSearchParams({ projectId: row.projectId, tab: "progress", returnTo: `/UI-31?${params}` })}`;
  const columns: Column<api.BoardRow>[] = [
    {
      id: "light",
      label: "신호등",
      sortable: true,
      render: (row) => (
        <>
          <StatusBadge
            tone={
              row.light === "위험"
                ? "bad"
                : row.light === "주의"
                  ? "warn"
                  : "ok"
            }
          >
            {row.light}
          </StatusBadge>
          {row.costUnknown && (
            <StatusBadge tone="neutral">원가 미확인</StatusBadge>
          )}
          <ul className="project-analysis-reasons">
            {row.reasons.map((reason, index) => (
              <li
                data-testid={`status-reason-${Array.from(row.projectId, (c) => c.codePointAt(0)!.toString(16)).join("-")}-${index}`}
                key={`${reason.kind}-${index}`}
              >
                {(() => {
                  const name =
                    reason.kind === "기한지난HIGH"
                      ? "기한 지난 높음 이슈"
                      : reason.kind === "기한지난이슈"
                        ? "기한 지난 이슈"
                        : reason.kind;
                  return (
                    <HelpTooltip
                      label={progressReasonText(name, reason.value)}
                      trigger={
                        <StatusBadge
                          tone={reason.light === "위험" ? "bad" : "warn"}
                        >
                          {progressReasonText(name, reason.value)}
                        </StatusBadge>
                      }
                    >
                      {reason.kind === "기한지난HIGH"
                        ? "기한 지난 높음 이슈가 있습니다."
                        : `${reason.formula} · 기준 ${reason.threshold}`}
                    </HelpTooltip>
                  );
                })()}
              </li>
            ))}
          </ul>
        </>
      ),
    },
    {
      id: "projectName",
      label: "프로젝트",
      sortable: true,
      render: (row) => (
        <>
          {canProjects ? (
            <Link
              className="project-link"
              data-testid={`status-project-${row.projectId.toLowerCase()}`}
              onClick={(event) => event.stopPropagation()}
              to={projectLink(row)}
            >
              {row.projectName}
            </Link>
          ) : (
            row.projectName
          )}
          <small>
            {row.projectId} · {types[row.contractType]}
          </small>
          {row.sourceStatus === "PRE" ? (
            <>
              <StatusBadge tone="info">선투입</StatusBadge>
              <small>
                선투입은 일반 원가 목록에서 제외됩니다. 진행 상세에서
                확인하세요.
              </small>
            </>
          ) : canCosts ? (
            <Link
              className="project-link"
              data-testid={`status-cost-${row.projectId.toLowerCase()}`}
              onClick={(event) => event.stopPropagation()}
              to={`/UI-04?${new URLSearchParams({ month: result.data?.currentMonth ?? "", projectId: row.projectId, detailProjectId: row.projectId, returnTo: `/UI-31?${params}` })}`}
            >
              원가 분석
            </Link>
          ) : null}
        </>
      ),
    },
    {
      id: "pm",
      label: "PM",
      render: (row) =>
        row.pm.map((person) => person.userName).join(", ") || "–",
    },
    {
      id: "currentStep",
      label: "현재 단계",
      render: (row) => (
        <>
          {row.allStepsCompleted ? (
            "모든 단계 완료"
          ) : row.currentStep ? (
            <>
              <span>{row.currentStep.stepName}</span>
              <StatusBadge
                tone={row.currentStep.status === "지연" ? "bad" : "info"}
              >
                {row.currentStep.status}
              </StatusBadge>
            </>
          ) : canProjects ? (
            <Link
              className="project-link"
              data-testid={`status-stage-${row.projectId.toLowerCase()}`}
              onClick={(event) => event.stopPropagation()}
              to={projectLink(row)}
            >
              기본 단계 미생성
            </Link>
          ) : (
            "기본 단계 미생성"
          )}
          <small>{row.planBasis}</small>
        </>
      ),
    },
    {
      id: "planProgress",
      label: "계획 진척률",
      sortable: true,
      render: (row) => (
        <AnalysisProgress label="계획" value={row.planProgress} />
      ),
    },
    {
      id: "actualProgress",
      label: "실적 진척률",
      sortable: true,
      render: (row) => (
        <AnalysisProgress label="실적" value={row.actualProgress} />
      ),
    },
    {
      id: "costUsage",
      label: "원가 사용률",
      sortable: true,
      render: (row) => (
        <AnalysisProgress label="TK 소진율" value={row.costUsage} />
      ),
    },
    {
      id: "costProgressGap",
      label: "진척 대비 원가 (%p)",
      render: (row) => analysisDisplay(row.costProgressGap),
    },
    {
      id: "openIssueCount",
      label: "열린 이슈",
      sortable: true,
      render: (row) => analysisDisplay(row.openIssueCount),
    },
    {
      id: "overdueIssueCount",
      label: "기한 지난 이슈",
      render: (row) => (
        <StatusBadge tone={row.overdueIssueCount ? "bad" : "neutral"}>
          {analysisDisplay(row.overdueIssueCount)}
        </StatusBadge>
      ),
    },
    {
      id: "lastUpdatedDate",
      label: "마지막 업데이트",
      sortable: true,
      render: (row) =>
        row.lastUpdatedDate == null
          ? noWeeklyMemo(String(row.elapsedDays))
          : analysisDisplay(row.lastUpdatedDate),
    },
    {
      id: "elapsedDays",
      label: "경과일",
      render: (row) => (
        <StatusBadge tone={row.updateWarning ? "warn" : "neutral"}>
          {analysisDisplay(row.elapsedDays)}일 전
        </StatusBadge>
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
  const staleWarnings = (result.data?.staleProjects ?? []).map((project) => {
    const elapsed = project.lastUpdatedDate == null
      ? noWeeklyMemo(project.elapsedDays)
      : `마지막 메모 작성 후 ${project.elapsedDays}일`;
    return `${project.projectName} · ${elapsed}`;
  });
  return (
    <div
      data-testid="project-status-board"
      data-screen-id="SCR-UI-31"
      data-slice-id="SLICE-002"
      className="project-analysis project-status-board"
    >
      {result.data && (
        <ProjectBoardOverview data={result.data} showBaseDate={tab === "board"} />
      )}
      <div className="project-status-view-row">
        <SegmentedTabList
          as="div"
          role="tablist"
          aria-label="프로젝트 현황 보기"
          className="project-analysis-tabs"
        >
          <button
            type="button"
            role="tab"
            aria-selected={tab === "board"}
            data-testid="status-board-tab"
            onClick={() => selectTab("board")}
          >
            현황판
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === "issues"}
            data-testid="status-issues-tab"
            onClick={() => selectTab("issues")}
          >
            이슈 모아 보기 ({result.data?.summary.openIssueCount ?? "–"})
          </button>
        </SegmentedTabList>
        {staleWarnings.length > 0 && (
          <FinancialWarnings
            warnings={staleWarnings}
            labelPrefix="14일 이내 업데이트가 없는 프로젝트"
            description="14일 넘게 업데이트가 없는 프로젝트입니다."
            groupTitle="업데이트 지연"
          />
        )}
      </div>
      {tab === "board" && (
        <FilterBar
          testId="status-filters"
          resetTestId="status-reset"
          searchTestId="status-search"
          onSubmit={() => apply({ ...draft, page: 1 })}
          onReset={() =>
            apply({ page: 1, size: analysisPageSize(pageSize ?? 50) })
          }
        >
          <label className="sicost-field">
            신호등
            <select
              data-testid="status-light"
              value={String(draft.light ?? "")}
              onChange={(e) => change("light", e.target.value)}
            >
              <option value="">전체</option>
              {["위험", "주의", "정상"].map((light) => (
                <option
                  data-testid={`status-light-option-${Array.from(light, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                  key={light}
                >
                  {light}
                </option>
              ))}
            </select>
          </label>
          <label className="sicost-field">
            계약유형
            <select
              data-testid="status-type"
              value={String(draft.contractType ?? "")}
              onChange={(e) => change("contractType", e.target.value)}
            >
              <option value="">전체</option>
              {Object.entries(types).map(([id, label]) => (
                <option
                  data-testid={`status-type-option-${Array.from(id, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                  key={id}
                  value={id}
                >
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="sicost-field">
            영업 파트
            <FieldHelp label="영업 파트" />
            <select
              data-testid="status-part"
              value={String(draft.partId ?? "")}
              onChange={(e) => change("partId", e.target.value)}
            >
              <option value="">전체</option>
              <option value="UNASSIGNED">미지정</option>
              {parts.map((part) => (
                <option
                  data-testid={`status-part-option-${Array.from(String(part.id), (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                  key={part.id}
                  value={part.id}
                >
                  {part.name}
                  {part.hqYn === "Y" ? " (본사 직접 영업)" : ""}
                </option>
              ))}
            </select>
          </label>
        </FilterBar>
      )}
      <AnalysisFailure error={settings.error} retry={settings.clear} />
      <AnalysisFailure error={result.error} retry={result.retry} />
      {tab === "board" && (
        <div className="project-analysis-toolbar">
          <strong>총 {result.data?.total ?? 0}건</strong>
          {renderColumns?.()}
          {renderExport?.(query)}
        </div>
      )}
      <section
        role="tabpanel"
        className="project-status-board-table"
        data-testid="status-board-panel"
        hidden={tab !== "board"}
      >
        <DataTable
          testId="status-board-table"
          onRowAction={
            canProjects ? (row) => navigate(projectLink(row)) : undefined
          }
          mobileRowAction={canProjects}
          dense={dense}
          rows={result.error ? [] : (result.data?.items ?? [])}
          columns={visibleColumns}
          rowKey={(row) => row.projectId}
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
                query.sort === id && query.direction !== "DESC"
                  ? "DESC"
                  : "ASC",
              page: 1,
            })
          }
          empty="조건에 맞는 결과가 없습니다."
          emptyAction={
            <button
              data-testid="status-empty-reset"
              type="button"
              onClick={() =>
                apply({ page: 1, size: analysisPageSize(pageSize ?? 50) })
              }
            >
              조건 초기화
            </button>
          }
        />
      </section>
      <section
        role="tabpanel"
        className="project-status-issues-table"
        data-testid="status-issues-panel"
        hidden={tab !== "issues"}
      >
        {tab === "issues" &&
          (renderIssues?.(query, result.retry) ?? (
            <p>프로젝트를 선택해 진행 상세에서 이슈를 확인하세요.</p>
          ))}
      </section>
      <CalculationMethods
        testId="status-calculation-panel"
        rows={calculationMethods}
      >
        {result.data?.calculationNotice && <p>{result.data.calculationNotice}</p>}
        <p>해당하는 신호등 사유는 모두 표시합니다.</p>
        {result.data?.items
          .filter((row) => row.completeness.issues.length > 0)
          .map((row) => (
            <section
              key={row.projectId}
              data-testid={`status-basis-${row.projectId.toLowerCase()}`}
            >
              <h3>{row.projectName}</h3>
              <AnalysisCompleteness value={row.completeness} />
            </section>
          ))}
      </CalculationMethods>
    </div>
  );
}
