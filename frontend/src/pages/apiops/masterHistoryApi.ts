import { request } from "../../shared/api";

export type MasterHistoryTarget = "USER" | "CLIENT" | "PART";
export type MasterHistoryEntry = {
  historyId: number;
  targetTable: string;
  targetKey: string;
  changeType: "NEW" | "UPD" | "STOP" | "RESUME";
  itemName: string;
  beforeValue: string | null;
  afterValue: string | null;
  reason: string | null;
  actorId: string;
  actorName: string;
  createdAt: string;
};
export type MasterHistoryPage = {
  items: MasterHistoryEntry[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export function getMasterHistory(
  targetType: MasterHistoryTarget,
  targetId: string,
  page = 1,
  size: 20 | 50 | 100 = 50,
  signal?: AbortSignal,
): Promise<MasterHistoryPage> {
  const query = new URLSearchParams({
    targetType,
    targetId,
    page: String(page),
    size: String(size),
  });
  return request(`/api/master-history?${query}`, { signal });
}
