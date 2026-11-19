import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { usePermission } from "./permission/PermissionBoundary";
import { focusFirstError } from "./FieldErrors";

export type DialogProps = {
  open: boolean;
  title: string;
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
  const errorSignature = useRef("");
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
        className={`sicost-dialog${wide ? " sicost-dialog-wide" : ""}`}
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
