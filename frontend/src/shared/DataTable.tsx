import { Pagination } from "./Pagination";
import { FieldHelp } from "./FieldHelp";
import {
  Fragment,
  useContext,
  useId,
  type CSSProperties,
  type Key,
  type ReactNode,
} from "react";
import { ErrorBanner } from "./Feedback";
import { Icon } from "./Icon";
import {
  PermissionContext,
  ScreenPermissionContext,
} from "./permission/PermissionBoundary";

export type Column<T> = {
  id: string;
  label: ReactNode;
  help?: ReactNode;
  render(row: T): ReactNode;
  align?: "left" | "right" | "center";
  /** true면 열 이름을 눌러 정렬한다(onSortChange 필요). */
  sortable?: boolean;
};
export type DataTableProps<T> = {
  rows: readonly T[];
  columns: readonly Column<T>[];
  rowKey(row: T): Key;
  tableLabel?: string;
  loading?: boolean;
  /** Show a message in the table body while loading. Defaults to skeleton rows. */
  loadingMessage?: string;
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
  /** 지금 정렬 기준. 없으면 화살표를 그리지 않는다. */
  sort?: { id: string; direction: "ASC" | "DESC" };
  /** 열 이름을 눌렀을 때. 같은 열을 다시 누르면 방향을 바꾸는 일은 화면이 한다. */
  onSortChange?(id: string): void;
  /** 0건일 때 빈 안내 아래에 놓을 동작(예: [조건 초기화]). */
  emptyAction?: ReactNode;
  onRowAction?(row: T): void;
  onColumnsChange?(): void;
  dense?: boolean;
  /** Screen-owned state survives work-tab switches. */
  expandableRow?: {
    expandedKeys: readonly Key[];
    onExpandedChange(keys: Key[]): void;
    render(row: T): ReactNode;
    canExpand?(row: T): boolean;
    label?(row: T): string;
  };
  /** Filter-wide server totals, never a reduction of current-page rows/children. */
  footer?: { label: string; render(column: Column<T>): ReactNode };
  /** Fixed pixel widths; offsets follow the currently visible column order. */
  stickyColumns?: readonly { id: string; width: number }[];
  testId?: string;
};
export function DataTable<T>({
  rows,
  columns: suppliedColumns,
  rowKey,
  tableLabel,
  loading,
  loadingMessage,
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
  sort,
  onSortChange,
  emptyAction,
  onRowAction,
  onColumnsChange,
  dense,
  expandableRow,
  footer,
  stickyColumns,
  testId = "data-table",
}: DataTableProps<T>) {
  const instanceId = useId();
  const screen = useContext(ScreenPermissionContext),
    permissions = useContext(PermissionContext);
  const fields = screen ? permissions?.fields?.[screen] : undefined;
  const aliases: Record<string, string> =
    screen === "UI-02"
      ? {
          rate: "monthlyRate",
          pay: "monthlyPay",
          monthlyRate: "monthlyRate",
          monthlyPay: "monthlyPay",
        }
      : screen === "UI-12"
        ? {
            billingUnitPrice: "billingUnitPrice",
            appliedUnitPrice: "appliedUnitPrice",
            defaultUnitPrice: "defaultUnitPrice",
            salesAmount: "salesAmount",
          }
        : {};
  const declared = rows
    .map((row) => row as { availableFields?: string[] })
    .find((row) => Array.isArray(row.availableFields))?.availableFields;
  const columns = suppliedColumns.filter((column) => {
    if (fields && aliases[column.id] && !fields.includes(aliases[column.id]))
      return false;
    if (
      screen === "UI-33" &&
      column.id === "desiredRate" &&
      fields &&
      !fields.includes("desiredRate")
    )
      return false;
    if (permissions?.sensitive === false) {
      const key = (aliases[column.id] ?? column.id)
        .replace(/_/g, "")
        .toLowerCase();
      const exception =
        (["UI-01", "UI-31"].includes(screen ?? "") &&
          [
            "costusage",
            "costusagerate",
            "costusageratio",
            "costusagepercent",
            "costprogressgap",
          ].includes(key)) ||
        (screen === "UI-19" && key === "profitstatus") ||
        (screen === "UI-32" &&
          [
            "marginrate",
            "targetunitprice",
            "requiredincreaserate",
            "annualgain",
          ].includes(key));
      // Empty datasets have no row metadata: never expose a private header merely
      // because there is not yet a first availableFields row to consult.
      if (
        !exception &&
        (/salary|monthlypay|cost|profit|margin|insurance|retirement|allowance|incometax|withholdingtax|vendorsupply|overhead/.test(
          key,
        ) ||
          (["UI-02", "UI-03", "UI-04"].includes(screen ?? "") &&
            [
              "pay",
              "netpay",
              "payamount",
              "basepayamount",
              "labor",
              "laborallocated",
              "teamexpense",
              "projectexpense",
            ].includes(key)))
      )
        return false;
    }
    if (
      screen === "UI-15" &&
      permissions &&
      !permissions.sensitive &&
      ["account", "bankCode", "bankName", "depositor"].includes(column.id)
    )
      return false;
    if (!declared) return true;
    const key = aliases[column.id] ?? column.id;
    const metadataAliases: Record<string, string[]> = {
      name: ["name", "developerName", "personName"],
      person: ["developerName", "personName"],
      project: ["projectName"],
      start: ["startDate"],
      end: ["endDate"],
      mm: ["contractMm", "monthlyMm"],
      status: ["status", "assignmentStatus"],
    };
    return (
      declared.includes(key) ||
      metadataAliases[key]?.some((name) => declared.includes(name)) === true
    );
  });
  if (error) return <ErrorBanner message={error} onRetry={onRetry} />;
  const pages = Math.max(1, Math.ceil(total / size));
  const span =
    columns.length + (onSelectionChange ? 1 : 0) + (expandableRow ? 1 : 0);
  const marker = (key: Key) =>
    String(key)
      .replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)
      .replace(/[^a-z0-9-]/g, "-");
  const cellStyle = (column: Column<T>): CSSProperties => {
    const fixed = stickyColumns?.find((item) => item.id === column.id);
    if (!fixed) return { textAlign: column.align };
    const before = columns.slice(0, columns.indexOf(column));
    const left = before.reduce(
      (sum, item) =>
        sum + (stickyColumns?.find((c) => c.id === item.id)?.width ?? 0),
      (onSelectionChange ? 44 : 0) + (expandableRow ? 44 : 0),
    );
    return {
      textAlign: column.align,
      position: "sticky",
      left,
      width: fixed.width,
      minWidth: fixed.width,
      maxWidth: fixed.width,
    };
  };
  const utilityStyle = (left: number): CSSProperties | undefined =>
    stickyColumns?.length
      ? { position: "sticky", left, width: 44, minWidth: 44, maxWidth: 44 }
      : undefined;
  return (
    <div
      className="sicost-grid"
      data-testid={testId}
      aria-busy={Boolean(loading)}
    >
      {onColumnsChange && (
        <button
          type="button"
          className="sicost-columns-action"
          data-testid={`${testId}-columns-button`}
          onClick={onColumnsChange}
        >
          열 설정
        </button>
      )}
      <div className="sicost-table-viewport">
        <table
          aria-label={tableLabel}
          className={dense ? "sicost-table sicost-dense" : "sicost-table"}
        >
          <thead>
            <tr>
              {onSelectionChange && (
                <th
                  scope="col"
                  className={
                    stickyColumns?.length ? "sicost-sticky-cell" : undefined
                  }
                  style={utilityStyle(0)}
                >
                  <input
                    type="checkbox"
                    aria-label="이 페이지 전체 선택"
                    data-testid={`${testId}-select-all`}
                    checked={
                      rows.length > 0 &&
                      rows.every((row) => selectedKeys?.includes(rowKey(row)))
                    }
                    disabled={!rows.length}
                    onChange={(event) =>
                      onSelectionChange(
                        event.target.checked
                          ? [
                              ...new Set([
                                ...(selectedKeys ?? []),
                                ...rows.map(rowKey),
                              ]),
                            ]
                          : (selectedKeys ?? []).filter(
                              (key) => !rows.some((row) => rowKey(row) === key),
                            ),
                      )
                    }
                  />
                </th>
              )}
              {expandableRow && (
                <th
                  scope="col"
                  className={
                    stickyColumns?.length ? "sicost-sticky-cell" : undefined
                  }
                  style={utilityStyle(onSelectionChange ? 44 : 0)}
                >
                  <span className="sicost-sr-only">월별 상세</span>
                </th>
              )}
              {columns.map((column) => (
                <th
                  scope="col"
                  key={column.id}
                  data-column={column.id}
                  className={
                    stickyColumns?.some((c) => c.id === column.id)
                      ? "sicost-sticky-cell"
                      : undefined
                  }
                  style={cellStyle(column)}
                  aria-sort={
                    sort?.id === column.id
                      ? sort.direction === "ASC"
                        ? "ascending"
                        : "descending"
                      : undefined
                  }
                >
                  {column.sortable && onSortChange ? (
                    <button
                      type="button"
                      className="sicost-sort-header"
                      data-testid={`sort-${column.id}`}
                      onClick={() => onSortChange(column.id)}
                    >
                      {column.label}
                      {sort?.id === column.id
                        ? sort.direction === "ASC"
                          ? " ↑"
                          : " ↓"
                        : ""}
                    </button>
                  ) : (
                    column.label
                  )}
                  {column.help ?? (typeof column.label === "string" && <FieldHelp label={column.label} kind="B" />)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              loadingMessage ? (
                <tr>
                  <td colSpan={span}>
                    <div className="sicost-empty sicost-table-loading" role="status">
                      {loadingMessage}
                    </div>
                  </td>
                </tr>
              ) : (
                <>{Array.from({length:5},(_,index)=><tr key={index} className="sicost-skeleton-row" aria-hidden="true">{Array.from({length:span},(_,column)=><td key={column}><i /></td>)}</tr>)}<tr className="sicost-sr-only"><td colSpan={span}><span role="status">불러오는 중입니다.</span></td></tr></>
              )
            ) : !rows.length ? (
              <tr>
                <td colSpan={span}>
                  <div className="sicost-empty sicost-empty-state">
                    <Icon name="search" size={26} />
                    <span>{empty}</span>
                    {emptyAction}
                  </div>
                </td>
              </tr>
            ) : (
              rows.map((row) => {
                const key = rowKey(row);
                const expanded =
                  expandableRow?.expandedKeys.includes(key) ?? false;
                const detailId = `${instanceId}-${String(key)}`;
                return (
                  <Fragment key={key}>
                    <tr
                      data-testid={`${testId}-row-${marker(key)}`}
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
                        <td
                          className={
                            stickyColumns?.length
                              ? "sicost-sticky-cell"
                              : undefined
                          }
                          style={utilityStyle(0)}
                        >
                          <input
                            type="checkbox"
                            aria-label={`${String(key)} 선택`}
                            data-testid={`${testId}-select-${marker(key)}`}
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
                      {expandableRow && (
                        <td
                          className={
                            stickyColumns?.length
                              ? "sicost-sticky-cell"
                              : undefined
                          }
                          style={utilityStyle(onSelectionChange ? 44 : 0)}
                        >
                          {(!expandableRow.canExpand ||
                            expandableRow.canExpand(row)) && (
                            <button
                              type="button"
                              data-testid={`${testId}-expand-${marker(key)}`}
                              aria-label={`${expandableRow.label?.(row) ?? String(key)} ${expanded ? "접기" : "펼치기"}`}
                              aria-expanded={expanded}
                              aria-controls={expanded ? detailId : undefined}
                              onClick={(event) => {
                                event.stopPropagation();
                                expandableRow.onExpandedChange(
                                  expanded
                                    ? expandableRow.expandedKeys.filter(
                                        (item) => item !== key,
                                      )
                                    : [...expandableRow.expandedKeys, key],
                                );
                              }}
                            >
                              {expanded ? "▾" : "▸"}
                            </button>
                          )}
                        </td>
                      )}
                      {columns.map((column) => (
                        <td
                          key={column.id}
                          data-column={column.id}
                          className={
                            stickyColumns?.some((c) => c.id === column.id)
                              ? "sicost-sticky-cell"
                              : undefined
                          }
                          style={cellStyle(column)}
                        >
                          {column.render(row)}
                        </td>
                      ))}
                    </tr>
                    {expanded && expandableRow && (
                      <tr
                        data-testid={`${testId}-detail-${marker(key)}`}
                        className="sicost-expanded-row"
                      >
                        <td colSpan={span}>
                          <div
                            id={detailId}
                            role="region"
                            aria-label={`${expandableRow.label?.(row) ?? String(key)} 상세`}
                            data-testid={`${testId}-panel-${marker(key)}`}
                          >
                            {expandableRow.render(row)}
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })
            )}
          </tbody>
          {footer && !loading && (
            <tfoot aria-label={footer.label} data-testid={`${testId}-totals`}>
              <tr>
                {onSelectionChange && (
                  <td
                    className={
                      stickyColumns?.length ? "sicost-sticky-cell" : undefined
                    }
                    style={utilityStyle(0)}
                  />
                )}
                {expandableRow && (
                  <td
                    className={
                      stickyColumns?.length ? "sicost-sticky-cell" : undefined
                    }
                    style={utilityStyle(onSelectionChange ? 44 : 0)}
                  />
                )}
                {columns.map((column) => (
                  <td
                    key={column.id}
                    data-column={column.id}
                    className={
                      stickyColumns?.some((c) => c.id === column.id)
                        ? "sicost-sticky-cell"
                        : undefined
                    }
                    style={cellStyle(column)}
                  >
                    {footer.render(column)}
                  </td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>
      {page !== undefined && (
        <Pagination page={page} size={size} total={total} loading={loading} onPageChange={onPageChange} onSizeChange={onSizeChange} testId={testId} />
      )}
    </div>
  );
}
