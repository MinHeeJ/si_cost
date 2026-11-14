import { useEffect, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";

export type DetailDrawerProps = {
  /** 접근성 이름. 비우면 제목을 쓴다. */
  label?: string;
  title: ReactNode;
  /** 제목 아래 보조 줄(아이디·기준월 등). */
  subtitle?: ReactNode;
  /** 머리 줄 오른쪽 작업 버튼들(수정·기준 변경 등). 닫기 버튼은 늘 마지막에 붙는다. */
  actions?: ReactNode;
  children: ReactNode;
  /** 처리 중에는 닫지 못하게 막는다. */
  pending?: boolean;
  hidden?: boolean;
  onClose(): void;
  /** 화면 시험이 부품을 찾는 이름. */
  testId?: string;
  closeTestId?: string;
};

/**
 * 오른쪽에서 나오는 상세 패널의 공통 부품(요구서 2.2 `DetailDrawer`).
 * 열림/닫기·제목·작업·Escape·바깥 클릭 닫기·초점 복원만 맡고, 탭과 본문은 화면이 children으로 넣는다.
 * 폼의 변경 중 닫기 경고는 화면이 onClose 앞에서 처리한다.
 */
export function DetailDrawer({
  label,
  title,
  subtitle,
  actions,
  children,
  pending = false,
  hidden = false,
  onClose,
  testId = "detail-drawer",
  closeTestId = "detail-drawer-close",
}: DetailDrawerProps) {
  const titleId = useId();
  const closeButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (hidden) return;
    const previous = document.activeElement as HTMLElement | null;
    closeButton.current?.focus();
    return () => {
      previous?.focus?.();
    };
  }, [hidden]);
  const close = () => {
    if (!pending) onClose();
  };
  return createPortal(
    <>
      <div
        className="sicost-drawer-scrim"
        style={hidden ? { display: "none" } : undefined}
        data-testid={`${testId}-scrim`}
        onClick={close}
      />
      <aside
        className="sicost-drawer"
        style={hidden ? { display: "none" } : undefined}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        aria-labelledby={label ? undefined : titleId}
        data-testid={testId}
        onKeyDown={(event) => {
          if (event.key === "Escape") {
            event.stopPropagation();
            close();
          }
        }}
      >
        <header className="sicost-drawer-header">
          <div className="sicost-drawer-heading">
            <h2 id={titleId}>{title}</h2>
            {subtitle != null && (
              <div className="sicost-drawer-subtitle">{subtitle}</div>
            )}
          </div>
          <div className="sicost-drawer-actions">
            {actions}
            <button
              type="button"
              ref={closeButton}
              data-testid={closeTestId}
              disabled={pending}
              onClick={close}
            >
              닫기
            </button>
          </div>
        </header>
        <div className="sicost-drawer-body">{children}</div>
      </aside>
    </>,
    document.body,
  );
}
