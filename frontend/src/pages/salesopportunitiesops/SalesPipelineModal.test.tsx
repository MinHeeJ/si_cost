import { createPortal } from "react-dom";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { SalesPipelinePanel } from "./SalesPipelineScreen";
import * as api from "./salesCommandsApi";
vi.mock("./salesCommandsApi");
vi.mock("../shared/sharedApi", () => ({ getPeriodContext: async () => ({ currentDate: "2026-11-15" }) }));
vi.mock("../salesopportunities/SalesOpportunityForm", () => ({
  SalesOpportunityForm: ({ onClose, detail }: { onClose(): void; detail?: { name: string } }) => (
    <section>{createPortal(<button data-testid="picker-portal">개발자 선택창</button>, document.body)}<span>{detail ? detail.name : "등록 초안"}</span><button data-testid="opportunity-form-back" onClick={onClose}>목록</button></section>
  ),
}));
beforeEach(() => {
  vi.mocked(api.getSalesOpportunities).mockResolvedValue({ items: [], total: 0, page: 1, size: 50, stageTotals: [], summary: { opportunityCount: 0, staleCount: 0, amount: "0", weightedAmount: "0", weightedPeople: "0" } });
});
it("등록 모달 뒤에 칸반과 탭을 유지하고 Escape 취소 뒤 목록으로 복귀한다", async () => {
  render(<MemoryRouter><SalesPipelinePanel /></MemoryRouter>);
  await waitFor(() => expect(screen.getAllByText("기회 없음")).toHaveLength(4));
  const opener = screen.getByTestId("pipeline-create-button");
  await userEvent.click(opener);
  const dialog = screen.getByRole("dialog", { name: "영업 기회 입력" });
  expect(dialog).toHaveAttribute("aria-modal", "true");
  expect(within(dialog).getByText("등록 초안")).toBeInTheDocument();
  expect(screen.getByTestId("pipeline-column-find")).toBeInTheDocument();
  expect(screen.getByRole("tab", { name: "매출·인력 예측" })).toBeInTheDocument();
  await userEvent.click(screen.getByTestId("picker-portal"));
  await userEvent.keyboard("{Escape}");
  expect(dialog).toBeInTheDocument();
  dialog.focus();
  await userEvent.keyboard("{Escape}");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(opener).toHaveFocus();
});

it("진행 칸반 빈 상태 안에 초기화 동작을 제공한다", async () => {
  render(<MemoryRouter><SalesPipelinePanel /></MemoryRouter>);
  await waitFor(() => expect(screen.getAllByText("기회 없음")).toHaveLength(4));
  await userEvent.click(within(screen.getByTestId("pipeline-column-find")).getByRole("button", { name: "조건 초기화" }));
  expect(api.getSalesOpportunities).toHaveBeenCalled();
});

it("종료 목록은 기본 결과일 내림차순을 표시하고 각 허용 열의 서버 정렬을 전환한다", async () => {
  vi.mocked(api.getSalesOpportunities).mockResolvedValue({ items: [], total: 55, page: 1, size: 50, stageTotals: [], summary: { opportunityCount: 0, staleCount: 0, amount: "0", weightedAmount: "0", weightedPeople: "0" } });
  render(<MemoryRouter><SalesPipelinePanel /></MemoryRouter>);
  await userEvent.click(screen.getByTestId("pipeline-closed-tab"));
  await screen.findByRole("button", { name: "결과일 ↓" });
  await userEvent.click(screen.getByTestId("pipeline-next-button"));
  await waitFor(() => expect(api.getSalesOpportunities).toHaveBeenLastCalledWith("WIN", false, 2, undefined));
  for (const title of ["예상액 (천원)", "결과일"]) {
    const sort = title === "결과일" ? "resultDate" : "amount";
    await userEvent.click(screen.getByRole("button", { name: new RegExp(title === "결과일" ? "^결과일" : "^예상액") }));
    await waitFor(() => expect(api.getSalesOpportunities).toHaveBeenLastCalledWith("WIN", false, 1, { sort, direction: "ASC" }));
    await userEvent.click(screen.getByRole("button", { name: `${title} ↑` }));
    await waitFor(() => expect(api.getSalesOpportunities).toHaveBeenLastCalledWith("WIN", false, 1, { sort, direction: "DESC" }));
  }
});
