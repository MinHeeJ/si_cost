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

export function CompactPeriodSummary({
  items,
}: {
  items: readonly { label: string; value: ReactNode }[];
}) {
  return (
    <PeriodStatus className="sicost-period-summary sicost-period-summary-compact">
      {items.map(({ label, value }) => (
        <span className="sicost-period-summary-item" key={label}>
          <span className="sicost-period-summary-label">{label}</span>
          <b>{value}</b>
        </span>
      ))}
    </PeriodStatus>
  );
}
