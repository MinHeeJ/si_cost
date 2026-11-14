import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { type Column } from "../../shared/DataTable";
import { AnalysisTable as DataTable } from "./AnalysisTable";
import { FilterBar } from "../../shared/FilterBar";
import { StatusBadge } from "../../shared/Feedback";
import {
  getSalesPartOptions,
  type PartOption,
} from "../salespartsops/partStandardsApi";
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
} from "./ProjectAnalysisParts";
import "./projectAnalysis.css";
const fields = [
  ["projectCount", "진행 프로젝트"],
  ["dangerCount", "위험"],
  ["openIssueCount", "열린 이슈"],
  ["overdueIssueCount", "기한 지남"],
  ["staleProjectCount", "14일 초과 업데이트 없음"],
] as const;
const types: Record<string, string> = {
  TK: "턴키",
  MO: "월단위",
  DP: "파견",
  IN: "내부",
};
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
  const [params, setParams] = useSearchParams();
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
      apply({ ...query, size: analysisPageSize(pageSize ?? 50), page: 1 });
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
                data-testid={`status-reason-${row.projectId.toLowerCase()}-${index}`}
                key={`${reason.kind}-${index}`}
                title={`${reason.formula} · 기준 ${reason.threshold}`}
              >
                <StatusBadge tone={reason.light === "위험" ? "bad" : "warn"}>
                  {reason.kind} {reason.value}
                </StatusBadge>
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
          <Link
            data-testid={`status-project-${row.projectId.toLowerCase()}`}
            to={projectLink(row)}
          >
            {row.projectName}
          </Link>
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
          ) : (
            <Link
              data-testid={`status-cost-${row.projectId.toLowerCase()}`}
              to={`/UI-04?${new URLSearchParams({ month: result.data?.currentMonth ?? "", projectId: row.projectId, detailProjectId: row.projectId, returnTo: `/UI-31?${params}` })}`}
            >
              원가 분석
            </Link>
          )}
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
          ) : (
            <Link
              data-testid={`status-stage-${row.projectId.toLowerCase()}`}
              to={projectLink(row)}
            >
              기본 단계 미생성
            </Link>
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
      render: (row) => analysisDisplay(row.lastUpdatedDate),
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
  return (
    <div
      data-testid="project-status-board"
      data-screen-id="SCR-UI-31"
      data-slice-id="SLICE-002"
      className="project-analysis"
    >
      {result.data && (
        <>
          <p>
            기준일 {result.data.baseDate} · Asia/Seoul · 원가 기준월{" "}
            {result.data.currentMonth}
          </p>
          <AnalysisSummary fields={fields} summary={result.data.summary} />
          <p>
            정상 {result.data.summary.normalCount} · 주의{" "}
            {result.data.summary.warningCount} · 위험{" "}
            {result.data.summary.dangerCount} · 열린 HIGH{" "}
            {result.data.summary.openHighIssueCount} · 원가 미확인{" "}
            {result.data.summary.unknownCostProjectCount}
          </p>
          {result.data.staleProjects.length > 0 && (
            <div
              className="sicost-banner sicost-tone-warn"
              data-testid="status-stale-banner"
            >
              <span>
                14일 초과 업데이트 없음:{" "}
                {result.data.staleProjects
                  .map(
                    (project) =>
                      `${project.projectName} (${project.elapsedDays}일)`,
                  )
                  .join(" · ")}
              </span>
            </div>
          )}
        </>
      )}
      <div
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
      </div>
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
                key={light}
                data-testid={`status-light-option-${["위험", "주의", "정상"].indexOf(light)}`}
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
                key={id}
                value={id}
                data-testid={`status-type-option-${id.toLowerCase()}`}
              >
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="sicost-field">
          영업 파트
          <select
            data-testid="status-part"
            value={String(draft.partId ?? "")}
            onChange={(e) => change("partId", e.target.value)}
          >
            <option value="">전체</option>
            <option value="UNASSIGNED">미지정</option>
            {parts.map((part) => (
              <option
                key={part.id}
                value={part.id}
                data-testid={`status-part-option-${part.id.toLowerCase()}`}
              >
                {part.name}
                {part.hqYn === "Y" ? " (본사 직접 영업)" : ""}
              </option>
            ))}
          </select>
        </label>
      </FilterBar>
      <AnalysisFailure error={settings.error} retry={settings.clear} />
      <AnalysisFailure error={result.error} retry={result.retry} />
      {tab === "board" && (
        <div className="project-analysis-toolbar">
          {renderColumns?.()}
          {renderExport?.(query)}
        </div>
      )}
      <section
        role="tabpanel"
        data-testid="status-board-panel"
        hidden={tab !== "board"}
      >
        <DataTable
          testId="status-board-table"
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
          empty="조건에 맞는 진행 프로젝트가 없습니다."
          emptyAction={
            <button
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
        data-testid="status-issues-panel"
        hidden={tab !== "issues"}
      >
        {tab === "issues" &&
          (renderIssues?.(query, result.retry) ?? (
            <p>프로젝트를 선택해 진행 상세에서 이슈를 확인하세요.</p>
          ))}
      </section>
      <details>
        <summary>계산 방법</summary>
        <p>{result.data?.calculationNotice}</p>
        <p>
          일정 늦음 = 계획 − 실적: 10.0%p 이상 위험, 5.0%p 이상 주의. TK 진척
          대비 원가 = 사용률 − 실적: 15.0%p 이상 위험, 5.0%p 이상 주의. 기한
          지난 HIGH 1건 이상 위험, 기한 지난 이슈 1건 이상 또는 메모 14일 초과
          주의. 모든 사유를 함께 표시합니다.
        </p>
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
      </details>
    </div>
  );
}
