import {
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";
import { Icon } from "./Icon";

/** For optional explanation only. Blocking warnings and unknown-value reasons stay inline. */
export function HelpTooltip({
  label,
  children,
  testId,
  interactive = false,
}: {
  label: string;
  children: ReactNode;
  testId: string;
  interactive?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState({ left: 16, top: 16 });
  const trigger = useRef<HTMLButtonElement>(null),
    popup = useRef<HTMLDivElement>(null);
  const restoring = useRef(false);
  const id = useId();
  useLayoutEffect(() => {
    if (!open) return;
    const place = () => {
      const anchor = trigger.current?.getBoundingClientRect(),
        box = popup.current?.getBoundingClientRect();
      if (!anchor || !box) return;
      setPosition({
        left: Math.max(
          16,
          Math.min(anchor.left, window.innerWidth - box.width - 16),
        ),
        top: Math.max(
          16,
          anchor.bottom + box.height + 24 > window.innerHeight
            ? anchor.top - box.height - 8
            : anchor.bottom + 8,
        ),
      });
    };
    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
    };
  }, [open]);
  useEffect(() => {
    if (!open) return;
    const dismiss = (event: PointerEvent) => {
      if (
        !trigger.current?.contains(event.target as Node) &&
        !popup.current?.contains(event.target as Node)
      )
        setOpen(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      event.stopPropagation();
      setOpen(false);
      restoring.current = true;
      trigger.current?.focus();
      restoring.current = false;
    };
    document.addEventListener("pointerdown", dismiss);
    document.addEventListener("keydown", escape, true);
    return () => {
      document.removeEventListener("pointerdown", dismiss);
      document.removeEventListener("keydown", escape, true);
    };
  }, [open]);
  const leave = (target: EventTarget | null) => {
    const node = target instanceof Node ? target : null;
    if (!trigger.current?.contains(node) && !popup.current?.contains(node))
      setOpen(false);
  };
  return (
    <span className="sicost-help-anchor">
      <button
        ref={trigger}
        type="button"
        className="sicost-help-trigger"
        data-testid={testId}
        aria-label={label}
        aria-describedby={open && !interactive ? id : undefined}
        aria-expanded={interactive ? open : undefined}
        aria-haspopup={interactive ? "dialog" : undefined}
        onClick={() => setOpen(true)}
        onFocus={() => {
          if (!restoring.current) setOpen(true);
        }}
        onBlur={(event) => leave(event.relatedTarget)}
        onMouseEnter={() => setOpen(true)}
        onMouseLeave={(event) => {
          if (document.activeElement !== trigger.current)
            leave(event.relatedTarget);
        }}
      >
        <Icon name="info" size={15} />
        {label}
      </button>
      {open &&
        createPortal(
          <div
            ref={popup}
            id={id}
            role={interactive ? "dialog" : "tooltip"}
            aria-label={interactive ? label : undefined}
            className="sicost-help-popup"
            data-testid={`${testId}-popup`}
            style={position}
            onMouseLeave={(event) => leave(event.relatedTarget)}
            onBlur={(event) => leave(event.relatedTarget)}
          >
            {children}
          </div>,
          trigger.current?.closest('[role="dialog"]') ?? document.body,
        )}
    </span>
  );
}
