import { useLocation, useSearchParams } from "react-router-dom";
import { ProjectCostDetailPanel } from "./ProjectCostDetailPanel";

/** Mounted by the UI-04 host next to its independently owned list. */
export function ProjectCostDetailRoute() {
  const [search, setSearch] = useSearchParams();
  const location = useLocation();
  const projectId = search.get("projectId");
  if (!projectId) return null;
  return (
    <ProjectCostDetailPanel
      projectId={projectId}
      month={search.get("month") ?? undefined}
      onClose={() => {
        const next = new URLSearchParams(search);
        next.delete("projectId");
        next.delete("costTab");
        setSearch(next, { replace: true, state: location.state });
      }}
    />
  );
}
