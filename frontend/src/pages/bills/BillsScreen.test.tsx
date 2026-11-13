import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { BillFormDialog } from "./BillFormDialog";
import { BillsScreen } from "./BillsScreen";
import { BillDetailDrawer } from "./BillDetailDrawer";
import * as api from "./billsApi";
import * as shared from "../shared/sharedApi";
import * as projects from "../projects/projectsApi";
import * as reviews from "../apiops/billingReviewApi";
import * as batch from "../receiptsops/receiptBatchApi";
vi.mock("./billsApi");
vi.mock("../shared/sharedApi");
vi.mock("../projects/projectsApi");
vi.mock("../apiops/billingReviewApi");
vi.mock("../receiptsops/receiptBatchApi");
const period: shared.PeriodContext = {
  currentDate: "2026-07-15",
  currentMonth: "2026-07",
  cashBaseMonth: "2025-01",
  timezone: "Asia/Seoul",
  monthClose: { month: "2026-07", status: "작업중" },
};
const bill: api.Bill = {
  id: "B-202607-001",
  projectId: "P-TEST",
  projectName: "청구 시험 프로젝트",
  customerName: "시험 고객",
  contractType: "MO",
  order: 1,
  billingMonth: "2026-07",
  billingDate: "2026-07-15",
  supplyAmount: "101",
  vatRate: "10",
  vatAmount: "10",
  totalAmount: "111",
  receiptAmount: "0",
  unpaidAmount: "111",
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
  quickActions: {
    copyAllowed: false,
    nextMonthAllowed: false,
    reason: "최신 청구만",
  },
  revision: "before",
};
const totals = {
  supplyAmount: "101",
  vatAmount: "10",
  totalAmount: "111",
  receiptAmount: "0",
  unpaidAmount: "111",
};
const page: api.BillsPage = {
  items: [bill],
  total: 1,
  page: 1,
  size: 50,
  totals,
};
const summary: api.Summary = {
  year: 2026,
  includeCarryover: true,
  billed: { amount: "111", count: 1, scope: "선택연도 미취소" },
  currentMonth: { amount: "111", count: 1, scope: "서버이번달" },
  unpaid: { amount: "111", count: 1, scope: "선택연도 및 이월" },
  lateCount: 0,
  lateScope: "선택연도 및 이월",
};
const detail: api.BillDetail = {
  bill,
  revision: "before",
  receipts: { items: [], total: 0, normalCount: 0, revision: "before" },
};
const saved: api.Mutation = {
  changed: true,
  bill: { ...bill, revision: "after" },
  revision: "after",
  warnings: [],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(reviews.getBillingChecks).mockResolvedValue({
    month: period.currentMonth,
    availableMonths: [period.currentMonth],
    defaultMonth: period.currentMonth,
    includesPlanned: true,
    summary: {
      projectCount: 0,
      matchedCount: 0,
      missingCount: 0,
      differenceCount: 0,
      missingSupplyAmount: "0",
      tabCount: 0,
    },
    items: [],
    total: 0,
    page: 1,
    size: 50,
    warnings: [],
  });
  vi.mocked(reviews.getReceivables).mockResolvedValue({
    asOfDate: period.currentDate,
    items: [],
    total: 0,
    tabCount: 0,
    page: 1,
    size: 50,
    cards: [],
  });
  vi.mocked(batch.loadReceiptBatchSelection).mockResolvedValue({
    items: [],
    total: 0,
    excluded: [],
    excludedCount: 0,
    canSave: false,
  });
  vi.mocked(shared.getPeriodContext).mockResolvedValue(period);
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(shared.getMonthClose).mockImplementation(async (from) => ({
    items: [{ month: from, status: "작업중" }],
  }));
  vi.mocked(shared.getSharedParameter).mockResolvedValue({
    key: "VAT_RT",
    value: "10.0000",
    unitNm: "%",
    effectiveMonth: "2026-01",
  });
  vi.mocked(api.getBills).mockResolvedValue(page);
  vi.mocked(api.getSummary).mockResolvedValue(summary);
  vi.mocked(api.getBill).mockResolvedValue(detail);
  vi.mocked(api.getProjectOptions).mockResolvedValue({
    items: [
      {
        id: "P-TEST",
        name: "청구 시험 프로젝트",
        customerName: "시험 고객",
        contractType: "MO",
        status: "ING",
      },
    ],
  });
  vi.mocked(api.getEstimate).mockImplementation(async (projectId, month) => ({
    projectId,
    month,
    supplyAmount: "101",
    vatRate: "10",
    defaultBillingDate: "2026-07-15",
    defaultDueDate: `${month === "2026-07" ? "2026-08" : "2026-09"}-25`,
    sources: [],
    warnings: [],
  }));
  vi.mocked(api.createBill).mockResolvedValue(saved);
  vi.mocked(api.putBill).mockResolvedValue(saved);
});
function form(edit = false) {
  const onSaved = vi.fn().mockResolvedValue(undefined),
    onClose = vi.fn(),
    onDirtyChange = vi.fn();
  render(
    <BillFormDialog
      year={2026}
      period={period}
      bill={edit ? bill : undefined}
      onSaved={onSaved}
      onClose={onClose}
      onDirtyChange={onDirtyChange}
    />,
  );
  return { onSaved, onClose, onDirtyChange };
}
async function choose() {
  await screen.findByRole("option", { name: "청구 시험 프로젝트 · 시험 고객" });
  fireEvent.change(screen.getByLabelText("프로젝트 *"), {
    target: { value: "P-TEST" },
  });
  await waitFor(() =>
    expect(screen.getByLabelText("공급가 (원) *")).toHaveValue("101"),
  );
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "등록" })).not.toBeDisabled(),
  );
}
it("목록은 13개 열·이월·취소 원금과 전체필터 합계를 보인다", async () => {
  vi.mocked(api.getBills).mockResolvedValue({
    ...page,
    items: [
      {
        ...bill,
        cancelled: true,
        status: "CNCL",
        carryover: true,
        cancelReason: "시험 취소",
        unpaidAmount: "0",
      },
    ],
  });
  render(<BillsScreen />);
  await screen.findByRole("button", { name: bill.id }, { timeout: 5000 });
  expect(screen.getAllByRole("columnheader")).toHaveLength(14);
  expect(
    screen.getAllByText("취소됨").some((n) => n.title === "시험 취소"),
  ).toBe(true);
  expect(screen.getByText(/필터전체 미취소/)).toHaveTextContent(
    "현재 페이지 합계 아님",
  );
  expect(
    screen.getByRole("checkbox", { name: "이월 미수 포함" }),
  ).toBeChecked();
});
it("조건 변경은 page1·선택해제이며 카드에는 목록조건을 보내지 않는다", async () => {
  render(<BillsScreen />);
  await screen.findByRole("button", { name: bill.id });
  fireEvent.change(screen.getByLabelText("상태"), {
    target: { value: "CNCL" },
  });
  await waitFor(() =>
    expect(api.getBills).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, status: "CNCL" }),
    ),
  );
  expect(api.getSummary).toHaveBeenCalledTimes(1);
  expect(api.getSummary).toHaveBeenCalledWith(2026, true);
  fireEvent.change(screen.getByLabelText("청구월"), {
    target: { value: "2026-08" },
  });
  await waitFor(() =>
    expect(api.getBills).toHaveBeenLastCalledWith(
      expect.objectContaining({ month: "2026-08", page: 1 }),
    ),
  );
});
it("선택 바는 행을 고른 때만 나타나고 일괄 입금·조회전체 선택·선택 해제를 거기서 한다", async () => {
  vi.mocked(api.getBills).mockResolvedValue({ ...page, total: 40 });
  render(<BillsScreen />);
  await screen.findByRole("button", { name: bill.id });
  expect(screen.queryByText(/선택 0건/)).not.toBeInTheDocument();
  expect(screen.queryByTestId("bill-selection-bar")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "일괄 입금" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "조회전체 선택" }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getAllByRole("checkbox")[1]);
  const bar = screen.getByTestId("bill-selection-bar");
  expect(bar).toHaveTextContent("선택 1건");
  expect(
    within(bar).getByRole("button", { name: "일괄 입금" }),
  ).toBeEnabled();
  await userEvent.click(within(bar).getByRole("button", { name: "조회전체 선택" }));
  expect(bar).toHaveTextContent("조회된 전체 40건 선택");
  expect(
    within(bar).queryByRole("button", { name: "조회전체 선택" }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("bill-selection-clear"));
  expect(screen.queryByTestId("bill-selection-bar")).not.toBeInTheDocument();
});
it("일괄 입금은 고른 청구로, 조회전체 선택이면 조건 전체로 입금 창을 준비한다", async () => {
  vi.mocked(api.getBills).mockResolvedValue({ ...page, total: 40 });
  render(<BillsScreen />);
  await screen.findByRole("button", { name: bill.id });
  await userEvent.click(screen.getAllByRole("checkbox")[1]);
  await userEvent.click(screen.getByTestId("receipt-batch-open"));
  await waitFor(() =>
    expect(batch.loadReceiptBatchSelection).toHaveBeenCalledTimes(1),
  );
  expect(vi.mocked(batch.loadReceiptBatchSelection).mock.calls[0][1]).toEqual([
    bill.id,
  ]);
  await userEvent.click(
    within(
      screen
        .getByTestId("receipt-batch-dialog")
        .closest('[role="dialog"]') as HTMLElement,
    ).getByRole("button", { name: "취소" }),
  );
  await userEvent.click(screen.getByTestId("bill-select-all"));
  await userEvent.click(screen.getByTestId("receipt-batch-open"));
  await waitFor(() =>
    expect(batch.loadReceiptBatchSelection).toHaveBeenCalledTimes(2),
  );
  expect(
    vi.mocked(batch.loadReceiptBatchSelection).mock.calls[1][1],
  ).toBeUndefined();
});
it("청구 등록은 흰 글자의 파란 주 버튼이다", async () => {
  render(<BillsScreen />);
  await screen.findByRole("button", { name: bill.id });
  expect(screen.getByTestId("bill-create")).toHaveClass("sicost-primary");
});
it("조회 버튼은 필터 오른쪽 끝의 [초기화][조회]로 공통 필터 바를 쓴다", async () => {
  render(<BillsScreen />);
  await screen.findByRole("button", { name: bill.id });
  const actions = screen
    .getByTestId("bill-search")
    .closest(".sicost-filterbar-actions");
  expect(actions).not.toBeNull();
  expect(
    Array.from(actions!.querySelectorAll("button")).map((b) => b.textContent),
  ).toEqual(["초기화", "조회"]);
});
it("청구 목록 다운로드는 현재 조건·정렬 전체를 view=BILLS로 요청한다", async () => {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(new Blob(["x"]), {
      status: 200,
      headers: {
        "Content-Type":
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Content-Disposition":
          "attachment; filename*=UTF-8''%EC%B2%AD%EA%B5%AC_20261112_220000.xlsx",
      },
    }),
  );
  vi.stubGlobal("fetch", fetchMock);
  URL.createObjectURL = vi.fn(() => "blob:x");
  URL.revokeObjectURL = vi.fn();
  render(<BillsScreen />);
  await screen.findByRole("button", { name: bill.id });
  fireEvent.change(screen.getByLabelText("상태"), {
    target: { value: "UNPAID" },
  });
  await waitFor(() =>
    expect(api.getBills).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: "UNPAID" }),
    ),
  );
  await userEvent.click(screen.getByTestId("billing-review-download-bills"));
  await waitFor(() => expect(fetchMock).toHaveBeenCalled());
  const [url, init] = fetchMock.mock.calls[0];
  const body = JSON.parse(init.body as string);
  expect(url).toBe("/api/exports/master-data");
  expect(body.view).toBe("BILLS");
  expect(body.filters).toMatchObject({
    year: 2026,
    status: "UNPAID",
    includeCarryover: true,
  });
  expect(body.columns).toHaveLength(12);
  expect(body).not.toHaveProperty("page");
  vi.unstubAllGlobals();
});
it("목록 로딩·빈 결과·조회 실패는 쓰기 없이 GET 재조회한다", async () => {
  vi.mocked(api.getBills)
    .mockRejectedValueOnce(new Error("청구 조회 실패"))
    .mockResolvedValue({ ...page, items: [], total: 0 });
  render(<BillsScreen />);
  await screen.findByText("청구 조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findAllByText("조회된 청구가 없습니다.");
  expect(api.createBill).not.toHaveBeenCalled();
});
it("다른 화면 기간이벤트는 무시하고 지난해는 조회·계획연도는 등록을 허용한다", async () => {
  render(<BillsScreen />);
  await screen.findByRole("button", { name: bill.id });
  act(() => {
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-12",
          value: { type: "YEAR", value: "2025" },
          context: period,
        },
      }),
    );
  });
  expect(screen.getByRole("button", { name: "청구 등록" })).not.toBeDisabled();
  act(() => {
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-13",
          value: { type: "YEAR", value: "2025" },
          context: period,
        },
      }),
    );
  });
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "청구 등록" })).toBeDisabled(),
  );
  expect(
    screen.queryByRole("checkbox", { name: "이월 미수 포함" }),
  ).not.toBeInTheDocument();
  act(() => {
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-13",
          value: { type: "YEAR", value: "2027" },
          context: period,
        },
      }),
    );
  });
  await screen.findByText("계획 · 내년 청구 목록");
  expect(screen.getByRole("button", { name: "청구 등록" })).not.toBeDisabled();
});
it("신규는 서버기본과 명시금액을 제출하고 폼에서 계산값을 보이지 않는다", async () => {
  const props = form();
  await choose();
  fireEvent.change(screen.getByLabelText("공급가 (원) *"), {
    target: { value: "1,001 원" },
  });
  expect(screen.getByLabelText("청구일 *")).toHaveValue(period.currentDate);
  expect(screen.getByLabelText("입금 예정일 *")).toHaveValue("2026-08-25");
  expect(screen.queryByLabelText(/^부가세금액/)).not.toBeInTheDocument();
  expect(screen.queryByText(/^합계/)).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await waitFor(() => expect(props.onSaved).toHaveBeenCalledWith(saved));
  expect(api.createBill).toHaveBeenCalledWith({
    year: 2026,
    projectId: "P-TEST",
    billingMonth: "2026-07",
    supplyAmount: "1001",
    vatRate: "10",
    billingDate: "2026-07-15",
    dueDate: "2026-08-25",
    taxInvoiceNo: null,
  });
});
it("수정은 식별자를 표시만 하고 5개 필드·revision만 제출한다", async () => {
  form(true);
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
  expect(screen.queryByLabelText("프로젝트 *")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("청구월 *")).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("세금계산서 번호"), {
    target: { value: "INV-ONLY" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.putBill).toHaveBeenCalledWith(bill.id, {
      year: 2026,
      expectedRevision: "before",
      supplyAmount: "101",
      vatRate: "10",
      billingDate: "2026-07-15",
      dueDate: "2026-08-25",
      taxInvoiceNo: "INV-ONLY",
    }),
  );
  expect(api.getEstimate).not.toHaveBeenCalled();
});
it.each(["0", "-1", "1.1", "1e3", "1000000000000000"])(
  "공급가 %s는 보정없이 필드오류와 입력을 보존한다",
  async (value) => {
    form();
    await choose();
    fireEvent.change(screen.getByLabelText("공급가 (원) *"), {
      target: { value },
    });
    await userEvent.click(screen.getByRole("button", { name: "등록" }));
    expect(screen.getByLabelText("공급가 (원) *")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByLabelText("공급가 (원) *")).toHaveValue(value);
    expect(api.createBill).not.toHaveBeenCalled();
  },
);
it("번호31자·세율과 예정일 검증을 표시한다", async () => {
  form();
  await choose();
  fireEvent.change(screen.getByLabelText("세금계산서 번호"), {
    target: { value: "가".repeat(31) },
  });
  fireEvent.change(screen.getByLabelText("부가세율 (%) *"), {
    target: { value: "10.001" },
  });
  fireEvent.change(screen.getByLabelText("입금 예정일 *"), {
    target: { value: "2026-07-14" },
  });
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  expect(screen.getByLabelText("세금계산서 번호")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(screen.getByLabelText("부가세율 (%) *")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(screen.getByLabelText("입금 예정일 *")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.createBill).not.toHaveBeenCalled();
});
it("월변경은 편집금액 교체확인과 수기예정일 변경 안내를 한다", async () => {
  form();
  await choose();
  fireEvent.change(screen.getByLabelText("공급가 (원) *"), {
    target: { value: "900" },
  });
  fireEvent.change(screen.getByLabelText("입금 예정일 *"), {
    target: { value: "2026-10-01" },
  });
  fireEvent.change(screen.getByLabelText("청구월 *"), {
    target: { value: "2026-08" },
  });
  expect(
    screen.getByRole("dialog", { name: "입력한 공급가를 변경하시겠습니까?" }),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("공급가 (원) *")).toHaveValue("900");
  await userEvent.click(
    screen.getByRole("button", { name: "기본값 다시 읽기" }),
  );
  await waitFor(() =>
    expect(screen.getByLabelText("입금 예정일 *")).toHaveValue("2026-09-25"),
  );
  await screen.findByText(/수기 입금예정일/);
});
it("마감월은 저장을 차단한다", async () => {
  vi.mocked(shared.getMonthClose).mockResolvedValue({
    items: [{ month: "2026-07", status: "마감" }],
  });
  form(true);
  await screen.findByText(/마감·이관·지난해 업무월/);
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
});
it("중복 첫응답은 입력 유지와 목록·토큰 확인 후 두번째 요청만 보낸다", async () => {
  vi.mocked(api.createBill)
    .mockRejectedValueOnce(
      new ApiError(409, {
        success: false,
        meta: {},
        code: "DUPLICATE_BILL_CONFIRMATION_REQUIRED",
        message: "중복 확인",
        requestId: "duplicate",
        details: {
          billing: {
            existingBills: [{ id: bill.id, order: 1 }],
            duplicateToken: "token",
          },
        } as never,
      }),
    )
    .mockResolvedValue(saved);
  form();
  await choose();
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  const dialog = await screen.findByRole("dialog", {
    name: "같은 달 청구 확인",
  });
  expect(dialog).toHaveTextContent(bill.id);
  await userEvent.click(within(dialog).getByRole("button", { name: "등록" }));
  await waitFor(() => expect(api.createBill).toHaveBeenCalledTimes(2));
  expect(api.createBill).toHaveBeenLastCalledWith(
    expect.objectContaining({
      confirmDuplicate: true,
      duplicateToken: "token",
      supplyAmount: "101",
    }),
  );
});
it("stale 오류는 자동 재전송없이 입력·field·requestId를 보존한다", async () => {
  vi.mocked(api.putBill).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다른 사용자 변경",
      requestId: "bill-stale",
      field: "taxInvoiceNo",
    }),
  );
  form(true);
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/자동 재저장하지 않습니다/);
  expect(screen.getByLabelText("세금계산서 번호")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  expect(api.putBill).toHaveBeenCalledTimes(1);
});
it("dirty 취소는 확인 전 입력을 유지한다", async () => {
  const props = form();
  await choose();
  fireEvent.change(screen.getByLabelText("세금계산서 번호"), {
    target: { value: "EDIT" },
  });
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("세금계산서 번호")).toHaveValue("EDIT");
  expect(props.onClose).not.toHaveBeenCalled();
});
it("상세는 취소포함 입금의 전체·정상건수와 번호경고를 구분한다", async () => {
  vi.mocked(api.getBill).mockResolvedValue({
    ...detail,
    receipts: {
      items: [
        {
          id: 1,
          billId: bill.id,
          date: "2026-07-15",
          amount: "11",
          cancelled: true,
          cancelReason: "오입금",
          cancelUserId: "admin",
          cancelledAt: "2026-07-15T09:00:00",
        },
      ],
      total: 1,
      normalCount: 0,
      revision: "before",
    },
  });
  render(
    <BillDetailDrawer
      id={bill.id}
      year={2026}
      period={period}
      onClose={vi.fn()}
      onEdit={vi.fn()}
    />,
  );
  await screen.findByText("번호 미입력");
  await userEvent.click(screen.getByRole("tab", { name: "입금 내역 (1)" }));
  expect(screen.getByText("정상 입금 0건")).toBeInTheDocument();
  expect(screen.getByText(/오입금 · admin/)).toBeInTheDocument();
});
it("기본 업무 호스트가 기존 점검·미수 패널과 다건 선택을 연결한다", async () => {
  render(<BillsScreen />);
  await screen.findByRole("button", { name: bill.id });
  await userEvent.click(screen.getAllByRole("checkbox")[1]);
  await userEvent.click(screen.getByTestId("receipt-batch-open"));
  await waitFor(() =>
    expect(batch.loadReceiptBatchSelection).toHaveBeenCalled(),
  );
  const filters = vi.mocked(batch.loadReceiptBatchSelection).mock.calls[0][0];
  expect(filters).toEqual({ year: 2026, includeCarryover: true });
  expect(filters).not.toHaveProperty("page");
  expect(filters).not.toHaveProperty("size");
  await userEvent.click(
    within(
      screen
        .getByTestId("receipt-batch-dialog")
        .closest('[role="dialog"]') as HTMLElement,
    ).getByRole("button", { name: "취소" }),
  );
  await userEvent.click(screen.getByTestId("billing-checks-tab"));
  expect(screen.getByTestId("billing-checks-panel")).toBeVisible();
  expect(screen.getByTestId("bills-list-panel")).not.toBeVisible();
  await userEvent.click(screen.getByTestId("billing-receivables-tab"));
  expect(screen.getByTestId("receivables-panel")).toBeVisible();
  await userEvent.click(screen.getByTestId("billing-list-tab"));
  expect(screen.getByTestId("bills-list-panel")).toBeVisible();
});
it("저장성공 뒤 조회 실패는 재저장하지 않는다", async () => {
  render(<BillsScreen />);
  await screen.findByRole("button", { name: bill.id });
  await userEvent.click(screen.getByRole("button", { name: "청구 등록" }));
  await choose();
  vi.mocked(api.getBills).mockRejectedValue(new Error("후속 조회 실패"));
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await screen.findByText(/저장됐지만 다시 불러오지 못했습니다/);
  expect(api.createBill).toHaveBeenCalledTimes(1);
});
