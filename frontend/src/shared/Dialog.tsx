import { ErrorBanner } from "./Feedback";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { usePermission } from "./permission/PermissionBoundary";
import { focusFirstError } from "./FieldErrors";
import { useOverlayLayer } from "./useOverlayLayer";

export type DialogProps = {
  open: boolean;
  title: string;
  titleHelp?: ReactNode;
  className?: string;
  children: ReactNode;
  pending?: boolean;
  onConfirm(): void | Promise<void>;
  onCancel(): void;
  confirmLabel?: string;
  confirmClassName?: string;
  danger?: boolean;
  confirmDisabled?: boolean;
  cancelLabel?: string;
  wide?: boolean;
  testId?: string;
  confirmTestId?: string;
  cancelTestId?: string;
  confirmActionId?: string;
  cancelActionId?: string;
  footerStart?: ReactNode;
  feedback?: ReactNode;
  noValidate?: boolean;
  permission?: { screen: string; action: string };
  hideCancel?: boolean;
  /** Full-page editing surface using the same submit, permission and focus controller. */
  fullPage?: boolean;
  fullPageBreadcrumb?: string;
};
const FOCUSABLE =
  'button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex="0"]';
export function Dialog({
  open,
  title,
  titleHelp,
  className,
  children,
  pending = false,
  onConfirm,
  onCancel,
  confirmLabel = "확인",
  confirmClassName = "sicost-primary",
  danger = false,
  confirmDisabled = false,
  cancelLabel = "취소",
  wide,
  testId,
  confirmTestId,
  cancelTestId,
  confirmActionId,
  cancelActionId,
  footerStart,
  feedback,
  noValidate,
  permission,
  hideCancel = false,
  fullPage = false,
  fullPageBreadcrumb,
}: DialogProps) {
  const granted = usePermission(
    permission?.screen ?? "",
    permission?.action ?? "INQ",
  );
  const readOnly = permission !== undefined && !granted;
  const ref = useRef<HTMLFormElement>(null);
  const topmost = useOverlayLayer(ref, open, fullPage ? 24 : 60);
  const inFlight = useRef(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const titleId = useId();
  const errorSignature = useRef("");
  useEffect(() => {
    if (!open) {
      setSubmitError(null);
      return;
    }
    const previous = document.activeElement as HTMLElement | null;
    const target =
      ref.current?.querySelector<HTMLElement>(
        '[aria-invalid="true"],input:not(:disabled),textarea:not(:disabled),select:not(:disabled)',
      ) ??
      ref.current?.querySelector<HTMLElement>(
        "[data-confirm]:not(:disabled)",
      ) ??
      ref.current?.querySelector<HTMLElement>(FOCUSABLE) ??
      ref.current;
    if (topmost()) target?.focus();
    return () => {
      previous?.focus();
    };
  }, [open]);
  useEffect(() => {
    const signature = open
      ? Array.from(
          ref.current?.querySelectorAll<HTMLElement>('[aria-invalid="true"]') ??
            [],
        )
          .map(
            (node) =>
              `${node.getAttribute("name") ?? node.id ?? node.dataset.testid}:${node.parentElement?.textContent}`,
          )
          .join("|")
      : "";
    if (signature && signature !== errorSignature.current)
      focusFirstError(ref.current);
    errorSignature.current = signature;
  });
  if (!open) return null;
  const confirm = async () => {
    if (readOnly || pending || confirmDisabled || inFlight.current) return;
    inFlight.current = true;
    setSubmitError(null);
    try {
      await onConfirm();
    } catch (error) {
      setSubmitError(
        error instanceof Error
          ? error.message
          : "처리하지 못했습니다. 입력한 내용은 유지됩니다.",
      );
    } finally {
      inFlight.current = false;
    }
  };
  return createPortal(
    <div
      className="sicost-scrim"
      onKeyDown={(event) => {
        if (!topmost()) return;
        if (event.key === "Escape" || event.key === "Tab")
          event.stopPropagation();
        if (event.key === "Escape" && !pending && !inFlight.current) {
          event.preventDefault();
          onCancel();
        }
        if (event.key === "Tab") {
          const nodes = [
            ...(ref.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? []),
          ];
          const first = nodes[0],
            last = nodes[nodes.length - 1];
          if (!first) {
            event.preventDefault();
            ref.current?.focus();
          } else if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last?.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
          }
        }
      }}
    >
      <form
        noValidate={noValidate}
        ref={ref}
        data-testid={testId}
        className={`sicost-dialog${wide ? " sicost-dialog-wide" : ""}${fullPage ? " sicost-dialog-page" : ""}${className ? ` ${className}` : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-busy={pending}
        data-permission-screen={permission?.screen}
        data-permission-action={permission?.action}
        tabIndex={-1}
        onSubmit={(event) => {
          event.preventDefault();
          void confirm();
        }}
      >
        <header>
          {fullPage && !hideCancel && (
            <button
              type="button"
              data-testid={`${testId ?? "full-page-form"}-back`}
              disabled={pending}
              onClick={() => {
                if (!inFlight.current) onCancel();
              }}
            >
              ‹ 목록
            </button>
          )}
          <div>
            {fullPage && fullPageBreadcrumb && (
              <p className="sicost-breadcrumb">{fullPageBreadcrumb}</p>
            )}
            <div
              className={
                titleHelp
                  ? "sicost-dialog-title-with-help"
                  : fullPage ? "sicost-title-row" : undefined
              }
            >
              <h2 id={titleId}>{title}</h2>
              {titleHelp}
              {fullPage && permission && <small>{permission.screen}</small>}
            </div>
          </div>
          {readOnly && (
            <button type="button" onClick={onCancel} aria-label="닫기">
              ×
            </button>
          )}
        </header>
        {(feedback || submitError) && (
          <div className="sicost-feedback-slot">
            {feedback}
            {submitError && (
              <ErrorBanner
                title={`${confirmLabel} 실패`}
                message={submitError}
                onDismiss={() => setSubmitError(null)}
                focusOnShow={topmost()}
              />
            )}
          </div>
        )}
        <div className="sicost-dialog-body">
          {readOnly ? (
            <fieldset
              disabled
              style={{ border: 0, margin: 0, padding: 0, minWidth: 0 }}
            >
              {children}
            </fieldset>
          ) : (
            children
          )}
        </div>
        {!readOnly && (
          <footer>
            {footerStart}
            {!hideCancel && (
              <button
                type="button"
                data-testid={cancelTestId}
                data-action-id={cancelActionId}
                disabled={pending}
                onClick={() => {
                  if (!inFlight.current) onCancel();
                }}
              >
                {cancelLabel}
              </button>
            )}
            <button
              type="submit"
              data-testid={confirmTestId}
              data-action-id={confirmActionId}
              data-confirm
              className={danger ? "sicost-danger" : confirmClassName}
              disabled={pending || confirmDisabled}
            >
              {pending ? "처리 중…" : confirmLabel}
            </button>
          </footer>
        )}
      </form>
    </div>,
    document.body,
  );
}
export function ConfirmDialog(props: DialogProps) {
  return <Dialog {...props} />;
}
