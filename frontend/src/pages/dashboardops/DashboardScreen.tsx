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
import { Dialog } from "../../shared/Dialog";
import { DashboardSkeleton } from "./DashboardSkeleton";
import { DashboardMetricCard } from "./DashboardMetricCard";
import { SectionCard } from "../../shared/Surface";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { Icon } from "../../shared/Icon";
import { ChartHost } from "../../shared/charts/ChartHost";
import { seriesChartOptions } from "../../shared/charts/chartAdapters";
import { flattenMenus, useSharedScreen } from "../shared/SharedScreenContext";
import * as api from "./dashboardApi";
import { DashboardTrendChart } from "./DashboardTrendChart";
import {
  closeCheckLabels,
  contractTypeLabels,
  dashboardMessage,
} from "./dashboardPresentation";
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
  periodLabel = "기준월",
  hidePeriod = false,
}: {
  basis: api.DashboardBasis;
  compact?: boolean;
  periodLabel?: string;
  hidePeriod?: boolean;
}) {
  if (hidePeriod && basis.status === "AVAILABLE" && !basis.reasons.length)
    return null;
  return (
    <div className="dashboard-basis">
      {!hidePeriod && (
        <span
          className={`dashboard-basis-period${compact ? " sicost-sr-only" : ""}`}
        >
          {basis.basisMonth ? periodLabel : "기준일"}{" "}
          {basis.basisMonth ?? basis.basisDate}
        </span>
      )}
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
        <span key={i}>{dashboardMessage(reason)}</span>
      ))}
    </div>
  );
}
function Panel({
  title,
  children,
  testId,
  actions,
}: {
  title: ReactNode;
  children: ReactNode;
  testId: string;
  actions?: ReactNode;
}) {
  return (
    <SectionCard
      title={title}
      actions={actions}
      className="dashboard-panel"
      testId={testId}
    >
      {children}
    </SectionCard>
  );
}
export function DashboardScreen({
  amountUnit = "천원",
}: { amountUnit?: AmountUnit } = {}) {
  const [month, setMonth] = useState<string>();
  const [trendRange, setTrendRange] = useState<"recent" | "all">("recent");
  const [actionsOpen, setActionsOpen] = useState(false);
  const [receivedData, setData] = useState<api.DashboardData>();
  // Retain cards only while refreshing the same selected period.
  const data =
    month === undefined || receivedData?.selectedMonth === month
      ? receivedData
      : undefined;
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
  const actions =
    data?.actions
      .filter((action) => action.count > 0)
      .sort((a, b) => Number(b.urgent) - Number(a.urgent)) ?? [];
  const trendRows = (data?.salesCostTrend ?? []).filter((row) => {
    if (!data || trendRange === "all") return true;
    const [year, monthNumber] = data.selectedMonth.split("-").map(Number);
    const [rowYear, rowMonth] = row.month.split("-").map(Number);
    const distance = (year - rowYear) * 12 + monthNumber - rowMonth;
    return distance >= 0 && distance < 3;
  });
  useEffect(() => setActionsOpen(false), [month]);
  const requestPending = useRef(false),
    lastReceived = useRef(0);
  const printing = useRef(false),
    printFocusUntil = useRef(0);
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
    const focus = () => {
      if (
        !printing.current &&
        Date.now() > printFocusUntil.current &&
        document.visibilityState === "visible" &&
        !requestPending.current &&
        Date.now() - lastReceived.current >= 30_000
      )
        changed();
    };
    const beforePrint = () => {
      printing.current = true;
    };
    const afterPrint = () => {
      printing.current = false;
      printFocusUntil.current = Date.now() + 1000;
    };
    window.addEventListener("beforeprint", beforePrint);
    window.addEventListener("afterprint", afterPrint);
    window.addEventListener("focus", focus);
    window.addEventListener("resize", resize);
    events.forEach((name) => window.addEventListener(name, changed));
    return () => {
      window.removeEventListener("sicost:screen-period-changed", period);
      window.removeEventListener("focus", focus);
      window.removeEventListener("beforeprint", beforePrint);
      window.removeEventListener("afterprint", afterPrint);
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
        if (!abort.signal.aborted) {
          setData(value);
          lastReceived.current = Date.now();
        }
      })
      .catch((e) => {
        if (!abort.signal.aborted) {
          if (e instanceof ApiError && (e.status === 401 || e.status === 403))
            setData(undefined);
          setError(e);
        }
      })
      .finally(() => {
        if (!abort.signal.aborted) {
          requestPending.current = false;
          setLoading(false);
        }
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
        e instanceof Error
          ? dashboardMessage(e.message, "대시보드를 불러오지 못했습니다.")
          : "대시보드를 불러오지 못했습니다."
      }
      onRetry={() => setRevision((n) => n + 1)}
      retryTestId="dashboard-retry"
    />
  );
  const actionRow = (action: api.DashboardAction, inDialog = false) => (
    <button
      className={`dashboard-list-row ${action.urgent ? "urgent" : "warning"}`}
      key={action.actionCode}
      type="button"
      data-testid={`dashboard-${inDialog ? "modal-action" : "action"}-${testKey(action.actionCode)}`}
      onClick={() => {
        if (inDialog) setActionsOpen(false);
        go(
          action.link,
          action.actionCode === "CASH_SHORTAGE" && action.link.linkKey
            ? { ...action.basis, basisMonth: action.link.linkKey }
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
        );
      }}
    >
      <span className="dashboard-row-icon">
        <Icon name="warning" size={18} />
      </span>
      <span>
        <strong>
          {actionLabels[action.actionCode] ?? "확인할 일"} {action.count}건
        </strong>
        <small>{dashboardMessage(action.reason)}</small>
        <small>
          {[
            ...action.months,
            action.amount !== null
              ? `${money(action.amount)} ${amountUnit}`
              : null,
            action.personCount !== null
              ? `${formatFixedDecimal(action.personCount, 1)}명`
              : null,
          ]
            .filter(Boolean)
            .join(" · ")}
        </small>
        {action.withoutNextAssignmentCount !== null && (
          <small>
            다음 배정 없음 {action.withoutNextAssignmentCount}명 · 추천 자리
            있음 {count(action.recommendedPlacementCount)}명
          </small>
        )}
        <DashboardBasis basis={action.basis} hidePeriod />
      </span>
      <span className="dashboard-row-target">
        {flattenMenus(menus).find(
          (menu) => menu.screenId === action.link.screenId,
        )?.name ?? "원천 보기"}{" "}
        ›
      </span>
    </button>
  );
  const metrics = (financial: boolean) =>
    data && (
      <div
        className={`dashboard-metrics dashboard-${financial ? "financial" : "operational"}-metrics`}
      >
        {data.metrics
          .filter(
            (card) =>
              !["UTILIZATION", "RECEIVABLE"].includes(card.metricCode) ===
              financial,
          )
          .map((card) => (
            <DashboardMetricCard
              key={card.metricCode}
              card={card}
              month={data.selectedMonth}
              amountUnit={amountUnit}
              onOpen={() =>
                go(
                  card.link,
                  card.basis,
                  card.metricCode === "RECEIVABLE"
                    ? { section: "receivables" }
                    : {},
                )
              }
            >
              <DashboardBasis
                basis={
                  card.metricCode === "RECEIVABLE"
                    ? { ...card.basis, basisMonth: null }
                    : card.basis
                }
                compact
              />
            </DashboardMetricCard>
          ))}
      </div>
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
        {loading && (
          <PeriodStatus className="dashboard-loading-status">
            <span role="status" className="dashboard-loading-label">
              <span className="dashboard-loading-spinner" aria-hidden="true" />
              {data ? "갱신 중…" : "대시보드를 불러오는 중입니다."}
            </span>
          </PeriodStatus>
        )}
        {loading && !data && <DashboardSkeleton />}
        {data && error != null && (
          <p role="status" className="sicost-refresh-status">
            갱신 실패 ·{" "}
            <button
              data-testid="dashboard-refresh-retry"
              onClick={() => setRevision((n) => n + 1)}
            >
              다시 시도
            </button>
          </p>
        )}
        {error != null && !data && failure(error)}
        {downloadError != null && (
          <ErrorBanner
            message={
              downloadError instanceof Error
                ? dashboardMessage(
                    downloadError.message,
                    "다운로드하지 못했습니다.",
                  )
                : "다운로드하지 못했습니다."
            }
          />
        )}
        {notice && <p role="status">{notice}</p>}
        {data && (
          <>
            <PeriodStatus className="dashboard-dates sicost-period-summary">
              <span className="sicost-period-summary-item">
                {data.scope === "ALL" ? "전체 회사" : "허용된 담당 범위"} ·
                단위: {amountUnit}
              </span>
              <span className="dashboard-print-date">
                조회월 {data.selectedMonth} · 기준일 {data.today}
              </span>
              <span className="sicost-period-summary-item">
                <HelpTooltip
                  label="숫자 기준"
                  testId="dashboard-basis-help"
                  trigger={
                    <span className="dashboard-date-item-content">
                      <span className="sicost-period-summary-label">기준일</span>
                      <b>{data.today.replace(/-/g, ".")}</b>
                    </span>
                  }
                >
                  현재 미수·할일·경보·수급·기회·자금은 오늘 {data.today} /
                  현재월 {data.currentMonth} 기준입니다. 청구 누락 의심은 선택월{" "}
                  {data.selectedMonth} 기준입니다.
                  <br />
                  조회 시각 {formatDateTime(data.observedAt)}
                  {data.metrics.map((card) => (
                    <p key={card.metricCode}>
                      {metricLabels[card.metricCode]} ·{" "}
                      {card.metricCode === "RECEIVABLE"
                        ? card.basis.basisDate
                        : (card.basis.basisMonth ?? card.basis.basisDate)}
                    </p>
                  ))}
                </HelpTooltip>
              </span>
              <span className="sicost-period-summary-item">
                <span className="sicost-period-summary-label">마감 완료</span>
                <b>
                  {data.lastClosedMonth
                    ? `${data.lastClosedMonth.slice(0, 4) !== data.selectedMonth.slice(0, 4) ? `${data.lastClosedMonth.slice(0, 4)}년 ` : ""}${Number(data.lastClosedMonth.slice(5))}월까지`
                    : "없음"}
                </b>
              </span>
              <span className="sicost-period-summary-item dashboard-date-state">
                {data.provisional ? (
                  <>
                    <StatusBadge tone="warn">잠정</StatusBadge>
                    {data.selectedMonth !== data.currentMonth ? (
                      <span>마감 전 매출·이익 변동 가능</span>
                    ) : (
                      <span>
                        아래{" "}
                        <b>{Number(data.selectedMonth.slice(5))}월 매출·이익</b>은
                        마감 전이라 바뀔 수 있습니다
                      </span>
                    )}
                  </>
                ) : data.selectedMonthCloseStatus === "CLOSE" ? (
                  <>
                    <StatusBadge tone="ok">확정</StatusBadge>
                    <span>
                      {Number(data.selectedMonth.slice(5))}월 숫자는 마감된
                      확정치입니다
                    </span>
                  </>
                ) : (
                  <>
                    <StatusBadge tone="warn">미확정</StatusBadge>
                    <span>선택한 달의 마감 상태를 확인해 주세요</span>
                  </>
                )}
              </span>
              {data.selectedMonth !== data.currentMonth && (
                <span className="sicost-period-summary-item dashboard-date-current-note">
                  <HelpTooltip
                    label="지표별 조회 기준"
                    trigger={
                      <span className="dashboard-date-item-content">
                        <Icon name="info" size={14} />
                        <span>손익은 선택월 · 미수금 등은 오늘 기준</span>
                      </span>
                    }
                  >
                    재무 지표와 청구 누락 의심은 선택월 기준입니다.
                    미수금·그 외 확인할 일·경보·인력 수급·파이프라인·자금은 오늘
                    또는 현재월 기준입니다.
                  </HelpTooltip>
                </span>
              )}
              <span className="sicost-sr-only">
                선택월 {data.selectedMonth} · 현재
                미수·할일·경보·수급·기회·자금은 오늘 {data.today} / 현재월{" "}
                {data.currentMonth} 기준 · {formatDateTime(data.observedAt)}
              </span>
            </PeriodStatus>
            {data.metrics.length > 0 && (
              <div className="dashboard-kpi-grid">
                {metrics(true)}
                {metrics(false)}
              </div>
            )}
            <div className="dashboard-main-grid">
              {data.salesCostTrend && (
                <Panel
                  title="매출·원가 추이"
                  testId="dashboard-trend"
                  actions={
                    <div
                      className="dashboard-range"
                      role="group"
                      aria-label="추이 기간"
                    >
                      <button
                        type="button"
                        data-testid="dashboard-trend-recent"
                        aria-pressed={trendRange === "recent"}
                        onClick={() => setTrendRange("recent")}
                      >
                        최근 3개월
                      </button>
                      <button
                        type="button"
                        data-testid="dashboard-trend-all"
                        aria-pressed={trendRange === "all"}
                        onClick={() => setTrendRange("all")}
                      >
                        전체 기간
                      </button>
                    </div>
                  }
                >
                  <DashboardBasis
                    basis={data.salesCostTrendBasis}
                    periodLabel="조회 기준월"
                  />
                  <DashboardTrendChart
                    rows={trendRows}
                    amountUnit={amountUnit}
                  />
                  <details className="dashboard-trend-details">
                    <summary data-testid="dashboard-trend-details">
                      상세 숫자표
                    </summary>
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
                          {trendRows.map((row) => (
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
                  </details>
                </Panel>
              )}
              <Panel
                title="확인할 일"
                actions={
                  <div className="dashboard-action-counts">
                    <span className="dashboard-action-count">
                      전체{" "}
                      {data.actionTypeCount === null ? "–" : actions.length}
                    </span>
                    <span
                      className={`dashboard-action-count${actions.some((action) => action.urgent) ? " urgent" : ""}`}
                    >
                      급함{" "}
                      {data.urgentActionTypeCount === null
                        ? "–"
                        : actions.filter((action) => action.urgent).length}
                    </span>
                  </div>
                }
                testId="dashboard-actions"
              >
                <DashboardBasis basis={data.actionsBasis} hidePeriod />
                {actions.length === 0 &&
                  (data.actionsBasis.status === "AVAILABLE" ||
                    data.actionsBasis.status === "EMPTY") && (
                    <p className="dashboard-empty">확인할 일이 없습니다</p>
                  )}
                {actions.length > 0 && (
                  <div className="dashboard-compact-list">
                    {actions.slice(0, 5).map((action) => actionRow(action))}
                  </div>
                )}
                {actions.length > 5 && (
                  <button
                    type="button"
                    className="dashboard-actions-all"
                    data-testid="dashboard-actions-all"
                    onClick={() => setActionsOpen(true)}
                  >
                    전체 {actions.length}유형 보기
                  </button>
                )}
              </Panel>
            </div>
            <div className="dashboard-two-grid">
              <DashboardSupply data={data} />
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
                    <div className="dashboard-close-summary">
                      <strong>
                        {closeProgress.totalCheckCount === 0
                          ? "점검 항목 없음"
                          : closeProgress.passedCheckCount === null
                            ? "점검 결과 미확인"
                            : `점검 항목 ${closeProgress.totalCheckCount}개 중 ${closeProgress.passedCheckCount}개 통과`}
                      </strong>
                      <StatusBadge tone="neutral">
                        {closeProgress.snapshot ? "당시 마감 사본" : "현재 점검"}
                      </StatusBadge>
                    </div>
                    {closeProgress.passedCheckCount !== null &&
                      closeProgress.totalCheckCount > 0 && (
                        <div
                          className="dashboard-close-segments"
                          role="progressbar"
                          aria-label="마감 점검 진행"
                          aria-valuemin={0}
                          aria-valuemax={closeProgress.totalCheckCount}
                          aria-valuenow={closeProgress.passedCheckCount}
                        >
                          {Array.from(
                            { length: closeProgress.totalCheckCount },
                            (_, index) => (
                              <span
                                key={index}
                                aria-hidden="true"
                                className={
                                  index < closeProgress.passedCheckCount!
                                    ? "passed"
                                    : ""
                                }
                              />
                            ),
                          )}
                        </div>
                      )}
                    {closeProgress.remainingChecks.map((check) => (
                      <p
                        key={check.checkCode}
                        data-testid={`dashboard-check-${testKey(check.checkCode)}`}
                      >
                        <StatusBadge tone="warn">
                          {closeCheckLabels[check.checkCode] ?? "마감 점검"}{" "}
                          {count(check.count)}건
                        </StatusBadge>{" "}
                        {dashboardMessage(check.reason)}
                      </p>
                    ))}
                    <DashboardBasis basis={closeProgress.basis} hidePeriod />
                  </Panel>
                )}
                {data.projectAlerts && (
                  <Panel
                    title={`프로젝트 경보 ${data.projectAlerts.length}건`}
                    testId="dashboard-alerts"
                  >
                    <DashboardBasis basis={data.projectAlertsBasis} hidePeriod />
                    {data.projectAlerts.length === 0 &&
                      ["AVAILABLE", "EMPTY"].includes(
                        data.projectAlertsBasis.status,
                      ) && (
                        <p className="dashboard-empty">
                          프로젝트 경보가 없습니다.
                        </p>
                      )}
                    {data.projectAlerts.length > 0 && (
                      <div className="dashboard-compact-list">
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
                              <small>{dashboardMessage(alert.reason)}</small>
                              <small>
                                {contractTypeLabels[alert.contractTypeCode] ??
                                  "계약 구분 미확인"}{" "}
                                마진율 {formatRate(alert.marginRate)} · 예산 소진{" "}
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
                              {alertLabels[alert.alertType] ?? "프로젝트 점검"}
                            </StatusBadge>
                          </button>
                        ))}
                      </div>
                    )}
                  </Panel>
                )}
              </div>
            </div>
            <div className="dashboard-bottom-grid">
              {data.salesParts && (
                <Panel
                  title={`${Number(data.selectedMonth.slice(5))}월 영업파트별 현황`}
                  testId="dashboard-parts"
                  actions={
                    <button
                      type="button"
                      data-testid="dashboard-parts-open"
                      onClick={() =>
                        go(
                          { screenId: "UI-07", linkKey: null },
                          { ...data.salesPartsBasis, basisMonth: data.selectedMonth },
                        )
                      }
                    >
                      자세히
                    </button>
                  }
                >
                  <DashboardBasis basis={data.salesPartsBasis} hidePeriod />
                  <div className="sicost-table-viewport">
                    <table
                      className="sicost-table dashboard-parts-table"
                      aria-label="영업 파트별 실적"
                    >
                      <thead>
                        <tr>
                          <th scope="col">파트</th>
                          <th scope="col">
                            매출 <small>({amountUnit})</small>
                          </th>
                          <th scope="col">
                            전체원가 <small>({amountUnit})</small>
                          </th>
                          <th scope="col">기여이익률</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.salesParts.length === 0 &&
                          ["AVAILABLE", "EMPTY"].includes(
                            data.salesPartsBasis.status,
                          ) && (
                            <tr>
                              <td colSpan={4}>
                                <div className="dashboard-empty">
                                  영업 파트 자료가 없습니다.
                                </div>
                              </td>
                            </tr>
                          )}
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
                  </div>
                  <p className="dashboard-parts-note">
                    {dashboardMessage(data.salesPartExplanation)}
                  </p>
                </Panel>
              )}
              <Panel
                title="영업 파이프라인"
                testId="dashboard-pipeline"
                actions={
                  <button
                    type="button"
                    data-testid="dashboard-pipeline-open"
                    onClick={() =>
                      go(
                        { screenId: "UI-29", linkKey: null },
                        data.pipeline.basis,
                      )
                    }
                  >
                    자세히
                  </button>
                }
              >
                <DashboardBasis basis={data.pipeline.basis} hidePeriod />
                <p className="dashboard-pipeline-count">
                  진행 기회 {count(data.pipeline.opportunityCount)}건
                </p>
                {data.pipeline.startingSoon.length === 0 &&
                  ["AVAILABLE", "EMPTY"].includes(
                    data.pipeline.basis.status,
                  ) && (
                    <p className="dashboard-empty">
                      곧 시작할 기회가 없습니다.
                    </p>
                  )}
                {data.pipeline.startingSoon.length > 0 && (
                  <div className="dashboard-compact-list">
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
                  </div>
                )}
                <footer className="dashboard-pipeline-total">
                  <strong>
                    확률 반영 예상액 {money(data.pipeline.weightedExpectedAmount)}{" "}
                    {amountUnit}
                  </strong>
                </footer>
              </Panel>
              {data.cashForecast && (
                <Panel title="자금 (앞으로 3개월)" testId="dashboard-cash">
                  <DashboardBasis basis={data.cashBasis} hidePeriod />
                  <div data-testid="dashboard-cash-chart">
                    <ChartHost
                      option={{
                        ...seriesChartOptions({
                          categories: data.cashForecast.map((row) => row.month),
                          series: [
                            {
                              name: "월말 잔액",
                              values: data.cashForecast.map(
                                (row) => row.closingBalance,
                              ),
                              showEndLabel: false,
                            },
                            {
                              name: "최소 자금",
                              values: data.cashForecast.map(
                                (row) => row.minimumCashAmount,
                              ),
                              dashed: true,
                            },
                          ],
                          formatValue: (value) =>
                            `${money(value)} ${amountUnit}`,
                          axisUnit: amountUnit,
                          edgeLabelsInside: true,
                        }),
                        grid: {
                          left: 12,
                          right: 16,
                          top: 64,
                          bottom: 30,
                          containLabel: true,
                        },
                      }}
                    >
                      <p>
                        아래 월별 행에서 월말 잔액과 최소 자금을 확인할 수
                        있습니다.
                      </p>
                    </ChartHost>
                  </div>
                  {data.cashForecast.length > 0 && (
                    <div className="dashboard-compact-list">
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
                            <strong>{row.month} </strong>
                            <small>
                              월말 잔액 {money(row.closingBalance)} {amountUnit}
                            </small>
                            <small>
                              최소 자금 {money(row.minimumCashAmount)} {amountUnit}
                            </small>
                            {row.unconfirmedReasons.map((reason, i) => (
                              <small key={i}>{dashboardMessage(reason)}</small>
                            ))}
                          </span>
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
                        </button>
                      ))}
                    </div>
                  )}
                </Panel>
              )}
            </div>
            <Dialog
              open={actionsOpen}
              title={`확인할 일 전체 ${actions.length}유형`}
              onCancel={() => setActionsOpen(false)}
              onConfirm={() => setActionsOpen(false)}
              confirmLabel="닫기"
              hideCancel
              testId="dashboard-actions-dialog"
              confirmTestId="dashboard-actions-close"
            >
              <div className="dashboard-compact-list">
                {actions.map((action) => actionRow(action, true))}
              </div>
            </Dialog>
          </>
        )}
      </div>
    </SharedScreenFrame>
  );
}

