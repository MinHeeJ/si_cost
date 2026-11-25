import { ScreenLoading } from "../../shared/ScreenLoading";
import { FieldHelp } from "../../shared/FieldHelp";
import { FinancialOverview, getFinancialWarnings } from "../../shared/FinancialOverview";
import { FinancialWarnings } from "../../shared/FinancialWarnings";
import { KpiStrip } from "../../shared/Surface";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import {
  formatAmount,
  compareDecimal,
  formatRate,
  type AmountUnit,
} from "../../shared/format";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { FinancialHistoryPanel } from "../apiops/FinancialHistoryPanel";
import { BusinessPlanImportDialog } from "../imports/BusinessPlanImportDialog";
import { BusinessPlanRevenueChart } from "./BusinessPlanRevenueChart";
import * as api from "./businessPlanPerformanceApi";
import "./businessPlanPerformance.css";

const events = [
  "sicost:projects-committed",
  "sicost:clients-committed",
  "sicost:sales-parts-committed",
  "sicost:operating-standards-committed",
  "sicost:codegroups-committed",
  "sicost:assignments-committed",
  "sicost:payroll-committed",
  "sicost:financial-committed",
  "sicost:financial-items-committed",
  "sicost:corporate-tax-committed",
  "sicost:adjustments-committed",
  "sicost:bills-committed",
  "sicost:billing-committed",
  "sicost:developers-committed",
  "sicost:historical-profit-loss-committed",
  api.BUSINESS_PLAN_COMMITTED,
  "sicost:business-plan-import-committed",
  "sicost:historical-profit-loss-import-committed",
  "sicost:project-expenses-committed",
  "sicost:distribution-committed",
];
const labels: Record<api.PlanSummary["metric"], string> = {
  CUMULATIVE_SALES: "누계 매출 달성률",
  CUMULATIVE_OPERATING_PROFIT: "누계 영업이익 달성률",
  CURRENT_MONTH_SALES: "기준월 매출 달성률",
};
const planGuide = (
  <dl>
    <div>
      <dt>실적과 목표의 기준</dt>
      <dd>
        실적은 영업이익표의 같은 달 매출액과 영업이익입니다. 턴키는 청구한 달의
        공급가액, 월단위·파견은 일한 달의 매출을 집계하며 금액은 부가세를 제외합니다.
        누계는 1월부터 선택한 기준월까지의 실적과 목표를 비교합니다.
      </dd>
    </div>
    <div>
      <dt>달성률과 이익률 차이</dt>
      <dd>
        달성률은 실적을 목표로 나눈 뒤 100을 곱한 값입니다. 반올림 전 달성률이
        100% 미만이면 빨간색으로 표시합니다. 이익률 차이는 실적 영업이익률에서
        목표 영업이익률을 뺀 값(%p)입니다. 목표가 없거나 0 이하인 달은 달성률을
        ‘–’로 표시합니다.
      </dd>
    </div>
    <div>
      <dt>목표 업로드</dt>
      <dd>
        회사 전체의 올해 12개월 매출·영업이익 목표를 PC에서 등록합니다.
        목표 업로드 창에서 양식을 내려받고 파일을 검증한 뒤 반영할 수 있습니다.
        지난 해 목표는 조회할 수 있습니다.
      </dd>
    </div>
  </dl>
);

const rows: {
  key: keyof api.PlanMonth;
  label: string;
  amount: boolean;
  adverse?: keyof api.PlanMonth;
}[] = [
  { key: "salesTargetAmt", label: "매출 목표", amount: true },
  { key: "salesActualAmt", label: "매출 실적", amount: true },
  {
    key: "salesAchievementRate",
    label: "달성률",
    amount: false,
    adverse: "salesUnderTarget",
  },
  { key: "operatingProfitTargetAmt", label: "영업이익 목표", amount: true },
  { key: "operatingProfitActualAmt", label: "영업이익 실적", amount: true },
  {
    key: "operatingMarginDifference",
    label: "차이(이익률)",
    amount: false,
    adverse: "operatingMarginAdverse",
  },
];

function missingRateReason(month: api.PlanMonth, key: keyof api.PlanMonth) {
  if (
    (key !== "salesAchievementRate" && key !== "operatingMarginDifference") ||
    month[key] != null ||
    month.actual.sourceKind === "FUTURE"
  )
    return undefined;
  if (
    month.salesTargetAmt == null ||
    compareDecimal(month.salesTargetAmt, "0") === 0 ||
    (key === "operatingMarginDifference" &&
      month.operatingProfitTargetAmt == null)
  )
    return "목표 없음";
  if (
    month.salesActualAmt == null ||
    (key === "operatingMarginDifference" &&
      month.operatingProfitActualAmt == null)
  )
    return "실적 자료 미확인";
  if (
    key === "operatingMarginDifference" &&
    compareDecimal(month.salesActualAmt, "0") === 0
  )
    return "매출 실적이 0이라 이익률을 비교하지 않습니다";
  return undefined;
}

