import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { StaffingAssignmentsScreen } from "./StaffingAssignmentsScreen";
import * as downloads from "./assignmentDownloadsApi";
import * as api from "../assignmentsops/assignmentActionsApi";
import * as shared from "../shared/sharedApi";
import { saveDownload } from "../b7/b7Api";
vi.mock("./assignmentDownloadsApi");
vi.mock("../assignmentsops/assignmentActionsApi");
vi.mock("../shared/sharedApi");
vi.mock("../b7/b7Api", () => ({ saveDownload: vi.fn() }));
const context: shared.PeriodContext = {
  currentDate: "2026-07-01",
  currentMonth: "2026-07",
  cashBaseMonth: "2025-01",
  timezone: "Asia/Seoul",
  monthClose: { month: "2026-07", status: "작업중" },
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(shared.getPeriodContext).mockResolvedValue(context);
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(shared.getSharedCodes).mockResolvedValue({ items: [] });
  vi.mocked(api.projectOptions).mockResolvedValue({ items: [] });
  vi.mocked(api.getAssignments).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(downloads.downloadAssignments).mockResolvedValue({
    blob: new Blob(["xlsx"]),
    filename: "투입배정.xlsx",
  });
});
it("retains loading and real empty list without inventing assignments", async () => {
  render(<StaffingAssignmentsScreen />);
  expect(
    screen.getByText("배정 기간을 불러오는 중입니다."),
  ).toBeInTheDocument();
  await screen.findByText("조회된 투입 배정이 없습니다.");
  expect(screen.queryByText("AI 추천")).not.toBeInTheDocument();
  expect(screen.queryByText("개인경비")).not.toBeInTheDocument();
  expect(screen.queryByText("청구서 생성")).not.toBeInTheDocument();
});
it("download passes selected year and applied filters with exact decimal display unit", async () => {
  render(<StaffingAssignmentsScreen />);
  await screen.findByText("조회된 투입 배정이 없습니다.");
  fireEvent.change(screen.getByTestId("assignments-search"), {
    target: { value: "  Ab_%  " },
  });
  await userEvent.click(screen.getByTestId("assignments-query"));
  fireEvent.change(screen.getByTestId("assignments-unit"), {
    target: { value: "백만원" },
  });
  await userEvent.click(screen.getByTestId("assignment-download"));
  await waitFor(() =>
    expect(downloads.downloadAssignments).toHaveBeenCalledWith(
      expect.objectContaining({ year: 2026, q: "Ab_%" }),
      { id: "projectName", direction: "ASC" },
      "백만원",
    ),
  );
  expect(saveDownload).toHaveBeenCalledOnce();
});
it("historical read-only year still permits download but forbids registration and checks", async () => {
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({
    items: [
      {
        type: "PERIOD",
        key: "UI-12",
        value: { type: "YEAR", value: "2025" },
        stored: true,
        revision: "period-ui-12",
      },
    ],
  });
  render(<StaffingAssignmentsScreen />);
  await screen.findByText(/지난 해·읽기 전용/);
  expect(screen.getByTestId("assignment-new")).toBeDisabled();
  expect(
    screen.queryByTestId("assignments-all-selection"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("assignment-download")).not.toBeDisabled();
});
it("download failure retains list and retry never saves the business record", async () => {
  vi.mocked(downloads.downloadAssignments).mockRejectedValueOnce(
    new Error("다운로드 실패"),
  );
  render(<StaffingAssignmentsScreen />);
  await screen.findByText("조회된 투입 배정이 없습니다.");
  await userEvent.click(screen.getByTestId("assignment-download"));
  await screen.findByText("다운로드 실패");
  expect(api.createAssignment).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("assignment-download"));
  await screen.findByText("파일을 내려받았습니다.");
  expect(api.createAssignment).not.toHaveBeenCalled();
});
it("preserves dirty callback and upstream actions extension instead of replacing merged screens", async () => {
  const dirty = vi.fn();
  render(
    <StaffingAssignmentsScreen
      onDirtyChange={dirty}
      renderActions={(c) => <span>연결 연도 {c.year}</span>}
    />,
  );
  await screen.findByText("연결 연도 2026");
  await waitFor(() =>
    expect(screen.getByTestId("assignment-new")).not.toBeDisabled(),
  );
  await userEvent.click(screen.getByTestId("assignment-new"));
  await screen.findByTestId("assignment-create-form");
  expect(dirty).toHaveBeenCalledWith(true);
  expect(screen.getByTestId("assignment-download")).toBeInTheDocument();
});
