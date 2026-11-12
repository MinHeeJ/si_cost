import { downloadB7File } from "../b7/b7Api";
import type { Filters } from "../assignmentsops/assignmentActionsApi";
export const assignmentColumns = [
  "warnings",
  "projectName",
  "developerName",
  "jobName",
  "startDate",
  "endDate",
  "billingUnitPrice",
  "contractMm",
  "yearMm",
  "extensionPossibility",
  "inputType",
] as const;
export function downloadAssignments(
  filters: Filters,
  sort: { id: string; direction: "ASC" | "DESC" },
  amountUnit: string,
) {
  return downloadB7File("/api/exports/master-data", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      view: "ASSIGNMENTS",
      filters,
      columns: assignmentColumns,
      sort: sort.id,
      direction: sort.direction,
      amountUnit,
    }),
  });
}
