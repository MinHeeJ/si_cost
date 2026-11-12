import {
  ApiError,
  request,
  type ApiEnvelope,
  type ApiErrorBody,
} from "../../shared/api";
import { downloadB7File, saveDownload } from "../b7/b7Api";
import type { ProjectUploadResult, ProjectUploadValidation } from "../b9/b9Api";

export type ProjectStep = "EST" | "CTRT" | "PLAN";
export type B6Comparison = {
  selectedStep: ProjectStep | null;
  contractAmount: string;
  totalAmount: string;
  differenceAmount: string;
  mismatchCount: number;
  items: {
    developerId: string;
    managementNo: string;
    name: string;
    month: string;
    type: "NO_ASSIGNMENT" | "NO_DISTRIBUTION";
    amount: string;
    actualMm: string;
  }[];
  warnings: string[];
};
export type B6Validation = Omit<
  ProjectUploadValidation,
  "prospectiveComparison"
> & { prospectiveComparison: B6Comparison | null };
export type B6Context = { year: number; projectId: string; step: ProjectStep };
export type DistributionCell = { month: string; amount: string };
export type DistributionRow = {
  rowSn: number;
  rowType: "DEV" | "ITEM";
  developerId: string | null;
  managementNo: string | null;
  name: string | null;
  itemName: string | null;
  jobNames: string[];
  useYn: "Y" | "N" | null;
  cells: DistributionCell[];
  totalAmount: string;
};
export type Distribution = {
  project: {
    id: string;
    name: string;
    contractType: string;
    status: string;
    startDate: string;
    endDate: string;
    contractAmount: string | null;
  };
  viewedStep: ProjectStep;
  selectedStep: ProjectStep | null;
  months: string[];
  rows: DistributionRow[];
  monthlyTotals: DistributionCell[];
  totalAmount: string;
  revision: string;
};
export type DistributionUpload = {
  uploadId: number;
  step: ProjectStep;
  fileName: string;
  actor: string;
  registeredAt: string;
  status: "CHECKED" | "APPLIED";
  appliedAt: string | null;
};
async function send<T>(
  path: string,
  file: File,
  context: B6Context,
  uploadId?: number,
  expectedRevision?: string,
): Promise<T> {
  const body = new FormData();
  body.append("file", file);
  body.append("year", String(context.year));
  body.append("projectId", context.projectId);
  body.append("step", context.step);
  if (uploadId !== undefined) body.append("uploadId", String(uploadId));
  if (expectedRevision !== undefined)
    body.append("expectedRevision", expectedRevision);
  const response = await fetch(path, {
    method: "POST",
    body,
    redirect: "error",
    headers: { Accept: "application/json" },
  });
  const value: unknown = await response.json();
  if (!response.ok) {
    const error = value as ApiErrorBody;
    if (
      error.success === false &&
      typeof error.message === "string" &&
      typeof error.code === "string"
    ) {
      const details: unknown = error.details;
      const differences =
        typeof details === "object" &&
        details !== null &&
        "differences" in details &&
        Array.isArray(details.differences)
          ? details.differences
          : [];
      const protectedMonths = differences
        .filter(
          (item): item is { month: string; difference: string } =>
            typeof item === "object" &&
            item !== null &&
            typeof item.month === "string" &&
            typeof item.difference === "string",
        )
        .map((item) => `${item.month} 차이 ${item.difference}원`)
        .join(" · ");
      throw new ApiError(response.status, {
        ...error,
        message: `${error.message}${protectedMonths ? ` (${protectedMonths})` : ""}${error.field ? ` [${error.field}]` : ""}`,
      });
    }
    throw new Error("요청에 실패했습니다. 파일과 검사 번호를 유지합니다.");
  }
  const envelope = value as ApiEnvelope<T>;
  if (envelope.success !== true || !envelope.meta || !envelope.data)
    throw new Error("올바르지 않은 서버 응답입니다.");
  return envelope.data;
}
export const createB6Validation = (file: File, context: B6Context) =>
  send<B6Validation>("/api/excel/B6/validate", file, context);
export const saveB6Apply = (
  file: File,
  uploadId: number,
  context: B6Context,
  expectedRevision: string,
) =>
  send<ProjectUploadResult>(
    "/api/excel/B6/apply",
    file,
    context,
    uploadId,
    expectedRevision,
  );
export const getB6Template = (projectId: string) =>
  downloadB7File(
    `/api/excel/B6/template?${new URLSearchParams({ projectId })}`,
  );
export const getUploadResult = (id: number) =>
  request<ProjectUploadResult>(`/api/uploads/${id}/result`);
export const getDistribution = (id: string, step: ProjectStep) =>
  request<Distribution>(
    `/api/projects/${encodeURIComponent(id)}/distribution?${new URLSearchParams({ step })}`,
  );
export const getDistributionComparison = (id: string) =>
  request<B6Comparison>(
    `/api/projects/${encodeURIComponent(id)}/distribution/comparison`,
  );
export const getDistributionUploads = (id: string) =>
  request<{ items: DistributionUpload[] }>(
    `/api/projects/${encodeURIComponent(id)}/distribution/uploads`,
  );
export const exportDistribution = (
  projectId: string,
  step: ProjectStep,
  amountUnit: string,
) =>
  downloadB7File("/api/exports/master-data", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      view: "PROJECT_DISTRIBUTION",
      projectId,
      step,
      amountUnit,
    }),
  });
export { saveDownload };
