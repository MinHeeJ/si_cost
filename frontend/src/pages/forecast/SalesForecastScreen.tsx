import { ScreenLoading } from "../../shared/ScreenLoading";
import { FieldHelp } from "../../shared/FieldHelp";
import { KpiStrip } from "../../shared/Surface";
import { CalculationMethods } from "../../shared/CalculationMethods";
import { PeriodStatus } from "../../shared/PeriodStatus";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../shared/api";
import {
  formatIntegerAmount,
  formatSummaryAmount,
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
const forecastMethods = [
  {
    metric: "월단위·파견 프로젝트 매출",
    formula: "투입 배정의 해당 월 유상 매출",
    reference: "확정 유상 투입을 철수일까지 반영하며 예상 연장은 제외합니다.",
  },
  {
    metric: "턴키 프로젝트 매출",
    formula: "(계약금액 − 미취소 청구액) ÷ 원래 남은 계약 개월 수",
    reference:
      "미래일의 미취소 청구액도 포함합니다. 잔액의 최솟값은 0원이고, 나눗셈의 나머지는 마지막 계약월에 반영합니다.",
  },
  {
    metric: "영업 기회 매출",
    formula: "예상 금액 × 수주 확률을 원 단위로 절사한 뒤 계약 개월 수로 나눔",
    reference:
      "월별 금액도 원 단위로 절사하고 나머지는 마지막 달에 반영합니다. 수주 후 프로젝트 매출로 집계합니다.",
  },
  {
    metric: "가용 인력",
    formula: "투입 가능한 인력 1인당 1 MM에서 확정 투입 MM과 예상 연장 MM을 차감",
    reference:
      "활성 직접 인력의 주직무에 입·퇴사월을 반영하고, 휴직과 하루라도 겹치는 달을 제외합니다. 가배정은 가용 인력에 남겨둡니다.",
  },
  {
    metric: "필요 인력과 6개월 평균",
    formula: "확률 반영 영업 기회 수요 + 진행 프로젝트의 직무별 미충원",
    reference:
      "직무별로 소수점 첫째 자리까지 반올림한 후 합산합니다. 6개월 평균은 월별 합계를 6으로 나눈 뒤 소수점 첫째 자리까지 반올림합니다.",
  },
] as const;
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
  const money = (value: string | null | undefined) =>
    formatIntegerAmount(value, unit);
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
        {data && (
          <PeriodStatus inline className="sicost-period-summary sales-forecast-scope">
            <span className="sicost-period-summary-item">
              <span className="sicost-period-summary-label">기준일</span>
              <b>{data.workforce.basisDate}</b>
            </span>
            <span className="sicost-period-summary-item">
              <span className="sicost-period-summary-label">예측 기간</span>
              <b>{data.from} ~ {data.revenueMonths.at(-1)?.month ?? data.from}</b>
            </span>
            <span className="sicost-period-summary-item">
              조회한 달의 다음 달부터 {data.months}개월
            </span>
            <span className="sicost-period-summary-item sales-forecast-scope-note">
              실제 손익·청구·미수에 저장하지 않습니다. 인력 필요에는 프로젝트
              미충원이 포함됩니다.
            </span>
          </PeriodStatus>
        )}
        <div className="sales-forecast-actions">
          {data && (
            <label className="sicost-field" htmlFor="sales-forecast-unit">
              금액 단위
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
            </label>
          )}
          <button
            type="button"
            data-testid="forecast-refresh-button"
            disabled={loading}
            onClick={() => setRevision((v) => v + 1)}
          >
            재조회
          </button>
          <SalesForecastDownloadPanel amountUnit={amountUnit} />
        </div>
      </div>
      {loading && <ScreenLoading message="예측을 불러오는 중입니다." />}
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
                {formatSummaryAmount(data.summary.sixMonthRevenue)}
              </strong>
            </article>
            <article data-testid="forecast-opportunity-card">
              <p>그중 기회 매출</p>
              <strong>
                {formatSummaryAmount(data.summary.opportunityRevenue)}
              </strong>
              <p>모두 따면 {formatSummaryAmount(data.summary.allWonRevenue)}</p>
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
              unit="원"
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
              </div>
            )}
            <div className="sicost-table-viewport">
              <table className="sicost-table sales-forecast-table" aria-label="월별 매출">
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
              <table className="sicost-table sales-forecast-table" aria-label="월별 인력 수요·공급">
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
              </div>
            ) : (
              <div className="sicost-table-viewport">
                <table className="sicost-table sales-forecast-table" aria-label="채용·외부인력 검토">
                  <thead>
                    <tr>
                      <th>직무<FieldHelp label="직무" kind="B" /></th>
                      <th scope="col">첫 부족 월</th>
                      <th scope="col">최대 부족</th>
                      <th scope="col">채용 검토 시작</th>
                      <th scope="col">후보 인원</th>
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
                            className="sicost-text-link"
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
          <CalculationMethods rows={forecastMethods} testId="forecast-method-toggle">
            <div className="sales-forecast-basis">
              <p>운영 기준은 {data.workforce.standardMonth}에 적용되는 값을 사용합니다.</p>
              <h3>월별 매출 산출 근거</h3>
              {data.revenueSources.length === 0 ? (
                <p>표시할 매출 산출 근거가 없습니다.</p>
              ) : (
                <div className="sicost-calculation-table-scroll">
                  <table aria-label="월별 매출 산출 근거" className="sicost-calculation-table">
                    <thead>
                      <tr>
                        <th scope="col">프로젝트·기회</th>
                        <th scope="col">예측 월</th>
                        <th scope="col">산출 기준</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.revenueSources.map((source, index) => (
                        <tr
                          key={`${source.sourceType}:${source.sourceId}:${source.month}`}
                          data-testid={`forecast-basis-${index}`}
                        >
                          <th scope="row">{source.name}</th>
                          <td>{source.month}</td>
                          <td>{source.basis}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </CalculationMethods>
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
