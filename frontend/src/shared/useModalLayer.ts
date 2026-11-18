import { useEffect, useRef, type RefObject } from "react";

const focusable =
  'button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],summary,[tabindex="0"]';
const layers: HTMLElement[] = [];
let originalOverflow = "";
function visible(node: HTMLElement) {
  for (
    let parent: HTMLElement | null = node;
    parent;
    parent = parent.parentElement
  ) {
    const style = getComputedStyle(parent);
    if (
      parent.hidden ||
      parent.getAttribute("aria-hidden") === "true" ||
      style.display === "none" ||
      style.visibility === "hidden"
    )
      return false;
  }
  return true;
}
function controls(element: HTMLElement) {
  return [...element.querySelectorAll<HTMLElement>(focusable)].filter(
    (node) => !node.matches(':disabled,[tabindex="-1"]') && visible(node),
  );
}

/** A single focus/scroll stack shared by drawers, forms and nested confirmations. */
export function useModalLayer(
  ref: RefObject<HTMLElement>,
  open: boolean,
  onEscape: () => void,
  initialFocus?: () => HTMLElement | null | undefined,
) {
  const escape = useRef(onEscape),
    initial = useRef(initialFocus);
  escape.current = onEscape;
  initial.current = initialFocus;
  useEffect(() => {
    const element = ref.current;
    if (!open || !element) return;
    const previous = document.activeElement as HTMLElement | null;
    if (!layers.length) {
      originalOverflow = document.body.style.overflow;
      document.body.style.overflow = "hidden";
    }
    layers.push(element);
    const top = () => layers.at(-1) === element;
    const first = () => {
      const preferred = initial.current?.();
      return preferred && visible(preferred)
        ? preferred
        : (controls(element)[0] ?? element);
    };
    first().focus();
    const focus = (event: FocusEvent) => {
      if (top() && !element.contains(event.target as Node)) first().focus();
    };
    const key = (event: KeyboardEvent) => {
      if (!top() || event.defaultPrevented) return;
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        escape.current();
      } else if (event.key === "Tab") {
        const nodes = controls(element),
          index = nodes.indexOf(document.activeElement as HTMLElement);
        if (!nodes.length) {
          event.preventDefault();
          element.focus();
        } else if (event.shiftKey && index <= 0) {
          event.preventDefault();
          nodes.at(-1)?.focus();
        } else if (
          !event.shiftKey &&
          (index < 0 || index === nodes.length - 1)
        ) {
          event.preventDefault();
          nodes[0].focus();
        }
      }
    };
    document.addEventListener("focusin", focus);
    document.addEventListener("keydown", key);
    return () => {
      document.removeEventListener("focusin", focus);
      document.removeEventListener("keydown", key);
      const wasTop = top();
      const index = layers.indexOf(element);
      if (index >= 0) layers.splice(index, 1);
      if (!layers.length) document.body.style.overflow = originalOverflow;
      if (wasTop) {
        const parent = layers.at(-1);
        if (previous?.isConnected && (!parent || parent.contains(previous)))
          previous.focus();
        else if (parent) (controls(parent)[0] ?? parent).focus();
      }
    };
  }, [open, ref]);
  return () => layers.at(-1) === ref.current;
}
