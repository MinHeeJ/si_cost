import { useRef, useState, type ReactNode } from "react";
import {
  StaffingAssignmentsScreen as AssignmentWorkspace,
  type AssignmentListActions,
} from "../assignmentsops/StaffingAssignmentsScreen";
import { AssignmentError } from "../assignmentsops/AssignmentDialogs";
import { saveDownload } from "../b7/b7Api";
import { downloadAssignments } from "./assignmentDownloadsApi";

export type StaffingAssignmentsProps = {
  onDirtyChange?(dirty: boolean): void;
  renderActions?(context: AssignmentListActions): ReactNode;
};
// SCR-UI-12: retain the existing assignment forms, history, monthly drawer and router.
export function StaffingAssignmentsScreen({
  onDirtyChange,
  renderActions,
}: StaffingAssignmentsProps = {}) {
  return (
    <AssignmentWorkspace
      onDirtyChange={onDirtyChange}
      renderActions={(context) => (
        <>
          <AssignmentDownloads context={context} />
          {renderActions?.(context)}
        </>
      )}
    />
  );
}
function AssignmentDownloads({ context }: { context: AssignmentListActions }) {
  const [pending, setPending] = useState(false),
    [error, setError] = useState<unknown>(),
    [success, setSuccess] = useState(false);
  const busy = useRef(false);
  async function download() {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    setError(undefined);
    setSuccess(false);
    try {
      saveDownload(
        await downloadAssignments(
          context.filters,
          context.sort,
          context.amountUnit,
        ),
      );
      setSuccess(true);
    } catch (e) {
      setError(e);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  return (
    <div
      className="assignment-toolbar assignment-desktop"
      data-testid="assignment-download-actions"
    >
      <button
        type="button"
        data-testid="assignment-download"
        disabled={pending}
        onClick={() => void download()}
      >
        {pending ? "파일 생성 중…" : "다운로드"}
      </button>
      {success && <span role="status">파일을 내려받았습니다.</span>}
      <AssignmentError error={error} retry={() => void download()} />
    </div>
  );
}
