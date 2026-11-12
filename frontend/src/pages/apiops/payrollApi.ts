import {
  ApiError,
  request,
  type ApiEnvelope,
  type ApiErrorBody,
} from "../../shared/api";
import { downloadB7File, saveDownload, type B7Summary } from "../b7/b7Api";

export type PayrollRow = {
  month: string;
  developerId: string;
  managementNo: string;
  developerName: string;
  employeeType: string;
  directClass: string;
  payAmount: string;
  allowanceAmount: string;
  companyInsuranceAmount: string;
  selfInsuranceAmount: string;
  retirementReserveAmount: string;
  incomeTaxAmount: string;
  withholdingTaxAmount: string;
  realPayAmount: string | null;
  vendorSupplyAmount: string;
  uploadId: number;
};
export type PayrollQuery = {
  month: string;
  q?: string;
  page: number;
  size: 20 | 50 | 100;
};
export type PayrollPage = PayrollQuery & {
  items: PayrollRow[];
  total: number;
  revision: string;
};
export type PayrollSummary = B7Summary & { tableWarningCount: number };
export type PayrollUploadRecord = {
  uploadId: number;
  month: string;
  fileName: string;
  actor: string;
  registeredAt: string;
  status: "CHECKED" | "APPLIED";
  summary: PayrollSummary;
  appliedAt: string | null;
};
export type PayrollUploadsPage = {
  items: PayrollUploadRecord[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type PayrollResult = {
  uploadId: number;
  formType: "B1";
  screenId: "UI-02";
  status: "CHECKED" | "APPLIED";
  summary: PayrollSummary;
  results: { rowNumber: number; developerId: string }[];
  changed: boolean;
  month: string;
  revision: string;
};
export type PayrollValidation = PayrollResult & {
  issueCount: number;
  replacement: {
    newCount: number;
    changedCount: number;
    removedCount: number;
    removedDevelopers: {
      developerId: string;
      managementNo: string;
      developerName: string;
    }[];
  };
};
function params(query: PayrollQuery) {
  const result = new URLSearchParams({
    month: query.month,
    page: String(query.page),
    size: String(query.size),
  });
  if (query.q?.trim()) result.set("q", query.q.trim());
  return result;
}
export const getPayroll = (query: PayrollQuery) =>
  request<PayrollPage>(`/api/payroll?${params(query)}`);
export const getPayrollUploads = (
  month: string,
  page: number,
  size: 20 | 50 | 100,
) =>
  request<PayrollUploadsPage>(
    `/api/payroll/uploads?${new URLSearchParams({ month, page: String(page), size: String(size) })}`,
  );
export const getPayrollResult = (uploadId: number) =>
  request<PayrollResult>(`/api/uploads/${uploadId}/result`);
export const getPayrollTemplate = () =>
  downloadB7File("/api/excel/B1/template");
export { saveDownload };
async function multipart<T>(path: string, body: FormData): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    body,
    redirect: "error",
  });
  const payload: unknown = await response.json();
  if (!response.ok) {
    const error = payload as ApiErrorBody;
    if (
      error.success === false &&
      typeof error.message === "string" &&
      typeof error.code === "string"
    )
      throw new ApiError(response.status, error);
    throw new Error("서버 요청에 실패했습니다.");
  }
  const envelope = payload as ApiEnvelope<T>;
  if (envelope.success !== true || !envelope.meta || !envelope.data)
    throw new Error("올바르지 않은 서버 응답입니다.");
  return envelope.data;
}
export function createB1Validation(
  file: File,
  month: string,
  uploadId?: number,
) {
  const body = new FormData();
  body.append("file", file);
  body.append("month", month);
  if (uploadId !== undefined) body.append("uploadId", String(uploadId));
  return multipart<PayrollValidation>("/api/excel/B1/validate", body);
}
export function createB1Apply(
  file: File,
  input: {
    month: string;
    uploadId: number;
    expectedRevision: string;
    confirmFullReplacement: true;
  },
) {
  const body = new FormData();
  body.append("file", file);
  for (const [key, value] of Object.entries(input))
    body.append(key, String(value));
  return multipart<PayrollResult>("/api/excel/B1/apply", body);
}
export function exportPayroll(
  query: PayrollQuery,
  columns: string[],
  amountUnit: "원" | "천원" | "백만원",
) {
  return downloadB7File("/api/exports/master-data", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      view: "PAYROLL",
      filters: { month: query.month, ...(query.q ? { q: query.q } : {}) },
      columns,
      amountUnit,
    }),
  });
}
