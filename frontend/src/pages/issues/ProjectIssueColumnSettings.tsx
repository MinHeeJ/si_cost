import {
  ColumnPreferencesDialog,
  type PreferenceColumn,
} from "../me/ColumnPreferencesDialog";
import { issueColumnIds, issueColumnLabels } from "./ProjectIssueTable";
export const projectIssuePreferenceColumns: PreferenceColumn[] =
  issueColumnIds.map((id) => ({
    id,
    label: issueColumnLabels[id],
    required: id === "title" || id === "projectName",
  }));
export function ProjectIssueColumnSettings({ onClose }: { onClose(): void }) {
  return (
    <ColumnPreferencesDialog
      open
      screenId="UI-31"
      viewId="issues"
      columns={projectIssuePreferenceColumns}
      onClose={onClose}
    />
  );
}