function DashboardSupply({ data }: { data: api.DashboardData }) {
  return (
    <Panel title="앞으로 6개월 인력 수급 예측" testId="dashboard-supply">
      <DashboardBasis basis={data.supplyBasis} hidePeriod />
      <p>
        {data.scope === "ALL" ? "전체 인력" : "허용된 담당 범위의 인력"} ·
        미충원 포함 · 단위: 명
      </p>
      <ChartHost
        option={seriesChartOptions({
          categories: data.supplyForecast.map((row) => row.month),
          series: [
            {
              name: "필요",
              type: "bar",
              values: data.supplyForecast.map((row) => row.requiredPersons),
            },
            {
              name: "가용",
              type: "bar",
              values: data.supplyForecast.map((row) => row.availablePersons),
            },
          ],
          formatValue: (value) => `${formatFixedDecimal(value, 1)}명`,
          axisUnit: "명",
        })}
      >
        <p className="dashboard-empty">
          차트 대신 아래 숫자표에서 인력 수급 예측을 확인할 수 있습니다.
        </p>
      </ChartHost>
      <div className="sicost-table-viewport">
        <table className="sicost-table" aria-label="인력 수급 예측 숫자표">
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
              <tr key={row.month} data-testid={`dashboard-supply-${row.month}`}>
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
  );
}
