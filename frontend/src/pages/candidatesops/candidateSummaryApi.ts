import { request } from "../../shared/api";

export type CandidateSummary = {
  total: number;
  inProgress: number;
  interviewScheduled: number;
  availableByNextMonthStart: number;
  overdueContact: number;
};

export function getCandidateSummary(signal?: AbortSignal) {
  return request<CandidateSummary>("/api/candidates/summary", {
    cache: "no-store",
    signal,
  });
}
