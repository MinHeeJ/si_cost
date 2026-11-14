import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { BusinessPlanPerformanceScreen } from "./BusinessPlanPerformanceScreen";
import { BusinessPlanPerformanceScreen as RegisteredScreen } from "../shared/BusinessPlanPerformanceScreen";
import * as api from "./businessPlanPerformanceApi";
import type { ReactNode } from "react";
vi.mock("./businessPlanPerformanceApi", async (original) => ({
  ...(await original<typeof import("./businessPlanPerformanceApi")>()),
  getBusinessPlanPerformance: vi.fn(),
  downloadBusinessPlanTemplate: vi.fn(),
}));
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
vi.mock("../apiops/FinancialHistoryPanel", () => ({
  FinancialHistoryPanel: () => <div />,
}));
export function result(
  status: api.BusinessPlanPerformanceData["targetStatus"] = "REGISTERED",
): api.BusinessPlanPerformanceData {
  return {
    context: {
      year: 2026,
      month: "2026-07",
      baseMonth: "202601",
      nowMonth: "2026-07",
      timezone: "Asia/Seoul",
      snapshotAt: "2026-07-15T09:00:00+09:00",
      aggregation: "YTD",
      amountUnit: "WON",
      readOnly: true,
    },
    targetStatus: status,
    summary: [
      "CUMULATIVE_SALES",
      "CUMULATIVE_OPERATING_PROFIT",
      "CURRENT_MONTH_SALES",
    ].map((metric) => ({
      metric: metric as api.PlanSummary["metric"],
      actualAmt: "99000",
      targetAmt: status === "UNREGISTERED" ? null : "99001",
      achievementRate: status === "UNREGISTERED" ? null : "100.0",
      underTarget: status === "UNREGISTERED" ? null : true,
    })),
    months: Array.from({ length: 12 }, (_, index) => {
      const month = `2026-${String(index + 1).padStart(2, "0")}`,
        future = index > 6;
      return {
        month,
        salesTargetAmt: status === "UNREGISTERED" ? null : "99001",
        operatingProfitTargetAmt: status === "UNREGISTERED" ? null : "-100",
        salesActualAmt: future ? null : "99000",
        operatingProfitActualAmt: future ? null : "-50",
        salesAchievementRate:
          future || status === "UNREGISTERED" ? null : "100.0",
        operatingMarginDifference: future ? null : "0.0",
        salesUnderTarget: future || status === "UNREGISTERED" ? null : true,
        operatingMarginAdverse: future ? null : true,
        targetRegistered: status !== "UNREGISTERED",
        actual: {
          month,
          sourceKind: future
            ? "FUTURE"
            : index === 0
              ? "CLOSED_SNAPSHOT"
              : "CALCULATED",
          closeStatus: future ? null : "WORK",
          fixed: index === 0,
          migrated: false,
          provisional: !future && index !== 0,
          amounts: { salesAmt: future ? null : "99000" },
          completeness: {
            costComplete: true,
            revenueComplete: true,
            taxComplete: false,
            missing: ["corpTaxAmt"],
            estimated: index === 6 ? ["급여 예정"] : [],
            sourceMonths: [month],
            issues: [],
            availableMonthCount: 1,
            requestedMonthCount: 1,
            missingMonths: [],
          },
          sources: [],
          calculatedAt: "2026-07-15T09:00:00+09:00",
        },
      };
    }),
    notices:
      status === "UNREGISTERED"
        ? ["2026년 사업계획 목표가 아직 없습니다. [목표 업로드]로 올려 주세요."]
        : status === "INCOMPLETE"
          ? ["일부 월 사업계획 목표가 없습니다."]
          : status === "ZERO_TARGETS"
            ? ["목표 없음 (미등록 아님)"]
            : ["잠정 · 예정 포함"],
  };
}
function period(year = "2026", month = "2026-07") {
  act(() =>
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: { screenId: "UI-18", value: { type: "YEAR_CUM", year, month } },
      }),
    ),
  );
}
beforeEach(() => {
  vi.clearAllMocks();
  Object.defineProperty(window, "innerWidth", {
    value: 1280,
    configurable: true,
  });
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(result());
});
async function draw() {
  render(<BusinessPlanPerformanceScreen />);
  period();
  await screen.findByRole("table", { name: "사업계획 월별 실적" });
}
it("uses retained route export and renders three cards, six rows and twelve monthly columns without editing or export", async () => {
  expect(RegisteredScreen).toBe(BusinessPlanPerformanceScreen);
  await draw();
  expect(within(screen.getByRole("table")).getAllByRole("row")).toHaveLength(7);
  expect(
    within(screen.getByRole("table")).getAllByRole("columnheader"),
  ).toHaveLength(13);
  expect(screen.getByText("누계 매출 달성률")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /실적 다운로드|저장/ }),
  ).not.toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("keeps unregistered actuals and upload guidance", async () => {
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(
    result("UNREGISTERED"),
  );
  await draw();
  expect(
    screen.getByText(/2026년 사업계획 목표가 아직 없습니다/),
  ).toBeInTheDocument();
  expect(
    screen.getByTestId("business-plan-row-sales-actual-amt"),
  ).toHaveTextContent("0.099");
  expect(
    screen.getByTestId("business-plan-summary-cumulative-sales"),
  ).toHaveTextContent("–");
});
it("shows registered zero and partial target notices distinctly", async () => {
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(
    result("INCOMPLETE"),
  );
  await draw();
  expect(
    screen.getByText("일부 월 사업계획 목표가 없습니다."),
  ).toBeInTheDocument();
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(
    result("ZERO_TARGETS"),
  );
  act(() => window.dispatchEvent(new Event(api.BUSINESS_PLAN_COMMITTED)));
  await screen.findByText("목표 없음 (미등록 아님)");
});
it("retains red color for rounded hundred and rounded zero margin", async () => {
  await draw();
  expect(
    within(
      screen.getByTestId("business-plan-summary-cumulative-sales"),
    ).getByText("100.0%"),
  ).toHaveClass("business-plan-adverse");
  expect(
    within(
      screen.getByTestId("business-plan-row-operating-margin-difference"),
    ).getAllByText("0.0%p")[0],
  ).toHaveClass("business-plan-adverse");
});
it("does not color undefined denominators red", async () => {
  const data = result();
  data.summary[0] = {
    ...data.summary[0],
    targetAmt: "0",
    achievementRate: null,
    underTarget: null,
  };
  data.months[0].salesAchievementRate = null;
  data.months[0].salesUnderTarget = null;
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(data);
  await draw();
  expect(
    within(
      screen.getByTestId("business-plan-summary-cumulative-sales"),
    ).getByText("–"),
  ).not.toHaveClass("business-plan-adverse");
});
it("shows future targets but omits future bars and preserves source explanations", async () => {
  await draw();
  expect(
    screen.queryByTestId("business-plan-actual-bar-2026-12"),
  ).not.toBeInTheDocument();
  expect(
    screen.getByTestId("business-plan-row-sales-target-amt"),
  ).toHaveTextContent("0.099001");
  expect(screen.getByLabelText("매출 실적 원천 설명")).toHaveAttribute(
    "title",
    expect.stringContaining("TK는 청구일"),
  );
  expect(screen.getByLabelText("영업이익 실적 원천 설명")).toHaveAttribute(
    "title",
    expect.stringContaining("입금액 아님"),
  );
  expect(screen.getAllByText("예정 포함").length).toBeGreaterThan(0);
});
it("formats large exact amounts by string scaling, not Number", async () => {
  const data = result();
  data.summary[0].actualAmt = "9007199254740993";
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(data);
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("단위"), "원");
  expect(
    screen.getByTestId("business-plan-summary-cumulative-sales"),
  ).toHaveTextContent("9,007,199,254,740,993");
});
it("downloads the selected year template but opens current-year upload from a past-year view", async () => {
  const data = result();
  data.context.year = 2025;
  data.context.month = "2025-12";
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(data);
  render(<BusinessPlanPerformanceScreen />);
  period("2025", "2025-12");
  await screen.findByRole("table");
  await userEvent.click(screen.getByTestId("business-plan-template"));
  expect(api.downloadBusinessPlanTemplate).toHaveBeenCalledWith(2025);
  await userEvent.click(screen.getByTestId("business-plan-upload"));
  expect(
    await screen.findByTestId("business-plan-import-year"),
  ).toHaveTextContent("대상연도 2026");
  expect(screen.getByLabelText("파일 선택")).toBeEnabled();
});
it("refreshes on source commit without a write or polling", async () => {
  await draw();
  act(() => window.dispatchEvent(new Event("sicost:bills-committed")));
  await waitFor(() =>
    expect(api.getBusinessPlanPerformance).toHaveBeenCalledTimes(2),
  );
});
it("shows loading, read error and explicit read-only retry", async () => {
  vi.mocked(api.getBusinessPlanPerformance).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  render(<BusinessPlanPerformanceScreen />);
  expect(screen.getByRole("status")).toBeInTheDocument();
  period();
  await screen.findByText("조회 실패");
  await userEvent.click(screen.getByRole("button", { name: /다시/ }));
  await screen.findByRole("table");
  expect(api.getBusinessPlanPerformance).toHaveBeenCalledTimes(2);
});
it("ignores a stale response after period changes", async () => {
  let resolve!: (data: api.BusinessPlanPerformanceData) => void;
  vi.mocked(api.getBusinessPlanPerformance).mockReturnValueOnce(
    new Promise((r) => {
      resolve = r;
    }),
  );
  render(<BusinessPlanPerformanceScreen />);
  period();
  await waitFor(() =>
    expect(api.getBusinessPlanPerformance).toHaveBeenCalledOnce(),
  );
  const next = result();
  next.context.month = "2026-06";
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(next);
  period("2026", "2026-06");
  await screen.findByRole("table");
  await act(async () => resolve(result()));
  expect(screen.getByText(/2026년 1~06월/)).toBeInTheDocument();
});
it("disables PC-only actions on narrow screens without hiding actuals", async () => {
  Object.defineProperty(window, "innerWidth", { value: 600 });
  await draw();
  expect(screen.getByTestId("business-plan-template")).toBeDisabled();
  expect(screen.getByTestId("business-plan-upload")).toBeDisabled();
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
    expect(api.getBusinessPlanPerformance).toHaveBeenCalledTimes(2),
  );
});
