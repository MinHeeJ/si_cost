import { useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { formatAmount } from "../../shared/format";
import type { SettlementResult } from "../salespartsettlements/salesPartSettlementsApi";

export function SettlementConfirmationDialog({
  data,
  card,
  pending,
  onConfirm,
  onCancel,
}: {
  data: SettlementResult;
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
      title="정산 확정 미리보기"
      pending={pending}
      onConfirm={onConfirm}
      onCancel={onCancel}
      confirmLabel="확정"
      confirmDisabled={!data.canConfirm || total == null}
    >
      <div
        data-testid="settlement-confirmation-dialog"
        data-screen-id="SCR-UI-19-M1"
      >
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
  pending,
  onConfirm,
  onCancel,
}: {
  data: SettlementResult;
  pending: boolean;
  onConfirm(reason: string): Promise<void>;
  onCancel(): void;
}) {
  const [reason, setReason] = useState("");
  const valid = reason.trim().length > 0 && [...reason.trim()].length <= 500;
  return (
    <Dialog
      open
      title="정산 확정 취소"
      pending={pending}
      onConfirm={() => onConfirm(reason.trim())}
      onCancel={onCancel}
      confirmLabel="확정 취소"
      confirmDisabled={!valid}
    >
      <div
        data-testid="settlement-cancellation-dialog"
        data-screen-id="SCR-UI-19-M2"
      >
        <p>
          {data.partId} / {data.month} · 확정 파트 이익금{" "}
          {formatAmount(data.summary.partProfitAmount, "원")} 원
        </p>
        <p>취소 후 현재 원천으로 다시 계산됩니다</p>
        <label>
          취소 사유
          <textarea
            data-testid="settlement-reason-input"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            aria-invalid={reason.length > 0 && !valid}
          />
        </label>
        {reason.length > 0 && !valid && (
          <p role="alert">
            취소 사유는 공백을 제외하고 1~500자로 입력해 주세요.
          </p>
        )}
      </div>
    </Dialog>
  );
}
