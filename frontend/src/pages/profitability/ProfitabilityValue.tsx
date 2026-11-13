import {
  formatAmount,
  formatDate,
  formatMm,
  formatRate,
  type AmountUnit,
} from "../../shared/format";

/** Display only: all calculations and rounding belong to the server. */
export function ProfitabilityValue({
  value,
  kind = "amount",
  unit = "천원",
}: {
  value: string | null | undefined;
  kind?: "amount" | "mm" | "rate" | "date";
  unit?: AmountUnit;
}) {
  const negative =
    kind !== "date" &&
    value != null &&
    /^-\d+(\.\d+)?$/.test(value) &&
    /[1-9]/.test(value);
  const text =
    kind === "mm"
      ? formatMm(value)
      : kind === "rate"
        ? formatRate(value)
        : kind === "date"
          ? formatDate(value)
          : formatAmount(value, unit);
  return (
    <span className={negative ? "sicost-number-negative" : undefined}>
      {text}
    </span>
  );
}
