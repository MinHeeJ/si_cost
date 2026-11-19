import type { EChartsOption, SeriesOption } from "echarts";
import { formatAmount, type AmountUnit } from "../format";
export type ChartSeries = {
  name: string;
  values: readonly (string | null)[];
  kind?: "line" | "bar";
  dashed?: boolean;
  stack?: string;
  color?: string;
};
// Coordinate-only conversion: original amount strings remain in tooltip closures and tables.
export function chartCoordinate(
  value: string | null,
  scale = 1_000_000,
): number | null {
  if (value === null || !/^-?\d+(?:\.\d+)?$/.test(value)) return null;
  const n = Number(value) / scale;
  return Number.isFinite(n) ? n : null;
}
export function seriesOptions(
  labels: readonly string[],
  series: readonly ChartSeries[],
  unit: AmountUnit = "백만원",
  scale = 1_000_000,
): EChartsOption {
  return {
    xAxis: { data: [...labels] },
    tooltip: {
      formatter: (input: unknown) => {
        const entries = Array.isArray(input) ? input : [input];
        const index = (entries[0] as { dataIndex?: number })?.dataIndex;
        if (index === undefined) return "";
        return [
          labels[index],
          ...series.map(
            (s) =>
              `${s.name}  ${formatAmount(s.values[index] ?? null, unit)} ${unit}`,
          ),
        ].join("\n");
      },
    },
    series: series.map((s) => ({
      name: s.name,
      type: s.kind ?? "line",
      data: s.values.map((v) => chartCoordinate(v, scale)),
      connectNulls: false,
      stack: s.stack,
      smooth: false,
      symbol: "circle",
      symbolSize: 5,
      showSymbol: true,
      ...(s.color ? { itemStyle: { color: s.color } } : {}),
      lineStyle: { width: 2, type: s.dashed ? "dashed" : "solid" },
      emphasis: { focus: "series" },
      barMaxWidth: 28,
    })) as SeriesOption[],
  };
}
