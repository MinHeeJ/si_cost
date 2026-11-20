function SkeletonCard({
  kind,
  metrics = false,
}: {
  kind: "chart" | "list";
  metrics?: boolean;
}) {
  return (
    <div className="dashboard-panel dashboard-skeleton-card">
      <div className="dashboard-skeleton-bar dashboard-skeleton-heading" />
      {metrics && (
        <div className="dashboard-metrics dashboard-financial-metrics">
          {Array.from({ length: 3 }, (_, index) => (
            <div className="dashboard-metric" key={index}>
              <div className="dashboard-skeleton-bar" />
              <div className="dashboard-skeleton-bar dashboard-skeleton-number" />
            </div>
          ))}
        </div>
      )}
      {kind === "chart" ? (
        <div className="dashboard-skeleton-chart" />
      ) : (
        Array.from({ length: 3 }, (_, index) => (
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
      <div className="dashboard-main-grid">
        <SkeletonCard kind="chart" metrics />
        <div className="dashboard-stack">
          <div className="dashboard-metrics dashboard-operational-metrics">
            <SkeletonCard kind="list" />
            <SkeletonCard kind="list" />
          </div>
          <SkeletonCard kind="list" />
        </div>
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
