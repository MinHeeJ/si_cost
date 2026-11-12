import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ReceiptDetailDrawer } from "./ReceiptDetailDrawer";
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
  receiptAmount: "400",
  unpaidAmount: "700",
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
  quickActions: { copyAllowed: false, nextMonthAllowed: false, reason: "종료" },
  revision: "before",
};
const row: api.Receipt = {
  id: 7,
  billId: bill.id,
  date: "2026-07-01",
  amount: "400",
  cancelled: false,
  cancelReason: null,
  cancelUserId: null,
  cancelledAt: null,
};
const data: api.BillReceipts = {
  items: [
    row,
    {
      ...row,
      id: 6,
      cancelled: true,
      cancelReason: "오입금",
      cancelUserId: "admin",
      cancelledAt: "2026-07-01T09:10:00.123456",
    },
  ],
  total: 2,
  normalCount: 1,
  revision: "before",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getReceiptBill).mockResolvedValue({
    bill,
    receipts: data,
    revision: "before",
  });
  vi.mocked(api.getBillReceipts).mockResolvedValue(data);
  vi.mocked(shared.getPeriodContext).mockResolvedValue({
    currentDate: "2026-07-01",
    currentMonth: "2026-07",
    cashBaseMonth: "2025-01",
    timezone: "Asia/Seoul",
    monthClose: { month: "2026-07", status: "작업중" },
  });
  vi.mocked(shared.getMonthClose).mockImplementation(async (from) => ({
    items: [{ month: from, status: "작업중" }],
  }));
});
function draw(year = 2026) {
  render(
    <ReceiptDetailDrawer billId={bill.id} year={year} onClose={vi.fn()} />,
  );
}
it("청구 상세 실제 필드·번호 경고·취소포함 총건수·정상건수를 구별한다", async () => {
  draw();
  await screen.findByText("종료 프로젝트");
  expect(screen.getByText("번호 미입력")).toHaveClass("sicost-tone-warn");
  expect(screen.getByText("미수")).toHaveClass("sicost-tone-warn");
  expect(screen.getByText("미수 알림 기준 설정 사용 불가")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("tab", { name: "입금 내역 (2)" }));
  expect(screen.getByText("정상 1건 · 전체 2건")).toBeVisible();
  expect(screen.getByText(/오입금/)).toBeInTheDocument();
  expect(screen.getByText("취소됨")).toHaveClass("sicost-tone-lock");
  expect(screen.getAllByRole("button", { name: "입금 취소" })).toHaveLength(1);
  expect(api.getBillReceipts).toHaveBeenCalledWith(bill.id, 2026);
});
it("END 기존 청구는 입금 버튼과 오늘 기본값을 제공한다", async () => {
  draw();
  await screen.findByText("종료 프로젝트");
  await userEvent.click(screen.getByRole("button", { name: "입금 등록" }));
  await screen.findByLabelText("입금일 *");
  expect(screen.getByLabelText("입금일 *")).toHaveValue("2026-07-01");
});
it.each(["PAID", "CNCL"] as const)(
  "%s 청구는 신규입금 버튼을 숨긴다",
  async (status) => {
    vi.mocked(api.getReceiptBill).mockResolvedValue({
      bill: { ...bill, status, cancelled: status === "CNCL" },
      receipts: data,
      revision: "before",
    });
    draw();
    await screen.findByText("종료 프로젝트");
    expect(
      screen.queryByRole("button", { name: "입금 등록" }),
    ).not.toBeInTheDocument();
  },
);
it("지난 연도는 조회하되 입금·취소 버튼을 숨긴다", async () => {
  draw(2025);
  await screen.findByText("종료 프로젝트");
  expect(
    screen.queryByRole("button", { name: "입금 등록" }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("tab", { name: "입금 내역 (2)" }));
  expect(
    screen.queryByRole("button", { name: "입금 취소" }),
  ).not.toBeInTheDocument();
  expect(api.getBillReceipts).toHaveBeenCalledWith(bill.id, 2025);
});
it("입금월 마감이면 개별취소만 잠근다", async () => {
  vi.mocked(shared.getMonthClose).mockResolvedValue({
    items: [{ month: "2026-07", status: "마감" }],
  });
  draw();
  await screen.findByText("종료 프로젝트");
  expect(screen.getByRole("button", { name: "입금 등록" })).not.toBeDisabled();
  await userEvent.click(screen.getByRole("tab", { name: "입금 내역 (2)" }));
  expect(screen.getByRole("button", { name: "입금 취소" })).toBeDisabled();
});
it("빈 실제 목록은 가상행 없이 빈 안내를 보인다", async () => {
  vi.mocked(api.getBillReceipts).mockResolvedValue({
    items: [],
    total: 0,
    normalCount: 0,
    revision: "before",
  });
  draw();
  await screen.findByText("종료 프로젝트");
  await userEvent.click(screen.getByRole("tab", { name: "입금 내역 (0)" }));
  expect(screen.getByText("등록된 입금 내역이 없습니다.")).toBeVisible();
  expect(screen.queryByText("오입금")).not.toBeInTheDocument();
});
it("실패한 조회는 재조회하고 쓰기를 보내지 않는다", async () => {
  vi.mocked(api.getBillReceipts).mockRejectedValueOnce(new Error("조회 실패"));
  draw();
  await screen.findByText("조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("종료 프로젝트");
  expect(api.createBillReceipt).not.toHaveBeenCalled();
});
it("상세와 입금 revision 불일치면 혼합된 값을 표시하지 않는다", async () => {
  vi.mocked(api.getBillReceipts).mockResolvedValue({
    ...data,
    revision: "changed",
  });
  draw();
  await screen.findByText(/조회 중 청구가 변경/);
  expect(screen.queryByText("종료 프로젝트")).not.toBeInTheDocument();
  expect(api.createBillReceipt).not.toHaveBeenCalled();
});
it("최신 취소 포함 내역에서 선택한 RCPT ID를 취소창으로 전달한다", async () => {
  draw();
  await screen.findByText("종료 프로젝트");
  await userEvent.click(screen.getByRole("tab", { name: "입금 내역 (2)" }));
  await userEvent.click(screen.getByRole("button", { name: "입금 취소" }));
  await waitFor(() =>
    expect(
      screen.getByText("입금 7 / 2026-07-01 / 400 원"),
    ).toBeInTheDocument(),
  );
});
