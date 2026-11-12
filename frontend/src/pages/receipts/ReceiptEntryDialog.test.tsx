import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  ReceiptEntryDialog,
  type ReceiptEntryDialogProps,
} from "./ReceiptEntryDialog";
import * as api from "./receiptsApi";
import * as shared from "../shared/sharedApi";
vi.mock("./receiptsApi");
vi.mock("../shared/sharedApi");
const bill: api.Bill = {
  id: "B-202501-001",
  projectId: "P-TEST",
  projectName: "종료 프로젝트",
  customerName: "고객",
  contractType: "TK",
  order: 1,
  billingMonth: "2025-01",
  billingDate: "2025-01-01",
  supplyAmount: "1000",
  vatRate: "10",
  vatAmount: "100",
  totalAmount: "1100",
  receiptAmount: "0",
  unpaidAmount: "1100",
  dueDate: "2026-07-01",
  taxInvoiceNo: null,
  taxInvoiceWarning: "번호 미입력",
  status: "UNPAID",
  cancelled: false,
  cancelReason: null,
  cancelUserId: null,
  cancelledAt: null,
  carryover: true,
  dunning: null,
  quickActions: {
    copyAllowed: false,
    nextMonthAllowed: false,
    reason: "종료 프로젝트",
  },
  revision: "before",
};
const period: shared.PeriodContext = {
  currentDate: "2026-07-01",
  currentMonth: "2026-07",
  cashBaseMonth: "2025-01",
  timezone: "Asia/Seoul",
  monthClose: { month: "2026-07", status: "작업중" },
};
const receipt: api.Receipt = {
  id: 7,
  billId: bill.id,
  date: "2026-07-01",
  amount: "400",
  cancelled: false,
  cancelReason: null,
  cancelUserId: null,
  cancelledAt: null,
};
const result: api.ReceiptResult = {
  changed: true,
  receipt,
  bill: { ...bill, revision: "after", unpaidAmount: "700" },
  revision: "after",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(shared.getMonthClose).mockImplementation(async (from) => ({
    items: [{ month: from, status: "작업중" }],
  }));
  vi.mocked(api.createBillReceipt).mockResolvedValue(result);
  vi.mocked(api.saveBillReceiptCancellation).mockResolvedValue(result);
});
function draw(overrides: Partial<ReceiptEntryDialogProps> = {}) {
  const props = {
    bill,
    year: 2026,
    period,
    onClose: vi.fn(),
    onSaved: vi.fn().mockResolvedValue(undefined),
    onDirtyChange: vi.fn(),
    ...overrides,
  };
  render(<ReceiptEntryDialog {...props} />);
  return props;
}
async function ready(label = "등록") {
  await waitFor(() =>
    expect(screen.getByRole("button", { name: label })).not.toBeDisabled(),
  );
}
function amount(value: string) {
  fireEvent.change(screen.getByLabelText("입금액 (원) *"), {
    target: { value },
  });
}
it("서버오늘·현재미수 기본값과 분할입금·year/revision을 전달한다", async () => {
  const props = draw();
  await ready();
  expect(screen.getByLabelText("입금일 *")).toHaveValue("2026-07-01");
  expect(screen.getByLabelText("입금액 (원) *")).toHaveValue("1100");
  amount(" 4,00 원 ");
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await waitFor(() => expect(props.onSaved).toHaveBeenCalledWith(result));
  expect(api.createBillReceipt).toHaveBeenCalledWith(bill.id, {
    year: 2026,
    expectedRevision: "before",
    date: "2026-07-01",
    amount: "400",
  });
});
it.each(["0", "-1", "1.5", "1e3", "+1", "1000000000000000", "1101"])(
  "잘못된/초과 금액 %s는 필드 오류이며 요청0",
  async (value) => {
    draw();
    await ready();
    amount(value);
    await userEvent.click(screen.getByRole("button", { name: "등록" }));
    expect(screen.getByLabelText("입금액 (원) *")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(api.createBillReceipt).not.toHaveBeenCalled();
  },
);
it("십오자리 원 금액을 Number 없이 그대로 전달한다", async () => {
  draw({ bill: { ...bill, unpaidAmount: "999999999999999" } });
  await ready();
  amount("999,999,999,999,999 원");
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await waitFor(() =>
    expect(api.createBillReceipt).toHaveBeenCalledWith(
      bill.id,
      expect.objectContaining({ amount: "999999999999999" }),
    ),
  );
});
it("미래 입금일을 거부한다", async () => {
  draw();
  await ready();
  fireEvent.change(screen.getByLabelText("입금일 *"), {
    target: { value: "2026-07-02" },
  });
  await ready();
  fireEvent.submit(screen.getByRole("dialog"));
  await screen.findByText("실제 입금일은 서버 오늘 이하여야 합니다.");
  expect(api.createBillReceipt).not.toHaveBeenCalled();
});
it("열린 입금월만 검사하며 원청구월은 검사하지 않는다", async () => {
  draw();
  await ready();
  expect(shared.getMonthClose).toHaveBeenCalledWith("2026-07", "2026-07");
  expect(shared.getMonthClose).not.toHaveBeenCalledWith("2025-01", "2025-01");
});
it("입금월마감은 등록을 잠근다", async () => {
  vi.mocked(shared.getMonthClose).mockResolvedValue({
    items: [{ month: "2026-07", status: "마감" }],
  });
  draw();
  await screen.findByText(/입금일의 월이 마감/);
  expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
});
it("지난연도문맥은 등록 불가이고 내년문맥은 오늘입금 가능", async () => {
  draw({ year: 2025 });
  await screen.findByText("지난 연도는 조회만 할 수 있습니다.");
  expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
});
it("내년 조회문맥은 실제오늘과 같지 않아도 제출한다", async () => {
  draw({ year: 2027 });
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await waitFor(() =>
    expect(api.createBillReceipt).toHaveBeenCalledWith(
      bill.id,
      expect.objectContaining({ year: 2027, date: period.currentDate }),
    ),
  );
});
it("정상입금 취소는 소속ID·사유·현재revision만 제출한다", async () => {
  draw({ receipt });
  await ready("입금 취소");
  fireEvent.change(screen.getByLabelText("취소 사유 *"), {
    target: { value: " 오입금 " },
  });
  await userEvent.click(screen.getByRole("button", { name: "입금 취소" }));
  await waitFor(() =>
    expect(api.saveBillReceiptCancellation).toHaveBeenCalledWith(bill.id, 7, {
      year: 2026,
      reason: "오입금",
      expectedRevision: "before",
    }),
  );
});
it.each(["", " ", "가".repeat(501)])(
  "취소 사유 필수·길이 오류는 자르지 않는다",
  async (reason) => {
    draw({ receipt });
    await ready("입금 취소");
    fireEvent.change(screen.getByLabelText("취소 사유 *"), {
      target: { value: reason },
    });
    await userEvent.click(screen.getByRole("button", { name: "입금 취소" }));
    expect(screen.getByLabelText("취소 사유 *")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(api.saveBillReceiptCancellation).not.toHaveBeenCalled();
  },
);
it("STALE_WRITE는 입력·문의번호 유지하고 자동재전송하지 않는다", async () => {
  vi.mocked(api.createBillReceipt).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "충돌",
      requestId: "receipt-stale",
    }),
  );
  draw();
  await ready();
  amount("400");
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await screen.findByText(/자동으로 재저장하지 않습니다/);
  expect(screen.getByText(/receipt-stale/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
  expect(api.createBillReceipt).toHaveBeenCalledOnce();
});
it("서버 field와 details.violations를 그린다", async () => {
  vi.mocked(api.createBillReceipt).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "오류",
      requestId: "receipt-field",
      details: { violations: [{ field: "amount", message: "금액 확인" }] },
    }),
  );
  draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await screen.findByText("금액 확인");
  expect(screen.getByLabelText("입금액 (원) *")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
});
it("저장후 조회실패는 저장성공과 구별하고 GET만 재시도한다", async () => {
  const props = draw({
    onSaved: vi
      .fn()
      .mockRejectedValueOnce(new Error("조회 실패"))
      .mockResolvedValue(undefined),
  });
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByTestId("receipt-refresh-saved"));
  await waitFor(() => expect(props.onSaved).toHaveBeenCalledTimes(2));
  expect(api.createBillReceipt).toHaveBeenCalledOnce();
});
it("dirty 취소 확인·계속작성·나가기는 API0", async () => {
  const props = draw();
  await ready();
  amount("400");
  await userEvent.click(screen.getByRole("button", { name: "닫기" }));
  const confirm = screen.getByRole("dialog", {
    name: "저장하지 않은 내용이 있습니다.",
  });
  await userEvent.click(
    within(confirm).getByRole("button", { name: "계속 작성" }),
  );
  expect(screen.getByLabelText("입금액 (원) *")).toHaveValue("400");
  await userEvent.click(screen.getByRole("button", { name: "닫기" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(props.onClose).toHaveBeenCalled();
  expect(api.createBillReceipt).not.toHaveBeenCalled();
});
it("저장중 중복 제출과 닫기 금지", async () => {
  let finish!: (value: api.ReceiptResult) => void;
  vi.mocked(api.createBillReceipt).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  draw();
  await ready();
  const dialog = screen.getByRole("dialog");
  fireEvent.submit(dialog);
  fireEvent.submit(dialog);
  await waitFor(() => expect(api.createBillReceipt).toHaveBeenCalledOnce());
  expect(screen.getByRole("button", { name: "닫기" })).toBeDisabled();
  finish(result);
});
