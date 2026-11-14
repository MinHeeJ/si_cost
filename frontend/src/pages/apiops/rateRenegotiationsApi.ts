import { ApiError, request, type ApiErrorBody } from "../../shared/api";
import { compareDecimal, type AmountUnit } from "../../shared/format";
import { saveMasterDataDownload } from "../exportsops/masterDataExportApi";

export type RenegotiationQuery = {
  renewalWindow: string;
  marginBelow: string;
  employeeType?: string;
  projectId?: string;
  increaseRate: string;
  page: number;
  size: 20 | 50 | 100;
  sort: string;
  direction: "ASC" | "DESC";
};
export const defaultRenegotiationQuery = (): RenegotiationQuery => ({
  renewalWindow: "183",
  marginBelow: "15",
  increaseRate: "5",
  page: 1,
  size: 50,
  sort: "marginRate",
  direction: "ASC",
});
export type RenegotiationIssue = {
  code: string;
  message: string;
  field: string | null;
  developerId: string | null;
  assignmentId: string | null;
  projectId: string | null;
  month: string | null;
  source: string;
  blocking: boolean;
};
export type RenegotiationRow = {
  assignmentId: string;
  developerId: string;
  name: string;
  managementNo: string;
  employeeType: string;
  projectId: string;
  projectName: string;
  renewalDate: string;
  daysUntilRenewal: number;
  planned: boolean;
  contractMm: string;
  monthlyMm: string;
  unitPrice: string;
  monthlyRevenue: string;
  monthlyLaborCost: string;
  marginRate: string;
  targetUnitPrice: string | null;
  requiredIncreaseRate: string | null;
  annualGain: string;
  targetAnnualGain: string | null;
  issues: RenegotiationIssue[];
};
export type RenegotiationsResult = {
  baseMonth: string;
  calculatedAt: string;
  appliedIncreaseRate: string;
  targetMarginRate: string | null;
  summary: {
    personCount: number;
    assignmentCount: number;
    weightedMarginRate: string | null;
    annualGain: string;
    targetAnnualGain: string | null;
  };
  items: RenegotiationRow[];
  pagination: { page: number; size: 20 | 50 | 100; total: number };
  unevaluatedCount: number;
  unevaluatedAssignments: RenegotiationIssue[];
  issues: RenegotiationIssue[];
};
export function normalizeIncrease(value: string): string {
  const text = value.trim();
  if (!text) return "0";
  if (text.length > 200 || !/^-?\d+(\.\d+)?$/.test(text))
    throw new Error("인상률은 숫자로 입력해 주세요.");
  if (compareDecimal(text, "0") < 0) return "0";
  if (compareDecimal(text, "50") > 0) return "50";
  if ((text.split(".")[1]?.length ?? 0) > 2)
    throw new Error("인상률은 소수 2자리까지 입력해 주세요.");
  return text;
}
export function getRateRenegotiations(
  query: RenegotiationQuery,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  // An explicitly empty simulation is zero; unlike omitted increaseRate (initial five).
  params.set("increaseRate", query.increaseRate);
  return request<RenegotiationsResult>(`/api/rate-renegotiations?${params}`, {
    signal,
  });
}
export async function downloadRenegotiations(
  query: RenegotiationQuery,
  columns: string[],
  amountUnit: AmountUnit,
) {
  const {
    renewalWindow,
    marginBelow,
    employeeType,
    projectId,
    increaseRate,
    sort,
    direction,
  } = query;
  const response = await fetch("/api/exports/master-data", {
    method: "POST",
    redirect: "error",
    headers: {
      "Content-Type": "application/json",
      Accept:
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/json",
    },
    body: JSON.stringify({
      view: "RATE_RENEGOTIATIONS",
      filters: {
        renewalWindow,
        marginBelow,
        employeeType,
        projectId,
        increaseRate,
      },
      sort,
      direction,
      columns,
      amountUnit,
    }),
  });
  const type = response.headers.get("Content-Type")?.split(";")[0].trim();
  if (type === "application/json") {
    const error = (await response.json()) as ApiErrorBody;
    if (error.success === false && typeof error.message === "string")
      throw new ApiError(response.status, error);
    throw new Error("다운로드 응답을 확인할 수 없습니다.");
  }
  if (
    !response.ok ||
    type !== "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  )
    throw new Error("엑셀 다운로드에 실패했습니다.");
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
  if (!/^단가재협상_\d{8}\.xlsx$/.test(filename))
    throw new Error("다운로드 파일명을 확인할 수 없습니다.");
  const blob = await response.blob();
  if (!blob.size) throw new Error("다운로드 파일이 비어 있습니다.");
  saveMasterDataDownload({ blob, filename });
}