// SCR-UI-18 — the existing router and shared frame retain menu, favorites, tabs and YEAR_CUM.
export function BusinessPlanPerformanceScreen({
  amountUnit,
  onAmountUnitChange,
  dense,
}: {
  amountUnit?: AmountUnit;
  dense?: boolean;
  onAmountUnitChange?(unit: AmountUnit): void;
} = {}) {
  function setUnit(next: AmountUnit) {
    if (onAmountUnitChange) onAmountUnitChange(next);
    else setLocalUnit(next);
  }
  const [period, setPeriod] = useState<{ year: number; month: string }>();
  const [data, setData] = useState<api.BusinessPlanPerformanceData>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true),
    [revision, setRevision] = useState(0);
  const [localUnit, setLocalUnit] = useState<AmountUnit>("천원");
  const unit = amountUnit ?? localUnit;
  const [upload, setUpload] = useState(false);
  const [uploadYear, setUploadYear] = useState<number>();
  const [mobile, setMobile] = useState(() => window.innerWidth < 768);
  const sequence = useRef(0);
  useEffect(() => {
    const changed = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          value: { type: string; year: string; month: string };
        }>
      ).detail;
      if (detail?.screenId === "UI-18" && detail.value.type === "YEAR_CUM") {
        setPeriod((previous) =>
          previous?.month === detail.value.month
            ? previous
            : { year: Number(detail.value.year), month: detail.value.month },
        );
        setUpload(false);
      }
    };
    const refresh = () => setRevision((n) => n + 1),
      resize = () => setMobile(window.innerWidth < 768);
    window.addEventListener("sicost:screen-period-changed", changed);
    window.addEventListener("resize", resize);
    events.forEach((event) => window.addEventListener(event, refresh));
    return () => {
      sequence.current++;
      window.removeEventListener("sicost:screen-period-changed", changed);
      window.removeEventListener("resize", resize);
      events.forEach((event) => window.removeEventListener(event, refresh));
    };
  }, []);
  const load = useCallback(
    async (signal?: AbortSignal) => {
      if (!period) return;
      const ticket = ++sequence.current;
      setLoading(true);
      setError(undefined);
      setData(undefined);
      try {
        const result = await api.getBusinessPlanPerformance(
          period.year,
          period.month,
          signal,
        );
        if (ticket === sequence.current && !signal?.aborted) setData(result);
      } catch (failure) {
        if (ticket === sequence.current && !signal?.aborted) setError(failure);
        throw failure;
      } finally {
        if (ticket === sequence.current && !signal?.aborted) setLoading(false);
      }
    },
    [period],
  );
  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal).catch(() => {});
    return () => controller.abort();
  }, [load, revision]);
  const unregisteredNotice =
    data?.targetStatus === "UNREGISTERED"
      ? `${data.context.year}년 사업계획 목표가 아직 없습니다. [목표 업로드]로 올려 주세요.`
      : undefined;
  const currentYear = Number(data?.context.nowMonth.slice(0, 4));
  const currentPlan = data?.context.year === currentYear;
  const cutoffMonth = data ? Number(data.context.month.slice(5)) : 0;
  const warnings = getFinancialWarnings(data?.notices ?? [], {
    excludeCompleteCoverage: true,
  }).filter((notice) => notice !== unregisteredNotice);
  return (
    <SharedScreenFrame
      screenId="UI-18"
      screenGuide={planGuide}
      periodBarOptions={{ showMonthCloseStatus: false, showCumulativeRange: false }}
      headerActions={
        <div className="sicost-title-action-group business-plan-toolbar">
          <label>
            단위{" "}
            <select
              data-testid="business-plan-unit"
              value={unit}
              onChange={(event) => setUnit(event.target.value as AmountUnit)}
            >
              <option>원</option>
              <option>천원</option>
              <option>백만원</option>
            </select>
          </label>

          <button
            data-testid="business-plan-upload"
            className="sicost-primary"
            type="button"
            disabled={!currentPlan || mobile}
            onClick={() => {
              setUploadYear(currentYear);
              setUpload(true);
            }}
          >
            목표 업로드
          </button>
        </div>
      }
    >
      <div
        className={`business-plan-performance${dense ? " financial-display-dense" : ""}`}
        data-testid="business-plan-performance"
        data-screen-id="SCR-UI-18"
      >
        {mobile && <p role="note">목표 업로드는 PC에서 이용해 주세요.</p>}
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "사업계획 실적을 불러오지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => setRevision((n) => n + 1)}
          />
        )}
        {loading && (
          <ScreenLoading message="사업계획 대비 실적을 불러오는 중입니다." />
        )}
        {data && (
          <>
            <FinancialOverview
              context={data.context}
              months={data.months.map((m) => m.actual)}
              provisional={data.months.some((m) => m.actual.provisional)}
              caption={null}
              periodDetail={
                <span className="business-plan-period-detail">
                  <span>집계 기간 <b>{cutoffMonth === 1 ? "1월" : `1~${cutoffMonth}월`}</b></span>
                  {unregisteredNotice && (
                    <span
                      className="business-plan-target-status"
                      role="status"
                      data-testid="business-plan-unregistered"
                    >
                      <StatusBadge tone="neutral">{data.context.year}년 목표 미등록</StatusBadge>
                      {currentPlan && !mobile && <span>목표 업로드로 등록해 주세요.</span>}
                    </span>
                  )}
                </span>
              }
            />
            {warnings.length > 0 && (
              <div className="business-plan-status-actions">
                <FinancialWarnings warnings={warnings} />
              </div>
            )}
            <KpiStrip className="business-plan-summary">
              {data.summary.map((summary) => (
                <article
                  key={summary.metric}
                  data-testid={`business-plan-summary-${summary.metric.toLowerCase().replaceAll("_", "-")}`}
                >
                  <p>{labels[summary.metric]}</p>
                  <strong
                    className={
                      summary.underTarget === true
                        ? "business-plan-adverse"
                        : ""
                    }
                    title={`실적 ${formatAmount(summary.actualAmt, "원")}원 / 목표 ${formatAmount(summary.targetAmt, "원")}원 · 반올림 전 비교`}
                  >
                    {formatRate(summary.achievementRate)}
                  </strong>
                  <p>
                    실적 {formatAmount(summary.actualAmt, unit)} / 목표{" "}
                    {formatAmount(summary.targetAmt, unit)}
                  </p>
                  {summary.achievementRate == null && (
                    <small>목표 없음 또는 자료 미확인</small>
                  )}
                </article>
              ))}
            </KpiStrip>
            <BusinessPlanRevenueChart months={data.months} unit={unit} />
            <div className="business-plan-scroll">
              <table
                aria-label="사업계획 월별 실적"
                className="business-plan-table"
              >
                <thead>
                  <tr>
                    <th>항목</th>
                    {data.months.map((month, index) => (
                      <th
                        key={month.month}
                        data-testid={`business-plan-month-header-${month.month}`}
                      >
                        {index + 1}월<br />
                        <StatusBadge
                          tone={
                            month.actual.provisional
                              ? "info"
                              : month.actual.sourceKind === "MISSING"
                                ? "warn"
                                : "lock"
                          }
                        >
                          {month.actual.sourceKind === "FUTURE"
                            ? "–"
                            : month.actual.sourceKind === "MIGRATED"
                              ? "이관"
                              : month.actual.sourceKind === "CLOSED_SNAPSHOT"
                                ? "확정"
                                : month.actual.sourceKind === "MISSING"
                                  ? "미확인"
                                  : "작업중"}
                        </StatusBadge>
                        {month.actual.completeness.estimated.length > 0 && (
                          <StatusBadge tone="warn">예정 포함</StatusBadge>
                        )}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr
                      key={row.key}
                      className={
                        !row.amount ? "business-plan-emphasis" : undefined
                      }
                      data-testid={`business-plan-row-${row.key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}`}
                    >
                      <th scope="row">
                        {row.label}
                        <FieldHelp label={row.label} kind="B" />
                      </th>
                      {data.months.map((month) => {
                        const value = month[row.key] as string | null;
                        return (
                          <td
                            key={month.month}
                            data-testid={`business-plan-cell-${row.key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}-${month.month}`}
                            data-financial-negative={
                              row.amount && value?.startsWith("-")
                            }
                            className={
                              row.adverse && month[row.adverse] === true
                                ? "business-plan-adverse"
                                : ""
                            }
                            title={missingRateReason(month, row.key)}
                          >
                            {row.amount
                              ? formatAmount(value, unit)
                              : value == null
                                ? "–"
                                : row.key === "operatingMarginDifference"
                                  ? `${value}%p`
                                  : formatRate(value)}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <FinancialHistoryPanel
              month={data.context.month}
              initialScope="BUSINESS_PLAN"
              refreshKey={revision}
            />
          </>
        )}
        {upload && uploadYear && !mobile && (
          <BusinessPlanImportDialog
            year={uploadYear}
            currentYear={uploadYear}
            onClose={() => setUpload(false)}
            onApplied={() => load()}
          />
        )}
      </div>
    </SharedScreenFrame>
  );
}
