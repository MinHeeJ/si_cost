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
  ProjectBudgetDialog,
  type ProjectBudgetDialogProps,
} from "./ProjectBudgetDialog";
import { ApiError } from "../../shared/api";
import * as api from "./projectBudgetApi";
vi.mock("./projectBudgetApi");
const original: api.ProjectBudgetData = {
  budgetAmount: "80",
  budgetManual: "N",
  revision: "before",
  autoPreviewAmount: "80",
  targetMarginRate: "20",
  basisMonth: "2026-07",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getProjectBudget).mockResolvedValue(original);
  vi.mocked(api.saveProjectBudget).mockImplementation(async (_id, body) => ({
    value: {
      ...original,
      budgetAmount: body.budgetAmount,
      budgetManual: "Y",
      revision: "after",
    },
    revision: "after",
    changed: true,
  }));
});
function draw(overrides: Partial<ProjectBudgetDialogProps> = {}) {
  const props: ProjectBudgetDialogProps = {
    projectId: "BUDGET-1",
    projectName: "예산 검증",
    contractType: "TK",
    effectiveStatus: "ING",
    year: 2026,
    currentYear: 2026,
    onClose: vi.fn(),
    onSaved: vi.fn().mockResolvedValue(undefined),
    onDirtyChange: vi.fn(),
    ...overrides,
  };
  render(<ProjectBudgetDialog {...props} />);
  return props;
}
async function input(value: string) {
  const field = await screen.findByRole("textbox", { name: /새 예산/ });
  await waitFor(() => expect(field).not.toBeDisabled());
  fireEvent.change(field, { target: { value } });
  return field;
}
it("공통 창과 서버 미리보기·현재 수기표식을 표시한다", async () => {
  draw();
  await screen.findByText(/기준월 2026-07/);
  expect(
    screen.getByRole("dialog", { name: "예산(목표 원가) 변경" }),
  ).toHaveClass("sicost-dialog");
  expect(screen.getByText("자동(N)")).toHaveClass("sicost-tone-info");
  expect(api.getProjectBudget).toHaveBeenCalledWith("BUDGET-1");
});
it("0원을 선택연도와 조회 revision으로 보내고 최종값을 표시한다", async () => {
  const props = draw();
  await input("0");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/예산을 저장했습니다/);
  expect(api.saveProjectBudget).toHaveBeenCalledWith("BUDGET-1", {
    year: 2026,
    expectedRevision: "before",
    budgetAmount: "0",
  });
  expect(props.onSaved).toHaveBeenCalledWith(
    expect.objectContaining({ revision: "after", changed: true }),
  );
  expect(screen.getByText("수기(Y)")).toHaveClass("sicost-tone-warn");
});
it("자동값과 같은 입력도 요청하여 N→Y 의도를 보존한다", async () => {
  draw();
  await input("80");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/예산을 저장했습니다/);
  expect(api.saveProjectBudget).toHaveBeenCalledTimes(1);
});
it.each(["-1", "1.2", "1e3", "+1", "1%", "", "1000000000000000"])(
  "잘못된 금액 %s은 필드 오류이며 입력을 보존한다",
  async (value) => {
    draw();
    const field = await input(value);
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(field).toHaveAttribute("aria-invalid", "true");
    expect(field).toHaveValue(value);
    expect(api.saveProjectBudget).not.toHaveBeenCalled();
  },
);
it("15자리 큰 금액을 Number 변환 없이 원 정수로 보낸다", async () => {
  draw();
  await input("999,999,999,999,999\u00a0원");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/예산을 저장했습니다/);
  expect(api.saveProjectBudget).toHaveBeenCalledWith(
    "BUDGET-1",
    expect.objectContaining({ budgetAmount: "999999999999999" }),
  );
});
it.each([
  { year: 2025 },
  { effectiveStatus: "LOSE" },
  { effectiveStatus: "END" },
  { readOnly: true },
])("기간/상태 읽기전용이면 편집·저장을 제공하지 않는다 %j", async (state) => {
  draw(state);
  await screen.findByText(/조회만 할 수 있습니다/);
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(api.saveProjectBudget).not.toHaveBeenCalled();
});
it("내부 프로젝트는 예산 입력을 숨긴다", async () => {
  draw({ contractType: "IN" });
  await screen.findByText(/내부 프로젝트는 예산을 입력하지 않습니다/);
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(api.saveProjectBudget).not.toHaveBeenCalled();
});
it("목표율이 없으면 자동 미리보기 불가를 알리고 수기 0은 허용한다", async () => {
  vi.mocked(api.getProjectBudget).mockResolvedValue({
    ...original,
    autoPreviewAmount: null,
    targetMarginRate: null,
    budgetAmount: null,
  });
  draw();
  await screen.findByText(/유효한 목표율이 없어/);
  expect(await input("0")).toHaveValue("0");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/예산을 저장했습니다/);
});
it("조회 실패와 로딩에는 저장을 차단하고 조회만 재시도한다", async () => {
  vi.mocked(api.getProjectBudget).mockRejectedValueOnce(new Error("조회 실패"));
  draw();
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  await screen.findByText("조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
  expect(api.getProjectBudget).toHaveBeenCalledTimes(2);
  expect(api.saveProjectBudget).not.toHaveBeenCalled();
});
it("필드오류와 요청ID를 표시하고 입력·revision을 유지한다", async () => {
  vi.mocked(api.saveProjectBudget).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_INPUT",
      field: "budgetAmount",
      message: "금액 확인",
      requestId: "budget-error",
    }),
  );
  draw();
  const field = await input("1");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(field).toHaveAttribute("aria-invalid", "true"));
  expect(field).toHaveValue("1");
  expect(screen.getByText(/budget-error/)).toBeInTheDocument();
  expect(api.saveProjectBudget).toHaveBeenCalledTimes(1);
});
it("STALE_WRITE를 자동 재시도하거나 덮어쓰지 않는다", async () => {
  vi.mocked(api.saveProjectBudget).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다른 사용자가 변경했습니다.",
      requestId: "stale-budget",
    }),
  );
  draw();
  const field = await input("1");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/자동으로 재저장하지 않습니다/);
  expect(field).toHaveValue("1");
  expect(api.saveProjectBudget).toHaveBeenCalledTimes(1);
});
it("저장된 수기 예산은 자동 전환 선택을 제공하지 않는다", async () => {
  vi.mocked(api.getProjectBudget).mockResolvedValue({
    ...original,
    budgetManual: "Y",
  });
  draw();
  await screen.findByText("수기(Y)");
  expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
});
it("창을 연 뒤 기준 변경은 최종 서버 기준으로 안내한다", async () => {
  vi.mocked(api.saveProjectBudget).mockResolvedValue({
    value: {
      ...original,
      budgetAmount: "0",
      budgetManual: "Y",
      revision: "after",
      autoPreviewAmount: "70",
      targetMarginRate: "30",
    },
    revision: "after",
    changed: true,
  });
  draw();
  await input("0");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/창을 연 뒤 자동 예산 기준이 변경되었습니다/);
  expect(screen.getByText(/목표율 30%/)).toBeInTheDocument();
});
it("저장후 조회 실패의 재시도는 조회 콜백만 실행한다", async () => {
  const onSaved = vi
    .fn()
    .mockRejectedValueOnce(new Error("목록 실패"))
    .mockResolvedValue(undefined);
  draw({ onSaved });
  await input("0");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/저장은 완료됐지만 목록을 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(2));
  expect(api.saveProjectBudget).toHaveBeenCalledTimes(1);
});
it("동일 Y 금액 무변경 응답을 구별한다", async () => {
  vi.mocked(api.saveProjectBudget).mockResolvedValue({
    value: { ...original, budgetManual: "Y" },
    revision: "before",
    changed: false,
  });
  draw();
  await input("80");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/바뀐 내용이 없습니다/);
});
it("dirty를 shell로 전파하고 계속 작성과 나가기를 구별한다", async () => {
  const props = draw();
  await input("79");
  expect(props.onDirtyChange).toHaveBeenLastCalledWith(true);
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  const guard = screen.getByRole("dialog", {
    name: "저장하지 않은 내용이 있습니다.",
  });
  await userEvent.click(
    within(guard).getByRole("button", { name: "계속 작성" }),
  );
  expect(props.onClose).not.toHaveBeenCalled();
  expect(screen.getByRole("textbox")).toHaveValue("79");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(props.onClose).toHaveBeenCalledTimes(1);
});
it("저장 중 중복 제출과 취소를 차단한다", async () => {
  let finish!: (result: api.SaveProjectBudgetResult) => void;
  vi.mocked(api.saveProjectBudget).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  draw();
  await input("0");
  const dialog = screen.getByRole("dialog");
  fireEvent.submit(dialog);
  fireEvent.submit(dialog);
  await waitFor(() => expect(api.saveProjectBudget).toHaveBeenCalledTimes(1));
  expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
  finish({
    value: { ...original, budgetAmount: "0", budgetManual: "Y" },
    revision: "after",
    changed: true,
  });
  await screen.findByText(/예산을 저장했습니다/);
});
