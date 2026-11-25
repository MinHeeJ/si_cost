import { ScreenLoading } from "../../shared/ScreenLoading";
import { usePermission } from "../../shared/permission/PermissionBoundary";
import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { DataTable } from "../../shared/DataTable";
import { ErrorBanner, FeedbackNotice, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatDecimal } from "../../shared/format";
import { getMonthClose, type PeriodContext } from "../shared/sharedApi";
import { BillActionsPanel } from "../billsops/BillActionsPanel";
import { ReceiptEntryDialog } from "../receipts/ReceiptEntryDialog";
import { ReceivableDeliveryActions } from "../receivablesops/ReceivableDeliveryActions";
import * as api from "./billsApi";

const protectedBillMessage =
  "청구월 또는 TK 매출월이 마감·이관됐거나 지난 연도입니다. 입금 작업은 입금일이 속한 월을 기준으로 판단합니다.";

export type AmountUnit = "원" | "천원" | "백만원";
export function billMoney(amount: string, unit: AmountUnit = "천원") {
  const scale = unit === "원" ? 0 : unit === "천원" ? 3 : 6;
  const padded = amount.padStart(scale + 1, "0");
  return formatDecimal(
    scale ? `${padded.slice(0, -scale)}.${padded.slice(-scale)}` : padded,
  );
}
export const billStatusNames = {
  PAID: "입금 완료",
  UNPAID: "미수",
  LATE: "연체",
  CNCL: "취소됨",
};
export function BillStatus({ bill }: { bill: api.Bill }) {
  const tone =
    bill.status === "PAID"
      ? "ok"
      : bill.status === "UNPAID"
        ? "warn"
        : bill.status === "LATE"
          ? "bad"
          : "lock";
  return (
    <span
      className={`bill-badge sicost-tone-${tone}`}
      title={bill.cancelReason ?? undefined}
    >
      {billStatusNames[bill.status]}
    </span>
  );
}
export function BillError({
  error,
  retry,
}: {
  error: unknown;
  retry?(): void;
}) {
  return error ? (
    <ErrorBanner
      message={
        error instanceof Error ? error.message : "요청을 처리하지 못했습니다."
      }
      requestId={error instanceof ApiError ? error.body.requestId : undefined}
      onRetry={retry}
    />
  ) : null;
}
export type BillDetailProps = {
  id: string;
  year: number;
  period: PeriodContext;
  amountUnit?: AmountUnit;
  refreshKey?: number;
  onClose(): void;
  onEdit(bill: api.Bill): void;
  onDirtyChange?(dirty: boolean): void;
  onChanged?(): Promise<void>;
  renderActions?(
    detail: api.BillDetail,
    refresh: () => Promise<void>,
  ): ReactNode;
  renderReceiptActions?(
    receipt: api.Receipt,
    detail: api.BillDetail,
    refresh: () => Promise<void>,
  ): ReactNode;
};
export function BillDetailDrawer({
  id,
  year,
  period,
  amountUnit = "천원",
  refreshKey,
  onClose,
  onEdit,
  renderActions,
  renderReceiptActions,
  onDirtyChange,
  onChanged,
}: BillDetailProps) {
  const [detail, setDetail] = useState<api.BillDetail>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true),
    [tab, setTab] = useState("bill"),
    [protectedBill, setProtectedBill] = useState(true);
  const [reload, setReload] = useState(0);
  const [childDirty, setChildDirty] = useState(false);
  const changedDirty = useCallback(
    (dirty: boolean) => {
      setChildDirty(dirty);
      onDirtyChange?.(dirty);
    },
    [onDirtyChange],
  );
  async function refresh() {
    const value = await api.getBill(id, year);
    setDetail(value);
    await onChanged?.();
  }
  useEffect(() => {
    let live = true;
    setLoading(true);
    setError(undefined);
    setProtectedBill(true);
    void api
      .getBill(id, year)
      .then(async (value) => {
        if (!live) return;
        setDetail(value);
        const months = new Set([value.bill.billingMonth]);
        if (value.bill.contractType === "TK")
          months.add(value.bill.billingDate.slice(0, 7));
        const closed = await Promise.all(
          [...months].map(async (month) => {
            if (
              month < period.cashBaseMonth ||
              month.slice(0, 4) < period.currentMonth.slice(0, 4)
            )
              return true;
            const state = await getMonthClose(month, month);
            return (
              state.items.length !== 1 || state.items[0].status !== "작업중"
            );
          }),
        );
        if (live) setProtectedBill(closed.some((v) => v));
      })
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [id, year, period, refreshKey, reload]);
  const [deliveryOpen, setDeliveryOpen] = useState(false);
  const canUpdate = usePermission("UI-13", "UPD");
  const bill = detail?.bill;
  const currentYear = Number(period.currentMonth.slice(0, 4));
  const showsCancelAction =
    !renderActions && canUpdate && year >= currentYear && !bill?.cancelled;
  return (
    <DetailDrawer
      feedback={error != null && <BillError error={error} retry={() => setReload((v) => v + 1)} />}
      hidden={deliveryOpen}
      title="청구 상세"
      label={`청구 상세 ${id}`}
      subtitle={
        <>
          <span>{id}</span>
          {bill && <BillStatus bill={bill} />}
        </>
      }
      onClose={onClose}
      testId="bill-detail-drawer"
      pending={childDirty}
      actions={
        bill && canUpdate && (
          <button
            type="button"
            data-testid="bill-edit"
            disabled={
              loading || childDirty || protectedBill || year < currentYear || bill.cancelled
            }
            onClick={() => onEdit(bill)}
          >
            수정
          </button>
        )
      }
    >
      {loading && <ScreenLoading message="청구 상세를 불러오는 중입니다." />}
      {detail && bill && (
        <>
          <div className="bill-detail-toolbar">
            <SegmentedTabList
              as="div"
              role="tablist"
              aria-label="청구 상세 탭"
              className="bill-tabs"
            >
              <button
                type="button"
                role="tab"
                aria-selected={tab === "bill"}
                data-testid="bill-info-tab"
                onClick={() => setTab("bill")}
              >
                청구 정보
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={tab === "receipts"}
                data-testid="bill-receipts-tab"
                onClick={() => setTab("receipts")}
              >
                입금 내역 ({detail.receipts.total})
              </button>
            </SegmentedTabList>
            <StatusBadge tone={detail.receipts.normalCount > 0 ? "ok" : "neutral"}>
              {`정상 입금 ${detail.receipts.normalCount}건`}
            </StatusBadge>
          </div>
          {renderActions ? (
            renderActions(detail, refresh)
          ) : (
            <BillingDetailActions
              detail={detail}
              year={year}
              period={period}
              protectedBill={protectedBill}
              refresh={refresh}
              onDirtyChange={changedDirty}
            />
          )}
          {protectedBill && !showsCancelAction && (
            <div className="bill-detail-notice">
              <FeedbackNotice tone="info" title="청구 수정 제한">
                {protectedBillMessage}
              </FeedbackNotice>
            </div>
          )}
          {tab === "bill" && bill.taxInvoiceWarning && (
            <div className="bill-detail-notice">
              <FeedbackNotice tone="warn" title="세금계산서 번호 확인">
                {bill.taxInvoiceWarning}
              </FeedbackNotice>
            </div>
          )}
          {tab === "bill" ? (
            <section role="tabpanel" data-testid="bill-info-panel">
              <dl className="bill-detail-fields">
                {[
                  ["프로젝트", bill.projectName],
                  ["고객사", bill.customerName],
                  ["청구월", bill.billingMonth],
                  ["차수", `${bill.order}차`],
                  ["청구일", bill.billingDate],
                  ["입금 예정일", bill.dueDate],
                  [
                    "공급가",
                    `${billMoney(bill.supplyAmount, amountUnit)} ${amountUnit}`,
                  ],
                  ["부가세율", `${bill.vatRate}%`],
                  [
                    "부가세",
                    `${billMoney(bill.vatAmount, amountUnit)} ${amountUnit}`,
                  ],
                  [
                    "합계",
                    `${billMoney(bill.totalAmount, amountUnit)} ${amountUnit}`,
                  ],
                  [
                    "입금합",
                    `${billMoney(bill.receiptAmount, amountUnit)} ${amountUnit}`,
                  ],
                  [
                    "잔액",
                    `${billMoney(bill.unpaidAmount, amountUnit)} ${amountUnit}`,
                  ],
                  ["세금계산서번호", bill.taxInvoiceNo ?? "–"],
                ].map(([label, value]) => (
                  <div
                    key={label}
                    data-testid={`bill-detail-field-${({ 프로젝트: "project", 고객사: "customer", 청구월: "billing-month", 차수: "order", 청구일: "billing-date", "입금 예정일": "due-date", 공급가: "supply-amount", 부가세율: "vat-rate", 부가세: "vat-amount", 합계: "total-amount", 입금합: "receipt-amount", 잔액: "unpaid-amount", 세금계산서번호: "tax-invoice-no" } as Record<string, string>)[label]}`}
                  >
                    <dt>{label}</dt>
                    <dd>{value}</dd>
                  </div>
                ))}
              </dl>
              {bill.cancelled && (
                <p title={bill.cancelReason ?? undefined}>
                  취소자 {bill.cancelUserId ?? "–"} ·{" "}
                  {bill.cancelledAt?.replace("T", " ") ?? "–"} ·{" "}
                  {bill.cancelReason}
                </p>
              )}
            </section>
          ) : (
            <section role="tabpanel" data-testid="bill-receipts-panel">
              <DataTable
                rows={detail.receipts.items}
                rowKey={(r) => r.id}
                empty="입금 내역이 없습니다."
                columns={[
                  { id: "id", label: "입금ID", render: (r) => r.id },
                  { id: "date", label: "입금일", render: (r) => r.date },
                  {
                    id: "amount",
                    label: `입금액 (${amountUnit})`,
                    align: "right",
                    render: (r) => billMoney(r.amount, amountUnit),
                  },
                  {
                    id: "status",
                    label: "상태",
                    render: (r) => (
                      <span
                        title={r.cancelReason ?? undefined}
                        className={`bill-badge sicost-tone-${r.cancelled ? "lock" : "ok"}`}
                      >
                        {r.cancelled ? "취소됨" : "정상"}
                      </span>
                    ),
                  },
                  {
                    id: "cancel",
                    label: "취소 정보",
                    render: (r) =>
                      r.cancelled
                        ? `${r.cancelReason} · ${r.cancelUserId ?? "–"} · ${r.cancelledAt?.replace("T", " ") ?? "–"}`
                        : "–",
                  },
                  {
                    id: "actions",
                    label: "작업",
                    render: (r) =>
                      renderReceiptActions ? (
                        renderReceiptActions(r, detail, refresh)
                      ) : (
                        <BillingDetailActions
                          detail={detail}
                          receipt={r}
                          year={year}
                          period={period}
                          protectedBill={protectedBill}
                          refresh={refresh}
                          onDirtyChange={changedDirty}
                        />
                      ),
                  },
                ]}
              />
            </section>
          )}
          <section className="bill-detail-dunning" aria-label="미수금 알림">
            <div className="bill-detail-dunning-heading">
              <h3>미수금 알림</h3>
              {bill.dunning === null && !bill.cancelled && (
                <StatusBadge tone="neutral">알림 기준 설정 확인 필요</StatusBadge>
              )}
            </div>
            <ReceivableDeliveryActions
              billId={bill.id}
              reminderLabel="미수금 다시 알림"
              deliveryLabel="알림 전달 상태"
              onChanged={refresh}
              onPanelChange={setDeliveryOpen}
            />
          </section>
        </>
      )}
    </DetailDrawer>
  );
}
function BillingDetailActions({
  detail,
  receipt,
  year,
  period,
  protectedBill,
  refresh,
  onDirtyChange,
}: {
  detail: api.BillDetail;
  receipt?: api.Receipt;
  year: number;
  period: PeriodContext;
  protectedBill: boolean;
  refresh(): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [open, setOpen] = useState(false);
  const canRegister = usePermission("UI-13", "REG");
  const canUpdate = usePermission("UI-13", "UPD");
  const currentYear = Number(period.currentMonth.slice(0, 4));
  const allowed =
    (receipt ? canUpdate : canRegister) &&
    year >= currentYear &&
    (receipt
      ? !receipt.cancelled
      : !detail.bill.cancelled && detail.bill.status !== "PAID");
  const receiptButton = allowed && (
    <button
      type="button"
      className={receipt ? undefined : "sicost-primary"}
      data-testid={
        receipt
          ? `bill-receipt-cancel-${receipt.id}`
          : "bill-receipt-create"
      }
      onClick={() => setOpen(true)}
    >
      {receipt ? "입금 취소" : "입금 등록"}
    </button>
  );
  return (
    <>
      {!receipt && (canRegister || canUpdate) ? (
        <BillActionsPanel
          bill={detail.bill}
          year={year}
          currentYear={currentYear}
          normalReceiptCount={detail.receipts.normalCount}
          cancellationBlockedReason={
            protectedBill ? protectedBillMessage : undefined
          }
          onChanged={refresh}
          onDirtyChange={onDirtyChange}
        >
          {receiptButton}
        </BillActionsPanel>
      ) : (
        receiptButton
      )}
      {open && allowed && (
        <ReceiptEntryDialog
          bill={detail.bill}
          receipt={receipt}
          year={year}
          period={period}
          onClose={() => setOpen(false)}
          onSaved={refresh}
          onDirtyChange={onDirtyChange}
        />
      )}
    </>
  );
}
