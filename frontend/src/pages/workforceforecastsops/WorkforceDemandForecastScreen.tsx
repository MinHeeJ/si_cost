import { FieldHelp } from "../../shared/FieldHelp";
import { KpiStrip } from "../../shared/Surface";
import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
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
function failure(error: unknown) {
  return `${error instanceof Error ? error.message : "조회하지 못했습니다."}${error instanceof ApiError ? ` · 요청 ID ${error.body.requestId}` : ""}`;
}
export function WorkforceDemandForecastScreen() {
  const [context, setContext] = useState<PeriodContext>();
  const [year, setYear] = useState("");
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
  const query =
    context && year
      ? {
          from: year === currentYear ? context.currentMonth : `${year}-01`,
          months:
            year === currentYear
              ? 13 - Number(context.currentMonth.slice(5))
              : 12,
          includeUnfilled: true,
        }
      : undefined;
  return (
    <SharedScreenFrame screenId="UI-17" periodBar={null}>
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
          <p role="status">기간을 불러오는 중입니다.</p>
        )}
        {context && query && (
          <>
            <div className="sicost-period-bar">
              <PeriodSelector
                type="YEAR"
                label="예측 연도"
                value={year}
                min={currentYear!}
                max={String(Math.min(9999, Number(currentYear) + 1))}
                disabled={pending}
                onChange={(v) => void changeYear(v)}
              />
              <span className="workforce-forecast-info">예측</span>
              <span>기준일 {context.currentDate}</span>
            </div>
            <WorkforceForecastPanel query={query} disabled={pending} />
          </>
        )}
      </section>
    </SharedScreenFrame>
  );
}
export function WorkforceForecastPanel({
  query,
  disabled = false,
  onDownload,
}: {
  query: api.WorkforceForecastQuery;
  disabled?: boolean;
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
  }, [query.from, query.months, query.includeUnfilled, reload]);
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
  return (
    <section
      className="workforce-forecast"
      data-testid="workforce-forecast-panel"
      aria-busy={loading}
    >
      <div className="workforce-forecast-toolbar">
        <p>
          {query.from} ~{" "}
          {data?.totals.at(-1)?.month ?? `${query.from.slice(0, 4)}-12`} · 전체
          직무
        </p>
        <ScreenTitleActions>
          {!mobile && (
            <button
              data-testid="workforce-download"
              disabled={disabled || loading || downloading || !data}
              onClick={() => void download()}
            >
              {downloading ? "파일 생성 중…" : "엑셀 다운로드"}
            </button>
          )}
          <button
            data-testid="workforce-refresh"
            disabled={loading || disabled}
            onClick={() => setReload((n) => n + 1)}
          >
            재조회
          </button>
        </ScreenTitleActions>
      </div>
      {mobile && <p role="note">엑셀 다운로드는 PC에서 이용해 주세요.</p>}
      {loading && <p role="status">예측을 불러오는 중입니다.</p>}
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
          <p className="workforce-forecast-muted">
            기준일 {data.basisDate} · 기준월 {data.standardMonth} · 미충원 포함
            · 단위: 명(MM)
          </p>
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
                <h2>전체 사람수(MM)</h2>
                <div className="sicost-table-viewport">
                  <table className="sicost-table">
                    <thead>
                      <tr>
                        <th>구분<FieldHelp label="구분" kind="B" /></th>
                        {data.totals.map((m) => (
                          <th key={m.month}>{m.month}</th>
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
                          <th scope="row">{label}<FieldHelp label={label} kind="B" /></th>
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
          <details className="workforce-forecast-card">
            <summary data-testid="workforce-calculation-toggle">
              계산 방법 / 범위 / 기준일
            </summary>
            <p>
              활성 직접 인력의 주직무 기준입니다. 입퇴사와 하루라도 겹치는
              휴직월을 제외하고 확정 MM과 종료 후 예상 연장을 차감합니다.
              가배정은 차감하지 않습니다.
            </p>
            <p>
              필요 인력은 기회 인원×월MM×확률 및 진행 프로젝트 미충원입니다.
              직무별 1자리 HALF_UP 후 합산합니다. 차이=가용−필요, 경계와 같은
              값은 경고가 아닙니다.
            </p>
            <p>추가 투입하지 않으면 PM이 인원 계획을 실제에 맞게 줄입니다.</p>
            {data.jobDiagnostics.map((j, n) => (
              <p
                key={`${j.jobCode}:${j.month.month}`}
                data-testid={`workforce-basis-${n}`}
              >
                {j.jobCode} · {j.month.month} · 반올림 전 가용{" "}
                {j.month.rawSupply ?? "–"} / 기회{" "}
                {j.month.rawOpportunityDemand ?? "–"} / 미충원{" "}
                {j.month.rawUnfilledDemand ?? "–"} · {j.basis}
              </p>
            ))}
            <h3>채용 검토 근거</h3>
            {data.hiring.length === 0 ? (
              <p>
                {data.issues.length
                  ? "미확인 원천과 기준을 확인해 주세요."
                  : "채용 검토가 필요한 직무가 없습니다."}
              </p>
            ) : (
              data.hiring.map((h) => (
                <p
                  key={h.jobCode}
                  data-testid={`workforce-hiring-${h.jobCode}`}
                >
                  {h.jobCode} · 첫부족월 {h.firstShortageMonth} · 최대부족{" "}
                  {people(h.maximumShortage)}명 · 권장시작{" "}
                  {h.startNow ? "지금" : h.recommendedStartMonth} · 후보{" "}
                  {h.candidateCount}명
                </p>
              ))
            )}
          </details>
        </>
      )}
    </section>
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
