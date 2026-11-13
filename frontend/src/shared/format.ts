export function formatDecimal(value: string | null | undefined): string {
  if (value == null) return "–";
  if (!/^-?\d+(\.\d+)?$/.test(value)) return value;
  const negative = value.startsWith("-");
  const [integer, rawFraction = ""] = (negative ? value.slice(1) : value).split(
    ".",
  );
  const fraction = rawFraction.replace(/0+$/, "");
  const grouped = integer
    .replace(/^0+(?=\d)/, "")
    .replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return `${negative && !/^0+(\.0+)?$/.test(value.slice(1)) ? "−" : ""}${grouped}${fraction ? `.${fraction}` : ""}`;
}
export function normalizeDecimal(value: string, emptyAsZero = false): string {
  const cleaned = value.replace(/[\s,]/g, "").replace(/[원%]$/, "");
  if (!cleaned && emptyAsZero) return "0";
  if (!/^\d{1,11}(\.\d{1,4})?$/.test(cleaned))
    throw new Error("정수 11자리·소수 4자리 이내의 0 이상 값을 입력해 주세요.");
  return cleaned;
}
export function compareDecimal(
  left: string | null,
  right: string | null,
): number {
  if (left === right) return 0;
  if (left == null) return -1;
  if (right == null) return 1;
  const fractionDigits = Math.max(
    left.split(".")[1]?.length ?? 0,
    right.split(".")[1]?.length ?? 0,
  );
  const integer = (value: string) => {
    const [whole, fraction = ""] = value.replace(/^-/, "").split(".");
    return BigInt(
      `${value.startsWith("-") ? "-" : ""}${whole}${fraction.padEnd(fractionDigits, "0")}`,
    );
  };
  const l = integer(left),
    r = integer(right);
  return l < r ? -1 : l > r ? 1 : 0;
}
export function formatDateTime(value: string | null | undefined): string {
  if (value == null) return "–";
  const date = new Date(
    /(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}+09:00`,
  );
  if (Number.isNaN(date.getTime())) return value;
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value;
  return `${get("year")}-${get("month")}-${get("day")} ${get("hour")}:${get("minute")}`;
}
export function validMonth(value: string): boolean {
  return /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(value);
}

export type AmountUnit = "원" | "천원" | "백만원";
/** Integer-won API strings are shifted, never converted to floating point or rounded. */
export function formatAmount(
  value: string | null | undefined,
  unit: AmountUnit = "천원",
): string {
  if (value == null) return "–";
  if (!/^-?\d+$/.test(value)) return value;
  const digits = unit === "원" ? 0 : unit === "천원" ? 3 : 6;
  const negative = value.startsWith("-");
  const absolute = value
    .replace(/^-/, "")
    .replace(/^0+(?=\d)/, "")
    .padStart(digits + 1, "0");
  const decimal = digits
    ? `${absolute.slice(0, -digits)}.${absolute.slice(-digits)}`
    : absolute;
  return formatDecimal(`${negative ? "-" : ""}${decimal}`);
}
/** The server has already rounded MM/rates; only preserve/pad its fixed display scale. */
export function formatFixedDecimal(
  value: string | null | undefined,
  digits: 1 | 2,
): string {
  if (value == null) return "–";
  if (!/^-?\d+(\.\d+)?$/.test(value)) return value;
  const [whole, fraction = ""] = value.split(".");
  const fixed = fraction.padEnd(digits, "0");
  // Unexpected nonzero extra precision is not silently rounded or discarded.
  if (fixed.slice(digits).replace(/0/g, "")) return formatDecimal(value);
  const head = formatDecimal(whole);
  const sign =
    value.startsWith("-") && head === "0" && /[1-9]/.test(fixed) ? "−" : "";
  return `${sign}${head}.${fixed.slice(0, digits)}`;
}
export const formatMm = (value: string | null | undefined): string =>
  formatFixedDecimal(value, 2);
export function formatRate(value: string | null | undefined): string {
  return value == null ? "–" : `${formatFixedDecimal(value, 1)}%`;
}
/** LocalDate is a calendar date, not an instant: do not apply browser timezone conversion. */
export function formatDate(value: string | null | undefined): string {
  if (value == null) return "–";
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
  return formatDateTime(value).slice(0, 10);
}
