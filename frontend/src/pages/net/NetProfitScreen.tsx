import { FinancialOverview, getFinancialWarnings } from "../../shared/FinancialOverview";
import { FinancialWarnings } from "../../shared/FinancialWarnings";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { KpiStrip } from "../../shared/Surface";
import { FinancialEmptyReset } from "../shared/FinancialEmptyReset";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import {
  compareDecimal,
  formatAmount,
  formatRate,
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
const noPositiveContributionNotice = "흑자인 유형이 없어 비율과 막대를 표시하지 않습니다.";
const netGuide = (
  <dl>
    <div>
      <dt>매출과 비교 기준</dt>
      <dd>
        턴키는 청구한 달의 공급가액, 월단위·파견은 일한 달의 매출입니다.
        화면의 금액은 부가세를 제외합니다. 1월부터 기준월까지의 누계를 전년 같은
        기간과 비교합니다.
      </dd>
    </div>
    <div>
      <dt>법인세와 전월 조정</dt>
      <dd>
        전월 조정과 연간 예상 법인세는 재무 항목에서 입력합니다. 순이익표의
        법인세는 연간 예상액을 달마다 나눈 금액입니다. 실제 납부액은 자금수지표에
        표시합니다.
      </dd>
    </div>
    <div>
      <dt>비용과 자금수지</dt>
      <dd>
        내부(자체 개발) 비용은 판관비의 연구개발비로 처리합니다. 실제 법인세 납부와
        카드 결제·계좌이체·대여금의 자금수지는 순이익 계산에 추가로 더하지 않습니다.
      </dd>
    </div>
  </dl>
);
// SCR-UI-09
export function NetProfitScreen({
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
  const warnings = getFinancialWarnings(data?.notices ?? [], {
    excludeCompleteCoverage: true,
  }).filter((notice) => notice !== noPositiveContributionNotice);
  const cutoffMonth = data ? Number(data.context.month.slice(5)) : 0;
  return (
    <SharedScreenFrame
      screenId="UI-09"
      screenGuide={netGuide}
      periodBar={
        <ScreenPeriodBar
          screenId="UI-09"
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
      }
    >
      <div
        className={`operating-profit net-profit${dense ? " financial-display-dense" : ""}`}
        data-testid="net-profit-screen"
        data-screen-id="SCR-UI-09"
      >
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
                data-testid="net-tax-link"
                onClick={() =>
                  navigate("/UI-10", { state: { section: "tax" } })
                }
              >
                법인세 월할 확인
              </button>
              <button
                type="button"
                data-testid="net-cashflow-link"
                onClick={() => navigate("/UI-21")}
              >
                자금수지표 안내
              </button>
              {warnings.length > 0 && <FinancialWarnings warnings={warnings} />}
            </div>
            {data.completeness.availableMonthCount === 0 && (
              <div className="operating-empty">
                조회 기간의 손익 자료가 없습니다. 미확인 금액은 0으로 보정하지
                않습니다.
                <FinancialEmptyReset screenId="UI-09" />
              </div>
            )}
            <KpiStrip className="operating-summary net-summary">
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
                      ? "누계 당기순이익"
                      : "기준월 순이익"}
                  </h2>
                  <strong
                    data-financial-negative={card.amount?.startsWith("-")}
                  >
                    {formatAmount(card.amount, unit)} <small>{unit}</small>
                  </strong>
                  <p>순이익률 {formatRate(card.rate)}</p>
                  <FinancialStatementChange
                    comparison={card.previousYear}
                    label="전년 같은 기간 대비 "
                  />
                  <FinancialStatementChange
                    comparison={card.previous}
                    label="전월 대비 "
                  />
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
              name="순이익표"
              testPrefix="net"
            />
            <section
              className="net-contributions"
              data-testid="net-contributions"
            >
              <div className="net-contribution-heading">
                <div className="net-contribution-title">
                  <h2>계약 유형별 기여도</h2>
                  <HelpTooltip label="계약 유형별 기여도" iconOnly>
                    유형별 매출총이익은 매출에서 배분 원가를 뺀 금액입니다.
                    대기 원가와 직접 경비를 배분하지 않으므로 회사 매출총이익과
                    계산 기준이 다릅니다. 흑자 유형의 이익 합계를 100%로 보고
                    각 유형의 비중을 계산합니다.
                  </HelpTooltip>
                </div>
                <span className="operating-muted">기준월 <b>{data.contributionMonth}</b></span>
              </div>
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
                      {!group.loss && (
                        <span>
                          {formatRate(group.positiveContributionShare)}
                        </span>
                      )}
                      {!group.loss &&
                        group.showBar &&
                        group.positiveContributionShare != null && (
                          <div
                            className="net-contribution-track"
                            aria-label={`${group.groupName} 기여도 ${group.positiveContributionShare}%`}
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
                  {data.contributions.contractTypes.some(
                    (group) => group.loss,
                  ) && (
                    <p role="note" data-testid="net-contribution-loss-note">
                      적자 유형은 비율에서 빼고 금액만 표시합니다.
                    </p>
                  )}
                  {!data.contributions.contractTypes.some(
                    (group) =>
                      group.contributionAmt != null &&
                      compareDecimal(group.contributionAmt, "0") > 0,
                  ) && (
                    <p role="note">
                      {noPositiveContributionNotice}
                    </p>
                  )}
                </>
              )}
            </section>
          </>
        )}
      </div>
    </SharedScreenFrame>
  );
}
