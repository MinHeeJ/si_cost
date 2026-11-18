import type { EChartsCoreOption } from "echarts/core";
import {
  formatAmount,
  formatFixedDecimal,
  formatRate,
  type AmountUnit,
} from "../format";
import { chartColors } from "./chartTheme";

export type ChartSeries = {
  name: string;
  values: readonly (string | null)[];
  type?: "line" | "bar";
  dashed?: boolean;
  stack?: string;
  color?: string;
};

/** Bounded chart coordinates only. The original strings remain the sole tooltip/table values. */
export function chartCoordinates(values: readonly (string | null)[]) {
  const digits = values.reduce(
    (max, value) =>
      Math.max(max, value?.replace(/^-/, "").split(".")[0].length ?? 0),
    0,
  );
  const scale = 10n ** BigInt(Math.max(6, digits - 9));
  const point = (value: string | null): number | null => {
    if (value === null || !/^-?\d+(\.\d+)?$/.test(value)) return null;
    const negative = value.startsWith("-"),
      [whole, fraction = ""] = value.replace(/^-/, "").split(".");
    const raw =
      BigInt(whole) * 1_000_000n + BigInt(fraction.padEnd(6, "0").slice(0, 6));
    return (Number(raw / scale) / 1_000_000) * (negative ? -1 : 1);
  };
  const axis = (value: number) =>
    formatAmount(
      String((BigInt(Math.round(value * 1000)) * scale) / 1000n),
      "백만원",
    );
  return { point, axis };
}

function tooltipText(text: string) {
  // richText rendering never interprets HTML; prevent accidental rich-text style tokens as well.
  return text.replace(
    /[{}|]/g,
    (character) => ({ "{": "｛", "}": "｝", "|": "｜" })[character]!,
  );
}

export function seriesOption(
  categories: readonly string[],
  series: readonly ChartSeries[],
  unit: AmountUnit = "백만원",
  minimum?: string,
): EChartsCoreOption {
  const coordinates = chartCoordinates([
    ...series.flatMap((item) => item.values),
    minimum ?? null,
  ]);
  return {
    tooltip: {
      formatter: (params: unknown) => {
        const points = (Array.isArray(params) ? params : [params]) as {
          dataIndex: number;
          seriesIndex: number;
        }[];
        const index = points[0]?.dataIndex;
        if (index == null) return "";
        return [
          tooltipText(categories[index] ?? ""),
          ...series.map(
            (item) =>
              `${tooltipText(item.name)}  ${formatAmount(item.values[index] ?? null, unit)} ${unit}`,
          ),
        ].join("\n");
      },
    },
    xAxis: { data: categories },
    yAxis: { axisLabel: { formatter: coordinates.axis } },
    series: series.map((item, index) => ({
      name: item.name,
      type: item.type ?? "line",
      stack: item.stack,
      data: item.values.map(coordinates.point),
      connectNulls: false,
      color: item.color ?? chartColors[index % chartColors.length],
      showSymbol: true,
      symbol: "circle",
      symbolSize: 5,
      lineStyle: { width: 2, type: item.dashed ? "dashed" : "solid" },
      barMaxWidth: 28,
      itemStyle: { borderRadius: item.type === "bar" ? [3, 3, 0, 0] : 0 },
      ...(index === 0 && minimum !== undefined
        ? {
            markLine: {
              symbol: "none",
              silent: true,
              data: [
                { yAxis: coordinates.point(minimum), name: "최소 운영자금" },
              ],
              label: { formatter: "최소 운영자금", position: "insideEndTop" },
              lineStyle: { color: "#b45309", type: "dashed" },
            },
          }
        : {}),
    })),
  };
}

