import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import { SalesPartnerProfitScreen } from "./SalesPartnerProfitScreen";
import * as api from "./salesPartSettlementsApi";
vi.mock("./salesPartSettlementsApi");
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
vi.mock("../shared/ScreenPeriodBar", () => ({ ScreenPeriodBar: () => null }));
function standard(): api.SettlementStandard {
  return {
    leaderId: "leader",
    leaderName: "파트장",
    distributionRate: "40",
    salesExpenseAmount: "10",
    effectiveMonth: "2026-01",
    valid: true,
  };
}
function tabs(parts = ["one", "two"]): api.SettlementTabsResult {
  return {
    month: "2026-07",
    baseMonth: "2026-01",
    currentMonth: "2026-07",
    parts: parts.map((partId) => ({
      partId,
      partName: partId,
      eligible: true,
      historyOnly: false,
      status: "WORK",
      standard: standard(),
      issues: [],
    })),
    eligibleCount: parts.length,
    confirmedCount: 0,
    unconfirmedCount: parts.length,
    issues: [],
  };
}
function response(partId = "one"): api.SettlementResult {
  const totals: api.SettlementTotals = {
    personCount: 61,
    deficitPersonCount: 1,
    mm: "30.50",
    unitPrice: "10000",
    payAmount: "12000",
    salesExpenseAmount: "610",
    retirementAmount: "1000",
    costAmount: "13610",
    profitAmount: "-3610",
    personProfitShareAmount: "-1444",
    cardAmount: "5",
    partProfitAmount: "-1449",
  };
  return {
    partId,
    month: "2026-07",
    status: "WORK",
    standard: standard(),
    cardAmount: "5",
    revision: "revision",
    confirmedBy: null,
    confirmedByName: null,
    confirmedAt: null,
    lastCancellationReason: null,
    readOnly: false,
    readOnlyReasons: [],
    canSaveCard: true,
    canConfirm: true,
    canCancel: false,
    summary: totals,
    totals,
    items: [
      {
        developerId: "actual",
        name: "실제 인력",
        managementNo: "001",
        employeeType: "FT",
        projectNames: ["쉼표, 프로젝트", "둘째 프로젝트"],
        legacyProjectText: null,
        mm: "0.50",
        unitPrice: "100",
        payAmount: "0",
        salesExpenseAmount: "10",
        retirementAmount: "0",
        costAmount: "10",
        profitAmount: "90",
        profitShareAmount: "36",
        remark: "월 급여 없음(시작 달 급여 0)",
        basis: {
          paySource: "START_MONTH_MISSING_ZERO",
          estimated: false,
          firstMonthMissingPay: true,
          firstInputDate: "2026-07-02",
          calendarDays: 31,
          payableDays: 30,
          basePayAmount: null,
          distributionRate: "40",
          tkSources: ["project: PLAN"],
          warnings: [],
        },
        issues: [],
      },
    ],
    pagination: { page: 1, size: 50, total: 61 },
    issues: [],
  };
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getSalesPartSettlements).mockResolvedValue(tabs());
  vi.mocked(api.getSalesPartSettlement).mockImplementation(async (id) =>
    response(id),
  );
});
function mount(props: Parameters<typeof SalesPartnerProfitScreen>[0] = {}) {
  return render(
    <MemoryRouter>
      <SalesPartnerProfitScreen {...props} />
    </MemoryRouter>,
  );
}
it("renders server whole-population summary, exact columns, original project names and first-month warning", async () => {
  const person = vi.fn();
  mount({ onPerson: person });
  await screen.findByText("61명");
  expect(screen.getByText("쉼표, 프로젝트 외 1건")).toHaveAttribute(
    "title",
    "쉼표, 프로젝트\n둘째 프로젝트",
  );
  expect(screen.getAllByText("⚠ 월 급여 없음(시작 달 급여 0)")[0]).toHaveClass(
    "settlement-negative",
  );
  expect(screen.getByRole("columnheader", { name: /비용 계/ })).toBeVisible();
  await userEvent.click(screen.getByRole("button", { name: /실제 인력/ }));
  expect(person).toHaveBeenCalledWith("actual");
  await userEvent.click(screen.getByText("계산 방법 펼침"));
  expect(screen.getByText(/파트 비용 = 급여/)).toBeVisible();
});
it("changes page size without deriving summary from current page and sends only approved sort", async () => {
  mount();
  await screen.findByText("61명");
  const summary = screen.getByTestId("settlement-summary").textContent;
  const size = screen.getByRole("combobox", { name: /한 화면 건수/ });
  await userEvent.selectOptions(size, "20");
  await waitFor(() =>
    expect(api.getSalesPartSettlement).toHaveBeenLastCalledWith(
      "one",
      expect.objectContaining({ size: 20, page: 1 }),
      expect.any(AbortSignal),
    ),
  );
  await screen.findByText("61명");
  expect(screen.getByTestId("settlement-summary").textContent).toBe(summary);
  await userEvent.click(screen.getByRole("button", { name: /단가/ }));
  await waitFor(() =>
    expect(api.getSalesPartSettlement).toHaveBeenLastCalledWith(
      "one",
      expect.objectContaining({ sort: "unitPrice" }),
      expect.any(AbortSignal),
    ),
  );
});
it("page, size and sorting wait for the same draft navigation guard", async () => {
  const guard = vi.fn();
  mount({ guardNavigation: guard });
  await screen.findByText("61명");
  const initial = vi.mocked(api.getSalesPartSettlement).mock.calls.length;
  await userEvent.click(screen.getByTestId("settlement-table-next-page"));
  expect(guard).toHaveBeenCalledOnce();
  expect(api.getSalesPartSettlement).toHaveBeenCalledTimes(initial);
  guard.mockClear();
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: /한 화면 건수/ }),
    "20",
  );
  expect(guard).toHaveBeenCalledOnce();
  expect(api.getSalesPartSettlement).toHaveBeenCalledTimes(initial);
  act(() => guard.mock.calls[0][0]());
  await waitFor(() =>
    expect(api.getSalesPartSettlement).toHaveBeenLastCalledWith(
      "one",
      expect.objectContaining({ size: 20 }),
      expect.any(AbortSignal),
    ),
  );
  await screen.findByText("61명");
  guard.mockClear();
  await userEvent.click(screen.getByRole("button", { name: /단가/ }));
  expect(guard).toHaveBeenCalledOnce();
  act(() => guard.mock.calls[0][0]());
  await waitFor(() =>
    expect(api.getSalesPartSettlement).toHaveBeenLastCalledWith(
      "one",
      expect.objectContaining({ sort: "unitPrice" }),
      expect.any(AbortSignal),
    ),
  );
});
it("opens the selected PART_ID standard rather than sending a standard mutation", async () => {
  const standard = vi.fn();
  mount({ onPartStandard: standard });
  await screen.findByText("61명");
  await userEvent.click(screen.getByRole("button", { name: "파트 기준 확인" }));
  expect(standard).toHaveBeenCalledWith("one");
});
it("retains eligible selection on month change and falls back to first eligible when stopped", async () => {
  mount();
  await screen.findByText("61명");
  await userEvent.click(screen.getByRole("tab", { name: "two" }));
  await waitFor(() =>
    expect(api.getSalesPartSettlement).toHaveBeenLastCalledWith(
      "two",
      expect.anything(),
      expect.any(AbortSignal),
    ),
  );
  act(() =>
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-19",
          value: { type: "YEAR_MONTH", value: "2026-08" },
        },
      }),
    ),
  );
  await waitFor(() =>
    expect(screen.getByRole("tab", { name: "two" })).toHaveAttribute(
      "aria-selected",
      "true",
    ),
  );
  vi.mocked(api.getSalesPartSettlements).mockResolvedValue({
    ...tabs(["one"]),
    month: "2026-09",
  });
  act(() =>
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-19",
          value: { type: "YEAR_MONTH", value: "2026-09" },
        },
      }),
    ),
  );
  await screen.findByText(/첫 유효 파트로 변경/);
  expect(screen.getByRole("tab", { name: "one" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
});
it("keeps history-only snapshot visible, readonly reasons and AUTO metadata", async () => {
  const history = tabs(["past"]);
  history.eligibleCount = 0;
  history.parts[0].eligible = false;
  history.parts[0].historyOnly = true;
  history.parts[0].status = "AUTO";
  vi.mocked(api.getSalesPartSettlements).mockResolvedValue(history);
  vi.mocked(api.getSalesPartSettlement).mockResolvedValue({
    ...response("past"),
    status: "AUTO",
    readOnly: true,
    readOnlyReasons: ["과거 연도는 조회 전용입니다."],
    canSaveCard: false,
    canConfirm: false,
    canCancel: false,
    confirmedByName: "확정자",
    confirmedAt: "2026-07-31T00:00:00+09:00",
  });
  mount();
  await screen.findByText("61명");
  expect(screen.getByText(/정산 대상 영업 파트가 없습니다/)).toBeVisible();
  expect(screen.getByRole("tab", { name: "past (확정 이력)" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(screen.getByText("마감자동")).toBeVisible();
  expect(screen.getByText("과거 연도는 조회 전용입니다.")).toBeVisible();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("distinguishes no-target from zero-person and null financial results", async () => {
  vi.mocked(api.getSalesPartSettlements).mockResolvedValue(tabs([]));
  mount();
  await screen.findByText(/정산 대상 영업 파트가 없습니다/);
  expect(api.getSalesPartSettlement).not.toHaveBeenCalled();
});
it("shows failed request with GET-only retry and clears stale detail", async () => {
  vi.mocked(api.getSalesPartSettlement).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  mount();
  await screen.findByRole("alert");
  expect(screen.queryByText("61명")).not.toBeInTheDocument();
  await userEvent.click(within(screen.getByRole("alert")).getByRole("button"));
  await screen.findByText("61명");
});
