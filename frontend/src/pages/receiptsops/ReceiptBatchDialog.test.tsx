import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { ReceiptBatchDialog } from "./ReceiptBatchDialog";
import { ReceiptBatchAction } from "./ReceiptBatchAction";
import * as api from "./receiptBatchApi";
vi.mock("./receiptBatchApi", async (original) => ({
  ...(await original<typeof api>()),
  createBatchReceipts: vi.fn(),
  loadReceiptBatchSelection: vi.fn(),
}));
const selection: api.ReceiptBatchSelection = {
  items: [
    {
      billId: "TEST-A",
      unpaidAmount: "999999999999999",
      expectedRevision: "first",
    },
    { billId: "TEST-B", unpaidAmount: "300", expectedRevision: "second" },
  ],
  total: 2,
  excluded: [{ billId: "TEST-C", reason: "취소 청구" }],
  excludedCount: 1,
  canSave: true,
};
const result: api.ReceiptBatchResult = {
  completedCount: 2,
  totalAmount: "1000000000000299",
  items: [],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.createBatchReceipts).mockResolvedValue(result);
  vi.mocked(api.loadReceiptBatchSelection).mockResolvedValue(selection);
});
function draw(
  overrides: Partial<React.ComponentProps<typeof ReceiptBatchDialog>> = {},
) {
  const props = {
    year: 2026,
    today: "2026-07-15",
    minimumDate: "2025-01-01",
    selection,
    projectNames: { "TEST-A": "프로젝트 A", "TEST-B": "프로젝트 B" },
    onClose: vi.fn(),
    onReload: vi.fn(),
    onRefresh: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
  render(<ReceiptBatchDialog {...props} />);
  return props;
}
it("기본 미수 전액과 공통 서버오늘, 제외사유, 정밀한 전체합을 보여준다", () => {
  draw();
  expect(screen.getByLabelText(/공통 입금일/)).toHaveValue("2026-07-15");
  expect(screen.getByLabelText(/TEST-A 입금액/)).toHaveValue("999999999999999");
  expect(
    screen.getByText("전체 입금합 1,000,000,000,000,299원"),
  ).toBeInTheDocument();
  expect(screen.getByText(/TEST-C · 취소 청구/)).toHaveClass(
    "sicost-tone-warn",
  );
});
it("고정 revision, 선택연도와 정규화된 분할금액만 전송하고 성공 후 즉시 갱신한다", async () => {
  const props = draw();
  fireEvent.change(screen.getByLabelText(/TEST-A 입금액/), {
    target: { value: " 1,000 원" },
  });
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  expect(api.createBatchReceipts).toHaveBeenCalledWith({
    year: 2026,
    date: "2026-07-15",
    items: [
      { billId: "TEST-A", amount: "1000", expectedRevision: "first" },
      { billId: "TEST-B", amount: "300", expectedRevision: "second" },
    ],
  });
  expect(props.onRefresh).toHaveBeenCalledWith(result);
  expect(await screen.findByText(/등록 완료/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
});
it.each(["0", "-1", "1.5", "1e3", "301", "1000000000000000"])(
  "한 행 %s 오류이면 전건 저장을 차단한다",
  async (amount) => {
    draw();
    fireEvent.change(screen.getByLabelText(/TEST-B 입금액/), {
      target: { value: amount },
    });
    expect(screen.getByLabelText(/TEST-B 입금액/)).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    await userEvent.click(screen.getByRole("button", { name: "등록" }));
    expect(api.createBatchReceipts).not.toHaveBeenCalled();
  },
);
it("개별 선택 해제와 전체 선택을 금액/전송 대상에 반영한다", async () => {
  draw();
  await userEvent.click(screen.getByRole("checkbox", { name: "TEST-B 선택" }));
  expect(screen.getByLabelText(/TEST-B 입금액/)).toBeDisabled();
  expect(
    screen.getByText("전체 입금합 999,999,999,999,999원"),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "전체 선택" }));
  expect(screen.getByLabelText(/TEST-B 입금액/)).toBeEnabled();
});
it("대상 0건은 빈 안내와 저장불가다", () => {
  draw({
    selection: {
      items: [],
      total: 0,
      excluded: [],
      excludedCount: 0,
      canSave: false,
    },
  });
  expect(
    screen.getByText(/입금할 수 있는 청구가 없습니다/),
  ).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
});
it("과거 조회연도는 입력과 등록이 비활성이다", () => {
  draw({ year: 2025 });
  expect(screen.getByText("지난 연도는 조회만 할 수 있습니다.")).toHaveClass(
    "sicost-tone-lock",
  );
  expect(screen.getByLabelText(/TEST-A 입금액/)).toBeDisabled();
  expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
});
it.each(["2026-07-16", "2025-12-31"])(
  "허용되지 않은 날짜 %s로 등록하지 않는다",
  (date) => {
    draw();
    fireEvent.change(screen.getByLabelText(/공통 입금일/), {
      target: { value: date },
    });
    expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
  },
);
it("409는 입력을 보존하고 자동 재전송 없이 재조회만 제공한다", async () => {
  vi.mocked(api.createBatchReceipts).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "청구가 변경되었습니다.",
      requestId: "test-request",
    }),
  );
  const props = draw();
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  expect(await screen.findByText("청구가 변경되었습니다.")).toBeInTheDocument();
  expect(screen.getByLabelText(/TEST-B 입금액/)).toHaveValue("300");
  expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "재조회" }));
  expect(props.onReload).toHaveBeenCalledTimes(1);
  expect(api.createBatchReceipts).toHaveBeenCalledTimes(1);
  expect(props.onRefresh).not.toHaveBeenCalled();
});
it("400 항목별 violations를 행 입력에 보여준다", async () => {
  vi.mocked(api.createBatchReceipts).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "입력 확인",
      requestId: "test-validation",
      details: {
        violations: [{ field: "items[1].amount", message: "입금액 확인" }],
      },
    }),
  );
  draw();
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await waitFor(() =>
    expect(screen.getByLabelText(/TEST-B 입금액/)).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(screen.getByText("입금액 확인")).toBeInTheDocument();
});
it("저장 성공 후 조회 실패는 재등록하지 않고 조회만 재시도한다", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("조회 실패"))
    .mockResolvedValue(undefined);
  draw({ onRefresh: refresh });
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  expect(
    await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "재조회" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.createBatchReceipts).toHaveBeenCalledTimes(1);
});
it("전송 중 중복 제출을 막는다", async () => {
  vi.mocked(api.createBatchReceipts).mockReturnValue(new Promise(() => {}));
  draw();
  fireEvent.submit(screen.getByRole("dialog"));
  fireEvent.submit(screen.getByRole("dialog"));
  expect(api.createBatchReceipts).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText(/공통 입금일/)).toBeDisabled();
});
it("변경 입력은 취소 확인 후에만 닫힌다", async () => {
  const props = draw();
  fireEvent.change(screen.getByLabelText(/TEST-B 입금액/), {
    target: { value: "1" },
  });
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(props.onClose).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "입력 취소" }));
  expect(props.onClose).toHaveBeenCalledTimes(1);
  expect(api.createBatchReceipts).not.toHaveBeenCalled();
});
it("고른 청구와 조회전체는 서버 선택 API로 준비하며 조건이 바뀌면 선택을 해제한다", async () => {
  const props = {
    filters: { year: 2026 },
    selectedIds: ["TEST-A"],
    today: "2026-07-15",
    minimumDate: "2025-01-01",
    onRefresh: vi.fn(),
  };
  const view = render(<ReceiptBatchAction {...props} />);
  await userEvent.click(screen.getByTestId("receipt-batch-open"));
  expect(await screen.findByRole("dialog")).toBeInTheDocument();
  expect(api.loadReceiptBatchSelection).toHaveBeenCalledWith(
    props.filters,
    props.selectedIds,
    expect.any(AbortSignal),
  );
  view.rerender(
    <ReceiptBatchAction
      {...props}
      filters={{ year: 2026, month: "2026-08" }}
      whole
    />,
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("receipt-batch-open"));
  expect(api.loadReceiptBatchSelection).toHaveBeenLastCalledWith(
    { year: 2026, month: "2026-08" },
    undefined,
    expect.any(AbortSignal),
  );
});
it("일괄 입금 버튼은 하나이고 고른 청구가 없으면 눌리지 않는다", () => {
  render(
    <ReceiptBatchAction
      filters={{ year: 2026 }}
      selectedIds={[]}
      today="2026-07-15"
      minimumDate="2025-01-01"
      onRefresh={vi.fn()}
    />,
  );
  expect(screen.getAllByRole("button")).toHaveLength(1);
  expect(screen.getByRole("button", { name: "일괄 입금" })).toBeDisabled();
});
it("선택 조회 실패는 저장창 대신 오류/재조회를 보인다", async () => {
  vi.mocked(api.loadReceiptBatchSelection).mockRejectedValue(
    new Error("선택 조회 실패"),
  );
  render(
    <ReceiptBatchAction
      filters={{ year: 2026 }}
      selectedIds={[]}
      whole
      today="2026-07-15"
      minimumDate="2025-01-01"
      onRefresh={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByTestId("receipt-batch-open"));
  expect(await screen.findByText("선택 조회 실패")).toBeInTheDocument();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.createBatchReceipts).not.toHaveBeenCalled();
});
it("모바일에는 다건입금 작업을 제공하지 않는다", () => {
  render(
    <ReceiptBatchAction
      mobile
      filters={{ year: 2026 }}
      selectedIds={[]}
      today="2026-07-15"
      minimumDate="2025-01-01"
      onRefresh={vi.fn()}
    />,
  );
  expect(screen.queryByTestId("receipt-batch-action")).not.toBeInTheDocument();
});
