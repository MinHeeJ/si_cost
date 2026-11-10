import { useEffect, useRef, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatDateTime } from "../../shared/format";
import { CODE_GROUPS_COMMITTED } from "../codegroups/codeGroupsApi";
import { CODE_MAPPINGS_COMMITTED } from "../codemappings/codeMappingsApi";
import { OPERATING_STANDARDS_COMMITTED } from "../operatingstandards/operatingStandardsApi";
import {
  getCommonCodeHistory,
  type CommonCodeHistory,
  type HistoryPage,
  type HistoryQuery,
} from "./sharedApi";
const targetNames: Record<string, string> = {
  GROUP: "그룹",
  CODE: "코드",
  VALUE_CODE: "값코드",
  OPER_STD: "운영 기준값",
  CODE_MAP: "표기 대응",
};
const typeNames: Record<string, string> = {
  NEW: "등록",
  UPD: "변경",
  STOP: "중지",
  RESUME: "재개",
  DEL: "삭제",
};
function HistoryValue({ value }: { value: string | null }) {
  if (value === null) return <span aria-label="값 없음">—</span>;
  return value.length > 80 ? (
    <details className="sicost-history-value">
      <summary>긴 값 펼침</summary>
      <pre>{value}</pre>
    </details>
  ) : (
    <span className="sicost-history-value">{value}</span>
  );
}
const columns: Column<CommonCodeHistory>[] = [
  {
    id: "occurredAt",
    label: "일시",
    render: (row) => formatDateTime(row.occurredAt),
  },
  {
    id: "target",
    label: "대상",
    render: (row) => (
      <StatusBadge tone="neutral">
        {targetNames[row.target] ?? row.target}
      </StatusBadge>
    ),
  },
  { id: "groupId", label: "그룹", render: (row) => row.groupId ?? "—" },
  { id: "entityKey", label: "코드 키", render: (row) => row.entityKey },
  {
    id: "type",
    label: "유형",
    render: (row) => (
      <StatusBadge
        tone={
          row.type === "DEL"
            ? "bad"
            : row.type === "STOP"
              ? "lock"
              : row.type === "NEW" || row.type === "RESUME"
                ? "ok"
                : "info"
        }
      >
        {typeNames[row.type] ?? row.type}
      </StatusBadge>
    ),
  },
  { id: "item", label: "항목", render: (row) => row.item },
  {
    id: "before",
    label: "변경 전",
    render: (row) => <HistoryValue value={row.before} />,
  },
  {
    id: "after",
    label: "변경 후",
    render: (row) => <HistoryValue value={row.after} />,
  },
  {
    id: "actor",
    label: "처리자",
    render: (row) =>
      row.actor === "SYSTEM"
        ? "시스템"
        : row.actor === "admin"
          ? "임시 관리자"
          : row.actor,
  },
];
export function ChangeHistoryPanel({
  pageSize = 50,
  dense = false,
  groupOptions = [],
  onPageSizeChange,
  columnOrder,
  hiddenColumns = [],
  onColumnsChange,
  onDensityChange,
}: {
  pageSize?: 20 | 50 | 100;
  dense?: boolean;
  groupOptions?: { groupId: string; name: string }[];
  onPageSizeChange?(size: 20 | 50 | 100): Promise<void>;
  columnOrder?: string[];
  hiddenColumns?: string[];
  onColumnsChange?(): void;
  onDensityChange?(): void;
}) {
  const [query, setQuery] = useState<HistoryQuery>({ page: 1, size: pageSize }),
    [data, setData] = useState<HistoryPage | null>(null);
  const [targetType, setTargetType] = useState(""),
    [groupId, setGroupId] = useState("");
  const [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [refresh, setRefresh] = useState(0);
  const read = useRef(0);
  useEffect(() => {
    const refresh = () => setRefresh((n) => n + 1);
    const events = [
      CODE_GROUPS_COMMITTED,
      CODE_MAPPINGS_COMMITTED,
      OPERATING_STANDARDS_COMMITTED,
    ];
    events.forEach((event) => window.addEventListener(event, refresh));
    return () =>
      events.forEach((event) => window.removeEventListener(event, refresh));
  }, []);
  useEffect(() => {
    setQuery((q) =>
      q.size === pageSize ? q : { ...q, page: 1, size: pageSize },
    );
  }, [pageSize]);
  useEffect(() => {
    const id = ++read.current;
    setLoading(true);
    setError("");
    void getCommonCodeHistory(query)
      .then((result) => {
        if (id === read.current) setData(result);
      })
      .catch((e: unknown) => {
        if (id === read.current)
          setError(
            e instanceof ApiError
              ? `${e.message} 문의 번호: ${e.body.requestId}`
              : e instanceof Error
                ? e.message
                : "이력 조회 실패",
          );
      })
      .finally(() => {
        if (id === read.current) setLoading(false);
      });
    return () => {
      read.current++;
    };
  }, [query, refresh]);
  const ordered =
    columnOrder &&
    columnOrder.length === columns.length &&
    new Set(columnOrder).size === columns.length &&
    columnOrder.every((id) => columns.some((c) => c.id === id))
      ? columnOrder.map((id) => columns.find((c) => c.id === id)!)
      : columns;
  const sizeChange = async (size: 20 | 50 | 100) => {
    try {
      await onPageSizeChange?.(size);
      setQuery((q) => ({ ...q, size, page: 1 }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "목록 설정 저장 실패");
    }
  };
  return (
    <section aria-label="변경 이력" className="sicost-history">
      <form
        className="sicost-history-filters"
        onSubmit={(event) => {
          event.preventDefault();
          setQuery((q) => ({
            ...q,
            targetType: targetType || undefined,
            groupId: groupId || undefined,
            page: 1,
          }));
        }}
      >
        <label>
          대상{" "}
          <select
            aria-label="이력 대상"
            value={targetType}
            onChange={(e) => setTargetType(e.target.value)}
          >
            <option value="">전체</option>
            {Object.entries(targetNames).map(([key, name]) => (
              <option key={key} value={key}>
                {name}
              </option>
            ))}
          </select>
        </label>
        <label>
          그룹{" "}
          <select
            aria-label="이력 그룹"
            value={groupId}
            onChange={(e) => setGroupId(e.target.value)}
          >
            <option value="">전체</option>
            {groupOptions.map((g) => (
              <option key={g.groupId} value={g.groupId}>
                {g.name} · {g.groupId}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="sicost-primary" disabled={loading}>
          조회
        </button>
      </form>
      <div className="sicost-list-toolbar">
        <h2>변경 이력</h2>
        <div>
          {onColumnsChange && (
            <button onClick={onColumnsChange}>열 설정</button>
          )}
          {onDensityChange && (
            <button onClick={onDensityChange} aria-pressed={dense}>
              목록 밀도
            </button>
          )}
        </div>
      </div>
      {error && (
        <ErrorBanner message={error} onRetry={() => setRefresh((n) => n + 1)} />
      )}
      {!error && (
        <DataTable
          rows={error ? [] : (data?.items ?? [])}
          rowKey={(row) => row.histId}
          columns={ordered.filter(
            (c) =>
              ["occurredAt", "target"].includes(c.id) ||
              !hiddenColumns.includes(c.id),
          )}
          loading={loading}
          empty="조회된 변경 이력이 없습니다."
          dense={dense}
          page={query.page}
          size={query.size}
          total={data?.total ?? 0}
          onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
          onSizeChange={(size) => void sizeChange(size)}
        />
      )}
    </section>
  );
}
