import { request } from "../../shared/api";

export type AssignmentHistory = {
  sequence: number;
  type:
    | "INPUT"
    | "EXT"
    | "EARLY"
    | "UPRC"
    | "RATE"
    | "EXTP"
    | "KIND"
    | "CRCT"
    | "CNCL";
  applyDate: string;
  before: string | null;
  after: string | null;
  reasonCode: string | null;
  memo: string | null;
  newUnitPrice: string | null;
  newContractMm: string | null;
  retroactive: "Y" | "N";
  closeCross: "Y" | "N";
  corrected: "Y" | "N";
  correctionTargetSequence: number | null;
  actor: string;
  registeredAt: string;
};
export type AssignmentHistoryPage = {
  items: AssignmentHistory[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type AssignmentHistoryCorrection = {
  year: number;
  expectedRevision: string;
  correctContent: string;
  reasonCode: string;
};
export type AssignmentCorrectionResult = {
  original: AssignmentHistory;
  correction: AssignmentHistory;
  revision: string;
  changed: true;
};
export function getAssignmentHistory(
  id: string,
  query: { year: number; type?: string; page: number; size: 20 | 50 | 100 },
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    year: String(query.year),
    page: String(query.page),
    size: String(query.size),
  });
  if (query.type) params.set("type", query.type);
  return request<AssignmentHistoryPage>(
    `/api/assignments/${encodeURIComponent(id)}/history?${params}`,
    { signal },
  );
}
export const createAssignmentHistoryCorrection = (
  id: string,
  sn: number,
  body: AssignmentHistoryCorrection,
) =>
  request<AssignmentCorrectionResult>(
    `/api/assignments/${encodeURIComponent(id)}/history/${sn}/corrections`,
    { method: "POST", body: JSON.stringify(body) },
  );
