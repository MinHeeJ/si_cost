import { request } from "../../shared/api";

export type CandidateContactStatus =
  | "BEFORE"
  | "DONE"
  | "ITVW"
  | "HOLD"
  | "CTRT";
export type CandidateContact = {
  sn: number;
  status: CandidateContactStatus;
  memo: string;
  contactDate: string;
  actorId: string;
  actorName: string;
};
export type CandidateContactsPage = {
  items: CandidateContact[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type CreateCandidateContact = {
  contactStatus: CandidateContactStatus;
  memo: string;
  expectedRevision: string;
};
export type CandidateContactCreated = {
  contact: CandidateContact;
  contactStatus: CandidateContactStatus;
  lastContactDate: string;
};
export type CandidateContactContext = {
  id: string;
  name: string;
  revision: string;
  contactStatus: CandidateContactStatus;
};
export function getCandidateContacts(
  id: string,
  page = 1,
  size: 20 | 50 | 100 = 50,
  signal?: AbortSignal,
) {
  return request<CandidateContactsPage>(
    `/api/candidates/${encodeURIComponent(id)}/contacts?${new URLSearchParams({ page: String(page), size: String(size) })}`,
    { signal },
  );
}
export function createCandidateContact(
  id: string,
  input: CreateCandidateContact,
) {
  return request<CandidateContactCreated>(
    `/api/candidates/${encodeURIComponent(id)}/contacts`,
    {
      method: "POST",
      body: JSON.stringify({
        contactStatus: input.contactStatus,
        memo: input.memo,
        expectedRevision: input.expectedRevision,
      }),
    },
  );
}
export function getCandidateContactContext(id: string, signal?: AbortSignal) {
  return request<CandidateContactContext>(
    `/api/candidates/${encodeURIComponent(id)}`,
    { signal },
  );
}
