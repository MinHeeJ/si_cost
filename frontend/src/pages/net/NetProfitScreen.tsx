import { FinancialEmptyReset } from "../shared/FinancialEmptyReset";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import {
  compareDecimal,
  formatAmount,
  formatRate,
  formatDateTime,
  type AmountUnit,
} from "../../shared/format";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { ScreenPeriodBar } from "../shared/ScreenPeriodBar";
import {
  FinancialStatementTable,
  FinancialStatementChange,
} from "../shared/FinancialStatementTable";
import * as api from "./netProfitApi";
import "../operating/operatingProfit.css";
import "./netProfit.css";

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
  "sicost:historical-profit-loss-import-committed",
  "sicost:business-plans-committed",
  "sicost:business-plan-import-committed",
  "sicost:project-expenses-committed",
  "sicost:distribution-committed",
];
// SCR-UI-09
export function NetProfitScreen({ amountUnit, onAmountUnitChange, dense }: {
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
    [data, setData] = useState<api.NetProfitData>();
  const unit = amountUnit ?? localUnit;
  const [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true),
    [revision, setRevision] = useState(0);
  const [pending, setPending] = useState(false),
    [mobile, setMobile] = useState(() => window.innerWidth < 768);
  const committed = useRef(false),
    downloading = useRef(false);
  useEffect(() => {
    const changed = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          value: { type: string; year: string; month: string };
        }>
      ).detail;
      if (detail?.screenId === "UI-09" && detail.value.type === "YEAR_CUM")
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
      .getNetProfitLoss(period.year, period.month, controller.signal)
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
    if (downloading.current || !period || mobile) return;
    downloading.current = true;
    setPending(true);
    setError(undefined);
    try {
      await api.downloadNetProfitLoss(
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
      downloading.current = false;
      setPending(false);
    }
  }
  return (
    <SharedScreenFrame
      screenId="UI-09"
      periodBar={
        <ScreenPeriodBar screenId="UI-09" initialMonth={initialMonth} />
      }
    >
      <div
        className={`operating-profit net-profit${dense ? " financial-display-dense" : ""}`}
        data-testid="net-profit-screen"
        data-screen-id="SCR-UI-09"
      >
        <div className="operating-toolbar">
          <label>
            단위{" "}
            <select
              data-testid="net-unit"
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
            data-testid="net-export"
            disabled={!data || loading || pending || mobile}
            onClick={() => void download()}
          >
            엑셀 다운로드
          </button>
        </div>
        {mobile && <p role="note">엑셀 다운로드는 PC에서 이용해 주세요.</p>}
        {loading && <p role="status">순이익표를 불러오는 중입니다.</p>}
        {error != null && (
          <ErrorBanner
            message={`${committed.current ? "저장은 완료됐지만 순이익표를 다시 불러오지 못했습니다. " : ""}${error instanceof Error ? error.message : "순이익표 조회 실패"}`}
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
                data-testid={`net-notice-${index}`}
              >
                {notice}
              </p>
            ))}
            {data.completeness.availableMonthCount === 0 && (
              <div className="operating-empty">
                조회 기간의 손익 자료가 없습니다. 미확인 금액은 0으로 보정하지
                않습니다.
                <FinancialEmptyReset screenId="UI-09" />
              </div>
            )}
            <div className="operating-summary net-summary">
              {data.summary.map((card) => (
                <article
                  className="operating-card"
                  key={card.metric}
                  data-testid={
                    card.metric === "netPrftAmt"
                      ? "net-cumulative-card"
                      : "net-month-card"
                  }
                >
                  <h2>
                    {card.metric === "netPrftAmt"
                      ? "누계 순이익"
                      : "기준월 순이익"}
                  </h2>
                  <strong data-financial-negative={card.amount?.startsWith("-")}>
                    {formatAmount(card.amount, unit)} <small>{unit}</small>
                  </strong>
                  <p>순이익률 {formatRate(card.rate)}</p>
                  <FinancialStatementChange
                    comparison={card.previousYear}
                    label="전년 동기 "
                  />
                  <FinancialStatementChange
                    comparison={card.previous}
                    label="전월 대비 "
                  />
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
              name="순이익표"
              testPrefix="net"
            />
            <section
              className="net-contributions"
              data-testid="net-contributions"
            >
              <h2>계약 유형별 기여도</h2>
              <p className="operating-muted">
                {data.contributionMonth} 기준월 · 매출 − 배분 노동비 · 양수
                기여이익끼리의 구성비이며 회사 순이익과 다릅니다.
              </p>
              {!data.contributions.visible ? (
                <p className="operating-empty">{data.contributions.reason}</p>
              ) : (
                <>
                  {data.contributions.reason && (
                    <p role="note">{data.contributions.reason}</p>
                  )}
                  {data.contributions.contractTypes.length === 0 && (
                    <p className="operating-empty">
                      기준월 계약 유형 자료가 없습니다.
                    </p>
                  )}
                  {data.contributions.contractTypes.map((group, index) => (
                    <div
                      className="net-contribution-row"
                      key={group.groupId ?? group.groupName}
                      data-testid={`net-contribution-${index}`}
                    >
                      <strong>{group.groupName}</strong>
                      <span className={group.loss ? "adverse" : ""}>
                        {group.loss && "적자 "}
                        {formatAmount(group.contributionAmt, unit)} {unit}
                      </span>
                      <span>{formatRate(group.positiveContributionShare)}</span>
                      {group.showBar &&
                        group.positiveContributionShare != null && (
                          <div
                            className="net-contribution-track"
                            aria-label={`${group.groupName} 양수 기여도 ${group.positiveContributionShare}%`}
                          >
                            <div
                              style={{
                                width: `${Number(group.positiveContributionShare)}%`,
                              }}
                            />
                          </div>
                        )}
                    </div>
                  ))}
                  {!data.contributions.contractTypes.some(
                    (group) =>
                      group.contributionAmt != null &&
                      compareDecimal(group.contributionAmt, "0") > 0,
                  ) && (
                    <p role="note">
                      양수 기여이익이 없어 비율과 막대를 표시하지 않습니다.
                    </p>
                  )}
                </>
              )}
            </section>
            <p className="operating-muted">
              TK는 청구일 기준 공급가액, MO/DP는 투입 귀속월 매출입니다. IN
              매출은 0이며 IN 비용은 연구개발비에 포함됩니다.
            </p>
            <div className="operating-banner info">
              <span>
                법인세는 연간 예상액의 월할이며 실제 세금 납부액과 다릅니다.
                전월 조정·법인세 입력은 재무 항목에서 확인합니다.
              </span>
              <button
                type="button"
                data-testid="net-tax-link"
                onClick={() => navigate("/UI-10", { state: { section: "tax" } })}
              >
                법인세 월할 확인
              </button>
            </div>
            <div className="operating-banner info">
              <span>
                실제 납부와 카드 결제·계좌이체·대여금의 자금수지는 손익에 추가
                가산하지 않습니다.
              </span>
              <button
                type="button"
                data-testid="net-cashflow-link"
                onClick={() => navigate("/UI-21")}
              >
                자금수지표 안내
              </button>
            </div>
          </>
        )}
      </div>
    </SharedScreenFrame>
  );
}
