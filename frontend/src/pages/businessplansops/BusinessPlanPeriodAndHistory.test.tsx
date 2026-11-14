import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ScreenPeriodBar } from "../shared/ScreenPeriodBar";
import { FinancialHistoryPanel } from "../apiops/FinancialHistoryPanel";
import * as shared from "../shared/sharedApi";
import * as history from "../apiops/financialReadApi";
vi.mock("../shared/sharedApi");
vi.mock("../apiops/financialReadApi");
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(shared.getPeriodContext).mockResolvedValue({
    currentDate: "2026-07-15",
    currentMonth: "2026-07",
    timezone: "Asia/Seoul",
    cashBaseMonth: "2025-01",
    monthClose: { month: "2026-07", status: "WORK" },
  });
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(shared.getMonthClose).mockResolvedValue({ items: [] });
  vi.mocked(shared.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, stored: true, revision: "next" },
    }),
  );
  vi.mocked(history.getFinancialHistory).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
});
it("past-year entry selects December, allows explicit later month selection and clamps return to current month without touching other PERIOD rows", async () => {
  render(<ScreenPeriodBar screenId="UI-18" />);
  await screen.findByLabelText("누계 연도");
  await userEvent.selectOptions(screen.getByLabelText("누계 연도"), "2025");
  await waitFor(() =>
    expect(shared.putSharedPreference).toHaveBeenLastCalledWith(
      "PERIOD",
      "UI-18",
      { type: "YEAR_CUM", year: "2025", month: "2025-12" },
      null,
    ),
  );
  await waitFor(() => expect(screen.getByLabelText("누계 연도")).toBeEnabled());
  await userEvent.selectOptions(screen.getByLabelText("누계 연도"), "2026");
  await waitFor(() =>
    expect(shared.putSharedPreference).toHaveBeenLastCalledWith(
      "PERIOD",
      "UI-18",
      { type: "YEAR_CUM", year: "2026", month: "2026-07" },
      "next",
    ),
  );
  expect(shared.putSharedPreference).toHaveBeenCalledTimes(2);
});
it("business-plan history reuses common history presentation with exact scope/month and empty state", async () => {
  render(
    <FinancialHistoryPanel month="2026-07" initialScope="BUSINESS_PLAN" />,
  );
  await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
  await screen.findByText("해당 범위의 변경 이력이 없습니다.");
  expect(history.getFinancialHistory).toHaveBeenCalledWith(
    "2026-07",
    "BUSINESS_PLAN",
    1,
    50,
    expect.any(AbortSignal),
  );
});
