import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { DashboardSupplyChart } from "./DashboardSupplyChart";
import { ChartHost } from "../../shared/charts/ChartHost";
import type { EChartsOption } from "echarts";
vi.mock("../../shared/charts/ChartHost", () => ({
  ChartHost: vi.fn(({ fallback }) => <div>{fallback}</div>),
}));

it("서버 수급·차이를 그대로 표시하며 null·0·음수와 정확한 인원 단위를 보존한다", () => {
  const rows = [
    {
      month: "2026-11",
      availablePersons: "0",
      requiredPersons: null,
      differencePersons: "-2.5",
    },
    {
      month: "2026-12",
      availablePersons: "9007199254740993.1",
      requiredPersons: "2.5",
      differencePersons: "8.2",
    },
  ];
  const original = JSON.stringify(rows);
  render(<DashboardSupplyChart rows={rows} />);
  const { option } = vi.mocked(ChartHost).mock.calls.at(-1)![0];
  const series = option.series as { data: (number | null)[]; type: string }[];
  expect(series.map((item) => item.type)).toEqual(["bar", "bar"]);
  expect(series[0].data[0]).toBe(0);
  expect(series[1].data[0]).toBeNull();
  const tooltip = (option.tooltip as { formatter: (input: unknown) => string })
    .formatter;
  expect(tooltip([{ dataIndex: 0 }])).toBe(
    "2026-11\n가용 0.0명\n필요 –명\n차이 −2.5명",
  );
  expect(tooltip([{ dataIndex: 1 }])).toContain("9,007,199,254,740,993.1명");
  expect(tooltip([{ dataIndex: 1 }])).toContain("차이 8.2명");
  expect(JSON.stringify(rows)).toBe(original);
  expect(screen.getByRole("note")).toHaveTextContent("아래 숫자표");
  expect((option as EChartsOption).yAxis).toEqual({ name: "명" });
});
