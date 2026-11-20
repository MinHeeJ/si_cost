import {
  authenticatedFetch,
  request,
  ApiError,
  type ApiErrorBody,
} from "../../shared/api";
import { saveMasterDataDownload } from "../exportsops/masterDataExportApi";

export type ForecastIssue = {
  field: string | null;
  needSn: number | null;
  reason: string;
  status: "UNKNOWN" | "INCOMPLETE";
};
export type RevenueMonth = {
  month: string;
  projectRevenue: string | null;
  opportunityRevenue: string;
  totalRevenue: string | null;
  allWonRevenue: string | null;
};
export type RevenueSource = {
  sourceType: "PROJECT" | "OPPORTUNITY";
  sourceId: string;
  name: string;
  contractType: string | null;
  month: string;
  amount: string | null;
  allWonAmount: string | null;
  basis: string;
};
export type WorkforceMonth = {
  month: string;
  supply: string | null;
  opportunityDemand: string | null;
  unfilledDemand: string | null;
  demand: string | null;
  difference: string | null;
  rawSupply: string | null;
  rawOpportunityDemand: string | null;
  rawUnfilledDemand: string | null;
  status: "SHORT" | "SURPLUS" | "BALANCED" | "UNKNOWN";
};
export type Hiring = {
  jobCode: string;
  firstShortageMonth: string;
  maximumShortage: string;
  recommendedStartMonth: string;
  startNow: boolean;
  candidateCount: number;
};
export type SalesForecast = {
  from: string;
  months: number;
  summary: {
    sixMonthRevenue: string | null;
    opportunityRevenue: string;
    allWonRevenue: string | null;
    averageDemand: string | null;
    averageSupply: string | null;
    hiringJobCount: number;
  };
  revenueMonths: RevenueMonth[];
  revenueSources: RevenueSource[];
  workforce: {
    from: string;
    months: number;
    includeUnfilled: boolean;
    basisDate: string;
    standardMonth: string;
    totals: WorkforceMonth[];
    jobDiagnostics: { jobCode: string; month: WorkforceMonth; basis: string }[];
    hiring: Hiring[];
    issues: ForecastIssue[];
    lastMonthSupply: string | null;
    lastMonthDemand: string | null;
    mostShortageJobCode: string | null;
    maximumShortage: string | null;
  };
};
export const getSalesOpportunityForecast = () =>
  request<SalesForecast>("/api/sales-opportunities/forecast");
export type ForecastAmountUnit = "WON" | "THOUSAND" | "MILLION";
export async function getSalesOpportunityForecastExport(
  amountUnit: ForecastAmountUnit,
) {
  const response = await authenticatedFetch(
    `/api/sales-opportunities/forecast/export?${new URLSearchParams({ amountUnit })}`,
    {
      headers: {
        Accept:
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      },
      redirect: "error",
    },
  );
  const type = response.headers.get("Content-Type")?.split(";")[0].trim();
  if (type === "application/json") {
    const error = (await response.json()) as ApiErrorBody;
    if (error.success === false && typeof error.message === "string")
      throw new ApiError(response.status, error);
  }
  if (
    !response.ok ||
    type !== "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  )
    throw new Error("파일을 내려받지 못했습니다. 다시 시도해 주세요.");
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(
    response.headers.get("Content-Disposition") ?? "",
  )?.[1];
  let filename = "";
  try {
    filename = decodeURIComponent(encoded ?? "");
  } catch {
    /* Invalid server filename is rejected below. */
  }
  if (!/^(?![=+@.-])[^/\\\r\n]+_\d{4}_\d{8}_\d{6}\.xlsx$/.test(filename))
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  const blob = await response.blob();
  if (!blob.size) throw new Error("다운로드 파일이 비어 있습니다.");
  return { blob, filename };
}
export const saveSalesForecastDownload = saveMasterDataDownload;
