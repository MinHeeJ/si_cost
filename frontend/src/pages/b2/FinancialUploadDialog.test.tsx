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
  FinancialUploadActions,
  FinancialUploadDialog,
} from "./FinancialUploadDialog";
import { ApiError } from "../../shared/api";
import * as api from "./financialImportApi";
import * as issues from "../uploadsops/uploadIssuesApi";
vi.mock("./financialImportApi");
vi.mock("../uploadsops/uploadIssuesApi");
const result: api.FinancialUploadResult = {
  uploadId: "999999999999",
  formType: "B2",
  screenId: "UI-10",
  month: "2026-06",
  status: "CHECKED",
  summary: {
    totalRows: 1,
    successRows: 1,
    warningRows: 0,
    errorRows: 0,
    replaceCount: 3,
    preserveCount: 4,
  },
  canApply: true,
  expectedRevision: "checked-revision",
  changed: false,
  appliedAt: null,
  issues: { items: [], total: 0, page: 1, size: 50 },
  items: [],
};
const applied: api.FinancialUploadResult = {
  ...result,
  status: "APPLIED",
  canApply: false,
  changed: true,
  appliedAt: "2026-06-15T09:00:00",
  items: [{ rowNumber: 2, itemSn: "7" }],
};
const file = new File(["contents"], "financial.xlsx");
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: false }));
  vi.mocked(api.createB2Validation).mockResolvedValue(result);
  vi.mocked(api.saveB2Apply).mockResolvedValue(applied);
  vi.mocked(api.getFinancialUploadResult).mockResolvedValue(applied);
});
function draw(
  props: Partial<React.ComponentProps<typeof FinancialUploadDialog>> = {},
) {
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(
    <FinancialUploadDialog
      month="2026-06"
      onClose={vi.fn()}
      onApplied={refresh}
      {...props}
    />,
  );
  return refresh;
}
async function check() {
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByText(/교체 예정 3줄/);
}
async function confirm() {
  await userEvent.click(screen.getByRole("button", { name: "교체 내용 확인" }));
  await userEvent.click(
    await screen.findByRole("button", { name: "교체 반영" }),
  );
}
it("readonly month and headers render, only explicit M6 confirmation sends same File/string id/revision", async () => {
  const refresh = draw();
  expect(screen.getByText(/대상월 2026-06/)).toBeInTheDocument();
  await check();
  expect(screen.getByText(/직접입력 보존 4줄/)).toBeInTheDocument();
  expect(screen.getByText("검사 문제가 없습니다.")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "교체 내용 확인" }));
  const confirmation = await screen.findByRole("dialog", {
    name: "재무 항목 엑셀 교체 확인",
  });
  expect(within(confirmation).getByText(/3줄을 지우고/)).toBeInTheDocument();
  expect(api.saveB2Apply).not.toHaveBeenCalled();
  await userEvent.click(
    within(confirmation).getByRole("button", { name: "교체 반영" }),
  );
  await screen.findByTestId("financial-upload-result-2");
  expect(api.saveB2Apply).toHaveBeenCalledWith(
    file,
    "2026-06",
    "999999999999",
    "checked-revision",
  );
  expect(refresh).toHaveBeenCalledOnce();
});
it("cancel M6 preserves checked state and sends no business request", async () => {
  draw();
  await check();
  await userEvent.click(screen.getByRole("button", { name: "교체 내용 확인" }));
  const dialog = await screen.findByRole("dialog", {
    name: "재무 항목 엑셀 교체 확인",
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "교체 내용 확인" }),
    ).toBeEnabled(),
  );
  expect(api.saveB2Apply).not.toHaveBeenCalled();
});
it("ERR blocks apply while WARN remains allowed", async () => {
  vi.mocked(api.createB2Validation).mockResolvedValue({
    ...result,
    canApply: false,
    summary: { ...result.summary, successRows: 0, errorRows: 1 },
  });
  draw();
  await check();
  expect(screen.getByRole("button", { name: "교체 내용 확인" })).toBeDisabled();
  expect(screen.getByText("오류 1건")).toHaveClass("b7-bad");
});
it("warning badge retains its semantic color and permits explicit confirmation", async () => {
  vi.mocked(api.createB2Validation).mockResolvedValue({
    ...result,
    summary: { ...result.summary, successRows: 0, warningRows: 1 },
  });
  draw();
  await check();
  expect(screen.getByText("경고 1건")).toHaveClass("b7-warn");
  expect(screen.getByRole("button", { name: "교체 내용 확인" })).toBeEnabled();
});
it("file changes discard old ID and confirmation, recheck requests fresh validation", async () => {
  draw();
  await check();
  await userEvent.upload(
    screen.getByLabelText("파일 선택"),
    new File(["changed"], "changed.xlsx"),
  );
  expect(screen.queryByText(/업로드 번호/)).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "교체 내용 확인" }),
  ).not.toBeInTheDocument();
  expect(api.saveB2Apply).not.toHaveBeenCalled();
});
it("read-only blocks input and requests but preserves template action", () => {
  draw({ readOnly: true });
  expect(screen.getByLabelText("파일 선택")).toBeDisabled();
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  expect(api.createB2Validation).not.toHaveBeenCalled();
});
it("failed validation retains file, error and manual retry without automatic requests", async () => {
  vi.mocked(api.createB2Validation).mockRejectedValue(new Error("헤더 오류"));
  draw();
  await userEvent.upload(screen.getByLabelText("파일 선택"), file);
  await userEvent.click(screen.getByRole("button", { name: "검사" }));
  await screen.findByRole("alert");
  expect(screen.getByText("financial.xlsx")).toBeInTheDocument();
  expect(api.createB2Validation).toHaveBeenCalledOnce();
});
it("422 financial.upload replaces diagnostics and disables apply", async () => {
  const latest = {
    ...result,
    canApply: false,
    summary: { ...result.summary, successRows: 0, errorRows: 1 },
    issues: {
      ...result.issues,
      total: 1,
      items: [
        {
          sequence: 1,
          severity: "ERR" as const,
          rowNumber: 2,
          columnName: "구분",
          inputValue: "판관비",
          reason: "현재 기준 비활성",
        },
      ],
    },
  };
  vi.mocked(api.saveB2Apply).mockRejectedValue(
    new ApiError(422, {
      success: false,
      meta: {},
      code: "REVALIDATION_FAILED",
      message: "다시 검사",
      requestId: "check-request",
      details: { financial: { upload: latest } } as never,
    }),
  );
  draw();
  await check();
  await confirm();
  await screen.findByText("현재 기준 비활성");
  expect(screen.getByRole("button", { name: "교체 내용 확인" })).toBeDisabled();
  expect(api.saveB2Apply).toHaveBeenCalledOnce();
});
it("409 requires a new inspection and cannot reuse confirmation", async () => {
  vi.mocked(api.saveB2Apply).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "새 검사 필요",
      requestId: "stale-request",
    }),
  );
  draw();
  await check();
  await confirm();
  await screen.findByRole("alert");
  expect(screen.getByRole("button", { name: "교체 내용 확인" })).toBeDisabled();
});
it("lost response does not auto resend, manual result query refreshes committed UI", async () => {
  vi.mocked(api.saveB2Apply).mockRejectedValueOnce(new Error("응답 유실"));
  const refresh = draw();
  await check();
  await confirm();
  await screen.findByRole("alert");
  expect(api.saveB2Apply).toHaveBeenCalledOnce();
  await userEvent.click(screen.getByRole("button", { name: "결과 재조회" }));
  await screen.findByTestId("financial-upload-result-2");
  expect(api.getFinancialUploadResult).toHaveBeenCalledWith("999999999999");
  expect(api.saveB2Apply).toHaveBeenCalledOnce();
  expect(refresh).toHaveBeenCalledOnce();
});
it("refresh failure retries only reads without repeating apply", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("조회 실패"))
    .mockResolvedValue(undefined);
  draw({ onApplied: refresh });
  await check();
  await confirm();
  await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.saveB2Apply).toHaveBeenCalledOnce();
});
it("invalid file blocked before server call", () => {
  draw();
  fireEvent.change(screen.getByLabelText("파일 선택"), {
    target: { files: [new File(["text"], "bad.csv")] },
  });
  expect(screen.getByRole("alert")).toHaveTextContent("XLSX");
  expect(api.createB2Validation).not.toHaveBeenCalled();
});
it("PC-only surface sends no business requests", () => {
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: true }));
  draw();
  expect(
    screen.getByText("엑셀 업로드는 PC에서 이용해 주세요."),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText("파일 선택")).not.toBeInTheDocument();
});
it("template failure never saves a file", async () => {
  vi.mocked(api.getB2Template).mockRejectedValue(new Error("기록 실패"));
  draw();
  await userEvent.click(screen.getByRole("button", { name: "양식 내려받기" }));
  await screen.findByRole("alert");
  expect(api.saveDownload).not.toHaveBeenCalled();
});
it("saved issues use string ID and shared severity pagination", async () => {
  vi.mocked(issues.getUploadIssues).mockResolvedValue(result.issues);
  draw();
  await check();
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "WARN");
  await waitFor(() =>
    expect(issues.getUploadIssues).toHaveBeenCalledWith(
      "999999999999",
      "WARN",
      1,
      50,
    ),
  );
});
it("toolbar waits for the owning editor dirty guard before opening upload", async () => {
  const beforeOpen = vi
    .fn()
    .mockResolvedValueOnce(false)
    .mockResolvedValueOnce(true);
  render(
    <FinancialUploadActions
      month="2026-06"
      beforeOpen={beforeOpen}
      onApplied={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "엑셀 업로드" }));
  expect(screen.queryByLabelText("파일 선택")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "엑셀 업로드" }));
  expect(await screen.findByLabelText("파일 선택")).toBeInTheDocument();
  expect(beforeOpen).toHaveBeenCalledTimes(2);
});
