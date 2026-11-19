import type { ReactNode } from "react";
import "./presentation.css";
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
  return (
    <section
      className={`sicost-section-card ${className}`}
      data-testid={testId}
    >
      {(title || actions) && (
        <header>
          <h2>{title}</h2>
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
}: {
  children: ReactNode;
  testId: string;
}) {
  return (
    <div className="sicost-kpi-strip" data-testid={testId}>
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
}: {
  label: ReactNode;
  value: ReactNode;
  unit?: ReactNode;
  comparison?: ReactNode;
  status?: ReactNode;
  source?: ReactNode;
  testId: string;
}) {
  return (
    <div className="sicost-kpi-item" data-testid={testId}>
      <div className="sicost-kpi-label">{label}</div>
      <strong className="sicost-kpi-value">
        {value}
        {unit && <small>{unit}</small>}
      </strong>
      {comparison && <div className="sicost-kpi-comparison">{comparison}</div>}
      {status}
      {source}
    </div>
  );
}
export function DetailDescription({
  children,
  testId,
}: {
  children: ReactNode;
  testId: string;
}) {
  return (
    <dl className="sicost-detail-description" data-testid={testId}>
      {children}
    </dl>
  );
}
