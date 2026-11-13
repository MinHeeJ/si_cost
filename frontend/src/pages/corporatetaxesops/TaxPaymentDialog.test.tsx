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
  TaxPaymentDialog,
  type TaxPaymentDialogProps,
} from "./TaxPaymentDialog";
import { CorporateTaxAllocationScreen } from "./CorporateTaxAllocationScreen";
import { CorporateTaxPanel } from "./CorporateTaxPanel";
import * as api from "./corporateTaxApi";
import * as shared from "../shared/sharedApi";
vi.mock("./corporateTaxApi");
vi.mock("../shared/sharedApi");
const audit = {
  regUserId: "admin",
  regDttm: "2026-10-01T09:00:00",
  modUserId: null,
  modDttm: null,
};
const payment: api.CorporateTaxPayment = {
  year: "2025",
  payType: "CNFM",
  dueDate: "2026-03-31",
  payAmount: null,
  paidDate: null,
  payYn: "N",
  displayAmount: "1234",
  automaticAmount: "1234",
  manuallyEdited: false,
  status: "확인 필요",
  confirmationRevision: "current",
  audit,
};
const result: api.CorporateTaxConfirmation = {
  changed: true,
  payment: {
    ...payment,
    payYn: "Y",
    paidDate: "2026-09-30",
    payAmount: "0",
    displayAmount: "0",
    status: "납부",
  },
  revision: "after",
};
const allocation: api.CorporateTaxAllocation = {
  year: "2026",
  estimatedTax: "18400000",
  closedTotal: "0",
  remainingAmount: "18400000",
  openMonthCount: 12,
  months: [
    {
      month: "2026-12",
      closeStatus: "WORK",
      amount: "1533337",
      verified: true,
      fixed: null,
      migrated: null,
    },
  ],
  verified: true,
  warnings: [],
};
const editor: api.CorporateTaxContext = {
  month: "2026-10",
  today: "2026-10-15",
  cashBaseMonth: "2025-01",
  editable: true,
  readOnlyReason: null,
  corporateTax: { year: "2026", estimatedTax: "18400000", audit },
  taxPayments: [payment],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(shared.getMonthClose).mockResolvedValue({ items: [] });
  vi.mocked(api.saveCorporateTaxPaymentConfirmation).mockResolvedValue(result);
  vi.mocked(api.getCorporateTaxAllocation).mockResolvedValue(allocation);
  vi.mocked(api.createCorporateTaxDefaults).mockResolvedValue({
    changed: false,
    corporateTax: editor.corporateTax!,
    taxPayments: [payment],
    revision: "current",
  });
});
function draw(overrides: Partial<TaxPaymentDialogProps> = {}) {
  const props = {
    payment,
    month: editor.month,
    today: editor.today,
    cashBaseMonth: editor.cashBaseMonth,
    editable: true,
    onClose: vi.fn(),
    onSaved: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
  render(<TaxPaymentDialog {...props} />);
  return props;
}
async function ready() {
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "확인" })).not.toBeDisabled(),
  );
}
function amount(value: string) {
  fireEvent.change(screen.getByLabelText("실제납부액 (원) *"), {
    target: { value },
  });
}
it("예정일·표시액을 기본값으로 사용하고 선택월과 다른 실제월의 명시0도 전송한다", async () => {
  const props = draw();
  await ready();
  expect(screen.getByLabelText("실제납부일 *")).toHaveValue(payment.dueDate);
  fireEvent.change(screen.getByLabelText("실제납부일 *"), {
    target: { value: "2026-09-30" },
  });
  amount(" 0 원 ");
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  await waitFor(() => expect(props.onSaved).toHaveBeenCalledWith(result));
  expect(api.saveCorporateTaxPaymentConfirmation).toHaveBeenCalledWith(
    "2025",
    "CNFM",
    {
      month: "2026-10",
      expectedRevision: "current",
      paidDate: "2026-09-30",
      payAmount: "0",
    },
  );
});
it.each(["", "-1", "1.5", "1e3", "1x", "1000000000000000"])(
  "잘못된 실제액 %s는 요청 없이 필드 오류",
  async (value) => {
    draw();
    await ready();
    amount(value);
    await userEvent.click(screen.getByRole("button", { name: "확인" }));
    expect(screen.getByLabelText("실제납부액 (원) *")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(api.saveCorporateTaxPaymentConfirmation).not.toHaveBeenCalled();
  },
);
it("큰 원 정수는 반올림하지 않는다", async () => {
  draw();
  await ready();
  amount("999,999,999,999,999 원");
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  await waitFor(() =>
    expect(api.saveCorporateTaxPaymentConfirmation).toHaveBeenCalledWith(
      "2025",
      "CNFM",
      expect.objectContaining({ payAmount: "999999999999999" }),
    ),
  );
});
it("미확인 금액은 0으로 채우지 않는다", async () => {
  draw({ payment: { ...payment, displayAmount: null } });
  await ready();
  expect(screen.getByLabelText("실제납부액 (원) *")).toHaveValue("");
});
it("미래일은 거부한다", async () => {
  draw();
  await ready();
  fireEvent.change(screen.getByLabelText("실제납부일 *"), {
    target: { value: "2026-10-16" },
  });
  await ready();
  fireEvent.submit(screen.getByRole("dialog"));
  await screen.findByText(/실제 달력일이며 서버 오늘 이하/);
  expect(api.saveCorporateTaxPaymentConfirmation).not.toHaveBeenCalled();
});
it("실제월 CLOSE는 차단한다", async () => {
  vi.mocked(shared.getMonthClose).mockResolvedValue({
    items: [{ month: "2026-03", status: "마감" }],
  });
  draw();
  await screen.findByText(/실제납부월이 마감/);
  expect(screen.getByRole("button", { name: "확인" })).toBeDisabled();
});
it("원예정월 마감이어도 날짜를 열린 실제월로 바꿀 수 있다", async () => {
  vi.mocked(shared.getMonthClose).mockImplementation(async (from) => ({
    items: from === "2026-03" ? [{ month: from, status: "마감" }] : [],
  }));
  draw();
  await screen.findByText(/실제납부월이 마감/);
  fireEvent.change(screen.getByLabelText("실제납부일 *"), {
    target: { value: "2026-09-30" },
  });
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  await waitFor(() =>
    expect(api.saveCorporateTaxPaymentConfirmation).toHaveBeenCalledOnce(),
  );
});
it.each([
  { date: "2025-12-31", base: "2025-01" },
  { date: "2026-03-31", base: "2026-04" },
])("지난해/운영시작 전 실제월 차단", async ({ date, base }) => {
  draw({ payment: { ...payment, dueDate: date }, cashBaseMonth: base });
  await screen.findByText(/실제납부월이 마감/);
  expect(screen.getByRole("button", { name: "확인" })).toBeDisabled();
});
it("선택월 읽기전용은 열린 실제월이어도 차단", async () => {
  draw({ editable: false });
  await screen.findByText("선택월은 읽기 전용입니다.");
  expect(screen.getByRole("button", { name: "확인" })).toBeDisabled();
});
it("확인행은 재확인 불가", async () => {
  draw({ payment: result.payment });
  await screen.findByText("이미 납부 확인된 일정입니다.");
  expect(screen.getByRole("button", { name: "확인" })).toBeDisabled();
});
it("월마감 조회 실패시 확인하지 않는다", async () => {
  vi.mocked(shared.getMonthClose).mockRejectedValue(new Error("월 조회 실패"));
  draw();
  await screen.findByText("월 조회 실패");
  expect(screen.getByRole("button", { name: "확인" })).toBeDisabled();
});
it.each(["STALE_WRITE", "TAX_ALREADY_PAID"])(
  "%s는 입력을 유지하며 자동재전송하지 않는다",
  async (code) => {
    vi.mocked(api.saveCorporateTaxPaymentConfirmation).mockRejectedValue(
      new ApiError(409, {
        success: false,
        meta: {},
        code,
        message: "최신 상태 충돌",
        requestId: "tax-conflict",
      }),
    );
    draw();
    await ready();
    amount("13");
    await userEvent.click(screen.getByRole("button", { name: "확인" }));
    await screen.findByText(/자동으로 재확인하지 않습니다/);
    expect(screen.getByLabelText("실제납부액 (원) *")).toHaveValue("13");
    expect(screen.getByText(/tax-conflict/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "확인" })).toBeDisabled();
    expect(api.saveCorporateTaxPaymentConfirmation).toHaveBeenCalledOnce();
  },
);
it("서버 필드 오류를 표시한다", async () => {
  vi.mocked(api.saveCorporateTaxPaymentConfirmation).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      field: "payAmount",
      message: "금액 확인",
      requestId: "field-tax",
    }),
  );
  draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  await waitFor(() =>
    expect(screen.getByLabelText("실제납부액 (원) *")).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
});
it("저장 성공 이후 조회 실패는 재저장 없이 조회만 재시도", async () => {
  const props = draw({
    onSaved: vi
      .fn()
      .mockRejectedValueOnce(new Error("read"))
      .mockResolvedValue(undefined),
  });
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/);
  await userEvent.click(screen.getByTestId("tax-confirmation-refresh"));
  await waitFor(() => expect(props.onSaved).toHaveBeenCalledTimes(2));
  expect(api.saveCorporateTaxPaymentConfirmation).toHaveBeenCalledOnce();
});
it("취소는 납부행을 삭제하거나 수정하지 않는다", async () => {
  const props = draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(props.onClose).toHaveBeenCalled();
  expect(api.saveCorporateTaxPaymentConfirmation).not.toHaveBeenCalled();
});
it("처리중 중복제출 금지", async () => {
  vi.mocked(api.saveCorporateTaxPaymentConfirmation).mockReturnValue(
    new Promise(() => {}),
  );
  draw();
  await ready();
  fireEvent.submit(screen.getByRole("dialog"));
  fireEvent.submit(screen.getByRole("dialog"));
  await waitFor(() =>
    expect(api.saveCorporateTaxPaymentConfirmation).toHaveBeenCalledOnce(),
  );
  expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
});
it("배분 조회는 미래12월까지 서버의 정확한 잔액을 표시하고 초기화하지 않는다", async () => {
  render(<CorporateTaxAllocationScreen year="2026" />);
  await screen.findByText("1,533,337 원");
  expect(api.getCorporateTaxAllocation).toHaveBeenCalledWith("2026");
  expect(api.createCorporateTaxDefaults).not.toHaveBeenCalled();
});
it("배분 누락/NULL은 미확인이고 음수 경고는 빨간 톤", async () => {
  vi.mocked(api.getCorporateTaxAllocation).mockResolvedValue({
    ...allocation,
    verified: false,
    remainingAmount: "-1",
    warnings: [
      "마감 법인세 자료를 확인할 수 없습니다",
      "금액이 맞는지 확인하세요",
    ],
    months: [
      {
        ...allocation.months[0],
        closeStatus: "CLOSE",
        amount: null,
        verified: false,
      },
    ],
  });
  render(<CorporateTaxAllocationScreen year="2026" />);
  await screen.findByText("마감 법인세 자료를 확인할 수 없습니다");
  expect(screen.getByText("미확인")).toBeInTheDocument();
  expect(screen.getByText("금액이 맞는지 확인하세요")).toHaveClass(
    "sicost-tone-bad",
  );
  expect(screen.getByText("마감")).toHaveClass("corporate-tax-lock");
});
it("배분 빈 목록", async () => {
  vi.mocked(api.getCorporateTaxAllocation).mockResolvedValue({
    ...allocation,
    months: [],
  });
  render(<CorporateTaxAllocationScreen year="2026" />);
  await screen.findByText("배분할 운영월이 없습니다.");
});
it("배분 실패 후 다시 조회", async () => {
  vi.mocked(api.getCorporateTaxAllocation)
    .mockRejectedValueOnce(new Error("배분 실패"))
    .mockResolvedValue(allocation);
  render(<CorporateTaxAllocationScreen year="2026" />);
  await screen.findByText("배분 실패");
  await userEvent.click(screen.getByTestId("corporate-tax-allocation-reload"));
  await screen.findByText("1,533,337 원");
});
it("편집월 진입은 별도 초기화 후 최신 editor를 읽는다", async () => {
  const refresh = vi.fn().mockResolvedValue(editor);
  render(<CorporateTaxPanel editor={editor} onRefresh={refresh} />);
  await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
  expect(api.createCorporateTaxDefaults).toHaveBeenCalledWith(editor.month);
  expect(
    vi.mocked(api.createCorporateTaxDefaults).mock.invocationCallOrder[0],
  ).toBeLessThan(refresh.mock.invocationCallOrder[0]);
});
it("과거/마감/시작전 문맥은 초기화 없이 읽기전용", async () => {
  render(
    <CorporateTaxPanel
      editor={{ ...editor, editable: false, readOnlyReason: "마감 월" }}
      onRefresh={vi.fn()}
    />,
  );
  await screen.findByText("마감 월");
  expect(api.createCorporateTaxDefaults).not.toHaveBeenCalled();
  expect(screen.getByRole("button", { name: "납부 확인" })).toBeDisabled();
});
it("납부/확인필요/예정은 서로 다른 상태색이고 확인행 CTA는 없다", async () => {
  render(
    <CorporateTaxPanel
      editor={{
        ...editor,
        editable: false,
        taxPayments: [
          result.payment,
          { ...payment, year: "2024" },
          { ...payment, status: "예정", payType: "MID" },
        ],
      }}
      onRefresh={vi.fn()}
    />,
  );
  await screen.findByText("1,533,337 원");
  expect(screen.getByText("납부")).toHaveClass("corporate-tax-ok");
  expect(screen.getByText("확인 필요")).toHaveClass("corporate-tax-warn");
  expect(screen.getByText("예정")).toHaveClass("corporate-tax-info");
  expect(screen.getAllByRole("button", { name: "납부 확인" })).toHaveLength(2);
});
it("dirty 납부 확인은 3선택·취소시 입력초안 유지", async () => {
  render(
    <CorporateTaxPanel
      editor={editor}
      dirty
      onRefresh={vi.fn().mockResolvedValue(editor)}
      onSaveDraft={vi.fn()}
      onDiscardDraft={vi.fn()}
    />,
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "납부 확인" }),
    ).not.toBeDisabled(),
  );
  await userEvent.click(screen.getByRole("button", { name: "납부 확인" }));
  const dialog = screen.getByRole("dialog", {
    name: "저장 전 변경을 먼저 처리해 주세요",
  });
  expect(
    within(dialog).getByRole("button", { name: "먼저 저장" }),
  ).toBeInTheDocument();
  expect(
    within(dialog).getByRole("button", { name: "변경 버리기" }),
  ).toBeInTheDocument();
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(screen.queryByTestId("tax-payment-dialog")).not.toBeInTheDocument();
});
it("초안 저장실패는 확인창으로 이동하지 않는다", async () => {
  render(
    <CorporateTaxPanel
      editor={editor}
      dirty
      onRefresh={vi.fn().mockResolvedValue(editor)}
      onSaveDraft={vi.fn().mockRejectedValue(new Error("초안 저장 실패"))}
      onDiscardDraft={vi.fn()}
    />,
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "납부 확인" }),
    ).not.toBeDisabled(),
  );
  await userEvent.click(screen.getByRole("button", { name: "납부 확인" }));
  await userEvent.click(screen.getByRole("button", { name: "먼저 저장" }));
  await screen.findAllByText("초안 저장 실패");
  expect(screen.queryByTestId("tax-payment-dialog")).not.toBeInTheDocument();
  expect(api.saveCorporateTaxPaymentConfirmation).not.toHaveBeenCalled();
});
it("초안 버리기는 저장하지 않고 최신행 revision으로 확인창을 연다", async () => {
  const discard = vi.fn(),
    save = vi.fn();
  const fresh = {
    ...editor,
    taxPayments: [{ ...payment, confirmationRevision: "fresh" }],
  };
  render(
    <CorporateTaxPanel
      editor={editor}
      dirty
      onRefresh={vi.fn().mockResolvedValue(fresh)}
      onSaveDraft={save}
      onDiscardDraft={discard}
    />,
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "납부 확인" }),
    ).not.toBeDisabled(),
  );
  await userEvent.click(screen.getByRole("button", { name: "납부 확인" }));
  await userEvent.click(screen.getByRole("button", { name: "변경 버리기" }));
  await screen.findByTestId("tax-payment-dialog");
  await ready();
  expect(discard).toHaveBeenCalledOnce();
  expect(save).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  await waitFor(() =>
    expect(api.saveCorporateTaxPaymentConfirmation).toHaveBeenCalledWith(
      "2025",
      "CNFM",
      expect.objectContaining({ expectedRevision: "fresh" }),
    ),
  );
});
it("먼저 저장 성공 뒤에만 최신 확인창을 연다", async () => {
  const save = vi.fn().mockResolvedValue(undefined),
    discard = vi.fn();
  render(
    <CorporateTaxPanel
      editor={editor}
      dirty
      onRefresh={vi.fn().mockResolvedValue(editor)}
      onSaveDraft={save}
      onDiscardDraft={discard}
    />,
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "납부 확인" }),
    ).not.toBeDisabled(),
  );
  await userEvent.click(screen.getByRole("button", { name: "납부 확인" }));
  await userEvent.click(screen.getByRole("button", { name: "먼저 저장" }));
  await screen.findByTestId("tax-payment-dialog");
  await ready();
  expect(save).toHaveBeenCalledOnce();
  expect(discard).not.toHaveBeenCalled();
  expect(api.saveCorporateTaxPaymentConfirmation).not.toHaveBeenCalled();
});
