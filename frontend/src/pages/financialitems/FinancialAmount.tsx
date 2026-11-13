import { formatDecimal } from "../../shared/format";
export type FinancialUnit = "원" | "천원" | "백만원";
export function FinancialAmount({
  value,
  unit = "천원",
  plus = false,
}: {
  value: string | null | undefined;
  unit?: FinancialUnit;
  plus?: boolean;
}) {
  if (value == null) return <span>미확인</span>;
  const places = unit === "천원" ? 3 : unit === "백만원" ? 6 : 0;
  const negative = value.startsWith("-"),
    digits = value.replace(/^-/, "").padStart(places + 1, "0");
  const raw = `${negative ? "-" : ""}${places ? `${digits.slice(0, -places)}.${digits.slice(-places)}` : digits}`;
  return (
    <span className={negative ? "financial-negative" : undefined}>
      {plus && !negative && BigInt(value) > 0n ? "+" : ""}
      {formatDecimal(raw)}
    </span>
  );
}
