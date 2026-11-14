import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
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
    <section className="profit-structure-card">
      <h2>{title}</h2>
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
    </section>
  );
}
// SCR-UI-07 — same materialized buckets drive cards, charts, compositions and numeric tables.
export function ProfitStructureScreen() {
  const navigate = useNavigate();
  const [period, setPeriod] = useState<{ year: number; month: string }>();
  const [aggregation, setAggregation] = useState<api.Aggregation>("MONTH"),
    [unit, setUnit] = useState<AmountUnit>("백만원");
  const [data, setData] = useState<api.StructureData>(),
    [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true),
    [revision, setRevision] = useState(0),
    [download, setDownload] = useState(false);
  const [numbers, setNumbers] = useState(false),
    [methods, setMethods] = useState(false),
    [mobile, setMobile] = useState(() => window.innerWidth < 768);
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
    <SharedScreenFrame screenId="UI-07">
      <div
        className="profit-structure"
        data-testid="profit-structure-screen"
        data-screen-id="SCR-UI-07"
      >
        <div className="profit-structure-toolbar">
          <div role="tablist" aria-label="기간 단위">
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
          </div>
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
            data-testid="structure-numbers"
            type="button"
            aria-expanded={numbers}
            onClick={() => setNumbers(!numbers)}
          >
            숫자로 보기
          </button>
          <button
            data-testid="structure-methods"
            type="button"
            aria-expanded={methods}
            onClick={() => setMethods(!methods)}
          >
            계산 방법
          </button>
          <button
            data-testid="structure-export"
            type="button"
            disabled={!data || loading || download || mobile}
            onClick={() => void exportFile()}
          >
            엑셀 다운로드
          </button>
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
        {loading && <p role="status">전체 수익 구조를 불러오는 중입니다.</p>}
        {data && (
          <div
            role="tabpanel"
            data-testid="structure-panel"
            aria-label={tabs.find(([v]) => v === aggregation)?.[1]}
          >
            <p className="profit-structure-muted">
              {data.context.month} 기준 · 회사 전체 · 읽기 전용 · 조회{" "}
              {formatDateTime(data.context.snapshotAt)}
            </p>
            {data.context.year < Number(data.context.nowMonth.slice(0, 4)) && (
              <StatusBadge tone="lock">지난 해·읽기 전용</StatusBadge>
            )}
            {last && (
              <p>
                {bucketLabel(last, quarterly)}{" "}
                <StatusBadge tone={last.provisional ? "warn" : "lock"}>
                  {last.provisional ? "잠정 · 작업중" : "확정/이관"}
                </StatusBadge>
                {last.completeness.estimated.length > 0 && (
                  <StatusBadge tone="info">예정 포함</StatusBadge>
                )}
              </p>
            )}
            {data.notices.map((notice, index) => (
              <p
                className="profit-structure-banner"
                role="note"
                key={index}
                data-testid={`structure-notice-${index}`}
              >
                {notice}
              </p>
            ))}
            {!data.months.some(
              (m) => m.sourceKind !== "MISSING" && m.sourceKind !== "FUTURE",
            ) && (
              <p className="profit-structure-empty">
                조회 기간의 손익 자료가 없습니다. 미확인 금액은 0으로 보정하지
                않습니다.
              </p>
            )}
            <div className="profit-structure-summary">
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
                      type="button"
                      data-testid="structure-operating-card"
                      onClick={() => go("/UI-08")}
                    >
                      {formatAmount(card.amount, unit)} {unit}
                    </button>
                  ) : (
                    <strong>
                      {card.metric === "idleCostAmt"
                        ? formatRate(card.rate)
                        : `${formatAmount(card.amount, unit)} ${unit}`}
                    </strong>
                  )}
                  <p>
                    {card.metric === "idleCostAmt"
                      ? `대기 원가 ${formatAmount(card.amount, unit)} ${unit}`
                      : card.rate != null
                        ? `이익률 ${formatRate(card.rate)}`
                        : "부가세 제외"}
                  </p>
                  <Change
                    comparison={card.previous}
                    label={quarterly ? "직전 분기" : "전월"}
                  />
                  <Change comparison={card.previousYear} label="전년 동기" />
                </article>
              ))}
            </div>
            <div className="profit-structure-charts">
              <StructureWaterfall steps={data.waterfall} unit={unit} />
              <StructureTrend
                title="매출·매출원가 추이"
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
                <RevenueComposition
                  title="계약 유형별 매출 구성"
                  id="type"
                  groups={last.composition.contractTypes}
                  unit={unit}
                />
                <RevenueComposition
                  title="고객사 상위 5 / 그 밖"
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
            <p role="note">
              기여이익은 매출−배분 노동비로 회사 매출총이익·영업이익과 다른
              지표입니다.
            </p>
            <p role="note">
              영업 파트 이익금은 사람 소속 파트 기준이라 이 숫자와 다를 수
              있습니다
            </p>
            {numbers && (
              <section
                className="profit-structure-card"
                data-testid="structure-numeric-panel"
              >
                <h2>숫자로 보기</h2>
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
                          data-testid={`structure-numeric-${kebab(row.metric)}`}
                        >
                          <th>{row.label}</th>
                          {row.bucketValues.map((value, i) => (
                            <td
                              key={row.bucketKeys[i]}
                              className={
                                i === row.bucketValues.length - 1
                                  ? "latest"
                                  : ""
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
              </section>
            )}
            {methods && (
              <section
                className="profit-structure-card"
                data-testid="structure-method-panel"
              >
                <h2>계산 방법</h2>
                <table aria-label="계산 방법">
                  <thead>
                    <tr>
                      <th>항목</th>
                      <th>원천</th>
                      <th>계산식</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.calculationMethods.map((method, i) => (
                      <tr
                        key={method.metric}
                        data-testid={`structure-method-${i}`}
                      >
                        <th>{method.metric}</th>
                        <td>{method.sources.join(", ")}</td>
                        <td>
                          {method.formula} · VAT 제외{" "}
                          {method.referenceOnly && "· 참고액"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
            )}
            <p className="profit-structure-muted">
              TK는 청구일 기준 공급가액, MO/DP는 투입 귀속월 매출입니다. 점선은
              참고 매출이며 회사 실제 매출·이익에 더하지 않습니다. 09의 누적
              진행률 매출과 다를 수 있습니다.
            </p>
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
