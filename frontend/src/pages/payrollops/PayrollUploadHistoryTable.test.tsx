import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { PayrollDialog } from "../apiops/PayrollDialog";
import * as api from "../apiops/payrollApi";
import * as shared from "../shared/sharedApi";
import { ApiError } from "../../shared/api";
vi.mock("../apiops/payrollApi");
vi.mock("../shared/sharedApi");
vi.mock("../uploadsops/uploadIssuesApi");
const summary = {
  totalRows: 7,
  successRows: 4,
  warningRows: 2,
  errorRows: 1,
  fileWarningCount: 1,
  tableWarningCount: 0,
};
beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  });
  vi.mocked(shared.getPeriodContext).mockResolvedValue({
    currentDate: "2026-07-15",
    currentMonth: "2026-07",
    timezone: "Asia/Seoul",
    cashBaseMonth: "2026-01",
    monthClose: { month: "2026-07", status: "작업중" },
  });
  vi.mocked(shared.getMonthClose).mockResolvedValue({ items: [] });
  vi.mocked(shared.getSharedCodes).mockResolvedValue({ items: [] });
  vi.mocked(api.getPayroll).mockResolvedValue({
    month: "2026-07",
    items: [],
    total: 0,
    page: 1,
    size: 50,
    revision: "stored",
  });
  vi.mocked(api.getPayrollUploads).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
});
async function history() {
  render(<PayrollDialog onClose={vi.fn()} />);
  await screen.findByText("선택월 급여 없음");
  await userEvent.click(screen.getByTestId("payroll-history-tab"));
}
it("기록 상태별 색과 원본 파일·처리자·건수를 표시하며 쓰기를 제공하지 않는다", async () => {
  vi.mocked(api.getPayrollUploads).mockResolvedValue({
    items: [
      {
        uploadId: 72,
        month: "2026-07",
        fileName: "두번째.xlsx",
        actor: "server-actor",
        registeredAt: "2026-07-15 09:00",
        status: "APPLIED",
        summary,
        appliedAt: "2026-07-15 09:01",
      },
      {
        uploadId: 71,
        month: "2026-07",
        fileName: "첫번째.xlsx",
        actor: "original-actor",
        registeredAt: "2026-07-15 09:00",
        status: "CHECKED",
        summary,
        appliedAt: null,
      },
    ],
    total: 2,
    page: 1,
    size: 50,
  });
  await history();
  await screen.findByText("두번째.xlsx");
  expect(screen.getByText("server-actor")).toBeInTheDocument();
  expect(screen.getByText("original-actor")).toBeInTheDocument();
  expect(screen.getByText("반영 완료")).toHaveClass("sicost-tone-ok");
  expect(screen.getByText("검사 완료")).toHaveClass("sicost-tone-info");
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(api.createB1Apply).not.toHaveBeenCalled();
});
it("페이지·크기·월 변경에 정확한 선택월 문맥과 page1을 보낸다", async () => {
  vi.mocked(api.getPayrollUploads).mockResolvedValue({
    items: [],
    total: 101,
    page: 1,
    size: 50,
  });
  await history();
  await waitFor(() =>
    expect(api.getPayrollUploads).toHaveBeenCalledWith("2026-07", 1, 50),
  );
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(api.getPayrollUploads).toHaveBeenLastCalledWith("2026-07", 2, 50),
  );
  await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
  await waitFor(() =>
    expect(api.getPayrollUploads).toHaveBeenLastCalledWith("2026-07", 1, 20),
  );
  fireEvent.change(screen.getByLabelText("연월"), {
    target: { value: "2026-06" },
  });
  await waitFor(() =>
    expect(api.getPayrollUploads).toHaveBeenLastCalledWith("2026-06", 1, 20),
  );
});
it("빈 기록은 선택월 안내로 표시한다", async () => {
  await history();
  expect(await screen.findByText("선택월 기록 없음")).toBeInTheDocument();
});
it("조회 중은 빈 결과로 보이지 않는다", async () => {
  vi.mocked(api.getPayrollUploads).mockImplementation(
    () => new Promise(() => {}),
  );
  await history();
  expect(await screen.findByText("불러오는 중입니다.")).toBeInTheDocument();
  expect(screen.queryByText("선택월 기록 없음")).not.toBeInTheDocument();
});
it("실패는 추적번호와 재조회로 복구하며 업로드를 다시 반영하지 않는다", async () => {
  vi.mocked(api.getPayrollUploads).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "기록 조회 실패",
      requestId: "history-request",
    }),
  );
  await history();
  expect(
    await screen.findByText(/문의 번호: history-request/),
  ).toBeInTheDocument();
  expect(screen.queryByText("선택월 기록 없음")).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("payroll-history-refresh"));
  expect(await screen.findByText("선택월 기록 없음")).toBeInTheDocument();
  expect(api.createB1Apply).not.toHaveBeenCalled();
});
