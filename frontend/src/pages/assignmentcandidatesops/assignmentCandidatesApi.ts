import { request } from "../../shared/api";
import type { Create, Page } from "../assignmentsops/assignmentActionsApi";

export type AssignmentCandidate = {
  id: string;
  opportunityId: string;
  projectId: string;
  projectName: string;
  developerId: string;
  developerName: string;
  jobCode: string;
  startDate: string;
  endDate: string;
  contractMm: string;
  billingUnitPrice: string | null;
  revision: string;
};
export type CandidateResult = {
  changed: boolean;
  revision: string;
  warnings: string[];
  opportunityId: string;
  tentativeId: string;
};
export type ConfirmationResult = CandidateResult & { assignmentId: string };
export const SALES_COMMITTED = "sicost:sales-committed";
export function notifyAssignmentCandidateCommitted(result: CandidateResult) {
  if (!result.changed) return;
  window.dispatchEvent(new CustomEvent(SALES_COMMITTED, { detail: result }));
  window.dispatchEvent(
    new CustomEvent("sicost:assignments-committed", { detail: result }),
  );
}
export const getAssignmentCandidates = (page = 1) =>
  request<Page<AssignmentCandidate>>(
    `/api/assignment-candidates?page=${page}&size=50`,
  );
export async function confirmAssignmentCandidate(
  candidate: AssignmentCandidate,
  values: Create,
) {
  const result = await request<ConfirmationResult>(
    `/api/assignment-candidates/${encodeURIComponent(candidate.id)}/confirm`,
    {
      method: "POST",
      body: JSON.stringify({
        ...values,
        inputType: "NEW",
        contractDeveloperId: null,
        sourceTentId: candidate.id,
        expectedRevision: candidate.revision,
      }),
    },
  );
  notifyAssignmentCandidateCommitted(result);
  return result;
}
export async function dropAssignmentCandidate(candidate: AssignmentCandidate) {
  const result = await request<CandidateResult>(
    `/api/assignment-candidates/${encodeURIComponent(candidate.id)}/drop`,
    {
      method: "POST",
      body: JSON.stringify({ expectedRevision: candidate.revision }),
    },
  );
  notifyAssignmentCandidateCommitted(result);
  return result;
}
