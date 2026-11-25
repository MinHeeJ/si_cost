type PageSize = 20 | 50 | 100;

type PaginationProps = {
  page: number;
  size: number;
  total: number;
  onPageChange?(page: number): void;
  onSizeChange?(size: PageSize): void;
  loading?: boolean;
  disabled?: boolean;
  pageSizeDisabled?: boolean;
  testId?: string;
  label?: string;
  unit?: string;
  hideSingle?: boolean;
  actionIds?: { previous?: string; next?: string; size?: string };
};

export function Pagination({
  page,
  size,
  total,
  onPageChange,
  onSizeChange,
  loading = false,
  disabled = false,
  pageSizeDisabled = false,
  testId = "table",
  label = "페이지",
  unit = "건",
  hideSingle = false,
  actionIds,
}: PaginationProps) {
  if (loading || (hideSingle && total <= size)) return null;
  const pages = Math.max(1, Math.ceil(total / size));
  const start = Math.max(1, Math.min(page - 2, pages - 4));
  return (
    <nav aria-label={label} className="sicost-pagination">
      <div className="sicost-pagination-layout">
        <span className="sicost-pagination-range">
          {total ? (page - 1) * size + 1 : 0}-{Math.min(page * size, total)} / {total}{unit}
        </span>
        <div className="sicost-pagination-pages">
          <button
            type="button"
            aria-label="처음 페이지"
            data-testid={`${testId}-first-page`}
            disabled={disabled || page <= 1}
            onClick={() => onPageChange?.(1)}
          >
            «
          </button>
          <button
            type="button"
            aria-label="이전 페이지"
            data-testid={`${testId}-previous-page`}
            data-action-id={actionIds?.previous}
            disabled={disabled || page <= 1}
            onClick={() => onPageChange?.(page - 1)}
          >
            ‹
          </button>
          {Array.from({ length: Math.min(5, pages) }, (_, index) => start + index).map((number) => (
            <button
              type="button"
              key={number}
              aria-label={`${number} 페이지`}
              aria-current={number === page ? "page" : undefined}
              disabled={disabled}
              onClick={() => onPageChange?.(number)}
            >
              {number}
            </button>
          ))}
          <button
            type="button"
            aria-label="다음 페이지"
            data-testid={`${testId}-next-page`}
            data-action-id={actionIds?.next}
            disabled={disabled || page >= pages}
            onClick={() => onPageChange?.(page + 1)}
          >
            ›
          </button>
          <button
            type="button"
            aria-label="마지막 페이지"
            data-testid={`${testId}-last-page`}
            disabled={disabled || page >= pages}
            onClick={() => onPageChange?.(pages)}
          >
            »
          </button>
        </div>
        {onSizeChange && (
          <select
            className="sicost-pagination-size"
            aria-label="한 화면 건수"
            data-testid={`${testId}-page-size`}
            data-action-id={actionIds?.size}
            value={size}
            disabled={disabled || pageSizeDisabled}
            onChange={(event) => onSizeChange(Number(event.target.value) as PageSize)}
          >
            {[20, 50, 100].map((value) => (
              <option key={value} value={value}>{value}건씩</option>
            ))}
          </select>
        )}
      </div>
    </nav>
  );
}
