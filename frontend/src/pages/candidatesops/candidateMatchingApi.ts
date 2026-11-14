import { ApiError, type ApiErrorBody } from "../../shared/api";
import type {
  CandidateFilters,
  CandidatesPage,
} from "../candidates/peopleClient";
import { getCandidates } from "../candidates/peopleClient";

export type CandidateCalculationIssue = {
  field: string | null;
  needSn: number | null;
  reason: string;
  status: "UNKNOWN" | "INCOMPLETE";
};
export type CandidateRead<T> = { data: T; issues: CandidateCalculationIssue[] };
export type CandidateShortage = {
  jobCode: string;
  firstShortageMonth: string;
  maximumShortage: string;
  availableUntil: string;
  availableCandidateCount: number;
  activeCandidateCount: number;
};
export type CandidateShortagePage = {
  items: CandidateShortage[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  includeUnfilled: false;
  from: string;
  months: 6;
};
export type CandidatePosition = {
  opportunityId: string;
  name: string;
  needSn: number;
  probability: string;
  jobCode: string;
  gradeCode: string | null;
  startMonth: string;
  endMonth: string;
  availableDate: string;
  effectiveRate: string;
  candidateCost: string;
  profitRate: string;
  belowApprovalThreshold: boolean;
  gradeDistance: number | null;
};
export type CandidatePositions = {
  candidateId: string;
  items: CandidatePosition[];
  total: number;
  limit: 4;
};
export type CandidateShortageQuery = {
  page: number;
  size: 20 | 50 | 100;
  sort?: "firstShortageMonth" | "maximumShortage" | "jobCode";
  direction?: "ASC" | "DESC";
};
async function read<T>(
  path: string,
  signal?: AbortSignal,
): Promise<CandidateRead<T>> {
  const response = await fetch(path, {
    signal,
    redirect: "error",
    headers: { Accept: "application/json" },
  });
  const body = await response.json();
  if (!response.ok) throw new ApiError(response.status, body as ApiErrorBody);
  if (body.success !== true || !body.meta || !body.data)
    throw new Error("올바르지 않은 서버 응답입니다.");
  return { data: body.data as T, issues: body.meta.issues ?? [] };
}
export function getCandidateShortages(
  query: CandidateShortageQuery,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined) params.set(key, String(value));
  });
  return read<CandidateShortagePage>(
    `/api/candidates/shortages?${params}`,
    signal,
  );
}
export function getCandidatePositions(id: string, signal?: AbortSignal) {
  return read<CandidatePositions>(
    `/api/candidates/${encodeURIComponent(id)}/positions`,
    signal,
  );
}
export type CandidateShortageContext = Pick<
  CandidateShortage,
  "jobCode" | "availableUntil"
>;
export async function getShortageCandidates(
  filters: CandidateFilters,
  context: CandidateShortageContext,
  signal?: AbortSignal,
): Promise<CandidatesPage> {
  // Apply the approved date bound on the server; retain active-status filtering across all pages.
  const first = await getCandidates(
    {
      ...filters,
      jobCode: context.jobCode,
      availability: undefined,
      availableUntil: context.availableUntil,
      page: 1,
      size: 100,
    },
    signal,
  );
  const rows = [...first.items];
  for (let page = 2; (page - 1) * 100 < first.total; page++) {
    if (signal?.aborted) throw new DOMException("조회 취소", "AbortError");
    const next = await getCandidates(
      {
        ...filters,
        jobCode: context.jobCode,
        availability: undefined,
        availableUntil: context.availableUntil,
        page,
        size: 100,
      },
      signal,
    );
    rows.push(...next.items);
  }
  const matching = rows.filter(
    (row) =>
      row.availableDate <= context.availableUntil &&
      !["HOLD", "CTRT"].includes(row.contactStatus),
  );
  const offset = (filters.page - 1) * filters.size;
  return {
    items: matching.slice(offset, offset + filters.size),
    total: matching.length,
    page: filters.page,
    size: filters.size,
  };
}
