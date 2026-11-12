import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { formatDecimal } from "../../shared/format";
import { getMonthClose, type PeriodContext } from "../shared/sharedApi";
import type { Bill, Receipt, ReceiptResult } from "./receiptsApi";
import * as api from "./receiptsApi";
import "./receipts.css";

export type ReceiptEntryDialogProps = {
  bill: Bill;
  year: number;
  period: PeriodContext;
  receipt?: Receipt;
  onClose(): void;
  onSaved(result: ReceiptResult): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
};
export function ReceiptEntryDialog({
  bill,
  year,
  period,
  receipt,
  onClose,
  onSaved,
  onDirtyChange,
}: ReceiptEntryDialogProps) {
  const [date, setDate] = useState(period.currentDate);
  const [amount, setAmount] = useState(bill.unpaidAmount);
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>();
  const [blocked, setBlocked] = useState(true);
  const [checking, setChecking] = useState(true);
  const [stale, setStale] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [saved, setSaved] = useState<ReceiptResult>();
  const [message, setMessage] = useState("");
  const flight = useRef(false);
  const dirty = receipt
    ? reason.length > 0
    : date !== period.currentDate || amount !== bill.unpaidAmount;
  const readonly = year < Number(period.currentMonth.slice(0, 4));
  const month = (receipt?.date ?? date).slice(0, 7);
  useEffect(() => {
    let live = true;
    setChecking(true);
    setBlocked(true);
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
      setChecking(false);
      return;
    }
    void getMonthClose(month, month)
      .then((result) => {
        if (live)
          setBlocked(
            month < period.cashBaseMonth ||
              !result.items.some(
                (row) => row.month === month && row.status === "작업중",
              ),
          );
      })
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setChecking(false);
      });
    return () => {
      live = false;
    };
  }, [month, period.cashBaseMonth]);
  useEffect(() => {
    onDirtyChange?.(dirty && !saved);
    return () => onDirtyChange?.(false);
  }, [dirty, saved, onDirtyChange]);
  useEffect(() => {
    const prevent = (event: BeforeUnloadEvent) => {
      if (dirty && !saved) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, [dirty, saved]);
  async function refresh(value: ReceiptResult) {
    try {
      await onSaved(value);
      onClose();
    } catch {
      setMessage("저장됐지만 다시 불러오지 못했습니다");
    }
  }
  async function submit() {
    if (flight.current || readonly || stale || saved || checking || blocked)
      return;
    const validation: Record<string, string> = {};
    const clean = amount.replace(/[\s\p{Z},]/gu, "").replace(/원$/, "");
    if (receipt) {
      if (![...reason.trim()].length || [...reason.trim()].length > 500)
        validation.reason = "취소 사유는 1~500자로 입력해 주세요.";
    } else {
      if (
        !/^\d+$/.test(clean) ||
        BigInt(clean) < 1n ||
        BigInt(clean) > 999999999999999n
      )
        validation.amount =
          "입금액은 1~999,999,999,999,999원 정수로 입력해 주세요.";
      else if (BigInt(clean) > BigInt(bill.unpaidAmount))
        validation.amount = "입금액이 현재 미수금을 초과합니다.";
      const actual = new Date(`${date}T00:00:00Z`);
      if (
        !/^(?!0000)\d{4}-\d{2}-\d{2}$/.test(date) ||
        Number.isNaN(actual.getTime()) ||
        actual.toISOString().slice(0, 10) !== date ||
        date > period.currentDate
      )
        validation.date = "실제 입금일은 서버 오늘 이하여야 합니다.";
    }
    if (month.slice(0, 4) < period.currentMonth.slice(0, 4))
      validation.date = "지난 연도의 입금은 변경할 수 없습니다.";
    setFields(validation);
    if (Object.keys(validation).length) return;
    flight.current = true;
    setPending(true);
    setError(undefined);
    try {
      const value = receipt
        ? await api.saveBillReceiptCancellation(bill.id, receipt.id, {
            year,
            expectedRevision: bill.revision,
            reason: reason.trim(),
          })
        : await api.createBillReceipt(bill.id, {
            year,
            expectedRevision: bill.revision,
            date,
            amount: BigInt(clean).toString(),
          });
      setSaved(value);
      onDirtyChange?.(false);
      window.dispatchEvent(
        new CustomEvent(api.RECEIPTS_COMMITTED, {
          detail: { billId: bill.id, revision: value.revision },
        }),
      );
      await refresh(value);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        if (e.body.code === "STALE_WRITE") setStale(true);
      } else setStale(true);
    } finally {
      flight.current = false;
      setPending(false);
    }
  }
  const title = receipt ? "입금 취소" : "입금 등록";
  function close() {
    if (!pending) {
      if (dirty && !saved) setLeaving(true);
      else onClose();
    }
  }
  return (
    <>
      <Dialog
        open
        title={title}
        onCancel={close}
        onConfirm={submit}
        pending={pending}
        confirmLabel={receipt ? "입금 취소" : "등록"}
        cancelLabel="닫기"
        confirmDisabled={
          readonly || stale || Boolean(saved) || checking || blocked
        }
      >
        <section
          className="receipt-form"
          data-testid="receipt-entry-dialog"
          data-screen-id="SCR-UI-13"
        >
          <p>
            청구 {bill.id} / {bill.projectName}
          </p>
          {receipt ? (
            <p>
              입금 {receipt.id} / {receipt.date} /{" "}
              {formatDecimal(receipt.amount)} 원
            </p>
          ) : (
            <p>
              현재 미수 {formatDecimal(bill.unpaidAmount)} 원 · 분할 입금 가능
            </p>
          )}
          {Boolean(error) && (
            <ErrorBanner
              message={error instanceof Error ? error.message : "요청 실패"}
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
            />
          )}
          {receipt ? (
            <div className="sicost-field">
              <label htmlFor="receipt-reason">취소 사유 *</label>
              <textarea
                id="receipt-reason"
                data-testid="receipt-reason"
                value={reason}
                disabled={pending || Boolean(saved)}
                onChange={(e) => setReason(e.target.value)}
                aria-invalid={Boolean(fields.reason)}
              />
              {fields.reason && <span role="alert">{fields.reason}</span>}
            </div>
          ) : (
            <div className="receipt-fields">
              <div className="sicost-field">
                <label htmlFor="receipt-date">입금일 *</label>
                <input
                  type="date"
                  id="receipt-date"
                  data-testid="receipt-date"
                  value={date}
                  max={period.currentDate}
                  disabled={pending || Boolean(saved)}
                  onChange={(e) => setDate(e.target.value)}
                  aria-invalid={Boolean(fields.date)}
                />
                {fields.date && <span role="alert">{fields.date}</span>}
              </div>
              <div className="sicost-field">
                <label htmlFor="receipt-amount">입금액 (원) *</label>
                <input
                  id="receipt-amount"
                  data-testid="receipt-amount"
                  inputMode="numeric"
                  value={amount}
                  disabled={pending || Boolean(saved)}
                  onChange={(e) => setAmount(e.target.value)}
                  aria-invalid={Boolean(fields.amount)}
                />
                {fields.amount && <span role="alert">{fields.amount}</span>}
              </div>
            </div>
          )}
          {readonly && (
            <p className="sicost-banner sicost-tone-lock">
              지난 연도는 조회만 할 수 있습니다.
            </p>
          )}
          {blocked && !checking && (
            <p className="sicost-banner sicost-tone-lock">
              입금일의 월이 마감·이관되어 변경할 수 없습니다.
            </p>
          )}
          {stale && (
            <p role="alert">
              다시 조회한 뒤 원래 입금행을 확인해 주세요. 자동으로 재저장하지
              않습니다.
            </p>
          )}
          {message && (
            <p role="status">
              {message}
              <button
                type="button"
                data-testid="receipt-refresh-saved"
                onClick={() => {
                  if (saved) void refresh(saved);
                }}
              >
                다시 조회
              </button>
            </p>
          )}
          {receipt && (
            <p>
              취소행 보존 · 잘못된 입금은 취소 후 재등록합니다. 복원·삭제는
              없습니다.
            </p>
          )}
        </section>
      </Dialog>
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
        confirmLabel="나가기"
        cancelLabel="계속 작성"
      >
        <p>입력한 내용을 저장하지 않고 나갑니다.</p>
      </Dialog>
    </>
  );
}
