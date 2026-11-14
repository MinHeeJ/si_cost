import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { ProfitStructureScreen } from "./ProfitStructureScreen";
import { ProfitStructureScreen as RegisteredScreen } from "../shared/ProfitStructureScreen";
import * as api from "./profitStructureApi";
vi.mock("./profitStructureApi", async (original) => ({
  ...(await original<typeof import("./profitStructureApi")>()),
  getProfitLossStructure: vi.fn(),
  downloadProfitLossStructure: vi.fn(),
}));
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
function response(): api.StructureData {
  const completeness = {
    costComplete: true,
    revenueComplete: true,
    taxComplete: false,
    missing: ["corpTaxAmt"],
    estimated: ["급여 예정"],
    sourceMonths: ["2026-07"],
    issues: [],
    availableMonthCount: 1,
    requestedMonthCount: 1,
    missingMonths: [],
  };
  const group: api.RevenueGroup = {
    groupKind: "CONTRACT_TYPE",
    groupId: "TK",
    groupName: "TK",
    other: false,
    salesAmt: "9007199254740993",
    allocatedLaborCostAmt: "500",
    contributionAmt: "-500",
    revenueShare: "100.0",
    contributionRate: "-1.0",
    positiveContributionShare: null,
    loss: true,
    showBar: true,
  };
  const bucket: api.StructureBucket = {
    key: "2026-07",
    months: ["2026-07"],
    throughMonth: "2026-07",
    monthCount: 1,
    amounts: {
      salesAmt: "9007199254740993",
      salesCostAmt: "100",
      grossPrftAmt: "-1",
      oprtPrftAmt: "-2",
      allocCostAmt: "-10",
      idleCostAmt: null,
      drctExpsAmt: "5",
    },
    completeness,
    provisional: true,
    comparisons: [],
    composition: {
      visible: true,
      reason: null,
      complete: true,
      revenueDifference: "0",
      contractTypes: [group],
      customers: [{ ...group, groupId: "customer", groupName: "고객사" }],
      salesParts: [{ ...group, groupId: "part", groupName: "본사" }],
    },
    referenceRevenue: {
      visible: true,
      amount: "120",
      complete: true,
      reason: null,
      issues: [],
    },
  };
  return {
    context: {
      year: 2026,
      month: "2026-07",
      baseMonth: "2026-01",
      nowMonth: "2026-07",
      timezone: "Asia/Seoul",
      snapshotAt: "2026-07-15T09:00:00+09:00",
      aggregation: "MONTH",
      amountUnit: "WON",
      readOnly: true,
    },
    months: [
      {
        month: "2026-07",
        sourceKind: "CALCULATED",
        closeStatus: "WORK",
        fixed: false,
        migrated: false,
        provisional: true,
        amounts: bucket.amounts,
        completeness,
        sources: [],
        calculatedAt: "2026-07-15T09:00:00+09:00",
      },
    ],
    buckets: [bucket],
    summary: ["salesAmt", "grossPrftAmt", "oprtPrftAmt", "idleCostAmt"].map(
      (metric) => ({
        metric,
        amount: bucket.amounts[metric],
        rate: metric === "salesAmt" ? null : "-1.0",
        previous: {
          metric,
          months: ["2026-07"],
          comparisonMonths: ["2026-06"],
          currentValue: "-2",
          comparisonValue: "2",
          changeRate: "-200.0",
          percentagePointChange: null,
          comparable: true,
          availableMonthCount: 1,
          requestedMonthCount: 1,
          missingMonths: [],
          direction: "ADVERSE",
        },
        previousYear: null,
      }),
    ),
    waterfall: [
      {
        metric: "salesAmt",
        label: "매출",
        kind: "SUBTOTAL",
        amount: "100",
        connected: true,
      },
      {
        metric: "prevAdjAmt",
        label: "전월 조정",
        kind: "CHANGE",
        amount: "-20",
        connected: true,
      },
      {
        metric: "oprtPrftAmt",
        label: "영업이익",
        kind: "SUBTOTAL",
        amount: "80",
        connected: true,
      },
      {
        metric: "corpTaxAmt",
        label: "월할 세금",
        kind: "CHANGE",
        amount: null,
        connected: false,
      },
    ],
    numericRows: [
      {
        metric: "salesAmt",
        label: "매출",
        isRate: false,
        bucketKeys: [bucket.key],
        bucketValues: [bucket.amounts.salesAmt],
      },
    ],
    calculationMethods: [
      {
        metric: "salesAmt",
        sources: ["TB_BL_BILL"],
        formula: "TK 청구일 공급가액, MO/DP 귀속월",
        referenceOnly: false,
        vatExcluded: true,
      },
    ],
    notices: ["잠정 · 예정 포함"],
  };
}
function Location() {
  const location = useLocation();
  return (
    <output data-testid="destination">
      {JSON.stringify({ path: location.pathname, state: location.state })}
    </output>
  );
}
function period(month = "2026-07") {
  act(() =>
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-07",
          value: { type: "YEAR_CUM", year: month.slice(0, 4), month },
        },
      }),
    ),
  );
}
async function draw() {
  render(
    <MemoryRouter>
      <ProfitStructureScreen />
      <Location />
    </MemoryRouter>,
  );
  period();
  await screen.findByTestId("structure-card-sales-amt");
}
beforeEach(() => {
  vi.clearAllMocks();
  Object.defineProperty(window, "innerWidth", {
    value: 1280,
    configurable: true,
  });
  vi.mocked(api.getProfitLossStructure).mockResolvedValue(response());
});
it("retains existing screen registration and renders four last-bucket cards and all chart regions", async () => {
  expect(RegisteredScreen).toBe(ProfitStructureScreen);
  await draw();
  expect(screen.getAllByTestId(/^structure-card-/)).toHaveLength(4);
  expect(
    screen.getByRole("img", { name: "수익 구조 폭포" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("img", { name: "매출·매출원가 추이" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("img", { name: "매출총이익·영업이익 추이" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("img", { name: "하위원가 구성" }),
  ).toBeInTheDocument();
  expect(screen.getByText(/영업 파트 이익금은 사람 소속/)).toBeInTheDocument();
});
it("uses exact string formatting for large selected-unit amounts", async () => {
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("단위"), "원");
  expect(screen.getByTestId("structure-card-sales-amt")).toHaveTextContent(
    "9,007,199,254,740,993",
  );
});
it("shows adverse comparison and provisional/estimated states with shared tones", async () => {
  await draw();
  expect(
    within(screen.getByTestId("structure-card-oprt-prft-amt")).getByText(
      /전월/,
    ),
  ).toHaveClass("adverse");
  expect(screen.getByText("잠정 · 작업중")).toBeInTheDocument();
  expect(screen.getByText("예정 포함")).toBeInTheDocument();
});
it("switches aggregation and preserves it on period change", async () => {
  await draw();
  await userEvent.click(screen.getByRole("tab", { name: "분기" }));
  await waitFor(() =>
    expect(api.getProfitLossStructure).toHaveBeenLastCalledWith(
      2026,
      "2026-07",
      "QUARTER",
      expect.any(AbortSignal),
    ),
  );
  period("2026-06");
  await waitFor(() =>
    expect(api.getProfitLossStructure).toHaveBeenLastCalledWith(
      2026,
      "2026-06",
      "QUARTER",
      expect.any(AbortSignal),
    ),
  );
  expect(screen.getByRole("tab", { name: "분기" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
});
it("provides numeric tables, latest highlight and calculation sources without cell editors", async () => {
  await draw();
  await userEvent.click(screen.getByRole("button", { name: "숫자로 보기" }));
  expect(
    screen.getByRole("table", { name: "기간별 손익 숫자표" }),
  ).toBeInTheDocument();
  expect(
    within(screen.getByTestId("structure-numeric-sales-amt")).getByRole("cell"),
  ).toHaveClass("latest");
  expect(
    screen.getByRole("table", { name: "고객사 구성 숫자표" }),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "계산 방법" }));
  expect(screen.getByText("TB_BL_BILL")).toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("passes selected month through router state to operating and net screens", async () => {
  await draw();
  await userEvent.click(screen.getByTestId("structure-operating-card"));
  expect(screen.getByTestId("destination")).toHaveTextContent(
    '"path":"/UI-08"',
  );
  expect(screen.getByTestId("destination")).toHaveTextContent(
    '"month":"2026-07"',
  );
  await userEvent.click(screen.getByTestId("structure-net-link"));
  expect(screen.getByTestId("destination")).toHaveTextContent(
    '"path":"/UI-09"',
  );
});
it("hides migrated composition/reference without inventing groups", async () => {
  const data = response();
  data.buckets[0].composition.visible = false;
  data.buckets[0].composition.reason =
    "월별 손익만 이관되어 세부 구성이 없습니다";
  data.buckets[0].referenceRevenue.visible = false;
  vi.mocked(api.getProfitLossStructure).mockResolvedValue(data);
  await draw();
  expect(
    screen.getByText("월별 손익만 이관되어 세부 구성이 없습니다"),
  ).toBeInTheDocument();
  expect(
    screen.queryByTestId("structure-type-group-0"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("structure-chart-revenue-reference-revenue-0"),
  ).not.toBeInTheDocument();
});
it("retains negative bars and does not make a null point", async () => {
  await draw();
  expect(
    screen
      .getByTestId("structure-chart-costs-alloc-cost-amt-0")
      .querySelector("rect"),
  ).toHaveAttribute("height");
  expect(
    screen.queryByTestId("structure-chart-costs-idle-cost-amt-0"),
  ).not.toBeInTheDocument();
  expect(
    screen.getByTestId("structure-waterfall-corp-tax-amt"),
  ).toHaveTextContent("미확인");
});
it("handles empty sources and failed requests with read-only retry", async () => {
  vi.mocked(api.getProfitLossStructure).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  render(
    <MemoryRouter>
      <ProfitStructureScreen />
    </MemoryRouter>,
  );
  expect(screen.getByRole("status")).toBeInTheDocument();
  period();
  await screen.findByText("조회 실패");
  const data = response();
  data.months[0].sourceKind = "MISSING";
  vi.mocked(api.getProfitLossStructure).mockResolvedValue(data);
  await userEvent.click(screen.getByRole("button", { name: /다시/ }));
  expect(
    await screen.findByText(/조회 기간의 손익 자료가 없습니다/),
  ).toBeInTheDocument();
});
it("refreshes on source commit and shows committed-but-refresh-failed distinctly", async () => {
  await draw();
  vi.mocked(api.getProfitLossStructure).mockRejectedValue(
    new Error("원천 조회 실패"),
  );
  act(() => window.dispatchEvent(new Event("sicost:bills-committed")));
  expect(await screen.findByText(/저장은 완료됐지만 목록/)).toBeInTheDocument();
});
it("downloads the same period, aggregation and selected unit", async () => {
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("단위"), "천원");
  await userEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  expect(api.downloadProfitLossStructure).toHaveBeenCalledWith(
    2026,
    "2026-07",
    "MONTH",
    "THOUSAND_WON",
  );
});
it("disables PC-only export on narrow displays", async () => {
  Object.defineProperty(window, "innerWidth", { value: 600 });
  await draw();
  expect(screen.getByRole("button", { name: "엑셀 다운로드" })).toBeDisabled();
  expect(screen.getByText(/PC에서 이용/)).toBeInTheDocument();
});
it.each([
  "sicost:business-plan-import-committed",
  "sicost:historical-profit-loss-import-committed",
  "sicost:project-expenses-committed",
  "sicost:distribution-committed",
])("refreshes on merged source event %s", async (event) => {
  await draw();
  act(() => window.dispatchEvent(new Event(event)));
  await waitFor(() =>
    expect(api.getProfitLossStructure).toHaveBeenCalledTimes(2),
  );
});
