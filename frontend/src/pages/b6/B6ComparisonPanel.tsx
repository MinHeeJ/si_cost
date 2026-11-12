import { DataTable } from "../../shared/DataTable";
import { formatDecimal } from "../../shared/format";
import { salesPartAmount } from "../salesparts/salesPartsApi";
import type { B6Comparison } from "./b6Api";

export type B6AmountUnit = "원" | "천원" | "백만원";
export function b6Amount(value: string | null, unit: B6AmountUnit) {
  if (value == null) return "–";
  const negative = value.startsWith("-");
  return formatDecimal(
    `${negative ? "-" : ""}${salesPartAmount(negative ? value.slice(1) : value, unit)}`,
  );
}
export function B6ComparisonPanel({
  comparison,
  title = "계산에 쓰는 표",
  unit = "원",
}: {
  comparison: B6Comparison;
  title?: string;
  unit?: B6AmountUnit;
}) {
  const warning =
    comparison.differenceAmount !== "0" || comparison.mismatchCount > 0;
  return (
    <section data-testid="b6-comparison-panel" className="b6-comparison">
      <div
        className={`sicost-banner sicost-tone-${warning ? "warn" : "info"}`}
        role="status"
      >
        <span>
          {title} · {comparison.selectedStep ?? "미등록"} · 선택표 총액{" "}
          {b6Amount(comparison.totalAmount, unit)} / 계약금액{" "}
          {b6Amount(comparison.contractAmount, unit)} / 차이{" "}
          {b6Amount(comparison.differenceAmount, unit)} ({unit}) · 불일치{" "}
          {comparison.mismatchCount}건
        </span>
      </div>
      {comparison.warnings.map((text, index) => (
        <p
          key={text}
          data-testid={`b6-comparison-warning-${index}`}
          className="b7-warn"
        >
          {text} · 경고만으로 저장/계산을 차단하지 않습니다.
        </p>
      ))}
      <DataTable
        rows={comparison.items}
        rowKey={(row) => `${row.developerId}:${row.month}:${row.type}`}
        empty="배정/배분 불일치가 없습니다."
        columns={[
          {
            id: "name",
            label: "사람",
            render: (row) => `${row.managementNo} · ${row.name}`,
          },
          {
            id: "type",
            label: "유형",
            render: (row) =>
              row.type === "NO_ASSIGNMENT" ? "배정 없음" : "배분표 없음",
          },
          { id: "month", label: "해당 월", render: (row) => row.month },
          {
            id: "amount",
            label: `금액 (${unit})`,
            align: "right",
            render: (row) => b6Amount(row.amount, unit),
          },
          {
            id: "mm",
            label: "실제MM",
            align: "right",
            render: (row) => row.actualMm,
          },
        ]}
      />
    </section>
  );
}
