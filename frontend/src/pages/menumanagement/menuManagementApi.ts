import { request } from "../../shared/api";

export type ManagedMenu = {
  menuId: string;
  parentId: string | null;
  name: string;
  level: 1 | 2;
  screenId: string | null;
  sortOrder: number;
  use: boolean;
  pcOnly: boolean;
  locked: boolean;
  icon: string | null;
};
export type ManagedMenuTree = {
  items: ManagedMenu[];
  revision: string;
  developmentMode: true;
  accessEnforcement: false;
};
export type ManagedMenuInput = Pick<
  ManagedMenu,
  "menuId" | "name" | "level" | "parentId" | "screenId" | "sortOrder"
> & { use?: boolean; pcOnly?: boolean };
export type ManagedMenuSaveResult = {
  value: ManagedMenuTree;
  revision: string;
  changed: boolean;
};
export type AvailableMenuScreens = {
  items: { screenId: string; name: string }[];
  revision: string;
};
export type MenuRegistration =
  | { level: 1; name: string; expectedRevision: string }
  | {
      level: 2;
      name: string;
      parentId: string;
      screenId: string;
      pcOnly: boolean;
      expectedRevision: string;
    };
export type MenuHistoryPage = {
  items: {
    historyId: number;
    createdAt: string;
    itemName: string;
    beforeValue: string | null;
    afterValue: string | null;
    actorId: string;
    actorName: string;
    reason: string | null;
  }[];
  total: number;
  page: number;
  size: number;
};
export const getMenuManagement = () =>
  request<ManagedMenuTree>("/api/menu-management");
export const putMenuManagement = (
  items: ManagedMenuInput[],
  expectedRevision: string,
) =>
  request<ManagedMenuSaveResult>("/api/menu-management", {
    method: "PUT",
    body: JSON.stringify({ items, expectedRevision }),
  });
export const getMenuManagementScreens = () =>
  request<AvailableMenuScreens>("/api/menu-management/screens");
export const createManagedMenu = (body: MenuRegistration) =>
  request<ManagedMenuSaveResult>("/api/menu-management/menus", {
    method: "POST",
    body: JSON.stringify(body),
  });
export const getMenuHistory = (menuId: string, page: number, size: number) =>
  request<MenuHistoryPage>(
    `/api/master-history?${new URLSearchParams({ targetType: "MENU", targetId: menuId, page: String(page), size: String(size) })}`,
  );
export function menuPayload(items: ManagedMenu[]): ManagedMenuInput[] {
  return items.map((row) => ({
    menuId: row.menuId,
    name: row.name.trim(),
    level: row.level,
    parentId: row.parentId,
    screenId: row.screenId,
    sortOrder: row.sortOrder,
    ...(row.level === 2 ? { use: row.use, pcOnly: row.pcOnly } : {}),
  }));
}
export function orderedMenus(items: ManagedMenu[]): ManagedMenu[] {
  const order = (a: ManagedMenu, b: ManagedMenu) =>
    a.sortOrder - b.sortOrder || a.menuId.localeCompare(b.menuId);
  return items
    .filter((row) => row.level === 1)
    .sort(order)
    .flatMap((root) => [
      root,
      ...items.filter((row) => row.parentId === root.menuId).sort(order),
    ]);
}
export const protectedMenu = (row: ManagedMenu) =>
  row.locked ||
  ["UI-23", "UI-24", "UI-25", "UI-27"].includes(row.screenId ?? "");
export function moveMenu(
  items: ManagedMenu[],
  id: string,
  parentId: string | null,
  target: number,
): ManagedMenu[] {
  const row = items.find((item) => item.menuId === id);
  if (
    !row ||
    (row.level === 1 && parentId !== null) ||
    (row.level === 2 &&
      !items.some((item) => item.level === 1 && item.menuId === parentId)) ||
    (protectedMenu(row) && parentId !== row.parentId)
  )
    return items;
  const siblings = items
    .filter((item) => item.menuId !== id && item.parentId === parentId)
    .sort((a, b) => a.sortOrder - b.sortOrder);
  siblings.splice(Math.max(0, Math.min(target, siblings.length)), 0, {
    ...row,
    parentId,
  });
  const moved = new Map(
    siblings.map((item, index) => [
      item.menuId,
      { ...item, sortOrder: index + 1 },
    ]),
  );
  let result = items.map((item) => moved.get(item.menuId) ?? item);
  if (parentId !== row.parentId) {
    const remaining = result
      .filter((item) => item.parentId === row.parentId)
      .sort((a, b) => a.sortOrder - b.sortOrder);
    const ranks = new Map(
      remaining.map((item, index) => [item.menuId, index + 1]),
    );
    result = result.map((item) =>
      ranks.has(item.menuId)
        ? { ...item, sortOrder: ranks.get(item.menuId)! }
        : item,
    );
  }
  return orderedMenus(result);
}
