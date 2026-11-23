import { stubBusinessFetch } from "../../shared/testSupport/businessFixtures";
import {
  render,
  screen,
  waitFor,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  FinancialItemsEditor,
  FinancialItemsScreen,
} from "../financialitems/FinancialItemsScreen";
import { financialEditorFixture } from "../financialitems/financialFixtures";
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
const fetcher = vi.fn<typeof fetch>();
const response = (data: unknown) =>
  new Response(JSON.stringify({ success: true, meta: {}, data }));
beforeEach(() => {
  vi.resetAllMocks();
  stubBusinessFetch(fetcher);
  fetcher.mockImplementation(async (url) => {
    const path = String(url),
      data = financialEditorFixture();
    if (path.startsWith("/api/codes/")) return response({ items: [] });
    if (path.includes("year-status"))
      return response({ year: "2026", months: [] });
    if (path.startsWith("/api/financial-items")) {
      const month =
        new URL(path, "https://test.invalid").searchParams.get("month") ??
        data.month;
      return response({
        ...data,
        month,
        adjustmentTargetMonths: ["2026-06", "2026-05"],
      });
    }
    return response({ items: [] });
  });
});
afterEach(() => vi.unstubAllGlobals());
it("consumes the month-close application month and target month without saving or changing PERIOD", async () => {
  render(
    <MemoryRouter
      initialEntries={[
        {
          pathname: "/UI-10",
          state: {
            section: "adjustments",
            month: "2026-07",
            targetMonth: "2026-05",
          },
        },
      ]}
    >
      <FinancialItemsScreen />
    </MemoryRouter>,
  );
  await screen.findByTestId("financial-items-panel");
  expect(screen.getByTestId("financial-month-2026-07")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await userEvent.click(screen.getByTestId("financial-adjustments-add"));
  expect(
    screen.getByTestId("financial-adjustments-new-1-target-month"),
  ).toHaveValue("2026-05");
  expect(
    fetcher.mock.calls.some(
      ([url, options]) =>
        String(url).includes("preferences") || options?.method === "PUT",
    ),
  ).toBe(false);
});
it("uses one-time financial source month without saving destination PERIOD", async () => {
  render(<FinancialItemsEditor initialMonth="2026-02" />);
  await screen.findByTestId("financial-items-panel");
  await waitFor(() =>
    expect(
      fetcher.mock.calls.some(([url]) =>
        String(url).includes("/api/financial-items?month=2026-02"),
      ),
    ).toBe(true),
  );
  expect(screen.getByTestId("financial-month-2026-02")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  expect(
    fetcher.mock.calls.some(
      ([url, options]) =>
        String(url).includes("preferences") || options?.method === "PUT",
    ),
  ).toBe(false);
});

function EntryButtons() {
  const navigate = useNavigate();
  return (
    <>
      <button
        onClick={() =>
          navigate("/UI-13", {
            state: { fromMonthClose: true, month: "2026-05" },
          })
        }
      >
        다른 화면
      </button>
      <button
        onClick={() =>
          navigate("/UI-10", {
            state: { fromCashFlow: true, month: "2026-03" },
          })
        }
      >
        재무 다시
      </button>
      <FinancialItemsScreen />
    </>
  );
}
it("retains a cached financial tab when another screen receives month context, then applies reentry context", async () => {
  render(
    <MemoryRouter
      initialEntries={[
        { pathname: "/UI-10", state: { fromCashFlow: true, month: "2026-02" } },
      ]}
    >
      <EntryButtons />
    </MemoryRouter>,
  );
  await screen.findByTestId("financial-items-panel");
  expect(screen.getByTestId("financial-month-2026-02")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await userEvent.click(screen.getByRole("button", { name: "다른 화면" }));
  expect(screen.getByTestId("financial-month-2026-02")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await userEvent.click(screen.getByRole("button", { name: "재무 다시" }));
  await waitFor(() =>
    expect(screen.getByTestId("financial-month-2026-03")).toHaveAttribute(
      "aria-pressed",
      "true",
    ),
  );
  expect(
    fetcher.mock.calls.some(([url]) => String(url).includes("preferences")),
  ).toBe(false);
});
