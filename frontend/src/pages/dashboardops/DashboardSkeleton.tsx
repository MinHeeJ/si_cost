function SkeletonMetrics({ financial }: { financial: boolean }) {
  return (
    <div className={`dashboard-metrics dashboard-${financial ? "financial" : "operational"}-metrics`}>
      {Array.from({ length: financial ? 3 : 2 }, (_, index) => (
        <div className="dashboard-metric dashboard-skeleton-card" key={index}>
          <div className="dashboard-skeleton-bar" />
          <div className="dashboard-skeleton-bar dashboard-skeleton-number" />
          <div className="dashboard-skeleton-bar" />
        </div>
      ))}
    </div>
  );
}

function SkeletonCard({ kind, rows = 3 }: { kind: "chart" | "list"; rows?: number }) {
  return (
    <div className="dashboard-panel dashboard-skeleton-card">
      <div className="dashboard-skeleton-bar dashboard-skeleton-heading" />
      {kind === "chart" ? (
        <div className="dashboard-skeleton-chart" />
      ) : (
        Array.from({ length: rows }, (_, index) => (
          <div className="dashboard-skeleton-row" key={index}>
            <div className="dashboard-skeleton-bar" />
            <div className="dashboard-skeleton-bar" />
          </div>
        ))
      )}
    </div>
  );
}

/** Decorative layout only: no invented business values or focusable controls. */
export function DashboardSkeleton() {
  return (
    <div
      className="dashboard-skeleton"
      data-testid="dashboard-skeleton"
      aria-hidden="true"
    >
      <div className="dashboard-kpi-grid">
        <SkeletonMetrics financial />
        <SkeletonMetrics financial={false} />
      </div>
      <div className="dashboard-main-grid">
        <SkeletonCard kind="chart" />
        <SkeletonCard kind="list" rows={5} />
      </div>
      <div className="dashboard-two-grid">
        <SkeletonCard kind="chart" />
        <div className="dashboard-stack">
          <SkeletonCard kind="list" />
          <SkeletonCard kind="list" />
        </div>
      </div>
      <div className="dashboard-bottom-grid">
        <SkeletonCard kind="list" />
        <SkeletonCard kind="list" />
        <SkeletonCard kind="chart" />
      </div>
    </div>
  );
}
