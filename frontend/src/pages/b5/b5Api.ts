import {
  ApiError,
  request,
  type ApiEnvelope,
  type ApiErrorBody,
} from "../../shared/api";
import { downloadB7File, saveDownload, type B7Summary } from "../b7/b7Api";
import type {
  SubPreview,
  Withdrawal,
} from "../assignmentsops/assignmentActionsApi";

export type WithdrawalSelection = {
  rowNumber: number;
  withdrawals: Withdrawal[];
};
export type B5Result = {
  uploadId: number;
  formType: "B5";
  screenId: "UI-12";
  status: "CHECKED" | "APPLIED";
  summary: B7Summary & { tableWarningCount: number };
  results: { rowNumber: number; assignmentId: string }[];
  changed: boolean;
  withdrawnIds: string[];
};
export type B5Validation = B5Result & {
  issueCount: number;
  previewToken: string;
  substitutionPreviews: { rowNumber: number; preview: SubPreview }[];
};
export const getB5Template = () => downloadB7File("/api/excel/B5/template");
export const getB5Result = (id: number) =>
  request<B5Result>(`/api/uploads/${id}/result`);
export { saveDownload };
async function send<T>(
  path: string,
  file: File,
  year: number,
  selections: WithdrawalSelection[],
  uploadId?: number,
  previewToken?: string,
) {
  const body = new FormData();
  body.append("file", file);
  body.append("year", String(year));
  body.append(
    "withdrawalSelections",
    new Blob([JSON.stringify(selections)], { type: "application/json" }),
  );
  if (uploadId !== undefined) body.append("uploadId", String(uploadId));
  if (previewToken !== undefined) body.append("previewToken", previewToken);
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
  const result = payload as ApiEnvelope<T>;
  if (!result.success || !result.meta || !result.data)
    throw new Error("올바르지 않은 서버 응답입니다.");
  return result.data;
}
export const createB5Validation = (
  file: File,
  year: number,
  selections: WithdrawalSelection[],
  uploadId?: number,
) =>
  send<B5Validation>(
    "/api/excel/B5/validate",
    file,
    year,
    selections,
    uploadId,
  );
export const createB5Apply = (
  file: File,
  year: number,
  selections: WithdrawalSelection[],
  uploadId: number,
  previewToken: string,
) =>
  send<B5Result>(
    "/api/excel/B5/apply",
    file,
    year,
    selections,
    uploadId,
    previewToken,
  );
