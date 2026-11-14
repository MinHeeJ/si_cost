import { StatusBadge } from "../../shared/Feedback";
import { formatAmount, formatRate, type AmountUnit } from "../../shared/format";

type StatementComparison = {
  changeRate: string | null;
  percentagePointChange: string | null;
  direction: string;
  comparisonMonths: string[];
  missingMonths: string[];
  availableMonthCount: number;
  requestedMonthCount: number;
};
type StatementMonth = {
  month: string;
  sourceKind: string;
  completeness: { issues: { metric: string; reason: string }[] };
};
type StatementRow = {
  metric: string;
  label: string;
  depth: number;
  isRate: boolean;
  monthlyValues: (string | null)[];
  cumulativeValue: string | null;
  previousYearCumulativeValue: string | null;
  comparison: StatementComparison | null;
};
export function FinancialStatementChange({
  comparison,
  label = "",
}: {
  comparison: StatementComparison | null;
  label?: string;
}) {
  if (!comparison) return null;
  const value = comparison.percentagePointChange ?? comparison.changeRate;
  return (
    <span
      className={`operating-change ${comparison.direction.toLowerCase()}`}
      title={`비교 ${comparison.comparisonMonths.join(", ")} · 빠진 월 ${comparison.missingMonths.join(", ") || "없음"}`}
    >
      {label}
      {value == null
        ? "–"
        : `${value.startsWith("-") || value === "0.0" ? "" : "+"}${value}${comparison.percentagePointChange == null ? "%" : "%p"}`}
      {comparison.availableMonthCount < comparison.requestedMonthCount &&
        ` · 자료 ${comparison.availableMonthCount}/${comparison.requestedMonthCount}개월`}
    </span>
  );
}
const status = (kind: string) =>
  kind === "CALCULATED"
    ? "작업중"
    : kind === "CLOSED_SNAPSHOT"
      ? "확정"
      : kind === "MIGRATED"
        ? "이관"
        : kind === "FUTURE"
          ? ""
          : "미확인";
const kebab = (value: string) =>
  value.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);
export function FinancialStatementTable({
  months,
  rows,
  month,
  unit,
  showPreviousYear,
  provisional,
  name,
  testPrefix,
}: {
  months: StatementMonth[];
  rows: StatementRow[];
  month: string;
  unit: AmountUnit;
  showPreviousYear: boolean;
  provisional: boolean;
  name: string;
  testPrefix: string;
}) {
  return (
    <div className="operating-table-scroll">
      <table aria-label={name}>
        <thead>
          <tr>
            <th scope="col">항목 ({unit})</th>
            {months.map((item) => (
              <th
                scope="col"
                key={item.month}
                className={item.month === month ? "operating-current" : ""}
              >
                {Number(item.month.slice(5))}월{" "}
                {status(item.sourceKind) && (
                  <StatusBadge
                    tone={
                      item.sourceKind === "CALCULATED"
                        ? "info"
                        : item.sourceKind === "MISSING"
                          ? "warn"
                          : "lock"
                    }
                  >
                    {status(item.sourceKind)}
                  </StatusBadge>
                )}
              </th>
            ))}
            <th scope="col" className="operating-cumulative">
              누계 {provisional && <StatusBadge tone="warn">잠정</StatusBadge>}
            </th>
            {showPreviousYear && (
              <>
                <th scope="col">전년 누계</th>
                <th scope="col">전년 대비</th>
              </>
            )}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.metric}
              className={row.depth ? "operating-detail" : "operating-total"}
              data-testid={`${testPrefix}-row-${kebab(row.metric)}`}
            >
              <th scope="row">
                {row.depth > 0 && "└"}
                {row.label}
              </th>
              {row.monthlyValues.map((value, index) => (
                <td
                  key={months[index].month}
                  data-financial-negative={value?.startsWith("-")}
                  className={
                    months[index].month === month ? "operating-current" : ""
                  }
                  title={months[index].completeness.issues
                    .filter((issue) => issue.metric === row.metric)
                    .map((issue) => issue.reason)
                    .join(" · ")}
                >
                  {row.isRate ? formatRate(value) : formatAmount(value, unit)}
                </td>
              ))}
              <td className="operating-cumulative" data-financial-negative={row.cumulativeValue?.startsWith("-")}>
                {row.isRate
                  ? formatRate(row.cumulativeValue)
                  : formatAmount(row.cumulativeValue, unit)}
              </td>
              {showPreviousYear && (
                <>
                  <td data-financial-negative={row.previousYearCumulativeValue?.startsWith("-")}>
                    {row.isRate
                      ? formatRate(row.previousYearCumulativeValue)
                      : formatAmount(row.previousYearCumulativeValue, unit)}
                  </td>
                  <td>
                    <FinancialStatementChange comparison={row.comparison} />
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
