import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { NetProfitScreen } from "./NetProfitScreen";
import { NetProfitScreen as RegisteredScreen } from "../shared/NetProfitScreen";
import * as api from "./netProfitApi";
import * as periods from "../shared/sharedApi";
vi.mock("./netProfitApi");
vi.mock("../shared/sharedApi");
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({
    children,
    periodBar,
  }: {
    children: ReactNode;
    periodBar?: ReactNode;
  }) => (
    <section>
      {periodBar}
      {children}
    </section>
  ),
}));
const context = {
  currentDate: "2026-07-15",
  currentMonth: "2026-07",
  timezone: "Asia/Seoul",
  cashBaseMonth: "2026-01",
  monthClose: { month: "2026-07", status: "작업중" },
};
function response(): api.NetProfitData {
  const completeness = {
    costComplete: true,
    revenueComplete: true,
    taxComplete: false,
    missing: ["corpTaxAmt"],
    estimated: ["급여 예정"],
    sourceMonths: ["2026-07"],
    issues: [],
    availableMonthCount: 7,
    requestedMonthCount: 7,
    missingMonths: [],
  };
  const names = [
    "매출액",
    "매출원가",
    "배분 원가",
    "대기 원가",
    "직접 경비",
    "매출총이익",
    "판관비",
    "입력 항목",
    "간접 인건비",
    "연구개발비(내부)",
    "전월 조정",
    "영업이익",
    "영업이익률",
    "영업외수익",
    "영업외비용",
    "법인세(월할)",
    "당기순이익",
    "순이익률",
  ];
  const metrics = [
    "salesAmt",
    "salesCostAmt",
    "allocCostAmt",
    "idleCostAmt",
    "drctExpsAmt",
    "grossPrftAmt",
    "sgaTotAmt",
    "sgaInputAmt",
    "indrctLaborAmt",
    "rndAmt",
    "prevAdjAmt",
    "oprtPrftAmt",
    "operatingProfitRate",
    "noprIncmAmt",
    "noprExpsAmt",
    "corpTaxAmt",
    "netPrftAmt",
    "netProfitRate",
  ];
  const comparison = {
    metric: "netPrftAmt",
    months: ["2026-07"],
    comparisonMonths: ["2026-06"],
    currentValue: "-20",
    comparisonValue: "20",
    changeRate: "-200.0",
    percentagePointChange: null,
    comparable: true,
    availableMonthCount: 1,
    requestedMonthCount: 1,
    missingMonths: [],
    direction: "ADVERSE" as const,
  };
  const group = {
    groupKind: "CONTRACT_TYPE",
    other: false,
    salesAmt: "100",
    allocatedLaborCostAmt: "50",
    contributionRate: "50.0",
    revenueShare: "50.0",
  };
  return {
    context: {
      year: 2026,
      month: "2026-07",
      baseMonth: "2026-01",
      nowMonth: "2026-07",
      timezone: "Asia/Seoul",
      snapshotAt: "2026-07-15T09:00:00+09:00",
      aggregation: "YTD",
      amountUnit: "WON",
      readOnly: true,
    },
    months: Array.from({ length: 12 }, (_, i) => ({
      month: `2026-${String(i + 1).padStart(2, "0")}`,
      sourceKind:
        i > 6
          ? "FUTURE"
          : i === 0
            ? "MIGRATED"
            : i === 1
              ? "CLOSED_SNAPSHOT"
              : "CALCULATED",
      closeStatus: "WORK",
      fixed: false,
      migrated: i === 0,
      provisional: i > 1 && i <= 6,
      amounts: {},
      completeness: {
        ...completeness,
        availableMonthCount: i <= 6 ? 1 : 0,
        requestedMonthCount: 1,
      },
      sources: [],
      calculatedAt: "2026-07-15T09:00:00+09:00",
    })),
    rows: metrics.map((metric, i) => ({
      metric,
      label: names[i],
      depth: [2, 3, 4, 7, 8, 9].includes(i) ? 1 : 0,
      isRate: i === 12 || i === 17,
      monthlyValues: Array.from({ length: 12 }, (_, m) =>
        m > 6
          ? null
          : i === 0
            ? "9007199254740993"
            : i === 17
              ? "-1.0"
              : i === 16
                ? "-20"
                : "0",
      ),
      cumulativeValue: i === 0 ? "9007199254740993" : i === 17 ? "-1.0" : "0",
      previousYearCumulativeValue: "0",
      comparison,
    })),
    summary: ["netPrftAmt", "currentMonthNetProfit"].map((metric) => ({
      metric,
      amount: metric === "netPrftAmt" ? "9007199254740993" : "-20",
      rate: "-1.0",
      previous: metric === "currentMonthNetProfit" ? comparison : null,
      previousYear: null,
    })),
    showPreviousYear: true,
    cumulativeProvisional: true,
    completeness,
    notices: ["자료 7/7개월"],
    contributionMonth: "2026-07",
    contributions: {
      visible: true,
      complete: true,
      reason: null,
      revenueDifference: "0",
      contractTypes: [
        {
          ...group,
          groupId: "MO",
          groupName: "MO",
          contributionAmt: "50",
          positiveContributionShare: "100.0",
          loss: false,
          showBar: true,
        },
        {
          ...group,
          groupId: "DP",
          groupName: "DP",
          contributionAmt: "-20",
          positiveContributionShare: null,
          loss: true,
          showBar: false,
        },
        {
          ...group,
          groupId: "TK",
          groupName: "TK",
          contributionAmt: "0",
          positiveContributionShare: null,
          loss: false,
          showBar: false,
        },
      ],
      customers: [],
      salesParts: [],
    },
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
async function draw(state?: object) {
  render(
    <MemoryRouter initialEntries={[{ pathname: "/UI-09", state }]}>
      <NetProfitScreen />
      <Location />
    </MemoryRouter>,
  );
  await screen.findByTestId("net-cumulative-card");
}
beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(window, "innerWidth", {
    value: 1280,
    configurable: true,
  });
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: false }));
  vi.mocked(api.getNetProfitLoss).mockResolvedValue(response());
  vi.mocked(periods.getPeriodContext).mockResolvedValue(context);
  vi.mocked(periods.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(periods.getMonthClose).mockResolvedValue({
    items: [context.monthClose],
  });
  vi.mocked(periods.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, stored: true, revision: "saved" },
    }),
  );
});
it("uses existing registration and fetches net data on entry with two cards and all rows", async () => {
  expect(RegisteredScreen).toBe(NetProfitScreen);
  await draw();
  expect(api.getNetProfitLoss).toHaveBeenCalledWith(
    2026,
    "2026-07",
    expect.any(AbortSignal),
  );
  expect(screen.getAllByTestId(/^net-row-/)).toHaveLength(18);
  const headings = within(
    screen.getByRole("table", { name: "순이익표" }),
  ).getAllByRole("rowheader");
  expect(headings.slice(-5).map((h) => h.textContent)).toEqual([
    "영업외수익",
    "영업외비용",
    "법인세(월할)",
    "당기순이익",
    "순이익률",
  ]);
  expect(screen.getByTestId("net-profit-screen")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-09",
  );
});
it("keeps exact large decimal amounts rather than converting through Number", async () => {
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("단위"), "원");
  expect(screen.getByTestId("net-cumulative-card")).toHaveTextContent(
    "9,007,199,254,740,993",
  );
});
it("future actual cells stay missing and zero stays zero", async () => {
  await draw();
  const cells = within(
    screen.getByTestId("net-row-nopr-incm-amt"),
  ).getAllByRole("cell");
  expect(cells[0]).toHaveTextContent("0");
  expect(cells[7]).toHaveTextContent("–");
});
it("negative profits and rates remain visible", async () => {
  await draw();
  expect(
    within(screen.getByTestId("net-row-net-prft-amt")).getAllByRole("cell")[6],
  ).toHaveTextContent("−0.02");
  expect(screen.getByTestId("net-month-card")).toHaveTextContent(
    "순이익률 −1.0%",
  );
});
it("uses adverse server comparison flags and independent provisional and estimated notices", async () => {
  await draw();
  expect(
    within(screen.getByTestId("net-month-card")).getByText(/전월 대비/),
  ).toHaveClass("adverse");
  expect(screen.getAllByText("잠정").length).toBeGreaterThan(0);
  expect(screen.getByText("예정 포함")).toBeInTheDocument();
});
it("distinguishes migrated closed and working states", async () => {
  await draw();
  expect(screen.getByText("이관")).toHaveClass("sicost-tone-lock");
  expect(screen.getByText("확정")).toHaveClass("sicost-tone-lock");
  expect(
    screen
      .getAllByText("작업중")
      .every((element) => element.classList.contains("sicost-tone-info")),
  ).toBe(true);
});
it("hides absent prior-year columns", async () => {
  const data = response();
  data.showPreviousYear = false;
  vi.mocked(api.getNetProfitLoss).mockResolvedValue(data);
  await draw();
  expect(
    screen.queryByRole("columnheader", { name: "전년 누계" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("columnheader", { name: "전년 대비" }),
  ).not.toBeInTheDocument();
});
it("renders basis-month positive-only contribution with loss and zero having no bars", async () => {
  await draw();
  expect(screen.getByTestId("net-contributions")).toHaveTextContent(
    "2026-07 기준월",
  );
  const loss = screen.getByTestId("net-contribution-1");
  expect(within(loss).getByText(/적자/)).toHaveClass("adverse");
  expect(within(loss).queryByLabelText(/양수 기여도/)).not.toBeInTheDocument();
  expect(screen.getByLabelText("MO 양수 기여도 100.0%")).toBeInTheDocument();
  expect(screen.getByTestId("net-contribution-2")).toHaveTextContent("0 천원");
  expect(
    within(screen.getByTestId("net-contribution-2")).queryByLabelText(
      /양수 기여도/,
    ),
  ).not.toBeInTheDocument();
});
it("hides migrated contribution rather than generating a breakdown", async () => {
  const data = response();
  data.contributions = {
    ...data.contributions,
    visible: false,
    reason: "월별 손익만 이관되어 세부 구성이 없습니다",
    contractTypes: [],
  };
  vi.mocked(api.getNetProfitLoss).mockResolvedValue(data);
  await draw();
  expect(
    screen.getByText("월별 손익만 이관되어 세부 구성이 없습니다"),
  ).toBeInTheDocument();
  expect(screen.queryByTestId("net-contribution-0")).not.toBeInTheDocument();
});
it("all loss or zero contributions explain the missing positive denominator", async () => {
  const data = response();
  data.contributions.contractTypes.shift();
  vi.mocked(api.getNetProfitLoss).mockResolvedValue(data);
  await draw();
  expect(screen.getByText(/양수 기여이익이 없어/)).toBeInTheDocument();
  expect(screen.queryByLabelText(/양수 기여도/)).not.toBeInTheDocument();
});
it("incomplete contributions show source reason without substituting values", async () => {
  const data = response();
  data.contributions.complete = false;
  data.contributions.reason = "배분 노동비 미확인";
  data.contributions.contractTypes = [
    {
      ...data.contributions.contractTypes[0],
      contributionAmt: null,
      positiveContributionShare: null,
      showBar: false,
    },
  ];
  vi.mocked(api.getNetProfitLoss).mockResolvedValue(data);
  await draw();
  expect(screen.getByText("배분 노동비 미확인")).toBeInTheDocument();
  expect(screen.getByTestId("net-contribution-0")).toHaveTextContent("– 천원");
});
it("incoming month is honored without overwriting saved periods", async () => {
  await draw({ month: "2026-05" });
  expect(screen.getByLabelText("누계 월")).toHaveValue("2026-05");
  expect(api.getNetProfitLoss).toHaveBeenLastCalledWith(
    2026,
    "2026-05",
    expect.any(AbortSignal),
  );
  expect(periods.putSharedPreference).not.toHaveBeenCalled();
});
it("past-year changes choose December then honor explicit month and clamp current year", async () => {
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("누계 연도"), "2025");
  await waitFor(() =>
    expect(api.getNetProfitLoss).toHaveBeenLastCalledWith(
      2025,
      "2025-12",
      expect.any(AbortSignal),
    ),
  );
  await userEvent.selectOptions(screen.getByLabelText("누계 월"), "2025-03");
  await waitFor(() =>
    expect(api.getNetProfitLoss).toHaveBeenLastCalledWith(
      2025,
      "2025-03",
      expect.any(AbortSignal),
    ),
  );
  await userEvent.selectOptions(screen.getByLabelText("누계 연도"), "2026");
  await waitFor(() =>
    expect(api.getNetProfitLoss).toHaveBeenLastCalledWith(
      2026,
      "2026-07",
      expect.any(AbortSignal),
    ),
  );
  expect(periods.putSharedPreference).toHaveBeenLastCalledWith(
    "PERIOD",
    "UI-09",
    expect.any(Object),
    "saved",
  );
});
it("has no write close upload or actual cashflow results", async () => {
  await draw();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /삭제|마감|저장|업로드/ }),
  ).not.toBeInTheDocument();
  expect(screen.getByText(/실제 세금 납부액과 다릅니다/)).toBeInTheDocument();
});
it("downloads selected period and unit", async () => {
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("단위"), "백만원");
  await userEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  expect(api.downloadNetProfitLoss).toHaveBeenCalledWith(
    2026,
    "2026-07",
    "MILLION_WON",
  );
});
it("navigates to existing tax screen without forcing a closed source month", async () => {
  await draw();
  await userEvent.click(
    screen.getByRole("button", { name: "법인세 월할 확인" }),
  );
  expect(screen.getByTestId("destination")).toHaveTextContent(
    '{"path":"/UI-10","state":{"section":"tax"}}',
  );
});
it("cashflow action only navigates to the existing follow-up screen", async () => {
  await draw();
  await userEvent.click(
    screen.getByRole("button", { name: "자금수지표 안내" }),
  );
  expect(screen.getByTestId("destination")).toHaveTextContent(
    '"path":"/UI-21"',
  );
});
it("empty sources preserve structure with null instead of fabricated zeros", async () => {
  const data = response();
  data.completeness.availableMonthCount = 0;
  data.rows.forEach((row) => {
    row.cumulativeValue = null;
    row.monthlyValues.fill(null);
  });
  vi.mocked(api.getNetProfitLoss).mockResolvedValue(data);
  await draw();
  expect(
    screen.getByText(/조회 기간의 손익 자료가 없습니다/),
  ).toBeInTheDocument();
  expect(
    within(screen.getByTestId("net-row-net-prft-amt")).getAllByRole("cell")[0],
  ).toHaveTextContent("–");
});
it("initial failures expose a read-only retry", async () => {
  vi.mocked(api.getNetProfitLoss).mockRejectedValueOnce(new Error("조회 실패"));
  render(
    <MemoryRouter>
      <NetProfitScreen />
    </MemoryRouter>,
  );
  await screen.findByText("조회 실패");
  await userEvent.click(screen.getByRole("button", { name: /다시/ }));
  expect(await screen.findByTestId("net-cumulative-card")).toBeInTheDocument();
});
it("aborts stale requests when period changes", async () => {
  await draw();
  const signal = vi.mocked(api.getNetProfitLoss).mock.calls[0][2];
  await userEvent.selectOptions(screen.getByLabelText("누계 월"), "2026-06");
  await waitFor(() => expect(signal?.aborted).toBe(true));
});
it.each([
  "sicost:bills-committed",
  "sicost:financial-items-committed",
  "sicost:corporate-tax-committed",
  "sicost:historical-profit-loss-import-committed",
  "sicost:business-plan-import-committed",
])("refreshes immediately after %s", async (event) => {
  await draw();
  const count = vi.mocked(api.getNetProfitLoss).mock.calls.length;
  act(() => window.dispatchEvent(new Event(event)));
  await waitFor(() =>
    expect(api.getNetProfitLoss).toHaveBeenCalledTimes(count + 1),
  );
});
it("saved-but-refresh-failed is distinct and retry does not replay writes", async () => {
  await draw();
  vi.mocked(api.getNetProfitLoss).mockRejectedValueOnce(new Error("연결 실패"));
  act(() => window.dispatchEvent(new Event("sicost:bills-committed")));
  expect(
    await screen.findByText(/저장은 완료됐지만 순이익표를/),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: /다시/ }));
  await screen.findByTestId("net-cumulative-card");
  expect(api.downloadNetProfitLoss).not.toHaveBeenCalled();
});
it("disables file actions on narrow screens", async () => {
  Object.defineProperty(window, "innerWidth", { value: 600 });
  await draw();
  expect(screen.getByRole("button", { name: "엑셀 다운로드" })).toBeDisabled();
  expect(
    screen.getByText("엑셀 다운로드는 PC에서 이용해 주세요."),
  ).toBeInTheDocument();
});
