import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useState } from "react";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { formatAmount, type AmountUnit } from "../../shared/format";
import {
  cashLabels,
  getCashFlowSources,
  type CashItem,
  type CashSource,
  type CashSourcesPage,
} from "./cashFlowApi";
export type CashSelection = {
  month: string;
  item: CashItem;
  page: number;
  size: number;
};
export function SourceBreakdown({
  selection,
  unit,
  refresh,
  onChange,
  onClose,
  onSource,
  notice,
}: {
  selection: CashSelection;
  unit: AmountUnit;
  refresh: number;
  onChange(value: CashSelection): void;
  onClose(): void;
  onSource(source: CashSource): void;
  notice?: string;
}) {
  const [data, setData] = useState<CashSourcesPage>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void getCashFlowSources(
      selection.month,
      selection.item,
      selection.page,
      selection.size,
      abort.signal,
    )
      .then((value) => {
        if (!abort.signal.aborted) setData(value);
      })
      .catch((e) => {
        if (!abort.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!abort.signal.aborted) setLoading(false);
      });
    return () => abort.abort();
  }, [
    selection.month,
    selection.item,
    selection.page,
    selection.size,
    refresh,
    retry,
  ]);
  return (
    <DetailDrawer
      title={`${selection.month} ${cashLabels[selection.item]} 계산근거`}
      testId="cash-source-panel"
      closeTestId="cash-source-close"
      onClose={onClose}
    >
      <div className="cash-source-panel operating-profit">
      {notice && <p role="status" className="operating-banner">{notice}</p>}
      {loading && <p role="status">원천을 불러오는 중입니다.</p>}
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "원천을 조회하지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRetry((n) => n + 1)}
        />
      )}
      {data && (
        <>
          <p>
            전체 {data.total}건 · {data.page}페이지 · 전체 금액은 페이지와
            무관합니다.
          </p>
          {data.items.length === 0 ? (
            <div className="operating-empty">조회된 원천이 없습니다.<button type="button" onClick={onClose}>조건 초기화</button></div>
          ) : (
            <div className="operating-table-scroll">
              <table aria-label="현금 원천">
                <thead>
                  <tr>
                    {[
                      "원천종류 / ID",
                      "귀속일 / 기준월",
                      "실적·예정",
                      "금액",
                      "참조급여 / 직원구분",
                      "계산근거 / 미확인",
                      "원천",
                    ].map((label) => (
                      <th key={label}>{label}<FieldHelp label={label} kind="B" /></th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((s, i) => (
                    <tr
                      key={`${s.sourceType}:${s.sourceId}:${s.attributionDate}`}
                      data-testid={`cash-source-row-${i}`}
                    >
                      <td>
                        {s.sourceType}
                        <br />
                        {s.sourceId}
                      </td>
                      <td>
                        {s.attributionDate ?? "–"}
                        <br />
                        {s.sourceMonth} → {s.attributionMonth}
                      </td>
                      <td>
                        <StatusBadge
                          tone={
                            s.status === "UNKNOWN"
                              ? "warn"
                              : s.status === "ACTUAL"
                                ? "lock"
                                : "info"
                          }
                        >
                          {s.status === "UNKNOWN"
                            ? "미확인"
                            : s.status === "ACTUAL"
                              ? "실적"
                              : "예정"}
                        </StatusBadge>
                      </td>
                      <td>
                        {formatAmount(s.amount, unit)}
                        <br />
                        VAT {formatAmount(s.vatAmount, unit)} ·{" "}
                        {s.includesVat ? "포함" : "별도"}
                      </td>
                      <td>
                        {s.referencePayrollMonth ?? "–"}
                        <br />
                        {s.employeeType ?? "–"}
                      </td>
                      <td>
                        {s.formula}
                        {s.issues.map((issue, j) => (
                          <p key={j} className="cash-unknown">
                            {issue.sourceId} {issue.message}
                          </p>
                        ))}
                      </td>
                      <td>
                        {s.sourceLink && (
                          <button
                            data-testid={`cash-source-link-${i}`}
                            onClick={() => onSource(s)}
                          >
                            원천 보기
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="operating-toolbar">
            <button
              data-testid="cash-source-prev"
              disabled={loading || data.page <= 1}
              onClick={() => onChange({ ...selection, page: data.page - 1 })}
            >
              이전 근거
            </button>
            <button
              data-testid="cash-source-next"
              disabled={loading || !data.hasMore}
              onClick={() => onChange({ ...selection, page: data.page + 1 })}
            >
              다음 근거
            </button>
            <label>
              페이지 크기{" "}
              <select
                data-testid="cash-source-size"
                value={selection.size}
                disabled={loading}
                onChange={(e) =>
                  onChange({
                    ...selection,
                    page: 1,
                    size: Number(e.target.value),
                  })
                }
              >
                {[20, 50, 100].map((n) => (
                  <option key={n}>{n}</option>
                ))}
              </select>
            </label>
          </div>
        </>
      )}
      </div>
    </DetailDrawer>
  );
}
