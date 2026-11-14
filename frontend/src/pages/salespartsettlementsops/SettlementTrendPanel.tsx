import { useSharedScreen } from "../shared/SharedScreenContext";
import { useEffect, useState } from "react";
import { formatAmount, formatMm, type AmountUnit } from "../../shared/format";
import { ErrorBanner } from "../../shared/Feedback";
import {
  getSalesPartSettlementTrend,
  type TrendResult,
} from "./settlementCommandsApi";
const labels = {
  MM: "MM",
  UNIT_PRICE: "단가",
  COST: "비용",
  PROFIT: "이익",
  PROFIT_SHARE: "이익금",
};
export function SettlementTrendPanel({
  partId,
  month,
  amountUnit,
  onAmountUnitChange,
}: {
  partId: string;
  month: string;
  amountUnit?: AmountUnit;
  onAmountUnitChange?(unit: AmountUnit): void;
}) {
  const shared = useSharedScreen("UI-19");
  const [data, setData] = useState<TrendResult>(),
    [error, setError] = useState<unknown>(),
    [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(shared.pageSize ?? 50),
    [retry, setRetry] = useState(0),
    [unit, setUnit] = useState<AmountUnit>("천원");
  const displayUnit = amountUnit ?? unit;
  useEffect(() => {
    setPage(1);
  }, [partId, month]);
  useEffect(() => {
    const controller = new AbortController();
    setData(undefined);
    setError(undefined);
    void getSalesPartSettlementTrend(
      partId,
      { month, page, size },
      controller.signal,
    )
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(reason);
      });
    return () => controller.abort();
  }, [partId, month, page, size, retry]);
  return (
    <div role="tabpanel" data-testid="settlement-trend-panel">
      {error != null ? (
        <ErrorBanner
          message={error instanceof Error ? error.message : "추이 조회 실패"}
          onRetry={() => setRetry((n) => n + 1)}
        />
      ) : !data ? (
        <p role="status">기간 추이를 불러오는 중입니다.</p>
      ) : (
        <>
          <div className="settlement-toolbar">
            <span>
              {month} · {data.pagination.total}명
            </span>
            <label>
              추이 단위{" "}
              <select
                data-testid="settlement-trend-unit"
                value={displayUnit}
                onChange={(e) => { const next = e.target.value as AmountUnit; if (onAmountUnitChange) onAmountUnitChange(next); else setUnit(next); }}
              >
                <option>원</option>
                <option>천원</option>
                <option>백만원</option>
              </select>
            </label>
          </div>
          <div className="sicost-table-viewport">
            <table className={`sicost-table settlement-trend-table${shared.dense ? " sicost-dense" : ""}`}>
              <thead>
                <tr>
                  <th>이름</th>
                  <th>구분</th>
                  {data.months.map((m) => (
                    <th key={m}>{m.slice(5)}월</th>
                  ))}
                  <th>합계</th>
                  <th>비고</th>
                </tr>
              </thead>
              <tbody>
                {data.items.flatMap((person) =>
                  person.metrics.map((metric, i) => (
                    <tr
                      key={`${person.developerId}-${metric.metric}`}
                      data-testid={`settlement-trend-${person.developerId.toLowerCase().replace(/[^a-z0-9-]/g, "-")}-${metric.metric.toLowerCase()}`}
                    >
                      {i === 0 && (
                        <th rowSpan={5}>
                          {person.name}
                          <small>{person.managementNo}</small>
                        </th>
                      )}
                      <th>{labels[metric.metric]}</th>
                      {metric.months.map((cell) => (
                        <td
                          key={cell.month}
                          title={`${cell.employeeType ?? ""} · ${cell.source} ${cell.issues.map((issue) => issue.message).join(" / ")}`}
                          className={
                            cell.value?.startsWith("-")
                              ? "settlement-negative"
                              : undefined
                          }
                        >
                          {metric.metric === "MM"
                            ? formatMm(cell.value)
                            : formatAmount(cell.value, displayUnit)}
                        </td>
                      ))}
                      <td className={metric.total?.startsWith("-") ? "settlement-negative" : undefined}>
                        {metric.metric === "MM"
                          ? formatMm(metric.total)
                          : formatAmount(metric.total, displayUnit)}
                      </td>
                      <td>{metric.remark ?? "–"}</td>
                    </tr>
                  )),
                )}
                {data.items.length === 0 && (
                  <tr>
                    <td colSpan={data.months.length + 4}>
                      기간 중 투입 인력이 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
              <tfoot>
                <tr>
                  <th colSpan={2}>파트 이익금</th>
                  {data.monthlyTotals.map((total) => (
                    <td
                      key={total.month}
                      className={total.partProfitAmount?.startsWith("-") ? "settlement-negative" : undefined}
                      title={`인력 이익금 ${total.personProfitShareAmount ?? "미확인"} · 카드 ${total.cardAmount ?? "미확인"}`}
                    >
                      {formatAmount(total.partProfitAmount, displayUnit)}
                    </td>
                  ))}
                  <td className={data.periodPartProfitAmount?.startsWith("-") ? "settlement-negative" : undefined}>{formatAmount(data.periodPartProfitAmount, displayUnit)}</td>
                  <td>카드는 월 1회 차감</td>
                </tr>
              </tfoot>
            </table>
          </div>
          {data.issues.map((issue, i) => (
            <p role="alert" key={i}>
              {issue.month} {issue.message}
            </p>
          ))}
          <div className="settlement-toolbar">
            <button
              type="button"
              data-testid="settlement-trend-previous"
              disabled={page === 1}
              onClick={() => setPage((p) => p - 1)}
            >
              이전 페이지
            </button>
            <span>
              {page} / {Math.max(1, Math.ceil(data.pagination.total / size))}
            </span>
            <button
              type="button"
              data-testid="settlement-trend-next"
              disabled={page * size >= data.pagination.total}
              onClick={() => setPage((p) => p + 1)}
            >
              다음 페이지
            </button>
            <label>
              추이 한 화면 건수{" "}
              <select
                data-testid="settlement-trend-size"
                value={size}
                onChange={(e) => {
                  const next = Number(e.target.value) as 20 | 50 | 100;
                  void (shared.savePageSize?.(next) ?? Promise.resolve()).then(() => { setSize(next); setPage(1); }).catch(setError);
                }}
              >
                <option value={20}>20건씩</option>
                <option value={50}>50건씩</option>
                <option value={100}>100건씩</option>
              </select>
            </label>
          </div>
        </>
      )}
    </div>
  );
}
