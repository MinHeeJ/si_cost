import { periodOptions } from "./periods";
export type PeriodSelectorProps = {
  type: "YEAR" | "YEAR_MONTH";
  value: string;
  min: string;
  max: string;
  disabled?: boolean;
  label?: string;
  onChange(value: string): void;
};
export function PeriodSelector({
  type,
  value,
  min,
  max,
  disabled,
  label = "기간",
  onChange,
}: PeriodSelectorProps) {
  const options = periodOptions(type, min, max),
    index = options.indexOf(value);
  const select = (next: string) => {
    if (!disabled && options.includes(next) && next !== value) onChange(next);
  };
  return (
    <div className="sicost-period-selector">
      <button
        type="button"
        aria-label="이전 기간"
        disabled={disabled || index <= 0}
        onClick={() => select(options[index - 1])}
      >
        ‹
      </button>
      <select
        aria-label={label}
        disabled={disabled || !options.length}
        value={value}
        onChange={(event) => select(event.target.value)}
      >
        {!options.length && <option value={value}>선택할 수 없음</option>}
        {options.map((option) => (
          <option key={option} value={option}>
            {type === "YEAR"
              ? `${option}년`
              : `${option.slice(0, 4)}년 ${Number(option.slice(5))}월`}
          </option>
        ))}
      </select>
      <button
        type="button"
        aria-label="다음 기간"
        disabled={disabled || index < 0 || index >= options.length - 1}
        onClick={() => select(options[index + 1])}
      >
        ›
      </button>
    </div>
  );
}
