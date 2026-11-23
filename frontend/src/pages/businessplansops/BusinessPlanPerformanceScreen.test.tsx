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
import { ScreenGuide } from "../../shared/ScreenGuide";
import { ScreenPermissionContext } from "../../shared/permission/PermissionBoundary";
vi.mock("./businessPlanPerformanceApi", async (original) => ({
  ...(await original<typeof import("./businessPlanPerformanceApi")>()),
  getBusinessPlanPerformance: vi.fn(),
  downloadBusinessPlanTemplate: vi.fn(),
}));
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children, headerActions, screenGuide }: {
    children: ReactNode;
    headerActions?: ReactNode;
    screenGuide?: ReactNode;
  }) => (
    <ScreenPermissionContext.Provider value="UI-18">
      <section>
        {headerActions}
        {screenGuide && <ScreenGuide screenId="UI-18" screenName="사업계획 대비 실적">{screenGuide}</ScreenGuide>}
        {children}
      </section>
    </ScreenPermissionContext.Provider>
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
        operatingMarginDifference:
          future || status === "UNREGISTERED" ? null : "0.0",
        salesUnderTarget: future || status === "UNREGISTERED" ? null : true,
        operatingMarginAdverse:
          future || status === "UNREGISTERED" ? null : true,
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
          ? [
              "일부 달의 목표가 없어 누계 달성률을 표시하지 않습니다. 12개월 목표를 올려 주세요.",
            ]
          : status === "ZERO_TARGETS"
            ? [
                "12개월 목표가 모두 0입니다(목표 없음). 달성률은 표시하지 않습니다.",
              ]
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
    screen.getByText("2026년 목표 미등록"),
  ).toBeInTheDocument();
  expect(
    screen.getByTestId("business-plan-row-sales-actual-amt"),
  ).toHaveTextContent("99");
  expect(
    screen.getByTestId("business-plan-summary-cumulative-sales"),
  ).toHaveTextContent("–");
  const banner = screen.getByTestId("business-plan-unregistered");
  expect(banner).toHaveClass("business-plan-target-status");
  expect(within(banner).getByText("2026년 목표 미등록")).toHaveClass("sicost-tone-neutral");
  expect(screen.getByRole("group", { name: "조회 기간 상태" })).toContainElement(banner);
  expect(banner).toHaveTextContent("목표 업로드로 등록해 주세요.");
  expect(
    banner.compareDocumentPosition(
      screen.getByTestId("business-plan-summary-cumulative-sales"),
    ) & Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  expect(screen.queryByText(/확인 필요/)).not.toBeInTheDocument();
  expect(
    screen.getAllByText("2026년 목표 미등록"),
  ).toHaveLength(1);
  for (const metric of [
    "cumulative-sales",
    "cumulative-operating-profit",
    "current-month-sales",
  ]) {
    expect(
      screen
        .getByTestId(`business-plan-summary-${metric}`)
        .querySelector("strong"),
    ).toHaveTextContent("–");
  }
  for (const metric of [
    "sales-target-amt",
    "operating-profit-target-amt",
    "sales-achievement-rate",
    "operating-margin-difference",
  ]) {
    const cells = within(
      screen.getByTestId(`business-plan-row-${metric}`),
    ).getAllByRole("cell");
    cells.forEach((cell) => expect(cell).toHaveTextContent("–"));
  }
  expect(
    screen.getByTestId("business-plan-cell-sales-achievement-rate-2026-01"),
  ).toHaveAttribute("title", "목표 없음");
});
it("shows registered zero and partial target notices distinctly", async () => {
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(
    result("INCOMPLETE"),
  );
  await draw();
  await userEvent.click(screen.getByRole("button", { name: "확인 필요 1건" }));
  expect(screen.getByRole("dialog", { name: "확인 필요 1건" })).toHaveTextContent(
    "일부 달의 목표가 없어 누계 달성률을 표시하지 않습니다. 12개월 목표를 올려 주세요.",
  );
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(
    result("ZERO_TARGETS"),
  );
  act(() => window.dispatchEvent(new Event(api.BUSINESS_PLAN_COMMITTED)));
  const warnings = await screen.findByRole("button", { name: "확인 필요 1건" });
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  await userEvent.click(warnings);
  expect(screen.getByRole("dialog", { name: "확인 필요 1건" })).toHaveTextContent(
    "12개월 목표가 모두 0입니다(목표 없음). 달성률은 표시하지 않습니다.",
  );
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
it("shows future targets but omits future bars and keeps only the existing FieldHelp for each source row", async () => {
  await draw();
  expect(
    screen.queryByTestId("business-plan-actual-bar-2026-12"),
  ).not.toBeInTheDocument();
  expect(
    screen.getByTestId("business-plan-row-sales-target-amt"),
  ).toHaveTextContent("99.001");
  for (const label of [
    "매출 목표",
    "매출 실적",
    "영업이익 목표",
    "영업이익 실적",
  ]) {
    const row = screen.getByRole("rowheader", { name: label });
    expect(
      within(row).getAllByRole("button", { name: `${label} 도움말` }),
    ).toHaveLength(1);
    expect(within(row).queryByText("ⓘ")).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(`${label} 원천 설명`),
    ).not.toBeInTheDocument();
  }
  await userEvent.click(
    screen.getByRole("button", { name: "매출 실적 도움말" }),
  );
  expect(screen.getByRole("tooltip")).toHaveTextContent("영업이익표");
  expect(screen.getAllByText("예정 포함").length).toBeGreaterThan(0);
});
it("emphasizes both rate rows and only explains missing rate cells without abbreviations", async () => {
  const data = result();
  Object.assign(data.months[0], {
    salesTargetAmt: "0",
    operatingProfitTargetAmt: "0",
    salesAchievementRate: null,
    operatingMarginDifference: null,
    salesUnderTarget: null,
    operatingMarginAdverse: null,
  });
  Object.assign(data.months[1], {
    salesActualAmt: null,
    operatingProfitActualAmt: null,
    salesAchievementRate: null,
    operatingMarginDifference: null,
    salesUnderTarget: null,
    operatingMarginAdverse: null,
  });
  Object.assign(data.months[2], {
    salesActualAmt: "0",
    salesAchievementRate: "0.0",
    operatingMarginDifference: null,
    operatingMarginAdverse: null,
  });
  Object.assign(data.months[3], {
    salesTargetAmt: null,
    operatingProfitTargetAmt: null,
    salesAchievementRate: null,
    operatingMarginDifference: null,
    salesUnderTarget: null,
    operatingMarginAdverse: null,
  });
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(data);
  await draw();
  for (const key of ["sales-achievement-rate", "operating-margin-difference"]) {
    expect(screen.getByTestId(`business-plan-row-${key}`)).toHaveClass(
      "business-plan-emphasis",
    );
    for (const month of ["01", "04"]) {
      const cell = screen.getByTestId(
        `business-plan-cell-${key}-2026-${month}`,
      );
      expect(cell).toHaveTextContent("–");
      expect(cell).toHaveAttribute("title", "목표 없음");
      expect(cell).not.toHaveClass("business-plan-adverse");
    }
    expect(
      screen.getByTestId(`business-plan-cell-${key}-2026-02`),
    ).toHaveAttribute("title", "실적 자료 미확인");
    expect(
      screen.getByTestId(`business-plan-cell-${key}-2026-12`),
    ).not.toHaveAttribute("title");
    expect(
      screen.getByTestId(`business-plan-cell-${key}-2026-05`),
    ).not.toHaveAttribute("title");
  }
  expect(
    screen.getByTestId(
      "business-plan-cell-operating-margin-difference-2026-03",
    ),
  ).toHaveAttribute("title", "매출 실적이 0이라 이익률을 비교하지 않습니다");
  expect(
    screen.getByTestId("business-plan-cell-sales-achievement-rate-2026-03"),
  ).not.toHaveAttribute("title");
  for (const cell of within(
    screen.getByRole("table", { name: "사업계획 월별 실적" }),
  ).getAllByRole("cell")) {
    if (cell.textContent !== "–") expect(cell).not.toHaveAttribute("title");
    expect(cell.getAttribute("title") ?? "").not.toMatch(/TK|MO|DP/);
  }
  expect(screen.queryByText(/실적은 영업이익표의 같은 달/)).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "화면 안내" }));
  const guide = screen.getByRole("dialog", { name: "사업계획 대비 실적 화면 안내" });
  expect(guide).toHaveTextContent(/영업이익표의 같은 달.*턴키는 청구한 달/);
  expect(guide).toHaveTextContent(/달성률은 실적을 목표로 나눈 뒤 100/);
  expect(guide).toHaveTextContent(/올해 12개월 매출·영업이익 목표를 PC에서 등록/);
  expect(guide).not.toHaveTextContent(/TK|MO|DP/);
});
it("keeps unrelated server notices when moving the unregistered notice to its banner", async () => {
  const data = result("UNREGISTERED");
  data.notices.push(
    "2026-07 실적 자료를 확인해 주세요.",
    "미마감 실적은 잠정입니다. 작업중 월의 원천 변경 시 달라질 수 있습니다.",
  );
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(data);
  await draw();
  await userEvent.click(screen.getByRole("button", { name: "확인 필요 1건" }));
  const dialog = screen.getByRole("dialog", { name: "확인 필요 1건" });
  expect(within(dialog).getByRole("heading", { name: "2026-07" })).toBeVisible();
  expect(dialog).toHaveTextContent("실적 자료를 확인해 주세요.");
  expect(dialog).not.toHaveTextContent("사업계획 목표가 아직 없습니다");
});
it("renders server negative summary rates and exact actual/target amounts unchanged", async () => {
  const data = result();
  Object.assign(data.summary[1], {
    actualAmt: "-36489146",
    targetAmt: "270000000",
    achievementRate: "-13.5",
    underTarget: true,
  });
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(data);
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("단위"), "원");
  const card = screen.getByTestId(
    "business-plan-summary-cumulative-operating-profit",
  );
  expect(card.querySelector("strong")).toHaveTextContent("−13.5%");
  expect(card.querySelector("strong")).toHaveClass("business-plan-adverse");
  expect(card).toHaveTextContent("실적 −36,489,146 / 목표 270,000,000");
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
it("removes the title template action and keeps past-year uploads read-only", async () => {
  const data = result();
  data.context.year = 2025;
  data.context.month = "2025-12";
  vi.mocked(api.getBusinessPlanPerformance).mockResolvedValue(data);
  render(<BusinessPlanPerformanceScreen />);
  period("2025", "2025-12");
  await screen.findByRole("table");
  expect(
    screen.queryByRole("button", { name: "양식 내려받기" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByText(/양식:.*업로드:/)).not.toBeInTheDocument();
  expect(screen.getByTestId("business-plan-upload")).toBeDisabled();
  expect(screen.queryByLabelText("파일 선택")).not.toBeInTheDocument();
});
it("opens the current-year upload from the current-year view", async () => {
  await draw();
  const actions = screen.getByTestId("business-plan-upload").parentElement!;
  expect(within(actions).getByRole("combobox", { name: "단위" })).toBeVisible();
  await userEvent.click(screen.getByTestId("business-plan-upload"));
  expect(
    await screen.findByTestId("business-plan-import-year"),
  ).toHaveTextContent("대상연도 2026");
  expect(screen.getByLabelText("파일 선택")).toBeEnabled();
  expect(screen.getByRole("button", { name: "양식 내려받기" })).toBeEnabled();
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
  expect(screen.getByRole("group", { name: "조회 기간 상태" })).toHaveTextContent("집계 기간 1~6월");
});
it("disables PC-only actions on narrow screens without hiding actuals", async () => {
  Object.defineProperty(window, "innerWidth", { value: 600 });
  await draw();
  expect(
    screen.queryByTestId("business-plan-template"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("business-plan-upload")).toBeDisabled();
  expect(
    screen.getByText("목표 업로드는 PC에서 이용해 주세요."),
  ).toBeInTheDocument();
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
