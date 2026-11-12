import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { B6UploadDialog } from "./B6UploadDialog";
import { ApiError } from "../../shared/api";
import * as api from "./b6Api";
vi.mock("./b6Api");
vi.mock("../uploadsops/uploadIssuesApi");
const summary = {
  totalRows: 1,
  successRows: 0,
  warningRows: 1,
  errorRows: 0,
  fileWarningCount: 0,
  tableWarningCount: 1,
};
const comparison: api.B6Comparison = {
  selectedStep: "PLAN",
  contractAmount: "100",
  totalAmount: "99",
  differenceAmount: "1",
  mismatchCount: 0,
  items: [],
  warnings: ["계산 선택표 차이 1원"],
};
const validation: api.B6Validation = {
  uploadId: 27,
  summary,
  canApply: true,
  revision: "project-revision",
  prospectiveComparison: comparison,
  uploadedTableTotal: "100",
  issues: {
    items: [
      {
        sequence: 1,
        severity: "WARN",
        rowNumber: 2,
        columnName: "합계",
        inputValue: "0",
        reason: "월 합계와 다릅니다.",
      },
    ],
    total: 1,
    page: 1,
    size: 50,
  },
};
const applied: Awaited<ReturnType<typeof api.getUploadResult>> = {
  uploadId: 27,
  formType: "B6",
  status: "APPLIED",
  projectId: "P-selected",
  step: "PLAN",
  summary,
  results: [],
  revision: "original-upload-revision",
  changed: true,
};
const file = new File(["workbook contents"], "distribution.xlsx");
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.createB6Validation).mockResolvedValue(validation);
  vi.mocked(api.saveB6Apply).mockResolvedValue(applied);
  vi.mocked(api.getUploadResult).mockResolvedValue(applied);
});
function draw(
  readOnly = false,
  onApplied = vi.fn().mockResolvedValue(undefined),
  onClose = vi.fn(),
  onDirtyChange = vi.fn(),
) {
  render(
    <B6UploadDialog
      projectId="P-selected"
      year={2026}
      startDate="2026-06-01"
      endDate="2026-07-31"
      readOnly={readOnly}
      onApplied={onApplied}
      onClose={onClose}
      onDirtyChange={onDirtyChange}
    />,
  );
  return { onApplied, onClose, onDirtyChange };
}
async function check() {
  await userEvent.selectOptions(screen.getByTestId("b6-step-select"), "PLAN");
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText("월 합계와 다릅니다.");
}
it("대상과 기간은 읽기전용이고 단계 필수·검사후 같은 File/ID/연도/단계/revision으로 직접 반영한다", async () => {
  const { onApplied } = draw();
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  expect(screen.getByLabelText("파일 선택")).toBeDisabled();
  expect(screen.getByText(/대상 P-selected/)).toHaveTextContent(
    "2026-06-01 ~ 2026-07-31",
  );
  await check();
  expect(api.createB6Validation).toHaveBeenCalledWith(file, {
    year: 2026,
    projectId: "P-selected",
    step: "PLAN",
  });
  expect(screen.getByText("업로드 표 자체 합계 100원")).toBeInTheDocument();
  expect(screen.getByText(/계산 선택 예정표/)).toHaveTextContent(
    "선택표 총액 99",
  );
  expect(screen.getByText("경고 1건")).toHaveClass("b7-warn");
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  expect(await screen.findByTestId("b6-applied-target")).toHaveTextContent(
    "APPLIED · 대상 P-selected · 당시 단계 PLAN",
  );
  expect(api.saveB6Apply).toHaveBeenCalledWith(
    file,
    27,
    { year: 2026, projectId: "P-selected", step: "PLAN" },
    "project-revision",
  );
  expect(onApplied).toHaveBeenCalledTimes(1);
  expect(screen.queryByText("최종 프로젝트ID")).not.toBeInTheDocument();
  expect(screen.queryByText("관리번호")).not.toBeInTheDocument();
});
it("단계 변경은 이전 File/검사를 버리고 새 검사만 허용한다", async () => {
  draw();
  await check();
  await userEvent.selectOptions(screen.getByTestId("b6-step-select"), "EST");
  expect(screen.queryByText("월 합계와 다릅니다.")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await waitFor(() =>
    expect(api.createB6Validation).toHaveBeenLastCalledWith(file, {
      year: 2026,
      projectId: "P-selected",
      step: "EST",
    }),
  );
});
it("파일 교체는 같은 단계에서도 새 검사이며 이전 반영은 불가능하다", async () => {
  draw();
  await check();
  const next = new File(["different"], "replacement.xlsx");
  await userEvent.upload(screen.getByLabelText("파일 선택"), next);
  expect(
    screen.queryByRole("button", { name: "전량 반영" }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await waitFor(() =>
    expect(api.createB6Validation).toHaveBeenLastCalledWith(next, {
      year: 2026,
      projectId: "P-selected",
      step: "PLAN",
    }),
  );
});
it.each(["오류", "빈 표"])(
  "%s는 canApply와 별도로 반영이 차단된다",
  async (mode) => {
    vi.mocked(api.createB6Validation).mockResolvedValue({
      ...validation,
      summary: {
        ...summary,
        errorRows: mode === "오류" ? 1 : 0,
        totalRows: mode === "빈 표" ? 0 : 1,
      },
    });
    draw();
    await check();
    expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
    expect(api.saveB6Apply).not.toHaveBeenCalled();
  },
);
it("보호월409와 문의번호는 파일·검사번호를 유지하고 자동 재시도하지 않는다", async () => {
  vi.mocked(api.saveB6Apply).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "CLOSED_MONTH",
      message: "2026-06 보호월 금액 차이 1원",
      requestId: "protected-month",
    }),
  );
  draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  expect(await screen.findByText(/protected-month/)).toHaveTextContent(
    "차이 1원",
  );
  expect(screen.getByText("distribution.xlsx")).toBeInTheDocument();
  expect(api.saveB6Apply).toHaveBeenCalledTimes(1);
});
it("422는 동일 ID 최신 문제로 교체하고 재반영을 막는다", async () => {
  vi.mocked(api.saveB6Apply).mockRejectedValue(
    new ApiError(422, {
      success: false,
      meta: {},
      code: "UPLOAD_VALIDATION_FAILED",
      message: "최신 오류",
      requestId: "recheck",
      details: {
        ...validation,
        canApply: false,
        summary: { ...summary, errorRows: 1 },
      } as never,
    }),
  );
  draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/최신 오류/);
  expect(screen.getByRole("button", { name: "전량 반영" })).toBeDisabled();
});
it("지난 연도/실주 읽기 전용은 파일·단계 변경을 막지만 양식 조회는 허용한다", async () => {
  draw(true);
  expect(screen.getByTestId("b6-step-select")).toBeDisabled();
  expect(screen.getByLabelText("파일 선택")).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "양식 내려받기" }));
  expect(api.getB6Template).toHaveBeenCalledWith("P-selected");
  expect(api.createB6Validation).not.toHaveBeenCalled();
});
it("검사대기 중 단계와 파일 변경·중복제출을 막는다", async () => {
  let finish!: (value: api.B6Validation) => void;
  vi.mocked(api.createB6Validation).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  draw();
  await userEvent.selectOptions(screen.getByTestId("b6-step-select"), "PLAN");
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  expect(screen.getByTestId("b6-step-select")).toBeDisabled();
  expect(screen.getByLabelText("파일 선택")).toBeDisabled();
  finish(validation);
  await screen.findByText("월 합계와 다릅니다.");
});
it("응답 유실은 명시 결과 재조회로 복구하며 재저장은 자동 수행하지 않는다", async () => {
  vi.mocked(api.saveB6Apply).mockRejectedValueOnce(new Error("응답 유실"));
  draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "전량 반영" }));
  await screen.findByText(/응답 유실 파일과/);
  expect(api.getUploadResult).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "결과 재조회" }));
  await screen.findByTestId("b6-applied-target");
  expect(api.saveB6Apply).toHaveBeenCalledTimes(1);
});
it("반영후 재조회 실패는 저장하지 않고 재조회만 반복한다", async () => {
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
  expect(api.saveB6Apply).toHaveBeenCalledTimes(1);
});
it("닫기는 dirty 확인만 하며 CHECKED를 삭제하거나 반영하지 않는다", async () => {
  const { onClose, onDirtyChange } = draw();
  await check();
  expect(onDirtyChange).toHaveBeenCalledWith(true);
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(onClose).toHaveBeenCalledTimes(1);
  expect(api.saveB6Apply).not.toHaveBeenCalled();
});
it("형식/크기 오류는 검사 API 없이 표시한다", async () => {
  draw();
  await userEvent.selectOptions(screen.getByTestId("b6-step-select"), "PLAN");
  fireEvent.change(screen.getByLabelText("파일 선택"), {
    target: { files: [new File([new Uint8Array(10485761)], "large.xlsx")] },
  });
  expect(screen.getByRole("alert")).toHaveTextContent("10MiB");
  expect(api.createB6Validation).not.toHaveBeenCalled();
});
it("모바일은 PC 안내만 제공한다", () => {
  const original = window.matchMedia;
  window.matchMedia = vi.fn().mockReturnValue({ matches: true });
  try {
    draw();
    expect(
      screen.getByText("이 기능은 PC에서 사용해 주세요."),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("파일 선택")).not.toBeInTheDocument();
  } finally {
    window.matchMedia = original;
  }
});
