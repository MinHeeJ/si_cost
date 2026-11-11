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
