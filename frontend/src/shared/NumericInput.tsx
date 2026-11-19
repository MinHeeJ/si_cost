import type { InputHTMLAttributes } from "react";

export type NumericKind = "amount" | "integer" | "decimal";
export function sanitizeNumber(value: string, kind: NumericKind = "decimal") {
  if (kind === "amount") return value.replace(/[^0-9,]/g, "");
  if (kind === "integer") return value.replace(/[^0-9]/g, "");
  const cleaned = value.replace(/[^0-9.]/g, "");
  const dot = cleaned.indexOf(".");
  return dot < 0
    ? cleaned
    : cleaned.slice(0, dot + 1) + cleaned.slice(dot + 1).replaceAll(".", "");
}

export type NumericInputProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "type"
> & { kind?: NumericKind };
export function NumericInput({
  kind = "decimal",
  onChange,
  ...props
}: NumericInputProps) {
  return (
    <input
      {...props}
      type="text"
      inputMode={kind === "decimal" ? "decimal" : "numeric"}
      onChange={(event) => {
        event.target.value = sanitizeNumber(event.target.value, kind);
        onChange?.(event);
      }}
    />
  );
}
