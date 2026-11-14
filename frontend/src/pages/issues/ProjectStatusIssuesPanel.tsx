import { useState } from "react";
import { usePreferences } from "../me/usePreferences";
import type { ScreenColumnPreferences } from "../me/preferencesApi";
import { ProjectIssueColumnSettings } from "./ProjectIssueColumnSettings";
import type { IssueColumnView } from "./ProjectIssueTable";
import { ProjectIssuesPanel } from "./ProjectIssuesPanel";
export function ProjectStatusIssuesPanel({
  onChanged,
  onDirtyChange,
}: {
  onChanged?(): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
}) {
  const preferences = usePreferences();
  const [columns, setColumns] = useState(false);
  const value = preferences.get("COLS", "UI-31")?.value as
    | ScreenColumnPreferences
    | undefined;
  const size=preferences.get("PAGE_SIZE")?.value;
  return (
    <>
      <ProjectIssuesPanel
        board
        pageSize={size===20||size===100?size:50}
        onPageSizeChange={(value)=>preferences.put("PAGE_SIZE","*",value)}
        dense={preferences.get("DENSE")?.value === "좁게"}
        onChanged={onChanged}
        onDirtyChange={onDirtyChange}
        columnView={value?.issues as IssueColumnView | undefined}
        onColumnsChange={() => setColumns(true)}
      />
      {columns && (
        <ProjectIssueColumnSettings onClose={() => setColumns(false)} />
      )}
    </>
  );
}
