import { useEffect, type RefObject } from "react";

const layers: { element: HTMLElement; priority: number }[] = [];
let previousOverflow = "";

function topLayer() {
  return layers.reduce<(typeof layers)[number] | undefined>(
    (top, layer) => (!top || layer.priority >= top.priority ? layer : top),
    undefined,
  );
}

export function canFocusOverlayContent(element: HTMLElement) {
  const active = topLayer();
  return !active || active.element.contains(element);
}

/** Shared lock and topmost-layer arbitration for nested portals. No business dirty state lives here. */
export function useOverlayLayer(
  ref: RefObject<HTMLElement>,
  open: boolean,
  priority = 60,
) {
  useEffect(() => {
    const element = ref.current;
    if (!open || !element) return;
    if (!layers.length) {
      previousOverflow = document.body.style.overflow;
      document.body.style.overflow = "hidden";
    }
    const layer = { element, priority };
    layers.push(layer);
    return () => {
      const index = layers.indexOf(layer);
      if (index >= 0) layers.splice(index, 1);
      if (!layers.length) document.body.style.overflow = previousOverflow;
    };
  }, [open, ref, priority]);
  return () => topLayer()?.element === ref.current;
}
