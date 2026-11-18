import { useId, useRef, type ReactNode } from "react";

export type Segment<K extends string> = {
  key: K;
  label: ReactNode;
  disabled?: boolean;
  testId?: string;
  id?: string;
  panelId?: string;
  actionId?: string;
};

/** Controlled selection: panels stay owned/mounted by their existing workspace. */
export function SegmentedTabs<K extends string>({
  value,
  items,
  onChange,
  label,
  testId,
  className = "",
}: {
  value: K;
  items: readonly Segment<K>[];
  onChange(value: K): void;
  label: string;
  testId: string;
  className?: string;
}) {
  const prefix = useId().replace(/:/g, "");
  const buttons = useRef(new Map<K, HTMLButtonElement>());
  return (
    <div
      role="tablist"
      aria-label={label}
      className={`sicost-segmented-tabs ${className}`.trim()}
      data-testid={testId}
    >
      {items.map((item) => (
        <button
          key={item.key}
          ref={(node) => {
            if (node) buttons.current.set(item.key, node);
            else buttons.current.delete(item.key);
          }}
          type="button"
          role="tab"
          id={item.id ?? `${prefix}-${item.key}`}
          aria-controls={item.panelId}
          aria-selected={value === item.key}
          tabIndex={value === item.key ? 0 : -1}
          disabled={item.disabled}
          data-testid={item.testId ?? `${testId}-${item.key}`}
          data-action-id={item.actionId}
          onClick={() => onChange(item.key)}
          onKeyDown={(event) => {
            if (!["ArrowRight", "ArrowLeft", "Home", "End"].includes(event.key))
              return;
            event.preventDefault();
            const enabled = items.filter((entry) => !entry.disabled);
            if (!enabled.length) return;
            const index = enabled.findIndex((entry) => entry.key === item.key);
            const next =
              event.key === "Home"
                ? enabled[0]
                : event.key === "End"
                  ? enabled.at(-1)!
                  : enabled[
                      (index +
                        (event.key === "ArrowRight" ? 1 : -1) +
                        enabled.length) %
                        enabled.length
                    ];
            buttons.current.get(next.key)?.focus();
            onChange(next.key);
          }}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}
