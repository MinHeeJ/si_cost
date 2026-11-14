import { useEffect, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatDecimal } from "../../shared/format";
import {
  getCandidateShortages,
  type CandidateRead,
  type CandidateShortage,
  type CandidateShortagePage,
  type CandidateShortageQuery,
} from "./candidateMatchingApi";
import "./candidateMatching.css";

// SCR-UI-33: immediately below the existing global candidate KPI summary.
export function CandidateShortagesPanel({
  active = true,
  refreshKey = 0,
  onView,
  jobLabel = (code) => code,
}: {
  active?: boolean;
  refreshKey?: number;
  onView(row: CandidateShortage): void;
  jobLabel?(code: string): string;
}) {
  const [query, setQuery] = useState<CandidateShortageQuery>({
    page: 1,
    size: 50,
  });
  const [result, setResult] = useState<CandidateRead<CandidateShortagePage>>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setResult(undefined);
    void getCandidateShortages(query, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setResult(data);
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [active, refreshKey, query, retry]);
  if (!active) return null;
  const columns: Column<CandidateShortage>[] = [
    {
      id: "jobCode",
      label: "직무",
      sortable: true,
      render: (row) => jobLabel(row.jobCode),
    },
    {
      id: "firstShortageMonth",
      label: "첫 부족월",
      sortable: true,
      render: (row) => row.firstShortageMonth,
    },
    {
      id: "maximumShortage",
      label: "최대 부족",
      sortable: true,
      align: "right",
      render: (row) => `${formatDecimal(row.maximumShortage)}명`,
    },
    {
      id: "candidates",
      label: "그달 말까지 가능 / 전체 활성 후보",
      render: (row) =>
        `${row.availableCandidateCount} / ${row.activeCandidateCount}명`,
    },
    {
      id: "action",
      label: "행동",
      render: (row) => (
        <button
          data-testid={`candidate-shortage-view-${row.jobCode.toLowerCase()}`}
          onClick={() => onView(row)}
        >
          후보 보기
        </button>
      ),
    },
  ];
  return (
    <section
      className="candidate-matching-panel"
      aria-label="부족 직무"
      data-testid="candidate-shortages-panel"
      aria-busy={loading}
    >
      <header>
        <h3>부족 직무</h3>
        <span>다음 달부터 6개월 · 영업 기회 기준(미충원 제외)</span>
      </header>
      {error ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "부족 직무를 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRetry((v) => v + 1)}
        />
      ) : (
        <>
          {result?.issues.map((issue, index) => (
            <p
              key={index}
              role="status"
              className="sicost-banner sicost-tone-warn"
            >
              기준 원천 미확인: {issue.reason} ({issue.field})
            </p>
          ))}
          {loading ? (
            <p role="status">부족 직무를 불러오는 중입니다.</p>
          ) : result &&
            (result.data.items.length > 0 || !result.issues.length) ? (
            <DataTable
              testId="candidate-shortages-table"
              rows={result.data.items}
              columns={columns}
              rowKey={(row) => row.jobCode}
              empty="부족 직무가 없습니다."
              emptyAction={<button type="button" onClick={() => setQuery({ page: 1, size: 50 })}>조건 초기화</button>}
              page={query.page}
              size={query.size}
              total={result.data.total}
              onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
              onSizeChange={(size) =>
                setQuery((q) => ({ ...q, page: 1, size }))
              }
              sort={
                query.sort
                  ? { id: query.sort, direction: query.direction ?? "ASC" }
                  : undefined
              }
              onSortChange={(id) =>
                setQuery((q) => ({
                  ...q,
                  page: 1,
                  sort: id as CandidateShortageQuery["sort"],
                  direction:
                    q.sort === id && q.direction !== "DESC" ? "DESC" : "ASC",
                }))
              }
            />
          ) : null}
        </>
      )}
    </section>
  );
}
