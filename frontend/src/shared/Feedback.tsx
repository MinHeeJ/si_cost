import { useEffect, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";

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
  retryTestId,
}: {
  message: string;
  requestId?: string;
  onRetry?: () => void;
  pending?: boolean;
  retryTestId?: string;
}) {
  return (
    <div role="alert" className="sicost-banner sicost-tone-bad">
      <span>
        {message}
        {requestId && <small> 문의 번호: {requestId}</small>}
      </span>
      {onRetry && (
        <button
          type="button"
          data-testid={retryTestId}
          disabled={pending}
          onClick={onRetry}
        >
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
      {tone === "ok" && <svg className="sicost-toast-check" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m5 12 4 4L19 6" /></svg>}{message}
    </div>
  );
}

export function FloatingToast({ message }: { message: string }) {
  const [visible, setVisible] = useState(true);
  useEffect(() => {
    const timer = window.setTimeout(() => setVisible(false), 3500);
    return () => window.clearTimeout(timer);
  }, []);
  return visible
    ? createPortal(
        <div className="sicost-toast-stack">
          <Toast message={message} />
        </div>,
        document.body,
      )
    : null;
}
