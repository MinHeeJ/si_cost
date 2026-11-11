import { request } from "../../shared/api";

export type ClientType = "CUST" | "VNDR" | "BOTH";
export type Client = {
  id: string;
  type: ClientType;
  name: string;
  businessNo: string;
  ceo: string | null;
  industryCode: string | null;
  industry: string | null;
  industryActive: boolean | null;
  contactName: string | null;
  phone: string | null;
  taxEmail: string | null;
  bankCode: string | null;
  bankName: string | null;
  bankActive: boolean | null;
  depositor: string | null;
  account: string | null;
  useYn: "Y" | "N";
  projectCount: number;
  personCount: number;
  basisDate: string;
  revision: string;
  historyCount: number;
};
export type ClientFields = {
  type: ClientType;
  name: string;
  ceo?: string | null;
  industryCode?: string | null;
  contactName?: string | null;
  phone?: string | null;
  taxEmail?: string | null;
  bankCode?: string;
  accountNumber?: string;
  depositor?: string;
};
export type CreateClient = ClientFields & { businessNo: string };
export type PutClient = ClientFields & {
  accountAction: "KEEP" | "REPLACE" | "CLEAR";
  expectedRevision: string;
};
export type ClientFilters = {
  type?: ClientType;
  useYn?: "Y" | "N";
  q?: string;
  sort?: string;
  direction?: "ASC" | "DESC";
  page: number;
  size: 20 | 50 | 100;
};
export type ClientsPage = {
  items: Client[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  basisDate: string;
};
export type ClientSaveResult = {
  id: string;
  changed: boolean;
  message: string;
  warnings: { field: string; message: string }[];
  client: Client;
};
export type ClientCode = { code: string; name: string };
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
export type ClientAssignmentsPage = {
  items: ClientAssignment[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  basisDate: string;
};
const path = (id: string) => `/api/clients/${encodeURIComponent(id)}`;
export function getClients(
  filters: ClientFilters,
  signal?: AbortSignal,
): Promise<ClientsPage> {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return request(`/api/clients?${query}`, { signal });
}
export const getClient = (id: string, signal?: AbortSignal) =>
  request<Client>(path(id), { signal });
export const createClient = (body: CreateClient) =>
  request<ClientSaveResult>("/api/clients", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const putClient = (id: string, body: PutClient) =>
  request<ClientSaveResult>(path(id), {
    method: "PUT",
    body: JSON.stringify(body),
  });
export const getClientCodes = (group: "CLNT_TYPE" | "INDUTY" | "BANK") =>
  request<{ items: ClientCode[] }>(`/api/codes/${group}`);
export const getClientAssignments = (
  id: string,
  page = 1,
  signal?: AbortSignal,
) =>
  request<ClientAssignmentsPage>(
    `${path(id)}/assignments?page=${page}&size=50`,
    { signal },
  );
export const setClientStatus = (client: Client) =>
  request<{
    items: {
      id: string;
      result: "applied" | "noChange" | "rejected";
      error?: { message: string };
    }[];
  }>("/api/clients/bulk-status", {
    method: "POST",
    body: JSON.stringify({
      targetUseYn: client.useYn === "Y" ? "N" : "Y",
      items: [{ id: client.id, expectedRevision: client.revision }],
    }),
  });
export const getClientAccount = (id: string) =>
  request<{ accountNumber: string | null }>(`${path(id)}/account`, {
    cache: "no-store",
  });
export const CLIENTS_COMMITTED = "sicost:clients-committed";
export function notifyClientsCommitted(id: string) {
  window.dispatchEvent(new CustomEvent(CLIENTS_COMMITTED, { detail: { id } }));
}
