import { formatDecimal } from "../../shared/format";
import { StatusBadge } from "../../shared/Feedback";
import type { AmountUnit, ProjectDistributionData } from "./distributionApi";

export function formatDistributionAmount(
  value: string | null,
  unit: AmountUnit,
): string {
  if (value == null) return "–";
  const amount = BigInt(value),
    negative = amount < 0n;
  const absolute = negative ? -amount : amount;
  const divisor = unit === "백만원" ? 1000000n : unit === "천원" ? 1000n : 1n;
  const digits = unit === "백만원" ? 6 : unit === "천원" ? 3 : 0;
  const fraction = digits
    ? (absolute % divisor).toString().padStart(digits, "0").replace(/0+$/, "")
    : "";
  return formatDecimal(
    `${negative ? "-" : ""}${absolute / divisor}${fraction ? `.${fraction}` : ""}`,
  );
}

export function MonthlyMatrixTable({
  data,
  amountUnit,
}: {
  data: ProjectDistributionData;
  amountUnit: AmountUnit;
}) {
  const money = (value: string | null) =>
    formatDistributionAmount(value, amountUnit);
  return (
    <div
      className="sicost-table-viewport distribution-matrix"
      data-testid="distribution-matrix"
    >
      <table className="sicost-table">
        <caption className="distribution-caption">
          {data.viewedStep} 배분표 · 단위 {amountUnit}
        </caption>
        <thead>
          <tr>
            <th scope="col">사람/항목</th>
            <th scope="col">관리번호</th>
            <th scope="col">이름/항목명</th>
            <th scope="col">직무</th>
            {data.months.map((month) => (
              <th
                data-testid={`distribution-month-header-${month}`}
                scope="col"
                key={month}
              >
                {month}
              </th>
            ))}
            <th scope="col">행합계</th>
          </tr>
        </thead>
        <tbody>
          {data.rows.length ? (
            data.rows.map((row) => (
              <tr key={row.rowSn} data-testid={`distribution-row-${row.rowSn}`}>
                <td>{row.rowType === "DEV" ? "사람" : "항목"}</td>
                <td>{row.managementNo ?? "–"}</td>
                <td>
                  {row.rowType === "DEV" ? row.name : row.itemName}{" "}
                  {row.useYn === "N" && (
                    <StatusBadge tone="lock">중지</StatusBadge>
                  )}
                </td>
                <td>{row.jobNames.join("·") || "–"}</td>
                {data.months.map((month) => (
                  <td
                    data-testid={`distribution-cell-${row.rowSn}-${month}`}
                    className="distribution-number"
                    key={month}
                  >
                    {money(
                      row.cells.find((cell) => cell.month === month)?.amount ??
                        null,
                    )}
                  </td>
                ))}
                <td className="distribution-number">
                  {money(row.totalAmount)}
                </td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={data.months.length + 5}>
                <div className="sicost-empty">
                  이 단계에 등록된 배분표가 없습니다.
                </div>
              </td>
            </tr>
          )}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row" colSpan={4}>
              월합계 / 전체합계
            </th>
            {data.months.map((month) => (
              <td
                data-testid={`distribution-month-total-${month}`}
                className="distribution-number"
                key={month}
              >
                {money(
                  data.monthlyTotals.find((cell) => cell.month === month)
                    ?.amount ?? null,
                )}
              </td>
            ))}
            <td className="distribution-number">{money(data.totalAmount)}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  );
}
