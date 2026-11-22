import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { CashFlowScreen } from "./CashFlowScreen";
import { cashItems, type CashFlowResult, type CashSource } from "./cashFlowApi";
import { SharedScreenContext } from "../shared/SharedScreenContext";
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
const fetcher = vi.fn<typeof fetch>();
const response = (data: unknown) =>
  new Response(JSON.stringify({ success: true, meta: {}, data }));
let data: CashFlowResult;
let sources: CashSource[];
const source = (status: CashSource["status"] = "PLANNED"): CashSource => ({
  sourceType: "TB_HR_MON_PAY",
  sourceTypeLabel: "급여",
  sourceId: "202610:001",
  item: "REG_PAY",
  attributionDate: null,
  sourceMonth: "2026-10",
  attributionMonth: "2026-11",
  status,
  amount: status === "UNKNOWN" ? null : "-1001",
  vatAmount: "0",
  includesVat: true,
  referencePayrollMonth: "2026-10",
  employeeType: "FT",
  verified: status !== "UNKNOWN",
  formula: "최근 급여 기준",
  sourceLink: "/UI-10",
  issues:
    status === "UNKNOWN"
      ? [
          {
            code: "SOURCE_UNKNOWN",
            message: "필수 급여 미확인",
            month: "2026-11",
            sourceId: "001",
          },
        ]
      : [],
});
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("fetch", fetcher);
  window.innerWidth = 1280;
  const values = {
    openingBalance: "1000",
    items: cashItems.map((item) => ({
      item,
      amount: item === "REG_PAY" ? null : "0",
      includesVat: true,
      verified: item !== "REG_PAY",
    })),
    incomeTotal: "0",
    expenseTotal: null,
    closingBalance: null,
  };
  data = {
    year: 2026,
    unit: "THOUSAND_WON",
    asOfDate: "2026-11-15",
    timezone: "Asia/Seoul",
    baseMonth: "2026-01",
    baseAmount: "1000",
    minimumCash: "500",
    minimumCashMonth: "2026-01",
    months: Array.from({ length: 12 }, (_, i) => ({
      ...values,
      month: `2026-${String(i + 1).padStart(2, "0")}`,
      status: i < 9 ? "ACTUAL" : "PLANNED",
      verified: false,
      shortage: false,
      issues: [],
    })),
    periodTotals: ["FIRST_HALF", "SECOND_HALF", "YEAR"].map((period) => ({
      ...values,
      period: period as "YEAR",
    })),
    chartPoints: [],
    shortages: [],
    issues: [],
  };
  sources = [source("UNKNOWN")];
  fetcher.mockImplementation(async (url) =>
    String(url).startsWith("/api/cash-flow/sources")
      ? response({
          month: "2026-11",
          employeeTypeLabels: {
            FT: "정규직",
            CT: "계약직",
            FL: "프리랜서",
            OS: "외주",
          },
          item: "REG_PAY",
          items: sources,
          total: 21,
          page:
            new URL(String(url), "https://test.invalid").searchParams.get(
              "page",
            ) === "2"
              ? 2
              : 1,
          size: 50,
          hasMore: true,
        })
      : response(data),
  );
});
afterEach(() => vi.unstubAllGlobals());
function Destination() {
  const location = useLocation();
  return <p data-testid="destination">{JSON.stringify(location.state)}</p>;
}
function ready() {
  return render(
    <MemoryRouter initialEntries={["/UI-21"]}>
      <Routes>
        <Route path="/UI-21" element={<CashFlowScreen />} />
        <Route path="/UI-10" element={<Destination />} />
        <Route path="/UI-13" element={<Destination />} />
        <Route path="/UI-27" element={<Destination />} />
      </Routes>
    </MemoryRouter>,
  );
}
it("renders all twelve months, period totals, real statuses and read-only non-leaf cells", async () => {
  ready();
  const table = await screen.findByRole("table", { name: "자금수지표" });
  expect(within(table).getAllByRole("columnheader")).toHaveLength(16);
  expect(within(table).getAllByText("실적")).toHaveLength(9);
  expect(within(table).getAllByText("예정")).toHaveLength(3);
  expect(
    within(screen.getByTestId("cash-row-opening")).queryByRole("button"),
  ).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("cash-row-reg-pay")).getAllByRole("button"),
  ).toHaveLength(12);
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  for (const label of [
    "현금매출",
    "인건비(정규직)",
    "인건비(계약직·프리랜서)",
    "용역비(외주)",
    "법인세 납부",
    "이월금",
  ])
    expect(
      within(table).getByRole("rowheader", { name: label }),
    ).toBeInTheDocument();
  for (const label of ["외주비", "월말 잔액", "정규직 급여"])
    expect(
      within(table).queryByRole("rowheader", { name: label }),
    ).not.toBeInTheDocument();
  expect(screen.getByTestId("cash-flow-guide")).toHaveTextContent(
    "예정(회색 글자)",
  );
});
it("opens a NULL leaf in the right detail drawer, shows reference and unknown evidence and closes without writes", async () => {
  ready();
  const button = await screen.findByRole("button", {
    name: "11월 인건비(정규직) 계산근거",
  });
  await userEvent.click(button);
  const panel = await screen.findByTestId("cash-source-panel");
  expect(panel).toHaveRole("dialog");
  expect(panel).toHaveClass("sicost-drawer");
  expect(within(panel).getByRole("heading")).toHaveTextContent(
    "2026-11 인건비(정규직) 계산근거",
  );
  const evidence = await within(panel).findByTestId("cash-source-row-0");
  expect(evidence).toHaveTextContent("급여");
  expect(evidence).toHaveTextContent("정규직");
  expect(evidence).toHaveTextContent("부가세");
  expect(evidence.textContent).not.toMatch(
    /TB_|VAT|AMT|IO_DT|REAL_PAY_AMT|\b(FT|CT|FL|OS|ACTUAL|PLANNED)\b/,
  );
  expect(screen.getByRole("table", { name: "자금수지표" })).toBeInTheDocument();
  await within(panel).findByText("필수 급여 미확인", { exact: false });
  expect(
    within(panel).getByText("202610:001", { exact: false }),
  ).toBeInTheDocument();
  expect(within(panel).getByText("미확인")).toHaveClass("sicost-tone-warn");
  expect(within(panel).getByTestId("cash-source-row-0")).toHaveTextContent(
    "2026-10",
  );
  await userEvent.click(screen.getByTestId("cash-source-close"));
  expect(screen.queryByTestId("cash-source-panel")).not.toBeInTheDocument();
  expect(button).toHaveFocus();
  expect(
    fetcher.mock.calls.every(
      ([, options]) => !options?.method || options.method === "GET",
    ),
  ).toBe(true);
});
it("paginates full sources using server count and resets page on size change", async () => {
  ready();
  await userEvent.click(
    await screen.findByRole("button", { name: "11월 인건비(정규직) 계산근거" }),
  );
  await screen.findByText("전체 21건", { exact: false });
  await userEvent.click(screen.getByTestId("cash-source-next"));
  await waitFor(() =>
    expect(
      fetcher.mock.calls.some(([url]) => String(url).includes("page=2")),
    ).toBe(true),
  );
  await screen.findByText("2페이지", { exact: false });
  await userEvent.selectOptions(screen.getByTestId("cash-source-size"), "20");
  await waitFor(() =>
    expect(
      fetcher.mock.calls.some(([url]) =>
        String(url).includes("page=1&size=20"),
      ),
    ).toBe(true),
  );
});
it("keeps table when sources fail and retries only the failed read", async () => {
  fetcher.mockImplementation(async (url) =>
    String(url).includes("/sources?")
      ? new Response(
          JSON.stringify({
            success: false,
            meta: {},
            code: "INTERNAL_ERROR",
            message: "원천 조회 실패",
            requestId: "cash-trace",
          }),
          { status: 500 },
        )
      : response(data),
  );
  ready();
  await userEvent.click(
    await screen.findByRole("button", { name: "11월 인건비(정규직) 계산근거" }),
  );
  await screen.findByText("원천 조회 실패", { exact: false });
  expect(screen.getByRole("table", { name: "자금수지표" })).toBeInTheDocument();
  expect(screen.getByRole("alert")).toHaveTextContent("cash-trace");
  await userEvent.click(
    within(screen.getByTestId("cash-source-panel")).getByRole("button", {
      name: "다시 조회",
    }),
  );
  expect(
    fetcher.mock.calls.filter(([url]) => String(url).includes("/sources?"))
      .length,
  ).toBeGreaterThanOrEqual(2);
});
it("shows an empty source list without treating it as unverified", async () => {
  sources = [];
  ready();
  await userEvent.click(
    await screen.findByRole("button", { name: "11월 인건비(정규직) 계산근거" }),
  );
  await screen.findByText("조회된 원천이 없습니다.");
});
it("sends source month and tax section as one-time navigation context without saving preferences", async () => {
  sources = [{ ...source(), item: "CORP_TAX", sourceType: "TB_FI_TAX_PAY" }];
  ready();
  await userEvent.click(
    await screen.findByRole("button", { name: "11월 법인세 납부 계산근거" }),
  );
  await screen.findByTestId("cash-source-link-0");
  await userEvent.click(screen.getByTestId("cash-source-link-0"));
  expect(await screen.findByTestId("destination")).toHaveTextContent(
    '"month":"2026-11"',
  );
  expect(screen.getByTestId("destination")).toHaveTextContent(
    '"section":"tax"',
  );
  expect(
    fetcher.mock.calls.some(([, options]) => options?.method === "PUT"),
  ).toBe(false);
});
it("refuses future destination navigation but allows future evidence", async () => {
  sources = [{ ...source(), attributionMonth: "2026-12" }];
  ready();
  await userEvent.click(
    await screen.findByRole("button", { name: "12월 인건비(정규직) 계산근거" }),
  );
  await screen.findByTestId("cash-source-link-0");
  await userEvent.click(screen.getByTestId("cash-source-link-0"));
  expect(screen.getByTestId("cash-flow-screen")).toBeInTheDocument();
  expect(
    screen.getByText("목적 화면의 조회 기간 범위를 벗어나", { exact: false }),
  ).toBeInTheDocument();
});
it("refreshes annual table and selected evidence after an actual commit event", async () => {
  ready();
  await userEvent.click(
    await screen.findByRole("button", { name: "11월 인건비(정규직) 계산근거" }),
  );
  await screen.findByTestId("cash-source-row-0");
  const before = fetcher.mock.calls.length;
  act(() =>
    window.dispatchEvent(new Event("sicost:financial-items-committed")),
  );
  await waitFor(() =>
    expect(fetcher.mock.calls.length).toBeGreaterThanOrEqual(before + 2),
  );
});
it("does not display old-year table as a newly selected year result", async () => {
  ready();
  await screen.findByRole("table", { name: "자금수지표" });
  fetcher.mockImplementation(async () => new Promise<Response>(() => {}));
  act(() =>
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: { screenId: "UI-21", value: { type: "YEAR", value: "2025" } },
      }),
    ),
  );
  expect(
    screen.queryByRole("table", { name: "자금수지표" }),
  ).not.toBeInTheDocument();
});
it("hides Excel on mobile and sends no download request", async () => {
  window.innerWidth = 375;
  ready();
  await screen.findByRole("table", { name: "자금수지표" });
  expect(screen.queryByTestId("cash-export")).not.toBeInTheDocument();
});
it("restores previous unit and table when a unit-only read fails without automatic retry", async () => {
  ready();
  await screen.findByRole("table", { name: "자금수지표" });
  fetcher.mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "단위 조회 실패",
          requestId: "unit-trace",
        }),
        { status: 500 },
      ),
  );
  const before = fetcher.mock.calls.length;
  await userEvent.selectOptions(screen.getByTestId("cash-unit"), "백만원");
  await screen.findByText("단위 조회 실패", { exact: false });
  expect(screen.getByTestId("cash-unit")).toHaveValue("천원");
  expect(screen.getByRole("table", { name: "자금수지표" })).toBeInTheDocument();
  expect(fetcher.mock.calls).toHaveLength(before + 1);
});
it("blocks duplicate Excel execution and separates failed download from a successful query", async () => {
  ready();
  await screen.findByRole("table", { name: "자금수지표" });
  let reject!: (reason: Error) => void;
  fetcher.mockImplementation(
    async () =>
      new Promise<Response>((_resolve, fail) => {
        reject = fail;
      }),
  );
  fireEvent.click(screen.getByTestId("cash-export"));
  fireEvent.click(screen.getByTestId("cash-export"));
  expect(screen.getByTestId("cash-export")).toBeDisabled();
  expect(
    fetcher.mock.calls.filter(([url]) =>
      String(url).includes("/export?year=2026&unit=THOUSAND_WON"),
    ),
  ).toHaveLength(1);
  await act(async () => reject(new Error("DNLD 기록 실패")));
  expect(await screen.findByRole("alert")).toHaveTextContent("DNLD 기록 실패");
  expect(screen.getByRole("table", { name: "자금수지표" })).toBeInTheDocument();
  expect(screen.getByTestId("cash-export")).toBeEnabled();
});
it("shows annual API failures with request id and explicit read retry", async () => {
  fetcher.mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "PERIOD_OUT_OF_SCOPE",
        message: "기간 범위 밖",
        requestId: "period-trace",
      }),
      { status: 409 },
    ),
  );
  ready();
  expect(await screen.findByRole("alert")).toHaveTextContent("period-trace");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});
