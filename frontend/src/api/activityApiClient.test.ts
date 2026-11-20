import { stubBusinessFetch } from "../shared/testSupport/businessFixtures";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as api from "./apiClient";
import { dashboardFixture } from "../pages/dashboardops/dashboardFixture";

const reminder = {
  expectedRevision: "selected-preview",
  operationId: "123e4567-e89b-42d3-a456-426614174000",
};
const cases: {
  name: string;
  path: string;
  method: string;
  body?: unknown;
  call(): Promise<unknown>;
}[] = [
  {
    name: "getDashboard",
    path: "/api/dashboard?month=2026-07",
    method: "GET",
    call: () => api.getDashboard("2026-07"),
  },
  {
    name: "getNotifications",
    path: "/api/notifications?page=1&size=20&sort=notifiedAt,desc;id,desc",
    method: "GET",
    call: () => api.getNotifications(),
  },
  {
    name: "getNotificationUnreadCount",
    path: "/api/notifications/unread-count",
    method: "GET",
    call: () => api.getNotificationUnreadCount(),
  },
  {
    name: "saveNotificationRead",
    path: "/api/notifications/137/read",
    method: "POST",
    call: () => api.saveNotificationRead("137"),
  },
  {
    name: "saveNotificationsReadAll",
    path: "/api/notifications/read-all",
    method: "POST",
    call: () => api.saveNotificationsReadAll(),
  },
  {
    name: "getReceivableDeliveryPreview",
    path: "/api/receivables/B-202607-042/delivery-preview",
    method: "GET",
    call: () => api.getReceivableDeliveryPreview("B-202607-042"),
  },
  {
    name: "getReceivableDeliveries",
    path: "/api/receivables/B-202607-042/deliveries?page=1&size=50&sort=reservedSn%2Casc",
    method: "GET",
    call: () => api.getReceivableDeliveries("B-202607-042"),
  },
  {
    name: "createReceivableReminder",
    path: "/api/receivables/B-202607-042/reminders",
    method: "POST",
    body: reminder,
    call: () => api.createReceivableReminder("B-202607-042", reminder),
  },
  {
    name: "getAutomationRuns",
    path: "/api/automation-runs?page=1&size=50&sort=runDate%2Cdesc%3BjobCode%2Casc",
    method: "GET",
    call: () => api.getAutomationRuns({ page: 1, size: 50 }),
  },
  {
    name: "createAutomationRunToday",
    path: "/api/automation-runs/DAILY_OPERATIONS/run-today",
    method: "POST",
    call: () => api.createAutomationRunToday("DAILY_OPERATIONS"),
  },
  {
    name: "getMyProfile",
    path: "/api/me/profile",
    method: "GET",
    call: () => api.getMyProfile(),
  },
];
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
describe("activity API registry", () => {
  it.each(cases)(
    "$name reuses the feature transport and approved inputs",
    async (test) => {
      const data =
        test.name === "getDashboard"
          ? dashboardFixture()
          : { value: "1.2300", completed: false };
      const fetcher = vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ success: true, meta: {}, data }), {
          status: test.method === "POST" ? 202 : 200,
        }),
      );
      stubBusinessFetch(fetcher);
      expect(await test.call()).toEqual(data);
      expect(fetcher).toHaveBeenCalledOnce();
      const [path, options] = fetcher.mock.calls[0] as [string, RequestInit];
      expect(path).toBe(test.path);
      expect(options.method ?? "GET").toBe(test.method);
      expect(
        options.body ? JSON.parse(String(options.body)) : undefined,
      ).toEqual(test.body);
      expect(new Headers(options.headers).get("Accept")).toBe(
        "application/json",
      );
    },
  );
  it.each(cases)(
    "$name preserves localized errors and does not retry",
    async (test) => {
      const body = {
        success: false,
        meta: {},
        code: "STALE_WRITE",
        message: "다시 조회해 주세요.",
        field: "expectedRevision",
        requestId: "activity-request",
      };
      const fetcher = vi
        .fn()
        .mockResolvedValue(new Response(JSON.stringify(body), { status: 409 }));
      stubBusinessFetch(fetcher);
      await expect(test.call()).rejects.toMatchObject({ status: 409, body });
      expect(fetcher).toHaveBeenCalledOnce();
    },
  );
  it("exports the selected dashboard workbook without parsing it as JSON", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response("workbook", {
        headers: {
          "Content-Type":
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "Content-Disposition": 'attachment; filename="dashboard.xlsx"',
        },
      }),
    );
    stubBusinessFetch(fetcher);
    const create = vi.fn().mockReturnValue("blob:dashboard");
    const revoke = vi.fn();
    vi.stubGlobal(
      "URL",
      class extends URL {
        static createObjectURL = create;
        static revokeObjectURL = revoke;
      },
    );
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => {});
    await api.getDashboardExport("2026-07");
    expect(fetcher.mock.calls[0][0]).toBe(
      "/api/dashboard/export?month=2026-07",
    );
    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({
        size: 8,
        type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      }),
    );
    expect(click).toHaveBeenCalledOnce();
    expect(revoke).toHaveBeenCalledWith("blob:dashboard");
  });
  it("does not download on a failed dashboard export", async () => {
    const body = {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "다운로드 기록 실패",
      requestId: "export-request",
    };
    const fetcher = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(body), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      }),
    );
    stubBusinessFetch(fetcher);
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => {});
    await expect(api.getDashboardExport("2026-07")).rejects.toMatchObject({
      status: 500,
      body,
    });
    expect(click).not.toHaveBeenCalled();
    expect(fetcher).toHaveBeenCalledOnce();
  });
});
