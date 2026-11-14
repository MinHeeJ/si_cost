import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { ApplicationRoutes } from "./router";
import * as shared from "../pages/shared/sharedApi";
import * as prefs from "../pages/me/preferencesApi";
import * as structure from "../pages/structure/profitStructureApi";
import * as operating from "../pages/operating/operatingProfitApi";
import * as net from "../pages/net/netProfitApi";
import * as plans from "../pages/businessplansops/businessPlanPerformanceApi";

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
vi.mock("../pages/structure/profitStructureApi");
vi.mock("../pages/operating/operatingProfitApi");
vi.mock("../pages/net/netProfitApi");
vi.mock(
  "../pages/businessplansops/businessPlanPerformanceApi",
  async (original) => ({
    ...(await original<
      typeof import("../pages/businessplansops/businessPlanPerformanceApi")
    >()),
    getBusinessPlanPerformance: vi.fn(),
  }),
);
const screens = [
  {
    id: "UI-07",
    name: "전체 수익 구조",
    testId: "profit-structure-screen",
    read: structure.getProfitLossStructure,
  },
  {
    id: "UI-08",
    name: "영업이익표",
    testId: "operating-profit-screen",
    read: operating.getOperatingProfitLoss,
  },
  {
    id: "UI-09",
    name: "순이익표",
    testId: "net-profit-screen",
    read: net.getNetProfitLoss,
  },
  {
    id: "UI-18",
    name: "사업계획 대비 실적",
    testId: "business-plan-performance",
    read: plans.getBusinessPlanPerformance,
  },
];
const context: plans.BusinessPlanPerformanceData["context"] = {
  year: 2026,
  month: "2026-07",
  baseMonth: "2026-01",
  nowMonth: "2026-07",
  timezone: "Asia/Seoul",
  snapshotAt: "2026-07-15T09:00:00+09:00",
  aggregation: "YTD",
  amountUnit: "WON",
  readOnly: true,
};
const completeness: plans.PlanActual["completeness"] = {
  costComplete: false,
  revenueComplete: false,
  taxComplete: false,
  missing: ["salesAmt"],
  estimated: [],
  sourceMonths: [],
  issues: [],
  availableMonthCount: 0,
  requestedMonthCount: 7,
  missingMonths: ["2026-07"],
};
const months: plans.PlanActual[] = Array.from({ length: 12 }, (_, index) => ({
  month: `2026-${String(index + 1).padStart(2, "0")}`,
  sourceKind: index > 6 ? "FUTURE" : "MISSING",
  closeStatus: null,
  fixed: false,
  migrated: false,
  provisional: false,
  amounts: {},
  completeness,
  sources: [],
  calculatedAt: context.snapshotAt,
}));
const statement: operating.OperatingProfitData = {
  context,
  months,
  summary: [],
  rows: [],
  showPreviousYear: false,
  cumulativeProvisional: false,
  completeness,
  notices: ["서버에서 확인한 손익 자료"],
};
function open(id: string, state?: object) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: `/${id}`, state }]}>
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
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  );
  vi.mocked(shared.getMenus).mockResolvedValue({
    items: [
      {
        menuId: "FINANCE",
        parentId: null,
        name: "재무·자금",
        screenId: null,
        sortOrder: 1,
        icon: null,
        pcOnly: false,
        children: screens.map((item, index) => ({
          menuId: item.id,
          parentId: "FINANCE",
          name: item.name,
          screenId: item.id,
          sortOrder: index,
          icon: null,
          pcOnly: false,
          children: [],
        })),
      },
    ],
    diagnostics: [],
  });
  vi.mocked(shared.getCurrentUser).mockResolvedValue({
    userId: "admin",
    name: "임시 관리자",
    roleIds: ["ADMIN"],
    developmentMode: true,
  });
  vi.mocked(shared.getPeriodContext).mockResolvedValue({
    currentDate: "2026-07-15",
    currentMonth: "2026-07",
    timezone: "Asia/Seoul",
    cashBaseMonth: "2026-01",
    monthClose: { month: "2026-07", status: "작업중" },
  });
  vi.mocked(shared.getMonthClose).mockResolvedValue({ items: [] });
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(prefs.getPreferences).mockResolvedValue({ items: [] });
  vi.mocked(prefs.putPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, stored: true, revision: "saved" },
    }),
  );
  vi.mocked(shared.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, stored: true, revision: "saved" },
    }),
  );
  vi.mocked(structure.getProfitLossStructure).mockResolvedValue({
    context,
    months,
    summary: [],
    buckets: [],
    waterfall: [],
    numericRows: [],
    calculationMethods: [],
    notices: statement.notices,
  });
  vi.mocked(operating.getOperatingProfitLoss).mockResolvedValue(statement);
  vi.mocked(net.getNetProfitLoss).mockResolvedValue({
    ...statement,
    contributionMonth: context.month,
    contributions: {
      visible: false,
      reason: "세부 원천 미확인",
      complete: false,
      revenueDifference: null,
      contractTypes: [],
      customers: [],
      salesParts: [],
    },
  });
  vi.mocked(plans.getBusinessPlanPerformance).mockResolvedValue({
    context,
    targetStatus: "UNREGISTERED",
    summary: [],
    months: months.map((actual) => ({
      month: actual.month,
      actual,
      salesTargetAmt: null,
      operatingProfitTargetAmt: null,
      salesActualAmt: null,
      operatingProfitActualAmt: null,
      salesAchievementRate: null,
      operatingMarginDifference: null,
      salesUnderTarget: null,
      operatingMarginAdverse: null,
      targetRegistered: false,
    })),
    notices: statement.notices,
  });
});
it.each(screens)(
  "/$id renders the real screen through the application router",
  async (item) => {
    open(item.id);
    const content = await screen.findByTestId(item.testId);
    expect(
      await within(content).findByText(statement.notices[0]),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: item.name }),
    ).toBeInTheDocument();
    expect(screen.getByText("개발용 임시 관리자")).toBeInTheDocument();
    expect(item.read).toHaveBeenCalledWith(
      2026,
      "2026-07",
      ...(item.id === "UI-07" ? ["MONTH"] : []),
      expect.any(AbortSignal),
    );
    expect(
      screen.getByRole("navigation", { name: "열린 화면" }),
    ).toHaveTextContent(item.name);
    expect(screen.getByLabelText("누계 월")).toHaveValue("2026-07");
    expect(
      screen.queryByText("이 화면은 다음 단계에서 만듭니다."),
    ).not.toBeInTheDocument();
  },
);
it.each(screens)(
  "/$id retries failed reads without saving business data",
  async (item) => {
    vi.mocked(item.read).mockRejectedValueOnce(
      new Error("재무 원천 조회 실패"),
    );
    open(item.id);
    const content = await screen.findByTestId(item.testId);
    await within(content).findByText("재무 원천 조회 실패");
    await userEvent.click(
      within(content).getByRole("button", { name: "다시 조회" }),
    );
    await within(content).findByText(statement.notices[0]);
    expect(item.read).toHaveBeenCalledTimes(2);
    expect(prefs.putPreference).not.toHaveBeenCalledWith(
      "PERIOD",
      expect.anything(),
      expect.anything(),
      expect.anything(),
    );
  },
);
it.each(["UI-08", "UI-09"])(
  "/%s receives a month without overwriting other PERIOD preferences",
  async (id) => {
    open(id, { period: { type: "YEAR_CUM", year: "2025", month: "2025-05" } });
    await waitFor(() =>
      expect(
        id === "UI-08"
          ? operating.getOperatingProfitLoss
          : net.getNetProfitLoss,
      ).toHaveBeenCalledWith(2025, "2025-05", expect.any(AbortSignal)),
    );
    expect(screen.getByLabelText("누계 월")).toHaveValue("2025-05");
    expect(shared.putSharedPreference).not.toHaveBeenCalled();
  },
);
it("keeps open financial tabs mounted and refreshes them after an import", async () => {
  open("UI-07");
  await screen.findByText(statement.notices[0]);
  await userEvent.click(screen.getByRole("link", { name: "영업이익표" }));
  await screen.findByTestId("operating-profit-screen");
  await waitFor(() =>
    expect(operating.getOperatingProfitLoss).toHaveBeenCalled(),
  );
  const previousStructure = vi.mocked(structure.getProfitLossStructure).mock
    .calls.length;
  const previousOperating = vi.mocked(operating.getOperatingProfitLoss).mock
    .calls.length;
  act(() =>
    window.dispatchEvent(
      new Event("sicost:historical-profit-loss-import-committed"),
    ),
  );
  await waitFor(() =>
    expect(structure.getProfitLossStructure).toHaveBeenCalledTimes(
      previousStructure + 1,
    ),
  );
  await waitFor(() =>
    expect(operating.getOperatingProfitLoss).toHaveBeenCalledTimes(
      previousOperating + 1,
    ),
  );
  expect(
    screen.getByRole("navigation", { name: "열린 화면" }),
  ).toHaveTextContent("전체 수익 구조");
  expect(
    screen.getByRole("navigation", { name: "열린 화면" }),
  ).toHaveTextContent("영업이익표");
});
