import { request } from "../../shared/api";
export type TentativeSelection = {
  opportunityId: string;
  needSn: number;
  developerId: string;
  startMonth: string;
  sourceScreenId: "UI-29" | "UI-05" | "UI-30";
  expectedRevision: string;
  previousOpportunityRevision?: string;
  warningConfirmed?: boolean;
};
export type TentativeResult = {
  changed: boolean;
  revision: string;
  warnings: string[];
  opportunityId: string;
  tentativeId: string;
};
export async function createTentativeAssignment(input: TentativeSelection) {
  const result = await request<TentativeResult>("/api/tentative-assignments", {
    method: "POST",
    body: JSON.stringify(input),
  });
  if (result.changed)
    window.dispatchEvent(
      new CustomEvent("sicost:sales-committed", { detail: result }),
    );
  return result;
}
