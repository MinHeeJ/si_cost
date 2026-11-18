import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  init,
  use,
  type EChartsCoreOption,
  type EChartsType,
} from "echarts/core";
import { BarChart, LineChart, CustomChart } from "echarts/charts";
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  MarkLineComponent,
} from "echarts/components";
import { SVGRenderer } from "echarts/renderers";
import { chartTheme } from "./chartTheme";
import { useReducedMotion } from "../useReducedMotion";

use([
  BarChart,
  LineChart,
  CustomChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  MarkLineComponent,
  SVGRenderer,
]);

export function ChartHost({
  option,
  label,
  children,
  testId,
  height = 300,
}: {
  option: EChartsCoreOption;
  label: string;
  children: ReactNode;
  testId: string;
  height?: number;
}) {
  const node = useRef<HTMLDivElement>(null),
    chart = useRef<EChartsType>();
  const latest = useRef(option),
    reduced = useReducedMotion(),
    motion = useRef(reduced);
  latest.current = option;
  motion.current = reduced;
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const element = node.current;
    if (!element) return;
    const resize = () => {
      if (
        !element.getBoundingClientRect().width ||
        !element.getBoundingClientRect().height
      )
        return;
      try {
        if (!chart.current) {
          chart.current = init(element, chartTheme, { renderer: "svg" });
          chart.current.setOption({
            ...latest.current,
            animation: !motion.current,
          });
        }
        chart.current.resize();
        setReady(true);
      } catch {
        chart.current?.dispose();
        chart.current = undefined;
        setReady(false);
      }
    };
    const observer =
      typeof ResizeObserver === "undefined"
        ? undefined
        : new ResizeObserver(resize);
    observer?.observe(element);
    window.addEventListener("resize", resize);
    document.addEventListener("visibilitychange", resize);
    resize();
    return () => {
      observer?.disconnect();
      window.removeEventListener("resize", resize);
      document.removeEventListener("visibilitychange", resize);
      chart.current?.dispose();
      chart.current = undefined;
    };
  }, []);
  useEffect(() => {
    try {
      chart.current?.setOption(
        {
          ...option,
          animation: !reduced,
          animationDuration: reduced ? 0 : 700,
          animationDurationUpdate: reduced ? 0 : 400,
        },
        { notMerge: true },
      );
    } catch {
      chart.current?.dispose();
      chart.current = undefined;
      setReady(false);
    }
  }, [option, reduced]);
  return (
    <div className="sicost-chart-host" data-testid={testId}>
      <div
        ref={node}
        className="sicost-chart-canvas"
        role={ready ? "img" : undefined}
        aria-label={ready ? label : undefined}
        aria-hidden={!ready}
        style={
          ready
            ? { height }
            : { height, position: "absolute", visibility: "hidden" }
        }
      />
      <div className="sicost-chart-fallback" hidden={ready}>
        {children}
      </div>
    </div>
  );
}
