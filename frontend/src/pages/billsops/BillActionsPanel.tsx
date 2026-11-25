import { usePermission } from "../../shared/permission/PermissionBoundary";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { Dialog } from "../../shared/Dialog";
import { FeedbackNotice, FloatingToast } from "../../shared/Feedback";
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
  children?: ReactNode;
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
  children,
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
  const { pending, outcome, clearOutcome, commit } = useCommitRefresh(
    "저장은 완료됐지만 청구 정보를 다시 불러오지 못했습니다.",
  );
  const writable = year >= currentYear;
  const registerPermission = usePermission("UI-13", "REG");
  const updatePermission = usePermission("UI-13", "UPD");
  const canRegister = writable && registerPermission;
  const canUpdate = writable && updatePermission;
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
  const cancelNoticeId = useId();
  const showCancel = canUpdate && !bill.cancelled;
  const hasNormalReceipts = normalReceiptCount > 0;
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
      {outcome?.kind === "saved" && lastResult && (
        <FloatingToast
          message={lastResult.bill.cancelled ? "청구를 취소했습니다." : outcome.message}
          detail={lastResult.bill.cancelled ? undefined : (
            `${lastResult.bill.id} · ${lastResult.bill.order}차 · 공급가 ${billAmount(lastResult.bill.supplyAmount, amountUnit)} ${amountUnit}`
          )}
        />
      )}
      {outcome?.kind === "refresh-failed" && saved && (
        <FeedbackNotice
          tone="warn"
          title="저장 완료 · 조회 실패"
          action={
            <button
              type="button"
              data-testid="bill-action-refresh"
              disabled={pending}
              onClick={() => {
                setError(undefined);
                void refresh(saved).then(clearOutcome).catch(setError);
              }}
            >
              다시 조회
            </button>
          }
        >
          {outcome.message}
        </FeedbackNotice>
      )}
      {lastResult && lastResult.warnings.length > 0 && (
        <FeedbackNotice tone="warn" title="청구 확인">
          {lastResult.warnings.join(" · ")}
        </FeedbackNotice>
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
      <div
        className="bill-actions-toolbar"
        role="group"
        aria-label="청구 및 입금 작업"
      >
        {canRegister && bill.quickActions.copyAllowed && (
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
        {canRegister &&
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
        {showCancel && (
          <button
            type="button"
            data-testid="bill-cancel-open"
            disabled={blocked || !canCancel}
            aria-describedby={
              hasNormalReceipts || cancellationBlockedReason
                ? cancelNoticeId
                : undefined
            }
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
        {children}
      </div>
      {showCancel && (hasNormalReceipts || cancellationBlockedReason) && (
        <div id={cancelNoticeId}>
          {cancellationBlockedReason ? (
            <FeedbackNotice tone="info" title="청구 수정·취소 제한">
              {cancellationBlockedReason}
            </FeedbackNotice>
          ) : (
            <FeedbackNotice tone="warn" title="청구 취소 제한">
              정상 입금이 있습니다. 청구를 취소하려면 입금 내역에서 먼저
              입금을 취소해 주세요.
            </FeedbackNotice>
          )}
        </div>
      )}
      {canRegister && draft && (
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
        open={canUpdate && cancelOpen}
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
