import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { CashFlowScreen } from "../shared/CashFlowScreen";
import { MonthCloseScreen } from "../shared/MonthCloseScreen";
import { cashItems, type CashFlowResult } from "../cashflowops/cashFlowApi";
import type { MonthCloses } from "../monthclosesops/monthCloseApi";

vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
const fetcher = vi.fn<typeof fetch>();
const response = (data: unknown) =>
  new Response(JSON.stringify({ success: true, meta: {}, data }));
let cash: CashFlowResult;
let close: MonthCloses;
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("fetch", fetcher);
  window.innerWidth = 1280;
  const values = {
    openingBalance: "1150000000",
    items: cashItems.map((item) => ({
      item,
      amount: item === "LOAN" ? "-1001" : "0",
      includesVat: true,
      verified: true,
    })),
    incomeTotal: "-1001",
    expenseTotal: "0",
    closingBalance: "1149998999",
  };
  const months = Array.from({ length: 12 }, (_, i) => ({
    ...values,
    month: `2026-${String(i + 1).padStart(2, "0")}`,
    status: i < 10 ? ("ACTUAL" as const) : ("PLANNED" as const),
    verified: true,
    shortage: i >= 10,
    issues: [],
  }));
  const chartPoints = months.map((m) => ({
    month: m.month,
    balance: m.closingBalance,
    status: m.status,
    shortage: m.shortage,
  }));
  cash = {
    year: 2026,
    unit: "THOUSAND_WON",
    asOfDate: "2026-11-15",
    timezone: "Asia/Seoul",
    baseMonth: "2026-01",
    baseAmount: "1150000000",
    minimumCash: "1150000000",
    minimumCashMonth: "2026-01",
    months,
    periodTotals: ["FIRST_HALF", "SECOND_HALF", "YEAR"].map((period) => ({
      ...values,
      period: period as "YEAR",
    })),
    chartPoints,
    shortages: chartPoints.filter((p) => p.shortage),
    issues: [],
  };
  close = {
    month: "2026-11",
    status: "WORK",
    closedBy: null,
    closedAt: null,
    nextCloseMonth: "2026-11",
    latestCloseMonth: "2026-10",
    managementAllowed: true,
    canCheck: true,
    canClose: true,
    canReopen: false,
    revision: "revision",
    checks: ["PAY", "MM_OVER", "BILL", "FI", "SETL", "BILL_MISS"].map(
      (code, i) => ({
        code,
        name: [
          "급여 업로드",
          "MM 초과자",
          "번호 미입력",
          "재무 항목",
          "영업 파트 정산",
          "청구 누락 점검",
        ][i],
        status: i === 2 ? "WARN" : "OK",
        count: i === 2 ? 1 : 0,
        description: i === 0 ? "0명 업로드 완료" : "현재 점검",
        checkedAt: null,
        linkScreenId: i === 0 ? null : i === 1 ? "UI-12" : "UI-13",
        linkMonth: "2026-11",
        linkFilter: i === 1 ? "MM_OVER" : "BILL_NUMBER_MISSING",
      }),
    ),
    checkMode: "CURRENT",
    warningCount: 1,
    history: { items: [], total: 0, page: 1, size: 50, hasMore: false },
    issues: [],
  };
  fetcher.mockImplementation(async (url) =>
    String(url).startsWith("/api/month-closes")
      ? response(close)
      : response(cash),
  );
});
afterEach(() => vi.unstubAllGlobals());
function Destination() {
  const location = useLocation();
  return (
    <p data-testid="financial-destination">{JSON.stringify(location.state)}</p>
  );
}
function mountCash() {
  render(
    <MemoryRouter initialEntries={["/UI-21"]}>
      <CashFlowScreen />
    </MemoryRouter>,
  );
}
async function mountClose() {
  render(
    <MemoryRouter initialEntries={["/UI-14"]}>
      <Routes>
        <Route path="/UI-14" element={<MonthCloseScreen />} />
        <Route path="/UI-12" element={<Destination />} />
        <Route path="/UI-13" element={<Destination />} />
      </Routes>
    </MemoryRouter>,
  );
  act(() =>
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-14",
          value: { type: "YEAR_MONTH", value: close.month },
          context: { currentMonth: "2026-11", cashBaseMonth: "2026-01" },
        },
      }),
    ),
  );
  await screen.findByText("마감 점검");
}
it("shared cash route uses the merged screen with fixed rows, twelve months and all three totals", async () => {
  mountCash();
  const table = await screen.findByRole("table", { name: "자금수지표" });
  expect(within(table).getAllByRole("columnheader")).toHaveLength(16);
  expect(within(table).getByText("상반기")).toBeInTheDocument();
  expect(within(table).getByText("하반기")).toBeInTheDocument();
  expect(within(table).getByText("연간")).toBeInTheDocument();
  expect(
    within(screen.getByTestId("cash-row-income-section")).queryByRole("button"),
  ).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("cash-row-opening")).queryByRole("button"),
  ).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("cash-row-loan")).getAllByRole("button")[0],
  ).toHaveTextContent("−1.001");
  expect(
    screen.queryByRole("button", { name: "기초금액 입력" }),
  ).not.toBeInTheDocument();
  expect(fetcher.mock.calls[0][0]).toBe("/api/cash-flow?unit=THOUSAND_WON");
});
it("shows every server shortage and keeps the chart in million won after a table unit change", async () => {
  mountCash();
  await screen.findByRole("table");
  expect(screen.getByText(/자금 부족 예정월:/)).toHaveTextContent("2026-11");
  expect(screen.getByText(/자금 부족 예정월:/)).toHaveTextContent("2026-12");
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "단위" }),
    "백만원",
  );
  await waitFor(() =>
    expect(
      fetcher.mock.calls.some(([url]) =>
        String(url).includes("unit=MILLION_WON"),
      ),
    ).toBe(true),
  );
  expect(screen.getByText("단위: 백만 원 · 부가세 포함")).toBeInTheDocument();
  expect(screen.getByText(/자금 부족 예정월:/)).toHaveTextContent("2026-12");
});
it("does not falsely bridge a NULL balance in the chart or turn unknown into zero", async () => {
  cash.months[10] = {
    ...cash.months[10],
    closingBalance: null,
    verified: false,
    expenseTotal: null,
  };
  cash.chartPoints[10] = {
    ...cash.chartPoints[10],
    balance: null,
    shortage: false,
  };
  mountCash();
  await screen.findByRole("table");
  expect(
    within(screen.getByTestId("cash-row-balance")).getAllByText("–"),
  ).toHaveLength(1);
  const graph = screen.getByRole("img", { name: /실적 실선/ });
  expect(graph.querySelectorAll(".cash-line.planned")).toHaveLength(0);
});
it("shows annual read errors with correlation id and only retries GET", async () => {
  fetcher.mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "PERIOD_OUT_OF_SCOPE",
        message: "기간 범위 밖",
        field: "year",
        requestId: "cash-request",
      }),
      { status: 409 },
    ),
  );
  mountCash();
  expect(await screen.findByRole("alert")).toHaveTextContent("cash-request");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  fetcher.mockImplementation(async () => response(cash));
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByRole("table");
  expect(
    fetcher.mock.calls.every(
      ([, options]) => !options?.method || options.method === "GET",
    ),
  ).toBe(true);
});
it("shared close route renders current fixed six checks and empty annual history", async () => {
  await mountClose();
  expect(
    screen.getAllByTestId(
      /^month-close-check-(pay|mm-over|bill|fi|setl|bill-miss)$/,
    ),
  ).toHaveLength(6);
  expect(screen.getByText("0명 업로드 완료")).toBeInTheDocument();
  expect(
    screen.getByText("선택연도 마감 이력이 없습니다."),
  ).toBeInTheDocument();
  expect(
    within(screen.getByTestId("month-close-check-pay")).queryByRole("button"),
  ).not.toBeInTheDocument();
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/month-closes?month=2026-11&page=1&size=50",
  );
});
it("CLOSE displays its warning snapshot and original actor instead of current command controls", async () => {
  close = {
    ...close,
    status: "CLOSE",
    checkMode: "CLOSE_SNAPSHOT",
    closedBy: "original",
    closedAt: "2026-11-15 09:00:00",
    canCheck: false,
    canClose: false,
    canReopen: true,
  };
  await mountClose();
  expect(screen.getByText(/마감 당시 6점검/)).toBeInTheDocument();
  expect(screen.getByText(/마감자 original/)).toBeInTheDocument();
  expect(
    within(screen.getByTestId("month-close-check-bill")).getByText("확인 필요"),
  ).toHaveClass("sicost-tone-warn");
  expect(
    screen.queryByRole("button", { name: "다시 점검" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "마감하기" }),
  ).not.toBeInTheDocument();
});
it("denied management scope disables commands without inventing an authentication flow", async () => {
  close.managementAllowed = false;
  await mountClose();
  expect(screen.getByRole("button", { name: "마감하기" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "다시 점검" })).toBeDisabled();
  expect(screen.queryByText("로그인")).not.toBeInTheDocument();
});
it("check navigation carries month and filter as transient state and never writes a period preference", async () => {
  await mountClose();
  await userEvent.click(
    within(screen.getByTestId("month-close-check-mm-over")).getByRole(
      "button",
      { name: "확인하기" },
    ),
  );
  expect(await screen.findByTestId("financial-destination")).toHaveTextContent(
    '"month":"2026-11"',
  );
  expect(screen.getByTestId("financial-destination")).toHaveTextContent(
    '"filter":"MM_OVER"',
  );
  expect(
    fetcher.mock.calls.every(
      ([, options]) => !options?.method || options.method === "GET",
    ),
  ).toBe(true);
});
