import { useEffect, useRef, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge, type Tone } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  getFinancialHistory,
  type FinancialHistoryEntry,
  type FinancialHistoryPage,
  type FinancialHistoryScope,
} from "./financialReadApi";
import "./financialRead.css";

type Props = { month: string; refreshKey?: string | number; dense?: boolean };
const types: Record<
  FinancialHistoryEntry["changeType"],
  { label: string; tone: Tone }
> = {
  NEW: { label: "등록", tone: "ok" },
  UPD: { label: "변경", tone: "info" },
  DEL: { label: "삭제", tone: "bad" },
  STOP: { label: "중지", tone: "lock" },
  RESUME: { label: "재개", tone: "ok" },
};
const columns: Column<FinancialHistoryEntry>[] = [
  {
    id: "occurredAt",
    label: "일시",
    render: (row) => row.occurredAt.replace("T", " ").slice(0, 19),
  },
  { id: "targetTable", label: "대상 테이블", render: (row) => row.targetTable },
  { id: "targetKey", label: "대상 키", render: (row) => row.targetKey },
  {
    id: "changeType",
    label: "유형",
    render: (row) => (
      <StatusBadge tone={types[row.changeType].tone}>
        {types[row.changeType].label}
      </StatusBadge>
    ),
  },
  { id: "field", label: "변경 항목", render: (row) => row.field },
  ...(["beforeValue", "afterValue", "reason"] as const).map(
    (id): Column<FinancialHistoryEntry> => ({
      id,
      label:
        id === "beforeValue"
          ? "이전 값"
          : id === "afterValue"
            ? "이후 값"
            : "사유",
      render: (row) =>
        row[id] == null ? (
          "–"
        ) : row[id]!.length > 80 ? (
          <details className="sicost-history-value">
            <summary
              data-testid={`financial-history-${row.historyId}-${id}-expand`}
            >
              긴 값 펼침
            </summary>
            <pre>{row[id]}</pre>
          </details>
        ) : (
          <span className="sicost-history-value">{row[id]}</span>
        ),
    }),
  ),
  { id: "userId", label: "처리자", render: (row) => row.userId },
];

export function FinancialHistoryPanel(props: Props) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  return (
    <section
      className="financial-read-panel"
      data-testid="financial-history-panel"
      data-screen-id={
        open ? "SCR-UI-10-HISTORY-OPEN" : "SCR-UI-10-HISTORY-CLOSE"
      }
    >
      <div className="sicost-list-toolbar">
        <h2>변경 이력</h2>
        <button
          ref={trigger}
          type="button"
          data-testid="financial-history-toggle"
          aria-expanded={open}
          aria-controls="financial-history-content"
          onClick={() => setOpen((value) => !value)}
        >
          {open ? "접기" : "변경 이력"}
        </button>
      </div>
      {open && (
        <div id="financial-history-content">
          <FinancialHistoryContent
            key={props.month}
            {...props}
            onClose={() => {
              setOpen(false);
              trigger.current?.focus();
            }}
          />
        </div>
      )}
    </section>
  );
}
function FinancialHistoryContent({
  month,
  refreshKey,
  dense,
  onClose,
}: Props & { onClose(): void }) {
  const [scope, setScope] = useState<FinancialHistoryScope>("MONTH");
  const [page, setPage] = useState(1);
  const [size, setSize] = useState<20 | 50 | 100>(50);
  const [data, setData] = useState<FinancialHistoryPage>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void getFinancialHistory(month, scope, page, size, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) setError(failure);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [month, scope, page, size, refreshKey, retry]);
  return (
    <div data-testid="financial-history-content">
      <div className="sicost-list-toolbar">
        <label>
          범위{" "}
          <select
            data-testid="financial-history-scope"
            value={scope}
            onChange={(event) => {
              setScope(event.target.value as FinancialHistoryScope);
              setPage(1);
            }}
          >
            <option value="MONTH">선택월 원천</option>
            <option value="TAX">관련 세금</option>
            <option value="CARDS">전역 카드</option>
          </select>
        </label>
        <span>{month} · 조회 전용</span>
        <button
          type="button"
          data-testid="financial-history-close"
          onClick={onClose}
        >
          닫기
        </button>
      </div>
      {error != null ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "이력을 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRetry((value) => value + 1)}
        />
      ) : (
        <DataTable
          columns={columns}
          rows={data?.items ?? []}
          rowKey={(row) => row.historyId}
          loading={loading}
          empty="해당 범위의 변경 이력이 없습니다."
          page={page}
          size={size}
          total={data?.total ?? 0}
          dense={dense}
          onPageChange={setPage}
          onSizeChange={(value) => {
            setSize(value);
            setPage(1);
          }}
        />
      )}
    </div>
  );
}
