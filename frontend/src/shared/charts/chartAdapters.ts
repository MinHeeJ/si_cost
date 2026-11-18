import type { EChartsCoreOption } from "echarts/core";
import type { CustomSeriesRenderItem } from "echarts";
import { chartColors, chartTheme } from "./chartTheme";

export type ChartSeries = {
  name: string;
  values: (string | null)[];
  type?: "line" | "bar";
  dashed?: boolean;
  stack?: string;
  color?: string;
};
// Only the coordinate is rounded. Original API strings always feed tooltips and numeric tables.
export function decimalCoordinate(
  raw: string | null,
  power = 0,
): number | null {
  if (raw == null || !/^-?\d+(\.\d+)?$/.test(raw)) return null;
  const negative = raw.startsWith("-"),
    [whole, decimal = ""] = raw.replace(/^-/, "").split(".");
  const digits = (whole + decimal).replace(/^0+/, "");
  if (!digits) return 0;
  return Number(
    `${negative ? "-" : ""}${digits.slice(0, 15)}e${digits.length - Math.min(15, digits.length) - decimal.length - power}`,
  );
}
export function coordinatePower(series: ChartSeries[]) {
  return Math.max(
    0,
    ...series.flatMap((s) =>
      s.values.map((value) =>
        value == null
          ? 0
          : value.replace(/^-/, "").split(".")[0].replace(/^0+/, "").length -
            12,
      ),
    ),
  );
}
export function seriesChartOptions({
  categories,
  series,
  formatValue,
  axisUnit = "",
  guide,
}: {
  categories: string[];
  series: ChartSeries[];
  formatValue(value: string | null): string;
  axisUnit?: string;
  guide?: { name: string; value: string };
}): EChartsCoreOption {
  const unitPower = axisUnit === "백만원" ? 6 : axisUnit === "천원" ? 3 : 0;
  const power = Math.max(
      unitPower,
      coordinatePower(
        guide
          ? [...series, { name: guide.name, values: [guide.value] }]
          : series,
      ),
    ),
    scaleLabel = power > unitPower ? ` ×10^${power - unitPower}` : "";
  return {
    ...chartTheme,
    xAxis: {
      type: "category",
      data: categories,
      boundaryGap: series.some((s) => s.type === "bar"),
      axisLine: { lineStyle: { color: "#D5DAE1" } },
      axisTick: { show: false },
    },
    yAxis: {
      type: "value",
      name: `${axisUnit}${scaleLabel}`,
      splitLine: { lineStyle: { color: "#E7EAEE", type: "dashed" } },
    },
    tooltip: {
      ...chartTheme.tooltip,
      formatter: (parameters: unknown) => {
        const items = (
          Array.isArray(parameters) ? parameters : [parameters]
        ) as { dataIndex?: number; seriesIndex?: number }[];
        const index = items[0]?.dataIndex ?? 0,
          box = document.createElement("div");
        const heading = document.createElement("strong");
        heading.textContent = categories[index] ?? "";
        box.append(heading);
        series.forEach((s, i) => {
          const row = document.createElement("div");
          row.style.cssText =
            "display:flex;gap:16px;justify-content:space-between;margin-top:6px";
          const label = document.createElement("span");
          label.textContent = s.name;
          label.style.color = s.color ?? chartColors[i % chartColors.length];
          const value = document.createElement("b");
          value.textContent = formatValue(s.values[index] ?? null);
          row.append(label, value);
          box.append(row);
        });
        return box;
      },
    },
    series: series.map((s, i) => ({
      name: s.name,
      type: s.type ?? "line",
      data: s.values.map((value) => decimalCoordinate(value, power)),
      stack: s.stack,
      connectNulls: false,
      symbol: "circle",
      symbolSize: 5,
      showSymbol: true,
      smooth: false,
      barMaxWidth: 28,
      itemStyle: { color: s.color ?? chartColors[i % chartColors.length] },
      lineStyle: { width: 2.5, type: s.dashed ? "dashed" : "solid" },
      emphasis: { focus: "series" },
      ...(i === 0 && guide
        ? {
            markLine: {
              symbol: "none",
              label: { formatter: guide.name },
              lineStyle: { type: "dashed", color: "#94A3B8" },
              data: [
                {
                  yAxis: decimalCoordinate(guide.value, power),
                  name: guide.name,
                },
              ],
            },
          }
        : {}),
    })),
  };
}

export function waterfallChartOptions(
  steps: {
    label: string;
    value: string | null;
    from: string | null;
    to: string | null;
    subtotal: boolean;
  }[],
  formatValue: (value: string | null) => string,
): EChartsCoreOption {
  const power = Math.max(
    6,
    coordinatePower([
      { name: "range", values: steps.flatMap((step) => [step.from, step.to]) },
    ]),
  );
  const renderItem: CustomSeriesRenderItem = (params, api) => {
    const step = steps[params.dataIndex];
    if (step.from == null || step.to == null) return;
    const from = api.coord([
        params.dataIndex,
        decimalCoordinate(step.from, power)!,
      ]),
      to = api.coord([params.dataIndex, decimalCoordinate(step.to, power)!]);
    const size = api.size?.([1, 0]) as number[] | undefined,
      width = Math.min(56, (size?.[0] ?? 48) * 0.65);
    return {
      type: "rect",
      shape: {
        x: from[0] - width / 2,
        y: Math.min(from[1], to[1]),
        width,
        height: Math.max(1, Math.abs(to[1] - from[1])),
      },
      style: {
        fill: step.subtotal
          ? chartColors[0]
          : step.value?.startsWith("-")
            ? "#B91C1C"
            : chartColors[1],
      },
    };
  };
  return {
    ...chartTheme,
    legend: { show: false },
    xAxis: {
      type: "category",
      data: steps.map((step) => step.label),
      axisLabel: { interval: 0, rotate: 15 },
      axisTick: { show: false },
    },
    yAxis: {
      type: "value",
      name: power === 6 ? "백만원" : `원 ×10^${power}`,
      splitLine: { lineStyle: { color: "#E7EAEE", type: "dashed" } },
    },
    tooltip: {
      ...chartTheme.tooltip,
      trigger: "item",
      formatter: (params: unknown) => {
        const index = (params as { dataIndex: number }).dataIndex,
          box = document.createElement("div");
        box.textContent = `${steps[index].label}: ${formatValue(steps[index].value)}`;
        return box;
      },
    },
    series: [
      {
        type: "custom",
        renderItem,
        encode: { x: 0, y: [1, 2] },
        data: steps.map((step, index) => [
          index,
          decimalCoordinate(step.from, power),
          decimalCoordinate(step.to, power),
        ]),
      },
    ],
  };
}
