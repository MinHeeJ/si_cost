import { FieldHelp } from "../../shared/FieldHelp";
import { KpiStrip } from "../../shared/Surface";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../shared/api";
import {
  formatIntegerAmount,
  formatFixedDecimal,
  type AmountUnit,
} from "../../shared/format";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { ChartHost } from "../../shared/charts/ChartHost";
import { seriesChartOptions } from "../../shared/charts/chartAdapters";
import { SalesForecastDownloadPanel } from "./SalesForecastDownloadPanel";
import * as api from "./salesForecastApi";
import "./salesForecast.css";

const unitNames: Record<api.ForecastAmountUnit, AmountUnit> = {
  WON: "원",
  THOUSAND: "천원",
  MILLION: "백만원",
};
export function SalesForecastScreen() {
  return (
    <SharedScreenFrame screenId="UI-29">
      <SalesForecastPanel />
    </SharedScreenFrame>
  );
}
export function SalesForecastPanel() {
  const [data, setData] = useState<api.SalesForecast>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [amountUnit, setAmountUnit] =
    useState<api.ForecastAmountUnit>("THOUSAND");
  useEffect(() => {
    let live = true;
    setLoading(true);
    setError(undefined);
    void api
      .getSalesOpportunityForecast()
      .then((value) => {
        if (live) setData(value);
      })
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [revision]);
  useEffect(() => {
    const refresh = () => setRevision((v) => v + 1);
    const events = [
      "sicost:sales-committed",
      "sicost:assignments-committed",
      "sicost:developers-committed",
      "sicost:projects-committed",
      "sicost:project-plans-committed",
      "sicost:operating-standards-committed",
      "sicost:codegroups-committed",
      "sicost:bills-committed",
      "sicost:payroll-committed",
    ];
    events.forEach((name) => window.addEventListener(name, refresh));
    return () =>
      events.forEach((name) => window.removeEventListener(name, refresh));
  }, []);
  const unit = unitNames[amountUnit];
  const money = (value: string | null | undefined) => formatIntegerAmount(value, unit);
  const people = (value: string | null | undefined) =>
    formatFixedDecimal(value, 1);
  const identities = data
    ? [
        ...new Map(
          data.revenueSources.map((r) => [`${r.sourceType}:${r.sourceId}`, r]),
        ).values(),
      ]
    : [];
  return (
    <section
      className="sales-forecast"
      data-testid="sales-forecast-screen"
      data-screen-id="SCR-UI-19-B140-EXPORT"
      aria-busy={loading}
    >
      <div className="sales-forecast-toolbar">
        <h2>
          매출·인력 예측 <span className="sales-forecast-badge">예측</span>
        </h2>
        <div className="sicost-field">
          <label htmlFor="sales-forecast-unit">금액 단위</label>
          <select
            id="sales-forecast-unit"
            data-testid="forecast-unit-select"
            value={amountUnit}
            onChange={(e) =>
              setAmountUnit(e.target.value as api.ForecastAmountUnit)
            }
          >
            <option value="WON">원</option>
            <option value="THOUSAND">천원</option>
            <option value="MILLION">백만원</option>
          </select>
        </div>
        <SalesForecastDownloadPanel amountUnit={amountUnit} />
        <button
          data-testid="forecast-refresh-button"
          disabled={loading}
          onClick={() => setRevision((v) => v + 1)}
        >
          재조회
        </button>
      </div>
      {loading && <p role="status">예측을 불러오는 중입니다.</p>}
      {error != null && (
        <div className="sales-forecast-error" role="alert">
          {error instanceof Error
            ? error.message
            : "예측을 불러오지 못했습니다."}
          {error instanceof ApiError && <p>요청 ID {error.body.requestId}</p>}
          <p>기존 결과는 이전 조회입니다. 재조회해 주세요.</p>
        </div>
      )}
      {data && (
        <>
          <p className="sales-forecast-muted">
            범위 {data.from} ~ {data.revenueMonths.at(-1)?.month} · 기준일{" "}
            {data.workforce.basisDate} · 기준월 {data.workforce.standardMonth}
          </p>
          <p className="sales-forecast-banner">
            다음 달부터 6개월 예측입니다. 실제 손익·청구·미수에 저장하지
            않습니다. 인력 필요에는 프로젝트 미충원이 포함됩니다.
          </p>
          {data.workforce.issues.length > 0 && (
            <div className="sales-forecast-warning" role="note">
              <strong>원천·기준 미확인 — 0과 구분합니다.</strong>
              {data.workforce.issues.map((issue, i) => (
                <p
                  key={`${issue.field}:${i}`}
                  data-testid={`forecast-issue-${i}`}
                >
                  {issue.field}: {issue.reason}
                </p>
              ))}
            </div>
          )}
          <KpiStrip className="sales-forecast-summary">
            <article data-testid="forecast-revenue-card">
              <p>6개월 예상매출</p>
              <strong>
                {money(data.summary.sixMonthRevenue)} <small>{unit}</small>
              </strong>
            </article>
            <article data-testid="forecast-opportunity-card">
              <p>그중 기회 매출</p>
              <strong>
                {money(data.summary.opportunityRevenue)} <small>{unit}</small>
              </strong>
              <p>
                모두 따면 {money(data.summary.allWonRevenue)} {unit}
              </p>
            </article>
            <article data-testid="forecast-people-card">
              <p>6개월 평균 필요 / 공급</p>
              <strong>
                {people(data.summary.averageDemand)} /{" "}
                {people(data.summary.averageSupply)} <small>명</small>
              </strong>
            </article>
            <article data-testid="forecast-hiring-card">
              <p>채용 검토 직무</p>
              <strong>
                {data.summary.hiringJobCount} <small>개</small>
              </strong>
            </article>
          </KpiStrip>
          <div className="sales-forecast-charts">
            <ForecastBars
              title="월별 예상매출"
              rows={data.revenueMonths.map((m) => ({
                month: m.month,
                first: m.projectRevenue,
                second: m.opportunityRevenue,
              }))}
              labels={["프로젝트 매출", "확률반영 기회 매출"]}
              stacked
              unit={unit}
            />
            <ForecastBars
              title="수요·공급"
              rows={data.workforce.totals.map((m) => ({
                month: m.month,
                first: m.supply,
                second: m.demand,
              }))}
              labels={["가용", "필요"]}
            />
          </div>
          <section className="sales-forecast-card">
            <h2>
              월별 매출 <small>단위: {unit}</small>
            </h2>
            {identities.length === 0 && (
              <div className="sales-forecast-empty">
                <p>예측 대상 프로젝트·기회가 없습니다.</p>
                <button type="button" onClick={() => setRevision((v) => v + 1)}>
                  조건 초기화
                </button>
              </div>
            )}
            <div className="sicost-table-viewport">
              <table className="sicost-table">
                <thead>
                  <tr>
                    <th scope="col">구분<FieldHelp label="구분" kind="B" /></th>
                    {data.revenueMonths.map((m) => (
                      <th scope="col" key={m.month}>
                        {m.month}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {identities.map((row) => (
                    <tr
                      key={`${row.sourceType}:${row.sourceId}`}
                      data-testid={`forecast-source-${row.sourceType.toLowerCase()}-${row.sourceId}`}
                    >
                      <th scope="row">
                        {row.sourceType === "PROJECT" ? "프로젝트" : "기회"} ·{" "}
                        {row.name}
                      </th>
                      {data.revenueMonths.map((m) => (
                        <td key={m.month} className="sicost-decimal">
                          {money(
                            data.revenueSources.find(
                              (r) =>
                                r.sourceType === row.sourceType &&
                                r.sourceId === row.sourceId &&
                                r.month === m.month,
                            )?.amount ?? "0",
                          )}
                        </td>
                      ))}
                    </tr>
                  ))}
                  {(
                    [
                      ["프로젝트 매출", "projectRevenue"],
                      ["기회 매출", "opportunityRevenue"],
                      ["합계", "totalRevenue"],
                      ["모두 따면", "allWonRevenue"],
                    ] as const
                  ).map(([label, key]) => (
                    <tr key={key} className="sales-forecast-total">
                      <th scope="row">{label}<FieldHelp label={label} kind="B" /></th>
                      {data.revenueMonths.map((m) => (
                        <td key={m.month} className="sicost-decimal">
                          {money(m[key])}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
          <section className="sales-forecast-card">
            <h2>
              월별 인력 수요·공급 <small>단위: 명</small>
            </h2>
            <div className="sicost-table-viewport">
              <table className="sicost-table">
                <thead>
                  <tr>
                    <th>구분<FieldHelp label="구분" kind="B" /></th>
                    {data.workforce.totals.map((m) => (
                      <th key={m.month}>{m.month}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {(
                    [
                      ["필요", "demand"],
                      ["그중 미충원", "unfilledDemand"],
                      ["가용", "supply"],
                      ["차이", "difference"],
                    ] as const
                  ).map(([label, key]) => (
                    <tr key={key}>
                      <th scope="row">{label}<FieldHelp label={label} kind="B" /></th>
                      {data.workforce.totals.map((m) => (
                        <td
                          key={m.month}
                          className={
                            key === "difference"
                              ? `sales-forecast-${m.status.toLowerCase()}`
                              : "sicost-decimal"
                          }
                        >
                          {people(m[key])}
                          {key === "difference" && m.status === "UNKNOWN"
                            ? " · 미확인"
                            : ""}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
          <section className="sales-forecast-card">
            <h2>채용·외부인력 검토</h2>
            {data.workforce.hiring.length === 0 ? (
              <div className="sales-forecast-empty">
                <p>
                  {data.workforce.issues.length
                    ? "미확인 기준과 원천을 확인해 주세요."
                    : "채용 검토가 필요한 직무가 없습니다."}
                </p>
                <button type="button" onClick={() => setRevision((v) => v + 1)}>
                  조건 초기화
                </button>
              </div>
            ) : (
              <div className="sicost-table-viewport">
                <table className="sicost-table">
                  <thead>
                    <tr>
                      <th>직무<FieldHelp label="직무" kind="B" /></th>
                      <th>첫부족월</th>
                      <th>최대부족</th>
                      <th>권장시작</th>
                      <th>후보</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.workforce.hiring.map((h) => (
                      <tr
                        key={h.jobCode}
                        data-testid={`forecast-hiring-${h.jobCode}`}
                      >
                        <th scope="row">{h.jobCode}</th>
                        <td>{h.firstShortageMonth}</td>
                        <td className="sales-forecast-short">
                          {people(h.maximumShortage)}명
                        </td>
                        <td>
                          {h.startNow ? (
                            <span className="sales-forecast-now">지금</span>
                          ) : (
                            h.recommendedStartMonth
                          )}
                        </td>
                        <td>
                          <Link
                            data-testid={`forecast-candidates-${h.jobCode}`}
                            to="/UI-33"
                            state={{ jobCode: h.jobCode }}
                          >
                            {h.candidateCount}명 보기
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
          <details className="sales-forecast-card">
            <summary data-testid="forecast-method-toggle">
              계산 방법 / 원천
            </summary>
            <p>
              MO/DP: 05 해당월 유상 매출, 철수일까지, 예상 연장 제외. TK: 미취소
              청구(미래일 포함)를 차감한 잔액 ÷ 원래 전체 잔여 계약개월, 마지막
              계약월 끝자리. 기회: DOWN(예상금액 ÷ 유효개월 × 확률 ÷ 100), 수주
              후 중복 합산 없음.
            </p>
            <p>
              공급: 활성 직접 인력의 주직무, 입퇴사·하루라도 겹친 휴직 제외.
              확정월MM과 종료 후 예상 연장만 차감하며 가배정은 차감하지
              않습니다. 필요: NEED 확률 수요 + 전체 계약의 직무별 미충원. 직무별
              1자리 HALF_UP 결과합, 평균은 6개월 합 ÷ 6 HALF_UP 1자리입니다.
            </p>
            {data.revenueSources.map((r, i) => (
              <p
                key={`${r.sourceType}:${r.sourceId}:${r.month}`}
                data-testid={`forecast-basis-${i}`}
              >
                {r.name} · {r.month}: {r.basis}
              </p>
            ))}
          </details>
        </>
      )}
    </section>
  );
}
function ForecastBars({
  title,
  rows,
  labels,
  stacked = false,
  unit,
}: {
  title: string;
  rows: { month: string; first: string | null; second: string | null }[];
  labels: [string, string];
  stacked?: boolean;
  unit?: AmountUnit;
}) {
  // Floating point conversion is exclusively for drawing coordinates, never business values.
  const maximum = Math.max(
    1,
    ...rows.map((r) =>
      Math.max(
        Number(r.first ?? 0),
        Number(r.second ?? 0),
        stacked ? Number(r.first ?? 0) + Number(r.second ?? 0) : 0,
      ),
    ),
  );
  return (
    <section className="sales-forecast-card">
      <h2>{title}</h2>
      <p className="sales-forecast-muted">
        <span className="sales-forecast-legend-primary">■ {labels[0]}</span>{" "}
        <span className="sales-forecast-legend-info">■ {labels[1]}</span> ·{" "}
        {unit ?? "명"}
      </p>
      <ChartHost
        option={seriesChartOptions({
          categories: rows.map((row) => row.month),
          series: [
            {
              name: labels[0],
              type: "bar",
              values: rows.map((row) => row.first),
              stack: stacked ? "forecast" : undefined,
            },
            {
              name: labels[1],
              type: "bar",
              values: rows.map((row) => row.second),
              stack: stacked ? "forecast" : undefined,
            },
          ],
          formatValue: (value) =>
            unit
              ? `${formatIntegerAmount(value, unit)} ${unit}`
              : `${formatFixedDecimal(value, 1)}명`,
          axisUnit: unit ?? "명",
        })}
      >
        <svg viewBox="0 0 600 225" role="img" aria-label={title}>
          {rows.map((r, i) => {
            const a = (Number(r.first ?? 0) / maximum) * 145,
              b = (Number(r.second ?? 0) / maximum) * 145,
              x = 25 + i * 96;
            return (
              <g key={r.month}>
                <title>
                  {r.month} · {labels[0]}{" "}
                  {unit
                    ? formatIntegerAmount(r.first, unit)
                    : formatFixedDecimal(r.first, 1)}{" "}
                  · {labels[1]}{" "}
                  {unit
                    ? formatIntegerAmount(r.second, unit)
                    : formatFixedDecimal(r.second, 1)}
                </title>
                {r.first != null && (
                  <rect
                    x={x}
                    y={175 - a}
                    width={stacked ? 45 : 23}
                    height={a}
                    fill="var(--primary)"
                  />
                )}
                {r.second != null && (
                  <rect
                    x={stacked ? x : x + 27}
                    y={175 - b - (stacked ? a : 0)}
                    width={stacked ? 45 : 23}
                    height={b}
                    fill="var(--info)"
                  />
                )}
                <text x={x} y="200">
                  {r.month}
                </text>
                {(r.first == null || r.second == null) && (
                  <text x={x} y="25">
                    미확인
                  </text>
                )}
              </g>
            );
          })}
          <line x1="15" x2="590" y1="175" y2="175" stroke="var(--divider)" />
        </svg>
      </ChartHost>
    </section>
  );
}
