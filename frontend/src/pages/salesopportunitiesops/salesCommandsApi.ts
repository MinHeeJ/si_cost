import { request } from "../../shared/api";
import { SALES_COMMITTED } from "../assignmentcandidatesops/assignmentCandidatesApi";
import { getSharedCodes } from "../shared/sharedApi";

export type Stage = "FIND" | "PROP" | "NEGO" | "PRE";
export type Need = {
  needSn?: number;
  jobCode?: string;
  gradeCode?: string | null;
  count?: number;
  startMonth?: string;
  months?: number;
  monthlyMm?: string;
  expectedRate?: string | null;
};
export type QuoteRow = {
  needSn: number | null;
  jobCode: string | null;
  gradeCode: string | null;
  quantityMm: string | null;
  effectiveRate: string | null;
  unitMonthlyCost: string | null;
  revenue: string | null;
  cost: string | null;
  minimumRate: string | null;
  targetRate: string | null;
  costBasisMonth: string | null;
  cohort: "JOB_GRADE" | "GRADE" | null;
  cohortCount: number;
  fallbackUsed: boolean;
  complete: boolean;
  unknownReason: string | null;
};
export type Quote = {
  rows: QuoteRow[];
  issues: {
    field: string | null;
    needSn: number | null;
    reason: string;
    status: "INCOMPLETE" | "UNKNOWN";
  }[];
  revenue: string | null;
  cost: string | null;
  rawProfitRate: string | null;
  storedProfitRate: string | null;
  minimumAmount: string | null;
  targetAmount: string | null;
  targetUnavailableReason: string | null;
  judgement:
    | "LOSS"
    | "APPROVAL_REQUIRED"
    | "BELOW_TARGET"
    | "MET"
    | "UNKNOWN"
    | "INCOMPLETE";
  standardMonth: string;
  approvalThreshold: string | null;
  reapprovalGap: string | null;
  targetMargin: string | null;
  formula: string;
};
export type Opportunity = {
  id: string;
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
  workplace: string | null;
  memo: string | null;
  status: "ING" | "WIN" | "LOSE" | "STOP";
  revision: string;
  needs: Need[];
  quote: Quote;
  approvalStatus: "NONE" | "WAIT" | "OK" | "REJ";
  approvedProfitRate: string | null;
  projectId: string | null;
  resultDate: string | null;
  lossReasonCode: string | null;
  lossMemo: string | null;
  nextTask: string | null;
  nextDue: string | null;
  tentativeAssignments: {
    id: string;
    developerId: string;
    status: string;
    needSn: number;
    startMonth: string;
    sourceScreenId?: string;
    processedAt?: string | null;
  }[];
  approvalHistory: {
    histSn: number;
    action: string;
    profitRate: string;
    reason: string | null;
    registeredBy: string;
    registeredAt: string;
  }[];
};
export type OpportunityPage = {
  items: Opportunity[];
  total: number;
  page: number;
  size: number;
  summary: {
    opportunityCount: number;
    staleCount: number;
    amount: string;
    weightedAmount: string;
    weightedPeople: string;
  };
  stageTotals: { stage: Stage; count: number; amount: string }[];
};
export type Result = {
  changed: boolean;
  revision: string;
  warnings: string[];
  opportunityId: string;
};
export type WinInput = {
  contractAmount: string;
  contractType: "TK" | "MO" | "DP";
  startDate: string;
  endDate: string;
  projectStatus: "ING" | "PRE";
};
export type WinResult = Result & { projectId: string };
export const getSalesOpportunity = (id: string) =>
  request<Opportunity>(`/api/sales-opportunities/${encodeURIComponent(id)}`);
export function getSalesOpportunities(
  status: "ING" | "WIN" | "LOSE" | "STOP",
  includeStopped = false,
  page = 1,
  ordering?: { sort: "amount" | "resultDate"; direction: "ASC" | "DESC" },
  size: 20 | 50 | 100 = 50,
) {
  return request<OpportunityPage>(
    `/api/sales-opportunities?status=${status}&includeStopped=${includeStopped}&page=${page}&size=${size}${ordering ? `&sort=${ordering.sort}&direction=${ordering.direction}` : ""}`,
  );
}
export const createSalesOpportunityQuotePreview = (needs: Need[]) =>
  request<Quote>("/api/sales-opportunities/quote-preview", {
    method: "POST",
    body: JSON.stringify({ needs }),
  });
async function command<T extends Result>(
  o: Opportunity,
  action: string,
  values: object = {},
): Promise<T> {
  const result = await request<T>(
    `/api/sales-opportunities/${encodeURIComponent(o.id)}/${action}`,
    {
      method: "POST",
      body: JSON.stringify({ expectedRevision: o.revision, ...values }),
    },
  );
  if (result.changed) {
    window.dispatchEvent(new CustomEvent(SALES_COMMITTED, { detail: result }));
    window.dispatchEvent(
      new CustomEvent("sicost:staffing-committed", { detail: result }),
    );
    if (action === "win")
      window.dispatchEvent(
        new CustomEvent("sicost:projects-committed", { detail: result }),
      );
  }
  return result;
}
export const saveSalesOpportunityStage = (
  o: Opportunity,
  values: { stage: Stage; probability?: string; manualProbability?: boolean },
) => command<Result>(o, "stage", values);
export const createSalesOpportunityApproval = (
  o: Opportunity,
  action: "OK" | "REJ",
  reason: string | null,
) => command<Result>(o, "approval", { action, reason });
export const createSalesOpportunityLoss = (
  o: Opportunity,
  reasonCode: string,
  memo: string | null,
) => command<Result>(o, "lose", { reasonCode, memo });
export const saveSalesOpportunityStop = (o: Opportunity) =>
  command<Result>(o, "stop");
export const saveSalesOpportunityResume = (o: Opportunity) =>
  command<Result>(o, "resume");
export const createSalesOpportunityWin = (o: Opportunity, values: WinInput) =>
  command<WinResult>(o, "win", values);
export const getLossReasons = () => getSharedCodes("LOSE_RSN");
