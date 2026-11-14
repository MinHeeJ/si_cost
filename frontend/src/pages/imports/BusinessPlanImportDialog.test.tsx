import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import {
  BusinessPlanImportActions,
  BusinessPlanImportDialog,
} from "./BusinessPlanImportDialog";
import { ApiError } from "../../shared/api";
import * as api from "./businessPlanImportsApi";
import * as issues from "../uploadsops/uploadIssuesApi";
vi.mock("./businessPlanImportsApi");
vi.mock("../uploadsops/uploadIssuesApi");
const checked: api.BusinessPlanValidation = {
  uploadId: "999999999999",
  status: "CHECKED",
  year: 2080,
  summary: {
    totalRows: 12,
    successRows: 12,
    warningRows: 0,
    errorRows: 0,
    fileWarningCount: 0,
  },
  issues: { items: [], total: 0, page: 1, size: 50 },
  expectedRevision: "year-revision",
  canApply: true,
  requiresClosedMonthConfirmation: true,
  blankTargetMonths: [],
  warnings: [],
  changes: [
    {
      month: "2080-01",
      rowNumber: 2,
      closed: true,
      changed: true,
      before: { salesTargetAmt: "1000", operatingProfitTargetAmt: "-1" },
      after: { salesTargetAmt: "2000", operatingProfitTargetAmt: "-2" },
    },
  ],
};
const applied: api.BusinessPlanApplied = {
  uploadId: checked.uploadId,
  status: "APPLIED",
  summary: checked.summary,
  year: 2080,
  mode: "ALL",
  alreadyApplied: false,
  message: "목표 반영이 완료되었습니다.",
  appliedAt: "2080-07-15T09:00:00+09:00",
  changedMonths: ["2080-01"],
  unchangedMonths: ["2080-02"],
  keptMonths: [],
  warnings: [],
};
const file = new File(["contents"], "targets.xlsx");
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: false }));
  vi.mocked(api.createBusinessPlanImportValidation).mockResolvedValue(checked);
  vi.mocked(api.saveBusinessPlanImport).mockResolvedValue(applied);
  vi.mocked(api.getBusinessPlanUploadResult).mockResolvedValue({
    uploadId: checked.uploadId,
    formType: "B10",
    screenId: "UI-18",
    year: 2080,
    targetMonth: "2080-01",
    status: "APPLIED",
    summary: checked.summary,
    appliedAt: applied.appliedAt,
    changedMonths: ["2080-01"],
    issues: checked.issues,
  });
});
function draw(
  props: Partial<React.ComponentProps<typeof BusinessPlanImportDialog>> = {},
) {
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(
    <BusinessPlanImportDialog
      year={2080}
      currentYear={2080}
      onClose={vi.fn()}
      onApplied={refresh}
      {...props}
    />,
  );
  return refresh;
}
async function inspect() {
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText(/검사 완료/);
}
async function confirm(mode = "전체 변경") {
  await userEvent.click(screen.getByRole("button", { name: "반영" }));
  const dialog = await screen.findByRole("dialog", {
    name: "목표 업로드 · 마감한 달 목표가 바뀝니다",
  });
  await userEvent.click(within(dialog).getByRole("button", { name: mode }));
}
it("shows checked before/after values and only sends same file/year/id/revision after explicit ALL choice", async () => {
  const refresh = draw();
  await inspect();
  await userEvent.click(screen.getByRole("button", { name: "반영" }));
  const dialog = await screen.findByRole("dialog", {
    name: "목표 업로드 · 마감한 달 목표가 바뀝니다",
  });
  expect(within(dialog).getByText("1,000")).toBeInTheDocument();
  expect(within(dialog).getByText("2,000")).toBeInTheDocument();
  expect(api.saveBusinessPlanImport).not.toHaveBeenCalled();
  await userEvent.click(
    within(dialog).getByRole("button", { name: "전체 변경" }),
  );
  await screen.findByTestId("business-plan-import-result");
  expect(api.saveBusinessPlanImport).toHaveBeenCalledWith(
    file,
    2080,
    checked.uploadId,
    "year-revision",
    "ALL",
  );
  expect(refresh).toHaveBeenCalledOnce();
  expect(screen.getByText("동일 월: 2080-02")).toBeInTheDocument();
});
it("KEEP_CLOSED is explicit and never defaults to ALL", async () => {
  draw();
  await inspect();
  await confirm("마감한 달은 두기");
  await screen.findByTestId("business-plan-import-result");
  expect(api.saveBusinessPlanImport).toHaveBeenCalledWith(
    file,
    2080,
    checked.uploadId,
    "year-revision",
    "KEEP_CLOSED",
  );
});
it("cancelling closed-month confirmation keeps CHECKED and sends no apply", async () => {
  draw();
  await inspect();
  await userEvent.click(screen.getByRole("button", { name: "반영" }));
  const dialog = await screen.findByRole("dialog", {
    name: "목표 업로드 · 마감한 달 목표가 바뀝니다",
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "반영" })).toBeEnabled(),
  );
  expect(api.saveBusinessPlanImport).not.toHaveBeenCalled();
});
it("same closed values apply without an unnecessary confirmation", async () => {
  vi.mocked(api.createBusinessPlanImportValidation).mockResolvedValue({
    ...checked,
    requiresClosedMonthConfirmation: false,
  });
  draw();
  await inspect();
  await userEvent.click(screen.getByRole("button", { name: "반영" }));
  await screen.findByTestId("business-plan-import-result");
  expect(
    screen.queryByRole("dialog", {
      name: "목표 업로드 · 마감한 달 목표가 바뀝니다",
    }),
  ).not.toBeInTheDocument();
});
it("past year keeps template but disables uploads", async () => {
  draw({ year: 2079 });
  expect(screen.getByLabelText("파일 선택")).toBeDisabled();
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "양식 내려받기" })).toBeEnabled();
});
it("ERR prevents any partial apply", async () => {
  vi.mocked(api.createBusinessPlanImportValidation).mockResolvedValue({
    ...checked,
    canApply: false,
    summary: { ...checked.summary, successRows: 11, errorRows: 1 },
  });
  draw();
  await inspect();
  expect(screen.getByRole("button", { name: "반영" })).toBeDisabled();
  expect(screen.getByText("오류 1건")).toHaveClass("b7-bad");
});
it("blank target warning is displayed and permits applying", async () => {
  vi.mocked(api.createBusinessPlanImportValidation).mockResolvedValue({
    ...checked,
    warnings: ["목표가 빈 달: 3월, 4월"],
    blankTargetMonths: ["2080-03", "2080-04"],
    summary: { ...checked.summary, successRows: 10, warningRows: 2 },
  });
  draw();
  await inspect();
  expect(screen.getByText("목표가 빈 달: 3월, 4월")).toHaveClass("b7-warn");
  expect(screen.getByRole("button", { name: "반영" })).toBeEnabled();
});
it("file change removes checked id and does not reuse old confirmation", async () => {
  draw();
  await inspect();
  await userEvent.upload(
    screen.getByLabelText("파일 선택"),
    new File(["other"], "other.xlsx"),
  );
  expect(screen.queryByText(/업로드 번호/)).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "검사" })).toBeEnabled();
});
it("request failure preserves selected file and shows read-only retry state", async () => {
  vi.mocked(api.createBusinessPlanImportValidation).mockRejectedValue(
    new Error("필수 제목 오류"),
  );
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByRole("alert");
  expect(screen.getByText("targets.xlsx")).toBeInTheDocument();
  expect(api.createBusinessPlanImportValidation).toHaveBeenCalledOnce();
});
it("stale close state requires reinspection rather than automatic ALL", async () => {
  vi.mocked(api.saveBusinessPlanImport).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다시 검사",
      requestId: "stale",
    }),
  );
  draw();
  await inspect();
  await confirm("마감한 달은 두기");
  await screen.findByRole("alert");
  expect(screen.getByRole("button", { name: "반영" })).toBeDisabled();
  expect(api.saveBusinessPlanImport).toHaveBeenCalledOnce();
});
it("latest 422 diagnostics replace old checked result", async () => {
  const latest = {
    ...checked,
    canApply: false,
    summary: { ...checked.summary, successRows: 11, errorRows: 1 },
    warnings: ["새 검사 오류"],
  };
  vi.mocked(api.saveBusinessPlanImport).mockRejectedValue(
    new ApiError(422, {
      success: false,
      meta: {},
      code: "REVALIDATION_FAILED",
      message: "재검사",
      requestId: "invalid",
      details: { financial: { upload: latest } } as never,
    }),
  );
  draw();
  await inspect();
  await confirm();
  await screen.findByText("새 검사 오류");
  expect(screen.getByRole("button", { name: "반영" })).toBeDisabled();
});
it("lost response never auto replays and result recovery only displays audit-proven months", async () => {
  vi.mocked(api.saveBusinessPlanImport).mockRejectedValue(
    new Error("통신 실패"),
  );
  const refresh = draw();
  await inspect();
  await confirm();
  await screen.findByRole("button", { name: "결과 재조회" });
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "반영" })).toBeDisabled(),
  );
  await userEvent.click(screen.getByRole("button", { name: "결과 재조회" }));
  await screen.findByTestId("business-plan-import-result");
  expect(api.saveBusinessPlanImport).toHaveBeenCalledOnce();
  expect(screen.queryByText(/동일 월:/)).not.toBeInTheDocument();
  expect(refresh).toHaveBeenCalledOnce();
});
it("saved-but-refresh-failed retries only refresh, not apply", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("조회 실패"))
    .mockResolvedValue(undefined);
  draw({ onApplied: refresh });
  await inspect();
  await confirm();
  await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.saveBusinessPlanImport).toHaveBeenCalledOnce();
});
it("shared issues severity and pagination stay wired to string upload id", async () => {
  vi.mocked(issues.getUploadIssues).mockResolvedValue(checked.issues);
  draw();
  await inspect();
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "ERR");
  await waitFor(() =>
    expect(issues.getUploadIssues).toHaveBeenCalledWith(
      checked.uploadId,
      "ERR",
      1,
      50,
    ),
  );
});
it("PC-only notice prevents file entry", () => {
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: true }));
  draw();
  expect(
    screen.getByText("엑셀 업로드는 PC에서 이용해 주세요."),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText("파일 선택")).not.toBeInTheDocument();
});
it("invalid extension rejected before request", () => {
  draw();
  fireEvent.change(screen.getByLabelText("파일 선택"), {
    target: { files: [new File(["text"], "bad.csv")] },
  });
  expect(screen.getByRole("alert")).toHaveTextContent("XLSX");
  expect(api.createBusinessPlanImportValidation).not.toHaveBeenCalled();
});
it("selected-year template uses display year while old-year upload action remains disabled", async () => {
  vi.mocked(api.getBusinessPlanImportTemplate).mockRejectedValue(
    new Error("기록 실패"),
  );
  render(
    <BusinessPlanImportActions
      year={2079}
      currentYear={2080}
      onApplied={vi.fn()}
    />,
  );
  expect(screen.getByRole("button", { name: "목표 업로드" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "양식 내려받기" }));
  await screen.findByRole("alert");
  expect(api.getBusinessPlanImportTemplate).toHaveBeenCalledWith(2079);
  expect(api.saveDownload).not.toHaveBeenCalled();
});
