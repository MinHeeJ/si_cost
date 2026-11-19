import { PeriodStatus } from "../../shared/PeriodStatus";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import {
  formatAmount,
  formatDateTime,
  formatFixedDecimal,
  formatRate,
  type AmountUnit,
} from "../../shared/format";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { SectionCard } from "../../shared/Surface";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { Icon } from "../../shared/Icon";
import { ChartHost } from "../../shared/charts/ChartHost";
import { seriesChartOptions } from "../../shared/charts/chartAdapters";
import { flattenMenus, useSharedScreen } from "../shared/SharedScreenContext";
import * as api from "./dashboardApi";
import { DashboardTrendChart } from "./DashboardTrendChart";
import "./dashboard.css";

const metricLabels: Record<api.DashboardMetric["metricCode"], string> = {
  SALES: "이번 달 매출",
  OPERATING_PROFIT: "이번 달 영업이익",
  YTD_OPERATING_PROFIT: "누계 영업이익",
  UTILIZATION: "가동률",
  RECEIVABLE: "미수금",
};
const actionLabels: Record<string, string> = {
  OVERDUE_RECEIVABLE: "미수 연체",
  CORPORATE_TAX_DUE: "법인세 납부 확인",
  CASH_SHORTAGE: "3개월 안 자금 부족",
  STAFF_SHORTAGE: "인력 부족 예상",
  MM_OVER: "MM 초과",
  PRECONTRACT_ASSIGNMENT: "미계약 선투입",
  WITHDRAWAL_DUE: "3개월 안 철수 예정",
  SALES_PART_MISSING: "영업 파트 미지정",
  PROJECT_RISK: "위험 프로젝트",
  ISSUE_OVERDUE: "기한 지난 이슈",
  WEEK_MEMO_STALE: "2주 미갱신",
  BILLING_MISSING: "청구 누락 의심",
  QUOTE_APPROVAL_WAIT: "견적 승인 대기",
};
const alertLabels = {
  LOW_MARGIN: "저마진",
  BUDGET_WARNING: "예산 주의",
  BUDGET_EXCEEDED: "예산 초과",
};
const events = [
  "sicost:bills-committed",
  "sicost:billing-committed",
  "sicost:payroll-committed",
  "sicost:financial-committed",
  "sicost:month-close-committed",
  "sicost:operating-standards-committed",
  "sicost:developers-committed",
  "sicost:assignments-committed",
  "sicost:projects-committed",
  "sicost:sales-opportunities-committed",
];
const testKey = (value: string) =>
  value.toLowerCase().replace(/[^a-z0-9-]/g, "-");
