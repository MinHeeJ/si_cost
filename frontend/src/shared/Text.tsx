import { useId, type InputHTMLAttributes } from "react";

export type TextProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange"
> & {
  label: string;
  value: string;
  onChange(value: string): void;
  error?: string;
};
export function Text({
  label,
  value,
  onChange,
  error,
  id,
  ...props
}: TextProps) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <div className="sicost-field">
      <label htmlFor={inputId}>
        {label}
        {props.required && <span aria-hidden="true"> *</span>}
      </label>
      <input
        {...props}
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
