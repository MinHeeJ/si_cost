import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { SettlementActions } from "./SettlementActions";
import { downloadSettlement } from "./settlementCommandsApi";
import type { SettlementResult } from "../salespartsettlements/salesPartSettlementsApi";
vi.mock("./settlementCommandsApi", () => ({downloadSettlement: vi.fn()}));
vi.mock("../apiops/FinancialHistoryPanel", () => ({FinancialHistoryPanel: ({partId, month}: {partId: string; month: string}) => <p>정산 이력 {partId} {month}</p>}));
const query = {month: "2026-10", page: 2, size: 20 as const, sort: "profitAmount" as const, direction: "DESC" as const};
const data = {partId: "PART-11", month: "2026-11", revision: "latest"} as SettlementResult;
function mount(trend = false) { return render(<MemoryRouter><SettlementActions data={data} query={query} trend={trend} unit="백만원" /></MemoryRouter>); }
beforeEach(() => {vi.resetAllMocks();vi.mocked(downloadSettlement).mockResolvedValue(undefined)});
it.each([false, true])("offers the required download for selected view (trend=%s) with loaded month and selected unit", async trend => {
  mount(trend);
  fireEvent.click(screen.getByRole("button", {name: "엑셀 다운로드"}));
  await waitFor(() => expect(downloadSettlement).toHaveBeenCalledWith("PART-11", {...query, month: "2026-11"}, trend ? "TREND" : "MONTH", "백만원"));
});
it("keeps the screen usable on failed download and opens only the selected part-month history", async () => {
  vi.mocked(downloadSettlement).mockRejectedValue(new Error("파일 생성 실패"));
  mount();fireEvent.click(screen.getByRole("button", {name: "엑셀 다운로드"}));
  await screen.findByText("파일 생성 실패");
  expect(screen.getByRole("button", {name: "엑셀 다운로드"})).toBeEnabled();
  fireEvent.click(screen.getByRole("button", {name: "변경 이력"}));
  expect(screen.getByTestId("settlement-history-drawer")).toHaveTextContent("정산 이력 PART-11 2026-11");
  fireEvent.click(screen.getByRole("button", {name: "닫기"}));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
