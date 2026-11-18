import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Icon } from "./Icon";

export function HelpTooltip({
  label,
  children,
  interactive = false,
  testId,
}: {
  label: string;
  children: ReactNode;
  interactive?: boolean;
  testId?: string;
}) {
  const id = useId(),
    button = useRef<HTMLButtonElement>(null),
    content = useRef<HTMLDivElement>(null);
  const pointerWasOpen = useRef(false),
    restoringFocus = useRef(false);
  const [open, setOpen] = useState(false),
    [position, setPosition] = useState({ top: 0, left: 0 });
  useEffect(() => {
    if (!open) return;
    const update = () => {
      const rect = button.current?.getBoundingClientRect(),
        box = content.current?.getBoundingClientRect();
      if (rect && box)
        setPosition({
          left: Math.max(
            8,
            Math.min(rect.left, window.innerWidth - box.width - 8),
          ),
          top:
            rect.bottom + box.height + 16 > window.innerHeight
              ? Math.max(8, rect.top - box.height - 8)
              : rect.bottom + 8,
        });
    };
    const dismiss = (event: PointerEvent) => {
      if (
        !button.current?.contains(event.target as Node) &&
        !content.current?.contains(event.target as Node)
      )
        setOpen(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.stopPropagation();
        setOpen(false);
        restoringFocus.current = true;
        button.current?.focus();
        restoringFocus.current = false;
      }
    };
    update();
    window.addEventListener("resize", update);
    window.addEventListener("scroll", update, true);
    document.addEventListener("pointerdown", dismiss);
    document.addEventListener("keydown", escape, true);
    return () => {
      window.removeEventListener("resize", update);
      window.removeEventListener("scroll", update, true);
      document.removeEventListener("pointerdown", dismiss);
      document.removeEventListener("keydown", escape, true);
    };
  }, [open]);
  return (
    <span
      className="sicost-help-anchor"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => {
        if (!interactive && document.activeElement !== button.current)
          setOpen(false);
      }}
    >
      <button
        ref={button}
        type="button"
        className="sicost-help-button"
        data-testid={testId ?? `help-${id.replace(/:/g, "").toLowerCase()}`}
        aria-label={label}
        aria-expanded={open}
        aria-describedby={open && !interactive ? id : undefined}
        aria-controls={open && interactive ? id : undefined}
        aria-haspopup={interactive ? "dialog" : undefined}
        onPointerDown={() => {
          pointerWasOpen.current = open;
        }}
        onFocus={() => {
          if (!restoringFocus.current) setOpen(true);
        }}
        onBlur={() => {
          if (!interactive) setOpen(false);
        }}
        onClick={(event) =>
          setOpen(event.detail === 0 ? !open : !pointerWasOpen.current)
        }
      >
        <Icon name="info" size={16} />
        {label}
      </button>
      {open &&
        createPortal(
          <div
            ref={content}
            id={id}
            data-testid={`${testId ?? `help-${id.replace(/:/g, "").toLowerCase()}`}-popover`}
            role={interactive ? "dialog" : "tooltip"}
            aria-label={interactive ? label : undefined}
            className="sicost-help-tooltip"
            style={{ position: "fixed", ...position }}
          >
            {children}
          </div>,
          document.body,
        )}
    </span>
  );
}
