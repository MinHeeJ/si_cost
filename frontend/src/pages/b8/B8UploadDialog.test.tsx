import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { B8UploadActions, B8UploadDialog } from "./B8UploadDialog";
import { ApiError } from "../../shared/api";
import * as api from "./b8Api";
import * as issues from "../uploadsops/uploadIssuesApi";
vi.mock("./b8Api");
vi.mock("../uploadsops/uploadIssuesApi");
const summary = {
  totalRows: 1,
  successRows: 1,
  warningRows: 0,
  errorRows: 0,
  fileWarningCount: 0,
};
const result: api.B8Validation = {
  uploadId: 17,
  summary,
  canApply: true,
  issues: { items: [], total: 0, page: 1, size: 50 },
  expectedNumbers: [{ rowNumber: 2, managementNo: "K-0001" }],
};
const file = new File(["workbook contents"], "developers.xlsx");
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: false }));
  vi.mocked(api.createB8Validation).mockResolvedValue(result);
  vi.mocked(api.saveB8Apply).mockResolvedValue({
    summary,
    finalNumbers: [{ rowNumber: 2, managementNo: "K-0031", id: "DEV-000001" }],
  });
});
function draw(
  onApplied = vi.fn().mockResolvedValue(undefined),
  onClose = vi.fn(),
) {
  render(<B8UploadDialog onApplied={onApplied} onClose={onClose} />);
  return { onApplied, onClose };
}
async function check() {
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText("K-0001");
}
it("원래 행과 예상·미예약 번호를 표시하고 같은 File/id로 반영한 최종번호를 보여준다", async () => {
  const { onApplied } = draw();
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  await check();
  expect(
    screen.getByText("새로 붙을 관리번호 · 예상·미예약"),
  ).toBeInTheDocument();
  expect(screen.getByText("검사 문제가 없습니다.")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  expect(await screen.findByText("K-0031")).toBeInTheDocument();
  expect(screen.getByText("DEV-000001")).toBeInTheDocument();
  expect(api.saveB8Apply).toHaveBeenCalledWith(file, 17);
  expect(onApplied).toHaveBeenCalledTimes(1);
  expect(screen.queryByText("K-0001")).not.toBeInTheDocument();
});
it("검사 중 입력·중복 버튼을 잠그고 완료 뒤 상태를 복구한다", async () => {
  let resolve!: (r: api.B8Validation) => void;
  vi.mocked(api.createB8Validation).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  expect(screen.getByLabelText("파일 선택")).toBeDisabled();
  expect(screen.getByRole("button", { name: "처리 중…" })).toBeDisabled();
  resolve(result);
  await screen.findByText("K-0001");
  expect(api.createB8Validation).toHaveBeenCalledTimes(1);
});
it("오류0이 아닌 행은 반영 불가이며 경고와 파일경고는 별도 카드로 보인다", async () => {
  vi.mocked(api.createB8Validation).mockResolvedValue({
    ...result,
    canApply: false,
    summary: { ...summary, successRows: 0, errorRows: 1, fileWarningCount: 2 },
  });
  draw();
  await check();
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(screen.getByText("오류 1건")).toHaveClass("b7-bad");
  expect(screen.getByText("파일 경고 2건")).toBeInTheDocument();
  expect(api.saveB8Apply).not.toHaveBeenCalled();
});
it("한국어 파일오류와 requestId를 표시하고 파일입력에 포커스하며 자동 재시도하지 않는다", async () => {
  vi.mocked(api.createB8Validation).mockRejectedValueOnce(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_FILE_HEADER",
      message: "제목을 확인해 주세요.",
      requestId: "request-check",
      field: "file",
    }),
  );
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText(/request-check/);
  expect(screen.getByLabelText("파일 선택")).toHaveFocus();
  expect(screen.getByText("developers.xlsx")).toBeInTheDocument();
  expect(api.createB8Validation).toHaveBeenCalledTimes(1);
});
it("파일 교체는 기존 uploadId·결과·예상번호를 해제하고 재검사를 요구한다", async () => {
  draw();
  await check();
  await userEvent.upload(
    screen.getByLabelText("파일 선택"),
    new File(["changed"], "changed.xlsx"),
  );
  expect(screen.queryByText("K-0001")).not.toBeInTheDocument();
  expect(screen.queryByText(/업로드 번호 17/)).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "검사" })).toBeEnabled();
});
it("dirty 취소의 계속 작성은 입력을 보존하고 명시적 나가기는 닫는다", async () => {
  const { onClose } = draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByText("K-0001")).toBeInTheDocument();
  expect(onClose).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(onClose).toHaveBeenCalledOnce();
  expect(api.saveB8Apply).not.toHaveBeenCalled();
});
it("422는 새 CHECKED 문제로 교체하고 낡은 예상번호와 반영 가능 상태를 없앤다", async () => {
  vi.mocked(api.saveB8Apply).mockRejectedValue(
    new ApiError(422, {
      success: false,
      meta: {},
      code: "UPLOAD_VALIDATION_FAILED",
      message: "현재 중복입니다",
      requestId: "request-recheck",
      details: {
        ...result,
        canApply: false,
        issues: {
          ...result.issues,
          total: 1,
          items: [
            {
              sequence: 1,
              severity: "ERR",
              rowNumber: 2,
              columnName: "관리번호",
              inputValue: "K-0001",
              reason: "현재 DB 중복",
            },
          ],
        },
      } as never,
    }),
  );
  draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText("현재 DB 중복");
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(
    screen.getByRole("region", { name: "예상 관리번호" }).querySelector("tbody")
      ?.children,
  ).toHaveLength(0);
  expect(api.saveB8Apply).toHaveBeenCalledOnce();
});
it("응답 유실 후 수동 반영은 같은 파일과 ID를 다시 전송한다", async () => {
  vi.mocked(api.saveB8Apply).mockRejectedValueOnce(
    new Error("응답을 받지 못했습니다"),
  );
  draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/응답을 받지 못했습니다/);
  expect(api.saveB8Apply).toHaveBeenCalledOnce();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText("K-0031");
  expect(api.saveB8Apply).toHaveBeenNthCalledWith(2, file, 17);
});
it("반영 후 조회 실패는 저장을 반복하지 않고 재조회만 한다", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("read failed"))
    .mockResolvedValue(undefined);
  draw(refresh);
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.saveB8Apply).toHaveBeenCalledOnce();
});
it("문제 유형과 페이지 조회는 공통 패널로 모든 문제를 요청한다", async () => {
  vi.mocked(issues.getUploadIssues).mockResolvedValue({
    ...result.issues,
    total: 70,
    items: [
      {
        sequence: 2,
        severity: "WARN",
        rowNumber: 3,
        columnName: "거주지 동",
        inputValue: "여러동",
        reason: "동을 못 찾아 비웠습니다",
      },
    ],
  });
  draw();
  await check();
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "WARN");
  await screen.findByText("동을 못 찾아 비웠습니다");
  expect(issues.getUploadIssues).toHaveBeenCalledWith(17, "WARN", 1, 50);
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(issues.getUploadIssues).toHaveBeenCalledWith(17, "WARN", 2, 50),
  );
});
it("잘못된 파일은 검사 전 차단한다", () => {
  draw();
  fireEvent.change(screen.getByLabelText("파일 선택"), {
    target: { files: [new File(["text"], "file.csv")] },
  });
  expect(screen.getByRole("alert")).toHaveTextContent("XLSX");
  expect(api.createB8Validation).not.toHaveBeenCalled();
});
it("767px 직접진입은 PC 안내만 보여주고 업무요청하지 않는다", () => {
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: true }));
  draw();
  expect(
    screen.getByText("엑셀 업로드는 PC에서 이용해 주세요."),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText("파일 선택")).not.toBeInTheDocument();
});
it("PC 양식 다운로드 선기록 실패는 오류를 표시하고 파일을 저장하지 않는다", async () => {
  vi.mocked(api.getB8Template).mockRejectedValue(new Error("접근기록 실패"));
  render(<B8UploadActions onApplied={vi.fn()} />);
  await userEvent.click(screen.getByRole("button", { name: "양식 내려받기" }));
  await screen.findByRole("alert");
  expect(api.saveDownload).not.toHaveBeenCalled();
});
