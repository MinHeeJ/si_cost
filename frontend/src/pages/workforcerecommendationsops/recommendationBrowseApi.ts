import { request } from "../../shared/api";
import type { RecommendationCandidate } from "../candidates/recommendationActionsApi";

export type TargetType = "OPPORTUNITY" | "PROJECT";
export type RecommendationCondition = {
  targetType: TargetType;
  targetId: string;
  jobCode: string;
  startMonth: string;
  requestedGradeCode: string;
  needSn: string | null;
};
export type RecommendationNeed = {
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
export type RecommendationTarget = {
  targetType: TargetType;
  targetId: string;
  name: string;
  contractTypeCode: string | null;
  projectIdAfterWin: string | null;
  contractStartDate: string | null;
  contractEndDate: string | null;
  needs: RecommendationNeed[];
  defaults: {
    jobCode: string | null;
    startMonth: string | null;
    requestedGradeCode: string;
    needSn: string | null;
  };
  revision: string;
};
export type RecommendationTargets = {
  items: RecommendationTarget[];
  firstTargetId: string | null;
  serverDate: string;
  currentMonth: string;
  baseMonth: string;
  revision: string;
};
export type RecommendationHistoryQuery = {
  sort?: keyof RecommendationHistoryItem;
  direction?: "ASC" | "DESC";
  targetType?: TargetType;
  targetId?: string;
  job?: string;
  recommendationType?: "AI" | "NEXT";
  page: number;
  size: number;
};
export type RecommendationHistoryItem = {
  rcmdId: string;
  recommendationType: "AI" | "NEXT";
  targetId: string;
  jobCode: string;
  startMonth: string;
  requestedGradeCode: string;
  executedAt: string;
  executedBy: string;
  candidateCount: number;
};
export type RecommendationHistory = {
  items: RecommendationHistoryItem[];
  page: number;
  size: number;
  totalCount: number;
};
export type RecommendationSnapshot = {
  rcmdId: string | null;
  recommendationType: "AI" | "NEXT";
  appliedCondition: RecommendationCondition | null;
  exclusions?: {
    totalCandidateCount: number;
    excludedUniqueCount: number;
    byReason: { reason: string; count: number }[];
    diagnostics: { developerId: string; reasons: string[] }[];
    reasonCountsOverlap: boolean;
  };
  sourceAssignmentId: string | null;
  executedAt: string | null;
  executedBy: string | null;
  candidates: (RecommendationCandidate & { scoreDetail: unknown })[];
  message: string;
  revision: string;
};
export function getWorkforceRecommendationTargets(signal?: AbortSignal) {
  return request<RecommendationTargets>(
    "/api/workforce-recommendations/targets",
    { signal },
  );
}
export function getWorkforceRecommendationHistory(
  query: RecommendationHistoryQuery,
  signal?: AbortSignal,
) {
  const search = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined) search.set(key, String(value));
  });
  return request<RecommendationHistory>(
    `/api/workforce-recommendations/history?${search}`,
    { signal },
  );
}
export function getRecommendationHistorySnapshot(
  rcmdId: string,
  signal?: AbortSignal,
) {
  return request<RecommendationSnapshot>(
    `/api/workforce-recommendations/${encodeURIComponent(rcmdId)}`,
    { signal },
  );
}
export function requestTargetRecommendation(
  condition: RecommendationCondition,
  signal?: AbortSignal,
) {
  return request<RecommendationSnapshot>("/api/workforce-recommendations", {
    method: "POST",
    body: JSON.stringify(condition),
    signal,
  });
}
