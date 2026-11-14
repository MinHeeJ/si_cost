import { request } from "../../shared/api";
import { notifyAssignmentCandidateCommitted } from "../assignmentcandidatesops/assignmentCandidatesApi";

export type SalesTentative = {
  id: string;
  opportunityId: string;
  needSn: number;
  developerId: string;
  startMonth: string;
  status: "ACT" | "REL" | "CNVT" | "DONE" | "DROP";
  sourceScreenId: "UI-29" | "UI-05" | "UI-30";
  processedAt: string | null;
};
export type TentativeReleaseResult = {
  changed: boolean;
  revision: string;
  warnings: string[];
  opportunityId: string;
  tentativeId: string;
};
export async function releaseTentativeAssignment(
  id: string,
  expectedRevision: string,
  reason?: string,
) {
  const result = await request<TentativeReleaseResult>(
    `/api/tentative-assignments/${encodeURIComponent(id)}/release`,
    {
      method: "POST",
      body: JSON.stringify({
        expectedRevision,
        ...(reason === undefined ? {} : { reason }),
      }),
    },
  );
  notifyAssignmentCandidateCommitted(result);
  return result;
}
