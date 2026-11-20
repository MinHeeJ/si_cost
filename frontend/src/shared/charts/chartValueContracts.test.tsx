import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import type { ReactElement } from "react";
import { SalesForecastPanel } from "../../pages/forecast/SalesForecastScreen";
import * as sales from "../../pages/forecast/salesForecastApi";
import { WorkforceForecastPanel } from "../../pages/workforceforecastsops/WorkforceDemandForecastScreen";
import * as workforce from "../../pages/workforceforecastsops/workforceForecastApi";
import { BusinessPlanRevenueChart } from "../../pages/businessplansops/BusinessPlanRevenueChart";
import type { PlanMonth } from "../../pages/businessplansops/businessPlanPerformanceApi";
import { StructureWaterfall } from "../../pages/structure/ProfitStructureCharts";
import { formatAmount } from "../format";
import { seriesChartOptions } from "./chartAdapters";

type ChartOption = {
  xAxis: { axisLabel: { formatter(label: string): string } };
  yAxis: { name: string };
  series: { data: (number | null)[]; renderItem?: (params: {dataIndex: number}, api: unknown) => {type: string; style?: {text?: string}; children?: {type: string; style: {text?: string}}[]} }[];
  tooltip: { formatter(parameters: unknown): HTMLElement };
};
const charts = vi.hoisted(() => new Map<string, ChartOption>());
vi.mock("./ChartHost", () => ({
  ChartHost: ({ option, children }: { option: ChartOption; children: ReactElement<{"aria-label": string}> }) => {
    charts.set(children.props["aria-label"], option);
    return children;
  },
}));
vi.mock("../../pages/forecast/salesForecastApi", async original => ({
  ...(await original<typeof import("../../pages/forecast/salesForecastApi")>()),
  getSalesOpportunityForecast: vi.fn(),
}));
vi.mock("../../pages/workforceforecastsops/workforceForecastApi", async original => ({
  ...(await original<typeof import("../../pages/workforceforecastsops/workforceForecastApi")>()),
  getWorkforceForecasts: vi.fn(),
}));
function forecast(): sales.SalesForecast {
  const months = ["2026-12", "2027-01"];
  const totals: sales.WorkforceMonth[] = months.map(month => ({
    month, supply: "0.5", demand: "1.1", opportunityDemand: "1.0", unfilledDemand: "0.1",
    difference: "-0.6", rawSupply: "0.5", rawOpportunityDemand: "1.0", rawUnfilledDemand: "0.1", status: "SHORT",
  }));
  return {
    from: months[0], months: 2,
    summary: {sixMonthRevenue: "1500500", opportunityRevenue: "0", allWonRevenue: "1500500", averageDemand: "1.1", averageSupply: "0.5", hiringJobCount: 0},
    revenueMonths: months.map((month, i) => ({month, projectRevenue: i ? null : "1500500", opportunityRevenue: "0", totalRevenue: i ? null : "1500500", allWonRevenue: null})),
    revenueSources: [],
    workforce: {from: months[0], months: 2, includeUnfilled: true, basisDate: "2026-11-19", standardMonth: "2026-11", totals, jobDiagnostics: [], hiring: [], issues: [], lastMonthSupply: "0.5", lastMonthDemand: "1.1", mostShortageJobCode: null, maximumShortage: "0.6"},
  };
}
beforeEach(() => {
  charts.clear();
  vi.clearAllMocks();
  vi.mocked(sales.getSalesOpportunityForecast).mockResolvedValue(forecast());
  vi.mocked(workforce.getWorkforceForecasts).mockResolvedValue(forecast().workforce);
});
it("keeps the forecast chart in won while only the monthly table follows the chosen unit (SFR-006-01)", async () => {
  render(<MemoryRouter><SalesForecastPanel /></MemoryRouter>);
  await screen.findByRole("img", {name: "월별 예상매출"});
  for (const [code, unit] of [["WON", "원"], ["THOUSAND", "천원"], ["MILLION", "백만원"]] as const) {
    await userEvent.selectOptions(screen.getByTestId("forecast-unit-select"), code);
    // 단위 전환은 「UI-29 예측 월별 표」에만 있다. 차트·툴팁은 원 단위 그대로다.
    expect(screen.getByRole("heading", {name: `월별 매출 단위: ${unit}`})).toBeVisible();
    const option = charts.get("월별 예상매출")!;
    expect(option.yAxis.name).toBe("원");
    expect(option.series[0].data).toEqual([1500500, null]);
    expect(option.tooltip.formatter([{dataIndex: 0}])).toHaveTextContent("1,500,500 원");
    expect(option.tooltip.formatter([{dataIndex: 1}])).toHaveTextContent("– 원");
    // 요약 카드는 단위 선택과 무관하게 큰 금액을 ○만 원/○.○억 원으로 보인다(SFR-006-02).
    expect(screen.getByTestId("forecast-revenue-card")).toHaveTextContent("150만 원");
    expect(charts.get("수요·공급")!.yAxis.name).toBe("명");
    expect(charts.get("수요·공급")!.tooltip.formatter([{dataIndex: 0}])).toHaveTextContent("0.5명");
  }
});
it("keeps workforce chart in people; UI-17 has no currency unit selector (SFR-006-01)", async () => {
  render(<MemoryRouter><WorkforceForecastPanel query={{from: "2026-12", months: 2, includeUnfilled: true}} /></MemoryRouter>);
  await screen.findByRole("img", {name: "월별 가용 vs 필요"});
  expect(screen.queryByTestId("workforce-unit")).toBeNull();
  const option = charts.get("월별 가용 vs 필요")!;
  expect(option.yAxis.name).toBe("명");
  expect(option.series[0].data).toEqual([0.5, 0.5]);
  expect(option.tooltip.formatter([{dataIndex: 0}])).toHaveTextContent("1.1명");
  expect(screen.getByTestId("workforce-supply-card")).toHaveTextContent("0.5명");
});
it.each(["원", "천원", "백만원"] as const)("keeps business-plan axes in millions while detailed values use %s", unit => {
  const months = [{month: "2026-11", salesActualAmt: "-1500500", salesTargetAmt: "0"}] as PlanMonth[];
  render(<BusinessPlanRevenueChart months={months} unit={unit} />);
  expect(screen.getByText(`축: 백만원 · 상세: ${unit}`)).toBeVisible();
  const option = charts.get("1~12월 매출 실적 막대와 목표 기준선, 백만원")!;
  expect(option.yAxis.name).toBe("백만원");
  expect(option.series[0].data).toEqual([-1.5005]);
  expect(option.series[1].data).toEqual([0]);
  expect(option.tooltip.formatter([{dataIndex: 0}])).toHaveTextContent(`${formatAmount("-1500500", unit)} ${unit}`);
});
it("keeps waterfall labels in millions and detail tooltips in the selected unit", () => {
  render(<StructureWaterfall unit="원" steps={[{metric: "salesAmt", label: "매출", kind: "SUBTOTAL", amount: "1500500", connected: true}]} />);
  const option = charts.get("수익 구조 폭포")!;
  const drawn = option.series[0].renderItem!({dataIndex: 0}, {coord: ([x,y]: number[]) => [x*100,y], size: () => [80,0]});
  expect(drawn.children!.find(child => child.type === "text")!.style.text).toBe("1.5005");
  expect(option.tooltip.formatter({dataIndex: 0})).toHaveTextContent("매출: 1,500,500 원");
});
it("retains year context across December/January while keeping single-year month labels", () => {
  const option = seriesChartOptions({categories: ["2026-12", "2027-01"], series: [{name: "가용", values: ["0", null]}], formatValue: value => value ?? "미확인", axisUnit: "명"}) as unknown as ChartOption;
  expect(option.xAxis.axisLabel.formatter("2026-12")).toBe("2026-12");
  expect(option.xAxis.axisLabel.formatter("2027-01")).toBe("2027-01");
  expect(option.series[0].data).toEqual([0, null]);
  const singleYear = seriesChartOptions({categories: ["2026-11", "2026-12"], series: [], formatValue: value => value ?? "미확인"}) as unknown as ChartOption;
  expect(singleYear.xAxis.axisLabel.formatter("2026-12")).toBe("12월");
});

it("draws missing waterfall steps as unknown rather than omitting them or drawing zero bars", () => {
  render(<StructureWaterfall unit="천원" steps={[
    {metric: "salesAmt", label: "매출", kind: "SUBTOTAL", amount: "1500500", connected: true},
    {metric: "salesCostAmt", label: "원가", kind: "CHANGE", amount: null, connected: false},
  ]} />);
  const option = charts.get("수익 구조 폭포")!;
  const drawing = {coord: ([x,y]: number[]) => [x*100,y], size: () => [80,0]};
  const missing = option.series[1].renderItem!({dataIndex: 1}, drawing);
  expect(missing.type).toBe("text");
  expect(missing.style!.text).toBe("미확인");
  expect(missing.children).toBeUndefined();
  const known = option.series[0].renderItem!({dataIndex: 0}, drawing);
  expect(known.children!.some(child => child.type === "line")).toBe(false);
  expect(option.tooltip.formatter({dataIndex: 1})).toHaveTextContent("원가: – 천원");
});
