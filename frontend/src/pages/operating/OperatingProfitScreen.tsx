import { FinancialEmptyReset } from "../shared/FinancialEmptyReset";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import {
  formatAmount,
  formatRate,
  formatDateTime,
  type AmountUnit,
} from "../../shared/format";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { ScreenPeriodBar } from "../shared/ScreenPeriodBar";
import { HistoricalProfitLossImportDialog } from "../migration/HistoricalProfitLossImportDialog";
import {
  getHistoricalProfitLossImportTemplate,
  saveDownload,
  HISTORICAL_PROFIT_LOSS_IMPORT_COMMITTED,
} from "../migration/historicalProfitLossImportsApi";
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
export function OperatingProfitScreen({ amountUnit, onAmountUnitChange, dense }: {
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
  async function download(template: boolean) {
    if (pending || !period) return;
    setPending(true);
    setError(undefined);
    try {
      if (template) saveDownload(await getHistoricalProfitLossImportTemplate());
      else
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
  return (
    <SharedScreenFrame
      screenId="UI-08"
      periodBar={
        <ScreenPeriodBar screenId="UI-08" initialMonth={initialMonth} />
      }
    >
      <div
        className={`operating-profit${dense ? " financial-display-dense" : ""}`}
        data-testid="operating-profit-screen"
        data-screen-id="SCR-UI-08"
      >
        <div className="operating-toolbar">
          <button
            type="button"
            data-testid="operating-template"
            disabled={!period || pending || mobile}
            onClick={() => void download(true)}
          >
            전년 손익 양식
          </button>
          <button
            type="button"
            data-testid="operating-upload"
            disabled={mobile}
            onClick={() => setUpload(true)}
          >
            전년 손익 업로드
          </button>
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
            data-testid="operating-export"
            disabled={!data || loading || pending || mobile}
            onClick={() => void download(false)}
          >
            엑셀 다운로드
          </button>
        </div>
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
            <p className="operating-muted">
              {data.context.month} 기준 · 부가세 제외 · 회사 전체 · 읽기 전용 ·
              조회 {formatDateTime(data.context.snapshotAt)}
            </p>
            {data.context.year < Number(data.context.nowMonth.slice(0, 4)) && (
              <StatusBadge tone="lock">지난 해·읽기 전용</StatusBadge>
            )}
            {data.cumulativeProvisional && (
              <div className="operating-banner" role="note">
                <StatusBadge tone="warn">잠정</StatusBadge> 미마감 월이 포함되어
                있습니다. 원천 변경 시 다시 계산합니다.
              </div>
            )}
            {data.completeness.estimated.length > 0 && (
              <div className="operating-banner info">
                <StatusBadge tone="info">예정 포함</StatusBadge>{" "}
                {data.completeness.estimated.join(" · ")}
              </div>
            )}
            {data.notices.map((notice, index) => (
              <p
                className="operating-muted"
                key={index}
                data-testid={`operating-notice-${index}`}
              >
                {notice}
              </p>
            ))}
            {data.completeness.availableMonthCount === 0 && (
              <div className="operating-empty">
                조회 기간의 손익 자료가 없습니다. 미확인 금액은 0으로 보정하지
                않습니다.
                <FinancialEmptyReset screenId="UI-08" />
              </div>
            )}
            <div className="operating-summary">
              {data.summary.map((card) => (
                <article
                  className="operating-card"
                  key={card.metric}
                  data-testid={`operating-card-${kebab(card.metric)}`}
                >
                  <h2>{labels[card.metric] ?? card.metric}</h2>
                  <strong data-financial-negative={card.amount?.startsWith("-")}>
                    {formatAmount(card.amount, unit)} <small>{unit}</small>
                  </strong>
                  {card.metric === "oprtPrftAmt" && (
                    <p>영업이익률 {formatRate(card.rate)}</p>
                  )}
                  <Change comparison={card.previousYear} label="전년 동기 " />
                  <Change comparison={card.previous} label="전월 " />
                </article>
              ))}
            </div>
            <FinancialStatementTable
              months={data.months}
              rows={data.rows}
              month={data.context.month}
              unit={unit}
              showPreviousYear={data.showPreviousYear}
              provisional={data.cumulativeProvisional}
              name="영업이익표"
              testPrefix="operating"
            />
            <p className="operating-muted">
              TK는 청구일 기준 공급가액, MO/DP는 투입 귀속월 매출입니다. 전월
              조정은 원래 마감 사본을 변경하지 않습니다.
            </p>
            <div className="operating-banner info">
              <span>
                전월 조정은 재무 항목의 현재 편집 가능한 월에서 입력합니다.
              </span>
              <button
                type="button"
                data-testid="operating-adjustment"
                onClick={() =>
                  navigate("/UI-10", { state: { section: "adjustments" } })
                }
              >
                전월 조정 입력
              </button>
            </div>
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
