import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { B9UploadActions, B9UploadDialog } from "./B9UploadDialog";
import { ApiError } from "../../shared/api";
import * as api from "./b9Api";
import * as issuesApi from "../uploadsops/uploadIssuesApi";
vi.mock("./b9Api");
vi.mock("../uploadsops/uploadIssuesApi");
const summary = {
  totalRows: 1,
  successRows: 0,
  warningRows: 1,
  errorRows: 0,
  fileWarningCount: 0,
  tableWarningCount: 0,
};
const validation: api.ProjectUploadValidation = {
  uploadId: 27,
  summary,
  canApply: true,
  revision: null,
  prospectiveComparison: null,
  uploadedTableTotal: null,
  issues: {
    items: [
      {
        sequence: 1,
        severity: "WARN",
        rowNumber: 2,
        columnName: "계약유형",
        inputValue: "옛턴키",
        reason: "이전 표기의 대응",
      },
    ],
    total: 1,
    page: 1,
    size: 50,
  },
};
const applied: api.ProjectUploadResult = {
  uploadId: 27,
  formType: "B9",
  status: "APPLIED",
  projectId: null,
  step: null,
  summary,
  results: [{ rowNumber: 2, id: "P-0290", name: "최초 프로젝트명" }],
  revision: "opaque-upload-revision",
  changed: true,
};
const file = new File(["workbook contents"], "projects.xlsx");
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.createB9Validation).mockResolvedValue(validation);
  vi.mocked(api.saveB9Apply).mockResolvedValue(applied);
  vi.mocked(api.getUploadResult).mockResolvedValue(applied);
});
function draw(
  readOnly = false,
  onApplied = vi.fn().mockResolvedValue(undefined),
  onClose = vi.fn(),
  onDirtyChange = vi.fn(),
) {
  render(
    <B9UploadDialog
      year={2026}
      readOnly={readOnly}
      onApplied={onApplied}
      onClose={onClose}
      onDirtyChange={onDirtyChange}
    />,
  );
  return { onApplied, onClose, onDirtyChange };
}
async function check() {
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText("이전 표기의 대응");
}
it("선택연도·신규전용 안내와 검사 순서를 표시하고 같은 File/ID/year로 경고 행을 반영한다", async () => {
  const { onApplied } = draw();
  expect(screen.getByText(/선택연도 2026/)).toHaveTextContent(
    "기존 프로젝트 수정은 화면에서",
  );
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  await check();
  expect(api.createB9Validation).toHaveBeenCalledWith(file, 2026);
  expect(screen.getByText("경고 1건")).toHaveClass("b7-warn");
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText("P-0290");
  expect(screen.getByText("최초 프로젝트명")).toBeInTheDocument();
  expect(screen.getByText("최종 프로젝트ID")).toBeInTheDocument();
  expect(screen.queryByText("관리번호")).not.toBeInTheDocument();
  expect(api.saveB9Apply).toHaveBeenCalledWith(file, 27, 2026);
  expect(onApplied).toHaveBeenCalledTimes(1);
});
it("오류 행은 반영 비활성·프로젝트 등록 0 안내이고 오류 색상은 경고와 다르다", async () => {
  vi.mocked(api.createB9Validation).mockResolvedValue({
    ...validation,
    canApply: false,
    summary: { ...summary, errorRows: 1, warningRows: 0 },
  });
  draw();
  await check();
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(screen.getByText(/등록된 프로젝트는 없습니다/)).toHaveClass("b7-bad");
  expect(api.saveB9Apply).not.toHaveBeenCalled();
});
it("지난 연도는 파일선택과 검사를 막되 빈 양식 다운로드는 허용한다", async () => {
  draw(true);
  expect(screen.getByLabelText("파일 선택")).toBeDisabled();
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  expect(
    screen.getByText("지난 연도는 조회만 할 수 있습니다."),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "양식 내려받기" }));
  expect(api.getB9Template).toHaveBeenCalledTimes(1);
  expect(api.createB9Validation).not.toHaveBeenCalled();
});
it("빈 행수 결과는 canApply 값과 무관하게 반영하지 않는다", async () => {
  vi.mocked(api.createB9Validation).mockResolvedValue({
    ...validation,
    summary: { ...summary, totalRows: 0 },
  });
  draw();
  await check();
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
});
it("422 최신 문제로 교체하고 같은 업로드 번호와 파일을 유지하며 자동재시도하지 않는다", async () => {
  vi.mocked(api.saveB9Apply).mockRejectedValue(
    new ApiError(422, {
      success: false,
      meta: {},
      code: "UPLOAD_VALIDATION_FAILED",
      message: "최신 중복 오류",
      requestId: "b9-check",
      details: {
        ...validation,
        canApply: false,
        summary: { ...summary, errorRows: 1, warningRows: 0 },
      } as never,
    }),
  );
  draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/최신 중복 오류/);
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(screen.getByText("projects.xlsx")).toBeInTheDocument();
  expect(api.saveB9Apply).toHaveBeenCalledTimes(1);
});
it("응답 유실은 자동 재요청 없이 명시 결과 재조회로 최초 ID/이름을 복원한다", async () => {
  vi.mocked(api.saveB9Apply).mockRejectedValueOnce(new Error("응답 유실"));
  draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/응답 유실 파일과/);
  expect(api.getUploadResult).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "결과 재조회" }));
  await screen.findByText("최초 프로젝트명");
  expect(api.getUploadResult).toHaveBeenCalledWith(27);
  expect(api.saveB9Apply).toHaveBeenCalledTimes(1);
});
it("파일교체는 이전 검사·반영상태를 버리고 새 검사 API를 호출한다", async () => {
  draw();
  await check();
  const replacement = new File(["new contents"], "new.xlsx");
  await userEvent.upload(screen.getByLabelText("파일 선택"), replacement);
  expect(screen.queryByText("이전 표기의 대응")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await waitFor(() =>
    expect(api.createB9Validation).toHaveBeenLastCalledWith(replacement, 2026),
  );
});
it("검사 실패는 입력 유지와 문의번호를 보여주고 수동 재검사를 허용한다", async () => {
  vi.mocked(api.createB9Validation).mockRejectedValueOnce(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "READ_ONLY_YEAR",
      message: "지난 연도입니다",
      requestId: "year-request",
    }),
  );
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  expect(await screen.findByText(/year-request/)).toHaveTextContent(
    "지난 연도입니다",
  );
  expect(screen.getByText("projects.xlsx")).toBeInTheDocument();
  expect(api.createB9Validation).toHaveBeenCalledTimes(1);
});
it("검사문제 빈 목록을 공통 빈 상태로 보여준다", async () => {
  vi.mocked(api.createB9Validation).mockResolvedValue({
    ...validation,
    issues: { ...validation.issues, items: [], total: 0 },
  });
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  expect(await screen.findByText("검사 문제가 없습니다.")).toBeInTheDocument();
});
it("후보 상세는 모든 사업자번호를 표시하며 문제 필터 API는 uploadId를 유지한다", async () => {
  const issue = {
    ...validation.issues.items[0],
    severity: "ERR" as const,
    columnName: "고객사",
    candidates: [
      { id: "C-A", name: "동일상호", businessNo: "0012345678" },
      { id: "C-B", name: "동일상호", businessNo: "0012345679" },
    ],
  };
  vi.mocked(api.createB9Validation).mockResolvedValue({
    ...validation,
    canApply: false,
    issues: { ...validation.issues, items: [issue] },
  });
  vi.mocked(issuesApi.getUploadIssues).mockResolvedValue({
    ...validation.issues,
    items: [issue],
  });
  draw();
  await check();
  await userEvent.click(screen.getByText("후보 상세 펼침/접기"));
  expect(screen.getByText(/C-A · 동일상호 · 0012345678/)).toBeVisible();
  expect(screen.getByText(/C-B · 동일상호 · 0012345679/)).toBeVisible();
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "ERR");
  await waitFor(() =>
    expect(issuesApi.getUploadIssues).toHaveBeenCalledWith(27, "ERR", 1, 50),
  );
});
it("닫기는 dirty 확인만 수행하고 CHECKED 삭제·반영 요청을 하지 않는다", async () => {
  const { onClose, onDirtyChange } = draw();
  await check();
  expect(onDirtyChange).toHaveBeenCalledWith(true);
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(
    screen.getByText("저장하지 않은 내용이 있습니다."),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(onClose).toHaveBeenCalledTimes(1);
  expect(api.saveB9Apply).not.toHaveBeenCalled();
});
it("반영후 목록 재조회 실패는 재저장 없이 재조회만 수행한다", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("refresh"))
    .mockResolvedValue(undefined);
  draw(false, refresh);
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.saveB9Apply).toHaveBeenCalledTimes(1);
});
it("파일 크기·확장자 오류는 서버 검사 없이 표시한다", () => {
  draw();
  fireEvent.change(screen.getByLabelText("파일 선택"), {
    target: { files: [new File([new Uint8Array(10485761)], "large.xlsx")] },
  });
  expect(screen.getByRole("alert")).toHaveTextContent("10MiB");
  fireEvent.change(screen.getByLabelText("파일 선택"), {
    target: { files: [new File(["wrong"], "wrong.csv")] },
  });
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  expect(api.createB9Validation).not.toHaveBeenCalled();
});
it("PC 목록에 양식과 업로드 CTA를 제공하고 읽기 전용이면 업로드만 막는다", () => {
  render(<B9UploadActions year={2025} readOnly onApplied={vi.fn()} />);
  expect(screen.getByRole("button", { name: "양식 내려받기" })).toBeEnabled();
  expect(screen.getByRole("button", { name: "엑셀 업로드" })).toBeDisabled();
});
it("모바일 직접 진입은 PC 안내만 표시하고 업로드 컨트롤은 숨긴다", () => {
  const original = window.matchMedia;
  window.matchMedia = vi.fn().mockReturnValue({ matches: true });
  try {
    draw();
    expect(
      screen.getByText("이 기능은 PC에서 사용해 주세요."),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("파일 선택")).not.toBeInTheDocument();
    expect(api.createB9Validation).not.toHaveBeenCalled();
  } finally {
    window.matchMedia = original;
  }
});
it("검사 대기 중에는 중복 제출과 파일 변경을 막는다", async () => {
  let finish!: (value: api.ProjectUploadValidation) => void;
  vi.mocked(api.createB9Validation).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  expect(screen.getByLabelText("파일 선택")).toBeDisabled();
  expect(screen.getByRole("button", { name: /처리 중/ })).toBeDisabled();
  finish(validation);
  await screen.findByText("이전 표기의 대응");
  expect(api.createB9Validation).toHaveBeenCalledTimes(1);
});
