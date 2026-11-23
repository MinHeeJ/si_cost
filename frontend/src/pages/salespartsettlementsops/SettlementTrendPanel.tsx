import { Dialog } from "../../shared/Dialog";
import { Icon } from "../../shared/Icon";
import { Pagination } from "../../shared/Pagination";
import { FieldHelp } from "../../shared/FieldHelp";
import { useSharedScreen } from "../shared/SharedScreenContext";
import { useEffect, useState } from "react";
import { formatAmount, formatMm, type AmountUnit } from "../../shared/format";
import { ErrorBanner } from "../../shared/Feedback";
import { ChartHost } from "../../shared/charts/ChartHost";
import { seriesChartOptions } from "../../shared/charts/chartAdapters";
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
  amountUnit = "천원",
}: {
  partId: string;
  month: string;
  amountUnit?: AmountUnit;
}) {
  const shared = useSharedScreen("UI-19");
  const [data, setData] = useState<TrendResult>(),
    [error, setError] = useState<unknown>(),
    [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(shared.pageSize ?? 50),
    [retry, setRetry] = useState(0);
  const [warningsOpen, setWarningsOpen] = useState(false);
  const closeWarnings = () => setWarningsOpen(false);
  const displayUnit = amountUnit;
  useEffect(() => {
    setPage(1);
    closeWarnings();
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
  const warningsByMonth = new Map<string, Map<string, number>>();
  for (const issue of data?.issues ?? []) {
    const key = issue.month ?? "기간 공통";
    const messages = warningsByMonth.get(key) ?? new Map<string, number>();
    messages.set(issue.message, (messages.get(issue.message) ?? 0) + 1);
    warningsByMonth.set(key, messages);
  }
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
            {data.issues.length > 0 && (
              <button
                type="button"
                className="settlement-trend-warning-button"
                aria-haspopup="dialog"
                onClick={() => setWarningsOpen(true)}
              >
                <Icon name="warning" size={15} />
                경고 {data.issues.length}건
              </button>
            )}
          </div>
          <ChartHost
            option={seriesChartOptions({
              categories: data.monthlyTotals.map((row) => row.month),
              series: [
                {
                  name: "파트 이익금",
                  values: data.monthlyTotals.map((row) => row.partProfitAmount),
                  type: "bar",
                },
              ],
              formatValue: (value) =>
                `${formatAmount(value, displayUnit)} ${displayUnit}`,
              axisUnit: displayUnit,
            })}
          >
            <p className="settlement-guide">
              차트를 표시할 수 없는 환경에서는 아래 월별 정산표를 이용해 주세요.
            </p>
          </ChartHost>
          <div className="sicost-table-viewport">
            <table
              className={`sicost-table settlement-trend-table${shared.dense ? " sicost-dense" : ""}`}
            >
              <thead>
                <tr>
                  <th scope="col">이름</th>
                  <th scope="col">구분<FieldHelp label="구분" kind="B" /></th>
                  {data.months.map((m) => (
                    <th
                      key={m}
                      className={
                        m === data.month ? "settlement-base-month" : undefined
                      }
                    >
                      {m.slice(5)}월
                    </th>
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
                        <th rowSpan={person.metrics.length}>
                          {person.name}
                          <small>{person.managementNo}</small>
                        </th>
                      )}
                      <th scope="row">{labels[metric.metric]}</th>
                      {metric.months.map((cell) => (
                        <td
                          key={cell.month}
                          title={`${cell.employeeType ?? ""} · ${cell.source} ${cell.issues.map((issue) => issue.message).join(" / ")}`}
                          className={
                            [
                              cell.month === data.month
                                ? "settlement-base-month"
                                : "",
                              cell.value?.startsWith("-")
                                ? "settlement-negative"
                                : "",
                            ]
                              .filter(Boolean)
                              .join(" ") || undefined
                          }
                        >
                          {metric.metric === "MM"
                            ? formatMm(cell.value)
                            : formatAmount(cell.value, displayUnit)}
                        </td>
                      ))}
                      <td
                        className={
                          metric.total?.startsWith("-")
                            ? "settlement-negative"
                            : undefined
                        }
                      >
                        {metric.metric === "MM"
                          ? formatMm(metric.total)
                          : formatAmount(metric.total, displayUnit)}
                      </td>
                      {i === 0 && (
                        <td
                          rowSpan={person.metrics.length}
                          className="settlement-remark"
                          title={
                            [
                              ...new Set(
                                person.metrics
                                  .map((item) => item.remark)
                                  .filter(Boolean),
                              ),
                            ].join("\n") || undefined
                          }
                        >
                          {person.baseMonthRemark ?? "–"}
                        </td>
                      )}
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
                      className={
                        [
                          total.month === data.month
                            ? "settlement-base-month"
                            : "",
                          total.partProfitAmount?.startsWith("-")
                            ? "settlement-negative"
                            : "",
                        ]
                          .filter(Boolean)
                          .join(" ") || undefined
                      }
                      title={`인력 이익금 ${total.personProfitShareAmount ?? "미확인"} · 카드 ${total.cardAmount ?? "미확인"}`}
                    >
                      {formatAmount(total.partProfitAmount, displayUnit)}
                    </td>
                  ))}
                  <td
                    className={
                      data.periodPartProfitAmount?.startsWith("-")
                        ? "settlement-negative"
                        : undefined
                    }
                  >
                    {formatAmount(data.periodPartProfitAmount, displayUnit)}
                  </td>
                  <td>카드는 월 1회 차감</td>
                </tr>
              </tfoot>
            </table>
          </div>
          <Dialog
            open={warningsOpen && data.issues.length > 0}
            title="기간 추이 경고"
            onConfirm={closeWarnings}
            onCancel={closeWarnings}
            confirmLabel="닫기"
            hideCancel
          >
            <div className="settlement-trend-warnings">
              <p>조회 기간의 정산 자료에서 확인이 필요한 항목입니다.</p>
              {[...warningsByMonth].map(([issueMonth, messages]) => (
                <section key={issueMonth}>
                  <h3>{issueMonth}</h3>
                  <ul>
                    {[...messages].map(([message, count]) => (
                      <li key={message}>
                        {message}
                        {count > 1 && <small>{count}건</small>}
                      </li>
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          </Dialog>
          <Pagination
            page={data.pagination.page}
            size={data.pagination.size}
            total={data.pagination.total}
            unit="명"
            testId="settlement-trend"
            onPageChange={setPage}
            onSizeChange={(next) => {
              void (shared.savePageSize?.(next) ?? Promise.resolve())
                .then(() => {
                  setSize(next);
                  setPage(1);
                })
                .catch(setError);
            }}
          />
        </>
      )}
    </div>
  );
}
