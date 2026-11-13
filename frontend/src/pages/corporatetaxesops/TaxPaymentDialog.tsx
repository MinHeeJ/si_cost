import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { getMonthClose } from "../shared/sharedApi";
import * as api from "./corporateTaxApi";
import "./corporateTax.css";

export type TaxPaymentDialogProps = {
  payment: api.CorporateTaxPayment;
  month: string;
  today: string;
  cashBaseMonth: string;
  editable: boolean;
  onClose(): void;
  onSaved(result: api.CorporateTaxConfirmation): Promise<void> | void;
};
export function TaxPaymentDialog({
  payment,
  month,
  today,
  cashBaseMonth,
  editable,
  onClose,
  onSaved,
}: TaxPaymentDialogProps) {
  const [date, setDate] = useState(payment.dueDate);
  const [amount, setAmount] = useState(payment.displayAmount ?? "");
  const [pending, setPending] = useState(false);
  const [checking, setChecking] = useState(true);
  const [blocked, setBlocked] = useState(true);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>();
  const [stale, setStale] = useState(false);
  const [saved, setSaved] = useState<api.CorporateTaxConfirmation>();
  const [refreshFailed, setRefreshFailed] = useState(false);
  const flight = useRef(false);
  const actualMonth = date.slice(0, 7);
  useEffect(() => {
    let live = true;
    setChecking(true);
    setBlocked(true);
    if (!/^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(actualMonth)) {
      setChecking(false);
      return;
    }
    void getMonthClose(actualMonth, actualMonth)
      .then((result) => {
        if (live)
          setBlocked(
            actualMonth < cashBaseMonth ||
              actualMonth.slice(0, 4) !== today.slice(0, 4) ||
              result.items.some(
                (row) =>
                  row.month === actualMonth &&
                  (row.status === "마감" || row.status === "CLOSE"),
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
  }, [actualMonth, cashBaseMonth, today]);
  async function refresh(result: api.CorporateTaxConfirmation) {
    try {
      await onSaved(result);
      onClose();
    } catch {
      setRefreshFailed(true);
    }
  }
  async function submit() {
    if (
      flight.current ||
      !editable ||
      payment.payYn === "Y" ||
      stale ||
      saved ||
      checking ||
      blocked
    )
      return;
    const errors: Record<string, string> = {};
    const clean = amount.replace(/[\s\p{Z},]/gu, "").replace(/원$/, "");
    if (!/^\d{1,15}$/.test(clean))
      errors.payAmount =
        "실제납부액은 0~999,999,999,999,999원 정수로 입력해 주세요.";
    const parsed = new Date(`${date}T00:00:00Z`);
    if (
      !/^(?!0000)\d{4}-\d{2}-\d{2}$/.test(date) ||
      Number.isNaN(parsed.getTime()) ||
      parsed.toISOString().slice(0, 10) !== date ||
      date > today
    )
      errors.paidDate =
        "실제납부일은 실제 달력일이며 서버 오늘 이하여야 합니다.";
    setFields(errors);
    if (Object.keys(errors).length) return;
    flight.current = true;
    setPending(true);
    setError(undefined);
    try {
      const result = await api.saveCorporateTaxPaymentConfirmation(
        payment.year,
        payment.payType,
        {
          month,
          expectedRevision: payment.confirmationRevision,
          paidDate: date,
          payAmount: BigInt(clean).toString(),
        },
      );
      setSaved(result);
      window.dispatchEvent(
        new CustomEvent(api.CORPORATE_TAX_COMMITTED, {
          detail: { month, year: payment.year },
        }),
      );
      await refresh(result);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        if (["STALE_WRITE", "TAX_ALREADY_PAID"].includes(e.body.code))
          setStale(true);
      } else setStale(true);
    } finally {
      flight.current = false;
      setPending(false);
    }
  }
  return (
    <Dialog
      open
      title="법인세 납부 확인"
      onCancel={onClose}
      onConfirm={submit}
      pending={pending}
      confirmDisabled={
        !editable ||
        payment.payYn === "Y" ||
        stale ||
        Boolean(saved) ||
        checking ||
        blocked
      }
    >
      <section className="corporate-tax-form" data-testid="tax-payment-dialog">
        <p>
          {payment.year}년{" "}
          {payment.payType === "MID" ? "중간예납" : "확정 신고"} · 예정일{" "}
          {payment.dueDate}
        </p>
        <p>
          올해·오늘 이하·운영 시작 이후·미마감월이어야 합니다. 선택월과
          실제납부월은 달라도 됩니다.
        </p>
        <p>확인 후 예정일/금액/실제일 수정·확인 취소는 제공하지 않습니다.</p>
        {Boolean(error) && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "납부 확인에 실패했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
          />
        )}
        <div className="sicost-field">
          <label htmlFor="tax-paid-date">실제납부일 *</label>
          <input
            id="tax-paid-date"
            data-testid="tax-paid-date"
            type="date"
            max={today}
            value={date}
            disabled={pending || Boolean(saved) || !editable}
            onChange={(e) => setDate(e.target.value)}
            aria-invalid={Boolean(fields.paidDate)}
          />
          {fields.paidDate && <span role="alert">{fields.paidDate}</span>}
        </div>
        <div className="sicost-field">
          <label htmlFor="tax-paid-amount">실제납부액 (원) *</label>
          <input
            id="tax-paid-amount"
            data-testid="tax-paid-amount"
            inputMode="numeric"
            value={amount}
            disabled={pending || Boolean(saved) || !editable}
            onChange={(e) => setAmount(e.target.value)}
            aria-invalid={Boolean(fields.payAmount)}
          />
          {fields.payAmount && <span role="alert">{fields.payAmount}</span>}
        </div>
        {blocked && !checking && (
          <p className="sicost-banner sicost-tone-lock">
            실제납부월이 마감되었거나 올해 운영기간 밖입니다.
          </p>
        )}
        {!editable && (
          <p className="sicost-banner sicost-tone-lock">
            선택월은 읽기 전용입니다.
          </p>
        )}
        {payment.payYn === "Y" && <p>이미 납부 확인된 일정입니다.</p>}
        {stale && (
          <p role="alert">
            최신 납부 내역을 다시 조회해 주세요. 자동으로 재확인하지 않습니다.
          </p>
        )}
        {refreshFailed && saved && (
          <p role="status">
            저장됐지만 다시 불러오지 못했습니다.{" "}
            <button
              type="button"
              data-testid="tax-confirmation-refresh"
              onClick={() => void refresh(saved)}
            >
              다시 조회
            </button>
          </p>
        )}
      </section>
    </Dialog>
  );
}