export type WaterfallValue = {
  label: string;
  amount: string | null;
  from: bigint | null;
  to: bigint | null;
  subtotal: boolean;
};
/** Server-provided ratios only: no client-side normalization, omitted groups or fabricated remainder. */
export function ratioOption(
  rows: readonly { label: string; share: string | null }[],
): EChartsCoreOption {
  return {
    legend: { show: false },
    grid: { left: 16, right: 40, top: 16, bottom: 24, containLabel: true },
    xAxis: { type: "value", axisLabel: { formatter: "{value}%" } },
    yAxis: {
      type: "category",
      inverse: true,
      data: rows.map((row) => row.label),
      axisTick: { show: false },
      axisLine: { show: false },
    },
    tooltip: {
      trigger: "item",
      formatter: (params: unknown) => {
        const row = rows[(params as { dataIndex: number }).dataIndex];
        return row ? `${tooltipText(row.label)}  ${formatRate(row.share)}` : "";
      },
    },
    series: [
      {
        type: "bar",
        barMaxWidth: 18,
        data: rows.map((row, index) => ({
          value: row.share === null ? null : Number(row.share),
          itemStyle: {
            color: row.share?.startsWith("-")
              ? "#b91c1c"
              : chartColors[index % chartColors.length],
            borderRadius: 3,
          },
        })),
      },
    ],
  };
}
export function quantityOption(
  categories: readonly string[],
  series: readonly ChartSeries[],
  suffix: string,
): EChartsCoreOption {
  const option = seriesOption(categories, series);
  return {
    ...option,
    yAxis: {
      type: "value",
      axisLabel: { formatter: (value: number) => `${value}${suffix}` },
    },
    tooltip: {
      formatter: (params: unknown) => {
        const point = (Array.isArray(params) ? params[0] : params) as
          | { dataIndex: number }
          | undefined;
        if (!point) return "";
        return [
          categories[point.dataIndex],
          ...series.map(
            (item) =>
              `${item.name}  ${formatFixedDecimal(item.values[point.dataIndex] ?? null, 1)}${suffix}`,
          ),
        ]
          .map(tooltipText)
          .join("\n");
      },
    },
    series: series.map((item, index) => ({
      name: item.name,
      type: item.type ?? "bar",
      stack: item.stack,
      data: item.values.map((value) => (value === null ? null : Number(value))),
      connectNulls: false,
      itemStyle: {
        color: item.color ?? chartColors[index % chartColors.length],
        borderRadius: [3, 3, 0, 0],
      },
      barMaxWidth: 28,
      lineStyle: { type: item.dashed ? "dashed" : "solid" },
    })),
  };
}
export function waterfallOption(
  steps: readonly WaterfallValue[],
  unit: AmountUnit,
): EChartsCoreOption {
  const coordinates = chartCoordinates(
    steps.flatMap((item) => [
      item.from?.toString() ?? null,
      item.to?.toString() ?? null,
    ]),
  );
  return {
    xAxis: {
      data: steps.map((item) => item.label),
      axisLabel: { interval: 0, rotate: 20 },
    },
    yAxis: { axisLabel: { formatter: coordinates.axis } },
    tooltip: {
      trigger: "item",
      formatter: (params: unknown) => {
        const item = steps[(params as { dataIndex: number }).dataIndex];
        return item
          ? `${tooltipText(item.label)}\n${formatAmount(item.amount, unit)} ${unit}`
          : "";
      },
    },
    series: [
      {
        type: "custom",
        data: steps.map((item, index) => [
          index,
          coordinates.point(item.from?.toString() ?? null),
          coordinates.point(item.to?.toString() ?? null),
        ]),
        renderItem: (
          _params: unknown,
          api: {
            value(index: number): number;
            coord(value: number[]): number[];
            size(value: number[]): number[];
          },
        ) => {
          const index = api.value(0),
            item = steps[index];
          if (!item || item.from === null || item.to === null) return;
          const start = api.coord([index, api.value(1)]),
            end = api.coord([index, api.value(2)]);
          const width = Math.min(48, api.size([1, 0])[0] * 0.65);
          return {
            type: "rect",
            shape: {
              x: start[0] - width / 2,
              y: Math.min(start[1], end[1]),
              width,
              height: Math.max(1, Math.abs(end[1] - start[1])),
            },
            style: {
              fill: item.subtotal
                ? chartColors[0]
                : item.to < item.from
                  ? "#b91c1c"
                  : chartColors[1],
            },
          };
        },
      },
    ],
  };
}
