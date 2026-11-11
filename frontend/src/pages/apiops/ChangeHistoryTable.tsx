import { useEffect, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge, type Tone } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  getMasterHistory,
  type MasterHistoryEntry,
  type MasterHistoryPage,
  type MasterHistoryTarget,
} from "./masterHistoryApi";

type Props = {
  targetType: MasterHistoryTarget;
  targetId: string;
  refreshKey?: string | number;
  dense?: boolean;
};
const types: Record<
  MasterHistoryEntry["changeType"],
  { label: string; tone: Tone }
> = {
  NEW: { label: "등록", tone: "ok" },
  UPD: { label: "변경", tone: "info" },
  STOP: { label: "중지", tone: "lock" },
  RESUME: { label: "재개", tone: "ok" },
};
const columns: Column<MasterHistoryEntry>[] = [
  { id: "createdAt", label: "일시", render: (row) => row.createdAt },
  { id: "targetKey", label: "대상", render: (row) => row.targetKey },
  {
    id: "changeType",
    label: "유형",
    render: (row) => (
      <StatusBadge tone={types[row.changeType].tone}>
        {types[row.changeType].label}
      </StatusBadge>
    ),
  },
  { id: "itemName", label: "항목", render: (row) => row.itemName },
  ...(["beforeValue", "afterValue", "reason"] as const).map(
    (id): Column<MasterHistoryEntry> => ({
      id,
      label:
        id === "beforeValue"
          ? "변경 전"
          : id === "afterValue"
            ? "변경 후"
            : "사유",
      render: (row) =>
        row[id] === null ? (
          "–"
        ) : row[id]!.length > 80 ? (
          <details className="sicost-history-value">
            <summary data-testid={`history-${row.historyId}-${id}-expand`}>
              긴 값 펼침
            </summary>
            <pre>{row[id]}</pre>
          </details>
        ) : (
          <span className="sicost-history-value">{row[id]}</span>
        ),
    }),
  ),
  {
    id: "actorName",
    label: "처리자",
    render: (row) => `${row.actorName} (${row.actorId})`,
  },
];

export function ChangeHistoryTable(props: Props) {
  return (
    <MasterHistoryContent
      key={`${props.targetType}:${props.targetId}`}
      {...props}
    />
  );
}
function MasterHistoryContent({
  targetType,
  targetId,
  refreshKey,
  dense,
}: Props) {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState<20 | 50 | 100>(50);
  const [data, setData] = useState<MasterHistoryPage>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    void getMasterHistory(targetType, targetId, page, size, controller.signal)
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
  }, [targetType, targetId, page, size, refreshKey, retry]);
  return (
    <section
      aria-label="변경 이력"
      data-testid="master-history-table"
      className="sicost-history"
    >
      <div className="sicost-list-toolbar">
        <h2>변경 이력</h2>
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
          rows={data?.items ?? []}
          columns={columns}
          rowKey={(row) => row.historyId}
          loading={loading}
          empty="조회된 변경 이력이 없습니다."
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
    </section>
  );
}
