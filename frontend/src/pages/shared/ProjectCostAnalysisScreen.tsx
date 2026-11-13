import { SharedScreenFrame } from "./FollowUpScreen";
import {
  ProjectCostAnalysis,
  type ProjectCostAnalysisProps,
} from "../apiops/ProjectCostAnalysis";
import { ProjectCostExportButton } from "../projectcostsops/ProjectCostExportButton";
export function ProjectCostAnalysisScreen(
  props: ProjectCostAnalysisProps = {},
) {
  return (
    <SharedScreenFrame screenId="UI-04" periodBar={null}>
      <ProjectCostAnalysis
        renderExport={(query, unit) => (
          <ProjectCostExportButton query={query} unit={unit} />
        )}
        {...props}
      />
    </SharedScreenFrame>
  );
}
