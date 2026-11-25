import { ScreenLoading } from "../../shared/ScreenLoading";
import { FinancialOverview, getFinancialWarnings } from "../../shared/FinancialOverview";
import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { KpiStrip } from "../../shared/Surface";
import { CalculationMethods } from "../../shared/CalculationMethods";
import { FinancialEmptyReset } from "../shared/FinancialEmptyReset";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { FinancialWarnings } from "../../shared/FinancialWarnings";
import { HelpTooltip } from "../../shared/HelpTooltip";
import {
  formatAmount,
  formatDateTime,
  formatRate,
  type AmountUnit,
} from "../../shared/format";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import {
  StructureTrend,
  StructureWaterfall,
  bucketLabel,
} from "./ProfitStructureCharts";
import * as api from "./profitStructureApi";
import "./profitStructure.css";
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
  "sicost:business-plans-committed",
  "sicost:business-plan-import-committed",
  "sicost:historical-profit-loss-import-committed",
  "sicost:project-expenses-committed",
  "sicost:distribution-committed",
];
const cardLabels: Record<string, string> = {
  salesAmt: "매출",
  grossPrftAmt: "매출총이익",
  oprtPrftAmt: "영업이익",
  idleCostAmt: "대기 원가 비중",
};
const tabs: [api.Aggregation, string][] = [
  ["MONTH", "월"],
  ["QUARTER", "분기"],
  ["YTD", "연 누계"],
];
function monthRange(months: string[]) {
  if (!months.length) return "조회 기간";
  const first = months[0],
    last = months[months.length - 1];
  const label = (month: string) =>
    `${month.slice(0, 4)}년 ${Number(month.slice(5))}월`;
  if (first === last) return label(first);
  return first.slice(0, 4) === last.slice(0, 4)
    ? `${first.slice(0, 4)}년 ${Number(first.slice(5))}~${Number(last.slice(5))}월`
    : `${label(first)}~${label(last)}`;
}
const structureGuide = (
  <dl>
    <div>
      <dt>집계와 비교 기준</dt>
      <dd>
        <ul>
          <li>월은 선택한 기준월의 실적을 전년 같은 달과 비교합니다.</li>
          <li>분기는 해당 분기 시작월부터 기준월까지의 실적을 전년의 같은 월 범위와 비교합니다.</li>
          <li>연 누계는 1월부터 기준월까지의 실적을 전년의 같은 누계 기간과 비교합니다.</li>
        </ul>
        <p>전년 자료가 있는 경우 요약 카드에서 전년 대비 증감을 확인할 수 있습니다.</p>
      </dd>
    </div>
    <div>
      <dt>금액 기준</dt>
      <dd>화면에 표시되는 금액은 부가세를 제외한 금액입니다.</dd>
    </div>
    <div>
      <dt>매출 집계 기준</dt>
      <dd>
        턴키는 청구한 달의 공급가액을, 월단위·파견은 실제로 일한 달의 매출을
        집계합니다. 같은 기간으로 비교하면 순이익표의 매출액에도 같은 기준이
        적용됩니다.
      </dd>
    </div>
  </dl>
);
function ReferenceRevenueHelp({ chartType }: { chartType?: "line" | "bar" }) {
  return (
    <HelpTooltip label="배분 기준 매출" iconOnly>
      {chartType === "line" && "차트에서 ‘배분 기준 매출(참고)’ 점선으로 표시합니다. "}
      {chartType === "bar" && "차트에서 ‘배분 기준 매출(참고)’ 막대로 표시합니다. "}
      월단위·파견 매출에 작업한 달로 나눈 턴키 매출을 합산한 참고값입니다.
      턴키는 배분표의 월별 금액을 쓰고, 배분표가 없으면 해당 월 원가에 계약금액을
      곱한 뒤 예산으로 나눕니다. 회사 매출이나 이익 합계에는 포함하지 않습니다.
    </HelpTooltip>
  );
}
const compositionHelp = (
  <HelpTooltip label="매출 구성 기준">
    기여이익은 해당 계약 유형·고객사·영업 파트의 매출에서 프로젝트에 배분된
    인건비를 뺀 금액입니다. 직접 경비·대기 원가·판관비를 차감하기 전이므로
    회사 전체의 매출총이익·영업이익과 계산 기준이 다릅니다.
    기여이익률은 매출에서 기여이익이 차지하는 비율이며, 아래 ‘숫자로 보기’의
    매출 구성 표에도 같은 기준을 적용합니다.
  </HelpTooltip>
);
const kebab = (value: string) =>
  value.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);
