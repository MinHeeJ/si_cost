import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { usePermission } from "./permission/PermissionBoundary";
import { useModalLayer } from "./useModalLayer";

export type DialogProps = {
  open: boolean;
  title: string;
  children: ReactNode;
  pending?: boolean;
  onConfirm(): void | Promise<void>;
  onCancel(): void;
  confirmLabel?: string;
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
  confirmDisabled = false,
  cancelLabel = "취소",
  wide,
  fullPage = false,
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
  const inFlight = useRef(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const titleId = useId();
  const dialogTestId =
    testId ?? `confirmation-dialog-${titleId.replace(/:/g, "").toLowerCase()}`;
  const isTop = useModalLayer(
    ref,
    open,
    () => {
      if (!pending && !inFlight.current) onCancel();
    },
    () => {
      const target =
        ref.current?.querySelector<HTMLElement>(
          '[aria-invalid="true"],input:not(:disabled),textarea:not(:disabled),select:not(:disabled)',
        ) ??
        ref.current?.querySelector<HTMLElement>(
          "[data-confirm]:not(:disabled)",
        ) ??
        ref.current?.querySelector<HTMLElement>(FOCUSABLE) ??
        ref.current;
      return target;
    },
  );
  useEffect(() => {
    if (open && isTop())
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
    <div className={`sicost-scrim${fullPage ? " sicost-form-scrim" : ""}`}>
      <form
        noValidate={noValidate}
        ref={ref}
        data-testid={dialogTestId}
        className={`sicost-dialog${wide ? " sicost-dialog-wide" : ""}${fullPage ? " sicost-dialog-full-page sicost-form-page" : ""}`}
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
            <button
              type="button"
              data-testid={`${dialogTestId}-close`}
              onClick={onCancel}
              aria-label="닫기"
            >
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
                data-testid={cancelTestId ?? `${dialogTestId}-cancel`}
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
              data-testid={confirmTestId ?? `${dialogTestId}-confirm`}
              data-action-id={confirmActionId}
              data-confirm
              className="sicost-primary"
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
