import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { SalesPipelinePanel } from "./SalesPipelineScreen";
import * as api from "./salesCommandsApi";
vi.mock("./salesCommandsApi");
vi.mock("../shared/sharedApi", () => ({
  getPeriodContext: async () => ({ currentDate: "2026-11-15" }),
}));
vi.mock("../salesopportunities/SalesOpportunityForm", () => ({
  SalesOpportunityForm: ({
    onClose,
    detail,
  }: {
    onClose(): void;
    detail?: { name: string };
  }) => (
    <section>
      <span>{detail ? detail.name : "등록 초안"}</span>
      <input aria-label="기회명 초안" />
      <button data-testid="opportunity-form-back" onClick={onClose}>
        목록
      </button>
    </section>
  ),
}));
beforeEach(() => {
  vi.mocked(api.getSalesOpportunities).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
    stageTotals: [],
    summary: {
      opportunityCount: 0,
      staleCount: 0,
      amount: "0",
      weightedAmount: "0",
      weightedPeople: "0",
    },
  });
});
it("등록 화면에서는 파이프라인 탭을 숨기고 목록 복귀 시 복원한다", async () => {
  render(
    <MemoryRouter>
      <SalesPipelinePanel />
    </MemoryRouter>,
  );
  await waitFor(() => expect(screen.getAllByText("기회 없음")).toHaveLength(4));
  const opener = screen.getByTestId("pipeline-create-button");
  await userEvent.click(opener);
  expect(screen.queryByRole("dialog", { name: "영업 기회 입력" })).not.toBeInTheDocument();
  const draft = screen.getByRole("textbox", { name: "기회명 초안" });
  await userEvent.type(draft, "저장 전 기회");
  expect(screen.queryByRole("tablist", { name: "영업 파이프라인 보기" })).not.toBeInTheDocument();
  expect(draft).toBeVisible();
  expect(draft).toHaveValue("저장 전 기회");
  await userEvent.click(screen.getByTestId("opportunity-form-back"));
  expect(screen.getByRole("tablist", { name: "영업 파이프라인 보기" })).toBeVisible();
  expect(screen.getByTestId("pipeline-column-find")).toBeInTheDocument();
  await waitFor(() => expect(screen.getByTestId("pipeline-create-button")).toHaveFocus());
});

it("종료 목록은 변경된 조건을 바로 적용하고 한 곳에서 초기화한다", async () => {
  render(<MemoryRouter><SalesPipelinePanel /></MemoryRouter>);
  await userEvent.click(screen.getByTestId("pipeline-closed-tab"));
  expect(screen.queryByText("진행 기회", { exact: true })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "초기화" })).not.toBeInTheDocument();
  await userEvent.selectOptions(screen.getByTestId("pipeline-result-select"), "LOSE");
  await userEvent.click(screen.getByTestId("pipeline-include-stopped-input"));
  await waitFor(() => expect(api.getSalesOpportunities).toHaveBeenLastCalledWith("LOSE", true, 1, undefined, 50));
  await screen.findByText("조건에 맞는 결과가 없습니다.");
  expect(screen.getAllByRole("button", { name: "초기화" })).toHaveLength(1);
  await userEvent.click(screen.getByTestId("pipeline-empty-reset"));
  expect(screen.getByTestId("pipeline-result-select")).toHaveValue("WIN");
  expect(screen.getByTestId("pipeline-include-stopped-input")).not.toBeChecked();
  await waitFor(() => expect(api.getSalesOpportunities).toHaveBeenLastCalledWith("WIN", false, 1, undefined, 50));
});

it("종료 목록의 전체 건수와 페이지 크기는 같은 서버 조건을 사용한다", async () => {
  const initial = await api.getSalesOpportunities("WIN");
  vi.mocked(api.getSalesOpportunities).mockImplementation(async (_status, _stopped, page = 1, _sort, size = 50) => ({ ...initial, total: 57, page, size }));
  render(<MemoryRouter><SalesPipelinePanel /></MemoryRouter>);
  await userEvent.click(screen.getByTestId("pipeline-closed-tab"));
  await screen.findByText("총 57건");
  expect(screen.getByTestId("pipeline-page-size")).toHaveValue("50");
  for (const size of [20, 100, 50]) {
    await userEvent.selectOptions(screen.getByTestId("pipeline-page-size"), String(size));
    await waitFor(() => expect(api.getSalesOpportunities).toHaveBeenLastCalledWith("WIN", false, 1, undefined, size));
    expect(screen.getByText("총 57건")).toBeInTheDocument();
  }
});

it("진행 칸반은 탭 아래에 요약을 표시하고 하나의 재조회만 제공한다", async () => {
  render(
    <MemoryRouter>
      <SalesPipelinePanel />
    </MemoryRouter>,
  );
  await waitFor(() => expect(screen.getAllByText("기회 없음")).toHaveLength(4));
  const tabs = screen.getByRole("tablist", { name: "영업 파이프라인 보기" });
  const panel = screen.getByRole("tabpanel", { name: "진행 중" });
  expect(tabs.compareDocumentPosition(panel) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(within(panel).getByText("진행 기회", { exact: true })).toBeVisible();
  for (const label of ["조회", "초기화", "조건 초기화"]) {
    expect(within(panel).queryByRole("button", { name: label })).not.toBeInTheDocument();
  }
  const calls = vi.mocked(api.getSalesOpportunities).mock.calls.length;
  await userEvent.click(within(panel).getByRole("button", { name: "재조회" }));
  expect(api.getSalesOpportunities).toHaveBeenCalledTimes(calls + 1);
});

it("종료 목록은 기본 결과일 내림차순을 표시하고 각 허용 열의 서버 정렬을 전환한다", async () => {
  vi.mocked(api.getSalesOpportunities).mockResolvedValue({
    items: [],
    total: 55,
    page: 1,
    size: 50,
    stageTotals: [],
    summary: {
      opportunityCount: 0,
      staleCount: 0,
      amount: "0",
      weightedAmount: "0",
      weightedPeople: "0",
    },
  });
  render(
    <MemoryRouter>
      <SalesPipelinePanel />
    </MemoryRouter>,
  );
  await userEvent.click(screen.getByTestId("pipeline-closed-tab"));
  await screen.findByRole("button", { name: "결과일 ↓" });
  await userEvent.click(screen.getByTestId("pipeline-next-page"));
  await waitFor(() =>
    expect(api.getSalesOpportunities).toHaveBeenLastCalledWith(
      "WIN",
      false,
      2,
      undefined,
      50,
    ),
  );
  for (const title of ["예상액 (원)", "결과일"]) {
    const sort = title === "결과일" ? "resultDate" : "amount";
    await userEvent.click(
      screen.getByRole("button", {
        name: new RegExp(title === "결과일" ? "^결과일" : "^예상액"),
      }),
    );
    await waitFor(() =>
      expect(api.getSalesOpportunities).toHaveBeenLastCalledWith(
        "WIN",
        false,
        1,
        { sort, direction: "ASC" },
        50,
      ),
    );
    await userEvent.click(screen.getByRole("button", { name: `${title} ↑` }));
    await waitFor(() =>
      expect(api.getSalesOpportunities).toHaveBeenLastCalledWith(
        "WIN",
        false,
        1,
        { sort, direction: "DESC" },
        50,
      ),
    );
  }
});