it("marks planned columns and only server shortage closing balances, preserving zero and NULL", async () => {
  data.months[10].shortage = true;
  data.months[10].closingBalance = "-1";
  data.months[11].closingBalance = "0";
  ready();
  await screen.findByRole("table", { name: "자금수지표" });
  const cells = within(screen.getByTestId("cash-row-balance")).getAllByRole(
    "cell",
  );
  expect(cells[10]).toHaveClass("cash-shortage", "cash-planned");
  expect(cells[10]).toHaveTextContent("−0.001");
  expect(cells[11]).not.toHaveClass("cash-shortage");
  expect(cells[11]).toHaveTextContent("0");
  expect(cells[0]).toHaveTextContent("–");
});

it("renders BASE-before months as unavailable without calculation-source actions", async () => {
  data.baseMonth = "2026-06";
  for (const month of data.months.slice(0, 5)) {
    month.status = "BEFORE_BASE";
    month.openingBalance = null;
    month.closingBalance = null;
    month.items = month.items.map((item) => ({ ...item, amount: null }));
  }
  ready();
  await screen.findByRole("table", { name: "자금수지표" });
  expect(
    screen.queryByTestId("cash-source-cash-sales-2026-05"),
  ).not.toBeInTheDocument();
  expect(
    screen.getByTestId("cash-source-cash-sales-2026-06"),
  ).toBeInTheDocument();
  expect(
    within(screen.getByTestId("cash-row-opening")).getAllByRole("cell")[0],
  ).toHaveTextContent("–");
});

