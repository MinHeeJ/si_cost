import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { OperatingProfitScreen } from "./OperatingProfitScreen";
import { OperatingProfitScreen as RegisteredScreen } from "../shared/OperatingProfitScreen";
import * as api from "./operatingProfitApi";
import * as periods from "../shared/sharedApi";
import * as migration from "../migration/historicalProfitLossImportsApi";
import * as history from "../apiops/financialReadApi";
vi.mock("./operatingProfitApi");
vi.mock("../shared/sharedApi");
vi.mock("../migration/historicalProfitLossImportsApi");
vi.mock("../apiops/financialReadApi");
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
function response(): api.OperatingProfitData {
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
  ];
  const comparison = {
    metric: "oprtPrftAmt",
    months: ["2026-07"],
    comparisonMonths: ["2025-07"],
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
      isRate: i === 12,
      monthlyValues: Array.from({ length: 12 }, (_, m) =>
        m > 6
          ? null
          : i === 0
            ? "9007199254740993"
            : i === 12
              ? "-1.0"
              : i === 10
                ? "-20"
                : "0",
      ),
      cumulativeValue: i === 0 ? "9007199254740993" : i === 12 ? "-1.0" : "0",
      previousYearCumulativeValue: "0",
      comparison,
    })),
    summary: [
      "salesAmt",
      "grossPrftAmt",
      "oprtPrftAmt",
      "currentMonthOperatingProfit",
    ].map((metric) => ({
      metric,
      amount: metric === "salesAmt" ? "9007199254740993" : "-20",
      rate: "-1.0",
      previous: metric === "currentMonthOperatingProfit" ? comparison : null,
      previousYear:
        metric === "currentMonthOperatingProfit" ? null : comparison,
    })),
    showPreviousYear: true,
    cumulativeProvisional: true,
    completeness,
    notices: ["자료 7/7개월"],
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
    <MemoryRouter initialEntries={[{ pathname: "/UI-08", state }]}>
      <OperatingProfitScreen />
      <Location />
    </MemoryRouter>,
  );
  await screen.findByTestId("operating-card-sales-amt");
}
beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(window, "innerWidth", {
    value: 1280,
    configurable: true,
  });
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: false }));
  vi.mocked(api.getOperatingProfitLoss).mockResolvedValue(response());
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
  vi.mocked(history.getFinancialHistory).mockResolvedValue({
    items: [],
    page: 1,
    size: 50,
    total: 0,
  });
});
it("retains existing registration and queries four cards and exact thirteen accounting rows", async () => {
  expect(RegisteredScreen).toBe(OperatingProfitScreen);
  await draw();
  expect(api.getOperatingProfitLoss).toHaveBeenCalledWith(
    2026,
    "2026-07",
    expect.any(AbortSignal),
  );
  expect(screen.getAllByTestId(/^operating-card-/)).toHaveLength(4);
  expect(screen.getAllByTestId(/^operating-row-/)).toHaveLength(13);
  const headings = within(
    screen.getByRole("table", { name: "영업이익표" }),
  ).getAllByRole("rowheader");
  expect(headings.map((h) => h.textContent)).toEqual([
    "매출액",
    "매출원가",
    "└배분 원가",
    "└대기 원가",
    "└직접 경비",
    "매출총이익",
    "판관비",
    "└입력 항목",
    "└간접 인건비",
    "└연구개발비(내부)",
    "전월 조정",
    "영업이익",
    "영업이익률",
  ]);
});
it("uses exact decimal display and leaves future values blank rather than zero", async () => {
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("단위"), "원");
  expect(screen.getByTestId("operating-card-sales-amt")).toHaveTextContent(
    "9,007,199,254,740,993",
  );
  const cells = within(
    screen.getByTestId("operating-row-sales-amt"),
  ).getAllByRole("cell");
  expect(cells[6]).toHaveClass("operating-current");
  expect(cells[7]).toHaveTextContent("–");
  expect(
    within(screen.getByTestId("operating-row-prev-adj-amt")).getAllByRole(
      "cell",
    )[0],
  ).toHaveTextContent("−20");
});
it("shows distinct imported closed and working tones", async () => {
  await draw();
  expect(screen.getByText("이관")).toHaveClass("sicost-tone-lock");
  expect(screen.getByText("확정")).toHaveClass("sicost-tone-lock");
  expect(
    screen
      .getAllByText("작업중")
      .every((element) => element.classList.contains("sicost-tone-info")),
  ).toBe(true);
});
it("uses adverse comparisons and provisional markers", async () => {
  await draw();
  expect(
    within(screen.getByTestId("operating-card-oprt-prft-amt")).getByText(
      /전년 동기/,
    ),
  ).toHaveClass("adverse");
  expect(screen.getAllByText("잠정").length).toBeGreaterThan(0);
  expect(screen.getByText("예정 포함")).toBeInTheDocument();
});
it("hides both prior columns without any prior-year material", async () => {
  const data = response();
  data.showPreviousYear = false;
  data.summary.forEach((card) => {
    card.previousYear = null;
  });
  vi.mocked(api.getOperatingProfitLoss).mockResolvedValue(data);
  await draw();
  expect(
    screen.queryByRole("columnheader", { name: "전년 누계" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("columnheader", { name: "전년 대비" }),
  ).not.toBeInTheDocument();
});
it("opens incoming selected month without changing any saved preference", async () => {
  await draw({ month: "2026-05" });
  expect(screen.getByLabelText("누계 월")).toHaveValue("2026-05");
  expect(api.getOperatingProfitLoss).toHaveBeenLastCalledWith(
    2026,
    "2026-05",
    expect.any(AbortSignal),
  );
  expect(periods.putSharedPreference).not.toHaveBeenCalled();
});
it("new past-year selection defaults to December then accepts explicit month and clamps current year", async () => {
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("누계 연도"), "2025");
  await waitFor(() =>
    expect(api.getOperatingProfitLoss).toHaveBeenLastCalledWith(
      2025,
      "2025-12",
      expect.any(AbortSignal),
    ),
  );
  await userEvent.selectOptions(screen.getByLabelText("누계 월"), "2025-03");
  await waitFor(() =>
    expect(api.getOperatingProfitLoss).toHaveBeenLastCalledWith(
      2025,
      "2025-03",
      expect.any(AbortSignal),
    ),
  );
  await userEvent.selectOptions(screen.getByLabelText("누계 연도"), "2026");
  await waitFor(() =>
    expect(api.getOperatingProfitLoss).toHaveBeenLastCalledWith(
      2026,
      "2026-07",
      expect.any(AbortSignal),
    ),
  );
  expect(periods.putSharedPreference).toHaveBeenLastCalledWith(
    "PERIOD",
    "UI-08",
    expect.any(Object),
    "saved",
  );
});
it("never offers editing deletion or close even in an operating year", async () => {
  await draw();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /삭제|마감|저장/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: "전년 손익 업로드" }),
  ).toBeEnabled();
});
it("imports use existing workflow and cancel does not apply", async () => {
  await draw();
  await userEvent.click(
    screen.getByRole("button", { name: "전년 손익 업로드" }),
  );
  expect(screen.getByRole("dialog")).toBeInTheDocument();
  expect(screen.getByText("이관 손익 업로드")).toBeInTheDocument();
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }),
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(migration.saveHistoricalProfitLossImport).not.toHaveBeenCalled();
});
it("downloads exact period and selected unit", async () => {
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("단위"), "백만원");
  await userEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  expect(api.downloadOperatingProfitLoss).toHaveBeenCalledWith(
    2026,
    "2026-07",
    "MILLION_WON",
  );
});
it("adjustment navigation does not pass a closed source month", async () => {
  await draw();
  await userEvent.click(screen.getByRole("button", { name: "전월 조정 입력" }));
  expect(screen.getByTestId("destination")).toHaveTextContent(
    '{"path":"/UI-10","state":{"section":"adjustments"}}',
  );
});
it("uses the common historical audit scope", async () => {
  await draw();
  await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
  await waitFor(() =>
    expect(history.getFinancialHistory).toHaveBeenCalledWith(
      "2026-07",
      "HISTORICAL_PROFIT_LOSS",
      1,
      50,
      expect.any(AbortSignal),
    ),
  );
  expect(screen.getByRole("option", { name: "이관 손익" })).toBeInTheDocument();
});
it("empty sources retain read-only structure and do not fabricate amounts", async () => {
  const data = response();
  data.completeness.availableMonthCount = 0;
  data.rows.forEach((row) => {
    row.cumulativeValue = null;
    row.monthlyValues.fill(null);
  });
  vi.mocked(api.getOperatingProfitLoss).mockResolvedValue(data);
  await draw();
  expect(
    screen.getByText(/조회 기간의 손익 자료가 없습니다/),
  ).toBeInTheDocument();
  expect(
    within(screen.getByTestId("operating-row-sales-amt")).getAllByRole(
      "cell",
    )[0],
  ).toHaveTextContent("–");
});
it("failed requests expose read-only retry", async () => {
  vi.mocked(api.getOperatingProfitLoss).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  render(
    <MemoryRouter>
      <OperatingProfitScreen />
    </MemoryRouter>,
  );
  await screen.findByText("조회 실패");
  await userEvent.click(screen.getByRole("button", { name: /다시/ }));
  expect(
    await screen.findByTestId("operating-card-sales-amt"),
  ).toBeInTheDocument();
  expect(migration.saveHistoricalProfitLossImport).not.toHaveBeenCalled();
});
it("source commits refresh immediately and distinguish saved-but-refresh-failed", async () => {
  await draw();
  vi.mocked(api.getOperatingProfitLoss).mockRejectedValue(
    new Error("연결 실패"),
  );
  act(() => window.dispatchEvent(new Event("sicost:bills-committed")));
  expect(
    await screen.findByText(/저장은 완료됐지만 손익표를/),
  ).toBeInTheDocument();
  expect(migration.saveHistoricalProfitLossImport).not.toHaveBeenCalled();
});
it("refreshes for the merged historical import commit event", async () => {
  await draw();
  const before = vi.mocked(api.getOperatingProfitLoss).mock.calls.length;
  act(() =>
    window.dispatchEvent(
      new Event("sicost:historical-profit-loss-import-committed"),
    ),
  );
  await waitFor(() =>
    expect(api.getOperatingProfitLoss).toHaveBeenCalledTimes(before + 1),
  );
});
it("PC-only file controls are disabled on narrow screens", async () => {
  Object.defineProperty(window, "innerWidth", { value: 600 });
  await draw();
  expect(
    screen.getByRole("button", { name: "전년 손익 업로드" }),
  ).toBeDisabled();
  expect(screen.getByRole("button", { name: "전년 손익 양식" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "엑셀 다운로드" })).toBeDisabled();
});
