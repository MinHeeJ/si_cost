import type { InputHTMLAttributes } from "react";

export function normalizeFinancialWon(
  value: string,
  signed = false,
  positive = false,
): string {
  const clean = value.replace(/[\s\p{Z},]/gu, "").replace(/원$/, "");
  if (!(signed ? /^-?\d+$/ : /^\d+$/).test(clean))
    throw new Error("원 정수로 입력해 주세요.");
  const number = BigInt(clean),
    limit = 999999999999999n;
  if (
    number > limit ||
    number < -limit ||
    (positive && number <= 0n) ||
    (signed && number === 0n)
  )
    throw new Error("금액의 허용 범위를 확인해 주세요.");
  return number.toString();
}
export function WonInput({
  value,
  onValueChange,
  signed,
  positive,
  ...props
}: Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> & {
  value: string;
  onValueChange(value: string): void;
  signed?: boolean;
  positive?: boolean;
}) {
  let invalid = false;
  if (value) {
    try {
      normalizeFinancialWon(value, signed, positive);
    } catch {
      invalid = true;
    }
  }
  return (
    <input
      {...props}
      inputMode={signed ? "text" : "numeric"}
      value={value}
      aria-invalid={invalid || props["aria-invalid"]}
      onChange={(event) => onValueChange(event.target.value)}
    />
  );
}
