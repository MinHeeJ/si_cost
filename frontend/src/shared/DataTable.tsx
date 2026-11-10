import type { Key, ReactNode } from "react";
import { ErrorBanner } from "./Feedback";
import { Icon } from "./Icon";

export type Column<T> = {
  id: string;
  label: string;
  render(row: T): ReactNode;
  align?: "left" | "right" | "center";
};
export type DataTableProps<T> = {
  rows: readonly T[];
  columns: readonly Column<T>[];
  rowKey(row: T): Key;
  loading?: boolean;
  empty?: string;
  error?: string;
  onRetry?: () => void;
  page?: number;
  size?: 20 | 50 | 100;
  total?: number;
  onPageChange?(page: number): void;
  onSizeChange?(size: 20 | 50 | 100): void;
  selectedKeys?: readonly Key[];
  onSelectionChange?(keys: Key[]): void;
  onRowAction?(row: T): void;
  onColumnsChange?(): void;
  dense?: boolean;
};
export function DataTable<T>({
  rows,
  columns,
  rowKey,
  loading,
  empty = "조회된 자료가 없습니다.",
  error,
  onRetry,
  page,
  size = 50,
  total = rows.length,
  onPageChange,
  onSizeChange,
  selectedKeys,
  onSelectionChange,
  onRowAction,
  onColumnsChange,
  dense,
}: DataTableProps<T>) {
  if (error) return <ErrorBanner message={error} onRetry={onRetry} />;
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <div className="sicost-grid" aria-busy={Boolean(loading)}>
      {onColumnsChange && (
        <button
          type="button"
          className="sicost-columns-action"
          onClick={onColumnsChange}
        >
          열 설정
        </button>
      )}
      <div className="sicost-table-viewport">
        <table className={dense ? "sicost-table sicost-dense" : "sicost-table"}>
          <thead>
            <tr>
              {onSelectionChange && <th scope="col">선택</th>}
              {columns.map((column) => (
                <th
                  scope="col"
                  key={column.id}
                  style={{ textAlign: column.align }}
                >
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={columns.length + (onSelectionChange ? 1 : 0)}>
                  <div
                    role="status"
                    className="sicost-empty sicost-loading-state"
                  >
                    불러오는 중입니다.
                    <div className="sicost-loading-lines" aria-hidden="true">
                      <i />
                      <i />
                      <i />
                    </div>
                  </div>
                </td>
              </tr>
            ) : !rows.length ? (
              <tr>
                <td colSpan={columns.length + (onSelectionChange ? 1 : 0)}>
                  <div className="sicost-empty sicost-empty-state">
                    <Icon name="search" size={26} />
                    <span>{empty}</span>
                  </div>
                </td>
              </tr>
            ) : (
              rows.map((row) => {
                const key = rowKey(row);
                return (
                  <tr
                    key={key}
                    aria-selected={
                      onSelectionChange
                        ? (selectedKeys?.includes(key) ?? false)
                        : undefined
                    }
                    tabIndex={onRowAction ? 0 : undefined}
                    onClick={() => onRowAction?.(row)}
                    onKeyDown={(event) => {
                      if (
                        event.key === "Enter" &&
                        event.target === event.currentTarget
                      )
                        onRowAction?.(row);
                    }}
                  >
                    {onSelectionChange && (
                      <td>
                        <input
                          type="checkbox"
                          aria-label={`${String(key)} 선택`}
                          checked={selectedKeys?.includes(key) ?? false}
                          onClick={(event) => event.stopPropagation()}
                          onChange={(event) =>
                            onSelectionChange(
                              event.target.checked
                                ? [...(selectedKeys ?? []), key]
                                : (selectedKeys ?? []).filter(
                                    (item) => item !== key,
                                  ),
                            )
                          }
                        />
                      </td>
                    )}
                    {columns.map((column) => (
                      <td
                        key={column.id}
                        data-column={column.id}
                        style={{ textAlign: column.align }}
                      >
                        {column.render(row)}
                      </td>
                    ))}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
      {page !== undefined && (
        <nav aria-label="페이지" className="sicost-pagination">
          <span>
            {total ? (page - 1) * size + 1 : 0}-{Math.min(page * size, total)} /{" "}
            {total}건
          </span>
          <div>
            <button
              type="button"
              aria-label="처음 페이지"
              disabled={loading || page <= 1}
              onClick={() => onPageChange?.(1)}
            >
              «
            </button>
            <button
              type="button"
              aria-label="이전 페이지"
              disabled={loading || page <= 1}
              onClick={() => onPageChange?.(page - 1)}
            >
              ‹
            </button>
            <span aria-current="page">{page}</span>
            <button
              type="button"
              aria-label="다음 페이지"
              disabled={loading || page >= pages}
              onClick={() => onPageChange?.(page + 1)}
            >
              ›
            </button>
            <button
              type="button"
              aria-label="마지막 페이지"
              disabled={loading || page >= pages}
              onClick={() => onPageChange?.(pages)}
            >
              »
            </button>
          </div>
          <select
            aria-label="한 화면 건수"
            value={size}
            disabled={loading}
            onChange={(event) =>
              onSizeChange?.(Number(event.target.value) as 20 | 50 | 100)
            }
          >
            {[20, 50, 100].map((value) => (
              <option key={value} value={value}>
                {value}건씩
              </option>
            ))}
          </select>
        </nav>
      )}
    </div>
  );
}
