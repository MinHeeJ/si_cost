import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Icon } from "./Icon";
import { canFocusOverlayContent } from "./useOverlayLayer";
import "./feedback.css";

export type Tone = "ok" | "warn" | "bad" | "info" | "lock" | "neutral";
type FeedbackTone = Exclude<Tone, "lock" | "neutral">;

const feedbackIcons: Record<FeedbackTone, string> = {
  ok: "checkCircle",
  warn: "warning",
  bad: "errorCircle",
  info: "info",
};

export function StatusBadge({
  tone,
  children,
}: {
  tone: Tone;
  children: ReactNode;
}) {
  return <span className={`sicost-badge sicost-tone-${tone}`}>{children}</span>;
}

export function FeedbackNotice({
  tone,
  title,
  children,
  action,
  onDismiss,
  focusOnShow = false,
  toast = false,
  heading = false,
  testId,
}: {
  tone: FeedbackTone;
  title: string;
  children?: ReactNode;
  action?: ReactNode;
  onDismiss?: () => void;
  focusOnShow?: boolean;
  toast?: boolean;
  heading?: boolean;
  testId?: string;
}) {
  const notice = useRef<HTMLDivElement>(null);
  const Title = heading ? "h2" : "strong";
  useEffect(() => {
    const element = notice.current;
    if (!focusOnShow || !element || !canFocusOverlayContent(element)) return;
    element.focus({ preventScroll: true });
    element.scrollIntoView?.({ block: "nearest" });
  }, [focusOnShow, title]);

  return (
    <div
      ref={notice}
      data-testid={testId}
      role={tone === "bad" || tone === "warn" ? "alert" : "status"}
      className={`sicost-feedback sicost-tone-${tone}${toast ? " sicost-toast" : ""}`}
      tabIndex={focusOnShow ? -1 : undefined}
    >
      <Icon name={feedbackIcons[tone]} size={20} className="sicost-feedback-icon" />
      <div className="sicost-feedback-content">
        <Title className="sicost-feedback-title">{title}</Title>
        {children != null && <div className="sicost-feedback-detail">{children}</div>}
      </div>
      {action && <div className="sicost-feedback-action">{action}</div>}
      {onDismiss && (
        <button
          type="button"
          className="sicost-feedback-dismiss"
          aria-label="알림 닫기"
          onClick={onDismiss}
        >
          <Icon name="close" size={16} />
        </button>
      )}
    </div>
  );
}

export function ErrorBanner({
  title,
  message,
  requestId,
  onRetry,
  pending = false,
  retryTestId,
  onDismiss,
  focusOnShow = false,
}: {
  title?: string;
  message: string;
  requestId?: string;
  onRetry?: () => void;
  pending?: boolean;
  retryTestId?: string;
  onDismiss?: () => void;
  focusOnShow?: boolean;
}) {
  const heading = title ?? (onRetry ? "조회 실패" : "요청 실패");
  const detail = message.trim() === heading ? undefined : message;
  return (
    <FeedbackNotice
      tone="bad"
      title={heading}
      action={onRetry && (
        <button type="button" data-testid={retryTestId} disabled={pending} onClick={onRetry}>
          다시 조회
        </button>
      )}
      onDismiss={onDismiss}
      focusOnShow={focusOnShow}
    >
      {detail}
      {requestId && <small className="sicost-feedback-reference">문의 번호: {requestId}</small>}
    </FeedbackNotice>
  );
}

export function Toast({
  message,
  tone = "ok",
}: {
  message: string;
  tone?: FeedbackTone;
}) {
  return <FeedbackNotice tone={tone} title={message} toast />;
}

export function FloatingToast({
  message,
  detail,
  durationMs = 3500,
}: {
  message: string;
  detail?: string;
  durationMs?: number;
}) {
  const [visible, setVisible] = useState(true);
  useEffect(() => {
    setVisible(true);
    const timer = window.setTimeout(() => setVisible(false), durationMs);
    return () => window.clearTimeout(timer);
  }, [message, detail, durationMs]);

  if (!visible) return null;
  const content = <FeedbackNotice tone="ok" title={message} toast>{detail}</FeedbackNotice>;
  const stack = document.getElementById("sicost-toast-stack");
  return stack
    ? createPortal(content, stack)
    : createPortal(<div className="sicost-toast-stack">{content}</div>, document.body);
}
