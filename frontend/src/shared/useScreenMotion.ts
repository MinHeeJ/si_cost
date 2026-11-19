import { useEffect, useRef } from "react";
/** Adds presentation markers without replacing screen components, form state or cached data. */
export function useScreenMotion(screenId: string) {
  const root = useRef<HTMLElement>(null);
  useEffect(() => {
    const target = root.current;
    if (!target) return;
    const body = target.querySelector(".sicost-screen-content");
    if (!body) return;
    let previous = "", frame = 0;
    const mark = () => {
      const nodes = [...body.querySelectorAll<HTMLElement>(".sicost-kpi-strip,.sicost-filterbar,.sicost-grid,.sicost-section-card,.dashboard-metrics,.dashboard-panel,.dashboard-dates,.operating-table-scroll,.profit-structure-card,.workforce-calendar-summary")];
      nodes.filter(n => !nodes.some(parent => parent !== n && parent.contains(n))).forEach((node, index) => {
        node.style.setProperty("--entry-delay", `${Math.min(index, 5) * 40}ms`);
        node.classList.add("sicost-enter-stage");
      });
    };
    const period = (event: Event) => {
      const d = (event as CustomEvent).detail;
      if (d?.screenId !== screenId) return;
      const next = JSON.stringify(d.value);
      if (next === previous) return;
      const replay = Boolean(previous); previous = next;
      if (!replay) return;
      body.querySelectorAll(".sicost-enter-stage").forEach(n => n.classList.remove("sicost-enter-stage"));
      target.classList.add("sicost-motion-reset");
      void target.offsetWidth;
      cancelAnimationFrame(frame); frame = requestAnimationFrame(() => { target.classList.remove("sicost-motion-reset"); mark(); });
    };
    const main = target.closest<HTMLElement>(".sicost-main");
    const scroll = () => {
      if (!main) return;
      target.style.setProperty("--title-shadow", String(Math.min(1, main.scrollTop / 48)));
      target.style.setProperty("--read-progress", String(main.scrollTop / Math.max(1, main.scrollHeight - main.clientHeight)));
    };
    mark(); scroll();
    const observer = new MutationObserver(mark); observer.observe(body, { childList: true, subtree: true });
    window.addEventListener("sicost:screen-period-changed", period); main?.addEventListener("scroll", scroll, { passive: true });
    return () => { observer.disconnect(); cancelAnimationFrame(frame); window.removeEventListener("sicost:screen-period-changed", period); main?.removeEventListener("scroll", scroll); };
  }, [screenId]);
  return root;
}
