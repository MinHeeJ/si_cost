import { ProjectProgressEditor } from "../progress/ProjectProgressEditor";
import type {
  ProjectProgress,
  ProgressMutation,
} from "../progress/projectProgressApi";
import "../progress/projectProgress.css";

// /UI-01 project detail keeps the established editor, server preview and dirty-close behavior.
export function ProjectDatesPreview({
  data,
  onSaved,
  onClose,
  onDirtyChange,
}: {
  data: ProjectProgress;
  onSaved(result: ProgressMutation): Promise<void>;
  onClose(reload?: boolean): void;
  onDirtyChange?(dirty: boolean): void;
}) {
  return (
    <ProjectProgressEditor
      data={data}
      mode={{ kind: "dates" }}
      onSaved={onSaved}
      onClose={onClose}
      onDirtyChange={onDirtyChange}
    />
  );
}
