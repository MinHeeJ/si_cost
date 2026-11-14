import { request } from "../../shared/api";
import { SALES_COMMITTED } from "../assignmentcandidatesops/assignmentCandidatesApi";
import type {
  Need,
  Opportunity,
  Result,
  Stage,
} from "../salesopportunitiesops/salesCommandsApi";
export type { Need, Opportunity, Result, Stage };
export type TentativeSelection = {
  needSn?: number;
  needIndex?: number;
  developerId: string;
  startMonth: string;
  sourceScreenId: "UI-29" | "UI-05" | "UI-30";
};
export type OpportunityInput = {
  name: string;
  customerId: string;
  partId: string;
  stage: Stage;
  probability: string;
  manualProbability: boolean;
  amount: string;
  startMonth: string;
  contractType: "TK" | "MO" | "DP" | null;
  months: number | null;
  nextTask: string | null;
  nextDue: string | null;
  workplace: string | null;
  memo: string | null;
  needs: Need[];
  tentativeSelections?: TentativeSelection[];
};
async function save(
  path: string,
  method: "POST" | "PUT",
  body: OpportunityInput | (OpportunityInput & { expectedRevision: string }),
) {
  const result = await request<Result>(path, {
    method,
    body: JSON.stringify(body),
  });
  if (result.changed) {
    window.dispatchEvent(new CustomEvent(SALES_COMMITTED, { detail: result }));
    window.dispatchEvent(
      new CustomEvent("sicost:staffing-committed", { detail: result }),
    );
  }
  return result;
}
export const createSalesOpportunity = (body: OpportunityInput) =>
  save("/api/sales-opportunities", "POST", body);
export const putSalesOpportunity = (
  id: string,
  body: OpportunityInput & { expectedRevision: string },
) => save(`/api/sales-opportunities/${encodeURIComponent(id)}`, "PUT", body);
export {
  getSalesOpportunity,
  getSalesOpportunities,
} from "../salesopportunitiesops/salesCommandsApi";
export {
  getPeriodContext,
  getSharedCodes,
  getSharedParameter,
} from "../shared/sharedApi";
export { getClientOptions } from "../clientsops/clientActionsApi";
export { getSalesPartOptions } from "../salespartsops/partStandardsApi";
export { getDevelopers } from "../developers/peopleClient";
