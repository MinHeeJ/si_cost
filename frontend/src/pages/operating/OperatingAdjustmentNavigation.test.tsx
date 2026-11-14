import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { FinancialItemsEditor } from "../financialitems/FinancialItemsScreen";
import { financialEditorFixture } from "../financialitems/financialFixtures";
import type { FinancialEditor } from "../financialitems/financialApi";
const fetcher = vi.fn<typeof fetch>();
let data: FinancialEditor;
const response = (value: unknown) =>
  new Response(JSON.stringify({ success: true, meta: {}, data: value }));
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("fetch", fetcher);
  data = financialEditorFixture();
  fetcher.mockImplementation(async (url) => {
    const path = String(url);
    if (path.startsWith("/api/codes/")) return response({ items: [] });
    if (path.startsWith("/api/financial-items/year-status"))
      return response({ year: "2026", months: [] });
    if (path === "/api/corporate-taxes/initialize")
      return response({ changed: false });
    if (path.startsWith("/api/financial-items")) {
      const month = new URL(path, "https://test.invalid").searchParams.get(
        "month",
      );
      return response(
        month ? { ...data, month, editable: true, closeStatus: "WORK" } : data,
      );
    }
    return response({ items: [] });
  });
});
afterEach(() => vi.unstubAllGlobals());
it("operating statement adjustment entry selects server writable month rather than closed display month", async () => {
  data = financialEditorFixture({
    month: "2026-06",
    editable: false,
    closeStatus: "CLOSE",
    firstEditableMonth: "2026-07",
  });
  render(<FinancialItemsEditor openAdjustments />);
  await waitFor(() =>
    expect(screen.getByLabelText("선택월")).toHaveValue("2026-07"),
  );
  expect(screen.getByTestId("financial-financial-panel")).not.toHaveAttribute(
    "hidden",
  );
  expect(
    fetcher.mock.calls.some(
      ([url, options]) =>
        String(url) === "/api/financial-items" && options?.method === "PUT",
    ),
  ).toBe(false);
});
it("ordinary financial entry keeps its existing month without adjustment redirect", async () => {
  data = financialEditorFixture({
    month: "2026-06",
    editable: false,
    closeStatus: "CLOSE",
    firstEditableMonth: "2026-07",
  });
  render(<FinancialItemsEditor />);
  await screen.findByTestId("financial-items-panel");
  expect(screen.getByLabelText("선택월")).toHaveValue("2026-06");
  expect(
    fetcher.mock.calls.some(
      ([url]) => String(url) === "/api/financial-items?month=2026-07",
    ),
  ).toBe(false);
});
