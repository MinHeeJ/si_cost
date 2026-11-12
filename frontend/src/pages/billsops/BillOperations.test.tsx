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
  BillActionsPanel,
  type BillActionsPanelProps,
} from "./BillActionsPanel";
import { BillCopyDialog } from "./BillCopyDialog";
import {
  BillSummaryCards,
  BillEstimatePanel,
  billAmount,
} from "./BillSummaryCards";
import { BillSelectionPanel } from "./BillSelectionPanel";
import * as api from "./billOperationsApi";
vi.mock("./billOperationsApi");
const bill: api.BillReference = {
  id: "B-202607-001",
  projectId: "P",
  projectName: "시험 프로젝트",
  customerName: "시험 고객사",
  contractType: "MO",
  order: 1,
  billingMonth: "2026-07",
  billingDate: "2026-07-15",
  supplyAmount: "1001",
  vatRate: "7.25",
  vatAmount: "72",
  totalAmount: "1073",
  receiptAmount: "0",
  unpaidAmount: "1073",
  dueDate: "2026-08-25",
  taxInvoiceNo: null,
  taxInvoiceWarning: "번호 미입력",
  status: "UNPAID",
  cancelled: false,
  cancelReason: null,
  cancelUserId: null,
  cancelledAt: null,
  carryover: false,
  dunning: null,
  quickActions: { copyAllowed: true, nextMonthAllowed: true, reason: null },
  revision: "before",
};
const saved: api.BillMutationResult = {
  changed: true,
  bill: { ...bill, id: "B-202608-001", billingMonth: "2026-08" },
  revision: "after",
  warnings: ["번호 미입력"],
};
const draft: api.BillCopyDraft = {
  sourceBillId: bill.id,
  expectedRevision: "before",
  year: 2026,
  projectId: "P",
  billingMonth: "2026-08",
  supplyAmount: "1001",
  vatRate: "7.25",
  billingDate: "2026-07-15",
  dueDate: "2026-09-25",
  taxInvoiceNo: null,
  warnings: [],
};
const summary: api.BillsSummary = {
  year: 2026,
  includeCarryover: true,
  billed: { amount: "1073", count: 1, scope: "선택연도 미취소" },
  currentMonth: { amount: "1073", count: 1, scope: "이번달" },
  unpaid: { amount: "1073", count: 1, scope: "선택연도 및 이월" },
  lateCount: 0,
  lateScope: "선택연도 및 이월",
};
const estimate: api.BillEstimate = {
  projectId: "P",
  month: "2026-08",
  supplyAmount: "0",
  vatRate: null,
  defaultBillingDate: "2026-07-15",
  defaultDueDate: "2026-09-25",
  sources: [],
  warnings: ["유효한 부가세 설정이 없습니다."],
};
const selection: api.BillSelection = {
  items: [
    {
      billId: bill.id,
      unpaidAmount: bill.unpaidAmount,
      expectedRevision: bill.revision,
    },
  ],
  total: 1,
  excluded: [{ billId: "B-202607-002", reason: "취소된 청구입니다." }],
  excludedCount: 1,
  canSave: true,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.createNextMonthBill).mockResolvedValue(saved);
  vi.mocked(api.getBillCopyDraft).mockResolvedValue(draft);
  vi.mocked(api.saveBillCancellation).mockResolvedValue({
    ...saved,
    bill: { ...bill, cancelled: true, unpaidAmount: "0" },
  });
  vi.mocked(api.createCopiedBill).mockResolvedValue(saved);
  vi.mocked(api.getBillsSummary).mockResolvedValue(summary);
  vi.mocked(api.getBillEstimate).mockResolvedValue(estimate);
  vi.mocked(api.getBillSelection).mockResolvedValue(selection);
});
function draw(overrides: Partial<BillActionsPanelProps> = {}) {
  const props: BillActionsPanelProps = {
    bill,
    year: 2026,
    currentYear: 2026,
    normalReceiptCount: 0,
    onChanged: vi.fn(),
    onDirtyChange: vi.fn(),
    ...overrides,
  };
  render(<BillActionsPanel {...props} />);
  return props;
}
it("창 없이 다음달 청구에 선택 ID/year/revision을 보내고 갱신한다", async () => {
  const props = draw();
  await userEvent.click(screen.getByRole("button", { name: "다음 달 청구" }));
  await waitFor(() => expect(props.onChanged).toHaveBeenCalledWith(saved));
  expect(api.createNextMonthBill).toHaveBeenCalledWith(bill.id, {
    year: 2026,
    expectedRevision: "before",
  });
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("TK에는 다음달 버튼이 없다", () => {
  draw({ bill: { ...bill, contractType: "TK" } });
  expect(
    screen.queryByRole("button", { name: "다음 달 청구" }),
  ).not.toBeInTheDocument();
});
it("비최신 행은 빠른청구가 없다", () => {
  draw({
    bill: {
      ...bill,
      quickActions: {
        copyAllowed: false,
        nextMonthAllowed: false,
        reason: "비최신",
      },
    },
  });
  expect(
    screen.queryByRole("button", { name: "고쳐서 청구" }),
  ).not.toBeInTheDocument();
});
it("지난 연도는 쓰기를 숨긴다", () => {
  draw({ year: 2025 });
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
  expect(screen.getByText(/지난 연도/)).toBeInTheDocument();
});
it("내년 문맥 쓰기는 추가로 금지하지 않는다", () => {
  draw({ year: 2027 });
  expect(
    screen.getByRole("button", { name: "다음 달 청구" }),
  ).not.toBeDisabled();
});
it("취소 행에는 취소 버튼이 없다", () => {
  draw({ bill: { ...bill, cancelled: true } });
  expect(
    screen.queryByRole("button", { name: "청구 취소" }),
  ).not.toBeInTheDocument();
});
it("정상 입금이 있으면 먼저 입금 취소 안내", () => {
  draw({ normalReceiptCount: 1 });
  expect(screen.getByRole("button", { name: "청구 취소" })).toBeDisabled();
  expect(screen.getByText(/먼저 입금을 취소/)).toBeInTheDocument();
});
it("마감월 취소 잠금을 표시한다", () => {
  draw({ cancellationBlockedReason: "청구월 마감" });
  expect(screen.getByRole("button", { name: "청구 취소" })).toBeDisabled();
  expect(screen.getByText("청구월 마감")).toBeInTheDocument();
});
it("알림 설정이 무효이면 가짜 단계0 대신 설정 확인 안내", () => {
  draw();
  expect(screen.getByText("알림 기준 설정 확인 필요")).toHaveClass(
    "sicost-tone-neutral",
  );
});
it("날짜 오류는 고쳐서 청구 안내·자동재전송0", async () => {
  vi.mocked(api.createNextMonthBill).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "고쳐서 청구에서 날짜를 수정해 주세요.",
      field: "dueDate",
      requestId: "date-error",
    }),
  );
  draw();
  await userEvent.click(screen.getByRole("button", { name: "다음 달 청구" }));
  await screen.findByText(/date-error/);
  expect(api.createNextMonthBill).toHaveBeenCalledTimes(1);
});
it("충돌은 쓰기 버튼을 잠그고 자동 재저장하지 않는다", async () => {
  vi.mocked(api.createNextMonthBill).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "충돌",
      requestId: "stale",
    }),
  );
  draw();
  await userEvent.click(screen.getByRole("button", { name: "다음 달 청구" }));
  await screen.findByText(/자동 재전송하지 않습니다/);
  expect(screen.getByRole("button", { name: "다음 달 청구" })).toBeDisabled();
});
it("저장 후 조회만 실패하면 조회만 다시 시도한다", async () => {
  const props = draw({
    onChanged: vi
      .fn()
      .mockRejectedValueOnce(new Error("조회 실패"))
      .mockResolvedValue(undefined),
  });
  await userEvent.click(screen.getByRole("button", { name: "다음 달 청구" }));
  await screen.findByText(/저장은 완료됐지만/);
  await userEvent.click(screen.getByTestId("bill-action-refresh"));
  await waitFor(() => expect(props.onChanged).toHaveBeenCalledTimes(2));
  expect(api.createNextMonthBill).toHaveBeenCalledTimes(1);
});
it("취소 사유 검증·입력 보존·선택 revision 전달", async () => {
  draw();
  await userEvent.click(screen.getByTestId("bill-cancel-open"));
  const dialog = screen.getByRole("dialog", { name: "청구 취소" });
  await userEvent.click(
    within(dialog).getByRole("button", { name: "청구 취소" }),
  );
  expect(api.saveBillCancellation).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("취소 사유 *"), {
    target: { value: "  실제 사유  " },
  });
  await userEvent.click(
    within(dialog).getByRole("button", { name: "청구 취소" }),
  );
  await waitFor(() =>
    expect(api.saveBillCancellation).toHaveBeenCalledWith(bill.id, {
      year: 2026,
      expectedRevision: "before",
      reason: "실제 사유",
    }),
  );
});
it("사유창 취소의 dirty 확인은 쓰지 않는다", async () => {
  draw();
  await userEvent.click(screen.getByTestId("bill-cancel-open"));
  fireEvent.change(screen.getByLabelText("취소 사유 *"), {
    target: { value: "작성 중" },
  });
  await userEvent.click(screen.getByRole("button", { name: "닫기" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("취소 사유 *")).toHaveValue("작성 중");
  expect(api.saveBillCancellation).not.toHaveBeenCalled();
});
it("서버 복사 초안의 프로젝트·월은 고정이며 원세율을 표시한다", async () => {
  draw();
  await userEvent.click(screen.getByRole("button", { name: "고쳐서 청구" }));
  await screen.findByRole("dialog", { name: /다음월 신규복사/ });
  expect(screen.getByText(/청구월 2026-08/)).toBeInTheDocument();
  expect(screen.getByLabelText("부가세율 (%) *")).toHaveValue("7.25");
  expect(screen.queryByLabelText(/프로젝트/)).not.toBeInTheDocument();
  expect(screen.queryByText(/합계/)).not.toBeInTheDocument();
  expect(screen.queryByLabelText(/부가세금액/)).not.toBeInTheDocument();
});
it("복사 등록은 명시 금액과 원청구 copyContext를 create-only POST로 보낸다", async () => {
  render(
    <BillCopyDialog
      draft={draft}
      projectName="프로젝트"
      onCancel={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  fireEvent.change(screen.getByLabelText("공급가 (원) *"), {
    target: { value: "9,999 원" },
  });
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await waitFor(() =>
    expect(api.createCopiedBill).toHaveBeenCalledWith(
      expect.objectContaining({
        supplyAmount: "9999",
        taxInvoiceNo: null,
        projectId: "P",
        billingMonth: "2026-08",
        copyContext: { sourceBillId: bill.id, expectedRevision: "before" },
      }),
    ),
  );
});
it.each(["0", "-1", "1.5", "1e3", "1000000000000000"])(
  "복사 공급가 %s 거부",
  async (value) => {
    render(
      <BillCopyDialog
        draft={draft}
        projectName="프로젝트"
        onCancel={vi.fn()}
        onChanged={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByLabelText("공급가 (원) *"), {
      target: { value },
    });
    await userEvent.click(screen.getByRole("button", { name: "등록" }));
    expect(screen.getByLabelText("공급가 (원) *")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(api.createCopiedBill).not.toHaveBeenCalled();
  },
);
it.each(["100.01", "10.001", "-1"])("복사 세율 %s 거부", async (value) => {
  render(
    <BillCopyDialog
      draft={draft}
      projectName="프로젝트"
      onCancel={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  fireEvent.change(screen.getByLabelText("부가세율 (%) *"), {
    target: { value },
  });
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  expect(api.createCopiedBill).not.toHaveBeenCalled();
});
it("예정일 역전은 자동 보정하지 않는다", async () => {
  render(
    <BillCopyDialog
      draft={{ ...draft, dueDate: "2026-06-25" }}
      projectName="프로젝트"
      onCancel={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  expect(screen.getByLabelText("입금예정일 *")).toHaveValue("2026-06-25");
  expect(api.createCopiedBill).not.toHaveBeenCalled();
});
it("요약은 목록 필터 없이 연도와 이월만 호출한다", async () => {
  render(<BillSummaryCards year={2026} includeCarryover />);
  await screen.findByText("청구 총액");
  expect(api.getBillsSummary).toHaveBeenCalledWith(2026, true);
  expect(screen.getByTestId("bill-summary-late")).toHaveTextContent("0건");
  expect(billAmount("999999999999999", "백만원")).toBe("999,999,999.999999");
});
it("요약 오류는 GET만 재조회한다", async () => {
  vi.mocked(api.getBillsSummary).mockRejectedValueOnce(new Error("요약 실패"));
  render(<BillSummaryCards year={2026} includeCarryover />);
  await screen.findByRole("alert");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("청구 총액");
  expect(api.getBillsSummary).toHaveBeenCalledTimes(2);
});
it("추정 빈 원천·무효 VAT를 명시하고 가상행을 만들지 않는다", async () => {
  render(<BillEstimatePanel projectId="P" month="2026-08" />);
  await screen.findByText("해당월 유상 투입 자료가 없습니다.");
  expect(
    screen.getByText("유효한 부가세 설정이 없습니다."),
  ).toBeInTheDocument();
  expect(api.getBillEstimate).toHaveBeenCalledWith("P", "2026-08");
});
it("선택은 제외사유·건수와 고정 ID/revision을 다음 입금창에 전달한다", async () => {
  const onPrepared = vi.fn();
  render(
    <BillSelectionPanel filters={{ year: 2026 }} onPrepared={onPrepared} />,
  );
  await screen.findByText("취소된 청구입니다.", { exact: false });
  await userEvent.click(screen.getByRole("button", { name: "입금 입력" }));
  expect(onPrepared).toHaveBeenCalledWith(selection);
  await userEvent.click(screen.getByRole("button", { name: "개별 제외" }));
  await waitFor(() =>
    expect(api.getBillSelection).toHaveBeenLastCalledWith({
      year: 2026,
      excludeIds: [bill.id],
    }),
  );
});
it("선택 대상0은 저장 준비를 막는다", async () => {
  vi.mocked(api.getBillSelection).mockResolvedValue({
    ...selection,
    items: [],
    total: 0,
    canSave: false,
  });
  render(<BillSelectionPanel filters={{ year: 2026 }} />);
  await screen.findByText("입금할 대상이 없습니다.");
  expect(screen.getByRole("button", { name: "입금 입력" })).toBeDisabled();
});
it("선택 조회 실패는 저장 준비를 막는다", async () => {
  vi.mocked(api.getBillSelection).mockRejectedValue(new Error("선택 실패"));
  render(<BillSelectionPanel filters={{ year: 2026 }} />);
  await screen.findByRole("alert");
  expect(screen.getByRole("button", { name: "입금 입력" })).toBeDisabled();
});
