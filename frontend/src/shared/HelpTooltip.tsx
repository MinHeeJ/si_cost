import {
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";
import "./presentation.css";

// An event closes the previous tooltip without retaining another screen's content.
const OPEN = "sicost:help-open";
export function HelpTooltip({
  label,
  children,
  trigger,
  testId,
  chip = false,
  ariaLabel,
}: {
  label: string;
  children: ReactNode;
  trigger?: ReactNode;
  testId?: string;
  chip?: boolean;
  ariaLabel?: string;
}) {
  const id = useId();
  const anchor = useRef<HTMLButtonElement>(null);
  const popup = useRef<HTMLDivElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const pinned = useRef(false);
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState({ left: 16, top: 0, above: false });
  const cancelTimer = () => clearTimeout(timer.current);
  function close() {
    cancelTimer();
    pinned.current = false;
    setOpen(false);
  }
  function show() {
    window.dispatchEvent(new CustomEvent(OPEN, { detail: id }));
    setOpen(true);
  }
  useEffect(() => {
    const other = (e: Event) => {
      if ((e as CustomEvent).detail !== id) close();
    };
    window.addEventListener(OPEN, other);
    return () => {
      cancelTimer();
      window.removeEventListener(OPEN, other);
    };
  }, [id]);
  useEffect(() => {
    if (!open) return;
    const outside = (e: PointerEvent) => {
      if (
        !anchor.current?.contains(e.target as Node) &&
        !popup.current?.contains(e.target as Node)
      )
        close();
    };
    const escape = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        close();
      }
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape, true);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape, true);
    };
  }, [open]);
  useLayoutEffect(() => {
    if (!open) return;
    const place = () => {
      const a = anchor.current?.getBoundingClientRect(),
        p = popup.current?.getBoundingClientRect();
      if (!a || !p) return;
      const above = a.bottom + p.height + 16 > window.innerHeight;
      setPosition({
        left: Math.max(16, Math.min(a.left, window.innerWidth - p.width - 16)),
        top: Math.max(16, above ? a.top - p.height - 8 : a.bottom + 8),
        above,
      });
    };
    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
    };
  }, [open, children]);
  return (
    <>
      <button
        ref={anchor}
        type="button"
        className={`sicost-help${chip ? " sicost-help-chip" : ""}${trigger ? " sicost-help-value" : ""}`}
        data-testid={
          testId ?? `help-${id.replace(/[^a-z0-9-]/gi, "").toLowerCase()}`
        }
        aria-label={ariaLabel ?? `${label} 도움말`}
        aria-expanded={open}
        aria-describedby={open ? id : undefined}
        onPointerEnter={(e) => {
          if (e.pointerType === "mouse") timer.current = setTimeout(show, 120);
        }}
        onPointerLeave={() => {
          cancelTimer();
          if (!pinned.current) setOpen(false);
        }}
        onFocus={show}
        onBlur={() => {
          if (!pinned.current) close();
        }}
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          if (pinned.current) close();
          else {
            pinned.current = true;
            show();
          }
        }}
      >
        {trigger ?? (
          <>
            <span aria-hidden="true">ⓘ</span>
            {chip && " 안내"}
          </>
        )}
      </button>
      {open &&
        createPortal(
          <div
            ref={popup}
            id={id}
            role="tooltip"
            className="sicost-help-popup"
            data-above={position.above}
            style={{ left: position.left, top: position.top }}
          >
            {children}
          </div>,
          document.body,
        )}
    </>
  );
}
