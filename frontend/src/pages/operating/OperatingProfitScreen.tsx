import { FinancialOverview, getFinancialWarnings } from "../../shared/FinancialOverview";
import { FinancialWarnings } from "../../shared/FinancialWarnings";
import { KpiStrip } from "../../shared/Surface";
import { FinancialEmptyReset } from "../shared/FinancialEmptyReset";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import {
  formatAmount,
  formatRate,
  type AmountUnit,
} from "../../shared/format";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { ScreenPeriodBar } from "../shared/ScreenPeriodBar";
import { HistoricalProfitLossImportDialog } from "../migration/HistoricalProfitLossImportDialog";
import { HISTORICAL_PROFIT_LOSS_IMPORT_COMMITTED } from "../migration/historicalProfitLossImportsApi";
import { FinancialHistoryPanel } from "../apiops/FinancialHistoryPanel";
import type { Comparison } from "../structure/profitStructureApi";
import { FinancialStatementTable } from "../shared/FinancialStatementTable";
import * as api from "./operatingProfitApi";
import "./operatingProfit.css";

const sourceEvents = [
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
  HISTORICAL_PROFIT_LOSS_IMPORT_COMMITTED,
  "sicost:business-plans-committed",
  "sicost:business-plan-import-committed",
  "sicost:project-expenses-committed",
  "sicost:distribution-committed",
];
const labels: Record<string, string> = {
  salesAmt: "누계 매출",
  grossPrftAmt: "누계 매출총이익",
  oprtPrftAmt: "누계 영업이익",
  currentMonthOperatingProfit: "기준월 영업이익",
};
const operatingGuide = (
  <dl>
    <div>
      <dt>매출 집계 기준</dt>
      <dd>턴키는 청구한 달의 공급가액, 월단위·파견은 일한 달의 매출입니다. 화면의 금액은 부가세를 제외합니다.</dd>
    </div>
    <div>
      <dt>누계와 전년 비교</dt>
      <dd>1월부터 기준월까지의 누계를 전년 같은 기간과 비교합니다. 비용이 늘어난 경우 증감 수치를 빨간색으로 표시합니다.</dd>
    </div>
    <div>
      <dt>전월 조정</dt>
      <dd>마감한 달의 숫자는 그대로 두고, 재무 항목에서 현재 편집할 수 있는 월에 조정액을 입력합니다. 조정액은 그 달의 ‘전월 조정’ 행에 반영됩니다.</dd>
    </div>
  </dl>
);

const kebab = (value: string) =>
  value.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);
