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
  SharedScreenFrame: ({ children, headerActions }: { children: ReactNode; headerActions?: ReactNode }) => (
    <section>{headerActions}{children}</section>
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
    groupName: "턴키",
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
        label: "법인세(월할)",
        kind: "CHANGE",
        amount: null,
        connected: false,
      },
    ],
    numericRows: [
      ["salesAmt", "매출"],
      ["salesCostAmt", "매출원가"],
      ["allocCostAmt", "배분 원가"],
      ["idleCostAmt", "대기 원가"],
      ["drctExpsAmt", "직접 경비"],
      ["grossPrftAmt", "매출총이익"],
      ["grossProfitRate", "매출총이익률"],
      ["oprtPrftAmt", "영업이익"],
      ["corpTaxAmt", "법인세(월할)"],
      ["netPrftAmt", "당기순이익"],
      ["idleCostShare", "대기 원가 비중"],
      ["referenceRevenue", "배분 기준 매출(참고)"],
    ].map(([metric, label]) => ({
      metric,
      label,
      isRate: metric.endsWith("Rate") || metric === "idleCostShare",
      bucketKeys: [bucket.key],
      bucketValues: [bucket.amounts[metric] ?? null],
    })),
    calculationMethods: [
      {
        metric: "기간",
        sources: ["요약 카드와 폭포 차트는 맨 오른쪽(가장 최근) 기간 기준"],
        formula:
          "월: 그 달 · 분기: 그 분기 안의 달을 더함(진행 중인 분기는 기준월까지) · 연 누계: 1월부터 그 달까지 더함",
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
async function draw(month = "2026-07") {
  render(
    <MemoryRouter>
      <ProfitStructureScreen />
      <Location />
    </MemoryRouter>,
  );
  period(month);
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
  expect(screen.getAllByText("잠정").length).toBeGreaterThan(0);
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
it("keeps numeric tables visible and lets the reader expand and collapse the calculation methods", async () => {
  await draw();
  expect(
    screen.queryByRole("button", { name: "숫자로 보기" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("table", { name: "기간별 손익 숫자표" }),
  ).toBeInTheDocument();
  expect(
    within(screen.getByTestId("structure-numeric-sales-amt")).getByRole("cell"),
  ).toHaveClass("latest");
  expect(
    screen.getByRole("table", { name: "고객사 구성 숫자표" }),
  ).toBeInTheDocument();
  const methods = screen.getByTestId("structure-method-panel");
  const summary = within(methods).getByText("계산 방법");
  expect(methods).not.toHaveAttribute("open");
  await userEvent.click(summary);
  expect(methods).toHaveAttribute("open");
  expect(screen.getByRole("table", { name: "계산 방법" })).toBeVisible();
  expect(
    within(screen.getByTestId("structure-method-panel"))
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["항목", "계산", "참고"]);
  expect(screen.getByTestId("structure-method-panel")).not.toHaveTextContent(
    /TB_|salesAmt|VAT/,
  );
  expect(screen.getByText("금액은 모두 부가세 제외입니다.")).toBeVisible();
  await userEvent.click(summary);
  expect(methods).not.toHaveAttribute("open");
  expect(screen.getByText("월별 · 단위 천원")).toBeVisible();
  expect(screen.getByText(/엑셀 다운로드는 이 표와 같습니다/)).toBeVisible();
  const numeric = screen.getByRole("table", { name: "기간별 손익 숫자표" });
  expect(
    numeric.querySelectorAll("tbody tr.profit-structure-subtotal"),
  ).toHaveLength(4);
  for (const metric of [
    "sales-amt",
    "gross-prft-amt",
    "oprt-prft-amt",
    "net-prft-amt",
  ])
    expect(screen.getByTestId(`structure-numeric-${metric}`)).toHaveClass(
      "profit-structure-subtotal",
    );
  expect(
    within(numeric)
      .getAllByRole("rowheader")
      .filter((cell) => cell.textContent?.startsWith("└")),
  ).toHaveLength(3);
  expect(
    screen.getByTestId("structure-numeric-alloc-cost-amt"),
  ).toHaveTextContent("└ 배분 원가");
  expect(
    screen.getByTestId("structure-numeric-idle-cost-amt"),
  ).toHaveTextContent("└ 대기 원가");
  expect(
    screen.getByTestId("structure-numeric-drct-exps-amt"),
  ).toHaveTextContent("└ 직접 경비");
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("shows server share comparisons in percentage points and keeps the favorable/adverse tones", async () => {
  const data = response(),
    card = data.summary[3];
  card.amount = "289";
  card.rate = "28.9";
  card.previous = {
    ...card.previous!,
    metric: "idleCostShare",
    changeRate: null,
    currentValue: "28.9",
    comparisonValue: "33.2",
    percentagePointChange: "-4.3",
    direction: "FAVORABLE",
  };
  card.previousYear = {
    ...card.previous,
    percentagePointChange: "2.1",
    direction: "ADVERSE",
  };
  data.summary[1].rate = "24.3";
  data.summary[2].rate = "10.3";
  vi.mocked(api.getProfitLossStructure).mockResolvedValue(data);
  await draw();
  const idle = within(screen.getByTestId("structure-card-idle-cost-amt"));
  expect(idle.getByText("전월 대비 -4.3%p")).toHaveClass("favorable");
  expect(idle.getByText("전년 같은 기간 대비 2.1%p")).toHaveClass("adverse");
  expect(idle.getByText("28.9%")).toBeVisible();
  expect(idle.getByText(/대기 원가 .* ÷ 매출원가/)).toBeVisible();
  expect(screen.getByTestId("structure-card-gross-prft-amt")).toHaveTextContent(
    "매출총이익률 24.3%",
  );
  expect(screen.getByTestId("structure-card-oprt-prft-amt")).toHaveTextContent(
    "영업이익률 10.3%",
  );
});
it.each([
  [2, "전 분기 같은 기간 대비"],
  [3, "전 분기 대비"],
])(
  "uses the server quarter's %i month count for its comparison label",
  async (count, label) => {
    vi.mocked(api.getProfitLossStructure).mockImplementation(
      async (_year, _month, aggregation) => {
        const data = response();
        data.context.aggregation = aggregation;
        data.buckets[0].monthCount = count as number;
        return data;
      },
    );
    await draw();
    await userEvent.click(screen.getByRole("tab", { name: "분기" }));
    expect(
      await within(screen.getByTestId("structure-card-sales-amt")).findByText(
        `${label} -200.0%`,
      ),
    ).toBeVisible();
    expect(screen.getByText("분기별 · 단위 천원")).toBeVisible();
  },
);
it("keeps sales part guidance inside its card and uses the server's composition names", async () => {
  await draw();
  expect(
    screen.getByRole("heading", { name: "고객사 상위 5곳" }),
  ).toBeVisible();
  const part = within(screen.getByTestId("structure-part-composition"));
  expect(part.getByText("프로젝트를 따온 파트 기준")).toBeVisible();
  expect(part.getByText(/영업 파트 이익금은 사람 소속/)).toBeVisible();
  expect(screen.getByTestId("structure-type-group-0")).toHaveTextContent(
    "턴키",
  );
  expect(
    screen.getByTestId("structure-waterfall-corp-tax-amt"),
  ).toHaveTextContent("법인세(월할)");
  expect(
    screen.getByTestId("structure-numeric-net-prft-amt"),
  ).toHaveTextContent("당기순이익");
  const compositionHelp = screen.getByRole("button", { name: "매출 구성 기준 도움말" });
  await userEvent.click(compositionHelp);
  expect(screen.getByRole("tooltip")).toHaveTextContent(/기여이익은.*인건비를 뺀 금액/);
  expect(screen.getByRole("tooltip")).toHaveTextContent(/숫자로 보기.*매출 구성 표에도 같은 기준/);
  await userEvent.keyboard("{Escape}");
  const referenceHelps = screen.getAllByRole("button", { name: "배분 기준 매출 도움말" });
  expect(referenceHelps).toHaveLength(2);
  expect(screen.getByTestId("structure-numeric-reference-revenue")).toContainElement(referenceHelps[1]);
  await userEvent.click(referenceHelps[0]);
  expect(screen.getByRole("tooltip")).toHaveTextContent(/점선.*회사 매출이나 이익 합계에는 포함하지 않습니다/);
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

it.each([
  ["MONTH", "월", ["2026-08"], null],
  [
    "QUARTER",
    "분기",
    ["2026-07", "2026-08"],
    "2026년 7~8월",
  ],
  [
    "YTD",
    "연 누계",
    Array.from(
      { length: 8 },
      (_, i) => `2026-${String(i + 1).padStart(2, "0")}`,
    ),
    "2026년 1~8월",
  ],
] as const)(
  "describes the server's actual %s bucket period",
  async (mode, tab, months, expected) => {
    vi.mocked(api.getProfitLossStructure).mockImplementation(
      async (year, month, aggregation) => {
        const result = response();
        result.context = {
          ...result.context,
          year,
          month,
          nowMonth: month,
          aggregation,
        };
        const bucketMonths = aggregation === mode ? [...months] : [month];
        result.buckets[0] = {
          ...result.buckets[0],
          months: bucketMonths,
          throughMonth: month,
          monthCount: bucketMonths.length,
        };
        result.months = bucketMonths.map((value) => ({
          ...result.months[0],
          month: value,
        }));
        return result;
      },
    );
    await draw("2026-08");
    if (mode !== "MONTH")
      await userEvent.click(screen.getByRole("tab", { name: tab }));
    await screen.findByTestId("structure-card-sales-amt");
    const periodStatus = screen.getByRole("group", { name: "조회 기간 상태" });
    if (expected) {
      expect(within(periodStatus).getByText(expected)).toBeVisible();
    } else {
      expect(within(periodStatus).queryByText(/집계 기간/)).not.toBeInTheDocument();
    }
    expect(api.getProfitLossStructure).toHaveBeenLastCalledWith(
      2026,
      "2026-08",
      mode,
      expect.any(AbortSignal),
    );
  },
);

it("opens the toolbar warning count as a modal with the current actionable notices", async () => {
  const warning = "선투입 원가: 프로젝트 진행 상태를 확인해 주세요.";
  const second = "유상 투입 없는 턴키 배분액: 월 투입과 배분표를 확인해 주세요.";
  vi.mocked(api.getProfitLossStructure).mockResolvedValue({
    ...response(),
    notices: [
      "잠정: 미마감 월은 작업중입니다.",
      ...[warning, second, warning].map((message) => `2026-07 ${message}`),
    ],
  });
  await draw();
  const button = screen.getByRole("button", { name: "확인 필요 2건" });
  expect(button.parentElement).toContainElement(screen.getByRole("tablist"));
  expect(screen.queryByText(/선투입 원가:/)).not.toBeInTheDocument();
  await userEvent.click(button);
  const dialog = screen.getByRole("dialog", { name: "확인 필요 2건" });
  const month = within(dialog).getByRole("heading", { name: "2026-07" });
  expect(within(month.parentElement!).getAllByRole("listitem").map((item) => item.textContent))
    .toEqual([warning, second]);
  await userEvent.keyboard("{Escape}");
  expect(screen.queryByRole("dialog", { name: "확인 필요 2건" })).not.toBeInTheDocument();
  expect(button).toHaveFocus();
});
