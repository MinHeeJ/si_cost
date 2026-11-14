import { ApiError, request, type ApiErrorBody } from "../../shared/api";
import { saveMasterDataDownload } from "../exportsops/masterDataExportApi";
import type { SalesForecast } from "../forecast/salesForecastApi";

export type WorkforceForecast = SalesForecast["workforce"];
export type WorkforceForecastQuery = {
  from: string;
  months: number;
  includeUnfilled: boolean;
};
export type WorkforceAmountUnit = "WON" | "THOUSAND" | "MILLION";
const parameters = (query: WorkforceForecastQuery) =>
  new URLSearchParams({
    from: query.from,
    months: String(query.months),
    includeUnfilled: String(query.includeUnfilled),
  });
export const getWorkforceForecasts = (query: WorkforceForecastQuery) =>
  request<WorkforceForecast>(`/api/workforce-forecasts?${parameters(query)}`);
export async function getWorkforceForecastExport(
  query: WorkforceForecastQuery,
  amountUnit: WorkforceAmountUnit,
) {
  const params = parameters(query);
  params.set("amountUnit", amountUnit);
  const response = await fetch(`/api/workforce-forecasts/export?${params}`, {
    headers: {
      Accept:
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    },
    redirect: "error",
  });
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
    /* Reject an invalid server filename. */
  }
  if (!/^인력수요예측_\d{4}_\d{8}_\d{6}\.xlsx$/.test(filename))
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  const blob = await response.blob();
  if (!blob.size) throw new Error("다운로드 파일이 비어 있습니다.");
  return { blob, filename };
}
export const saveWorkforceForecastDownload = saveMasterDataDownload;
