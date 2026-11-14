import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import {
  formatAmount,
  formatDecimal,
  type AmountUnit,
} from "../../shared/format";
import * as api from "./projectCostApi";
import "./projectCost.css";

export function CostSourceIssues({ issues }: { issues: api.MissingReason[] }) {
  if (!issues.length) return null;
  return (
    <ul className="cost-source-issues">
      {issues.map((issue, i) => (
        <li
          key={`${issue.code}-${issue.month}-${issue.personId}-${i}`}
          data-testid={`cost-source-issue-${i}`}
        >
          {issue.month} {issue.personId} {issue.message}{" "}
          <small>
            {issue.code} · {issue.metric}
          </small>
        </li>
      ))}
    </ul>
  );
}
export function CostMetrics({
  detail,
  unit = "천원",
}: {
  detail: api.Detail;
  unit?: AmountUnit;
}) {
  const row = detail.row;
  return (
    <section className="cost-metrics" data-testid="cost-metrics">
      <h3>
        {row.contractType === "IN"
          ? "연구개발비"
          : detail.sourceStatus === "PRE"
            ? "선투입 원가"
            : detail.sourceStatus === "LOSE"
              ? "실주 보존 이력"
              : "프로젝트 원가"}{" "}
        · {detail.month}
      </h3>
      <dl>
        <dt>누적 원가 ({unit})</dt>
        <dd className={row.cumulativeCost?.startsWith("-") ? "cost-negative" : undefined}>{formatAmount(row.cumulativeCost, unit)}</dd>
        <dt>누적 매출참고 ({unit})</dt>
        <dd className={row.cumulativeRevenue?.startsWith("-") ? "cost-negative" : undefined}>{formatAmount(row.cumulativeRevenue, unit)}</dd>
        <dt>누적 마진</dt>
        <dd className={row.cumulativeMargin?.startsWith("-") ? "cost-negative" : undefined}>
          {row.cumulativeMargin ?? "–"}
          {row.cumulativeMargin != null && "%"}
        </dd>
        <dt>예산 소진율</dt>
        <dd>
          {row.budgetUsage ?? "–"}
          {row.budgetUsage != null && "%"}
        </dd>
      </dl>
      <p className="cost-help">{detail.revenueExplanation}</p>
      {detail.sourceStatus === "PRE" && (
        <p className="sicost-banner sicost-tone-info">
          선투입: 매출 0 · 예상 최종 마진 미계산
        </p>
      )}
      {row.alerts.map((alert) => (
        <p
          className="sicost-banner sicost-tone-bad"
          key={alert.kind}
          title={`${alert.actualNumerator} / ${alert.actualDenominator}; ${alert.comparison}; 기준 ${alert.threshold}`}
          data-testid={`cost-alert-${alert.kind.toLowerCase().replaceAll("_", "-")}`}
        >
          {alert.message} · {alert.comparison}
        </p>
      ))}
      {row.budgetWarning && (
        <p className="sicost-banner sicost-tone-warn">예산 소진 주의</p>
      )}
      {detail.completeness.estimated && (
        <StatusBadge tone="info">예정</StatusBadge>
      )}
      {(!detail.completeness.costComplete ||
        !detail.completeness.revenueComplete) && (
        <p className="sicost-banner sicost-tone-warn">
          자료 없음/확인 필요 · 미확인 금액은 – 로 표시합니다.
        </p>
      )}
      <p className="cost-help">
        집계 {detail.completeness.rangeStartMonth} ~{" "}
        {detail.completeness.rangeEndMonth}
        {detail.completeness.missingBeforeMonth &&
          ` · 운영 시작 전 누락 범위 있음 (${detail.completeness.missingBeforeMonth} 이전)`}
      </p>
      <CostSourceIssues issues={detail.completeness.issues} />
    </section>
  );
}
export function ProjectCostSummaryPanel({
  projectId,
  month,
  amountUnit = "천원",
}: {
  projectId: string;
  month?: string;
  amountUnit?: AmountUnit;
}) {
  const [detail, setDetail] = useState<api.Detail>();
  const [error, setError] = useState<unknown>();
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let live = true;
    setDetail(undefined);
    setError(undefined);
    api
      .getProjectCostAnalysis(projectId, month)
      .then((value) => {
        if (live) setDetail(value);
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, [projectId, month, attempt]);
  return (
    <section
      data-testid="project-cost-summary"
      data-screen-id="SCR-UI-01-B138-COST"
    >
      {!detail && !error && <p role="status">원가를 불러오는 중입니다.</p>}
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "원가를 조회하지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setAttempt((n) => n + 1)}
        />
      )}
      {detail && (
        <>
          <CostMetrics detail={detail} unit={amountUnit} />
          <ProjectCostEntryLink
            projectId={projectId}
            month={detail.month}
            sourceStatus={detail.sourceStatus}
            origin="project"
          />
        </>
      )}
    </section>
  );
}
export function ProjectCostEntryLink({
  projectId,
  month,
  sourceStatus,
  origin = "board",
}: {
  projectId: string;
  month: string;
  sourceStatus?: string;
  origin?: "board" | "project";
}) {
  const location = useLocation();
  const search = new URLSearchParams({ projectId, month, costTab: "months" });
  return (
    <span
      data-testid={`cost-entry-${origin}`}
      data-screen-id={
        origin === "board" ? "SCR-UI-31-B138-COST" : "SCR-UI-01-B138-COST"
      }
    >
      <Link
        data-testid={`cost-entry-${origin}-link`}
        to={{ pathname: "/UI-04", search: search.toString() }}
        state={{ returnTo: location.pathname + location.search }}
      >
        원가 분석
      </Link>
      {sourceStatus === "PRE" && (
        <small> 선투입 원가 · 매출 0 · 예상 최종 미계산</small>
      )}
    </span>
  );
}
export const costDecimal = formatDecimal;
