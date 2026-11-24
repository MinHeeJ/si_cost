import { StatusBadge } from "../../shared/Feedback";
import "./issues.css";

export function IssueDueDate({
  issue,
  baseDate,
}: {
  issue: { dueDate: string; status: string; overdue: boolean };
  baseDate?: string;
}) {
  const overdue = issue.status !== "CLOSE" && issue.overdue;
  const days = baseDate
    ? Math.max(
        0,
        Math.round(
          (Date.parse(baseDate) - Date.parse(issue.dueDate)) / 86400000,
        ),
      )
    : 0;
  return (
    <span className={overdue ? "issue-overdue" : undefined}>
      {issue.dueDate}
      {overdue && days > 0 ? ` (${days}일 지남)` : ""}
      {overdue && <StatusBadge tone="bad">기한 지남</StatusBadge>}
    </span>
  );
}