it("대시보드의 다음연도 요청월은 연도범위 확장·다른 원천 상세 없이 안내한다", async () => {
  render(
    <MemoryRouter
      initialEntries={[
        {
          pathname: "/UI-21",
          state: { fromDashboard: true, month: "2027-01" },
        },
      ]}
    >
      <CashFlowScreen />
    </MemoryRouter>,
  );
  expect(await screen.findByText(/대시보드 요청월 2027-01/)).toBeVisible();
  await waitFor(() =>
    expect(screen.getByText(/조회 가능 연도 밖/)).toBeVisible(),
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});

it("uses server shortages for a red alert, won balances, header warnings and receivables navigation", async () => {
  data.minimumCash = "1000000000";
  data.shortages = [
    {
      month: "2026-10",
      balance: "950000000",
      status: "PLANNED",
      shortage: true,
    },
    {
      month: "2026-12",
      balance: "980000000",
      status: "PLANNED",
      shortage: true,
    },
  ];
  data.months[9].shortage = true;
  data.months[11].shortage = true;
  ready();
  const banner = await screen.findByTestId("cash-shortage-alert");
  expect(banner).toHaveRole("alert");
  expect(banner).toHaveTextContent(
    "10월, 12월 월말 잔액이 최소 운영자금(1,000,000,000원)보다 적을 것으로 예상됩니다.",
  );
  expect(banner).toHaveTextContent(
    "예상 잔액: 10월 950,000,000원 · 12월 980,000,000원",
  );
  expect(screen.getAllByTestId(/cash-shortage-warning-/)).toHaveLength(2);
  expect(screen.getByTestId("cash-shortage-warning-2026-10")).toHaveAttribute(
    "title",
    "월말 잔액이 최소 운영자금보다 적을 것으로 예상",
  );
  await userEvent.click(
    within(banner).getByRole("button", { name: "미수금 보기" }),
  );
  expect(await screen.findByTestId("destination")).toHaveTextContent(
    '"section":"receivables"',
  );
});

it("does not infer shortages locally and hides settings when the menu is unavailable", async () => {
  data.minimumCash = "1000000000";
  data.months[10].closingBalance = "1";
  ready();
  await screen.findByRole("table", { name: "자금수지표" });
  expect(screen.queryByTestId("cash-shortage-alert")).not.toBeInTheDocument();
  expect(screen.queryAllByTestId(/cash-shortage-warning-/)).toHaveLength(0);
  expect(screen.queryByTestId("cash-standards")).not.toBeInTheDocument();
});

it("only exposes standards through the user's menu and sends the standards tab", async () => {
  render(
    <SharedScreenContext.Provider
      value={{
        menus: [
          {
            menuId: "settings",
            parentId: null,
            name: "공통코드",
            screenId: "UI-27",
            sortOrder: 1,
            icon: null,
            pcOnly: true,
            children: [],
          },
        ],
        favorites: [],
        star: () => {},
        pending: false,
      }}
    >
      <MemoryRouter initialEntries={["/UI-21"]}>
        <Routes>
          <Route path="/UI-21" element={<CashFlowScreen />} />
          <Route path="/UI-27" element={<Destination />} />
        </Routes>
      </MemoryRouter>
    </SharedScreenContext.Provider>,
  );
  const button = await screen.findByRole("button", {
    name: "기초 잔액·최소 운영자금 설정",
  });
  expect(
    screen.getByText(
      "자금수지 시작 월·기초 잔액·최소 운영자금은 운영 기준값에서 관리자가 정합니다.",
    ),
  ).toBeInTheDocument();
  await userEvent.click(button);
  expect(await screen.findByTestId("destination")).toHaveTextContent(
    '"section":"standards"',
  );
});
