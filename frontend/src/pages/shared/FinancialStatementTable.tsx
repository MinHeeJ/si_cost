import { HelpTooltip } from "../../shared/HelpTooltip";
import { FieldHelp } from "../../shared/FieldHelp";
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
export function CashFlowStatementTable({
  columns,
  rows,
  unit,
  onSource,
}: {
  columns: {
    key: string;
    label: string;
    status?: string;
    sourceMonth?: string;
    shortage?: boolean;
  }[];
  rows: {
    key: string;
    label: string;
    help?: string;
    values: (string | null)[];
    sourceItem?: string;
    section?: boolean;
  }[];
  unit: AmountUnit;
  onSource(month: string, item: string, trigger: HTMLButtonElement): void;
}) {
  return (
    <div className="operating-table-scroll">
      <table aria-label="자금수지표" className="cash-statement-table">
        <thead>
          <tr>
            <th scope="col"><span>항목</span></th>
            {columns.map((c) => (
              <th
                scope="col"
                key={c.key}
                className={c.status === "PLANNED" ? "cash-planned" : undefined}
              >
                <span>{c.label}</span>
                {c.status && (
                  <span className="cash-statement-column-status">
                    <StatusBadge
                      tone={
                        c.status === "ACTUAL"
                          ? "lock"
                          : c.status === "BEFORE_BASE"
                            ? "neutral"
                            : "info"
                      }
                    >
                      {c.status === "ACTUAL"
                        ? "실적"
                        : c.status === "BEFORE_BASE"
                          ? "운영 시작 전"
                          : "예정"}
                    </StatusBadge>
                    {c.status === "PLANNED" && c.shortage && (
                      <span
                        data-testid={`cash-shortage-warning-${c.key}`}
                        className="cash-shortage-warning"
                        role="img"
                        aria-label={`${c.label} 자금 부족 예상`}
                        title="월말 잔액이 최소 운영자금보다 적을 것으로 예상"
                      >
                        ⚠
                      </span>
                    )}
                  </span>
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.key}
              className={
                row.sourceItem ? "operating-detail" : "operating-total"
              }
              data-testid={`cash-row-${row.key.toLowerCase().replaceAll("_", "-")}`}
            >
              <th scope="row" aria-label={row.help ? row.label : undefined}>
                {row.label}
                {row.help && (
                  <HelpTooltip label={row.label} iconOnly>
                    {row.help}
                  </HelpTooltip>
                )}
              </th>
              {row.values.map((value, i) => (
                <td
                  key={columns[i].key}
                  className={
                    [
                      columns[i].status === "PLANNED" ? "cash-planned" : "",
                      row.key === "balance" && columns[i].shortage
                        ? "cash-shortage"
                        : "",
                    ]
                      .filter(Boolean)
                      .join(" ") || undefined
                  }
                  data-financial-negative={value?.startsWith("-")}
                >
                  {row.section ? (
                    ""
                  ) : row.sourceItem &&
                    columns[i].sourceMonth &&
                    columns[i].status !== "BEFORE_BASE" ? (
                    <button
                      type="button"
                      data-testid={`cash-source-${row.key.toLowerCase().replaceAll("_", "-")}-${columns[i].key}`}
                      aria-label={`${columns[i].label} ${row.label} 계산근거`}
                      onClick={(e) =>
                        onSource(
                          columns[i].sourceMonth!,
                          row.sourceItem!,
                          e.currentTarget,
                        )
                      }
                    >
                      {formatAmount(value, unit)}
                    </button>
                  ) : (
                    formatAmount(value, unit)
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function FinancialStatementTable({
  months,
  rows,
  month,
  currentMonth = month,
  unit,
  showPreviousYear,
  provisional,
  name,
  testPrefix,
}: {
  months: StatementMonth[];
  rows: StatementRow[];
  month: string;
  currentMonth?: string;
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
                aria-label={`${Number(item.month.slice(5))}월 ${status(item.sourceKind) ?? ""}`}
                key={item.month}
                className={
                  item.month === currentMonth ? "operating-current" : ""
                }
              >
                {Number(item.month.slice(5))}월{" "}
                {item.completeness.issues.length > 0 && (
                  <HelpTooltip
                    label={`${Number(item.month.slice(5))}월 확인 사항`}
                    trigger={<span aria-hidden="true">⚠</span>}
                  >
                    {item.completeness.issues.map((issue, i) => (
                      <p key={i}>{issue.reason}</p>
                    ))}
                  </HelpTooltip>
                )}
                {item.month === currentMonth && status(item.sourceKind) && (
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
              className={
                row.depth
                  ? "operating-detail"
                  : [
                        "salesAmt",
                        "grossPrftAmt",
                        "oprtPrftAmt",
                        "netPrftAmt",
                      ].includes(row.metric)
                    ? "operating-total"
                    : "operating-standard"
              }
              data-testid={`${testPrefix}-row-${kebab(row.metric)}`}
            >
              <th scope="row">
                {row.depth > 0 && "└"}
                {row.label}
                <FieldHelp label={row.label} kind="B" />
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
              <td
                className="operating-cumulative"
                data-financial-negative={row.cumulativeValue?.startsWith("-")}
              >
                {row.isRate
                  ? formatRate(row.cumulativeValue)
                  : formatAmount(row.cumulativeValue, unit)}
              </td>
              {showPreviousYear && (
                <>
                  <td
                    data-financial-negative={row.previousYearCumulativeValue?.startsWith(
                      "-",
                    )}
                  >
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
