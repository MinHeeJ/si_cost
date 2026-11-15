import { request } from "../../shared/api";

export type RecommendationSelection = {
  actionCode: "ASGN" | "TENT" | "EXCL" | null;
  exclusionReasonCode: string | null;
  actedBy: string | null;
  actedAt: string | null;
  assignmentId: string | null;
  tentativeAssignmentId: string | null;
  finalOpportunityId: string | null;
  finalNeedSn: string | null;
};
export type RecommendationCandidate = {
  rank: number;
  developer: {
    developerId: string;
    name: string;
    managementNumber: string;
    jobCode: string;
    subJobCode: string | null;
    gradeCode: string | null;
    employeeTypeCode: string;
    detailId: string;
  };
  opportunityId: string | null;
  needSn: string | null;
  jobCode: string;
  startMonth: string;
  requestedGradeCode: string;
  totalScore: string;
  scoreDetail?: unknown;
  scoreItems: {
    itemCode: string;
    baseScore: string;
    weight: string;
    score: string;
    basis: string;
  }[];
  reason: string;
  otherTentativeAssignments: {
    tentativeAssignmentId: string;
    opportunityId: string;
    opportunityName: string;
    needSn: string;
    startMonth: string;
  }[];
  selection: RecommendationSelection;
  revision: string;
  targetRevision: string;
  candidateRevision: string;
  nextPosition: {
    opportunityName: string;
    probability: string;
    salesStepCode: string;
    customerId: string | null;
    customerName: string | null;
    originalStartMonth: string;
    actualStartMonth: string;
    gradeCode: string;
    remainingCount: number;
    gapMonths: number;
    waitDays: number;
    alreadyTentativelyAssigned: boolean;
    reasonChips: string[];
  } | null;
};
export type RecommendationActionResult = {
  rcmdId: string;
  rank: number;
  changed: boolean;
  selection: RecommendationSelection;
  revision: string;
  message: string;
  releasedTentativeAssignmentId: string | null;
};
export type RecommendationWarning = { code: string; message: string };
export type RecommendationTentativeRequest = {
  needSn: string;
  expectedRevision: string;
  targetRevision: string;
  candidateRevision: string;
  confirmedWarnings: string[];
};
function actionPath(rcmdId: string, rank: number, action: string) {
  if (
    !/^[1-9][0-9]{0,11}$/.test(rcmdId) ||
    !Number.isInteger(rank) ||
    rank < 1 ||
    rank > 5
  )
    throw new Error("추천 식별자를 확인해 주세요.");
  return `/api/workforce-recommendations/${rcmdId}/candidates/${rank}/${action}`;
}
export const createWorkforceRecommendationExclusion = (
  rcmdId: string,
  rank: number,
  body: { reasonCode: string; expectedRevision: string },
) =>
  request<RecommendationActionResult>(actionPath(rcmdId, rank, "exclude"), {
    method: "POST",
    body: JSON.stringify(body),
  });
export const createWorkforceRecommendationTentativeAssignment = (
  rcmdId: string,
  rank: number,
  body: RecommendationTentativeRequest,
) =>
  request<RecommendationActionResult>(
    actionPath(rcmdId, rank, "tentative-assignment"),
    { method: "POST", body: JSON.stringify(body) },
  );
export function notifyRecommendationCommitted(
  result: RecommendationActionResult,
) {
  if (!result.changed) return;
  for (const event of [
    "sicost:sales-committed",
    "sicost:assignments-committed",
  ])
    window.dispatchEvent(new CustomEvent(event, { detail: result }));
}
