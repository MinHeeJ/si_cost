import { FieldHelp } from "./FieldHelp";
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
  unit?: string;
  helpScreenId?: string;
};
export function Text({
  label,
  value,
  onChange,
  error,
  id,
  numericKind,
  unit,
  helpScreenId,
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
        <FieldHelp label={label} screenId={helpScreenId} />
      </label>
      <div className={unit ? "sicost-input-unit" : undefined}><Input
        {...props}
        {...(numericKind ? { kind: numericKind } : {})}
        data-testid={
          props["data-testid" as keyof typeof props] ??
          `text-${inputId.replace(/[^a-z0-9-]/gi, "").toLowerCase()}`
        }
        id={inputId}
        aria-label={props["aria-label"] ?? label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${inputId}-error` : undefined}
      />
      {unit && <span aria-hidden="true">{unit}</span>}</div>
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
