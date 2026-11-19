import {
  authenticatedFetch,
  request,
  ApiError,
  type ApiErrorBody,
} from "../../shared/api";
import { saveMasterDataDownload } from "../exportsops/masterDataExportApi";
export type CalendarFilter = {
  year: number;
  jobCode?: string;
  gradeCode?: string;
  employeeType?: string;
  remainingOnly?: boolean;
  page?: number;
  size?: 20 | 50 | 100;
  sort?: string;
  direction?: "ASC" | "DESC";
};
export type CalendarCell = {
  month: string;
  status:
    | "BEFORE_JOIN"
    | "RETIRED"
    | "LEAVE"
    | "ASSIGNED"
    | "TENTATIVE"
    | "AVAILABLE"
    | "PAST_EMPTY";
  actualMm: string | null;
  assignmentCount: number;
  overAssigned: boolean;
  leavingSoon: boolean;
  projects: {
    projectId: string;
    projectName?: string | null;
    actualMm: string;
    endDate: string;
    extensionPossibility: string | null;
    preContract: boolean;
  }[];
  tentativeAssignments: {
    id: string;
    opportunityId: string;
    startMonth: string;
    status: string;
  }[];
  tentativeProbability: string | null;
};
export type CalendarPerson = {
  developerId: string;
  managementNo?: string | null;
  name: string;
  jobCode: string;
  gradeCode: string | null;
  employeeType: string;
  lastWithdrawalDate: string | null;
  cells: CalendarCell[];
};
export type CalendarPage = {
  items: CalendarPerson[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  year: number;
  summary: {
    availablePeople: number;
    remainingMm: string;
    leavingSoonPeople: number;
    highExtensionPeople: number;
    concurrentPeople: number;
    overAssignedPeople: number;
    thirdMonthRemainingMm: string;
  };
  monthTotals: {
    month: string;
    availablePeople: number | null;
    remainingMm: string | null;
  }[];
};
function params(filter: CalendarFilter) {
  const q = new URLSearchParams();
  Object.entries(filter).forEach(([k, v]) => {
    if (v !== undefined && v !== "") q.set(k, String(v));
  });
  return q;
}
export const getWorkforceOperations = (
  filter: CalendarFilter,
  signal?: AbortSignal,
) =>
  request<CalendarPage>(`/api/workforce-operations?${params(filter)}`, {
    signal,
  });
export async function downloadWorkforceCalendar(
  filter: CalendarFilter,
  tab: "CALENDAR" | "SUPPLY_DEMAND" = "CALENDAR",
  amountUnit: "WON" | "THOUSAND" | "MILLION" = "THOUSAND",
) {
  const q = params(tab === "CALENDAR" ? filter : { year: filter.year });
  q.delete("page");
  q.delete("size");
  q.set("tab", tab);
  q.set("amountUnit", amountUnit);
  const response = await authenticatedFetch(
    `/api/workforce-operations/export?${q}`,
    {
      headers: {
        Accept:
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      },
      redirect: "error",
    },
  );
  if (response.headers.get("Content-Type")?.includes("application/json")) {
    const body = (await response.json()) as ApiErrorBody;
    throw new ApiError(response.status, body);
  }
  if (
    !response.ok ||
    !response.headers
      .get("Content-Type")
      ?.includes(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      )
  )
    throw new Error("파일을 내려받지 못했습니다.");
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(
    response.headers.get("Content-Disposition") ?? "",
  )?.[1];
  const filename = decodeURIComponent(encoded ?? "");
  if (!/^[^/\\\r\n]+\.xlsx$/.test(filename))
    throw new Error("파일명을 확인할 수 없습니다.");
  const blob = await response.blob();
  if (!blob.size) throw new Error("파일이 비어 있습니다.");
  saveMasterDataDownload({ blob, filename });
}
