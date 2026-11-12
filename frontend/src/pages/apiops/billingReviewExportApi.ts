import { ApiError, type ApiErrorBody } from "../../shared/api";
import type { ReviewAmountUnit } from "./billingReviewApi";
export const billingCheckColumns = [
  "verdict",
  "projectName",
  "contractType",
  "personCount",
  "mm",
  "estimatedAmount",
  "billedAmount",
  "differenceAmount",
  "explanation",
] as const;
export const receivableColumns = [
  "projectName",
  "billId",
  "unpaidAmount",
  "dueDate",
  "elapsedDays",
  "step",
  "sentHistory",
  "nextNotification",
] as const;
export async function downloadBillingReview(input: {
  view: "BILLING_CHECKS" | "RECEIVABLES";
  month?: string;
  amountUnit: ReviewAmountUnit;
}) {
  const type =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  const response = await fetch("/api/exports/master-data", {
    method: "POST",
    redirect: "error",
    headers: {
      "Content-Type": "application/json",
      Accept: `${type}, application/json`,
    },
    body: JSON.stringify({
      view: input.view,
      filters: input.view === "BILLING_CHECKS" ? { month: input.month } : {},
      columns:
        input.view === "BILLING_CHECKS"
          ? billingCheckColumns
          : receivableColumns,
      amountUnit: input.amountUnit,
    }),
  });
  if (
    response.headers.get("Content-Type")?.split(";")[0].trim() ===
    "application/json"
  ) {
    const error = (await response.json()) as ApiErrorBody;
    if (error.success === false && typeof error.message === "string")
      throw new ApiError(response.status, error);
    throw new Error("다운로드 응답을 확인할 수 없습니다.");
  }
  if (
    !response.ok ||
    response.headers.get("Content-Type")?.split(";")[0].trim() !== type
  )
    throw new Error("파일을 내려받지 못했습니다.");
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(
    response.headers.get("Content-Disposition") ?? "",
  )?.[1];
  let filename = "";
  try {
    filename = decodeURIComponent(encoded ?? "");
  } catch {
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  }
  if (!/^(청구누락점검|미수알림)_[0-9]{8}_[0-9]{6}\.xlsx$/.test(filename))
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  const blob = await response.blob();
  if (!blob.size) throw new Error("다운로드 파일이 비어 있습니다.");
  return { blob, filename };
}
