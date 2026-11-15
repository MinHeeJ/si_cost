import { request } from "../../shared/api";
import type { Create } from "../assignmentsops/assignmentActionsApi";

export type Condition = {
  targetType: "OPPORTUNITY" | "PROJECT";
  targetId: string;
  jobCode: string;
  startMonth: string;
  requestedGradeCode: string;
  needSn: string | null;
};
export type Need = {
  needSn: string;
  jobCode: string;
  gradeCode: string | null;
  needCount: number;
  startMonth: string;
  periodMonths: number;
  monthlyMm: string;
  activeCount: number;
  remainingCount: number;
  revision: string;
};
export type Target = {
  targetType: Condition["targetType"];
  targetId: string;
  name: string;
  contractTypeCode: string | null;
  projectIdAfterWin: string | null;
  contractStartDate: string | null;
  contractEndDate: string | null;
  needs: Need[];
  defaults: {
    jobCode: string | null;
    startMonth: string | null;
    requestedGradeCode: string;
    needSn: string | null;
  };
  revision: string;
};
export type Targets = {
  items: Target[];
  firstTargetId: string | null;
  serverDate: string;
  currentMonth: string;
  baseMonth: string;
  revision: string;
};
export type Selection = {
  actionCode: "ASGN" | "TENT" | "EXCL" | null;
  exclusionReasonCode: string | null;
  actedBy: string | null;
  actedAt: string | null;
  assignmentId: string | null;
  tentativeAssignmentId: string | null;
  finalOpportunityId: string | null;
  finalNeedSn: string | null;
};
export type ScoreItem = {
  itemCode: string;
  baseScore: string;
  weight: string;
  score: string;
  basis: string;
};
export type Candidate = {
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
  scoreItems: ScoreItem[];
  scoreDetail: unknown;
  reason: string;
  otherTentativeAssignments: {
    tentativeAssignmentId: string;
    opportunityId: string;
    opportunityName: string;
    needSn: string;
    startMonth: string;
  }[];
  selection: Selection;
  revision: string;
  targetRevision: string;
  candidateRevision: string;
  nextPosition: unknown;
};
export type Data = {
  rcmdId: string | null;
  recommendationType: "AI" | "NEXT";
  appliedCondition: Condition | null;
  sourceAssignmentId: string | null;
  executedAt: string | null;
  executedBy: string | null;
  candidates: Candidate[];
  exclusions: {
    totalCandidateCount: number;
    excludedUniqueCount: number;
    byReason: { reason: string; count: number }[];
    diagnostics: { developerId: string; reasons: string[] }[];
    reasonCountsOverlap: true;
  };
  message: string;
  revision: string;
  shortageMonths: unknown[];
};
export type HistoryItem = {
  rcmdId: string;
  recommendationType: string;
  targetId: string;
  jobCode: string;
  startMonth: string;
  requestedGradeCode: string;
  executedAt: string;
  executedBy: string;
  candidateCount: number;
};
export type HistoryData = {
  items: HistoryItem[];
  page: number;
  size: number;
  totalCount: number;
};
export type Warning = { code: string; message: string };
export type Action = {
  rcmdId: string;
  rank: number;
  changed: boolean;
  selection: Selection;
  revision: string;
  message: string;
  releasedTentativeAssignmentId: string | null;
};
export const getTargets = () =>
  request<Targets>("/api/workforce-recommendations/targets");
export const createRecommendation = (condition: Condition) =>
  request<Data>("/api/workforce-recommendations", {
    method: "POST",
    body: JSON.stringify(condition),
  });
export const getRecommendation = (id: string) =>
  request<Data>(`/api/workforce-recommendations/${encodeURIComponent(id)}`);
export function getHistory(query: {
  targetType?: string;
  targetId?: string;
  job?: string;
  page: number;
  size: number;
}) {
  const q = new URLSearchParams();
  Object.entries(query).forEach(([k, v]) => {
    if (v !== "" && v != null) q.set(k, String(v));
  });
  return request<HistoryData>(`/api/workforce-recommendations/history?${q}`);
}
export const excludeCandidate = (
  id: string,
  candidate: Candidate,
  reasonCode: string,
) =>
  request<Action>(
    `/api/workforce-recommendations/${encodeURIComponent(id)}/candidates/${candidate.rank}/exclude`,
    {
      method: "POST",
      body: JSON.stringify({
        reasonCode,
        expectedRevision: candidate.revision,
      }),
    },
  );
export const selectTentative = (
  id: string,
  candidate: Candidate,
  needSn: string,
  confirmedWarnings: string[],
) =>
  request<Action>(
    `/api/workforce-recommendations/${encodeURIComponent(id)}/candidates/${candidate.rank}/tentative-assignment`,
    {
      method: "POST",
      body: JSON.stringify({
        needSn,
        expectedRevision: candidate.revision,
        targetRevision: candidate.targetRevision,
        candidateRevision: candidate.candidateRevision,
        confirmedWarnings,
      }),
    },
  );
export async function saveRecommendedAssignment(
  id: string,
  candidate: Candidate,
  condition: Condition,
  value: Create,
) {
  const same =
    value.developerId === candidate.developer.developerId &&
    value.projectId === condition.targetId;
  const saved = await request<{ assignment: { id: string } }>(
    "/api/assignments",
    {
      method: "POST",
      body: JSON.stringify({
        ...value,
        ...(same
          ? { recommendationRef: { rcmdId: id, rank: candidate.rank } }
          : {}),
      }),
    },
  );
  return { assignmentId: saved.assignment.id };
}
export function announceRecommendationCommit() {
  for (const event of [
    "sicost:sales-committed",
    "sicost:assignments-committed",
    "sicost:workforce-committed",
  ])
    window.dispatchEvent(new Event(event));
}
