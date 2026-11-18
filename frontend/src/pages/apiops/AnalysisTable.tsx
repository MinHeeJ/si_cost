import { useEffect, useRef, useState } from "react";
import { DataTable, type DataTableProps } from "../../shared/DataTable";
import { PROJECTS_COMMITTED } from "../projects/projectsApi";

export function useAnalysisMobile() {
  const [mobile, setMobile] = useState(() => window.innerWidth <= 767);
  useEffect(() => {
    const resize = () => setMobile(window.innerWidth <= 767);
    window.addEventListener("resize", resize);
    return () => window.removeEventListener("resize", resize);
  }, []);
  return mobile;
}

/** Desktop keeps DataTable; the same columns/actions form 20-row mobile cards. */
export function AnalysisTable<T>(
  props: DataTableProps<T> & { mobileRowAction?: boolean },
) {
  const mobile = useAnalysisMobile();
  const mobileAction = props.mobileRowAction ? props.onRowAction : undefined;
  const callbacks = useRef(props);
  callbacks.current = props;
  const previousMobile = useRef(false),
    desktopSize = useRef(props.size ?? 50);
  const [pages, setPages] = useState<Record<number, readonly T[]>>({});
  const page = props.page ?? 1;
  useEffect(() => {
    if (mobile) {
      desktopSize.current = props.size ?? 50;
      callbacks.current.onSizeChange?.(20);
    } else if (previousMobile.current)
      callbacks.current.onSizeChange?.(desktopSize.current);
    previousMobile.current = mobile;
    setPages({});
  }, [mobile]);
  useEffect(() => {
    if (!mobile || props.loading || props.size !== 20) return;
    setPages((old) =>
      page === 1 ? { 1: props.rows } : { ...old, [page]: props.rows },
    );
  }, [mobile, props.rows, props.loading, props.size, page]);
  useEffect(() => {
    if (!mobile) return;
    const committed = () => {
      setPages({});
      callbacks.current.onPageChange?.(1);
    };
    window.addEventListener(PROJECTS_COMMITTED, committed);
    return () => window.removeEventListener(PROJECTS_COMMITTED, committed);
  }, [mobile]);
  if (!mobile) return <DataTable {...props} />;
  const rows = Object.entries(pages)
    .sort(([a], [b]) => Number(a) - Number(b))
    .flatMap(([, items]) => items);
  const core = new Set([
    "projectName",
    "title",
    "contractType",
    "light",
    "importance",
    "actualMm",
    "cumulativeCost",
    "cumulativeMargin",
    "planProgress",
    "actualProgress",
    "openIssueCount",
    "dueDate",
    "status",
    "lastUpdatedDate",
  ]);
  const columns = props.columns.filter((column) => core.has(column.id));
  return (
    <section className="analysis-mobile-list" data-testid={props.testId}>
      <p>총 {props.total ?? 0}건</p>
      {rows.map((row) => (
        <article
          key={props.rowKey(row)}
          className="analysis-mobile-card"
          data-testid={`analysis-mobile-card-${Array.from(String(props.rowKey(row)), (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
          tabIndex={mobileAction ? 0 : undefined}
          onClick={mobileAction ? () => mobileAction(row) : undefined}
          onKeyDown={
            mobileAction
              ? (event) => {
                  if (
                    event.key === "Enter" &&
                    event.target === event.currentTarget
                  )
                    mobileAction(row);
                }
              : undefined
          }
        >
          <dl>
            {columns.map((column) => (
              <div
                key={column.id}
                data-testid={`analysis-mobile-field-${Array.from(String(props.rowKey(row)), (c) => c.codePointAt(0)!.toString(16)).join("-")}-column-${Array.from(column.id, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
              >
                <dt>{column.label}</dt>
                <dd>{column.render(row)}</dd>
              </div>
            ))}
          </dl>
        </article>
      ))}
      {props.loading && <p role="status">불러오는 중입니다.</p>}
      {!props.loading && !rows.length && (
        <div className="analysis-mobile-empty">
          {props.empty ?? "조회된 자료가 없습니다."}
          {props.emptyAction}
        </div>
      )}
      {rows.length < (props.total ?? 0) && (
        <button
          data-testid="analysis-mobile-more-button"
          type="button"
          disabled={props.loading}
          onClick={() => props.onPageChange?.(page + 1)}
        >
          20개 더보기
        </button>
      )}
    </section>
  );
}
