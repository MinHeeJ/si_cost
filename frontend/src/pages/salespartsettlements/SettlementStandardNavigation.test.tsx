import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, expect, it, vi } from "vitest";
import { SalesPartsScreen } from "../salesparts/SalesPartsScreen";
import * as api from "../salesparts/salesPartsApi";
vi.mock("../salesparts/salesPartsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../salesparts/salesPartsApi")>()),
  getSalesParts: vi.fn(),
  getSalesPart: vi.fn(),
}));
afterEach(() => {
  window.history.replaceState({}, "", "/");
  vi.clearAllMocks();
});
it("the UI-19 standard link opens the existing UI-15 selected-part detail without a standard write", async () => {
  const part = {
    id: "selected-part",
    name: "선택 파트",
    useYn: "Y" as const,
    currentMonth: "2026-07",
    currentStandard: null,
    scheduledChange: null,
    personCount: 0,
    projectCount: 0,
    revision: "revision",
  };
  vi.mocked(api.getSalesParts).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(api.getSalesPart).mockResolvedValue({
    part,
    standards: [],
    minMonth: "2026-01",
    revision: "revision",
  });
  window.history.replaceState({}, "", "/UI-15?partId=selected-part");
  render(
    <MemoryRouter>
      <SalesPartsScreen />
    </MemoryRouter>,
  );
  await screen.findByTestId("sales-part-detail-dialog");
  expect(api.getSalesPart).toHaveBeenCalledWith("selected-part");
});
