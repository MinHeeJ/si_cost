import { useId } from "react";
import { HelpTooltip } from "./HelpTooltip";

export type CodeOption = { code: string; name: string; disabled?: boolean };
export type CodeSelectProps = {
  label: string;
  value: string;
  options: readonly CodeOption[];
  onChange(value: string): void;
  required?: boolean;
  error?: string;
  help?: string;
  disabled?: boolean;
  onSelectionConfirmed?(): void;
};
export function CodeSelect({
  label,
  value,
  options,
  onChange,
  error,
  help,
  onSelectionConfirmed,
  ...props
}: CodeSelectProps) {
  const id = useId();
  return (
    <div className="sicost-field">
      <div className="sicost-field-label">
        <label htmlFor={id}>{label}</label>
        {help && <HelpTooltip label={label}>{help}</HelpTooltip>}
      </div>
      <select
        {...props}
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onSelectionConfirmed}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
      >
        <option value="">선택해 주세요</option>
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
