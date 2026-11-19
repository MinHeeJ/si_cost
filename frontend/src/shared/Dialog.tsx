import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useOverlay } from "./useOverlay";
import { usePermission } from "./permission/PermissionBoundary";

export type DialogProps = {
  open: boolean;
  title: string;
  children: ReactNode;
  pending?: boolean;
  onConfirm(): void | Promise<void>;
  onCancel(): void;
  confirmLabel?: string;
  confirmClassName?: string;
  confirmDisabled?: boolean;
  cancelLabel?: string;
  wide?: boolean;
  fullPage?: boolean;
  testId?: string;
  confirmTestId?: string;
  cancelTestId?: string;
  confirmActionId?: string;
  cancelActionId?: string;
  footerStart?: ReactNode;
  noValidate?: boolean;
  permission?: { screen: string; action: string };
  hideCancel?: boolean;
};
const FOCUSABLE =
  'button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex="0"]';
export function Dialog({
  open,
  title,
  children,
  pending = false,
  onConfirm,
  onCancel,
  confirmLabel = "확인",
  confirmClassName = "sicost-primary",
  confirmDisabled = false,
  cancelLabel = "취소",
  wide,
  fullPage,
  testId,
  confirmTestId,
  cancelTestId,
  confirmActionId,
  cancelActionId,
  footerStart,
  noValidate,
  permission,
  hideCancel = false,
}: DialogProps) {
  const granted = usePermission(
    permission?.screen ?? "",
    permission?.action ?? "INQ",
  );
  const readOnly = permission !== undefined && !granted;
  const ref = useRef<HTMLFormElement>(null);
  const handleKeyDown = useOverlay(ref, open);
  const inFlight = useRef(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const titleId = useId();
  useEffect(() => {
    if (!open) return;
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
    target?.focus();
    return () => {
      previous?.focus();
    };
  }, [open]);
  useEffect(() => {
    if (open)
      ref.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
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
        if (!handleKeyDown(event)) return;
        if (event.key === "Escape" && !pending && !inFlight.current) {
          event.preventDefault();
          event.stopPropagation();
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
        className={`sicost-dialog${wide ? " sicost-dialog-wide" : ""}${fullPage ? " sicost-dialog-page" : ""}`}
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
          <h2 id={titleId}>{title}</h2>
          {readOnly && (
            <button type="button" onClick={onCancel} aria-label="닫기">
              ×
            </button>
          )}
        </header>
        <div className="sicost-dialog-body">
          {readOnly ? (
            <>
              <p role="note">
                수정 권한이 없어 보기만 할 수 있습니다(권한 › 메뉴×동작 표의
                수정).
              </p>
              <fieldset
                disabled
                style={{ border: 0, margin: 0, padding: 0, minWidth: 0 }}
              >
                {children}
              </fieldset>
            </>
          ) : (
            children
          )}
          {submitError && (
            <p role="alert" className="sicost-field-error">
              {submitError}
            </p>
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
              className={confirmClassName}
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
