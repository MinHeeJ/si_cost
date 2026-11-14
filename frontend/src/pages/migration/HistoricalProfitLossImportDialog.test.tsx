import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import {
  HistoricalProfitLossImportActions,
  HistoricalProfitLossImportDialog,
} from "./HistoricalProfitLossImportDialog";
import { ApiError } from "../../shared/api";
import * as api from "./historicalProfitLossImportsApi";
import * as issues from "../uploadsops/uploadIssuesApi";
vi.mock("./historicalProfitLossImportsApi");
vi.mock("../uploadsops/uploadIssuesApi");
const checked: api.MigrationValidation = {
  uploadId: "19",
  status: "CHECKED",
  summary: {
    totalRows: 1,
    successRows: 1,
    warningRows: 0,
    errorRows: 0,
    fileWarningCount: 0,
  },
  issues: { items: [], total: 0, page: 1, size: 50 },
  expectedRevision: "range-revision",
  canApply: true,
  warnings: [],
  allowedFromMonth: "2079-01",
  allowedThroughMonth: "2080-06",
  changes: [
    {
      month: "2079-01",
      rowNumber: 2,
      changed: true,
      before: null,
      after: {
        salesAmt: "1000",
        salesCostAmt: "500",
        sgaTotAmt: "200",
        oprtPrftAmt: "300",
        grossPrftAmt: null,
        netPrftAmt: null,
      },
    },
  ],
};
const applied: api.MigrationApplied = {
  uploadId: "19",
  status: "APPLIED",
  summary: checked.summary,
  alreadyApplied: false,
  message: "이관 손익 반영이 완료되었습니다.",
  appliedAt: "2080-07-15T09:00:00+09:00",
  changedMonths: ["2079-01"],
  unchangedMonths: [],
  keptMonths: ["2079-02"],
  warnings: [],
};
const file = new File(["contents"], "history.xlsx");
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: false }));
  vi.mocked(api.createHistoricalProfitLossImportValidation).mockResolvedValue(
    checked,
  );
  vi.mocked(api.saveHistoricalProfitLossImport).mockResolvedValue(applied);
  vi.mocked(api.getHistoricalProfitLossUploadResult).mockResolvedValue({
    uploadId: "19",
    formType: "B11",
    screenId: "UI-08",
    year: null,
    targetMonth: null,
    status: "APPLIED",
    summary: checked.summary,
    appliedAt: applied.appliedAt,
    changedMonths: ["2079-01"],
    issues: checked.issues,
  });
});
function draw(
  props: Partial<
    React.ComponentProps<typeof HistoricalProfitLossImportDialog>
  > = {},
) {
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(
    <HistoricalProfitLossImportDialog
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
it("checks first then applies same file id and revision without year or mode and shows raw null details", async () => {
  const refresh = draw();
  await inspect();
  expect(api.saveHistoricalProfitLossImport).not.toHaveBeenCalled();
  expect(screen.getByTestId("migration-range")).toHaveTextContent(
    "2079-01 ~ 2080-06",
  );
  expect(screen.getByTestId("migration-month-2079-01")).toHaveTextContent(
    "1,000",
  );
  expect(screen.getByTestId("migration-month-2079-01")).toHaveTextContent("–");
  await userEvent.click(screen.getByRole("button", { name: "반영" }));
  await screen.findByTestId("migration-result");
  expect(api.saveHistoricalProfitLossImport).toHaveBeenCalledWith(
    file,
    "19",
    "range-revision",
  );
  expect(refresh).toHaveBeenCalledOnce();
  expect(screen.getByText("유지 월: 2079-02")).toBeInTheDocument();
});
it("ERR disables entire apply", async () => {
  vi.mocked(api.createHistoricalProfitLossImportValidation).mockResolvedValue({
    ...checked,
    canApply: false,
    changes: [],
    summary: { ...checked.summary, successRows: 0, errorRows: 1 },
  });
  draw();
  await inspect();
  expect(screen.getByRole("button", { name: "반영" })).toBeDisabled();
  expect(screen.getByText("오류 1건")).toHaveClass("b7-bad");
  expect(screen.getByText("표시할 변경월이 없습니다.")).toBeInTheDocument();
});
it("WARN permits applying but never corrects uploaded money", async () => {
  vi.mocked(api.createHistoricalProfitLossImportValidation).mockResolvedValue({
    ...checked,
    warnings: ["계산식과 입력값이 다릅니다."],
    summary: { ...checked.summary, successRows: 0, warningRows: 1 },
  });
  draw();
  await inspect();
  expect(screen.getByText("계산식과 입력값이 다릅니다.")).toHaveClass(
    "b7-warn",
  );
  expect(screen.getByRole("button", { name: "반영" })).toBeEnabled();
});
it("failed validation preserves selected file and shows retryable error", async () => {
  vi.mocked(api.createHistoricalProfitLossImportValidation).mockRejectedValue(
    new Error("필수 제목 오류"),
  );
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByRole("alert");
  expect(screen.getByText("history.xlsx")).toBeInTheDocument();
  expect(api.saveHistoricalProfitLossImport).not.toHaveBeenCalled();
});
it("stale revision requires reinspection and never retries writes", async () => {
  vi.mocked(api.saveHistoricalProfitLossImport).mockRejectedValue(
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
  await userEvent.click(screen.getByRole("button", { name: "반영" }));
  await screen.findByRole("alert");
  expect(screen.getByRole("button", { name: "반영" })).toBeDisabled();
  expect(api.saveHistoricalProfitLossImport).toHaveBeenCalledOnce();
});
it("latest 422 replaces diagnostics", async () => {
  const latest = {
    ...checked,
    canApply: false,
    warnings: ["신규 오류"],
    summary: { ...checked.summary, successRows: 0, errorRows: 1 },
  };
  vi.mocked(api.saveHistoricalProfitLossImport).mockRejectedValue(
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
  await userEvent.click(screen.getByRole("button", { name: "반영" }));
  await screen.findByText("신규 오류");
  expect(screen.getByRole("button", { name: "반영" })).toBeDisabled();
});
it("uncertain apply only recovers result and never fabricates kept months", async () => {
  vi.mocked(api.saveHistoricalProfitLossImport).mockRejectedValue(
    new Error("통신 실패"),
  );
  const refresh = draw();
  await inspect();
  await userEvent.click(screen.getByRole("button", { name: "반영" }));
  await screen.findByRole("button", { name: "결과 재조회" });
  expect(screen.getByRole("button", { name: "반영" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "결과 재조회" }));
  await screen.findByTestId("migration-result");
  expect(api.saveHistoricalProfitLossImport).toHaveBeenCalledOnce();
  expect(screen.queryByText(/유지 월:/)).not.toBeInTheDocument();
  expect(refresh).toHaveBeenCalledOnce();
});
it("saved refresh failure retries only read", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("조회 실패"))
    .mockResolvedValue(undefined);
  draw({ onApplied: refresh });
  await inspect();
  await userEvent.click(screen.getByRole("button", { name: "반영" }));
  await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.saveHistoricalProfitLossImport).toHaveBeenCalledOnce();
});
it("issue severity uses shared paged API", async () => {
  vi.mocked(issues.getUploadIssues).mockResolvedValue(checked.issues);
  draw();
  await inspect();
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "ERR");
  await waitFor(() =>
    expect(issues.getUploadIssues).toHaveBeenCalledWith("19", "ERR", 1, 50),
  );
});
it("changing file discards checked id", async () => {
  draw();
  await inspect();
  await userEvent.upload(
    screen.getByLabelText("파일 선택"),
    new File(["different"], "other.xlsx"),
  );
  expect(screen.queryByText(/업로드 번호/)).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "반영" }),
  ).not.toBeInTheDocument();
});
it("closing or cancellation never applies", async () => {
  const close = vi.fn();
  draw({ onClose: close });
  await inspect();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.saveHistoricalProfitLossImport).not.toHaveBeenCalled();
});
it("PC notice hides file input", () => {
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: true }));
  draw();
  expect(
    screen.getByText("엑셀 업로드는 PC에서 이용해 주세요."),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText("파일 선택")).not.toBeInTheDocument();
});
it("invalid extension never calls server", () => {
  draw();
  fireEvent.change(screen.getByLabelText("파일 선택"), {
    target: { files: [new File(["text"], "bad.csv")] },
  });
  expect(screen.getByRole("alert")).toHaveTextContent("XLSX");
  expect(api.createHistoricalProfitLossImportValidation).not.toHaveBeenCalled();
});
it("template uses no display-year parameter and download failure is visible", async () => {
  vi.mocked(api.getHistoricalProfitLossImportTemplate).mockRejectedValue(
    new Error("기록 실패"),
  );
  render(<HistoricalProfitLossImportActions onApplied={vi.fn()} />);
  await userEvent.click(
    screen.getByRole("button", { name: "이관 양식 내려받기" }),
  );
  await screen.findByRole("alert");
  expect(api.getHistoricalProfitLossImportTemplate).toHaveBeenCalledWith();
  expect(api.saveDownload).not.toHaveBeenCalled();
});
