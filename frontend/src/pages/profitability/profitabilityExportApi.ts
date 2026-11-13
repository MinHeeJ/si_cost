import { ApiError, type ApiErrorBody } from "../../shared/api";
import { saveMasterDataDownload } from "../exportsops/masterDataExportApi";
import type { AmountUnit } from "../../shared/format";
import type { ColumnView } from "../me/preferencesApi";
import {
  profitabilityParameters,
  type ProfitabilityQuery,
} from "./individualProfitabilityApi";

export type ProfitabilityExportOptions = Partial<ColumnView> & {
  unit?: AmountUnit;
};
const xlsxType =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
export async function getProfitabilityExport(
  query: ProfitabilityQuery,
  options: ProfitabilityExportOptions = {},
  signal?: AbortSignal,
) {
  const params = profitabilityParameters(query, true);
  if (options.order) params.set("order", options.order.join(","));
  if (options.hidden) params.set("hidden", options.hidden.join(","));
  if (options.unit) params.set("unit", options.unit);
  const response = await fetch(
    `/api/individual-profitability/export?${params}`,
    {
      method: "GET",
      redirect: "error",
      cache: "no-store",
      signal,
      headers: { Accept: `${xlsxType}, application/json` },
    },
  );
  const type = response.headers.get("Content-Type")?.split(";")[0].trim();
  if (type === "application/json") {
    const error = (await response.json()) as ApiErrorBody;
    if (
      error.success === false &&
      typeof error.code === "string" &&
      typeof error.message === "string"
    )
      throw new ApiError(response.status, error);
    throw new Error("다운로드 응답을 확인할 수 없습니다.");
  }
  if (!response.ok || type !== xlsxType)
    throw new Error("파일을 내려받지 못했습니다. 다시 시도해 주세요.");
  let filename: string;
  try {
    filename = decodeURIComponent(
      /filename\*=UTF-8''([^;]+)/i.exec(
        response.headers.get("Content-Disposition") ?? "",
      )?.[1] ?? "",
    );
  } catch {
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  }
  if (!/^개인원가수익_[0-9]{4}_[0-9]{8}_[0-9]{6}\.xlsx$/.test(filename))
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  const blob = await response.blob();
  if (!blob.size) throw new Error("다운로드 파일이 비어 있습니다.");
  return { blob, filename };
}
export const saveProfitabilityDownload = saveMasterDataDownload;
