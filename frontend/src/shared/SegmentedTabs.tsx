import { useRef, type ReactNode } from "react";
import "./presentation.css";
export type Segment = {
  key: string;
  label: ReactNode;
  panelId: string;
  disabled?: boolean;
  testId: string;
};
export function SegmentedTabs({
  tabs,
  value,
  onChange,
  label,
  testId,
}: {
  tabs: readonly Segment[];
  value: string;
  onChange(key: string): void;
  label: string;
  testId: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  return (
    <div
      ref={ref}
      role="tablist"
      aria-label={label}
      data-testid={testId}
      className="sicost-segmented-tabs"
      onKeyDown={(e) => {
        if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(e.key)) return;
        e.preventDefault();
        const available = tabs.filter((t) => !t.disabled),
          index = available.findIndex((t) => t.key === value);
        const next =
          e.key === "Home"
            ? available[0]
            : e.key === "End"
              ? available[available.length - 1]
              : available[
                  (index +
                    (e.key === "ArrowRight" ? 1 : -1) +
                    available.length) %
                    available.length
                ];
        if (!next) return;
        onChange(next.key);
        ref.current
          ?.querySelector<HTMLButtonElement>(`[data-testid="${next.testId}"]`)
          ?.focus();
      }}
    >
      {tabs.map((t) => (
        <button
          key={t.key}
          id={`${t.panelId}-tab-${t.key}`}
          type="button"
          role="tab"
          data-testid={t.testId}
          aria-selected={value === t.key}
          aria-controls={t.panelId}
          disabled={t.disabled}
          tabIndex={value === t.key ? 0 : -1}
          onClick={() => onChange(t.key)}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
