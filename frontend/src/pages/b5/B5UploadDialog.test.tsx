import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { B5Actions, B5UploadDialog } from "./B5UploadDialog";
import * as api from "./b5Api";
import * as issues from "../uploadsops/uploadIssuesApi";
import * as shared from "../shared/sharedApi";
import { ApiError } from "../../shared/api";
vi.mock("./b5Api");
vi.mock("../uploadsops/uploadIssuesApi");
vi.mock("../shared/sharedApi");
const summary = {
  totalRows: 1,
  successRows: 1,
  warningRows: 0,
  errorRows: 0,
  fileWarningCount: 0,
  tableWarningCount: 0,
};
const checked: api.B5Validation = {
  uploadId: 19,
  formType: "B5",
  screenId: "UI-12",
  status: "CHECKED",
  summary,
  results: [],
  changed: false,
  withdrawnIds: [],
  issueCount: 0,
  previewToken: "current-token",
  substitutionPreviews: [],
};
const receipt: api.B5Result = {
  ...checked,
  status: "APPLIED",
  changed: true,
  results: [{ rowNumber: 2, assignmentId: "A-first" }],
  withdrawnIds: [],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.createB5Validation).mockResolvedValue(checked);
  vi.mocked(api.createB5Apply).mockResolvedValue(receipt);
  vi.mocked(api.getB5Result).mockResolvedValue(receipt);
  vi.mocked(issues.getUploadIssues).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: [{ code: "ETC", name: "기타" }],
  } as Awaited<ReturnType<typeof shared.getSharedCodes>>);
});
function draw(
  readOnly = false,
  onApplied = vi.fn().mockResolvedValue(undefined),
) {
  const onClose = vi.fn();
  render(
    <B5UploadDialog
      year={2026}
      readOnly={readOnly}
      onClose={onClose}
      onApplied={onApplied}
    />,
  );
  return { onClose, onApplied };
}
async function check() {
  const file = new File(["original"], "assignments.xlsx");
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await waitFor(() =>
    expect(
      screen.queryByTestId("b7-recheck-button") ??
        screen.queryByTestId("b5-receipt"),
    ).toBeInTheDocument(),
  );
  return file;
}
it("신규 전용과 10열 안내를 표시하고 같은 File과 year로 검사 후 반영한다", async () => {
  const { onApplied } = draw();
  const file = await check();
  expect(screen.getByText(/수정은 변경 기록/)).toBeInTheDocument();
  expect(api.createB5Validation).toHaveBeenCalledWith(
    file,
    2026,
    [],
    undefined,
  );
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByTestId("b5-receipt");
  expect(screen.getByText("A-first")).toBeInTheDocument();
  expect(api.createB5Apply).toHaveBeenCalledWith(
    file,
    2026,
    [],
    19,
    "current-token",
  );
  expect(onApplied).toHaveBeenCalledOnce();
});
it("지난 해는 파일입력과 검사 비활성, 양식 다운로드는 유지한다", () => {
  draw(true);
  expect(screen.getByLabelText("파일 선택")).toBeDisabled();
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "양식 내려받기" })).toBeEnabled();
});
it("오류행이 있으면 반영할 수 없다", async () => {
  vi.mocked(api.createB5Validation).mockResolvedValue({
    ...checked,
    summary: { ...summary, successRows: 0, errorRows: 1 },
    issueCount: 1,
  });
  draw();
  await check();
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(api.createB5Apply).not.toHaveBeenCalled();
});
it("SUB 기본 체크 없음, 사유 선택 변경은 동일ID 재검사와 새 토큰이 필요하다", async () => {
  const sub = {
    rowNumber: 2,
    preview: {
      previewToken: "row-token",
      withdrawalDate: "2026-05-31",
      candidates: [{ id: "A-old", expectedRevision: "revision" }],
      warnings: [],
      protectedMonths: [],
      turnkeyNotice: "배분표 재업로드 안내",
    },
  };
  vi.mocked(api.createB5Validation).mockResolvedValue({
    ...checked,
    substitutionPreviews: [sub],
  });
  draw();
  const file = await check();
  const box = screen.getByRole("checkbox", { name: /A-old/ });
  expect(box).not.toBeChecked();
  expect(screen.getByText("배분표 재업로드 안내")).toBeInTheDocument();
  await userEvent.click(box);
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  await userEvent.selectOptions(screen.getByLabelText("철수 사유"), "ETC");
  vi.mocked(api.createB5Validation).mockResolvedValue({
    ...checked,
    previewToken: "new-token",
    substitutionPreviews: [sub],
  });
  await userEvent.click(screen.getByRole("button", { name: "다시 검사" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "전량 반영" })).toBeEnabled(),
  );
  expect(api.createB5Validation).toHaveBeenLastCalledWith(
    file,
    2026,
    [
      {
        rowNumber: 2,
        withdrawals: [
          {
            assignmentId: "A-old",
            expectedRevision: "revision",
            withdrawalDate: "2026-05-31",
            reasonCode: "ETC",
            memo: "",
          },
        ],
      },
    ],
    19,
  );
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByTestId("b5-receipt");
  expect(vi.mocked(api.createB5Apply).mock.calls[0][4]).toBe("new-token");
});
it("409는 저장 자동재시도 없이 재검사 안내 및 반영 차단", async () => {
  vi.mocked(api.createB5Apply).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "대상집합 변경",
      requestId: "trace",
    }),
  );
  draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/선택 또는 원천이 변경/);
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(api.createB5Apply).toHaveBeenCalledOnce();
});
it("422는 새 검사요약과 문제를 표시하고 업무반영하지 않는다", async () => {
  vi.mocked(api.createB5Apply).mockRejectedValue(
    new ApiError(422, {
      success: false,
      meta: {},
      code: "REVALIDATION_FAILED",
      message: "다시 검사 오류",
      requestId: "trace",
      details: {
        ...checked,
        summary: { ...summary, successRows: 0, errorRows: 1 },
        issueCount: 1,
        previewToken: "latest",
      } as unknown as ApiError["body"]["details"],
    }),
  );
  draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText("오류 1건");
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
  expect(screen.queryByTestId("b5-receipt")).not.toBeInTheDocument();
});
it("APPLIED 재검사는 최초 결과만 조회하고 신규 반영하지 않는다", async () => {
  vi.mocked(api.createB5Validation).mockResolvedValue({
    ...checked,
    ...receipt,
  });
  const { onApplied } = draw();
  await check();
  await screen.findByTestId("b5-receipt");
  expect(api.getB5Result).toHaveBeenCalledWith(19);
  expect(api.createB5Apply).not.toHaveBeenCalled();
  expect(onApplied).toHaveBeenCalledOnce();
});
it("조회실패 뒤 다시 불러오기는 재저장 없이 콜백만 호출한다", async () => {
  const onApplied = vi
    .fn()
    .mockRejectedValueOnce(new Error("refresh"))
    .mockResolvedValue(undefined);
  draw(false, onApplied);
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/저장됐지만 다시 불러오지/);
  await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
  await waitFor(() => expect(onApplied).toHaveBeenCalledTimes(2));
  expect(api.createB5Apply).toHaveBeenCalledOnce();
});
it("작성 중 취소는 확인을 받고 서버 검사기록을 삭제하지 않는다", async () => {
  const { onClose } = draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(onClose).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(onClose).toHaveBeenCalledOnce();
  expect(api.createB5Apply).not.toHaveBeenCalled();
});
it("검사실패는 빈 정상상태로 감추지 않는다", async () => {
  vi.mocked(api.createB5Validation).mockRejectedValue(
    new Error("검사 요청 실패"),
  );
  draw();
  await userEvent.upload(
    screen.getByLabelText("파일 선택"),
    new File(["original"], "assignments.xlsx"),
  );
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText(/검사 요청 실패/);
  expect(screen.getByRole("button", { name: "검사" })).toBeEnabled();
  expect(api.createB5Apply).not.toHaveBeenCalled();
});
it("읽기전용 액션은 다운로드 가능하며 업로드를 막는다", () => {
  render(<B5Actions year={2025} readOnly onApplied={vi.fn()} />);
  expect(screen.getByRole("button", { name: "엑셀 업로드" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "양식 내려받기" })).toBeEnabled();
});
