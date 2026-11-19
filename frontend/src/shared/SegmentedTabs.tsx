import {
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type HTMLAttributes,
  type ReactNode,
} from "react";

/** Existing controlled tab buttons keep all of their handlers, IDs and panel mount policy. */
export function SegmentedTabList({
  children,
  className = "",
  as: Container = "div",
  onKeyDown,
  "data-testid": testId,
  ...props
}: HTMLAttributes<HTMLElement> & {
  as?: "div" | "nav" | "section";
  "data-testid"?: string;
}) {
  const ref = useRef<HTMLElement>(null),
    id = useId();
  const [indicator, setIndicator] = useState({ left: 0, width: 0 });
  useLayoutEffect(() => {
    const update = () => {
      const selected = ref.current?.querySelector<HTMLElement>(
        '[aria-selected="true"],[aria-pressed="true"],[aria-current="page"]',
      );
      if (selected)
        setIndicator((previous) =>
          previous.left === selected.offsetLeft &&
          previous.width === selected.offsetWidth
            ? previous
            : { left: selected.offsetLeft, width: selected.offsetWidth },
        );
    };
    update();
    const observer =
      typeof ResizeObserver === "undefined"
        ? undefined
        : new ResizeObserver(update);
    if (ref.current) observer?.observe(ref.current);
    return () => observer?.disconnect();
  }, [children]);
  return (
    <Container
      {...props}
      ref={ref as React.RefObject<HTMLDivElement>}
      role={props.role ?? (Container === "nav" ? "navigation" : "tablist")}
      className={`sicost-segmented-tabs ${className}`}
      data-testid={testId ?? `tablist-${id.replace(/:/g, "").toLowerCase()}`}
      style={
        {
          ...props.style,
          "--segment-left": `${indicator.left}px`,
          "--segment-width": `${indicator.width}px`,
        } as CSSProperties
      }
      onKeyDown={(event) => {
        onKeyDown?.(event);
        if (
          event.defaultPrevented ||
          event.nativeEvent.isComposing ||
          !["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)
        )
          return;
        const buttons = [
          ...(ref.current?.querySelectorAll<HTMLButtonElement>(
            "button:not(:disabled)",
          ) ?? []),
        ];
        const current = buttons.indexOf(
          (event.target as HTMLElement).closest("button")!,
        );
        if (current < 0) return;
        event.preventDefault();
        const next =
          event.key === "Home"
            ? 0
            : event.key === "End"
              ? buttons.length - 1
              : (current +
                  (event.key === "ArrowRight" ? 1 : -1) +
                  buttons.length) %
                buttons.length;
        buttons[next]?.click();
        buttons[next]?.focus();
      }}
    >
      {children}
    </Container>
  );
}

export type Segment = {
  key: string;
  label: ReactNode;
  disabled?: boolean;
  panelId?: string;
  testId?: string;
};
export function SegmentedTabs({
  tabs,
  value,
  onChange,
  label,
  testId,
}: {
  tabs: Segment[];
  value: string;
  onChange(key: string): void;
  label: string;
  testId?: string;
}) {
  const id = useId(),
    ref = useRef<HTMLDivElement>(null);
  const [indicator, setIndicator] = useState({ left: 0, width: 0 });
  useLayoutEffect(() => {
    const update = () => {
      const button = ref.current?.querySelector<HTMLElement>(
        '[aria-selected="true"]',
      );
      if (button)
        setIndicator({ left: button.offsetLeft, width: button.offsetWidth });
    };
    update();
    const observer =
      typeof ResizeObserver === "undefined"
        ? undefined
        : new ResizeObserver(update);
    if (ref.current) observer?.observe(ref.current);
    return () => observer?.disconnect();
  }, [value, tabs]);
  return (
    <div
      ref={ref}
      role="tablist"
      aria-label={label}
      data-testid={testId}
      className="sicost-segmented-tabs"
      style={
        {
          "--segment-left": `${indicator.left}px`,
          "--segment-width": `${indicator.width}px`,
        } as CSSProperties
      }
    >
      {tabs.map((tab, index) => (
        <button
          key={tab.key}
          id={`${id}-${tab.key}`}
          data-testid={
            tab.testId ?? `${testId ?? "segmented-tabs"}-tab-${index}`
          }
          type="button"
          role="tab"
          aria-selected={tab.key === value}
          aria-controls={tab.panelId}
          disabled={tab.disabled}
          tabIndex={tab.key === value ? 0 : -1}
          onClick={() => onChange(tab.key)}
          onKeyDown={(event) => {
            if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key))
              return;
            event.preventDefault();
            const enabled = tabs.filter((item) => !item.disabled),
              current = enabled.findIndex((item) => item.key === tab.key);
            const next =
              event.key === "Home"
                ? 0
                : event.key === "End"
                  ? enabled.length - 1
                  : (current +
                      (event.key === "ArrowRight" ? 1 : -1) +
                      enabled.length) %
                    enabled.length;
            const target = enabled[next];
            if (target) {
              onChange(target.key);
              document.getElementById(`${id}-${target.key}`)?.focus();
            }
          }}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
