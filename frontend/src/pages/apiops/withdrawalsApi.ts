import {
  authenticatedFetch,
  ApiError,
  request,
  type ApiErrorBody,
} from "../../shared/api";
import { saveMasterDataDownload } from "../exportsops/masterDataExportApi";

export type WithdrawalQuery = {
  sort?:
    | "name"
    | "project"
    | "job"
    | "date"
    | "days"
    | "history"
    | "extension"
    | "next";
  direction?: "ASC" | "DESC";
  year?: number;
  period: "ALL" | "SOON" | "LATER" | "PAST";
  extension: "ALL" | "H" | "M" | "L" | "UNSET";
  job?: string;
  page: number;
  size: 20 | 50 | 100;
};
export type WithdrawalNext = {
  kind: "CONFIRMED" | "CANDIDATE" | "TENTATIVE";
  assignmentId: string | null;
  tentativeAssignmentId: string | null;
  projectId: string | null;
  opportunityId: string | null;
  name: string;
  startDate: string | null;
  startMonth: string | null;
};
export type WithdrawalRow = {
  assignmentId: string;
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
  projectId: string;
  projectName: string;
  jobCode: string;
  withdrawalDate: string;
  remainingDays: number;
  extensionCount: number;
  earlyWithdrawal: boolean;
  extensionPossibility: "H" | "M" | "L" | null;
  extensionEditable: boolean;
  nextAssignments: WithdrawalNext[];
  recommendationCount: number;
  canRecommend: boolean;
  canChangeTentative: boolean;
  revision: string;
};
export type WithdrawalsData = {
  items: WithdrawalRow[];
  year: number;
  page: number;
  size: 20 | 50 | 100;
  totalCount: number;
  serverDate: string;
  baseMonth: string;
  guidance: {
    visible: boolean;
    withinThreeMonthsPeople: number;
    withoutNextAssignmentPeople: number;
    withMatchingPositionsPeople: number;
    message: string;
  };
};
export type NextPositionCandidate = {
  rank: number;
  opportunityId: string | null;
  needSn: string | null;
  jobCode: string;
  startMonth: string;
  totalScore: string;
  revision: string;
  targetRevision: string;
  candidateRevision: string;
  reason: string;
  scoreItems: {
    itemCode: string;
    baseScore: string;
    weight: string;
    score: string;
    basis: string;
  }[];
  selection: { actionCode: "TENT" | "ASGN" | "EXCL" | null };
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
export type NextPositionData = {
  rcmdId: string | null;
  candidates: NextPositionCandidate[];
  message: string;
  revision: string;
  shortageMonths: { month: string; jobCode: string; shortagePeople: string }[];
};
export type WithdrawalWarning = { code: string; message: string };
const params = (q: Partial<WithdrawalQuery>) => {
  const p = new URLSearchParams();
  Object.entries(q).forEach(([k, v]) => {
    if (v !== undefined) p.set(k, String(v));
  });
  return p;
};
export const getWithdrawals = (q: WithdrawalQuery, signal?: AbortSignal) =>
  request<WithdrawalsData>(`/api/withdrawals?${params(q)}`, { signal });
export const saveWithdrawalExtensionPossibility = (
  row: WithdrawalRow,
  year: number,
  extensionPossibility: WithdrawalRow["extensionPossibility"],
) =>
  request<{ changed: boolean; revision: string }>(
    `/api/withdrawals/${encodeURIComponent(row.assignmentId)}/extension-possibility`,
    {
      method: "POST",
      body: JSON.stringify({
        year,
        expectedRevision: row.revision,
        extensionPossibility,
      }),
    },
  );
export const createNextPositionRecommendation = (
  row: WithdrawalRow,
  year: number,
) =>
  request<NextPositionData>(
    `/api/withdrawals/${encodeURIComponent(row.assignmentId)}/next-positions`,
    {
      method: "POST",
      body: JSON.stringify({ year, expectedRevision: row.revision }),
    },
  );
export const getWithdrawalRecommendation = (rcmdId: string) =>
  request<NextPositionData>(
    `/api/workforce-recommendations/${encodeURIComponent(rcmdId)}`,
  );
export const selectWithdrawalPosition = (
  rcmdId: string,
  candidate: NextPositionCandidate,
  confirmedWarnings: string[],
) =>
  request<{ message: string; changed: boolean }>(
    `/api/workforce-recommendations/${encodeURIComponent(rcmdId)}/candidates/${candidate.rank}/tentative-assignment`,
    {
      method: "POST",
      body: JSON.stringify({
        needSn: candidate.needSn,
        expectedRevision: candidate.revision,
        targetRevision: candidate.targetRevision,
        candidateRevision: candidate.candidateRevision,
        confirmedWarnings,
      }),
    },
  );
export async function downloadWithdrawals(query: WithdrawalQuery) {
  if (!window.matchMedia("(min-width: 1024px)").matches)
    throw new Error("다운로드는 PC에서 이용해 주세요.");
  const { year, period, extension, job, sort, direction } = query;
  const response = await authenticatedFetch(
    `/api/withdrawals/export?${params({ year, period, extension, ...(job ? { job } : {}), sort, direction })}`,
    {
      redirect: "error",
      headers: {
        Accept:
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/json",
      },
    },
  );
  if (response.headers.get("Content-Type")?.includes("application/json"))
    throw new ApiError(
      response.status,
      (await response.json()) as ApiErrorBody,
    );
  if (
    !response.ok ||
    !response.headers
      .get("Content-Type")
      ?.includes(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      )
  )
    throw new Error("다운로드 응답을 확인할 수 없습니다.");
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const filename = decodeURIComponent(
    /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1] ??
      /filename="([^"]+)"/i.exec(disposition)?.[1] ??
      "",
  );
  if (!filename.endsWith(".xlsx") || /[\\/\r\n]/.test(filename))
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  const blob = await response.blob();
  if (!blob.size) throw new Error("다운로드 파일이 비어 있습니다.");
  saveMasterDataDownload({ blob, filename });
}
export function withdrawalWarnings(error: unknown): WithdrawalWarning[] {
  if (!(error instanceof ApiError) || error.status !== 409) return [];
  const details = error.body.details as
    | { workforceRecommendationWarnings?: WithdrawalWarning[] }
    | undefined;
  return details?.workforceRecommendationWarnings ?? [];
}
export function withdrawalCommitted() {
  for (const name of [
    "sicost:assignments-committed",
    "sicost:sales-committed",
    "sicost:staffing-committed",
  ])
    window.dispatchEvent(new Event(name));
}