function Change({
  comparison,
  label = "",
}: {
  comparison: Comparison | null;
  label?: string;
}) {
  if (!comparison) return null;
  const value = comparison.percentagePointChange ?? comparison.changeRate;
  return (
    <span
      className={`operating-change ${comparison.direction.toLowerCase()}`}
      title={`비교 ${comparison.comparisonMonths.join(", ")} · 빠진 월 ${comparison.missingMonths.join(", ") || "없음"}`}
    >
      {label}
      {value == null
        ? "–"
        : `${value.startsWith("-") || value === "0.0" ? "" : "+"}${value}${comparison.percentagePointChange == null ? "%" : "%p"}`}
      {comparison.availableMonthCount < comparison.requestedMonthCount &&
        ` · 자료 ${comparison.availableMonthCount}/${comparison.requestedMonthCount}개월`}
    </span>
  );
}
// SCR-UI-08
export function OperatingProfitScreen({
  amountUnit,
  onAmountUnitChange,
  dense,
}: {
  amountUnit?: AmountUnit;
  dense?: boolean;
  onAmountUnitChange?(unit: AmountUnit): void;
} = {}) {
  const navigate = useNavigate(),
    location = useLocation();
  const incoming = location.state as {
    month?: string;
    period?: { month?: string };
  } | null;
  const initialMonth = incoming?.period?.month ?? incoming?.month;
  function setUnit(next: AmountUnit) {
    if (onAmountUnitChange) onAmountUnitChange(next);
    else setLocalUnit(next);
  }
  const [period, setPeriod] = useState<{ year: number; month: string }>();
  const [localUnit, setLocalUnit] = useState<AmountUnit>("천원"),
    [data, setData] = useState<api.OperatingProfitData>();
  const unit = amountUnit ?? localUnit;
  const [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true),
    [revision, setRevision] = useState(0);
  const [upload, setUpload] = useState(false),
    [pending, setPending] = useState(false),
    [mobile, setMobile] = useState(() => window.innerWidth < 768);
  const committed = useRef(false);
  useEffect(() => {
    const changed = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          value: { type: string; year: string; month: string };
        }>
      ).detail;
      if (detail?.screenId === "UI-08" && detail.value.type === "YEAR_CUM")
        setPeriod((previous) =>
          previous?.month === detail.value.month
            ? previous
            : { year: Number(detail.value.year), month: detail.value.month },
        );
    };
    const refresh = () => {
        committed.current = true;
        setRevision((n) => n + 1);
      },
      resize = () => setMobile(window.innerWidth < 768);
    window.addEventListener("sicost:screen-period-changed", changed);
    window.addEventListener("resize", resize);
    sourceEvents.forEach((event) => window.addEventListener(event, refresh));
    return () => {
      window.removeEventListener("sicost:screen-period-changed", changed);
      window.removeEventListener("resize", resize);
      sourceEvents.forEach((event) =>
        window.removeEventListener(event, refresh),
      );
    };
  }, []);
  useEffect(() => {
    if (!period) return;
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void api
      .getOperatingProfitLoss(period.year, period.month, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setData(result);
          committed.current = false;
        }
      })
      .catch((failure) => {
        if (!controller.signal.aborted) setError(failure);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [period, revision]);
  async function download() {
    if (pending || !period) return;
    setPending(true);
    setError(undefined);
    try {
      await api.downloadOperatingProfitLoss(
        period.year,
        period.month,
        unit === "원"
          ? "WON"
          : unit === "천원"
            ? "THOUSAND_WON"
            : "MILLION_WON",
      );
    } catch (failure) {
      setError(failure);
    } finally {
      setPending(false);
    }
  }
  const warnings = getFinancialWarnings(data?.notices ?? []).filter((notice) => {
    const coverage = notice.match(/^(?:전년 )?자료\s+(\d+)\/(\d+)개월$/);
    return !coverage || coverage[1] !== coverage[2];
  });
  const cutoffMonth = data ? Number(data.context.month.slice(5)) : 0;
  return (
    <SharedScreenFrame
      screenId="UI-08"
      screenGuide={operatingGuide}
      periodBar={
        <ScreenPeriodBar
          screenId="UI-08"
          initialMonth={initialMonth}
          showMonthCloseStatus={false}
          showCumulativeRange={false}
        />
      }
      headerActions={
        <div className="sicost-title-action-group operating-actions">
          <label>
            단위{" "}
            <select
              data-testid="operating-unit"
              value={unit}
              onChange={(event) => setUnit(event.target.value as AmountUnit)}
            >
              <option>원</option>
              <option>천원</option>
              <option>백만원</option>
            </select>
          </label>
          <button
            type="button"
            data-testid="operating-upload"
            disabled={mobile}
            onClick={() => setUpload(true)}
          >
            전년 손익 업로드
          </button>

          <button
            type="button"
            data-testid="operating-export"
            disabled={!data || loading || pending || mobile}
            onClick={() => void download()}
          >
            엑셀 다운로드
          </button>
        </div>
      }
    >
      <div
        className={`operating-profit${dense ? " financial-display-dense" : ""}`}
        data-testid="operating-profit-screen"
        data-screen-id="SCR-UI-08"
      >
        {mobile && (
          <p role="note">엑셀 업로드·다운로드는 PC에서 이용해 주세요.</p>
        )}
        {loading && <p role="status">영업이익표를 불러오는 중입니다.</p>}
        {error != null && (
          <ErrorBanner
            message={`${committed.current ? "저장은 완료됐지만 손익표를 다시 불러오지 못했습니다. " : ""}${error instanceof Error ? error.message : "영업이익표 조회 실패"}`}
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => setRevision((n) => n + 1)}
          />
        )}
        {data && (
          <>
            <FinancialOverview
              context={data.context}
              months={data.months}
              provisional={data.cumulativeProvisional}
              caption={null}
              periodDetail={
                <span>집계 기간 <b>{cutoffMonth === 1 ? "1월" : `1~${cutoffMonth}월`}</b></span>
              }
            />
            <div className="operating-toolbar">
              <button
                type="button"
                data-testid="operating-adjustment"
                onClick={() =>
                  navigate("/UI-10", { state: { section: "adjustments" } })
                }
              >
                전월 조정 입력
              </button>
              {warnings.length > 0 && <FinancialWarnings warnings={warnings} />}
            </div>
            {data.completeness.availableMonthCount === 0 && (
              <div className="operating-empty">
                조회 기간의 손익 자료가 없습니다. 미확인 금액은 0으로 보정하지
                않습니다.
                <FinancialEmptyReset screenId="UI-08" />
              </div>
            )}
            <KpiStrip className="operating-summary">
              {data.summary.map((card) => (
                <article
                  className="operating-card"
                  key={card.metric}
                  data-testid={`operating-card-${kebab(card.metric)}`}
                >
                  <h2>{labels[card.metric] ?? card.metric}</h2>
                  <strong
                    data-financial-negative={card.amount?.startsWith("-")}
                  >
                    {formatAmount(card.amount, unit)} <small>{unit}</small>
                  </strong>
                  {card.metric === "oprtPrftAmt" && (
                    <p>영업이익률 {formatRate(card.rate)}</p>
                  )}
                  <Change
                    comparison={card.previousYear}
                    label="전년 같은 기간 대비 "
                  />
                  <Change comparison={card.previous} label="전월 대비 " />
                </article>
              ))}
            </KpiStrip>
            <FinancialStatementTable
              currentMonth={data.context.nowMonth}
              months={data.months}
              rows={data.rows}
              month={data.context.month}
              unit={unit}
              showPreviousYear={data.showPreviousYear}
              provisional={data.cumulativeProvisional}
              name="영업이익표"
              testPrefix="operating"
            />
            <FinancialHistoryPanel
              month={data.context.month}
              initialScope="HISTORICAL_PROFIT_LOSS"
              refreshKey={revision}
            />
          </>
        )}
        {upload && (
          <HistoricalProfitLossImportDialog
            onClose={() => setUpload(false)}
            onApplied={() => {
              committed.current = true;
              setRevision((n) => n + 1);
            }}
          />
        )}
      </div>
    </SharedScreenFrame>
  );
}
