import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { CandidateSummaryPanel } from "./CandidateSummaryPanel";
import * as api from "./candidateSummaryApi";

vi.mock("./candidateSummaryApi", () => ({ getCandidateSummary: vi.fn() }));
const counts: api.CandidateSummary = {
  total: 1234,
  inProgress: 1200,
  interviewScheduled: 9,
  availableByNextMonthStart: 42,
  overdueContact: 3,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getCandidateSummary).mockResolvedValue(counts);
});
function metric(id: string) {
  return screen.getByTestId(`candidate-summary-${id}`);
}
it("renders all five API counts with global boundary wording, never list-only totals", async () => {
  render(<CandidateSummaryPanel />);
  await screen.findByText("전체 후보");
  expect(metric("total")).toHaveTextContent("1,234명");
  expect(metric("in-progress")).toHaveTextContent("진행 중1,200명");
  expect(metric("interview-scheduled")).toHaveTextContent("면접 예정9명");
  expect(metric("available-by-next-month-start")).toHaveTextContent(
    "다음 달 시작까지 가능42명",
  );
  expect(metric("overdue-contact")).toHaveTextContent("30일 넘게 미연락3명");
  expect(
    screen.getByText(/목록의 ‘다음 달까지’는 다음 달 말일/),
  ).toHaveTextContent("다음 달 1일");
  expect(metric("in-progress")).toHaveAttribute("title", "보류·계약 완료 제외");
  expect(metric("overdue-contact")).toHaveAttribute(
    "title",
    expect.stringContaining("정확히 30일은 제외"),
  );
  expect(api.getCandidateSummary).toHaveBeenCalledWith(expect.any(AbortSignal));
  expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
});
it("loading never appears as zero or empty candidate data", async () => {
  vi.mocked(api.getCandidateSummary).mockReturnValue(new Promise(() => {}));
  render(<CandidateSummaryPanel />);
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  expect(screen.getByRole("region", { name: "후보 요약" })).toHaveAttribute(
    "aria-busy",
    "true",
  );
  expect(screen.queryByText("등록된 후보가 없습니다.")).not.toBeInTheDocument();
  expect(screen.queryByText("전체 후보")).not.toBeInTheDocument();
});
it("an empty API result shows five real zeroes and no warning tone", async () => {
  vi.mocked(api.getCandidateSummary).mockResolvedValue({
    total: 0,
    inProgress: 0,
    interviewScheduled: 0,
    availableByNextMonthStart: 0,
    overdueContact: 0,
  });
  render(<CandidateSummaryPanel />);
  await screen.findByText("등록된 후보가 없습니다.");
  expect(screen.getAllByText("0")).toHaveLength(5);
  expect(metric("overdue-contact").querySelector("dd")).not.toHaveClass(
    "candidate-summary-overdue",
  );
  expect(metric("interview-scheduled").querySelector("dd")).not.toHaveClass(
    "candidate-summary-interview",
  );
});
it("positive overdue and interview indicators keep distinct design token classes", async () => {
  render(<CandidateSummaryPanel />);
  await screen.findByText("면접 예정");
  expect(metric("overdue-contact").querySelector("dd")).toHaveClass(
    "candidate-summary-overdue",
  );
  expect(metric("interview-scheduled").querySelector("dd")).toHaveClass(
    "candidate-summary-interview",
  );
  expect(screen.queryByText("등록된 후보가 없습니다.")).not.toBeInTheDocument();
});
it("failed request shows requestId and explicit read-only retry, not empty data or automatic retries", async () => {
  vi.mocked(api.getCandidateSummary).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "자료 처리 중 오류가 발생했습니다.",
      requestId: "candidate-trace",
    }),
  );
  render(<CandidateSummaryPanel />);
  const alert = await screen.findByRole("alert");
  expect(alert).toHaveTextContent("candidate-trace");
  expect(screen.queryByText("전체 후보")).not.toBeInTheDocument();
  expect(screen.queryByText("등록된 후보가 없습니다.")).not.toBeInTheDocument();
  expect(api.getCandidateSummary).toHaveBeenCalledTimes(1);
  await userEvent.click(
    within(alert).getByRole("button", { name: "다시 조회" }),
  );
  await screen.findByText("전체 후보");
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(api.getCandidateSummary).toHaveBeenCalledTimes(2);
});
it("successful candidate/contact commit refresh key re-reads all summary counts", async () => {
  const view = render(<CandidateSummaryPanel refreshKey={0} />);
  await screen.findByText("전체 후보");
  vi.mocked(api.getCandidateSummary).mockResolvedValue({
    ...counts,
    total: 1235,
    overdueContact: 0,
  });
  view.rerender(<CandidateSummaryPanel refreshKey={1} />);
  await waitFor(() => expect(metric("total")).toHaveTextContent("1,235명"));
  expect(metric("overdue-contact")).toHaveTextContent("0명");
  expect(metric("overdue-contact").querySelector("dd")).not.toHaveClass(
    "candidate-summary-overdue",
  );
  expect(api.getCandidateSummary).toHaveBeenCalledTimes(2);
});
it("a refresh failure cannot leave stale counts looking current", async () => {
  const view = render(<CandidateSummaryPanel />);
  await screen.findByText("전체 후보");
  vi.mocked(api.getCandidateSummary).mockRejectedValue(
    new Error("요약 조회 실패"),
  );
  view.rerender(<CandidateSummaryPanel refreshKey={1} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("요약 조회 실패");
  expect(screen.queryByText("전체 후보")).not.toBeInTheDocument();
  expect(api.getCandidateSummary).toHaveBeenCalledTimes(2);
});
it("inactive retained screen does not request and reactivation reads fresh data", async () => {
  const view = render(<CandidateSummaryPanel active={false} />);
  expect(api.getCandidateSummary).not.toHaveBeenCalled();
  expect(screen.queryByRole("region")).not.toBeInTheDocument();
  view.rerender(<CandidateSummaryPanel active />);
  await screen.findByText("전체 후보");
  view.rerender(<CandidateSummaryPanel active={false} />);
  view.rerender(<CandidateSummaryPanel active />);
  await screen.findByText("전체 후보");
  expect(api.getCandidateSummary).toHaveBeenCalledTimes(2);
});
it("late cancelled response cannot replace a newer summary", async () => {
  let resolve!: (value: api.CandidateSummary) => void;
  vi.mocked(api.getCandidateSummary).mockReturnValueOnce(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  const view = render(<CandidateSummaryPanel refreshKey={0} />);
  const firstSignal = vi.mocked(api.getCandidateSummary).mock.calls[0][0];
  view.rerender(<CandidateSummaryPanel refreshKey={1} />);
  await screen.findByText("전체 후보");
  expect(firstSignal?.aborted).toBe(true);
  await act(async () => resolve({ ...counts, total: 999 }));
  expect(metric("total")).toHaveTextContent("1,234명");
});
it("unmount aborts the in-flight read", () => {
  vi.mocked(api.getCandidateSummary).mockReturnValue(new Promise(() => {}));
  const view = render(<CandidateSummaryPanel />);
  const signal = vi.mocked(api.getCandidateSummary).mock.calls[0][0];
  view.unmount();
  expect(signal?.aborted).toBe(true);
});
