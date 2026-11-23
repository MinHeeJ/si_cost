import { request } from "../../shared/api";
import { downloadB7File, saveDownload } from "../b7/b7Api";
export type CashUnit = "WON" | "THOUSAND_WON" | "MILLION_WON";
export const cashItems = [
  "CASH_SALES",
  "MISC",
  "LOAN",
  "REG_PAY",
  "CON_FL_PAY",
  "OS_PAY",
  "UNPAID_LABOR",
  "EXPENSE",
  "CORP_TAX",
] as const;
export type CashItem = (typeof cashItems)[number];
export const cashLabels: Record<CashItem, string> = {
  CASH_SALES: "현금매출",
  MISC: "잡수익",
  LOAN: "대여금",
  REG_PAY: "인건비(정규직)",
  CON_FL_PAY: "인건비(계약직·프리랜서)",
  OS_PAY: "용역비(외주)",
  UNPAID_LABOR: "미지급 인건비",
  EXPENSE: "제경비",
  CORP_TAX: "법인세 납부",
};
export type CashIssue = {
  code: string;
  message: string;
  month: string;
  sourceType?: string;
  sourceId?: string;
  field?: string;
};
export type CashSource = {
  sourceType: string;
  sourceTypeLabel: string;
  sourceId: string;
  item: CashItem;
  attributionDate: string | null;
  sourceMonth: string;
  attributionMonth: string;
  status: "ACTUAL" | "PLANNED" | "UNKNOWN";
  amount: string | null;
  vatAmount: string | null;
  includesVat: boolean;
  referencePayrollMonth: string | null;
  employeeType: string | null;
  verified: boolean;
  formula: string;
  sourceLink: string | null;
  issues: CashIssue[];
};
export type CashSourcesPage = {
  employeeTypeLabels: Record<string, string>;
  month: string;
  item: CashItem;
  items: CashSource[];
  total: number;
  page: number;
  size: number;
  hasMore: boolean;
};
export type CashValues = {
  openingBalance: string | null;
  items: {
    item: CashItem;
    amount: string | null;
    includesVat: boolean;
    verified: boolean;
  }[];
  incomeTotal: string | null;
  expenseTotal: string | null;
  closingBalance: string | null;
};
export type CashMonth = CashValues & {
  month: string;
  status: "ACTUAL" | "PLANNED" | "BEFORE_BASE";
  verified: boolean;
  shortage: boolean;
  issues: CashIssue[];
};
export type CashPoint = {
  month: string;
  balance: string | null;
  status: CashMonth["status"];
  shortage: boolean;
};
export type CashFlowResult = {
  year: number;
  unit: CashUnit;
  asOfDate: string;
  timezone: string;
  baseMonth: string;
  baseAmount: string;
  minimumCash: string;
  minimumCashMonth: string;
  months: CashMonth[];
  periodTotals: (CashValues & {
    period: "FIRST_HALF" | "SECOND_HALF" | "YEAR";
  })[];
  chartPoints: CashPoint[];
  shortages: CashPoint[];
  issues: CashIssue[];
};
export const getCashFlow = (
  year: number | undefined,
  unit: CashUnit,
  signal?: AbortSignal,
) =>
  request<CashFlowResult>(
    `/api/cash-flow?${new URLSearchParams({ ...(year === undefined ? {} : { year: String(year) }), unit })}`,
    { signal },
  );
export const getCashFlowSources = (
  month: string,
  item: CashItem,
  page = 1,
  size = 50,
  signal?: AbortSignal,
) =>
  request<CashSourcesPage>(
    `/api/cash-flow/sources?${new URLSearchParams({ month, item, page: String(page), size: String(size) })}`,
    { signal },
  );
export async function getCashFlowExport(year: number, unit: CashUnit) {
  saveDownload(
    await downloadB7File(
      `/api/cash-flow/export?${new URLSearchParams({ year: String(year), unit })}`,
    ),
  );
}
