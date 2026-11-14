import { useLocation } from "react-router-dom";
import { StaffingAssignmentsScreen as Workspace, type StaffingAssignmentsProps } from "../assignments/StaffingAssignmentsScreen";
export function StaffingAssignmentsScreen(props: StaffingAssignmentsProps = {}) {
  const location = useLocation();
  const query = new URLSearchParams(location.search);
  const ids = query.getAll("assignmentId");
  const id = location.pathname === "/UI-12" && ids.length === 1 && ids[0].length > 0 && ids[0].length <= 20 ? ids[0] : undefined;
  return <Workspace {...props} entryAssignmentId={id} entryHistory={query.get("tab") === "history"} />;
}
