import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { PayrollUploadDialog } from "../apiops/PayrollUploadDialog";
import * as api from "../apiops/payrollApi";
import * as issues from "../uploadsops/uploadIssuesApi";
vi.mock("../apiops/payrollApi");
vi.mock("../uploadsops/uploadIssuesApi");
const summary = {
  totalRows: 1,
  successRows: 1,
  warningRows: 0,
  errorRows: 0,
  fileWarningCount: 0,
  tableWarningCount: 0,
};
const checked: api.PayrollValidation = {
  uploadId: 19,
  formType: "B1",
  screenId: "UI-02",
  status: "CHECKED",
  month: "2026-06",
  revision: "month-revision",
  changed: false,
  summary,
  results: [],
  issueCount: 0,
  replacement: {
    newCount: 1,
    changedCount: 0,
    removedCount: 0,
    removedDevelopers: [],
  },
};
const receipt: api.PayrollResult = {
  ...checked,
  status: "APPLIED",
  changed: true,
  results: [{ rowNumber: 2, developerId: "D-receipt" }],
  revision: "immutable-first",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.createB1Validation).mockResolvedValue(checked);
  vi.mocked(api.getPayrollResult).mockResolvedValue(receipt);
  vi.mocked(issues.getUploadIssues).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
});
function draw(readOnly = false) {
  const onApplied = vi.fn().mockResolvedValue(undefined),
    onClose = vi.fn();
  render(
    <PayrollUploadDialog
      month="2026-06"
      readOnly={readOnly}
      readOnlyMessage="마감된 월은 조회만 할 수 있습니다."
      onClose={onClose}
      onApplied={onApplied}
    />,
  );
  return { onApplied, onClose };
}
it("APPLIED 재검사는 최초 영수증을 보여주고 급여를 다시 저장하지 않는다", async () => {
  vi.mocked(api.createB1Validation).mockResolvedValue({
    ...checked,
    ...receipt,
  });
  const { onApplied } = draw();
  await userEvent.upload(
    screen.getByLabelText("파일 선택"),
    new File(["original"], "salary.xlsx"),
  );
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  expect(await screen.findByTestId("payroll-result-2")).toHaveTextContent(
    "D-receipt",
  );
  expect(api.getPayrollResult).toHaveBeenCalledWith(19);
  expect(api.createB1Apply).not.toHaveBeenCalled();
  expect(onApplied).toHaveBeenCalledOnce();
  expect(
    screen.queryByTestId("payroll-full-replacement"),
  ).not.toBeInTheDocument();
});
it("마감월은 읽기전용 안내와 검사 비활성으로 기존 화면을 보존한다", () => {
  draw(true);
  expect(
    screen.getByText("마감된 월은 조회만 할 수 있습니다."),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("파일 선택")).toBeDisabled();
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
});
it("검사 취소는 브라우저 파일만 폐기하고 서버에 반영이나 삭제를 보내지 않는다", async () => {
  const { onClose } = draw();
  await userEvent.upload(
    screen.getByLabelText("파일 선택"),
    new File(["original"], "salary.xlsx"),
  );
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByTestId("payroll-replacement-preview");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(onClose).toHaveBeenCalledOnce();
  expect(api.createB1Apply).not.toHaveBeenCalled();
});
it("문제조회 실패를 빈문제로 속이지 않고 같은 uploadId로 다시 검사한다", async () => {
  vi.mocked(issues.getUploadIssues).mockRejectedValueOnce(
    new Error("문제 조회 실패"),
  );
  draw();
  const file = new File(["original"], "salary.xlsx");
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText(/문제 조회 실패/);
  expect(api.createB1Apply).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await waitFor(() =>
    expect(api.createB1Validation).toHaveBeenLastCalledWith(
      file,
      "2026-06",
      19,
    ),
  );
});
