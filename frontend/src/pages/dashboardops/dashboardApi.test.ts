import { afterEach, expect, it, vi } from "vitest";
import { getDashboard } from "./dashboardApi";
import { dashboardFixture } from "./dashboardFixture";
import { seriesChartOptions } from "../../shared/charts/chartAdapters";

afterEach(() => vi.unstubAllGlobals());
it.each(["2027-01", "2027-02"])(
  "%s 최근 3개월은 같은 권한 API의 전년도 원본 행을 사용한다",
  async (selectedMonth) => {
    const original = dashboardFixture();
    const current = {
      ...original,
      selectedMonth,
      cashBaseMonth: "2026-01",
      salesCostTrend: original.salesCostTrend!.map((row) => ({
        ...row,
        month: row.month.replace("2026", "2027"),
      })),
    };
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ success: true, meta: {}, data: current }),
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ success: true, meta: {}, data: original }),
        ),
      );
    vi.stubGlobal("fetch", fetch);
    const signal = new AbortController().signal;
    const result = await getDashboard(selectedMonth, signal);
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(fetch.mock.calls.map(([url]) => url)).toEqual([
      `/api/dashboard?month=${selectedMonth}`,
      "/api/dashboard?month=2026-12",
    ]);
    expect(
      fetch.mock.calls.every(([, options]) => options.signal === signal),
    ).toBe(true);
    expect(result.selectedMonth).toBe(selectedMonth);
    expect(result.metrics).toEqual(current.metrics);
    const history = original.salesCostTrend!.filter(
      (row) =>
        row.month >= (selectedMonth.endsWith("01") ? "2026-11" : "2026-12"),
    );
    expect(result.salesCostTrend).toEqual([
      ...history,
      ...current.salesCostTrend,
    ]);
  },
);
it("운영 시작 이전의 기간과 권한상 생략된 추이는 추가 조회하지 않는다", async () => {
  const data = { ...dashboardFixture(), selectedMonth: "2026-01" };
  const fetch = vi
    .fn()
    .mockResolvedValue(
      new Response(JSON.stringify({ success: true, meta: {}, data })),
    );
  vi.stubGlobal("fetch", fetch);
  await getDashboard(data.selectedMonth);
  expect(fetch).toHaveBeenCalledTimes(1);
  fetch.mockResolvedValueOnce(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: { ...data, selectedMonth: "2027-01", salesCostTrend: undefined },
      }),
    ),
  );
  await getDashboard("2027-01");
  expect(fetch).toHaveBeenCalledTimes(2);
});
it("대시보드 선은 실측 점·미확인 공백·점선을 유지하며 잘리는 끝값 라벨만 생략한다", () => {
  const rows = dashboardFixture().salesCostTrend!;
  const option = seriesChartOptions({
    categories: rows.map((row) => row.month),
    axisUnit: "천원",
    edgeLabelsInside: true,
    formatValue: String,
    series: [
      {
        name: "매출",
        values: rows.map((row) => row.salesAmount),
        showEndLabel: false,
      },
      {
        name: "최소 자금",
        values: rows.map((row) => row.salesTargetAmount),
        dashed: true,
      },
    ],
  });
  const series = option.series as {
    connectNulls: boolean;
    showSymbol: boolean;
    endLabel: { show: boolean };
    lineStyle: { type: string | number[] };
    data: (number | null)[];
  }[];
  expect(series[0].connectNulls).toBe(false);
  expect(series[0].showSymbol).toBe(true);
  expect(series[0].endLabel.show).toBe(false);
  expect(series[0].lineStyle.type).toBe("solid");
  expect(series[0].data).toContain(null);
  expect(series[1].lineStyle.type).toEqual([6, 4]);
  expect(
    (
      option.xAxis as {
        axisLabel: { alignMinLabel: string; alignMaxLabel: string };
      }
    ).axisLabel,
  ).toMatchObject({ alignMinLabel: "left", alignMaxLabel: "right" });
});
