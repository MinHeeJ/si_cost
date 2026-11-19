import { useEffect, type KeyboardEvent, type RefObject } from "react";
const focusable =
  'button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex="0"]';
let locks = 0;
let originalOverflow = "";
function topDialog() {
  return [
    ...document.querySelectorAll<HTMLElement>(
      '[role="dialog"][aria-modal="true"]',
    ),
  ]
    .filter(
      (node) => !node.closest("[hidden]") && node.style.display !== "none",
    )
    .at(-1);
}
export function useOverlay(ref: RefObject<HTMLElement>, active: boolean) {
  useEffect(() => {
    if (!active) return;
    if (locks++ === 0) {
      originalOverflow = document.body.style.overflow;
      document.body.style.overflow = "hidden";
    }
    return () => {
      if (--locks === 0) document.body.style.overflow = originalOverflow;
    };
  }, [active]);
  return (event: KeyboardEvent) => {
    if (topDialog() !== ref.current) return false;
    if (event.key === "Tab") {
      const nodes = [
        ...(ref.current?.querySelectorAll<HTMLElement>(focusable) ?? []),
      ].filter(
        (node) =>
          !node.closest("[hidden]") &&
          node.style.display !== "none" &&
          node.getAttribute("aria-hidden") !== "true",
      );
      const first = nodes[0],
        last = nodes.at(-1);
      if (!first) {
        event.preventDefault();
        ref.current?.focus();
      } else if (
        event.shiftKey &&
        (document.activeElement === first ||
          document.activeElement === ref.current)
      ) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    return true;
  };
}
