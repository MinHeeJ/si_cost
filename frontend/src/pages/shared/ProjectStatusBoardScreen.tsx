import { SharedScreenFrame } from "./FollowUpScreen";
import {
  ProjectStatusBoard,
  type ProjectStatusBoardProps,
} from "../apiops/ProjectStatusBoard";
import {
  ProjectStatusIssuesPanel,
  type StatusIssueDialogHost,
} from "../projectstatusboardops/ProjectStatusIssuesPanel";
import { ProjectAnalysisDownload } from "../projectstatusboardops/ProjectAnalysisDownload";
export function ProjectStatusBoardScreen({
  renderIssue,
  ...props
}: ProjectStatusBoardProps & { renderIssue?: StatusIssueDialogHost } = {}) {
  return (
    <SharedScreenFrame screenId="UI-31">
      <ProjectStatusBoard
        renderIssues={(_query, onChanged) => (
          <ProjectStatusIssuesPanel
            onChanged={onChanged}
            renderIssue={renderIssue}
          />
        )}
        renderExport={(query) => (
          <ProjectAnalysisDownload view="statusBoard" query={query} />
        )}
        {...props}
      />
    </SharedScreenFrame>
  );
}
