import type { AnalysisQuery } from "../apiops/projectAnalysisApi";
import type { AmountUnit } from "../../shared/format";
import type { ColumnView } from "../me/preferencesApi";
import { ProjectAnalysisDownload } from "../projectstatusboardops/ProjectAnalysisDownload";

// SCR-UI-04: reuse the merged download flow, including preference, cancellation and error handling.
export function ProjectCostExportButton({
  query,
  unit,
  columns,
}: {
  query: AnalysisQuery;
  unit?: AmountUnit;
  columns?: ColumnView;
}) {
  return (
    <ProjectAnalysisDownload
      view="projectCost"
      query={query}
      unit={unit}
      columns={columns}
    />
  );
}
