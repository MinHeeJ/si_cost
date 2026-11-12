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
  ProjectExpensesPanel,
  type ProjectExpensesPanelProps,
} from "./ProjectExpensesPanel";
import { ApiError } from "../../shared/api";
import * as api from "./projectExpensesApi";
import * as shared from "../shared/sharedApi";
vi.mock("./projectExpensesApi");
vi.mock("../shared/sharedApi");
const row: api.ProjectExpense = {
  expenseMonth: "2026-07",
  expenseSn: 3,
  itemCode: "TRIP",
  itemName: "출장비",
  amount: "999999999999999",
  memo: "출장 메모",
  registeredAt: "2026-07-01T09:00:00",
  revision: "before",
};
const data: api.ProjectExpensesData = {
  items: [row],
  totalAmount: row.amount,
  revision: "before",
};
const period: shared.PeriodContext = {
  currentDate: "2026-07-01",
  currentMonth: "2026-07",
  timezone: "Asia/Seoul",
  cashBaseMonth: "2025-01",
  monthClose: { month: "2026-07", status: "작업중" },
};
const saved: api.ExpenseResult = {
  value: { ...data, revision: "after" },
  revision: "after",
  changed: true,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getProjectExpenses).mockResolvedValue(data);
  vi.mocked(shared.getPeriodContext).mockResolvedValue(period);
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "TRIP",
        name: "출장비",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.mocked(shared.getMonthClose).mockImplementation(async (from) => ({
    items: [{ month: from, status: "작업중" }],
  }));
  vi.mocked(api.createProjectExpense).mockResolvedValue(saved);
  vi.mocked(api.deleteProjectExpense).mockResolvedValue({
    ...saved,
    value: { ...saved.value, items: [], totalAmount: "0" },
  });
});
function draw(overrides: Partial<ProjectExpensesPanelProps> = {}) {
  const props: ProjectExpensesPanelProps = {
    projectId: "EXPENSE-1",
    year: 2026,
    contractType: "TK",
    effectiveStatus: "ING",
    onChanged: vi.fn().mockResolvedValue(undefined),
    onDirtyChange: vi.fn(),
    ...overrides,
  };
  render(<ProjectExpensesPanel {...props} />);
  return props;
}
async function ready() {
  await screen.findByText("출장 메모");
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "경비 추가" }),
    ).not.toBeDisabled(),
  );
}
async function open() {
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "경비 추가" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
}
function fill(amount = "1") {
  fireEvent.change(screen.getByLabelText("항목 *"), {
    target: { value: "TRIP" },
  });
  fireEvent.change(screen.getByLabelText("금액 (원) *"), {
    target: { value: amount },
  });
}
it("공통 표에 월·항목·금액·메모·삭제와 정확한 단위를 표시한다", async () => {
  draw();
  await ready();
  expect(
    screen.getAllByRole("columnheader").map((node) => node.textContent),
  ).toEqual(["월", "항목", "금액 (천원)", "메모", "삭제"]);
  expect(screen.getByText("999,999,999,999.999")).toBeInTheDocument();
  expect(screen.getByTestId("project-expenses-panel")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-01",
  );
  expect(
    screen.queryByRole("button", { name: "수정" }),
  ).not.toBeInTheDocument();
});
it("로딩 중 중복 등록을 막고 빈 실제 목록을 표시한다", async () => {
  let resolve!: (value: api.ProjectExpensesData) => void;
  vi.mocked(api.getProjectExpenses).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  draw();
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "경비 추가" })).toBeDisabled();
  resolve({ ...data, items: [], totalAmount: "0" });
  await screen.findByText("등록된 직접 경비가 없습니다.");
  expect(api.createProjectExpense).not.toHaveBeenCalled();
});
it("조회 실패는 요청ID와 재조회만 제공한다", async () => {
  vi.mocked(api.getProjectExpenses).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "expense-read",
    }),
  );
  draw();
  expect(await screen.findByRole("alert")).toHaveTextContent("expense-read");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await ready();
  expect(api.getProjectExpenses).toHaveBeenCalledTimes(2);
  expect(api.createProjectExpense).not.toHaveBeenCalled();
});
it("내부 프로젝트는 경비 영역과 API 호출을 숨긴다", () => {
  draw({ contractType: "IN" });
  expect(
    screen.queryByTestId("project-expenses-panel"),
  ).not.toBeInTheDocument();
  expect(api.getProjectExpenses).not.toHaveBeenCalled();
});
it.each([{ year: 2025 }, { effectiveStatus: "LOSE" }])(
  "읽기전용 %j은 조회하되 등록·삭제를 금지한다",
  async (state) => {
    draw(state);
    await screen.findByText("출장 메모");
    expect(screen.getByRole("button", { name: "경비 추가" })).toBeDisabled();
    expect(
      screen.queryByRole("button", { name: "삭제" }),
    ).not.toBeInTheDocument();
    expect(api.createProjectExpense).not.toHaveBeenCalled();
  },
);
it("계약 종료 후에도 미마감 일회성 경비를 등록할 수 있다", async () => {
  draw({ effectiveStatus: "END" });
  await open();
  fill();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(api.createProjectExpense).toHaveBeenCalledOnce());
});
it("서버 이번달과 활성 코드만 사용하고 일원·선택메모·year·revision을 보낸다", async () => {
  const props = draw();
  await open();
  expect(screen.getByLabelText("경비 월 *")).toHaveValue("2026-07");
  expect(shared.getSharedCodes).toHaveBeenCalledWith("DRCT_EXPS_ITEM");
  fill();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(props.onChanged).toHaveBeenCalledWith(saved));
  expect(api.createProjectExpense).toHaveBeenCalledWith("EXPENSE-1", {
    year: 2026,
    expectedRevision: "before",
    expenseMonth: "2026-07",
    itemCode: "TRIP",
    amount: "1",
    memo: null,
  });
});
it("큰 원 금액은 BigInt 정규화로 손실 없이 보낸다", async () => {
  draw();
  await open();
  fill("999,999,999,999,999\u00a0원");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createProjectExpense).toHaveBeenCalledWith(
      "EXPENSE-1",
      expect.objectContaining({ amount: "999999999999999" }),
    ),
  );
});
it.each(["0", "-1", "1.5", "+1", "1e3", "1%", "", "1000000000000000"])(
  "금액 %s 거부와 입력 보존",
  async (value) => {
    draw();
    await open();
    fill(value);
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(screen.getByLabelText("금액 (원) *")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByLabelText("금액 (원) *")).toHaveValue(value);
    expect(api.createProjectExpense).not.toHaveBeenCalled();
  },
);
it("활성 항목 미선택을 필드 오류로 표시한다", async () => {
  draw();
  await open();
  fireEvent.change(screen.getByLabelText("금액 (원) *"), {
    target: { value: "1" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(screen.getByLabelText("항목 *")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.createProjectExpense).not.toHaveBeenCalled();
});
it("메모 500자 초과는 자르지 않고 거부한다", async () => {
  draw();
  await open();
  fill();
  fireEvent.change(screen.getByLabelText("메모"), {
    target: { value: "가".repeat(501) },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(screen.getByLabelText("메모")).toHaveAttribute("aria-invalid", "true");
  expect(api.createProjectExpense).not.toHaveBeenCalled();
});
it("마감된 행은 삭제할 수 없다", async () => {
  vi.mocked(shared.getMonthClose).mockResolvedValue({
    items: [{ month: "2026-07", status: "마감" }],
  });
  draw();
  await ready();
  expect(screen.getByRole("button", { name: "삭제" })).toBeDisabled();
});
it("선택 등록월이 마감이면 필드 안내하고 저장하지 않는다", async () => {
  vi.mocked(shared.getMonthClose).mockResolvedValue({
    items: [{ month: "2026-07", status: "마감" }],
  });
  draw();
  await open();
  fill();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(screen.getByLabelText("경비 월 *")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.createProjectExpense).not.toHaveBeenCalled();
});
it("이관월은 추가를 막고 기존 행의 삭제도 막는다", async () => {
  vi.mocked(shared.getPeriodContext).mockResolvedValue({
    ...period,
    cashBaseMonth: "2026-08",
  });
  draw();
  await open();
  fill();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(screen.getByLabelText("경비 월 *")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.createProjectExpense).not.toHaveBeenCalled();
});
it("삭제 취소는 요청0이며 확인만 선택 복합키를 보낸다", async () => {
  draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "삭제" }));
  const dialog = screen.getByRole("dialog", { name: "직접 경비 삭제 확인" });
  expect(dialog).toHaveTextContent("EXPENSE-1 / 2026-07 / 3");
  expect(dialog).toHaveTextContent("999,999,999,999,999 원");
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(api.deleteProjectExpense).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "삭제" }));
  await userEvent.click(screen.getByRole("button", { name: "삭제 확인" }));
  await waitFor(() =>
    expect(api.deleteProjectExpense).toHaveBeenCalledWith("EXPENSE-1", row, {
      year: 2026,
      expectedRevision: "before",
    }),
  );
});
it("필드 오류는 요청ID·입력·revision을 유지한다", async () => {
  vi.mocked(api.createProjectExpense).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_INPUT",
      field: "amount",
      message: "금액 확인",
      requestId: "expense-field",
    }),
  );
  draw();
  await open();
  fill();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(screen.getByLabelText("금액 (원) *")).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(screen.getByLabelText("금액 (원) *")).toHaveValue("1");
  expect(screen.getByText(/expense-field/)).toBeInTheDocument();
});
it("STALE_WRITE는 자동 재저장하지 않고 명시적 재조회를 요구한다", async () => {
  vi.mocked(api.createProjectExpense).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "충돌",
      requestId: "expense-stale",
    }),
  );
  draw();
  await open();
  fill();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/자동으로 재저장하지 않습니다/);
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  expect(api.createProjectExpense).toHaveBeenCalledTimes(1);
});
it("저장 이후 부모 조회 실패는 재저장하지 않고 조회만 재시도한다", async () => {
  const props = draw({
    onChanged: vi
      .fn()
      .mockRejectedValueOnce(new Error("조회 실패"))
      .mockResolvedValue(undefined),
  });
  await open();
  fill();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/저장은 완료됐지만 목록을 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByTestId("expenses-committed-refresh"));
  await waitFor(() => expect(props.onChanged).toHaveBeenCalledTimes(2));
  expect(api.createProjectExpense).toHaveBeenCalledTimes(1);
});
it("dirty를 shell로 전달하고 취소 확인의 계속 작성·나가기를 구별한다", async () => {
  const props = draw();
  await open();
  fill();
  await waitFor(() =>
    expect(props.onDirtyChange).toHaveBeenLastCalledWith(true),
  );
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("금액 (원) *")).toHaveValue("1");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.createProjectExpense).not.toHaveBeenCalled();
});
it("저장 중 중복 제출·취소를 차단한다", async () => {
  let finish!: (result: api.ExpenseResult) => void;
  vi.mocked(api.createProjectExpense).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  draw();
  await open();
  fill();
  const dialog = screen.getByRole("dialog");
  fireEvent.submit(dialog);
  fireEvent.submit(dialog);
  await waitFor(() => expect(api.createProjectExpense).toHaveBeenCalledOnce());
  expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
  finish(saved);
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
});
