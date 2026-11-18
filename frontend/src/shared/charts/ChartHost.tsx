import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  init,
  use,
  type EChartsType,
  type EChartsCoreOption,
} from "echarts/core";
import { LineChart, BarChart, PieChart, CustomChart } from "echarts/charts";
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  MarkLineComponent,
} from "echarts/components";
import { SVGRenderer } from "echarts/renderers";
import { useReducedMotion } from "../useReducedMotion";

use([
  LineChart,
  BarChart,
  PieChart,
  CustomChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  MarkLineComponent,
  SVGRenderer,
]);

export function ChartHost({
  option,
  children,
  height = 260,
  testId,
}: {
  option: EChartsCoreOption;
  children: ReactNode;
  height?: number;
  testId?: string;
}) {
  const element = useRef<HTMLDivElement>(null),
    chart = useRef<EChartsType>(),
    options = useRef(option);
  const reduced = useReducedMotion(),
    [ready, setReady] = useState(false);
  options.current = {
    ...option,
    animation: !reduced,
    animationDuration: reduced ? 0 : 700,
    animationDurationUpdate: reduced ? 0 : 400,
  };
  useEffect(() => {
    const target = element.current;
    if (!target || typeof ResizeObserver === "undefined") return;
    let disposed = false;
    const resize = () => {
      if (disposed || !target.clientWidth || !target.clientHeight) return;
      try {
        if (!chart.current) {
          chart.current = init(target, undefined, { renderer: "svg" });
          chart.current.setOption(options.current);
        }
        chart.current.resize();
        setReady(true);
      } catch {
        chart.current?.dispose();
        chart.current = undefined;
        setReady(false);
      }
    };
    const observer = new ResizeObserver(resize);
    observer.observe(target);
    resize();
    document.addEventListener("visibilitychange", resize);
    return () => {
      disposed = true;
      observer.disconnect();
      document.removeEventListener("visibilitychange", resize);
      chart.current?.dispose();
      chart.current = undefined;
    };
  }, []);
  useEffect(() => {
    chart.current?.setOption(options.current, {
      replaceMerge: ["series"],
      lazyUpdate: true,
    });
  }, [option, reduced]);
  return (
    <div className="sicost-chart-host" data-testid={testId}>
      <div
        ref={element}
        aria-hidden="true"
        style={{
          height,
          width: "100%",
          display: typeof ResizeObserver === "undefined" ? "none" : undefined,
        }}
      />
      <div className={ready ? "sicost-sr-only" : undefined}>{children}</div>
    </div>
  );
}
