import type { ReactNode } from "react";

export type Tone = "ok" | "warn" | "bad" | "info" | "lock" | "neutral";
export function StatusBadge({
  tone,
  children,
}: {
  tone: Tone;
  children: ReactNode;
}) {
  return <span className={`sicost-badge sicost-tone-${tone}`}>{children}</span>;
}
export function ErrorBanner({
  message,
  requestId,
  onRetry,
  pending = false,
}: {
  message: string;
  requestId?: string;
  onRetry?: () => void;
  pending?: boolean;
}) {
  return (
    <div role="alert" className="sicost-banner sicost-tone-bad">
      <span>
        {message}
        {requestId && <small> 문의 번호: {requestId}</small>}
      </span>
      {onRetry && (
        <button type="button" disabled={pending} onClick={onRetry}>
          다시 조회
        </button>
      )}
    </div>
  );
}
export function Toast({
  message,
  tone = "ok",
}: {
  message: string;
  tone?: Tone;
}) {
  return (
    <div role="status" className={`sicost-toast sicost-tone-${tone}`}>
      {message}
    </div>
  );
}
