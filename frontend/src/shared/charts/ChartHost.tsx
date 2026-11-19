import { useEffect, useRef, useState, type ReactNode } from "react";
import { init, use } from "echarts/core";
import type { EChartsOption } from "echarts";
import { LineChart, BarChart, CustomChart } from "echarts/charts";
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  MarkLineComponent,
  AriaComponent,
} from "echarts/components";
import { SVGRenderer } from "echarts/renderers";
import { useReducedMotion } from "../useReducedMotion";
import { chartTheme } from "./chartTheme";
use([
  LineChart,
  BarChart,
  CustomChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  MarkLineComponent,
  AriaComponent,
  SVGRenderer,
]);
export function ChartHost({
  option,
  label,
  fallback,
  testId,
  height = 280,
  heading,
  className,
}: {
  option: EChartsOption;
  label: string;
  fallback: ReactNode;
  testId: string;
  height?: number;
  heading?: ReactNode;
  className?: string;
}) {
  const host = useRef<HTMLDivElement>(null);
  const [failed, setFailed] = useState(false);
  const reduced = useReducedMotion();
  const chart = useRef<ReturnType<typeof init>>();
  useEffect(() => {
    // jsdom and unavailable SVG renderers use the existing accessible SVG/table.
    if (!host.current || typeof ResizeObserver === "undefined") {
      setFailed(true);
      return;
    }
    let observer: ResizeObserver | undefined;
    try {
      const instance = init(host.current, chartTheme, { renderer: "svg" });
      chart.current = instance;
      observer = new ResizeObserver(() => {
        if (host.current?.clientWidth) instance.resize();
      });
      observer.observe(host.current);
      const resize = () => instance.resize();
      window.addEventListener("resize", resize);
      return () => {
        window.removeEventListener("resize", resize);
        observer?.disconnect();
        instance.dispose();
        chart.current = undefined;
      };
    } catch {
      observer?.disconnect();
      setFailed(true);
    }
  }, []);
  useEffect(() => {
    try {
      chart.current?.setOption(
        { ...option, animation: !reduced, aria: { enabled: true } },
        { notMerge: true },
      );
    } catch {
      setFailed(true);
    }
  }, [option, reduced]);
  return (
    <div
      data-testid={testId}
      className={`sicost-chart-host${!failed && className ? ` ${className}` : ""}`}
    >
      {!failed && heading}
      {!failed && (
        <div
          ref={host}
          role="img"
          aria-label={label}
          style={{ width: "100%", height, minWidth: 0 }}
        />
      )}
      <div hidden={!failed}>{fallback}</div>
    </div>
  );
}
