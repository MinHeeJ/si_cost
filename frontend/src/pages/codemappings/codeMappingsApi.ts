import { request } from "../../shared/api";

export type CodeMapping = {
  mapId: number;
  sourceText: string;
  groupId: string;
  code: string;
  codeName: string;
  revision: string;
};
export type MappingQuery = {
  sourceText?: string;
  groupId?: string;
  page: number;
  size: 20 | 50 | 100;
};
export type MappingPage = {
  items: CodeMapping[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type MappingGroup = { groupId: string; name: string };
export type MappingCode = { code: string; name: string };
export type CreateMapping = {
  sourceText: string;
  groupId: string;
  code: string;
};
export type DeleteMappings = {
  items: { mapId: number; expectedRevision: string }[];
};
export function getCodeMappings(query: MappingQuery): Promise<MappingPage> {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  if (query.sourceText) params.set("sourceText", query.sourceText);
  if (query.groupId) params.set("groupId", query.groupId);
  return request<MappingPage>(`/api/code-mappings?${params}`);
}
export function createCodeMapping(body: CreateMapping): Promise<void> {
  return request<void>("/api/code-mappings", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
export function deleteCodeMappings(body: DeleteMappings): Promise<void> {
  return request<void>("/api/code-mappings/delete", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
export function getMappingGroups(): Promise<{ items: MappingGroup[] }> {
  return request("/api/code-groups");
}
export function getMappingCodes(
  groupId: string,
): Promise<{ items: MappingCode[] }> {
  return request(
    `/api/codes/${encodeURIComponent(groupId)}?includeInactive=false`,
  );
}
export const CODE_MAPPINGS_COMMITTED = "sicost:code-mappings-committed";
