import { request } from "../../shared/api";
import type { MasterHistoryEntry, MasterHistoryPage } from "./masterHistoryApi";

export type PeopleHistoryTarget = "DEVELOPER" | "CANDIDATE";
export type PeopleHistoryEntry = MasterHistoryEntry;
export type PeopleHistoryPage = MasterHistoryPage;

export function getPeopleHistory(
  targetType: PeopleHistoryTarget,
  targetId: string,
  page = 1,
  size: 20 | 50 | 100 = 50,
  signal?: AbortSignal,
): Promise<PeopleHistoryPage> {
  const query = new URLSearchParams({
    targetType,
    targetId,
    page: String(page),
    size: String(size),
  });
  return request(`/api/people-history?${query}`, { signal });
}
