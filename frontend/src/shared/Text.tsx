import { useId, type InputHTMLAttributes } from "react";
import { NumericInput, type NumericKind } from "./NumericInput";

export type TextProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange"
> & {
  label: string;
  value: string;
  onChange(value: string): void;
  error?: string;
  numericKind?: NumericKind;
};
export function Text({
  label,
  value,
  onChange,
  error,
  id,
  numericKind,
  ...props
}: TextProps) {
  const generated = useId();
  const inputId = id ?? generated;
  const Input = numericKind ? NumericInput : "input";
  return (
    <div className="sicost-field">
      <label htmlFor={inputId}>
        {label}
        {props.required && <span aria-hidden="true"> *</span>}
      </label>
      <Input
        {...props}
        {...(numericKind ? { kind: numericKind } : {})}
        data-testid={
          props["data-testid" as keyof typeof props] ??
          `text-${inputId.replace(/[^a-z0-9-]/gi, "").toLowerCase()}`
        }
        id={inputId}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${inputId}-error` : undefined}
      />
      {error && (
        <span
          id={`${inputId}-error`}
          role="alert"
          className="sicost-field-error"
        >
          {error}
        </span>
      )}
    </div>
  );
}
