import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { DataTable, type Column } from "../../shared/DataTable";
import { StatusBadge } from "../../shared/Feedback";
import { FilterBar } from "../../shared/FilterBar";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { AssignmentDrawer } from "../assignmentsops/AssignmentDrawer";
import { DeveloperDrawer } from "../developers/DeveloperDrawer";
import {
  getPeriodContext,
  getSharedCodes,
  getSharedPreferences,
  type PeriodContext,
  type PeriodValue,
} from "../shared/sharedApi";
import { useSharedScreen } from "../shared/SharedScreenContext";
import { WithdrawalError, WithdrawalNextPanel } from "./WithdrawalNextPanel";
import * as api from "./withdrawalsApi";
import "./withdrawals.css";

export function WithdrawalListScreen() {
  const [year, setYear] = useState<number>();
  const [error, setError] = useState<unknown>();
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let live = true;
    void Promise.all([getPeriodContext(), getSharedPreferences()])
      .then(([ctx, prefs]) => {
        if (!live) return;
        const saved = prefs.items.find(
          (p) => p.type === "PERIOD" && p.key === "UI-05",
        )?.value as { type?: string; value?: string } | undefined;
        const current = Number(ctx.currentMonth.slice(0, 4));
        const selected =
          saved?.type === "YEAR" && /^[0-9]{4}$/.test(saved.value ?? "")
            ? Number(saved?.value)
            : current;
        setYear(
          (old) =>
            old ??
            Math.max(
              Number(ctx.cashBaseMonth.slice(0, 4)),
              Math.min(current + 1, selected),
            ),
        );
        setError(undefined);
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, [retry]);
  useEffect(() => {
    const changed = (event: Event) => {
      const d = (
        event as CustomEvent<{
          screenId: string;
          value: PeriodValue;
          context: PeriodContext;
        }>
      ).detail;
      if (d?.screenId === "UI-05" && d.value.type === "YEAR")
        setYear(Number(d.value.value));
    };
    window.addEventListener("sicost:screen-period-changed", changed);
    return () =>
      window.removeEventListener("sicost:screen-period-changed", changed);
  }, []);
  return (
    <section data-testid="withdrawal-list-screen" data-screen-id="SCR-UI-05">
      {error ? (
        <WithdrawalError error={error} retry={() => setRetry((n) => n + 1)} />
      ) : year ? (
        <WithdrawalContent key={year} year={year} />
      ) : (
        <p role="status">조회 기간을 불러오는 중입니다.</p>
      )}
    </section>
  );
}
const defaults = () => ({
  period: "ALL" as api.WithdrawalQuery["period"],
  extension: "ALL" as api.WithdrawalQuery["extension"],
  job: "",
});
export function WithdrawalContent({ year }: { year: number }) {
  const shared = useSharedScreen("UI-05");
  const [draft, setDraft] = useState(defaults);
  const [query, setQuery] = useState<api.WithdrawalQuery>({
    year,
    ...defaults(),
    job: undefined,
    page: 1,
    size: shared.pageSize ?? 50,
  });
  const [data, setData] = useState<api.WithdrawalsData>();
  const [jobs, setJobs] = useState<{ code: string; name: string }[]>([]);
  const [error, setError] = useState<unknown>();
  const [optionsError, setOptionsError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [dense, setDense] = useState(shared.dense ?? false);
  const [assignment, setAssignment] = useState<string>();
  const [developer, setDeveloper] = useState<string>();
  const [panel, setPanel] = useState<{
    row: api.WithdrawalRow;
    initial: Promise<api.NextPositionData>;
  }>();
  const [rowError, setRowError] = useState<{ id: string; error: unknown }>();
  const [notice, setNotice] = useState("");
  const generation = useRef(0);
  const { pending, message, commit } = useCommitRefresh();
  const load = useCallback(async () => {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const value = await api.getWithdrawals(query);
      if (version === generation.current) setData(value);
    } catch (e) {
      if (version === generation.current) setError(e);
      throw e;
    } finally {
      if (version === generation.current) setLoading(false);
    }
  }, [query]);
  useEffect(() => {
    void load().catch(() => undefined);
    return () => {
      generation.current++;
    };
  }, [load]);
  useEffect(() => {
    let live = true;
    void getSharedCodes("JOB", true)
      .then((c) => {
        if (live) {
          setJobs(c.items);
          setOptionsError(undefined);
        }
      })
      .catch((e) => {
        if (live) setOptionsError(e);
      });
    return () => {
      live = false;
    };
  }, []);
  useEffect(() => {
    const refresh = () => void load().catch(() => undefined);
    const names = [
      "sicost:assignments-committed",
      "sicost:sales-committed",
      "sicost:staffing-committed",
    ];
    names.forEach((name) => window.addEventListener(name, refresh));
    return () =>
      names.forEach((name) => window.removeEventListener(name, refresh));
  }, [load]);
  const apply = (filters = draft) => {
    setQuery((old) => ({
      ...old,
      ...filters,
      job: filters.job || undefined,
      page: 1,
    }));
    setNotice("");
  };
  const openNext = (row: api.WithdrawalRow) => {
    const initial = api.createNextPositionRecommendation(row, year);
    void initial.catch(() => undefined);
    setPanel({ row, initial });
  };
  async function extension(row: api.WithdrawalRow, value: string) {
    setRowError(undefined);
    setNotice("");
    try {
      await commit(async () => {
        await api.saveWithdrawalExtensionPossibility(
          row,
          year,
          value === "" ? null : (value as "H" | "M" | "L"),
        );
        setNotice("연장가능성이 반영되었습니다.");
        api.withdrawalCommitted();
      }, load);
    } catch (e) {
      setRowError({ id: row.assignmentId, error: e });
    }
  }
  const columns: Column<api.WithdrawalRow>[] = [
    {
      id: "name",
      label: "이름",
      render: (row) => (
        <>
          <button
            className="withdrawal-link"
            data-testid={`withdrawal-person-${row.assignmentId.toLowerCase()}`}
            onClick={(e) => {
              e.stopPropagation();
              setDeveloper(row.developer.detailId);
            }}
          >
            {row.developer.name}
          </button>
          <small className="withdrawal-sub">
            {row.developer.managementNumber}
          </small>
        </>
      ),
    },
    {
      id: "project",
      label: "프로젝트",
      render: (row) => (
        <Link
          data-testid={`withdrawal-project-${row.assignmentId.toLowerCase()}`}
          onClick={(e) => e.stopPropagation()}
          to={`/UI-01?${new URLSearchParams({ projectId: row.projectId, year: String(year) })}`}
        >
          {row.projectName}
        </Link>
      ),
    },
    {
      id: "job",
      label: "직무",
      render: (row) =>
        jobs.find((j) => j.code === row.jobCode)?.name ?? row.jobCode,
    },
    { id: "date", label: "철수일", render: (row) => row.withdrawalDate },
    {
      id: "days",
      label: "남은 일수",
      render: (row) => (
        <StatusBadge
          tone={
            row.remainingDays < 0
              ? "lock"
              : row.remainingDays <= 30
                ? "bad"
                : row.remainingDays <= 60
                  ? "warn"
                  : "neutral"
          }
        >
          {row.remainingDays < 0 ? "철수 완료" : `D-${row.remainingDays}`}
        </StatusBadge>
      ),
    },
    {
      id: "history",
      label: "연장·조기 철수",
      render: (row) => (
        <>
          연장 {row.extensionCount}회
          {row.earlyWithdrawal && (
            <small className="withdrawal-sub">조기철수 이력</small>
          )}
        </>
      ),
    },
    {
      id: "extension",
      label: "연장 가능성",
      render: (row) => (
        <div
          onClick={(e) => e.stopPropagation()}
          onKeyDown={(e) => e.stopPropagation()}
        >
          <select
            aria-label={`${row.developer.name} 연장 가능성`}
            data-testid={`withdrawal-extension-${row.assignmentId.toLowerCase()}`}
            value={row.extensionPossibility ?? ""}
            disabled={!row.extensionEditable || pending || loading}
            onChange={(e) => void extension(row, e.target.value)}
          >
            <option value="">미정</option>
            <option value="H">높음</option>
            <option value="M">보통</option>
            <option value="L">낮음</option>
          </select>
          {rowError?.id === row.assignmentId && (
            <WithdrawalError
              error={rowError.error}
              retry={() => void load().catch(() => undefined)}
            />
          )}
        </div>
      ),
    },
    {
      id: "next",
      label: "다음 배정",
      render: (row) => (
        <div
          onClick={(e) => e.stopPropagation()}
          onKeyDown={(e) => e.stopPropagation()}
        >
          {row.remainingDays < 0 ? (
            "–"
          ) : row.nextAssignments.length ? (
            <>
              <NextLink next={row.nextAssignments[0]} id={row.assignmentId} />
              {row.nextAssignments.length > 1 && (
                <details>
                  <summary
                    data-testid={`withdrawal-next-all-${row.assignmentId.toLowerCase()}`}
                  >
                    전체 다음 배정 {row.nextAssignments.length}건
                  </summary>
                  {row.nextAssignments.map((next, index) => (
                    <div
                      key={`${next.kind}-${next.assignmentId ?? next.tentativeAssignmentId}`}
                    >
                      <NextLink
                        next={next}
                        id={`${row.assignmentId}-${index}`}
                      />
                    </div>
                  ))}
                </details>
              )}
              {row.canChangeTentative && (
                <button
                  data-testid={`withdrawal-change-${row.assignmentId.toLowerCase()}`}
                  disabled={pending || loading}
                  onClick={() => openNext(row)}
                >
                  바꾸기
                </button>
              )}
            </>
          ) : row.canRecommend && row.recommendationCount > 0 ? (
            <button
              data-testid={`withdrawal-recommend-${row.assignmentId.toLowerCase()}`}
              disabled={pending || loading}
              onClick={() => openNext(row)}
            >
              추천 {row.recommendationCount}곳
            </button>
          ) : row.canRecommend ? (
            "없음 · 맞는 자리 없음"
          ) : (
            "없음"
          )}
        </div>
      ),
    },
  ];
  return (
    <div className="withdrawal-screen" data-testid="withdrawal-content">
      <div className="withdrawal-toolbar">
        <button
          className="withdrawal-download"
          data-testid="withdrawal-download"
          disabled={loading || pending || !data || Boolean(error)}
          onClick={() => {
            setNotice("");
            void api
              .downloadWithdrawals(query)
              .then(() => setNotice("다운로드했습니다."))
              .catch(setError);
          }}
        >
          엑셀 다운로드
        </button>
      </div>
      {data?.guidance.visible && (
        <div
          className="sicost-banner sicost-tone-info"
          data-testid="withdrawal-guidance"
        >
          <span>{data.guidance.message}</span>
          <button
            data-testid="withdrawal-guidance-recommend"
            onClick={() => {
              const filters = { ...defaults(), period: "SOON" as const };
              setDraft(filters);
              apply(filters);
            }}
          >
            추천
          </button>
        </div>
      )}
      <p className="withdrawal-help">
        연장가능성 변경은 인력 운영 보드/수요 예측에 반영됩니다. 연도 경계의
        다른 연도 자료는 연도를 바꿔 조회하세요.
      </p>
      {data && year < Number(data.serverDate.slice(0, 4)) && (
        <p className="sicost-banner sicost-tone-lock">
          지난 연도는 조회만 할 수 있습니다.
        </p>
      )}
      <FilterBar
        testId="withdrawal-filters"
        searchTestId="withdrawal-search"
        resetTestId="withdrawal-reset"
        onSubmit={() => apply()}
        onReset={() => {
          const filters = defaults();
          setDraft(filters);
          apply(filters);
        }}
      >
        <label className="sicost-field">
          기간
          <select
            data-testid="withdrawal-period"
            value={draft.period}
            onChange={(e) =>
              setDraft((v) => ({
                ...v,
                period: e.target.value as api.WithdrawalQuery["period"],
              }))
            }
          >
            <option value="ALL">전체</option>
            <option value="SOON">3개월 안</option>
            <option value="LATER">3개월 뒤</option>
            <option value="PAST">이미 철수</option>
          </select>
        </label>
        <label className="sicost-field">
          연장 가능성
          <select
            data-testid="withdrawal-extension-filter"
            value={draft.extension}
            onChange={(e) =>
              setDraft((v) => ({
                ...v,
                extension: e.target.value as api.WithdrawalQuery["extension"],
              }))
            }
          >
            <option value="ALL">전체</option>
            <option value="H">높음</option>
            <option value="M">보통</option>
            <option value="L">낮음</option>
            <option value="UNSET">미정</option>
          </select>
        </label>
        <label className="sicost-field">
          직무
          <select
            data-testid="withdrawal-job"
            value={draft.job}
            onChange={(e) => setDraft((v) => ({ ...v, job: e.target.value }))}
          >
            <option value="">전체</option>
            {jobs.map((j) => (
              <option key={j.code} value={j.code}>
                {j.name}
              </option>
            ))}
          </select>
        </label>
      </FilterBar>
      <WithdrawalError error={optionsError} />
      {loading && <p role="status">철수 조회 중</p>}
      <WithdrawalError
        error={error}
        retry={() => void load().catch(() => undefined)}
      />
      {(notice || message) && (
        <p role="status">
          {message?.includes("완료됐지만") ? message : notice || message}
        </p>
      )}
      <div className="withdrawal-toolbar">
        <span>필터 전체 {data?.totalCount ?? "–"}건</span>
        <button
          data-testid="withdrawal-density"
          aria-pressed={dense}
          onClick={() => setDense((v) => !v)}
        >
          {dense ? "넓게 보기" : "좁게 보기"}
        </button>
      </div>
      <DataTable
        testId="withdrawal-table"
        rows={error ? [] : (data?.items ?? [])}
        columns={columns}
        rowKey={(row) => row.assignmentId}
        loading={loading}
        dense={dense}
        empty="조건에 맞는 철수 배정이 없습니다."
        page={query.page}
        size={query.size}
        total={data?.totalCount ?? 0}
        onPageChange={(page) => setQuery((v) => ({ ...v, page }))}
        onSizeChange={(size) => {
          setQuery((v) => ({ ...v, size, page: 1 }));
          void shared.savePageSize?.(size);
        }}
        onRowAction={(row) => setAssignment(row.assignmentId)}
      />
      {assignment && data && (
        <AssignmentDrawer
          id={assignment}
          year={year}
          readonly={year < Number(data.serverDate.slice(0, 4))}
          onClose={() => setAssignment(undefined)}
          onChange={() => void load().catch(() => undefined)}
        />
      )}
      {developer && (
        <DeveloperDrawer
          id={developer}
          onClose={() => setDeveloper(undefined)}
        />
      )}
      {panel && (
        <WithdrawalNextPanel
          key={panel.row.assignmentId}
          row={panel.row}
          year={year}
          initial={panel.initial}
          onClose={() => setPanel(undefined)}
          onChanged={load}
        />
      )}
    </div>
  );
}
function NextLink({ next, id }: { next: api.WithdrawalNext; id: string }) {
  const kind = {
    CONFIRMED: "확정",
    CANDIDATE: "배정 후보",
    TENTATIVE: "가배정",
  }[next.kind];
  const path =
    next.kind === "TENTATIVE"
      ? `/UI-29?${new URLSearchParams({ opportunityId: next.opportunityId ?? "" })}`
      : next.kind === "CANDIDATE"
        ? "/UI-12"
        : `/UI-01?${new URLSearchParams({ projectId: next.projectId ?? "" })}`;
  return (
    <span>
      <StatusBadge
        tone={
          next.kind === "CONFIRMED"
            ? "ok"
            : next.kind === "CANDIDATE"
              ? "warn"
              : "info"
        }
      >
        {kind}
      </StatusBadge>{" "}
      <Link data-testid={`withdrawal-next-link-${id.toLowerCase()}`} to={path}>
        {next.name}
      </Link>
      <small className="withdrawal-sub">
        {next.startDate ?? next.startMonth}
      </small>
    </span>
  );
}
