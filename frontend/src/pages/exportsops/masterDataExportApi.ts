import { ApiError, type ApiErrorBody } from "../../shared/api";

export const clientExportColumns = [
  "type",
  "name",
  "businessNo",
  "ceo",
  "industry",
  "contactName",
  "phone",
  "taxEmail",
  "account",
  "projectCount",
  "personCount",
  "useYn",
] as const;
export const partExportColumns = [
  "name",
  "leaderName",
  "profitRate",
  "salesExpenseAmount",
  "effectiveMonth",
  "scheduledChange",
  "personCount",
  "projectCount",
  "useYn",
] as const;
export type ClientExportColumn = (typeof clientExportColumns)[number];
export type PartExportColumn = (typeof partExportColumns)[number];
export type ExportAmountUnit = "원" | "천원" | "백만원";
export type MasterExportFilters = {
  type?: "CUST" | "VNDR" | "BOTH";
  useYn?: "Y" | "N";
  q?: string;
};
export type MasterDataExportRequest = {
  view: "CLIENTS" | "PARTS";
  filters: MasterExportFilters;
  sort?: string;
  direction?: "ASC" | "DESC";
  columns: readonly (ClientExportColumn | PartExportColumn)[];
  amountUnit?: ExportAmountUnit;
};
export type MasterDataDownload = { blob: Blob; filename: string };
const xlsxType =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

export async function createMasterDataExport(
  input: MasterDataExportRequest,
  signal?: AbortSignal,
): Promise<MasterDataDownload> {
  // Pick only wire fields: page, selection and processor fields never reach export.
  const { type, useYn, q } = input.filters;
  const response = await fetch("/api/exports/master-data", {
    method: "POST",
    redirect: "error",
    signal,
    headers: {
      "Content-Type": "application/json",
      Accept: `${xlsxType}, application/json`,
    },
    body: JSON.stringify({
      view: input.view,
      filters: {
        ...(type ? { type } : {}),
        ...(useYn ? { useYn } : {}),
        ...(q?.trim() ? { q: q.trim() } : {}),
      },
      columns: input.columns,
      sort: input.sort,
      direction: input.direction,
      amountUnit: input.amountUnit,
    }),
  });
  const contentType = response.headers
    .get("Content-Type")
    ?.split(";")[0]
    .trim();
  if (contentType === "application/json") {
    const error = (await response.json()) as ApiErrorBody;
    if (
      error.success === false &&
      typeof error.code === "string" &&
      typeof error.message === "string"
    )
      throw new ApiError(response.status, error);
    throw new Error("다운로드 응답을 확인할 수 없습니다.");
  }
  if (!response.ok || contentType !== xlsxType)
    throw new Error("파일을 내려받지 못했습니다. 다시 시도해 주세요.");
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  let filename: string;
  try {
    filename = decodeURIComponent(encoded ?? "");
  } catch {
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  }
  if (!/^(거래처|영업파트)_[0-9]{8}_[0-9]{6}\.xlsx$/.test(filename))
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  const blob = await response.blob();
  if (!blob.size) throw new Error("다운로드 파일이 비어 있습니다.");
  return { blob, filename };
}

export function saveMasterDataDownload(download: MasterDataDownload) {
  const url = URL.createObjectURL(download.blob);
  const anchor = document.createElement("a");
  try {
    anchor.href = url;
    anchor.download = download.filename;
    anchor.hidden = true;
    document.body.appendChild(anchor);
    anchor.click();
  } finally {
    anchor.remove();
    URL.revokeObjectURL(url);
  }
}
