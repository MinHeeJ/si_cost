import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { FilterBar } from "../../shared/FilterBar";
import { formatFixedDecimal } from "../../shared/format";
import { DeveloperDrawer } from "../developers/DeveloperDrawer";
import { WorkforceForecastPanel } from "../workforceforecastsops/WorkforceDemandForecastScreen";
import {
  getPeriodContext,
  getSharedCodes,
  type PeriodContext,
  type SharedCodeLookup,
  type PeriodValue,
} from "../shared/sharedApi";
import * as api from "./workforceCalendarApi";
import "./workforceCalendar.css";

const events = [
  "sicost:sales-committed",
  "sicost:assignments-committed",
  "sicost:developers-committed",
  "sicost:projects-committed",
  "sicost:project-plans-committed",
];
const decimal = (v: string | null | undefined) => formatFixedDecimal(v, 2);
const statuses: Record<api.CalendarCell["status"], string> = {
  BEFORE_JOIN: "입사 전",
  RETIRED: "퇴사",
  LEAVE: "휴직",
  ASSIGNED: "투입중",
  TENTATIVE: "가배정",
  AVAILABLE: "비어있음",
  PAST_EMPTY: "–",
};
export function WorkforceOperationsScreen() {
  const [context, setContext] = useState<PeriodContext>();
  const [year, setYear] = useState<number>();
  const [error, setError] = useState<unknown>();
  const [reload, setReload] = useState(0);
  const [tab, setTab] = useState("calendar");
  useEffect(() => {
    let live = true;
    void getPeriodContext()
      .then((c) => {
        if (live) {
          setContext(c);
          setYear((y) => y ?? Number(c.currentMonth.slice(0, 4)));
          setError(undefined);
        }
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, [reload]);
  useEffect(() => {
    const update = (event: Event) => {
      const d = (
        event as CustomEvent<{
          screenId: string;
          value: PeriodValue;
          context: PeriodContext;
        }>
      ).detail;
      if (d?.screenId === "UI-28" && d.value.type === "YEAR") {
        setContext(d.context);
        setYear(Number(d.value.value));
      }
    };
    window.addEventListener("sicost:screen-period-changed", update);
    return () =>
      window.removeEventListener("sicost:screen-period-changed", update);
  }, []);
  const next = context ? nextMonth(context.currentMonth) : "";
  return (
    <section
      className="workforce-calendar"
      data-testid="workforce-operations-screen"
      data-screen-id="SCR-UI-28"
    >
      {error != null && (
        <CalendarError error={error} retry={() => setReload((n) => n + 1)} />
      )}
      {!context && !error && <p role="status">기간을 불러오는 중입니다.</p>}
      {context && year && (
        <>
          <div role="tablist" aria-label="인력 운영 보기">
            <button
              role="tab"
              data-testid="calendar-tab"
              aria-selected={tab === "calendar"}
              onClick={() => setTab("calendar")}
            >
              달력
            </button>
            <button
              role="tab"
              data-testid="supply-tab"
              aria-selected={tab === "supply"}
              onClick={() => setTab("supply")}
            >
              수요·공급
            </button>
          </div>
          <div
            role="tabpanel"
            aria-label="달력"
            data-testid="calendar-panel"
            hidden={tab !== "calendar"}
          >
            <WorkforceCalendar year={year} context={context} />
          </div>
          <div
            role="tabpanel"
            aria-label="수요·공급"
            data-testid="supply-panel"
            hidden={tab !== "supply"}
          >
            <p>달력 필터 미적용 · {next}부터 6개월 전체 인력입니다.</p>
            <WorkforceForecastPanel
              query={{ from: next, months: 6, includeUnfilled: true }}
              onDownload={(unit) =>
                api.downloadWorkforceCalendar({ year }, "SUPPLY_DEMAND", unit)
              }
            />
          </div>
        </>
      )}
    </section>
  );
}
function nextMonth(month: string) {
  const year = Number(month.slice(0, 4)),
    m = Number(month.slice(5));
  return m === 12
    ? `${year + 1}-01`
    : `${year}-${String(m + 1).padStart(2, "0")}`;
}
export function WorkforceCalendar({
  year,
  context,
}: {
  year: number;
  context: PeriodContext;
}) {
  const [draft, setDraft] = useState({
    jobCode: "",
    gradeCode: "",
    employeeType: "",
    remainingOnly: false,
  });
  const [filter, setFilter] = useState(draft);
  const [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(50);
  const [data, setData] = useState<api.CalendarPage>();
  const [loading, setLoading] = useState(true),
    [reload, setReload] = useState(0);
  const [error, setError] = useState<unknown>(),
    [optionsError, setOptionsError] = useState<unknown>();
  const [options, setOptions] = useState<Record<string, SharedCodeLookup[]>>(
    {},
  );
  const [selected, setSelected] = useState<string>();
  const [pending, setPending] = useState(false);
  const [notice, setNotice] = useState("");
  const query: api.CalendarFilter = {
    year,
    jobCode: filter.jobCode || undefined,
    gradeCode: filter.gradeCode || undefined,
    employeeType: filter.employeeType || undefined,
    remainingOnly: filter.remainingOnly,
    page,
    size,
  };
  useEffect(() => {
    let live = true;
    void Promise.all([
      getSharedCodes("JOB", true),
      getSharedCodes("GRADE", true),
      getSharedCodes("EMP_TYPE", true),
    ])
      .then(([j, g, e]) => {
        if (live) {
          setOptions({
            jobCode: j.items,
            gradeCode: g.items,
            employeeType: e.items,
          });
          setOptionsError(undefined);
        }
      })
      .catch((e) => {
        if (live) setOptionsError(e);
      });
    return () => {
      live = false;
    };
  }, [reload]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setData(undefined);
    setError(undefined);
    void api
      .getWorkforceOperations(query, controller.signal)
      .then((d) => {
        if (!controller.signal.aborted) setData(d);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [year, filter, page, size, reload]);
  useEffect(() => {
    const refresh = () => setReload((n) => n + 1);
    events.forEach((e) => window.addEventListener(e, refresh));
    return () => events.forEach((e) => window.removeEventListener(e, refresh));
  }, []);
  const reset = () => {
    const empty = {
      jobCode: "",
      gradeCode: "",
      employeeType: "",
      remainingOnly: false,
    };
    setDraft(empty);
    setFilter(empty);
    setPage(1);
  };
  const lookup = (key: string, code: string) =>
    options[key]?.find((v) => v.code === code)?.name ?? code;
  async function download() {
    if (pending || window.matchMedia?.("(max-width: 767px)").matches) return;
    setPending(true);
    setNotice("");
    try {
      await api.downloadWorkforceCalendar(query);
      setNotice("다운로드했습니다.");
    } catch (e) {
      setError(e);
    } finally {
      setPending(false);
    }
  }
  return (
    <section
      data-testid="workforce-calendar-content"
      className="workforce-calendar"
    >
      <p>
        {year}년 · 기준일 {context.currentDate} ·{" "}
        {year < Number(context.currentMonth.slice(0, 4))
          ? "과거 조회 전용"
          : "조회 전용"}
      </p>
      {data && (
        <div className="workforce-calendar-summary">
          <button
            data-testid="calendar-available-card"
            onClick={() => {
              setDraft((d) => ({ ...d, remainingOnly: true }));
              setFilter((f) => ({ ...f, remainingOnly: true }));
              setPage(1);
            }}
          >
            현재 비는인원 {data.summary.availablePeople}명 / 남는 MM{" "}
            {decimal(data.summary.remainingMm)}
          </button>
          <Link
            data-testid="calendar-withdrawal-card"
            to={`/UI-05?${new URLSearchParams({ context: "withdrawal", year: String(year), jobCode: filter.jobCode })}`}
          >
            3개월 안 철수 예정 {data.summary.leavingSoonPeople}명 · 연장 가능성
            높음 {data.summary.highExtensionPeople}명
          </Link>
          <Link
            data-testid="calendar-concurrent-card"
            to={`/UI-12?${new URLSearchParams({ year: String(year), warning: "CONCURRENT", jobCode: filter.jobCode })}`}
          >
            동시 투입 {data.summary.concurrentPeople}명 · 1.0 초과{" "}
            {data.summary.overAssignedPeople}명
          </Link>
          <article>
            M+3 남는 MM {decimal(data.summary.thirdMonthRemainingMm)}
          </article>
        </div>
      )}
      <FilterBar
        testId="calendar-filters"
        resetTestId="calendar-reset"
        searchTestId="calendar-search"
        onSubmit={() => {
          setFilter(draft);
          setPage(1);
        }}
        onReset={reset}
      >
        {(
          [
            ["jobCode", "직무"],
            ["gradeCode", "등급"],
            ["employeeType", "직원구분"],
          ] as const
        ).map(([key, label]) => (
          <label key={key}>
            {label}
            <select
              data-testid={`calendar-filter-${key}`}
              value={draft[key]}
              onChange={(e) => setDraft({ ...draft, [key]: e.target.value })}
            >
              <option value="">전체</option>
              {options[key]?.map((o) => (
                <option key={o.code} value={o.code}>
                  {o.name}
                </option>
              ))}
            </select>
          </label>
        ))}
        <label>
          보기
          <select
            data-testid="calendar-filter-remaining"
            value={String(draft.remainingOnly)}
            onChange={(e) =>
              setDraft({ ...draft, remainingOnly: e.target.value === "true" })
            }
          >
            <option value="false">전체 인력</option>
            <option value="true">남는 MM이 있는 사람만</option>
          </select>
        </label>
      </FilterBar>
      {optionsError != null && (
        <CalendarError
          error={optionsError}
          retry={() => setReload((n) => n + 1)}
        />
      )}
      {error != null && (
        <CalendarError error={error} retry={() => setReload((n) => n + 1)} />
      )}
      <div className="workforce-calendar-toolbar">
        <button
          data-testid="calendar-refresh"
          disabled={loading}
          onClick={() => setReload((n) => n + 1)}
        >
          재조회
        </button>
        <button
          className="workforce-calendar-download"
          data-testid="calendar-download"
          disabled={loading || pending || !data}
          onClick={() => void download()}
        >
          {pending ? "파일 생성 중…" : "엑셀 다운로드"}
        </button>
      </div>
      {notice && <p role="status">{notice}</p>}
      {loading && <p role="status">달력을 불러오는 중입니다.</p>}
      {data && (
        <>
          <div className="sicost-table-viewport">
            <table className="sicost-table workforce-calendar-table">
              <thead>
                <tr>
                  <th>이름</th>
                  <th>직무</th>
                  {Array.from({ length: 12 }, (_, i) => (
                    <th key={i}>{i + 1}월</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.items.map((person) => (
                  <tr
                    key={person.developerId}
                    data-testid={`calendar-person-${person.developerId}`}
                  >
                    <th scope="row">
                      <button
                        data-testid={`calendar-developer-${person.developerId}`}
                        onClick={() => setSelected(person.developerId)}
                      >
                        {person.name}
                      </button>
                    </th>
                    <td>{lookup("jobCode", person.jobCode)}</td>
                    {person.cells.map((cell) => (
                      <td key={cell.month}>
                        <WorkforceCalendarCell
                          cell={cell}
                          personId={person.developerId}
                        />
                      </td>
                    ))}
                  </tr>
                ))}
                {!data.items.length && (
                  <tr>
                    <td colSpan={14}>조회된 인력이 없습니다.</td>
                  </tr>
                )}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row" colSpan={2}>
                    비는인원
                  </th>
                  {data.monthTotals.map((m) => (
                    <td key={m.month}>{m.availablePeople ?? "–"}</td>
                  ))}
                </tr>
                <tr>
                  <th scope="row" colSpan={2}>
                    남는 MM
                  </th>
                  {data.monthTotals.map((m) => (
                    <td key={m.month}>{decimal(m.remainingMm)}</td>
                  ))}
                </tr>
              </tfoot>
            </table>
          </div>
          <nav aria-label="달력 페이지">
            <button
              data-testid="calendar-previous"
              disabled={page <= 1}
              onClick={() => setPage((p) => p - 1)}
            >
              이전
            </button>
            <span>
              {data.page} · 전체 {data.total}명
            </span>
            <button
              data-testid="calendar-next"
              disabled={page * size >= data.total}
              onClick={() => setPage((p) => p + 1)}
            >
              다음
            </button>
            <label>
              한 화면 건수
              <select
                data-testid="calendar-size"
                value={size}
                onChange={(e) => {
                  setSize(Number(e.target.value) as 20 | 50 | 100);
                  setPage(1);
                }}
              >
                {[20, 50, 100].map((n) => (
                  <option key={n} value={n}>
                    {n}건씩
                  </option>
                ))}
              </select>
            </label>
          </nav>
        </>
      )}
      {selected && (
        <DeveloperDrawer id={selected} onClose={() => setSelected(undefined)} />
      )}
    </section>
  );
}
export function WorkforceCalendarCell({
  cell,
  personId = "person",
}: {
  cell: api.CalendarCell;
  personId?: string;
}) {
  const [open, setOpen] = useState(false);
  const pre = cell.projects.some((p) => p.preContract);
  const label =
    cell.status === "ASSIGNED"
      ? `${pre ? "⚠ 미계약 " : cell.leavingSoon ? "철수예정 " : "투입중 "}${decimal(cell.actualMm)} MM${cell.assignmentCount >= 2 ? ` · ${cell.assignmentCount}건` : ""}${cell.overAssigned ? " · 1.0 초과" : ""}`
      : cell.status === "TENTATIVE"
        ? `${cell.tentativeProbability ?? "–"}%`
        : statuses[cell.status];
  return (
    <div
      className={`workforce-calendar-cell workforce-calendar-${cell.status.toLowerCase()}${cell.leavingSoon ? " workforce-calendar-ending" : ""}${cell.overAssigned ? " workforce-calendar-over" : ""}`}
    >
      <button
        data-testid={`calendar-cell-${personId.toLowerCase()}-${cell.month}`}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        onBlur={() => setOpen(false)}
      >
        {label}
      </button>
      {open && (
        <div role="note" className="workforce-calendar-help">
          {cell.month} · {statuses[cell.status]} · 전체합{" "}
          {decimal(cell.actualMm)} MM
          {cell.projects.map((p, i) => (
            <p key={`${p.projectId}:${i}`}>
              {p.projectId}: {decimal(p.actualMm)} MM · 종료 {p.endDate} · EXT{" "}
              {p.extensionPossibility ?? "미정"}
            </p>
          ))}
          {cell.tentativeAssignments.map((t) => (
            <p key={t.id}>
              가배정 {t.opportunityId} · {t.startMonth} ~ 자리 종료월 ·{" "}
              {cell.tentativeProbability ?? "–"}%
            </p>
          ))}
        </div>
      )}
    </div>
  );
}
function CalendarError({ error, retry }: { error: unknown; retry(): void }) {
  return (
    <ErrorBanner
      message={error instanceof Error ? error.message : "조회하지 못했습니다."}
      requestId={error instanceof ApiError ? error.body.requestId : undefined}
      onRetry={retry}
    />
  );
}
