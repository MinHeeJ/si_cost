import { useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode, type FocusEvent, type MouseEvent } from "react";
import { createPortal } from "react-dom";
import { Icon } from "./Icon";

const OPEN_HELP = "sicost:help-open";
/** One portal per open help, shared by fields, headers, status badges and calendar cells. */
export function HelpTooltip({ label, children, interactive = false, testId, iconOnly = false, trigger, disabledTarget, className = "" }: {
  label: string; children: ReactNode; interactive?: boolean; testId?: string;
  iconOnly?: boolean; trigger?: ReactNode; disabledTarget?: ReactNode; className?: string;
}) {
  const id = useId(), button = useRef<HTMLElement | null>(null), content = useRef<HTMLDivElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(), pinned = useRef(false), restoring = useRef(false);
  const [open, setOpen] = useState(false), [position, setPosition] = useState({ top: 0, left: 0, above: false, arrow: 12 });
  const cancelTimer = () => { clearTimeout(timer.current); };
  const close = () => { cancelTimer(); pinned.current = false; setOpen(false); };
  const show = () => { cancelTimer(); window.dispatchEvent(new CustomEvent(OPEN_HELP, { detail: id })); setOpen(true); };
  useEffect(() => {
    const other = (e: Event) => { if ((e as CustomEvent).detail !== id) close(); };
    window.addEventListener(OPEN_HELP, other);
    return () => { cancelTimer(); window.removeEventListener(OPEN_HELP, other); };
  }, [id]);
  useLayoutEffect(() => {
    if (!open) return;
    const update = () => {
      const r = button.current?.getBoundingClientRect(), box = content.current?.getBoundingClientRect();
      if (!r || !box) return;
      const left = Math.max(16, Math.min(r.left, window.innerWidth - box.width - 16));
      const above = r.bottom + box.height + 12 > window.innerHeight;
      setPosition({ left, top: above ? Math.max(16, r.top - box.height - 10) : r.bottom + 10, above, arrow: Math.max(10, Math.min(r.left + r.width / 2 - left, box.width - 20)) });
    };
    const dismiss = (e: PointerEvent) => { if (!button.current?.contains(e.target as Node) && !content.current?.contains(e.target as Node)) close(); };
    const escape = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      e.stopImmediatePropagation(); e.preventDefault(); close();
      restoring.current = true; button.current?.focus(); restoring.current = false;
    };
    update(); window.addEventListener("resize", update); window.addEventListener("scroll", update, true);
    document.addEventListener("pointerdown", dismiss); document.addEventListener("keydown", escape, true);
    return () => { window.removeEventListener("resize", update); window.removeEventListener("scroll", update, true); document.removeEventListener("pointerdown", dismiss); document.removeEventListener("keydown", escape, true); };
  }, [open]);
  const helpLabel = label.endsWith("도움말") ? label : `${label} 도움말`;
  const focus = () => { if (!restoring.current) show(); };
  const blur = (e: FocusEvent<HTMLElement>) => { if (!content.current?.contains(e.relatedTarget as Node)) close(); };
  const click = (e: MouseEvent<HTMLElement>) => { e.preventDefault(); e.stopPropagation(); if (pinned.current) close(); else { pinned.current = true; show(); } };
  const attributes = { "data-testid": testId ?? `help-${id.replace(/:/g, "").toLowerCase()}`, "aria-label": helpLabel, "aria-expanded": open, "aria-describedby": open ? id : undefined, onFocus: focus, onBlur: blur, onClick: click };
  return <span className={`sicost-help-anchor ${className}`} onPointerEnter={e => { if (e.pointerType === "mouse") { cancelTimer(); timer.current = setTimeout(show, 120); } }} onPointerLeave={() => { cancelTimer(); if (!pinned.current && !interactive) close(); }}>
    {disabledTarget !== undefined ? <span {...attributes} ref={node => { button.current = node; }} className="sicost-disabled-help" role="button" tabIndex={0} onKeyDown={e => { if(e.key === "Enter" || e.key === " ") {e.preventDefault();e.currentTarget.click();} }}>{disabledTarget}</span> :
    <button {...attributes} ref={node => { button.current = node; }} type="button" className={`sicost-help-button${iconOnly ? " sicost-help-icon" : ""}${trigger !== undefined ? " sicost-help-value" : ""}`} aria-controls={interactive && open ? id : undefined} aria-haspopup={interactive ? "dialog" : undefined}>
      {trigger ?? <><Icon name="info" size={iconOnly ? 14 : 16} />{!iconOnly && label}</>}
    </button>}
    {open && createPortal(<div ref={content} id={id} role={interactive ? "dialog" : "tooltip"} aria-label={interactive ? helpLabel : undefined} data-testid={`${testId ?? `help-${id.replace(/:/g, "").toLowerCase()}`}-popover`} className="sicost-help-tooltip" onBlur={e => { if (!button.current?.contains(e.relatedTarget as Node) && !e.currentTarget.contains(e.relatedTarget as Node)) close(); }} data-side={position.above ? "top" : "bottom"} style={{ position: "fixed", top: position.top, left: position.left, "--help-arrow": `${position.arrow}px` } as React.CSSProperties}>{children}</div>, document.body)}
  </span>;
}