const count = (value: number | null) => (value == null ? "–" : String(value));
export function DashboardBasis({
  basis,
  compact = false,
}: {
  basis: api.DashboardBasis;
  compact?: boolean;
}) {
  return (
    <div className="dashboard-basis">
      <span
        className={compact ? "sicost-sr-only" : undefined}
        title={`${basis.basisMonth ?? basis.basisDate} · ${basis.sourceService} · ${formatDateTime(basis.observedAt)}`}
      >
        {basis.basisMonth ?? basis.basisDate} · {basis.sourceService} ·{" "}
        <span className="sicost-sr-only">
          {formatDateTime(basis.observedAt)}
        </span>
      </span>
      {basis.status === "FAILED" && (
        <StatusBadge tone="bad">조회 실패</StatusBadge>
      )}
      {basis.status === "UNCONFIRMED" && (
        <StatusBadge tone="warn">미확인</StatusBadge>
      )}
      {basis.status === "EMPTY" && (
        <StatusBadge tone="neutral">자료 없음</StatusBadge>
      )}
      {basis.reasons.map((reason, i) => (
        <span key={i}>{reason}</span>
      ))}
    </div>
  );
}
function Panel({
  title,
  children,
  testId,
}: {
  title: string;
  children: ReactNode;
  testId: string;
}) {
  return (
    <SectionCard title={title} className="dashboard-panel" testId={testId}>
      {children}
    </SectionCard>
  );
}
export function DashboardScreen({
  amountUnit = "천원",
}: { amountUnit?: AmountUnit } = {}) {
  const [month, setMonth] = useState<string>();
  const [data, setData] = useState<api.DashboardData>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true),
    [revision, setRevision] = useState(0);
  const [exporting, setExporting] = useState(false),
    [downloadError, setDownloadError] = useState<unknown>();
  const [notice, setNotice] = useState(""),
    [mobile, setMobile] = useState(() => window.innerWidth < 768);
  const activeExport = useRef(false),
    navigate = useNavigate(),
    { menus } = useSharedScreen("UI-16");
  const money = (value: string | null) => formatAmount(value, amountUnit);
  const closeProgress = data?.closeProgress;
  const requestPending = useRef(false), lastReceived = useRef(0);
  useEffect(() => {
    const period = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          value: { type: string; value: string };
        }>
      ).detail;
      if (detail?.screenId === "UI-16" && detail.value.type === "YEAR_MONTH")
        setMonth(detail.value.value);
    };
    const changed = () => setRevision((n) => n + 1),
      resize = () => setMobile(window.innerWidth < 768);
    window.addEventListener("sicost:screen-period-changed", period);
    const focus = () => { if (document.visibilityState === "visible" && !requestPending.current && Date.now() - lastReceived.current >= 30_000) changed(); };
    window.addEventListener("focus", focus);
    window.addEventListener("resize", resize);
    events.forEach((name) => window.addEventListener(name, changed));
    return () => {
      window.removeEventListener("sicost:screen-period-changed", period);
      window.removeEventListener("focus", focus);
      window.removeEventListener("resize", resize);
      events.forEach((name) => window.removeEventListener(name, changed));
    };
  }, []);
  useEffect(() => {
    const abort = new AbortController();
    requestPending.current = true;
    setLoading(true);
    setError(undefined);
    void api
      .getDashboard(month, abort.signal)
      .then((value) => {
        if (!abort.signal.aborted) { setData(value); lastReceived.current = Date.now(); }
      })
      .catch((e) => {
        if (!abort.signal.aborted) {
          if (e instanceof ApiError && (e.status === 401 || e.status === 403)) setData(undefined);
          setError(e);
        }
      })
      .finally(() => {
        if (!abort.signal.aborted) { requestPending.current = false; setLoading(false); }
      });
    return () => abort.abort();
  }, [month, revision]);
  function go(
    link: api.DashboardLink,
    basis: api.DashboardBasis,
    extra: Record<string, unknown> = {},
  ) {
    if (
      !/^UI-\d{2}$/.test(link.screenId) ||
      !flattenMenus(menus).some((m) => m.screenId === link.screenId)
    ) {
      setNotice("대상 화면을 사용할 수 없습니다.");
      return;
    }
    const query = extra.projectId
      ? `?${new URLSearchParams({ detailProjectId: String(extra.projectId), ...(basis.basisMonth ? { month: basis.basisMonth } : {}) })}`
      : "";
    navigate(`/${link.screenId}${query}`, {
      state: {
        fromDashboard: true,
        linkKey: link.linkKey,
        month: basis.basisMonth,
        year: basis.basisMonth?.slice(0, 4),
        ...extra,
      },
    });
  }
  async function download() {
    if (!data || activeExport.current || mobile || loading || error) return;
    activeExport.current = true;
    setExporting(true);
    setDownloadError(undefined);
    setNotice("");
    try {
      await api.getDashboardExport(data.selectedMonth);
      setNotice("엑셀 다운로드가 완료됐습니다.");
    } catch (e) {
      setDownloadError(e);
    } finally {
      activeExport.current = false;
      setExporting(false);
    }
  }
  const failure = (e: unknown) => (
    <ErrorBanner
      message={
        e instanceof Error ? e.message : "대시보드를 불러오지 못했습니다."
      }
      requestId={e instanceof ApiError ? e.body.requestId : undefined}
      onRetry={() => setRevision((n) => n + 1)}
      retryTestId="dashboard-retry"
    />
  );
  return (
    <SharedScreenFrame screenId="UI-16">
      <div
        className="dashboard-content"
        data-testid="dashboard-screen"
        aria-busy={loading}
      >
        <ScreenTitleActions>
          <div className="dashboard-toolbar">
            <small>
              {data?.scope === "ALL" ? "전체 회사" : "허용된 담당 범위"} · 단위:{" "}
              {amountUnit}
            </small>
            {!mobile && (
              <button
                data-testid="dashboard-export"
                disabled={loading || exporting || !data || !!error}
                onClick={() => void download()}
              >
                {exporting ? "파일 생성 중…" : "엑셀 다운로드"}
              </button>
            )}
          </div>
        </ScreenTitleActions>
        {loading && !data && <p role="status">대시보드를 불러오는 중입니다.</p>}
        {data && loading && <p role="status" className="sicost-refresh-status">갱신 중…</p>}
        {data && error != null && <p role="status" className="sicost-refresh-status">갱신 실패 · <button onClick={() => setRevision(n => n + 1)}>다시 시도</button></p>}
        {error != null && !data && failure(error)}
        {downloadError != null && (
          <ErrorBanner
            message={
              downloadError instanceof Error
                ? downloadError.message
                : "다운로드하지 못했습니다."
            }
            requestId={
              downloadError instanceof ApiError
                ? downloadError.body.requestId
                : undefined
            }
          />
        )}
        {notice && <p role="status">{notice}</p>}
        {data && (
          <>
            <PeriodStatus>
            <div className="dashboard-dates">
              <span>
                기준일 <b>{data.today}</b>
              </span>
              <span>선택월 {data.selectedMonth}</span>
              <StatusBadge
                tone={
                  data.selectedMonthCloseStatus === "CLOSE" ? "lock" : "info"
                }
              >
                {data.selectedMonthCloseStatus === "CLOSE" ? "마감" : "작업중"}
              </StatusBadge>{" "}
              <span>
                마감 완료 <b>{data.lastClosedMonth ?? "–"}</b>
              </span>
              {data.provisional && <StatusBadge tone="warn">잠정</StatusBadge>}
              <span className="dashboard-date-note sicost-sr-only">
                현재 미수·할일·경보·수급·기회·자금은 오늘 D / 현재월 M{" "}
                {data.currentMonth} 기준 · {formatDateTime(data.observedAt)}
              </span>
              <HelpTooltip label="숫자 기준" testId="dashboard-basis-help">
                현재 미수·할일·경보·수급·기회·자금은 오늘 {data.today} / 현재월{" "}
                {data.currentMonth} 기준입니다.
                <br />
                조회 시각 {formatDateTime(data.observedAt)}
                {data.metrics.map((card) => (
                  <p key={card.metricCode}>
                    {metricLabels[card.metricCode]} ·{" "}
                    {card.basis.basisMonth ?? card.basis.basisDate} ·{" "}
                    {card.basis.sourceService}
                  </p>
                ))}
              </HelpTooltip>
            </div>
            </PeriodStatus>
            <div className="dashboard-metrics">
              {data.metrics.map((card) => (
                <button
                  className="dashboard-metric"
                  key={card.metricCode}
                  data-testid={`dashboard-metric-${testKey(card.metricCode)}`}
                  onClick={() =>
                    go(
                      card.link,
                      card.basis,
                      card.metricCode === "RECEIVABLE"
                        ? { section: "receivables" }
                        : {},
                    )
                  }
                >
                  <span>
                    {card.metricCode === "SALES"
                      ? `${data.selectedMonth.slice(5)}월 매출`
                      : card.metricCode === "OPERATING_PROFIT"
                        ? `${data.selectedMonth.slice(5)}월 영업이익`
                        : metricLabels[card.metricCode]}
                  </span>
                  <strong>
                    {card.unit === "PERCENT"
                      ? formatRate(card.value)
                      : `${money(card.value)} ${amountUnit}`}
                  </strong>
                  {(card.metricCode === "SALES" ||
                    card.metricCode === "OPERATING_PROFIT") && (
                    <small>
                      전월 대비 {formatRate(card.changeRate)} · 차액{" "}
                      {money(card.difference)} {amountUnit}
                    </small>
                  )}
                  {card.metricCode === "OPERATING_PROFIT" && (
                    <small>이익률 {formatRate(card.profitRate)}</small>
                  )}
                  {(card.metricCode === "SALES" ||
                    card.metricCode === "YTD_OPERATING_PROFIT") && (
                    <small>
                      {card.metricCode === "YTD_OPERATING_PROFIT"
                        ? `1~${data.selectedMonth.slice(5)}월 · `
                        : ""}
                      목표 달성 {formatRate(card.achievementRate)}
                    </small>
                  )}
                  {card.metricCode === "UTILIZATION" && (
                    <small>
                      목표 {formatRate(card.targetValue)} 대비{" "}
                      {formatFixedDecimal(card.targetGapPercentagePoints, 1)}%p
                      · 대기 {count(card.idlePersonCount)}명
                    </small>
                  )}
                  {card.metricCode === "RECEIVABLE" && (
                    <small className="dashboard-danger">
                      연체 {count(card.overdueCount)}건{" "}
                      {money(card.overdueAmount)} {amountUnit}
                    </small>
                  )}
                  <DashboardBasis basis={card.basis} compact />
                </button>
              ))}
            </div>
            <div className="dashboard-main-grid">
              <Panel
                title={`확인할 일 ${count(data.actionTypeCount)}유형 · 급함 ${count(data.urgentActionTypeCount)}유형`}
                testId="dashboard-actions"
              >
                <DashboardBasis basis={data.actionsBasis} />
                {data.actions.length === 0 &&
                  (data.actionsBasis.status === "AVAILABLE" ||
                    data.actionsBasis.status === "EMPTY") && (
                    <p className="dashboard-empty">확인할 일이 없습니다</p>
                  )}
                {data.actions
                  .filter((a) => a.count > 0)
                  .map((action) => (
                    <button
                      className={`dashboard-list-row ${action.urgent ? "urgent" : "warning"}`}
                      key={action.actionCode}
                      data-testid={`dashboard-action-${testKey(action.actionCode)}`}
                      onClick={() =>
                        go(
                          action.link,
                          action.actionCode === "CASH_SHORTAGE" &&
                            action.link.linkKey
                            ? {
                                ...action.basis,
                                basisMonth: action.link.linkKey,
                              }
                            : action.basis,
                          {
                            section:
                              action.actionCode === "OVERDUE_RECEIVABLE"
                                ? "receivables"
                                : action.actionCode === "BILLING_MISSING"
                                  ? "checks"
                                  : action.actionCode === "CORPORATE_TAX_DUE"
                                    ? "tax"
                                    : action.actionCode === "STAFF_SHORTAGE" &&
                                        action.link.screenId === "UI-28"
                                      ? "supply"
                                      : undefined,
                            filter:
                              action.actionCode === "OVERDUE_RECEIVABLE"
                                ? "overdue"
                                : action.actionCode === "MM_OVER"
                                  ? "MM_OVER"
                                  : undefined,
                          },
                        )
                      }
                    >
                      <span className="dashboard-row-icon">
                        <Icon name="warning" size={18} />
                      </span>
                      <span>
                        <strong>
                          {actionLabels[action.actionCode] ?? action.actionCode}{" "}
                          {action.count}건
                        </strong>
                        <small>{action.reason}</small>
                        <small>
                          {action.months.join(" · ")}
                          {action.amount !== null &&
                            ` · ${money(action.amount)} ${amountUnit}`}
                          {action.personCount !== null &&
                            ` · ${formatFixedDecimal(action.personCount, 1)}명`}
                        </small>
                        {action.withoutNextAssignmentCount !== null && (
                          <small>
                            다음 배정 없음 {action.withoutNextAssignmentCount}명
                            · 추천 자리 있음{" "}
                            {count(action.recommendedPlacementCount)}명
                          </small>
                        )}
                        <DashboardBasis basis={action.basis} compact />
                      </span>
                      <span className="dashboard-row-target">
                        {flattenMenus(menus).find(
                          (menu) => menu.screenId === action.link.screenId,
                        )?.name ?? "원천 보기"}{" "}
                        ›
                      </span>
                    </button>
                  ))}
              </Panel>
              <div className="dashboard-stack">
                {closeProgress && (
                  <Panel
                    title={`${data.selectedMonth.slice(5)}월 마감 진행`}
                    testId="dashboard-close"
                  >
                    <button
                      data-testid="dashboard-close-open"
                      onClick={() =>
                        go(closeProgress.link, closeProgress.basis)
                      }
                    >
                      월 마감
                    </button>
                    <p>
                      {count(closeProgress.passedCheckCount)}/
                      {closeProgress.totalCheckCount} ·{" "}
                      {closeProgress.snapshot ? "당시 마감 사본" : "현재 점검"}
                    </p>
                    {closeProgress.passedCheckCount !== null && (
                      <progress
                        max={closeProgress.totalCheckCount}
                        value={closeProgress.passedCheckCount}
                        aria-label="마감 점검 진행"
                      />
                    )}
                    {closeProgress.remainingChecks.map((check) => (
                      <p
                        key={check.checkCode}
                        data-testid={`dashboard-check-${testKey(check.checkCode)}`}
                      >
                        <StatusBadge tone="warn">
                          {check.checkCode} {count(check.count)}건
                        </StatusBadge>{" "}
                        {check.reason}
                      </p>
                    ))}
                    <DashboardBasis basis={closeProgress.basis} />
                  </Panel>
                )}
                {data.projectAlerts && (
                  <Panel
                    title={`프로젝트 경보 ${data.projectAlerts.length}건`}
                    testId="dashboard-alerts"
                  >
                    <DashboardBasis basis={data.projectAlertsBasis} />
                    {data.projectAlerts.length === 0 &&
                      ["AVAILABLE", "EMPTY"].includes(
                        data.projectAlertsBasis.status,
                      ) && (
                        <p className="dashboard-empty">
                          프로젝트 경보가 없습니다.
                        </p>
                      )}
                    {data.projectAlerts.map((alert) => (
                      <button
                        className="dashboard-list-row"
                        key={`${alert.projectId}-${alert.alertType}`}
                        data-testid={`dashboard-alert-${testKey(alert.projectId)}-${testKey(alert.alertType)}`}
                        onClick={() =>
                          go(alert.link, alert.basis, {
                            projectId: alert.projectId,
                          })
                        }
                      >
                        <span>
                          <strong>{alert.projectName}</strong>
                          <small>{alert.reason}</small>
                          <small>
                            {alert.contractTypeCode} 마진율{" "}
                            {formatRate(alert.marginRate)} · 예산 소진{" "}
                            {formatRate(alert.budgetConsumptionRate)}
                          </small>
                        </span>
                        <StatusBadge
                          tone={
                            alert.alertType === "BUDGET_EXCEEDED"
                              ? "bad"
                              : "warn"
                          }
                        >
                          {alertLabels[alert.alertType]}
                        </StatusBadge>
                      </button>
                    ))}
                  </Panel>
                )}
              </div>
            </div>
            <div className="dashboard-two-grid">
              {data.salesCostTrend && (
                <Panel title="매출·원가 추이" testId="dashboard-trend">
                  <DashboardBasis basis={data.salesCostTrendBasis} />
                  <DashboardTrendChart rows={data.salesCostTrend} />
                  <div className="sicost-table-viewport">
                    <table
                      className="sicost-table"
                      aria-label="매출·원가 추이 숫자표"
                    >
                      <thead>
                        <tr>
                          <th>월</th>
                          <th>매출 ({amountUnit})</th>
                          <th>매출원가 ({amountUnit})</th>
                          <th>목표 ({amountUnit})</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.salesCostTrend.map((row) => (
                          <tr
                            key={row.month}
                            data-testid={`dashboard-trend-${row.month}`}
                          >
                            <td>{row.month}</td>
                            <td>{money(row.salesAmount)}</td>
                            <td>{money(row.salesCostAmount)}</td>
                            <td>{money(row.salesTargetAmount)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </Panel>
              )}
              <Panel
                title="앞으로 6개월 인력 수급 예측"
                testId="dashboard-supply"
              >
                <DashboardBasis basis={data.supplyBasis} />
                <p>
                  {data.scope === "ALL"
                    ? "전체 인력"
                    : "허용된 담당 범위의 인력"}{" "}
                  · 미충원 포함 · 단위: 명
                </p>
                <ChartHost
                  option={seriesChartOptions({
                    categories: data.supplyForecast.map((row) => row.month),
                    series: [
                      {
                        name: "필요",
                        type: "bar",
                        values: data.supplyForecast.map(
                          (row) => row.requiredPersons,
                        ),
                      },
                      {
                        name: "가용",
                        type: "bar",
                        values: data.supplyForecast.map(
                          (row) => row.availablePersons,
                        ),
                      },
                    ],
                    formatValue: (value) => `${formatFixedDecimal(value, 1)}명`,
                    axisUnit: "명",
                  })}
                >
                  <p className="dashboard-empty">
                    차트 대신 아래 숫자표에서 인력 수급 예측을 확인할 수
                    있습니다.
                  </p>
                </ChartHost>
                <div className="sicost-table-viewport">
                  <table
                    className="sicost-table"
                    aria-label="인력 수급 예측 숫자표"
                  >
                    <thead>
                      <tr>
                        <th>월</th>
                        <th>필요</th>
                        <th>가용</th>
                        <th>차이</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.supplyForecast.map((row) => (
                        <tr
                          key={row.month}
                          data-testid={`dashboard-supply-${row.month}`}
                        >
                          <td>{row.month}</td>
                          <td>{formatFixedDecimal(row.requiredPersons, 1)}</td>
                          <td>{formatFixedDecimal(row.availablePersons, 1)}</td>
                          <td
                            className={
                              row.differencePersons?.startsWith("-")
                                ? "dashboard-danger"
                                : ""
                            }
                          >
                            {formatFixedDecimal(row.differencePersons, 1)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Panel>
            </div>
            <div className="dashboard-bottom-grid">
              {data.salesParts && (
                <Panel
                  title={`영업 파트별 ${data.selectedMonth.slice(5)}월`}
                  testId="dashboard-parts"
                >
                  <DashboardBasis basis={data.salesPartsBasis} />
                  <p>{data.salesPartExplanation}</p>
                  {data.salesParts.length === 0 &&
                    ["AVAILABLE", "EMPTY"].includes(
                      data.salesPartsBasis.status,
                    ) && (
                      <p className="dashboard-empty">
                        영업 파트 자료가 없습니다.
                      </p>
                    )}
                  <table className="sicost-table" aria-label="영업 파트별 실적">
                    <thead>
                      <tr>
                        <th>파트</th>
                        <th>매출 ({amountUnit})</th>
                        <th>전체원가 ({amountUnit})</th>
                        <th>기여이익률</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.salesParts.map((row) => (
                        <tr
                          key={row.partId ?? "unassigned"}
                          data-testid={`dashboard-part-${testKey(row.partId ?? "unassigned")}`}
                        >
                          <td>{row.partName}</td>
                          <td>{money(row.salesAmount)}</td>
                          <td>{money(row.projectCostAmount)}</td>
                          <td>{formatRate(row.contributionMarginRate)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </Panel>
              )}
              <Panel title="영업 파이프라인" testId="dashboard-pipeline">
                <button
                  type="button"
                  onClick={() =>
                    go(
                      { screenId: "UI-29", linkKey: null },
                      data.pipeline.basis,
                    )
                  }
                >
                  자세히
                </button>
                <DashboardBasis basis={data.pipeline.basis} />
                <p>진행 기회 {count(data.pipeline.opportunityCount)}건</p>
                <strong>
                  확률 반영 예상액 {money(data.pipeline.weightedExpectedAmount)}{" "}
                  {amountUnit}
                </strong>
                {data.pipeline.startingSoon.length === 0 &&
                  ["AVAILABLE", "EMPTY"].includes(
                    data.pipeline.basis.status,
                  ) && (
                    <p className="dashboard-empty">
                      곧 시작할 기회가 없습니다.
                    </p>
                  )}
                {data.pipeline.startingSoon.map((row) => (
                  <button
                    className="dashboard-list-row"
                    key={row.opportunityId}
                    data-testid={`dashboard-opportunity-${testKey(row.opportunityId)}`}
                    onClick={() =>
                      go(row.link, data.pipeline.basis, {
                        opportunityId: row.opportunityId,
                      })
                    }
                  >
                    <span>
                      <strong>{row.opportunityName}</strong>
                      <small>
                        {row.startMonth} · 확률{" "}
                        {formatRate(row.winProbabilityRate)} ·{" "}
                        {money(row.expectedAmount)} {amountUnit}
                      </small>
                    </span>
                    <StatusBadge tone="info">예측</StatusBadge>
                  </button>
                ))}
              </Panel>
              {data.cashForecast && (
                <Panel title="자금 (앞으로 3개월)" testId="dashboard-cash">
                  <DashboardBasis basis={data.cashBasis} />
                  {data.cashForecast.map((row) => (
                    <button
                      className="dashboard-list-row"
                      key={row.month}
                      data-testid={`dashboard-cash-${row.month}`}
                      onClick={() =>
                        go(
                          row.link,
                          { ...data.cashBasis, basisMonth: row.month },
                          {},
                        )
                      }
                    >
                      <span>
                        <strong>
                          {row.month}{" "}
                          <StatusBadge
                            tone={
                              row.shortage === true
                                ? "bad"
                                : row.shortage === null
                                  ? "warn"
                                  : "info"
                            }
                          >
                            {row.shortage === true
                              ? "자금 부족"
                              : row.shortage === null
                                ? "미확인"
                                : "예측"}
                          </StatusBadge>
                        </strong>
                        <small>
                          월말 잔액 {money(row.closingBalance)} {amountUnit}
                        </small>
                        <small>
                          최소 자금 {money(row.minimumCashAmount)} {amountUnit}
                        </small>
                        {row.unconfirmedReasons.map((reason, i) => (
                          <small key={i}>{reason}</small>
                        ))}
                      </span>
                    </button>
                  ))}
                </Panel>
              )}
            </div>
          </>
        )}
      </div>
    </SharedScreenFrame>
  );
}
