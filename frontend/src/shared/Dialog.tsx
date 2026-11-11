import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";

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
}: DialogProps) {
  const ref = useRef<HTMLFormElement>(null);
  const inFlight = useRef(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const titleId = useId();
  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    const target =
      ref.current?.querySelector<HTMLElement>(
        '[aria-invalid="true"],input:not(:disabled),textarea:not(:disabled),select:not(:disabled)',
      ) ?? ref.current?.querySelector<HTMLElement>("[data-confirm]");
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
    if (pending || confirmDisabled || inFlight.current) return;
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
        ref={ref}
        className={`sicost-dialog${wide ? " sicost-dialog-wide" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-busy={pending}
        tabIndex={-1}
        onSubmit={(event) => {
          event.preventDefault();
          void confirm();
        }}
      >
        <header>
          <h2 id={titleId}>{title}</h2>
        </header>
        <div className="sicost-dialog-body">
          {children}
          {submitError && (
            <p role="alert" className="sicost-field-error">
              {submitError}
            </p>
          )}
        </div>
        <footer>
          <button
            type="button"
            disabled={pending}
            onClick={() => {
              if (!inFlight.current) onCancel();
            }}
          >
            {cancelLabel}
          </button>
          <button
            type="submit"
            data-confirm
            className="sicost-primary"
            disabled={pending || confirmDisabled}
          >
            {pending ? "처리 중…" : confirmLabel}
          </button>
        </footer>
      </form>
    </div>,
    document.body,
  );
}
export function ConfirmDialog(props: DialogProps) {
  return <Dialog {...props} />;
}
