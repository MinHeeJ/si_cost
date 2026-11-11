import { request, type ApiErrorBody } from "../../shared/api";

export type ClientPurpose = "CUSTOMER" | "VENDOR";
export type ClientType = "CUST" | "VNDR" | "BOTH";
export type ClientOption = { id: string; name: string; type: ClientType };
export type ClientFilters = {
  type?: ClientType;
  useYn?: "Y" | "N";
  q?: string;
  sort?: string;
  direction?: "ASC" | "DESC";
};
export type ClientSelectionItem = { id: string; revision: string };
export type ClientSelection = { items: ClientSelectionItem[]; total: number };
export type ClientAccount = {
  id: string;
  bankCode: string | null;
  bankName: string | null;
  accountNumber: string | null;
  depositor: string | null;
};
export type ClientAssignment = {
  assignmentId: string;
  projectId: string;
  projectName: string;
  managementNo: string;
  developerName: string;
  jobCode: string;
  jobName: string;
  inputBeginDate: string;
  inputEndDate: string;
};
export type ClientAssignments = {
  items: ClientAssignment[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  basisDate: string;
};
export type ClientBulkTarget = { id: string; expectedRevision: string };
export type ClientBulkItem = {
  id: string;
  name: string;
  result: "applied" | "noChange" | "rejected";
  error?: ApiErrorBody;
};
export type ClientBulkResult = {
  items: ClientBulkItem[];
  applied: number;
  noChange: number;
  rejected: number;
};
export const clientsCommittedEvent = "sicost:clients-committed";
export function notifyClientsCommitted() {
  window.dispatchEvent(new Event(clientsCommittedEvent));
}
function query(values: Record<string, string | number | undefined>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return params.toString();
}
export function getClientOptions(
  purpose: ClientPurpose,
  q?: string,
  signal?: AbortSignal,
) {
  return request<{ items: ClientOption[] }>(
    `/api/clients/options?${query({ purpose, q })}`,
    { signal, cache: "no-store" },
  );
}
export function getClientSelection(
  filters: ClientFilters,
  signal?: AbortSignal,
) {
  return request<ClientSelection>(`/api/clients/selection?${query(filters)}`, {
    signal,
    cache: "no-store",
  });
}
export function getClientAccount(id: string, signal?: AbortSignal) {
  return request<ClientAccount>(
    `/api/clients/${encodeURIComponent(id)}/account`,
    { signal, cache: "no-store" },
  );
}
export function getClientAssignments(
  id: string,
  page = 1,
  size: 20 | 50 | 100 = 50,
  signal?: AbortSignal,
) {
  return request<ClientAssignments>(
    `/api/clients/${encodeURIComponent(id)}/assignments?${query({ page, size })}`,
    { signal, cache: "no-store" },
  );
}
export function saveClientBulkStatus(
  targetUseYn: "Y" | "N",
  items: ClientBulkTarget[],
) {
  return request<ClientBulkResult>("/api/clients/bulk-status", {
    method: "POST",
    body: JSON.stringify({ targetUseYn, items }),
  });
}
