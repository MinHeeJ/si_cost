import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import {
  formatAmount,
  formatDateTime,
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
const basis =
  "실적 = 영업이익표의 같은 월 값. TK는 청구일 기준 유효 청구 공급가액, MO·DP는 투입 귀속월 매출입니다. 입금액 아님 · 부가세 제외. 목표 = 업로드한 회사 전체 목표.";
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

// SCR-UI-18 — the existing router and shared frame retain menu, favorites, tabs and YEAR_CUM.
export function BusinessPlanPerformanceScreen({ amountUnit, onAmountUnitChange, dense }: {
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
  const [upload, setUpload] = useState(false),
    [downloading, setDownloading] = useState(false);
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
  async function template() {
    if (!period || downloading) return;
    setDownloading(true);
    setError(undefined);
    try {
      await api.downloadBusinessPlanTemplate(period.year);
    } catch (failure) {
      setError(failure);
    } finally {
      setDownloading(false);
    }
  }
  return (
    <SharedScreenFrame screenId="UI-18">
      <div
        className={`business-plan-performance${dense ? " financial-display-dense" : ""}`}
        data-testid="business-plan-performance"
        data-screen-id="SCR-UI-18"
      >
        <div className="business-plan-toolbar">
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
            data-testid="business-plan-template"
            type="button"
            disabled={!period || downloading || mobile}
            onClick={() => void template()}
          >
            양식 내려받기
          </button>
          <button
            data-testid="business-plan-upload"
            className="sicost-primary"
            type="button"
            disabled={!data || mobile || data.context.year !== Number(data.context.nowMonth.slice(0, 4))}
            onClick={() => {
              setUploadYear(Number(data!.context.nowMonth.slice(0, 4)));
              setUpload(true);
            }}
          >
            목표 업로드
          </button>
          {data && (
            <span>
              양식: {data.context.year}년 · 업로드:{" "}
              {data.context.nowMonth.slice(0, 4)}년 목표
            </span>
          )}
        </div>
        {mobile && (
          <p role="note">목표 업로드와 양식 내려받기는 PC에서 이용해 주세요.</p>
        )}
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
          <p role="status">사업계획 대비 실적을 불러오는 중입니다.</p>
        )}
        {data && (
          <>
            <p className="business-plan-context">
              {data.context.year}년 1~{data.context.month.slice(5)}월 · 회사
              전체 · 읽기 전용 · 조회 {formatDateTime(data.context.snapshotAt)}
            </p>
            {data.context.year !==
              Number(data.context.nowMonth.slice(0, 4)) && (
              <p className="business-plan-warning">
                지난 해 실적·목표는 읽기 전용입니다.
              </p>
            )}
            {data.notices.map((notice, index) => (
              <p
                className="business-plan-warning"
                role="note"
                key={index}
                data-testid={`business-plan-notice-${index}`}
              >
                {notice}
              </p>
            ))}
            <div className="business-plan-summary">
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
            </div>
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
                      <th key={month.month}>
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
                      data-testid={`business-plan-row-${row.key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}`}
                    >
                      <th scope="row">
                        {row.label}{" "}
                        {row.amount && (
                          <span
                            tabIndex={0}
                            title={basis}
                            aria-label={`${row.label} 원천 설명`}
                          >
                            ⓘ
                          </span>
                        )}
                      </th>
                      {data.months.map((month) => {
                        const value = month[row.key] as string | null;
                        return (
                          <td
                            key={month.month}
                            data-financial-negative={row.amount && value?.startsWith("-")}
                            className={
                              row.adverse && month[row.adverse] === true
                                ? "business-plan-adverse"
                                : ""
                            }
                            title={`${basis}\n실적매출 ${formatAmount(month.salesActualAmt, "원")}원 / 목표매출 ${formatAmount(month.salesTargetAmt, "원")}원 · 실적영업이익 ${formatAmount(month.operatingProfitActualAmt, "원")}원 / 목표영업이익 ${formatAmount(month.operatingProfitTargetAmt, "원")}원\n${month.actual.completeness.issues.map((issue) => issue.reason).join("; ")}`}
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
            <p role="note">
              {basis} 미래 실적은 결측이며 미래 목표는 12월까지 표시합니다. 목표
              0·음수는 달성률을 계산하지 않습니다. 경계 색상은 반올림 전
              실제율을 따릅니다.
            </p>
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
