import { request } from "../../shared/api";
import type {
  SettlementIssue,
  SettlementResult,
} from "../salespartsettlements/salesPartSettlementsApi";
export type SettlementMutation = Pick<
  SettlementResult,
  | "partId"
  | "month"
  | "status"
  | "cardAmount"
  | "confirmedBy"
  | "confirmedAt"
  | "lastCancellationReason"
  | "revision"
  | "issues"
> & { changed: boolean; partProfitAmount: string | null };
export type SettlementCardCommand = {
  month: string;
  cardAmount: string;
  expectedRevision: string;
};
export type SettlementCancelCommand = {
  month: string;
  reason: string;
  expectedRevision: string;
};
export type TrendCell = {
  month: string;
  value: string | null;
  employeeType: string | null;
  source:
    | "CNFM"
    | "AUTO"
    | "CALCULATED"
    | "BEFORE_BASE"
    | "NO_INPUT"
    | "UNKNOWN";
  issues: SettlementIssue[];
};
export type TrendMetric = {
  metric: "MM" | "UNIT_PRICE" | "COST" | "PROFIT" | "PROFIT_SHARE";
  months: TrendCell[];
  total: string | null;
  remark: string | null;
};
export type TrendResult = {
  partId: string;
  month: string;
  months: string[];
  items: {
    developerId: string;
    name: string;
    managementNo: string;
    metrics: TrendMetric[];
  }[];
  pagination: { page: number; size: 20 | 50 | 100; total: number };
  monthlyTotals: {
    month: string;
    personProfitShareAmount: string | null;
    cardAmount: string | null;
    partProfitAmount: string | null;
    issues: SettlementIssue[];
  }[];
  periodPartProfitAmount: string | null;
  issues: SettlementIssue[];
};
const path = (id: string, action: string) =>
  `/api/sales-part-settlements/${encodeURIComponent(id)}/${action}`;
export const saveSalesPartSettlementCard = (
  id: string,
  value: SettlementCardCommand,
) =>
  request<SettlementMutation>(path(id, "card"), {
    method: "PUT",
    body: JSON.stringify(value),
  });
export const createSalesPartSettlementConfirmation = (
  id: string,
  value: SettlementCardCommand,
) =>
  request<SettlementMutation>(path(id, "confirm"), {
    method: "POST",
    body: JSON.stringify(value),
  });
export const createSalesPartSettlementCancellation = (
  id: string,
  value: SettlementCancelCommand,
) =>
  request<SettlementMutation>(path(id, "cancel"), {
    method: "POST",
    body: JSON.stringify(value),
  });
export const getSalesPartSettlementTrend = (
  id: string,
  query: { month: string; page: number; size: 20 | 50 | 100 },
  signal?: AbortSignal,
) =>
  request<TrendResult>(
    `${path(id, "trend")}?${new URLSearchParams({ month: query.month, page: String(query.page), size: String(query.size) })}`,
    { signal },
  );
export function parseSettlementCard(input: string): string {
  const text = input.trim();
  if (!/^(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)?$/.test(text))
    throw new Error("법인카드 사용액은 올바른 원정수로 입력해 주세요.");
  const value = BigInt(text.replaceAll(",", "") || "0");
  if (value > 999999999999999n)
    throw new Error("법인카드 사용액의 최대 금액을 초과했습니다.");
  return value.toString();
}
