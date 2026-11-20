import { FieldHelp } from "./FieldHelp";
import { useId } from "react";

export type CodeOption = { code: string; name: string; disabled?: boolean };
export type CodeSelectProps = {
  label: string;
  value: string;
  options: readonly CodeOption[];
  onChange(value: string): void;
  required?: boolean;
  error?: string;
  disabled?: boolean;
  onSelectionConfirmed?(): void;
};
export function CodeSelect({
  label,
  value,
  options,
  onChange,
  error,
  onSelectionConfirmed,
  ...props
}: CodeSelectProps) {
  const id = useId();
  return (
    <div className="sicost-field">
      <label htmlFor={id}>{label}{props.required && <span aria-hidden="true"> *</span>}<FieldHelp label={label} /></label>
      <select
        {...props}
        id={id}
        aria-label={label}
        data-testid={`code-select-${id.replace(/[^a-z0-9-]/gi, "").toLowerCase()}`}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onSelectionConfirmed}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
      >
        <option value="">선택</option>
        {options.map((option) => (
          <option
            key={option.code}
            value={option.code}
            disabled={option.disabled}
          >
            {option.name}
          </option>
        ))}
      </select>
      {error && (
        <span id={`${id}-error`} role="alert" className="sicost-field-error">
          {error}
        </span>
      )}
    </div>
  );
}
