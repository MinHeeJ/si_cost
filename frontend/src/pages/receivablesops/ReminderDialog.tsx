import { ScreenLoading } from "../../shared/ScreenLoading";
import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { formatDecimal } from "../../shared/format";
import * as api from "./dunningDeliveryApi";
import "./dunningDelivery.css";

export function ReminderDialog({
  billId,
  onClose,
  onAccepted,
}: {
  billId: string;
  onClose(): void;
  onAccepted(result: api.ReminderResult): Promise<void> | void;
}) {
  const [preview, setPreview] = useState<api.DeliveryPreview>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true),
    [pending, setPending] = useState(false),
    [reload, setReload] = useState(0);
  const [stale, setStale] = useState(false),
    [result, setResult] = useState<api.ReminderResult>();
  const [operationId] = useState(() => crypto.randomUUID?.() ?? (() => {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 15) | 64;
    bytes[8] = (bytes[8] & 63) | 128;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  })());
  const active = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    api
      .getReceivableDeliveryPreview(billId, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) {
          setPreview(value);
          setStale(false);
        }
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [billId, reload]);
  async function submit() {
    if (!preview?.canRemind || active.current || stale || result) return;
    active.current = true;
    setPending(true);
    setError(undefined);
    try {
      const saved = await api.createReceivableReminder(billId, {
        expectedRevision: preview.revision,
        operationId,
      });
      setResult(saved);
      try {
        await onAccepted(saved);
      } catch {
        setError(
          new Error("요청은 저장됐지만 전달결과를 불러오지 못했습니다."),
        );
      }
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 409) setStale(true);
    } finally {
      active.current = false;
      setPending(false);
    }
  }
  return (
    <Dialog
      open
      title="다시알림 확인"
      wide
      testId="receivable-reminder-dialog"
      pending={pending}
      confirmLabel="확인 후 다시알림"
      confirmDisabled={loading || !preview?.canRemind || stale || !!result}
      confirmTestId="reminder-confirm"
      cancelTestId="reminder-cancel"
      onConfirm={submit}
      onCancel={onClose}
    >
      <section className="dunning-delivery" data-testid="reminder-preview">
        {loading && <ScreenLoading message="최신 미리보기를 불러오는 중입니다." />}
        {!!error && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "미리보기를 불러오지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
          />
        )}
        {!pending && !result && (
          <button
            type="button"
            data-testid="reminder-reload"
            onClick={() => setReload((v) => v + 1)}
          >
            최신 미리보기 다시 확인
          </button>
        )}
        {preview && (
          <>
            <dl className="dunning-preview-fields">
              <div>
                <dt>청구</dt>
                <dd>
                  {preview.billId} / {preview.projectName}
                </dd>
              </div>
              <div>
                <dt>현재미수 (원)</dt>
                <dd>{formatDecimal(preview.unpaidAmount)}</dd>
              </div>
              <div>
                <dt>입금 예정일</dt>
                <dd>{preview.dueDate}</dd>
              </div>
              <div>
                <dt>현재단계</dt>
                <dd>
                  <StatusBadge tone={preview.currentStep > 1 ? "bad" : "warn"}>
                    {preview.currentStep}단계
                  </StatusBadge>
                </dd>
              </div>
              <div>
                <dt>기준시각</dt>
                <dd>{preview.observedAt.replace("T", " ")}</dd>
              </div>
              <div>
                <dt>방법</dt>
                <dd>시스템+메일</dd>
              </div>
              <div>
                <dt>메일설정</dt>
                <dd>
                  {preview.mailConfigured
                    ? "설정됨"
                    : "미설정 · 메일 실패 (MAIL_NOT_CONFIGURED)"}
                </dd>
              </div>
              <div>
                <dt>처리중</dt>
                <dd>{preview.inProgress ? "전달 처리 중" : "아니요"}</dd>
              </div>
            </dl>
            <div className="sicost-table-viewport">
              <table className="sicost-table">
                <thead>
                  <tr>
                    {["이름", "아이디", "자격", "이메일", "이메일누락"].map(
                      (label) => (
                        <th key={label}>{label}<FieldHelp label={label} kind="B" /></th>
                      ),
                    )}
                  </tr>
                </thead>
                <tbody>
                  {preview.recipients.map((person) => (
                    <tr
                      key={person.userId}
                      data-testid={`reminder-recipient-${person.userId.toLowerCase()}`}
                    >
                      <td>{person.userName}</td>
                      <td>{person.userId}</td>
                      <td>{person.qualificationCodes.join(" · ")}</td>
                      <td>{person.emailAddress ?? "–"}</td>
                      <td>
                        {person.emailMissing ? (
                          <StatusBadge tone="bad">누락 · 메일 실패</StatusBadge>
                        ) : (
                          "없음"
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!preview.recipients.length && (
              <p className="dunning-empty">수신자가 없습니다.</p>
            )}
            {preview.unassignedReasons.map((reason) => (
              <p key={reason} className="sicost-banner sicost-tone-warn">
                {reason}
              </p>
            ))}
            {preview.unavailableReasons.map((reason) => (
              <p key={reason} className="sicost-banner sicost-tone-lock">
                {reason}
              </p>
            ))}
            <p className="sicost-banner sicost-tone-info">
              확인 한 번은 현재단계 수동알림 한 회를 추가합니다. 요청 저장은
              메일 성공을 뜻하지 않습니다. 마감청구의 금액은 변경하지 않습니다.
            </p>
          </>
        )}
        {result && (
          <p role="status">
            요청 저장 · #{result.reservedSn} ·{" "}
            {result.completed ? "처리 종료 (채널별 결과 확인)" : "전달 처리 중"}
          </p>
        )}
        {result && !!error && (
          <button
            type="button"
            data-testid="reminder-result-reload"
            onClick={() => void onAccepted(result)}
          >
            전달결과 재조회
          </button>
        )}
        {!!error && !stale && !result && (
          <p>
            자동 재전송하지 않습니다. 전달상태를 확인한 뒤 같은 요청으로 명시
            재시도할 수 있습니다.
          </p>
        )}
      </section>
    </Dialog>
  );
}
