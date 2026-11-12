import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { PayrollUploadDialog } from "./PayrollUploadDialog";
import * as api from "./payrollApi";
import * as issues from "../uploadsops/uploadIssuesApi";
vi.mock("./payrollApi");
vi.mock("../uploadsops/uploadIssuesApi");
const checked: api.PayrollValidation = {
  uploadId: 81,
  formType: "B1",
  screenId: "UI-02",
  status: "CHECKED",
  month: "2026-07",
  revision: "complete-month",
  changed: false,
  summary: {
    totalRows: 1,
    successRows: 1,
    warningRows: 0,
    errorRows: 0,
    fileWarningCount: 0,
    tableWarningCount: 0,
  },
  results: [],
  issueCount: 0,
  replacement: {
    newCount: 1,
    changedCount: 0,
    removedCount: 1,
    removedDevelopers: [
      {
        developerId: "D-REMOVED",
        managementNo: "M-REMOVED",
        developerName: "제거대상",
      },
    ],
  },
};
const saved: api.PayrollResult = {
  ...checked,
  status: "APPLIED",
  changed: true,
  results: [{ rowNumber: 2, developerId: "D-PAY" }],
};
const file = new File(["original file"], "payroll.xlsx");
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.createB1Validation).mockResolvedValue(checked);
  vi.mocked(api.createB1Apply).mockResolvedValue(saved);
  vi.mocked(api.getPayrollResult).mockResolvedValue(saved);
  vi.mocked(issues.getUploadIssues).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
});
function draw(
  onApplied = vi.fn().mockResolvedValue(undefined),
  onClose = vi.fn(),
) {
  render(
    <PayrollUploadDialog
      month="2026-07"
      readOnly={false}
      readOnlyMessage=""
      onClose={onClose}
      onApplied={onApplied}
    />,
  );
  return { onApplied, onClose };
}
async function check() {
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByTestId("payroll-replacement-preview");
  await waitFor(() =>
    expect(screen.getByTestId("payroll-full-replacement")).toBeEnabled(),
  );
}
async function confirm() {
  await check();
  await userEvent.click(screen.getByTestId("payroll-full-replacement"));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "전량 반영" })).toBeEnabled(),
  );
}
it("B1 독립 DTO를 어댑트하고 제거목록·확인 후 같은 File/id/month/revision으로 반영", async () => {
  const { onApplied } = draw();
  await check();
  expect(api.createB1Validation).toHaveBeenCalledWith(
    file,
    "2026-07",
    undefined,
  );
  expect(
    screen.getByText(/제거대상 · M-REMOVED · D-REMOVED/),
  ).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  await userEvent.click(screen.getByTestId("payroll-full-replacement"));
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  expect(await screen.findByTestId("payroll-result-2")).toHaveTextContent(
    "D-PAY",
  );
  expect(api.createB1Apply).toHaveBeenCalledWith(file, {
    month: "2026-07",
    uploadId: 81,
    expectedRevision: "complete-month",
    confirmFullReplacement: true,
  });
  expect(onApplied).toHaveBeenCalledOnce();
  expect(api.createB1Apply).toHaveBeenCalledOnce();
  expect(screen.queryByText("최종 프로젝트ID")).not.toBeInTheDocument();
  expect(screen.queryByText("예상 관리번호")).not.toBeInTheDocument();
});
it("ERR 한 건은 확인·반영 비활성, 기존월 유지 안내", async () => {
  vi.mocked(api.createB1Validation).mockResolvedValue({
    ...checked,
    summary: { ...checked.summary, successRows: 0, errorRows: 1 },
    issueCount: 1,
  });
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByTestId("payroll-replacement-preview");
  expect(screen.getByTestId("payroll-full-replacement")).toBeDisabled();
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(
    screen.getByText(/기존 월 급여는 변경되지 않았습니다/),
  ).toBeInTheDocument();
  expect(api.createB1Apply).not.toHaveBeenCalled();
});
it("파일 교체·재검사는 전량교체 확인 해제, 같은 파일 재검사는 uploadId 유지", async () => {
  draw();
  await confirm();
  await userEvent.click(screen.getByRole("button", { name: "다시 검사" }));
  await waitFor(() => expect(api.createB1Validation).toHaveBeenCalledTimes(2));
  expect(api.createB1Validation).toHaveBeenLastCalledWith(file, "2026-07", 81);
  expect(screen.getByTestId("payroll-full-replacement")).not.toBeChecked();
  await userEvent.upload(
    screen.getByLabelText("파일 선택"),
    new File(["new"], "replacement.xlsx"),
  );
  expect(
    screen.queryByTestId("payroll-replacement-preview"),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "검사" })).toBeEnabled();
});
it("409 STALE_WRITE는 확인해제·재검사 강제, 자동 재반영 없음", async () => {
  vi.mocked(api.createB1Apply).mockRejectedValueOnce(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "월revision 변경",
      requestId: "pay-stale",
    }),
  );
  draw();
  await confirm();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/월자료가 변경되었습니다/);
  expect(screen.getByTestId("payroll-full-replacement")).toBeDisabled();
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(api.createB1Apply).toHaveBeenCalledOnce();
  await userEvent.click(screen.getByRole("button", { name: "다시 검사" }));
  await waitFor(() =>
    expect(screen.getByTestId("payroll-full-replacement")).toBeEnabled(),
  );
  expect(screen.getByTestId("payroll-full-replacement")).not.toBeChecked();
});
it("422 독립 B1 진단으로 교체하고 원본파일·번호 유지", async () => {
  const replacement = {
    ...checked,
    summary: { ...checked.summary, successRows: 0, errorRows: 1 },
    issueCount: 1,
  };
  vi.mocked(api.createB1Apply).mockRejectedValue(
    new ApiError(422, {
      success: false,
      meta: {},
      code: "REVALIDATION_FAILED",
      message: "재검사 오류",
      requestId: "pay-422",
      details: replacement as never,
    }),
  );
  draw();
  await confirm();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/재검사 오류/);
  expect(screen.getByText("오류 1건")).toHaveClass("b7-bad");
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(screen.getByText("payroll.xlsx")).toBeInTheDocument();
  expect(api.createB1Apply).toHaveBeenCalledOnce();
});
it("반영 뒤 재조회 실패는 저장 반복 없이 조회만 재시도", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("read failed"))
    .mockResolvedValue(undefined);
  draw(refresh);
  await confirm();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.createB1Apply).toHaveBeenCalledOnce();
});
it("응답유실은 결과 재조회로 최초 APPLIED 복구, POST 자동 반복 없음", async () => {
  vi.mocked(api.createB1Apply).mockRejectedValue(new Error("응답 유실"));
  draw();
  await confirm();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/응답 유실 파일/);
  await userEvent.click(screen.getByRole("button", { name: "결과 재조회" }));
  await screen.findByTestId("payroll-result-2");
  expect(api.getPayrollResult).toHaveBeenCalledWith(81);
  expect(api.createB1Apply).toHaveBeenCalledOnce();
});
it("dirty 취소는 계속 작성으로 보존하고 명시 나가기만 닫는다", async () => {
  const { onClose } = draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByTestId("payroll-replacement-preview")).toBeInTheDocument();
  expect(onClose).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(onClose).toHaveBeenCalledOnce();
  expect(api.createB1Apply).not.toHaveBeenCalled();
});
it("10MiB 초과는 검사 요청 없이 한국어 오류", async () => {
  draw();
  fireEvent.change(screen.getByLabelText("파일 선택"), {
    target: { files: [new File([new Uint8Array(10485761)], "large.xlsx")] },
  });
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  expect(screen.getByRole("alert")).toHaveTextContent("10MiB");
  expect(api.createB1Validation).not.toHaveBeenCalled();
});
it("다른 확장자는 파일 단계에서 거부", async () => {
  draw();
  fireEvent.change(screen.getByLabelText("파일 선택"), {
    target: { files: [new File(["csv"], "payroll.csv")] },
  });
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  expect(api.createB1Validation).not.toHaveBeenCalled();
});
