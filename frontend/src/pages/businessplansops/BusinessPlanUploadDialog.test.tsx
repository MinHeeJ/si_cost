import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { BusinessPlanUploadDialog } from "./BusinessPlanUploadDialog";
import * as api from "./businessPlanPerformanceApi";
import * as issues from "../uploadsops/uploadIssuesApi";
vi.mock("./businessPlanPerformanceApi");
vi.mock("../uploadsops/uploadIssuesApi");
const summary = {
  totalRows: 12,
  successRows: 12,
  warningRows: 0,
  errorRows: 0,
  fileWarningCount: 0,
};
const validation: api.PlanValidation = {
  uploadId: "123",
  status: "CHECKED",
  year: 2026,
  summary,
  canApply: true,
  expectedRevision: "revision-owned",
  warnings: [],
  blankTargetMonths: [],
  requiresClosedMonthConfirmation: true,
  issues: { items: [], total: 0, page: 1, size: 50 },
  changes: [
    {
      month: "2026-01",
      rowNumber: 2,
      closed: true,
      changed: true,
      before: { salesTargetAmt: "10", operatingProfitTargetAmt: "-1" },
      after: { salesTargetAmt: "20", operatingProfitTargetAmt: "0" },
    },
  ],
};
const applied: api.PlanApplied = {
  uploadId: "123",
  status: "APPLIED",
  summary,
  alreadyApplied: false,
  message: "반영했습니다.",
  appliedAt: "2026-07-15T09:00:00+09:00",
  changedMonths: ["2026-01"],
  unchangedMonths: [],
  keptMonths: [],
  warnings: [],
  year: 2026,
  mode: "ALL",
};
const file = new File(["test workbook transport"], "targets.xlsx");
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.validateBusinessPlan).mockResolvedValue(validation);
  vi.mocked(api.applyBusinessPlan).mockResolvedValue(applied);
});
function draw(
  onApplied = vi.fn().mockResolvedValue(undefined),
  onClose = vi.fn(),
) {
  render(
    <BusinessPlanUploadDialog
      year={2026}
      onApplied={onApplied}
      onClose={onClose}
    />,
  );
  return { onApplied, onClose };
}
async function check() {
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText(/마감한 달 목표가 바뀝니다/);
}
it("shows closed before/after and requires explicit ALL choice before apply", async () => {
  const { onApplied } = draw();
  await check();
  expect(screen.getByRole("button", { name: "전체 변경" })).toBeDisabled();
  expect(
    screen.getByTestId("business-plan-target-change-2026-01"),
  ).toHaveTextContent("10 → 20");
  expect(screen.getByTestId("business-plan-mode-all")).not.toBeChecked();
  await userEvent.click(screen.getByTestId("business-plan-mode-all"));
  await userEvent.click(screen.getByRole("button", { name: "전체 변경" }));
  await screen.findByTestId("business-plan-upload-applied");
  expect(api.applyBusinessPlan).toHaveBeenCalledWith(file, validation, "ALL");
  expect(onApplied).toHaveBeenCalledOnce();
});
it("passes KEEP_CLOSED only after user choice", async () => {
  draw();
  await check();
  await userEvent.click(screen.getByTestId("business-plan-mode-keep"));
  await userEvent.click(
    screen.getByRole("button", { name: "마감한 달은 두기" }),
  );
  await screen.findByTestId("business-plan-upload-applied");
  expect(api.applyBusinessPlan).toHaveBeenCalledWith(
    file,
    validation,
    "KEEP_CLOSED",
  );
});
it("cancel does not apply and retains dirty-close confirmation", async () => {
  const { onClose } = draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(api.applyBusinessPlan).not.toHaveBeenCalled();
  expect(onClose).toHaveBeenCalledOnce();
});
it("validation error prevents apply even after mode selection", async () => {
  vi.mocked(api.validateBusinessPlan).mockResolvedValue({
    ...validation,
    canApply: false,
    summary: { ...summary, successRows: 0, errorRows: 12 },
  });
  draw();
  await check();
  await userEvent.click(screen.getByTestId("business-plan-mode-all"));
  expect(screen.getByRole("button", { name: "전체 변경" })).toBeDisabled();
  expect(api.applyBusinessPlan).not.toHaveBeenCalled();
});
it("a changed close revision requires recheck instead of automatic apply", async () => {
  vi.mocked(api.applyBusinessPlan).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다시 검사해 주세요.",
      requestId: "owned-request",
    }),
  );
  draw();
  await check();
  await userEvent.click(screen.getByTestId("business-plan-mode-all"));
  await userEvent.click(screen.getByRole("button", { name: "전체 변경" }));
  await screen.findByText(/owned-request/);
  expect(screen.getByRole("button", { name: "전체 변경" })).toBeDisabled();
  expect(api.applyBusinessPlan).toHaveBeenCalledOnce();
});
it("saved-but-refresh-failed stays separate and never replays the write", async () => {
  draw(vi.fn().mockRejectedValue(new Error("refresh failed")));
  await check();
  await userEvent.click(screen.getByTestId("business-plan-mode-all"));
  await userEvent.click(screen.getByRole("button", { name: "전체 변경" }));
  await screen.findByText(/다시 불러오지 못했습니다/);
  expect(api.applyBusinessPlan).toHaveBeenCalledOnce();
  expect(
    screen.getByTestId("business-plan-upload-applied"),
  ).toBeInTheDocument();
});
it("uses current upload year for its own template", async () => {
  draw();
  await userEvent.click(screen.getByTestId("import-template-button"));
  await waitFor(() =>
    expect(api.downloadBusinessPlanTemplate).toHaveBeenCalledWith(2026),
  );
});
