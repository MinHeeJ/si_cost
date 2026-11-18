import { stubBusinessFetch } from "../shared/testSupport/businessFixtures";
import { afterEach, expect, it, vi } from "vitest";
import * as api from "./apiClient";

const id = "선택/대상";
const encodedId = encodeURIComponent(id);
const roleBody = {
  dataScope: "PART" as const,
  sensitiveView: false,
  menuPermissions: [],
  expectedRevision: "role-r",
};
const items = [{ userId: id, expectedRevision: "user-r" }];
const cases: {
  name: string;
  path: string;
  method: string;
  body?: unknown;
  call(): Promise<unknown>;
}[] = [
  {
    name: "permission union",
    path: "/api/current-user/permission-settings",
    method: "GET",
    call: () => api.getCurrentUserPermissionSettings(),
  },
  {
    name: "roles",
    path: "/api/roles",
    method: "GET",
    call: () => api.getRoles(),
  },
  {
    name: "role detail",
    path: `/api/roles/${encodedId}`,
    method: "GET",
    call: () => api.getRole(id),
  },
  {
    name: "role create",
    path: "/api/roles",
    method: "POST",
    body: { mode: "NEW", name: "추가 역할", dataScope: "PART" },
    call: () =>
      api.createRole({ mode: "NEW", name: "추가 역할", dataScope: "PART" }),
  },
  {
    name: "role update",
    path: `/api/roles/${encodedId}`,
    method: "PUT",
    body: roleBody,
    call: () => api.putRole(id, roleBody),
  },
  {
    name: "role stop",
    path: `/api/roles/${encodedId}/stop`,
    method: "POST",
    body: { expectedRevision: "role-r" },
    call: () => api.saveRoleStop(id, "role-r"),
  },
  {
    name: "menu tree",
    path: "/api/menu-management",
    method: "GET",
    call: () => api.getMenuManagement(),
  },
  {
    name: "menu tree save",
    path: "/api/menu-management",
    method: "PUT",
    body: { items: [], expectedRevision: "menu-r" },
    call: () => api.putMenuManagement([], "menu-r"),
  },
  {
    name: "available screens",
    path: "/api/menu-management/screens",
    method: "GET",
    call: () => api.getMenuManagementScreens(),
  },
  {
    name: "menu registration",
    path: "/api/menu-management/menus",
    method: "POST",
    body: { level: 1, name: "새 그룹", expectedRevision: "menu-r" },
    call: () =>
      api.createManagedMenu({
        level: 1,
        name: "새 그룹",
        expectedRevision: "menu-r",
      }),
  },
  {
    name: "menu history",
    path: `/api/master-history?${new URLSearchParams({ targetType: "MENU", targetId: id, page: "2", size: "20" })}`,
    method: "GET",
    call: () => api.getMenuHistory(id, 2, 20),
  },
  {
    name: "user scopes",
    path: `/api/business-users/${encodedId}/scopes`,
    method: "GET",
    call: () => api.getBusinessUserScopes(id),
  },
  {
    name: "user activity",
    path: `/api/business-users/${encodedId}/access-logs?page=2&size=20`,
    method: "GET",
    call: () => api.getBusinessUserAccessLogs(id, 2, 20),
  },
  ...(["STOP", "RESUME", "UNLOCK"] as const).map((action) => ({
    name: `user ${action}`,
    path: `/api/business-users/${encodedId}/${action.toLowerCase()}`,
    method: "POST",
    body: { expectedRevision: "user-r" },
    call: () => api.changeBusinessUserState(id, action, "user-r"),
  })),
  {
    name: "bulk state",
    path: "/api/business-users/status-changes",
    method: "POST",
    body: { action: "STOP", items },
    call: () => api.saveBusinessUserStatusChanges("STOP", items),
  },
];
afterEach(() => vi.unstubAllGlobals());
it.each(cases)(
  "registry forwards $name to the feature client",
  async (test) => {
    const data = { changed: false };
    const fetcher = vi
      .fn()
      .mockImplementation(
        async () =>
          new Response(JSON.stringify({ success: true, meta: {}, data })),
      );
    stubBusinessFetch(fetcher);
    await expect(test.call()).resolves.toEqual(data);
    expect(fetcher).toHaveBeenCalledTimes(1);
    const [url, options] = fetcher.mock.calls[0];
    expect(url).toBe(test.path);
    expect(options.method ?? "GET").toBe(test.method);
    if (test.body) expect(JSON.parse(options.body)).toEqual(test.body);
    else expect(options.body).toBeUndefined();
  },
);
it.each(cases)(
  "registry preserves $name errors without retry or authentication redirect",
  async (test) => {
    const fetcher = vi.fn().mockImplementation(
      async () =>
        new Response(
          JSON.stringify({
            success: false,
            meta: {},
            code: "STALE_WRITE",
            message: "최신값을 조회해 주세요.",
            requestId: "trace",
          }),
          { status: 409 },
        ),
    );
    stubBusinessFetch(fetcher);
    await expect(test.call()).rejects.toBeInstanceOf(api.ApiError);
    expect(fetcher).toHaveBeenCalledTimes(1);
  },
);
