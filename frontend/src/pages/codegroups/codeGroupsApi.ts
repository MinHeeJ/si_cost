import { request } from "../../shared/api";
export type YesNo = "Y" | "N";
export type CodeGroup = {
  groupId: string;
  name: string;
  sysCodeYn: YesNo;
  valCodeYn: YesNo;
  useYn: YesNo;
  sortOrder: number;
  codeCount: number;
};
export type Code = {
  code: string;
  name: string;
  sortOrder: number;
  useYn: YesNo;
  addVal1: string | null;
  addVal2: string | null;
  addVal3: string | null;
  codeVal: string | null;
  unitNm: string | null;
};
export type CodeChange = {
  code: string;
  isNew: boolean;
  name?: string;
  useYn?: YesNo;
  addVal1?: string;
  addVal2?: string;
  codeVal?: string;
  unitNm?: string;
};
export type GroupCodesPage = {
  items: Code[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  groupRevision: string;
};
export type CodeSaveResult = {
  changed: boolean;
  message?: string;
  groupRevision: string;
  items: Code[];
};
const path = (group: string) =>
  `/api/code-groups/${encodeURIComponent(group)}/codes`;
export const getCodeGroups = () =>
  request<{ items: CodeGroup[] }>("/api/code-groups");
export const getGroupCodes = (
  group: string,
  page = 1,
  size: 20 | 50 | 100 = 50,
) => request<GroupCodesPage>(`${path(group)}?page=${page}&size=${size}`);
export const createCodeGroup = (body: { groupId: string; name: string }) =>
  request<void>("/api/code-groups", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const saveGroupCodes = (
  group: string,
  body: { expectedRevision: string; changes: CodeChange[] },
) =>
  request<CodeSaveResult>(path(group), {
    method: "PUT",
    body: JSON.stringify(body),
  });
export const deleteGroupCode = (
  group: string,
  code: string,
  revision: string,
) =>
  request<void>(
    `${path(group)}/${encodeURIComponent(code)}?expectedRevision=${encodeURIComponent(revision)}`,
    { method: "DELETE" },
  );
export const CODE_GROUPS_COMMITTED = "sicost:codegroups-committed";
export function notifyCodeGroupsCommitted(groupId: string) {
  window.dispatchEvent(
    new CustomEvent(CODE_GROUPS_COMMITTED, { detail: { groupId } }),
  );
}
