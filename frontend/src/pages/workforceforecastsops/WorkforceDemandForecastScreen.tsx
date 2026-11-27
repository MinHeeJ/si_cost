import { ScreenLoading } from "../../shared/ScreenLoading";
import { CalculationMethods } from "../../shared/CalculationMethods";
import { FieldHelp } from "../../shared/FieldHelp";
import { KpiStrip } from "../../shared/Surface";
import { PeriodStatus } from "../../shared/PeriodStatus";
import { StatusBadge } from "../../shared/Feedback";
import { useEffect, useRef, useState } from "react";

import { ApiError } from "../../shared/api";
import { formatFixedDecimal } from "../../shared/format";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { ChartHost } from "../../shared/charts/ChartHost";
import { seriesChartOptions } from "../../shared/charts/chartAdapters";
import { PeriodSelector } from "../shared/PeriodSelector";
import {
  getPeriodContext,
  getSharedPreferences,
  putSharedPreference,
  type PeriodContext,
  type SharedPreference,
} from "../shared/sharedApi";
import * as api from "./workforceForecastApi";
import { CodeName } from "../../shared/codeNames";
import "./workforceForecast.css";

const sourceEvents = [
  "sicost:sales-committed",
  "sicost:assignments-committed",
  "sicost:developers-committed",
  "sicost:projects-committed",
  "sicost:project-plans-committed",
  "sicost:operating-standards-committed",
  "sicost:codegroups-committed",
];
const people = (value: string | null | undefined) =>
  formatFixedDecimal(value, 1);
const forecastMonthLabel = new Intl.DateTimeFormat("ko-KR", {
  year: "numeric",
  month: "long",
  timeZone: "UTC",
});

function forecastRange(from: string, months: number) {
  const first = new Date(`${from}-01T00:00:00Z`);
  const last = new Date(first);
  last.setUTCMonth(last.getUTCMonth() + months - 1);
  return `${forecastMonthLabel.format(first)} ~ ${forecastMonthLabel.format(last)}`;
}

function failure(error: unknown) {
  return `${error instanceof Error ? error.message : "조회하지 못했습니다."}${error instanceof ApiError ? ` · 요청 ID ${error.body.requestId}` : ""}`;
}

const workforceForecastGuide = (
  <dl>
    <div>
      <dt>예측 연도 선택</dt>
      <dd>
        올해를 선택하면 이번 달부터 12월까지, 내년을 선택하면 1월부터
        12월까지 전체 직무의 전망을 봅니다.
      </dd>
    </div>
    <div>
      <dt>월별 필요·가용 인력</dt>
      <dd>
        진행 프로젝트의 미충원분을 보여줍니다. 영업 기회 포함을 선택하면 수주 확률 조건에 맞는 필요 인력도 더합니다.
        차이는 가용 인력에서 필요 인력을 뺀 값입니다. 1 MM은 한 사람이 한 달간
        풀타임으로 일하는 양입니다.
      </dd>
    </div>
    <div>
      <dt>산출 근거와 채용 검토</dt>
      <dd>
        하단의 [계산 방법]에서 직무별 월별 산출 값과 채용 검토 근거를 확인할 수
        있습니다.
      </dd>
    </div>
  </dl>
);

