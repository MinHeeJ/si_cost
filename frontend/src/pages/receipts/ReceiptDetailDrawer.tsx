import { useCallback, useEffect, useRef, useState } from "react";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { formatDateTime, formatDecimal } from "../../shared/format";
import {
  getMonthClose,
  getPeriodContext,
  type PeriodContext,
} from "../shared/sharedApi";
import { ReceiptEntryDialog } from "./ReceiptEntryDialog";
import * as api from "./receiptsApi";
import "./receipts.css";

export type ReceiptDetailDrawerProps = {
  billId: string;
  year: number;
  onClose(): void;
  onChanged?(result: api.ReceiptResult): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
  onEdit?(bill: api.Bill): void;
  onCancelBill?(bill: api.Bill): void;
};
export function ReceiptDetailDrawer({
  billId,
  year,
  onClose,
  onChanged,
  onDirtyChange,
  onEdit,
  onCancelBill,
}: ReceiptDetailDrawerProps) {
  const [detail, setDetail] = useState<api.BillDetail>();
  const [period, setPeriod] = useState<PeriodContext>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [tab, setTab] = useState<"bill" | "receipts">("bill");
  const [action, setAction] = useState<"create" | api.Receipt>();
  const [dirty, setDirty] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [blockedMonths, setBlockedMonths] = useState<Set<string>>(new Set());
  const generation = useRef(0);
  const load = useCallback(async () => {
    const run = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const [value, receipts, context] = await Promise.all([
        api.getReceiptBill(billId, year),
        api.getBillReceipts(billId, year),
        getPeriodContext(),
      ]);
      if (value.revision !== receipts.revision)
        throw new Error("조회 중 청구가 변경됐습니다. 다시 조회해 주세요.");
      const months = [
        ...new Set(
          receipts.items
            .map((r) => r.date.slice(0, 7))
            .concat(context.currentMonth),
        ),
      ].sort();
      const closes = await getMonthClose(months[0], months[months.length - 1]);
      if (run !== generation.current) return;
      setDetail({ ...value, receipts });
      setPeriod(context);
      setBlockedMonths(
        new Set(
          months.filter(
            (month) =>
              month < context.cashBaseMonth ||
              month.slice(0, 4) < context.currentMonth.slice(0, 4) ||
              !closes.items.some(
                (c) => c.month === month && c.status === "작업중",
              ),
          ),
        ),
      );
    } catch (e) {
      if (run === generation.current) setError(e);
      throw e;
    } finally {
      if (run === generation.current) setLoading(false);
    }
  }, [billId, year]);
  useEffect(() => {
    void load().catch(() => {});
    return () => {
      generation.current++;
    };
  }, [load]);
  const changedDirty = useCallback(
    (value: boolean) => {
      setDirty(value);
      onDirtyChange?.(value);
    },
    [onDirtyChange],
  );
  const readonly = !period || year < Number(period.currentMonth.slice(0, 4));
  const bill = detail?.bill;
  const locked = readonly || loading || Boolean(error);
  function close() {
    if (action) {
      setLeaving(true);
      return;
    }
    if (dirty) setLeaving(true);
    else onClose();
  }
  const columns: Column<api.Receipt>[] = [
    {
      id: "id",
      label: "입금ID",
      render: (row) => (
        <span data-testid={`receipt-row-${row.id}`}>{row.id}</span>
      ),
    },
    { id: "date", label: "입금일", render: (row) => row.date },
    {
      id: "amount",
      label: "입금액 (원)",
      align: "right",
      render: (row) => formatDecimal(row.amount),
    },
    {
      id: "status",
      label: "상태",
      align: "center",
      render: (row) => (
        <StatusBadge tone={row.cancelled ? "lock" : "ok"}>
          {row.cancelled ? "취소됨" : "정상"}
        </StatusBadge>
      ),
    },
    {
      id: "cancel",
      label: "취소사유·취소자·시각",
      render: (row) =>
        row.cancelled ? (
          <span>
            {row.cancelReason ?? "–"}
            <br />
            {row.cancelUserId ?? "–"} · {formatDateTime(row.cancelledAt)}
          </span>
        ) : (
          "–"
        ),
    },
    {
      id: "action",
      label: "작업",
      render: (row) =>
        !readonly && !row.cancelled ? (
          <button
            type="button"
            data-testid={`receipt-cancel-${row.id}`}
            disabled={locked || blockedMonths.has(row.date.slice(0, 7))}
            onClick={() => setAction(row)}
          >
            입금 취소
          </button>
        ) : (
          "–"
        ),
    },
  ];
  return (
    <>
      <DetailDrawer
        title={`청구 상세 ${billId}`}
        testId="receipt-detail-drawer"
        onClose={close}
        pending={Boolean(action)}
      >
        <section
          data-screen-id="SCR-UI-13"
          className="receipt-detail"
          data-testid="receipt-detail"
        >
          <nav
            className="receipt-tabs"
            role="tablist"
            aria-label="청구 상세 탭"
          >
            <button
              type="button"
              role="tab"
              id="receipt-info-tab"
              aria-controls="receipt-info-panel"
              aria-selected={tab === "bill"}
              data-testid="receipt-info-tab"
              onClick={() => setTab("bill")}
            >
              청구 정보
            </button>
            <button
              type="button"
              role="tab"
              id="receipt-history-tab"
              aria-controls="receipt-history-panel"
              aria-selected={tab === "receipts"}
              data-testid="receipt-history-tab"
              onClick={() => setTab("receipts")}
            >
              입금 내역 ({detail?.receipts.total ?? 0})
            </button>
          </nav>
          {loading && <p role="status">불러오는 중입니다.</p>}
          {Boolean(error) && (
            <ErrorBanner
              message={
                error instanceof Error ? error.message : "조회하지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={() => {
                void load().catch(() => {});
              }}
            />
          )}
          {bill && detail && (
            <>
              {readonly && (
                <p className="sicost-banner sicost-tone-lock">
                  지난 연도는 조회만 할 수 있습니다.
                </p>
              )}
              <div
                role="tabpanel"
                id="receipt-info-panel"
                aria-labelledby="receipt-info-tab"
                hidden={tab !== "bill"}
                data-testid="receipt-info-panel"
              >
                <dl className="receipt-summary">
                  {[
                    ["프로젝트", bill.projectName],
                    ["고객사", bill.customerName],
                    ["청구월", bill.billingMonth],
                    ["차수", String(bill.order)],
                    ["청구일", bill.billingDate],
                    ["입금 예정일", bill.dueDate],
                    ["공급가", `${formatDecimal(bill.supplyAmount)} 원`],
                    ["세율", `${formatDecimal(bill.vatRate)} %`],
                    ["부가세", `${formatDecimal(bill.vatAmount)} 원`],
                    ["합계", `${formatDecimal(bill.totalAmount)} 원`],
                    ["입금합", `${formatDecimal(bill.receiptAmount)} 원`],
                    ["잔액", `${formatDecimal(bill.unpaidAmount)} 원`],
                    ["세금계산서번호", bill.taxInvoiceNo ?? "–"],
                  ].map(([label, value]) => (
                    <div key={label}>
                      <dt>{label}</dt>
                      <dd>{value}</dd>
                    </div>
                  ))}
                  <div>
                    <dt>상태</dt>
                    <dd>
                      <StatusBadge
                        tone={
                          bill.status === "PAID"
                            ? "ok"
                            : bill.status === "LATE"
                              ? "bad"
                              : bill.status === "CNCL"
                                ? "lock"
                                : "warn"
                        }
                      >
                        {
                          {
                            PAID: "입금 완료",
                            LATE: "연체",
                            CNCL: "취소됨",
                            UNPAID: "미수",
                          }[bill.status]
                        }
                      </StatusBadge>
                    </dd>
                  </div>
                </dl>
                {bill.taxInvoiceNo === null && (
                  <p className="sicost-banner sicost-tone-warn">번호 미입력</p>
                )}
                {bill.dunning === null && (
                  <p className="receipt-help">미수 알림 기준 설정 사용 불가</p>
                )}
                {bill.cancelled && (
                  <p className="sicost-banner sicost-tone-lock">
                    청구 취소: {bill.cancelUserId ?? "–"} /{" "}
                    {formatDateTime(bill.cancelledAt)} /{" "}
                    {bill.cancelReason ?? "–"}
                  </p>
                )}
                <div className="receipt-actions">
                  {onEdit && (
                    <button
                      type="button"
                      data-testid="receipt-bill-edit"
                      disabled={locked || bill.cancelled}
                      onClick={() => onEdit(bill)}
                    >
                      수정
                    </button>
                  )}
                  {onCancelBill && (
                    <button
                      type="button"
                      data-testid="receipt-bill-cancel"
                      disabled={
                        locked ||
                        bill.cancelled ||
                        Boolean(detail.receipts.normalCount)
                      }
                      onClick={() => onCancelBill(bill)}
                    >
                      청구 취소
                    </button>
                  )}
                  {!readonly && !bill.cancelled && bill.status !== "PAID" && (
                    <button
                      type="button"
                      className="sicost-primary"
                      data-testid="receipt-create-open"
                      disabled={locked}
                      onClick={() => setAction("create")}
                    >
                      입금 등록
                    </button>
                  )}
                </div>
              </div>
              <div
                role="tabpanel"
                id="receipt-history-panel"
                aria-labelledby="receipt-history-tab"
                hidden={tab !== "receipts"}
                data-testid="receipt-history-panel"
              >
                <p>
                  정상 {detail.receipts.normalCount}건 · 전체{" "}
                  {detail.receipts.total}건
                </p>
                <DataTable
                  rows={detail.receipts.items}
                  columns={columns}
                  rowKey={(row) => row.id}
                  loading={loading}
                  empty="등록된 입금 내역이 없습니다."
                />
              </div>
            </>
          )}
        </section>
      </DetailDrawer>
      {action && bill && period && (
        <ReceiptEntryDialog
          bill={{ ...bill, revision: detail!.revision }}
          year={year}
          period={period}
          receipt={action === "create" ? undefined : action}
          onClose={() => {
            setAction(undefined);
            changedDirty(false);
          }}
          onDirtyChange={changedDirty}
          onSaved={async (result) => {
            await load();
            await onChanged?.(result);
          }}
        />
      )}
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        onCancel={() => setLeaving(false)}
        onConfirm={() => {
          changedDirty(false);
          onClose();
        }}
        confirmLabel="나가기"
        cancelLabel="계속 작성"
      >
        <p>저장하지 않고 상세를 닫습니다.</p>
      </Dialog>
    </>
  );
}
