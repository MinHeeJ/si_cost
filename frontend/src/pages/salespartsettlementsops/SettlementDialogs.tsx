import { useId, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { formatAmount } from "../../shared/format";
import type { SettlementResult } from "../salespartsettlements/salesPartSettlementsApi";

export function SettlementConfirmationDialog({
  data,
  partName,
  card,
  pending,
  onConfirm,
  onCancel,
}: {
  data: SettlementResult;
  partName?: string;
  card: string;
  pending: boolean;
  onConfirm(): Promise<void>;
  onCancel(): void;
}) {
  const total =
    data.summary.personProfitShareAmount == null
      ? null
      : (
          BigInt(data.summary.personProfitShareAmount) - BigInt(card)
        ).toString();
  return (
    <Dialog
      open
      title={`${partName ?? data.partId} ${data.month} 정산을 확정할까요?`}
      pending={pending}
      onConfirm={onConfirm}
      onCancel={onCancel}
      confirmLabel="확정하기"
      confirmDisabled={!data.canConfirm || total == null}
    >
      <div
        data-testid="settlement-confirmation-dialog"
        data-screen-id="SCR-UI-19-M1"
      >
        <p>확정하면 이 달 파트 정산(법인카드 사용액 포함)이 잠깁니다.</p>
        <p>
          {data.partId} / {data.month} 전체 {data.summary.personCount}명 · 적자{" "}
          {data.summary.deficitPersonCount}명
        </p>
        <p>
          인력 이익금 합{" "}
          {formatAmount(data.summary.personProfitShareAmount, "원")} 원
        </p>
        <p>
          카드 {formatAmount(card, "원")} 원 · 최종 파트 이익금{" "}
          {formatAmount(total, "원")} 원
        </p>
        <p>카드 사용액을 함께 저장하고 정산을 확정합니다</p>
        {data.issues.map((issue, i) => (
          <p role={issue.blocking ? "alert" : undefined} key={i}>
            {issue.message}
          </p>
        ))}
      </div>
    </Dialog>
  );
}
export function SettlementCancellationDialog({
  data,
  partName,
  pending,
  onConfirm,
  onCancel,
}: {
  data: SettlementResult;
  partName?: string;
  pending: boolean;
  onConfirm(reason: string): Promise<void>;
  onCancel(): void;
}) {
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const errorId = useId();
  const valid = reason.trim().length > 0 && [...reason.trim()].length <= 500;
  const error =
    submitted && !valid
      ? reason.trim().length === 0
        ? "취소 사유를 입력해 주세요."
        : "취소 사유는 공백을 제외하고 1~500자로 입력해 주세요."
      : "";
  return (
    <Dialog
      open
      title="정산 확정을 취소할까요?"
      pending={pending}
      noValidate
      onConfirm={async () => {
        setSubmitted(true);
        if (valid) await onConfirm(reason.trim());
      }}
      onCancel={onCancel}
      confirmLabel="확정 취소"
      cancelLabel="닫기"
      confirmClassName="sicost-danger"
      confirmTestId="settlement-cancellation-submit"
      cancelTestId="settlement-cancellation-close"
    >
      <div
        data-testid="settlement-cancellation-dialog"
        data-screen-id="SCR-UI-19-M2"
      >
        <p>
          취소하면 이 달 파트 정산을 다시 고칠 수 있습니다. 사유는 기록에
          남습니다.
        </p>
        <p>
          {partName ?? data.partId} / {data.month} · 확정 파트 이익금{" "}
          {formatAmount(data.summary.partProfitAmount, "원")} 원
        </p>
        <p>취소 후 현재 원천으로 다시 계산됩니다</p>
        <label className="sicost-field">
          취소 사유 *
          <textarea
            data-testid="settlement-reason-input"
            value={reason}
            rows={4}
            maxLength={500}
            disabled={pending}
            aria-required="true"
            aria-describedby={error ? errorId : undefined}
            onChange={(e) => setReason(e.target.value)}
            aria-invalid={Boolean(error)}
          />
        </label>
        {error && (
          <p
            role="alert"
            id={errorId}
            className="sicost-field-error"
            data-testid="settlement-reason-error"
          >
            {error}
          </p>
        )}
      </div>
    </Dialog>
  );
}
