import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ExcelUploadDialog } from "./ExcelUploadDialog";
import { ApiError } from "../../shared/api";
import * as api from "./b7Api";
vi.mock("./b7Api");
const summary = {
  totalRows: 1,
  successRows: 0,
  warningRows: 1,
  errorRows: 0,
  fileWarningCount: 0,
};
const result: api.B7Validation = {
  uploadId: 17,
  status: "CHECKED",
  canApply: true,
  summary,
  issues: {
    items: [
      {
        sequence: 1,
        severity: "WARN",
        rowNumber: 2,
        columnName: "상호",
        inputValue: "검사명",
        reason: "같은 상호",
      },
    ],
    total: 1,
    page: 1,
    size: 50,
  },
};
const file = new File(["file contents"], "clients.xlsx");
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.createB7Validation).mockResolvedValue(result);
  vi.mocked(api.saveB7Apply).mockResolvedValue({
    uploadId: 17,
    status: "APPLIED",
    summary,
    appliedCount: 1,
    appliedAt: "2026-06-15 09:00",
  });
});
function draw(
  onApplied = vi.fn().mockResolvedValue(undefined),
  onClose = vi.fn(),
) {
  render(<ExcelUploadDialog onClose={onClose} onApplied={onApplied} />);
  return { onApplied, onClose };
}
async function selectAndCheck() {
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText("검사명");
}
it("파일 선택 전 검사를 막고 검사 뒤 경고를 표시한 후 같은 파일·ID로 한 번 반영한다", async () => {
  const { onApplied } = draw();
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  await selectAndCheck();
  expect(api.createB7Validation).toHaveBeenCalledWith(file);
  expect(screen.getByText("경고 1건")).toHaveClass("b7-warn");
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/1건 반영 완료/);
  expect(api.saveB7Apply).toHaveBeenCalledWith(file, 17);
  expect(onApplied).toHaveBeenCalledTimes(1);
  expect(
    screen.queryByRole("button", { name: "전량 반영" }),
  ).not.toBeInTheDocument();
});
it("오류가 있는 결과는 전량 반영을 비활성화한다", async () => {
  vi.mocked(api.createB7Validation).mockResolvedValue({
    ...result,
    canApply: false,
    summary: { ...summary, warningRows: 0, errorRows: 1 },
  });
  draw();
  await selectAndCheck();
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(api.saveB7Apply).not.toHaveBeenCalled();
});
it("422 재검사 결과로 문제를 교체하고 자동 재시도하지 않는다", async () => {
  vi.mocked(api.saveB7Apply).mockRejectedValue(
    new ApiError(422, {
      success: false,
      meta: {},
      code: "UPLOAD_VALIDATION_FAILED",
      message: "현재 중복입니다",
      requestId: "test",
      details: { ...result, canApply: false } as never,
    }),
  );
  draw();
  await selectAndCheck();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/현재 중복입니다/);
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(api.saveB7Apply).toHaveBeenCalledTimes(1);
});
it("검사 통신 실패는 파일을 보존하고 사용자가 다시 검사할 수 있다", async () => {
  vi.mocked(api.createB7Validation).mockRejectedValueOnce(
    new Error("연결 오류"),
  );
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText(/연결 오류/);
  expect(screen.getByText("clients.xlsx")).toBeInTheDocument();
  expect(api.createB7Validation).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText("검사명");
});
it("파일 변경은 이전 검사와 반영 가능 상태를 제거한다", async () => {
  draw();
  await selectAndCheck();
  await userEvent.upload(
    screen.getByLabelText("파일 선택"),
    new File(["changed"], "changed.xlsx"),
  );
  expect(screen.queryByText("검사명")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "검사" })).toBeEnabled();
});
it("검사 결과가 비어 있으면 공통 빈 목록을 표시한다", async () => {
  vi.mocked(api.createB7Validation).mockResolvedValue({
    ...result,
    issues: { ...result.issues, items: [], total: 0 },
  });
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  expect(await screen.findByText("검사 문제가 없습니다.")).toBeInTheDocument();
});
it("취소는 미저장 확인을 거치고 계속 작성은 검사 상태를 유지한다", async () => {
  const { onClose } = draw();
  await selectAndCheck();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(
    screen.getByText("저장하지 않은 내용이 있습니다."),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByText("검사명")).toBeInTheDocument();
  expect(onClose).not.toHaveBeenCalled();
});
it("저장 후 조회 실패는 저장 반복 대신 조회만 재시도한다", async () => {
  const refreshed = vi
    .fn()
    .mockRejectedValueOnce(new Error("read failed"))
    .mockResolvedValue(undefined);
  draw(refreshed);
  await selectAndCheck();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
  await waitFor(() => expect(refreshed).toHaveBeenCalledTimes(2));
  expect(api.saveB7Apply).toHaveBeenCalledTimes(1);
});
it("10MiB 초과 파일은 서버에 보내지 않는다", async () => {
  draw();
  const large = new File([new Uint8Array(10485761)], "large.xlsx");
  fireEvent.change(screen.getByLabelText("파일 선택"), {
    target: { files: [large] },
  });
  expect(screen.getByRole("alert")).toHaveTextContent("10MiB");
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  expect(api.createB7Validation).not.toHaveBeenCalled();
});
