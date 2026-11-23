import { createContext, useContext, type ReactNode } from "react";
import { createPortal } from "react-dom";
export const PeriodStatusTarget = createContext<HTMLElement|null|undefined>(undefined);
export function PeriodStatus({
  children,
  className = "",
  inline = false,
}: {
  children: ReactNode;
  className?: string;
  inline?: boolean;
}) {
  const target = useContext(PeriodStatusTarget);
  const status = (
    <div className={`sicost-period-status ${className}`.trim()} role="group" aria-label="조회 기간 상태">
      {children}
    </div>
  );
  if (inline || target === undefined) return status;
  return target ? createPortal(status, target) : null;
}