export function WorkforceDemandForecastScreen() {
  const [context, setContext] = useState<PeriodContext>();
  const [year, setYear] = useState("");
  const [opportunityFilter, setOpportunityFilter] = useState({ include: false, minimum: "0" });
  const [minimumDraft, setMinimumDraft] = useState("0");
  const [preference, setPreference] = useState<SharedPreference>();
  const [error, setError] = useState<unknown>();
  const [reload, setReload] = useState(0);
  const [pending, setPending] = useState(false);
  const saving = useRef(false);
  useEffect(() => {
    let live = true;
    setError(undefined);
    void Promise.all([getPeriodContext(), getSharedPreferences()])
      .then(([ctx, prefs]) => {
        if (!live) return;
        const currentYear = ctx.currentMonth.slice(0, 4);
        const pref = prefs.items.find(
          (p) => p.type === "PERIOD" && p.key === "UI-17",
        );
        const saved = pref?.value as
          | { type?: string; value?: string }
          | undefined;
        const valid =
          saved?.type === "YEAR" &&
          [currentYear, String(Number(currentYear) + 1)].includes(
            saved.value ?? "",
          );
        setContext(ctx);
        setPreference(pref);
        setYear(valid ? saved!.value! : currentYear);
        if (pref?.error) setError(new Error(pref.error.message));
        else if (pref?.stored && !valid)
          setError(
            new Error(
              "저장된 기간이 예측 범위를 벗어나 올해를 표시합니다. 저장된 설정은 변경하지 않았습니다.",
            ),
          );
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, [reload]);
  const changeYear = async (value: string) => {
    if (
      !context ||
      saving.current ||
      ![
        context.currentMonth.slice(0, 4),
        String(Number(context.currentMonth.slice(0, 4)) + 1),
      ].includes(value)
    )
      return;
    saving.current = true;
    setPending(true);
    setError(undefined);
    try {
      const result = await putSharedPreference(
        "PERIOD",
        "UI-17",
        { type: "YEAR", value },
        preference?.revision ?? null,
      );
      setPreference(result.preference);
      setYear(value);
    } catch (e) {
      setError(e);
    } finally {
      saving.current = false;
      setPending(false);
    }
  };
  const currentYear = context?.currentMonth.slice(0, 4);
  const isCurrentYear = year === currentYear;
  const query =
    context && year
      ? {
          from: isCurrentYear ? context.currentMonth : `${year}-01`,
          months:
            isCurrentYear
              ? 13 - Number(context.currentMonth.slice(5))
              : 12,
          includeUnfilled: true,
          includeOpportunities: opportunityFilter.include,
          minimumWinProbability: opportunityFilter.include ? opportunityFilter.minimum : undefined,
        }
      : undefined;
  return (
    <SharedScreenFrame
      screenId="UI-17"
      screenGuide={workforceForecastGuide}
      periodBar={
        currentYear && query ? (
          <section className="sicost-period-bar" aria-label="화면 기간">
            <div className="sicost-period-controls">
              <PeriodSelector
                type="YEAR"
                label="예측 연도"
                value={year}
                min={currentYear}
                max={String(Math.min(9999, Number(currentYear) + 1))}
                disabled={pending}
                onChange={(value) => void changeYear(value)}
              />
              <StatusBadge tone={isCurrentYear ? "ok" : "info"}>
                {isCurrentYear ? "올해" : "내년·계획"}
              </StatusBadge>
            </div>
          </section>
        ) : null
      }
    >
      <section
        className="workforce-forecast"
        data-testid="workforce-forecast-screen"
      >
        {error != null && (
          <div role="alert" className="workforce-forecast-error">
            {failure(error)}{" "}
            <button
              data-testid="workforce-period-retry"
              onClick={() => setReload((n) => n + 1)}
            >
              기간 재조회
            </button>
          </div>
        )}
        {!context && error == null && (
          <ScreenLoading message="기간을 불러오는 중입니다." />
        )}
        {context && query && (
          <form className="workforce-forecast-opportunity-filter" onSubmit={(event) => {
            event.preventDefault();
            setOpportunityFilter({ include: true, minimum: minimumDraft || "0" });
          }}>
            <label>
              <input type="checkbox" checked={opportunityFilter.include}
                onChange={(event) => setOpportunityFilter({ include: event.target.checked, minimum: opportunityFilter.minimum })} />
              영업 기회 포함
            </label>
            {opportunityFilter.include && (
              <>
                <label>
                  수주 확률
                  <input type="number" min="0" max="100" step="0.01" value={minimumDraft}
                    onChange={(event) => setMinimumDraft(event.target.value)} />
                  % 이상
                </label>
                <button type="submit">적용</button>
              </>
            )}
          </form>
        )}
        {context && query && (
          <WorkforceForecastPanel
            query={query}
            basisDate={context.currentDate}
            disabled={pending}
            periodRule={
              isCurrentYear ? "이번 달부터 12월까지" : "내년 1월부터 12월까지"
            }
          />
        )}
      </section>
    </SharedScreenFrame>
  );
}
export function WorkforceForecastPanel({
  query,
  basisDate,
  disabled = false,
  periodRule,
  onDownload,
}: {
  query: api.WorkforceForecastQuery;
  basisDate?: string;
  disabled?: boolean;
  periodRule?: string;
  onDownload?(unit: api.WorkforceAmountUnit): Promise<void>;
}) {
  const [data, setData] = useState<api.WorkforceForecast>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const unit: api.WorkforceAmountUnit = "WON";
  const [downloading, setDownloading] = useState(false);
  const downloadActive = useRef(false);
  const [downloadError, setDownloadError] = useState<unknown>();
  const [downloaded, setDownloaded] = useState(false);
  const [mobile, setMobile] = useState(
    () => window.matchMedia?.("(max-width: 767px)")?.matches ?? false,
  );
  useEffect(() => {
    const media = window.matchMedia?.("(max-width: 767px)");
    const change = () => setMobile(media?.matches ?? false);
    media?.addEventListener?.("change", change);
    return () => media?.removeEventListener?.("change", change);
  }, []);
  useEffect(() => {
    const refresh = () => setReload((n) => n + 1);
    sourceEvents.forEach((e) => window.addEventListener(e, refresh));
    return () =>
      sourceEvents.forEach((e) => window.removeEventListener(e, refresh));
  }, []);
  useEffect(() => {
    let live = true;
    setLoading(true);
    setError(undefined);
    setData(undefined);
    setDownloaded(false);
    setDownloadError(undefined);
    void api
      .getWorkforceForecasts(query)
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
  }, [query.from, query.months, query.includeUnfilled, query.includeOpportunities, query.minimumWinProbability, reload]);
  const download = async () => {
    if (
      downloadActive.current ||
      disabled ||
      loading ||
      !data ||
      window.matchMedia?.("(max-width: 767px)")?.matches
    )
      return;
    downloadActive.current = true;
    setDownloading(true);
    setDownloadError(undefined);
    setDownloaded(false);
    try {
      if (onDownload) await onDownload(unit);
      else
        api.saveWorkforceForecastDownload(
          await api.getWorkforceForecastExport(query, unit),
        );
      setDownloaded(true);
    } catch (e) {
      setDownloadError(e);
    } finally {
      downloadActive.current = false;
      setDownloading(false);
    }
  };
  const refreshButton = (
    <button
      type="button"
      data-testid="workforce-refresh"
      disabled={loading || disabled}
      onClick={() => setReload((n) => n + 1)}
    >
      재조회
    </button>
  );
  const downloadButton = !mobile && (
    <button
      className="sicost-download-button"
      type="button"
      data-testid="workforce-download"
      disabled={disabled || loading || downloading || !data}
      onClick={() => void download()}
    >
      {downloading ? "파일 생성 중…" : "엑셀 다운로드"}
    </button>
  );
  return (
    <section
      className="workforce-forecast"
      data-testid="workforce-forecast-panel"
      aria-busy={loading}
    >
      <div className="workforce-forecast-toolbar">
        <PeriodStatus
          inline
          className={`sicost-period-summary workforce-forecast-scope${basisDate ? " workforce-forecast-scope-with-basis" : ""}`}
        >
          {basisDate && (
            <span className="sicost-period-summary-item">
              <span className="sicost-period-summary-label">기준일</span>
              <b>{basisDate}</b>
            </span>
          )}
          <span className="sicost-period-summary-item">
            <span className="sicost-period-summary-label">예측 기간</span>
            <b>{forecastRange(query.from, query.months)}</b>
          </span>
          <span className="sicost-period-summary-item">
            {periodRule ? `${periodRule} · 전체 직무` : "전체 직무"}
          </span>
        </PeriodStatus>
        <div className="workforce-forecast-actions">
          {refreshButton}
          {downloadButton}
        </div>
      </div>
      {mobile && <p role="note">엑셀 다운로드는 PC에서 이용해 주세요.</p>}
      {loading && <ScreenLoading message="예측을 불러오는 중입니다." />}
      {error != null && (
        <p role="alert" className="workforce-forecast-error">
          {failure(error)}
        </p>
      )}
      {downloadError != null && (
        <p role="alert" className="workforce-forecast-error">
          {failure(downloadError)}
        </p>
      )}
      {downloaded && <p role="status">다운로드했습니다.</p>}
      {data && (
        <>
          {data.issues.length > 0 && (
            <div role="note" className="workforce-forecast-warning">
              <strong>원천·기준 미확인 — 0과 구분합니다.</strong>
              {data.issues.map((i, n) => (
                <p data-testid={`workforce-issue-${n}`} key={`${i.field}:${n}`}>
                  {i.field}: {i.reason}
                </p>
              ))}
            </div>
          )}
          <KpiStrip className="workforce-forecast-summary">
            <article data-testid="workforce-supply-card">
              <p>마지막월 가용</p>
              <strong>{people(data.lastMonthSupply)}명</strong>
            </article>
            <article data-testid="workforce-demand-card">
              <p>마지막월 필요</p>
              <strong>{people(data.lastMonthDemand)}명</strong>
              <p>그중 미충원 {people(data.totals.at(-1)?.unfilledDemand)}명</p>
            </article>
            <article data-testid="workforce-shortage-card">
              <p>가장 부족한 직무</p>
              <strong>
                {data.mostShortageJobCode ? (
                  <CodeName group="JOB" value={data.mostShortageJobCode} />
                ) : data.issues.length ? (
                  "미확인"
                ) : (
                  "부족 없음"
                )}
              </strong>
              <p>최대부족 {people(data.maximumShortage)}명</p>
            </article>
          </KpiStrip>
          {data.totals.length === 0 ? (
            <div className="workforce-forecast-empty">
              <p>선택 기간에 예측 자료가 없습니다.</p>
              <button type="button" onClick={() => setReload((n) => n + 1)}>
                조건 초기화
              </button>
            </div>
          ) : (
            <>
              <WorkforceBars data={data} />
              <section className="workforce-forecast-card">
                <h2>월별 필요·가용 인력 (MM)</h2>
                <div className="sicost-table-viewport">
                  <table
                    aria-label="월별 필요·가용 인력"
                    className="sicost-table workforce-forecast-table"
                  >
                    <thead>
                      <tr>
                        <th scope="col">
                          구분
                          <FieldHelp label="구분" kind="B" />
                        </th>
                        {data.totals.map((m) => (
                          <th scope="col" key={m.month}>
                            {m.month}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {(
                        [
                          ["필요 인력", "demand"],
                          ["가용 인력", "supply"],
                          ["차이 가용−필요", "difference"],
                        ] as const
                      ).map(([label, key]) => (
                        <tr data-testid={`workforce-row-${key}`} key={key}>
                          <th scope="row">
                            {label}
                            <FieldHelp label={label} kind="B" />
                          </th>
                          {data.totals.map((m) => (
                            <td
                              key={m.month}
                              className={
                                key === "difference"
                                  ? `workforce-forecast-${m.status.toLowerCase()}`
                                  : "sicost-decimal"
                              }
                            >
                              {key === "difference"
                                ? difference(m.difference, m.status)
                                : people(m[key])}
                              {key === "demand" && (
                                <small>
                                  그중 미충원 {people(m.unfilledDemand)}
                                </small>
                              )}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            </>
          )}
          <WorkforceForecastCalculation data={data} includeOpportunities={query.includeOpportunities} />
        </>
      )}
    </section>
  );
}

const workforceCalculationMethods = [
  {
    metric: "가용 인력",
    formula:
      "1인당 1 MM − 확정 투입 MM − 예상 연장 MM. 음수는 0으로 보고 직무별로 합산합니다.",
    reference:
      "활성 직접 인력의 주직무를 기준으로 집계합니다. 입사월부터 퇴사월까지 반영하며, 휴직과 하루라도 겹치는 달은 제외합니다. 가배정은 차감하지 않습니다.",
  },
  {
    metric: "필요 인력",
    formula: "영업 기회 인원 × 월 MM × 수주 확률 + 진행 프로젝트 미충원 MM",
    reference:
      "미충원 MM은 직무별 인원 계획에서 확정 투입분을 뺀 잔여량을 남은 계약 개월에 나눠 반영합니다.",
  },
  {
    metric: "차이와 경고",
    formula: "가용 인력 − 필요 인력",
    reference:
      "가용과 필요는 직무별로 소수점 첫째 자리까지 반올림한 뒤 합산합니다. 차이가 부족·여유 기준을 넘을 때 경고합니다.",
  },
] as const;

function WorkforceForecastCalculation({ data, includeOpportunities }: {
  data: api.WorkforceForecast;
  includeOpportunities?: boolean;
}) {
  const methods = workforceCalculationMethods.map((entry) =>
    includeOpportunities != null && entry.metric === "필요 인력"
      ? {
          ...entry,
          formula: includeOpportunities
            ? "수주 확률 조건을 충족한 영업 기회의 인원 × 월 MM + 진행 프로젝트 미충원 MM"
            : "진행 프로젝트 미충원 MM",
        }
      : entry,
  );
  return (
    <CalculationMethods
      rows={methods}
      testId="workforce-calculation-toggle"
    >
      <div className="workforce-calculation-detail">
        <p>
          기준일 {data.basisDate}에 진행 중인 프로젝트를 판단합니다. 운영
          기준은 {data.standardMonth}에 적용되는 값을 사용하며, 전체 직무의{" "}
          {forecastRange(data.from, data.months)} 전망을 집계합니다.
        </p>
        <p>
          추가 투입 계획이 없다면 PM이 인원 계획을 실제 투입에 맞게 조정합니다.
        </p>
        <section>
          <h3>직무별 월별 산출 근거 (반올림 전 MM)</h3>
          {data.jobDiagnostics.length === 0 ? (
            <p>표시할 직무별 산출 근거가 없습니다.</p>
          ) : (
            <div className="sicost-calculation-table-scroll">
              <table
                aria-label="직무별 월별 산출 근거"
                className="sicost-calculation-table workforce-calculation-data"
              >
                <thead>
                  <tr>
                    <th scope="col">직무 코드</th>
                    <th scope="col">예측 월</th>
                    <th scope="col">가용 MM</th>
                    <th scope="col">영업 기회 수요 MM</th>
                    <th scope="col">미충원 수요 MM</th>
                    <th scope="col">산출 기준</th>
                  </tr>
                </thead>
                <tbody>
                  {data.jobDiagnostics.map(({ jobCode, month, basis }, index) => (
                    <tr
                      key={`${jobCode}:${month.month}`}
                      data-testid={`workforce-basis-${index}`}
                    >
                      <th scope="row">{jobCode}</th>
                      <td>{month.month}</td>
                      <td>{month.rawSupply ?? "–"}</td>
                      <td>{month.rawOpportunityDemand ?? "–"}</td>
                      <td>{month.rawUnfilledDemand ?? "–"}</td>
                      <td>{basis}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
        <section>
          <h3>채용 검토 근거</h3>
          {data.hiring.length === 0 ? (
            <p>
              {data.issues.length
                ? "미확인 원천과 기준을 확인해 주세요."
                : "채용 검토가 필요한 직무가 없습니다."}
            </p>
          ) : (
            <div className="sicost-calculation-table-scroll">
              <table
                aria-label="채용 검토 근거"
                className="sicost-calculation-table workforce-calculation-data"
              >
                <thead>
                  <tr>
                    <th scope="col">직무 코드</th>
                    <th scope="col">첫 부족 월</th>
                    <th scope="col">최대 부족 MM</th>
                    <th scope="col">채용 검토 시작</th>
                    <th scope="col">후보 인원</th>
                  </tr>
                </thead>
                <tbody>
                  {data.hiring.map((hiring) => (
                    <tr
                      key={hiring.jobCode}
                      data-testid={`workforce-hiring-${hiring.jobCode}`}
                    >
                      <th scope="row">{hiring.jobCode}</th>
                      <td>{hiring.firstShortageMonth}</td>
                      <td>{people(hiring.maximumShortage)}</td>
                      <td>{hiring.startNow ? "지금" : hiring.recommendedStartMonth}</td>
                      <td>{hiring.candidateCount}명</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </CalculationMethods>
  );
}

function difference(value: string | null, status: string) {
  if (value == null) return "– · 미확인";
  const zero = /^-?0(?:\.0+)?$/.test(value);
  return `${zero ? "맞음" : `${people(value.replace(/^-/, ""))}명 ${value.startsWith("-") ? "부족" : "남음"}`}${status === "UNKNOWN" ? " · 판정 미확인" : ""}`;
}
function WorkforceBars({ data }: { data: api.WorkforceForecast }) {
  const maximum = Math.max(
    1,
    ...data.totals.flatMap((m) => [
      Number(m.supply ?? 0),
      Number(m.demand ?? 0),
    ]),
  );
  return (
    <section className="workforce-forecast-card">
      <h2>월별 가용 vs 필요</h2>
      <p className="workforce-forecast-muted workforce-chart-legend">
        <span className="workforce-forecast-available">■ 가용</span>{" "}
        <span className="workforce-forecast-needed">■ 필요</span>
      </p>
      <ChartHost
        option={seriesChartOptions({
          categories: data.totals.map((month) => month.month),
          series: [
            {
              name: "가용",
              type: "bar",
              values: data.totals.map((month) => month.supply),
            },
            {
              name: "필요",
              type: "bar",
              values: data.totals.map((month) => month.demand),
            },
            {
              name: "그중 미충원",
              type: "line",
              values: data.totals.map((month) => month.unfilledDemand),
              dashed: true,
            },
          ],
          formatValue: (value) => `${people(value)}명`,
          axisUnit: "명",
        })}
      >
        <svg
          role="img"
          aria-label="월별 가용 vs 필요"
          viewBox={`0 0 ${Math.max(600, data.months * 80)} 240`}
        >
          {data.totals.map((m, i) => {
            const a = (Number(m.supply ?? 0) / maximum) * 160,
              b = (Number(m.demand ?? 0) / maximum) * 160,
              x = 25 + i * Math.max(80, 550 / data.months);
            return (
              <g key={m.month}>
                <title>
                  {m.month} · 가용 {people(m.supply)} · 필요 {people(m.demand)}{" "}
                  · 그중 미충원 {people(m.unfilledDemand)}
                </title>
                {m.supply != null && (
                  <rect
                    x={x}
                    y={190 - a}
                    width="20"
                    height={a}
                    fill="var(--primary)"
                  />
                )}
                {m.demand != null && (
                  <rect
                    x={x + 24}
                    y={190 - b}
                    width="20"
                    height={b}
                    fill="var(--warn)"
                  />
                )}
                <text x={x} y="215">
                  {m.month}
                </text>
                {(m.supply == null || m.demand == null) && (
                  <text x={x} y="25">
                    미확인
                  </text>
                )}
              </g>
            );
          })}
        </svg>
      </ChartHost>
    </section>
  );
}
