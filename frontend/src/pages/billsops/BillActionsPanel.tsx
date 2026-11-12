import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { BillCopyDialog } from "./BillCopyDialog";
import { BillingError, billAmount } from "./BillSummaryCards";
import * as api from "./billOperationsApi";
import "./billOperations.css";

export type BillActionsPanelProps = {
  bill: api.BillReference;
  year: number;
  currentYear: number;
  normalReceiptCount: number;
  cancellationBlockedReason?: string;
  amountUnit?: "원" | "천원" | "백만원";
  onChanged(result: api.BillMutationResult): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
};
export function BillActionsPanel({
  bill,
  year,
  currentYear,
  normalReceiptCount,
  cancellationBlockedReason,
  amountUnit = "천원",
  onChanged,
  onDirtyChange,
}: BillActionsPanelProps) {
  const [error, setError] = useState<unknown>();
  const [draft, setDraft] = useState<api.BillCopyDraft>();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [leaving, setLeaving] = useState(false);
  const [stale, setStale] = useState(false);
  const [saved, setSaved] = useState<api.BillMutationResult>();
  const [lastResult, setLastResult] = useState<api.BillMutationResult>();
  const [loadingDraft, setLoadingDraft] = useState(false);
  const busy = useRef(false);
  const { pending, message, commit } = useCommitRefresh();
  const writable = year >= currentYear;
  const dirty = cancelOpen && Boolean(reason);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    setStale(false);
    setError(undefined);
  }, [bill.id, bill.revision]);
  async function refresh(value: api.BillMutationResult) {
    await onChanged(value);
    setSaved(undefined);
  }
  async function mutate(save: () => Promise<api.BillMutationResult>) {
    if (busy.current || pending || stale || !writable) return;
    busy.current = true;
    setError(undefined);
    setFields({});
    try {
      let result: api.BillMutationResult | undefined;
      await commit(
        async () => {
          result = await save();
          setSaved(result);
          setLastResult(result);
          setCancelOpen(false);
          setReason("");
          onDirtyChange?.(false);
        },
        async () => {
          if (result) await refresh(result);
        },
      );
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        if (e.body.code === "STALE_WRITE") setStale(true);
      }
    } finally {
      busy.current = false;
    }
  }
  const blocked = !writable || pending || stale || loadingDraft;
  const canCancel =
    !bill.cancelled &&
    normalReceiptCount === 0 &&
    !cancellationBlockedReason &&
    Number(bill.billingMonth.slice(0, 4)) >= currentYear;
  return (
    <section
      className="bill-actions"
      data-screen-id="SCR-UI-13"
      data-testid="bill-actions-panel"
    >
      <BillingError error={error} />
      {message && <p role="status">{message}</p>}
      {lastResult && !saved && (
        <p className="sicost-banner sicost-tone-info">
          {lastResult.bill.id} · {lastResult.bill.order}차 · 공급가{" "}
          {billAmount(lastResult.bill.supplyAmount, amountUnit)} {amountUnit} ·{" "}
          {lastResult.warnings.join(" · ")}
        </p>
      )}
      {saved && (
        <div className="sicost-banner sicost-tone-info">
          <span>
            {saved.bill.id} · {saved.bill.order}차 · 공급가{" "}
            {billAmount(saved.bill.supplyAmount, amountUnit)} {amountUnit}{" "}
            {saved.warnings.join(" · ")}
          </span>
          <button
            type="button"
            data-testid="bill-action-refresh"
            disabled={pending}
            onClick={() => {
              void refresh(saved).catch(setError);
            }}
          >
            다시 조회
          </button>
        </div>
      )}
      {!writable && (
        <p className="sicost-banner sicost-tone-lock">
          지난 연도는 조회만 할 수 있습니다.
        </p>
      )}
      {stale && (
        <p role="alert">
          다른 사용자가 변경했습니다. 다시 조회한 뒤 확인해 주세요. 자동
          재전송하지 않습니다.
        </p>
      )}
      <div className="bill-actions-toolbar">
        {writable && bill.quickActions.copyAllowed && (
          <button
            type="button"
            data-testid="bill-copy-open"
            disabled={blocked}
            onClick={async () => {
              if (busy.current) return;
              busy.current = true;
              setLoadingDraft(true);
              setError(undefined);
              try {
                setDraft(await api.getBillCopyDraft(bill.id, year));
              } catch (e) {
                setError(e);
              } finally {
                setLoadingDraft(false);
                busy.current = false;
              }
            }}
          >
            고쳐서 청구
          </button>
        )}
        {writable &&
          bill.contractType !== "TK" &&
          bill.quickActions.nextMonthAllowed && (
            <button
              type="button"
              data-testid="bill-next-month"
              disabled={blocked}
              onClick={() => {
                void mutate(() =>
                  api.createNextMonthBill(bill.id, {
                    year,
                    expectedRevision: bill.revision,
                  }),
                );
              }}
            >
              다음 달 청구
            </button>
          )}
        {writable && !bill.cancelled && (
          <button
            type="button"
            data-testid="bill-cancel-open"
            disabled={blocked || !canCancel}
            onClick={() => {
              setError(undefined);
              setCancelOpen(true);
              setReason("");
              setFields({});
            }}
          >
            청구 취소
          </button>
        )}
        {!bill.quickActions.copyAllowed && (
          <span title={bill.quickActions.reason ?? undefined}>–</span>
        )}
      </div>
      {normalReceiptCount > 0 && !bill.cancelled && (
        <p>정상 입금이 있습니다. 먼저 입금을 취소해 주세요.</p>
      )}
      {cancellationBlockedReason && (
        <p className="sicost-banner sicost-tone-lock">
          {cancellationBlockedReason}
        </p>
      )}
      {bill.dunning === null && !bill.cancelled && (
        <StatusBadge tone="neutral">알림 기준 설정 확인 필요</StatusBadge>
      )}
      {draft && (
        <BillCopyDialog
          key={draft.sourceBillId}
          draft={draft}
          projectName={bill.projectName}
          onCancel={() => setDraft(undefined)}
          onChanged={onChanged}
          onDirtyChange={onDirtyChange}
        />
      )}
      <Dialog
        open={cancelOpen}
        title="청구 취소"
        confirmLabel="청구 취소"
        cancelLabel="닫기"
        pending={pending}
        confirmDisabled={blocked || !canCancel}
        onCancel={() => {
          if (dirty) setLeaving(true);
          else setCancelOpen(false);
        }}
        onConfirm={async () => {
          const value = reason.replace(/^[\s\p{Z}]+|[\s\p{Z}]+$/gu, "");
          if (!value || [...value].length > 500) {
            setFields({ reason: "취소 사유는 1~500자로 입력해 주세요." });
            return;
          }
          await mutate(() =>
            api.saveBillCancellation(bill.id, {
              year,
              expectedRevision: bill.revision,
              reason: value,
            }),
          );
        }}
      >
        <div data-testid="bill-cancel-dialog">
          <BillingError error={error} />
          <p>
            {bill.id} · 정상 입금 {normalReceiptCount}건
          </p>
          <div className="sicost-field">
            <label htmlFor="bill-cancel-reason">취소 사유 *</label>
            <textarea
              id="bill-cancel-reason"
              data-testid="bill-cancel-reason"
              disabled={pending}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              aria-invalid={Boolean(fields.reason)}
            />
            {fields.reason && (
              <span role="alert" className="sicost-field-error">
                {fields.reason}
              </span>
            )}
          </div>
          <p>
            원금액·번호·차수·입금 이력을 보존합니다. 복원과 물리 삭제는
            없습니다.
          </p>
        </div>
      </Dialog>
      <Dialog
        open={leaving}
        title="작성 중인 내용을 나가시겠습니까?"
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onCancel={() => setLeaving(false)}
        onConfirm={() => {
          setLeaving(false);
          setCancelOpen(false);
        }}
      >
        <p>자동 저장하지 않습니다.</p>
      </Dialog>
    </section>
  );
}