function Change({
  comparison,
  label,
}: {
  comparison: api.Comparison | null;
  label: string;
}) {
  if (!comparison) return null;
  return (
    <small
      className={`profit-structure-change ${comparison.direction.toLowerCase()}`}
      title={`${comparison.comparisonMonths.join(", ")} · 빠진 월: ${comparison.missingMonths.join(", ") || "없음"}`}
    >
      {label}{" "}
      {comparison.changeRate == null
        ? comparison.percentagePointChange == null
          ? "–"
          : `${comparison.percentagePointChange}%p`
        : `${comparison.changeRate}%`}
      {comparison.availableMonthCount < comparison.requestedMonthCount &&
        ` · 자료 ${comparison.availableMonthCount}/${comparison.requestedMonthCount}개월`}
    </small>
  );
}
function RevenueComposition({
  title,
  groups,
  unit,
  id,
}: {
  title: string;
  groups: api.RevenueGroup[];
  unit: AmountUnit;
  id: string;
}) {
  const maximum = Math.max(
    1,
    ...groups.map((g) => Math.abs(Number(g.salesAmt ?? "0"))),
  );
  return (
    <section
      className="profit-structure-card"
      data-testid={`structure-${id}-composition`}
    >
      <div className="profit-structure-section-title">
        <h2>{title}</h2>
        {id === "part" && <small>프로젝트를 따온 파트 기준</small>}
      </div>
      {groups.length === 0 ? (
        <p className="profit-structure-empty">표시할 구성 자료가 없습니다.</p>
      ) : (
        <div>
          {groups.map((group, index) => (
            <article
              className="profit-structure-group"
              key={group.groupId ?? "unassigned"}
              data-testid={`structure-${id}-group-${index}`}
            >
              <div>
                <strong>{group.groupName}</strong>
                <span>
                  {formatAmount(group.salesAmt, unit)} {unit} ·{" "}
                  {formatRate(group.revenueShare)}
                </span>
              </div>
              {group.showBar && group.salesAmt != null && (
                <svg
                  viewBox="0 0 300 12"
                  role="img"
                  aria-label={`${group.groupName} 매출 비중`}
                >
                  <rect
                    x={
                      Number(group.salesAmt) < 0
                        ? 150 -
                          (Math.abs(Number(group.salesAmt)) / maximum) * 140
                        : 150
                    }
                    y="0"
                    height="12"
                    width={(Math.abs(Number(group.salesAmt)) / maximum) * 140}
                    fill={
                      Number(group.salesAmt) < 0
                        ? "var(--bad)"
                        : "var(--primary)"
                    }
                  />
                </svg>
              )}
              <small className={group.loss ? "adverse" : ""}>
                기여이익 {formatAmount(group.contributionAmt, unit)} ·
                기여이익률 {formatRate(group.contributionRate)}
              </small>
            </article>
          ))}
        </div>
      )}
      {id === "part" && (
        <p role="note" className="profit-structure-muted">
          영업 파트 이익금은 사람 소속 파트 기준이라 이 숫자와 다를 수 있습니다
        </p>
      )}
    </section>
  );
}
// SCR-UI-07 — same materialized buckets drive cards, charts, compositions and numeric tables.
export function ProfitStructureScreen({
  amountUnit,
  onAmountUnitChange,
  dense,
}: {
  amountUnit?: AmountUnit;
  dense?: boolean;
  onAmountUnitChange?(unit: AmountUnit): void;
} = {}) {
  const navigate = useNavigate();
  function setUnit(next: AmountUnit) {
    if (onAmountUnitChange) onAmountUnitChange(next);
    else setLocalUnit(next);
  }
  const [period, setPeriod] = useState<{ year: number; month: string }>();
  const [aggregation, setAggregation] = useState<api.Aggregation>("MONTH"),
    [localUnit, setLocalUnit] = useState<AmountUnit>("천원");
  const unit = amountUnit ?? localUnit;
  const [data, setData] = useState<api.StructureData>(),
    [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true),
    [revision, setRevision] = useState(0),
    [download, setDownload] = useState(false);
  const [mobile, setMobile] = useState(() => window.innerWidth < 768);
  const sequence = useRef(0),
    committed = useRef(false);
  useEffect(() => {
    const changed = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          value: { type: string; year: string; month: string };
        }>
      ).detail;
      if (detail?.screenId === "UI-07" && detail.value.type === "YEAR_CUM")
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
    events.forEach((event) => window.addEventListener(event, refresh));
    return () => {
      sequence.current++;
      window.removeEventListener("sicost:screen-period-changed", changed);
      window.removeEventListener("resize", resize);
      events.forEach((event) => window.removeEventListener(event, refresh));
    };
  }, []);
  useEffect(() => {
    if (!period) return;
    const controller = new AbortController(),
      ticket = ++sequence.current;
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void api
      .getProfitLossStructure(
        period.year,
        period.month,
        aggregation,
        controller.signal,
      )
      .then((result) => {
        if (ticket === sequence.current && !controller.signal.aborted) {
          setData(result);
          committed.current = false;
        }
      })
      .catch((failure) => {
        if (ticket === sequence.current && !controller.signal.aborted)
          setError(failure);
      })
      .finally(() => {
        if (ticket === sequence.current && !controller.signal.aborted)
          setLoading(false);
      });
    return () => controller.abort();
  }, [period, aggregation, revision]);
  const last = data?.buckets.at(-1),
    quarterly = aggregation === "QUARTER";
  const warnings = data ? getFinancialWarnings(data.notices) : [];
  function go(path: string) {
    if (period)
      navigate(path, {
        state: {
          month: period.month,
          year: period.year,
          period: {
            type: "YEAR_CUM",
            year: String(period.year),
            month: period.month,
          },
        },
      });
  }
  async function exportFile() {
    if (!period || download) return;
    setDownload(true);
    setError(undefined);
    try {
      await api.downloadProfitLossStructure(
        period.year,
        period.month,
        aggregation,
        unit === "원"
          ? "WON"
          : unit === "천원"
            ? "THOUSAND_WON"
            : "MILLION_WON",
      );
    } catch (failure) {
      setError(failure);
    } finally {
      setDownload(false);
    }
  }
  return (
    <SharedScreenFrame
      screenId="UI-07"
      screenGuide={structureGuide}
      periodBarOptions={{ showMonthCloseStatus: false, showCumulativeRange: false }}
      headerActions={
        <div className="sicost-title-action-group profit-structure-actions">
          <label>
            단위{" "}
            <select
              data-testid="structure-unit"
              value={unit}
              onChange={(event) => setUnit(event.target.value as AmountUnit)}
            >
              <option>원</option>
              <option>천원</option>
              <option>백만원</option>
            </select>
          </label>
          <button
            className="sicost-download-button"
            data-testid="structure-export"
            type="button"
            disabled={!data || loading || download || mobile}
            onClick={() => void exportFile()}
          >
            엑셀 다운로드
          </button>
        </div>
      }
    >
      <div
        className={`profit-structure${dense ? " financial-display-dense" : ""}`}
        data-testid="profit-structure-screen"
        data-screen-id="SCR-UI-07"
      >
        <div className="profit-structure-toolbar">
          <SegmentedTabList as="div" role="tablist" aria-label="기간 단위">
            {tabs.map(([value, label]) => (
              <button
                key={value}
                type="button"
                role="tab"
                aria-selected={aggregation === value}
                data-testid={`structure-tab-${value.toLowerCase()}`}
                onClick={() => setAggregation(value)}
              >
                {label}
              </button>
            ))}
          </SegmentedTabList>
          {warnings.length > 0 && <FinancialWarnings warnings={warnings} />}
        </div>
        {mobile && <p role="note">엑셀 다운로드는 PC에서 이용해 주세요.</p>}
        {error != null && (
          <ErrorBanner
            message={`${committed.current ? "저장은 완료됐지만 목록을 다시 불러오지 못했습니다. " : ""}${error instanceof Error ? error.message : "수익 구조 조회 실패"}`}
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => setRevision((n) => n + 1)}
          />
        )}
        {loading && <ScreenLoading message="전체 수익 구조를 불러오는 중입니다." />}
        {data && (
          <div
            role="tabpanel"
            data-testid="structure-panel"
            aria-label={tabs.find(([v]) => v === aggregation)?.[1]}
          >
            <FinancialOverview
              context={data.context}
              months={data.months}
              provisional={
                last?.provisional ?? data.months.some((m) => m.provisional)
              }
              caption={null}
              periodDetail={
                last && data.context.aggregation !== "MONTH" && last.months.length > 0 && (
                  <span>집계 기간 <b>{monthRange(last.months)}</b></span>
                )
              }
            />
            {!data.months.some(
              (m) => m.sourceKind !== "MISSING" && m.sourceKind !== "FUTURE",
            ) && (
              <p className="profit-structure-empty">
                조회 기간의 손익 자료가 없습니다. 미확인 금액은 0으로 보정하지
                않습니다.
                <FinancialEmptyReset screenId="UI-07" />
              </p>
            )}
            <KpiStrip className="profit-structure-summary">
              {data.summary.map((card) => (
                <article
                  className="profit-structure-card"
                  key={card.metric}
                  data-testid={`structure-card-${kebab(card.metric)}`}
                >
                  <h2>{cardLabels[card.metric] ?? card.metric}</h2>
                  {card.metric === "oprtPrftAmt" ? (
                    <button
                      className="profit-structure-value-link"
                      data-financial-negative={card.amount?.startsWith("-")}
                      type="button"
                      data-testid="structure-operating-card"
                      onClick={() => go("/UI-08")}
                    >
                      {formatAmount(card.amount, unit)} {unit}
                    </button>
                  ) : (
                    <strong
                      data-financial-negative={card.amount?.startsWith("-")}
                    >
                      {card.metric === "idleCostAmt"
                        ? formatRate(card.rate)
                        : `${formatAmount(card.amount, unit)} ${unit}`}
                    </strong>
                  )}
                  <p>
                    {card.metric === "idleCostAmt"
                      ? `대기 원가 ${formatAmount(card.amount, unit)} ${unit} ÷ 매출원가`
                      : card.rate != null
                        ? `${card.metric === "grossPrftAmt" ? "매출총이익률" : "영업이익률"} ${formatRate(card.rate)}`
                        : "부가세 제외"}
                  </p>
                  <Change
                    comparison={card.previous}
                    label={
                      quarterly
                        ? last && last.monthCount < 3
                          ? "전 분기 같은 기간 대비"
                          : "전 분기 대비"
                        : "전월 대비"
                    }
                  />
                  <Change
                    comparison={card.previousYear}
                    label="전년 같은 기간 대비"
                  />
                </article>
              ))}
            </KpiStrip>
            <div className="profit-structure-charts">
              <StructureWaterfall steps={data.waterfall} unit={unit} />
              <StructureTrend
                title="매출·매출원가 추이"
                titleHelp={<ReferenceRevenueHelp chartType={quarterly ? "bar" : "line"} />}
                buckets={data.buckets}
                metrics={["salesAmt", "salesCostAmt", "referenceRevenue"]}
                labels={["매출", "매출원가", "배분 기준 매출(참고)"]}
                quarterly={quarterly}
                unit={unit}
              />
              <StructureTrend
                title="매출총이익·영업이익 추이"
                buckets={data.buckets}
                metrics={["grossPrftAmt", "oprtPrftAmt"]}
                labels={["매출총이익", "영업이익"]}
                quarterly={quarterly}
                unit={unit}
              />
              <StructureTrend
                title="하위원가 구성"
                buckets={data.buckets}
                metrics={["allocCostAmt", "idleCostAmt", "drctExpsAmt"]}
                labels={["배분 원가", "대기 원가", "직접 경비"]}
                quarterly={quarterly}
                stacked
                unit={unit}
              />
            </div>
            {last?.composition.visible ? (
              <div className="profit-structure-compositions">
                <div className="profit-structure-composition-help">{compositionHelp}</div>
                <RevenueComposition
                  title="계약 유형별 매출 구성"
                  id="type"
                  groups={last.composition.contractTypes}
                  unit={unit}
                />
                <RevenueComposition
                  title="고객사 상위 5곳"
                  id="customer"
                  groups={last.composition.customers}
                  unit={unit}
                />
                <RevenueComposition
                  title="영업 파트별 매출 구성"
                  id="part"
                  groups={last.composition.salesParts}
                  unit={unit}
                />
              </div>
            ) : (
              last && (
                <p className="profit-structure-banner">
                  {last.composition.reason}
                </p>
              )
            )}
            {last?.composition.visible && !last.composition.complete && (
              <p className="profit-structure-banner">
                {last.composition.reason} 매출 차이{" "}
                {formatAmount(last.composition.revenueDifference, unit)} {unit}
              </p>
            )}
            <section
              className="profit-structure-card"
              data-testid="structure-numeric-panel"
            >
              <div className="profit-structure-section-title">
                <h2>숫자로 보기</h2>
                <small>
                  {aggregation === "MONTH"
                    ? "월별"
                    : quarterly
                      ? "분기별"
                      : "월별 누계"}
                  {" · 단위 "}
                  {unit}
                </small>
                {!last?.composition.visible &&
                  data.buckets.some((bucket) => bucket.composition.visible) &&
                  compositionHelp}
              </div>
              <div className="profit-structure-table-scroll">
                <table aria-label="기간별 손익 숫자표">
                  <thead>
                    <tr>
                      <th>항목 ({unit})</th>
                      {data.buckets.map((b, i) => (
                        <th
                          key={b.key}
                          className={
                            i === data.buckets.length - 1 ? "latest" : ""
                          }
                        >
                          {bucketLabel(b, quarterly)}
                          <br />
                          자료 {b.completeness.availableMonthCount}/
                          {b.monthCount}개월
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {data.numericRows.map((row) => (
                      <tr
                        key={row.metric}
                        className={
                          [
                            "salesAmt",
                            "grossPrftAmt",
                            "oprtPrftAmt",
                            "netPrftAmt",
                          ].includes(row.metric)
                            ? "profit-structure-subtotal"
                            : [
                                  "allocCostAmt",
                                  "idleCostAmt",
                                  "drctExpsAmt",
                                ].includes(row.metric)
                              ? "profit-structure-child-row"
                              : undefined
                        }
                        data-testid={`structure-numeric-${kebab(row.metric)}`}
                      >
                        <th scope="row">
                          {[
                            "allocCostAmt",
                            "idleCostAmt",
                            "drctExpsAmt",
                          ].includes(row.metric)
                            ? "└ "
                            : ""}
                          {row.label}
                          {row.metric === "referenceRevenue" && (
                            <ReferenceRevenueHelp />
                          )}
                        </th>
                        {row.bucketValues.map((value, i) => (
                          <td
                            key={row.bucketKeys[i]}
                            data-financial-negative={value?.startsWith("-")}
                            className={
                              i === row.bucketValues.length - 1 ? "latest" : ""
                            }
                          >
                            {row.isRate
                              ? formatRate(value)
                              : formatAmount(value, unit)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {(["contractTypes", "customers", "salesParts"] as const).map(
                (kind) => (
                  <CompositionNumbers
                    key={kind}
                    kind={kind}
                    data={data}
                    unit={unit}
                  />
                ),
              )}
              <p className="profit-structure-muted">
                엑셀 다운로드는 이 표와 같습니다. 자세한 항목은
                영업이익표·순이익표에서 봅니다. 같은 계산이라 숫자가 같습니다.
              </p>
            </section>
            <CalculationMethods
              testId="structure-method-panel"
              rowTestIdPrefix="structure-method"
              rows={data.calculationMethods.map((method) => ({
                metric: method.metric,
                formula: method.formula,
                reference: method.sources.join(" "),
              }))}
            >
              <p className="profit-structure-muted">
                금액은 모두 부가세 제외입니다.
              </p>
            </CalculationMethods>
            <div className="profit-structure-toolbar">
              <button
                data-testid="structure-operating-link"
                type="button"
                onClick={() => go("/UI-08")}
              >
                영업이익표 보기
              </button>
              <button
                data-testid="structure-net-link"
                type="button"
                onClick={() => go("/UI-09")}
              >
                순이익표 보기
              </button>
            </div>
          </div>
        )}
      </div>
    </SharedScreenFrame>
  );
}
function CompositionNumbers({
  kind,
  data,
  unit,
}: {
  kind: "contractTypes" | "customers" | "salesParts";
  data: api.StructureData;
  unit: AmountUnit;
}) {
  const identities = new Map<string, api.RevenueGroup>();
  data.buckets.forEach((b) => {
    if (b.composition.visible)
      b.composition[kind].forEach((g) =>
        identities.set(g.groupId ?? "unassigned", g),
      );
  });
  return (
    <div className="profit-structure-table-scroll">
      <table
        aria-label={`${kind === "contractTypes" ? "유형" : kind === "customers" ? "고객사" : "영업 파트"} 구성 숫자표`}
      >
        <thead>
          <tr>
            <th>구성 / 지표</th>
            {data.buckets.map((b) => (
              <th key={b.key}>{b.key}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {[...identities].flatMap(([id, group], index) =>
            (["salesAmt", "revenueShare", "contributionRate"] as const).map(
              (metric) => (
                <tr
                  key={`${id}-${metric}`}
                  data-testid={`structure-composition-${kind.toLowerCase()}-${index}-${kebab(metric)}`}
                >
                  <th>
                    {group.groupName} ·{" "}
                    {metric === "salesAmt"
                      ? "매출"
                      : metric === "revenueShare"
                        ? "비중"
                        : "기여이익률"}
                  </th>
                  {data.buckets.map((b, i) => {
                    const selected = b.composition.visible
                      ? b.composition[kind].find(
                          (g) => (g.groupId ?? "unassigned") === id,
                        )
                      : undefined;
                    return (
                      <td
                        key={b.key}
                        className={
                          i === data.buckets.length - 1 ? "latest" : ""
                        }
                      >
                        {metric === "salesAmt"
                          ? formatAmount(selected?.[metric], unit)
                          : formatRate(selected?.[metric])}
                      </td>
                    );
                  })}
                </tr>
              ),
            ),
          )}
        </tbody>
      </table>
    </div>
  );
}
