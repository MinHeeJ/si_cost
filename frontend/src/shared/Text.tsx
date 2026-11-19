import { FieldHelp } from "./FieldHelp";
import { useId, type InputHTMLAttributes } from "react";

export type TextProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange"
> & {
  label: string;
  value: string;
  onChange(value: string): void;
  error?: string;
  unit?: string;
  helpScreenId?: string;
};
export function Text({
  label,
  value,
  onChange,
  error,
  id,
  unit,
  helpScreenId,
  ...props
}: TextProps) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <div className="sicost-field">
      <label htmlFor={inputId}>
        {label}
        {props.required && <span aria-hidden="true"> *</span>}
        <FieldHelp label={label} screenId={helpScreenId} />
      </label>
      <div className={unit ? "sicost-input-unit" : undefined}><input
        {...props}
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
