import { request } from "../../shared/api";
import { SALES_COMMITTED } from "../assignmentcandidatesops/assignmentCandidatesApi";
import { getSalesOpportunities as listOpportunities } from "../salesopportunitiesops/salesCommandsApi";
import { getDeveloperAssignments } from "../developers/peopleClient";
import { getMonths } from "../assignmentsops/assignmentActionsApi";
import type {
  Need,
  Opportunity,
  Result,
  Stage,
} from "../salesopportunitiesops/salesCommandsApi";
export type { Need, Opportunity, Result, Stage };
export async function getNeedWarnings(
  developerId: string,
  startMonth: string,
  opportunityId?: string,
) {
  const messages: string[] = [];
  let page = 1;
  for (;;) {
    const opportunities = await listOpportunities("ING", false, page);
    for (const opportunity of opportunities.items) {
      if (
        opportunity.id !== opportunityId &&
        opportunity.tentativeAssignments.some(
          (t) => t.developerId === developerId && t.status === "ACT",
        )
      )
        messages.push(
          `다른 기회(${opportunity.name})에 가배정되어 있습니다. 이 기회에도 가배정할 수 있습니다.`,
        );
    }
    if (page * opportunities.size >= opportunities.total) break;
    page++;
  }
  if (/^\d{4}-\d{2}$/.test(startMonth)) {
    let totalMm = 0;
    page = 1;
    for (;;) {
      const assignments = await getDeveloperAssignments(developerId, page);
      const totals = await Promise.all(
        assignments.items
          .filter((a) => a.assignmentStatus !== "CNCL")
          .map(async (a) => {
            const months = await getMonths(
              a.id,
              Number(startMonth.slice(0, 4)),
            );
            return months.items
              .filter((m) => m.month === startMonth)
              .reduce((sum, m) => sum + Number(m.monthlyMm), 0);
          }),
      );
      totalMm += totals.reduce((sum, mm) => sum + mm, 0);
      if (page * assignments.size >= assignments.total) break;
      page++;
    }
    if (totalMm >= 1)
      messages.push(
        `${Number(startMonth.slice(5))}월에 이미 ${totalMm.toFixed(2)} MM 투입 중입니다.`,
      );
  }
  return messages;
}
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
