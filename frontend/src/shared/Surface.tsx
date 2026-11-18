import type { HTMLAttributes, ReactNode } from "react";

export function SectionCard({
  title,
  actions,
  children,
  className = "",
  testId,
}: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  testId?: string;
}) {
  return (
    <section
      className={`sicost-section-card ${className}`}
      data-testid={testId}
    >
      {(title || actions) && (
        <header className="sicost-section-heading">
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
  label = "조회 요약",
  className = "",
  as: Container = "div",
  ...props
}: HTMLAttributes<HTMLElement> & { label?: string; as?: "div" | "section" }) {
  return (
    <Container
      {...props}
      className={`sicost-kpi-strip ${className}`}
      aria-label={props["aria-label"] ?? label}
    >
      {children}
    </Container>
  );
}
export function KpiItem({
  label,
  value,
  unit,
  comparison,
  status,
  source,
}: {
  label: ReactNode;
  value: ReactNode;
  unit?: ReactNode;
  comparison?: ReactNode;
  status?: ReactNode;
  source?: ReactNode;
}) {
  return (
    <div className="sicost-kpi-item">
      <div className="sicost-kpi-label">
        {label}
        {status}
      </div>
      <strong className="sicost-kpi-value">
        {value}
        {unit && <small>{unit}</small>}
      </strong>
      {comparison && <div className="sicost-kpi-comparison">{comparison}</div>}
      {source}
    </div>
  );
}
export function DetailDescription({ children }: { children: ReactNode }) {
  return <dl className="sicost-detail-description">{children}</dl>;
}
