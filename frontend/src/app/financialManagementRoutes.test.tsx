import {
  act,
  render,
  screen,
  waitFor,
  within,
} from "../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { ApplicationRoutes, applicationRoutes } from "./router";
import * as shared from "../pages/shared/sharedApi";
import * as prefs from "../pages/me/preferencesApi";
import * as cash from "../pages/cashflowops/cashFlowApi";
import * as close from "../pages/monthclosesops/monthCloseApi";

vi.mock("../pages/shared/sharedApi");
vi.mock("../pages/me/preferencesApi", async (original) => ({
  ...(await original<typeof import("../pages/me/preferencesApi")>()),
  getPreferences: vi.fn(),
  putPreference: vi.fn(),
  deletePreference: vi.fn(),
  preferencesApi: {
    getPreferences: (...args: []) => prefs.getPreferences(...args),
    putPreference: (...args: Parameters<typeof prefs.putPreference>) =>
      prefs.putPreference(...args),
    deletePreference: (...args: Parameters<typeof prefs.deletePreference>) =>
      prefs.deletePreference(...args),
  },
}));
vi.mock("../pages/cashflowops/cashFlowApi", async (original) => ({
  ...(await original<typeof import("../pages/cashflowops/cashFlowApi")>()),
  getCashFlow: vi.fn(),
  getCashFlowExport: vi.fn(),
}));
vi.mock("../pages/monthclosesops/monthCloseApi", async (original) => ({
  ...(await original<typeof import("../pages/monthclosesops/monthCloseApi")>()),
  getMonthCloses: vi.fn(),
  saveMonthCloseChecks: vi.fn(),
  saveMonthClose: vi.fn(),
  saveMonthCloseReopen: vi.fn(),
}));
const context = {
  currentDate: "2026-11-15",
  currentMonth: "2026-11",
  timezone: "Asia/Seoul",
  cashBaseMonth: "2025-01",
  monthClose: { month: "2026-11", status: "작업중" },
};
const routes = [
  { id: "UI-21", name: "자금수지표" },
  { id: "UI-14", name: "월 마감" },
];
let preferences: prefs.Preference[];
function annual(year: number, unit: cash.CashUnit): cash.CashFlowResult {
  const values = {
    openingBalance: "0",
    items: cash.cashItems.map((item) => ({
      item,
      amount: "0",
      includesVat: true,
      verified: true,
    })),
    incomeTotal: "0",
    expenseTotal: "0",
    closingBalance: "0",
  };
  const months: cash.CashMonth[] = Array.from({ length: 12 }, (_, index) => ({
    ...values,
    month: `${year}-${String(index + 1).padStart(2, "0")}`,
    status: "ACTUAL",
    verified: true,
    shortage: false,
    issues: [],
  }));
  return {
    year,
    unit,
    asOfDate: context.currentDate,
    timezone: context.timezone,
    baseMonth: context.cashBaseMonth,
    baseAmount: "0",
    minimumCash: "0",
    minimumCashMonth: context.cashBaseMonth,
    months,
    periodTotals: (["FIRST_HALF", "SECOND_HALF", "YEAR"] as const).map(
      (period) => ({ ...values, period }),
    ),
    chartPoints: months.map((m) => ({
      month: m.month,
      balance: m.closingBalance,
      status: m.status,
      shortage: false,
    })),
    shortages: [],
    issues: [],
  };
}
function mount(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/${id}`]}>
      <ApplicationRoutes />
    </MemoryRouter>,
  );
}
beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 1280,
  });
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  });
  preferences = [
    {
      type: "AMT_UNIT",
      key: "*",
      value: "백만원",
      revision: "unit-v1",
      stored: true,
    },
  ];
  vi.mocked(prefs.getPreferences).mockImplementation(async () => ({
    items: preferences,
  }));
  vi.mocked(prefs.putPreference).mockImplementation(
    async (type, key, value) => {
      const preference = { type, key, value, revision: "saved", stored: true };
      preferences = [
        ...preferences.filter((item) => item.type !== type || item.key !== key),
        preference,
      ];
      return { changed: true, preference };
    },
  );
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(shared.getCurrentUser).mockResolvedValue({
    userId: "admin",
    name: "임시 관리자",
    roleIds: ["ADMIN"],
    developmentMode: true,
  });
  vi.mocked(shared.getMenus).mockResolvedValue({
    items: routes.map((route, index) => ({
      menuId: route.id,
      parentId: null,
      name: route.name,
      screenId: route.id,
      sortOrder: index,
      icon: null,
      pcOnly: route.id === "UI-14",
      children: [],
    })),
    diagnostics: [],
  });
  vi.mocked(shared.getPeriodContext).mockResolvedValue(context);
  vi.mocked(shared.getMonthClose).mockResolvedValue({ items: [] });
  vi.mocked(shared.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, revision: "saved", stored: true },
    }),
  );
  vi.mocked(cash.getCashFlow).mockImplementation(async (year, unit) =>
    annual(year ?? 2026, unit),
  );
  vi.mocked(close.getMonthCloses).mockImplementation(
    async (month, page = 1, size = 50) => ({
      month,
      status: "WORK",
      closedBy: null,
      closedAt: null,
      nextCloseMonth: month,
      latestCloseMonth: null,
      managementAllowed: true,
      canCheck: true,
      canClose: true,
      canReopen: false,
      revision: "complete-revision",
      checks: [],
      checkMode: "CURRENT",
      warningCount: 0,
      history: { items: [], total: 0, page, size, hasMore: false },
      issues: [],
    }),
  );
});
it.each(routes)(
  "/$id renders the merged feature exactly once in the existing shell",
  async ({ id, name }) => {
    mount(id);
    const content = await screen.findByTestId(
      id === "UI-21" ? "cash-flow-screen" : "month-close-screen",
    );
    if (id === "UI-21")
      await within(content).findByRole("table", { name: "자금수지표" });
    else await within(content).findByText("선택연도 마감 이력이 없습니다.");
    expect(screen.getAllByRole("heading", { name })).toHaveLength(1);
    expect(
      applicationRoutes.filter((route) => route.path === `/${id}`),
    ).toHaveLength(1);
    expect(
      applicationRoutes.some((route) => route.path?.startsWith(`/${id}-`)),
    ).toBe(false);
    expect(
      screen.getByRole("navigation", { name: "열린 화면" }),
    ).toHaveTextContent(name);
    expect(
      screen.getByRole("region", { name: "화면 기간" }),
    ).toBeInTheDocument();
  },
);
it("UI-21 uses and saves the global amount unit for query and export", async () => {
  mount("UI-21");
  await screen.findByRole("table", { name: "자금수지표" });
  expect(screen.getByTestId("cash-unit")).toHaveValue("백만원");
  await userEvent.selectOptions(screen.getByTestId("cash-unit"), "원");
  await waitFor(() =>
    expect(prefs.putPreference).toHaveBeenCalledWith(
      "AMT_UNIT",
      "*",
      "원",
      "unit-v1",
    ),
  );
  await waitFor(() =>
    expect(cash.getCashFlow).toHaveBeenLastCalledWith(
      2026,
      "WON",
      expect.any(AbortSignal),
    ),
  );
  await userEvent.click(screen.getByTestId("cash-export"));
  expect(cash.getCashFlowExport).toHaveBeenCalledWith(2026, "WON");
});
it("UI-21 preserves the previous amount unit when preference persistence fails", async () => {
  vi.mocked(prefs.putPreference).mockImplementation(
    async (type, key, value) => {
      if (type === "AMT_UNIT") throw new Error("단위 저장 실패");
      return {
        changed: true,
        preference: { type, key, value, revision: "saved", stored: true },
      };
    },
  );
  mount("UI-21");
  await screen.findByRole("table", { name: "자금수지표" });
  await userEvent.selectOptions(screen.getByTestId("cash-unit"), "원");
  expect(await screen.findByText("단위 저장 실패")).toBeInTheDocument();
  expect(screen.getByTestId("cash-unit")).toHaveValue("백만원");
});
it.each(routes)(
  "/$id keeps read failures recoverable without executing commands",
  async ({ id }) => {
    if (id === "UI-21")
      vi.mocked(cash.getCashFlow).mockRejectedValue(
        new Error("원천 조회 실패"),
      );
    else
      vi.mocked(close.getMonthCloses).mockRejectedValue(
        new Error("원천 조회 실패"),
      );
    mount(id);
    await screen.findByText("원천 조회 실패");
    vi.mocked(cash.getCashFlow).mockImplementation(async (year, unit) =>
      annual(year ?? 2026, unit),
    );
    vi.mocked(close.getMonthCloses).mockResolvedValue({
      month: "2026-11",
      status: "WORK",
      closedBy: null,
      closedAt: null,
      nextCloseMonth: "2026-11",
      latestCloseMonth: null,
      managementAllowed: true,
      canCheck: true,
      canClose: true,
      canReopen: false,
      revision: "complete-revision",
      checks: [],
      checkMode: "CURRENT",
      warningCount: 0,
      history: { items: [], total: 0, page: 1, size: 50, hasMore: false },
      issues: [],
    });
    await userEvent.click(
      id === "UI-21"
        ? within(screen.getByTestId("cash-flow-screen")).getByRole("button", {
            name: "다시 조회",
          })
        : screen.getByTestId("month-close-reload-button"),
    );
    await waitFor(() =>
      expect(screen.queryByText("원천 조회 실패")).not.toBeInTheDocument(),
    );
    expect(close.saveMonthCloseChecks).not.toHaveBeenCalled();
    expect(close.saveMonthClose).not.toHaveBeenCalled();
    expect(close.saveMonthCloseReopen).not.toHaveBeenCalled();
  },
);
it("refreshes the open cash and close screens after the native committed event", async () => {
  mount("UI-21");
  await screen.findByRole("table", { name: "자금수지표" });
  await userEvent.click(screen.getByRole("link", { name: "월 마감" }));
  await screen.findByText("선택연도 마감 이력이 없습니다.");
  const cashReads = vi.mocked(cash.getCashFlow).mock.calls.length;
  const closeReads = vi.mocked(close.getMonthCloses).mock.calls.length;
  act(() => close.publishMonthCloseCommit("2026-11"));
  await waitFor(() =>
    expect(cash.getCashFlow).toHaveBeenCalledTimes(cashReads + 1),
  );
  await waitFor(() =>
    expect(close.getMonthCloses).toHaveBeenCalledTimes(closeReads + 1),
  );
});
