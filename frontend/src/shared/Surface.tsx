import { useId, type ReactNode } from "react";

/** Presentation slots only: callers own values, visibility and business calculations. */
export function SectionCard({
  title,
  actions,
  children,
  testId,
  className = "",
}: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  testId: string;
  className?: string;
}) {
  const id = useId();
  return (
    <section
      className={`sicost-section-card ${className}`.trim()}
      data-testid={testId}
      aria-labelledby={title ? id : undefined}
    >
      {title && (
        <header className="sicost-section-heading">
          <h2 id={id}>{title}</h2>
          {actions}
        </header>
      )}
      {children}
    </section>
  );
}

export function KpiStrip({
  children,
  testId,
  label,
  className = "",
}: {
  children: ReactNode;
  testId: string;
  label: string;
  className?: string;
}) {
  return (
    <div
      className={`sicost-kpi-strip ${className}`.trim()}
      data-testid={testId}
      role="group"
      aria-label={label}
    >
      {children}
    </div>
  );
}

export function KpiItem({
  label,
  value,
  unit,
  comparison,
  status,
  source,
  testId,
  onClick,
  disabled,
  valueClassName,
}: {
  label: ReactNode;
  value: ReactNode;
  unit?: ReactNode;
  comparison?: ReactNode;
  status?: ReactNode;
  source?: ReactNode;
  testId: string;
  onClick?: () => void;
  disabled?: boolean;
  valueClassName?: string;
}) {
  const body = (
    <>
      <span className="sicost-kpi-label">
        {label}
        {status}
      </span>
      <strong className={`sicost-kpi-value ${valueClassName ?? ""}`.trim()}>
        {value}
        {unit && <small>{unit}</small>}
      </strong>
      {comparison != null && (
        <span className="sicost-kpi-comparison">{comparison}</span>
      )}
      {source}
    </>
  );
  return onClick ? (
    <button
      type="button"
      className="sicost-kpi-item"
      data-testid={testId}
      onClick={onClick}
      disabled={disabled}
    >
      {body}
    </button>
  ) : (
    <div className="sicost-kpi-item" data-testid={testId}>
      {body}
    </div>
  );
}

export function DetailDescription({
  children,
  testId,
  className = "",
}: {
  children: ReactNode;
  testId: string;
  className?: string;
}) {
  return (
    <dl
      className={`sicost-detail-description ${className}`.trim()}
      data-testid={testId}
    >
      {children}
    </dl>
  );
}
