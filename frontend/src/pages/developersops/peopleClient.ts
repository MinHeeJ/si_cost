import { request } from "../../shared/api";

export type DeveloperFilters = {
  employeeType?: "FT" | "CT" | "FL" | "OS";
  jobCode?: string;
  directClass?: "DRCT" | "INDRCT";
  workStatus?: "INPUT" | "WTHDR" | "IDLE" | "LEAVE" | "RETIRE";
  q?: string;
  includeStopped?: boolean;
  sort?:
    | "name"
    | "companyName"
    | "employeeType"
    | "grade"
    | "job"
    | "status"
    | "withdrawalDate"
    | "monthlyRate";
  direction?: "ASC" | "DESC";
};
export type DeveloperSelectionItem = { id: string; revision: string };
export type DeveloperAssignment = {
  id: string;
  projectId: string;
  projectName: string;
  jobCode: string;
  jobName: string;
  startDate: string;
  endDate: string;
  contractMm: string;
  assignmentStatus: string;
};
export type DeveloperAssignmentsPage = {
  items: DeveloperAssignment[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type DeveloperBulkRejected = {
  id: string;
  code: string;
  message: string;
  details: {
    projects?: {
      id: string;
      name: string;
      startDate: string;
      endDate: string;
    }[];
    referenceCount?: number;
  };
};
export type DeveloperBulkResult = {
  applied: { id: string }[];
  noChange: { id: string }[];
  rejected: DeveloperBulkRejected[];
  appliedCount: number;
  noChangeCount: number;
  rejectedCount: number;
};
function query(values: Record<string, string | number | boolean | undefined>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return params.toString();
}
export function getDeveloperSelection(
  filters: DeveloperFilters,
  signal?: AbortSignal,
) {
  return request<{ items: DeveloperSelectionItem[] }>(
    `/api/developers/selection?${query(filters)}`,
    { signal, cache: "no-store" },
  );
}
export function getDeveloperAssignments(
  id: string,
  page = 1,
  size: 20 | 50 | 100 = 50,
  signal?: AbortSignal,
) {
  return request<DeveloperAssignmentsPage>(
    `/api/developers/${encodeURIComponent(id)}/assignments?${query({ page, size })}`,
    { signal, cache: "no-store" },
  );
}
export function saveDeveloperBulkStatus(
  targetUseYn: "Y" | "N",
  items: DeveloperSelectionItem[],
) {
  return request<DeveloperBulkResult>("/api/developers/bulk-status", {
    method: "POST",
    body: JSON.stringify({ targetUseYn, items }),
  });
}
