import { stubBusinessFetch } from "../../shared/testSupport/businessFixtures";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getNotificationUnreadCount,
  getNotifications,
  saveNotificationRead,
  saveNotificationsReadAll,
} from "./notificationsApi";
afterEach(() => vi.unstubAllGlobals());
describe("알림 상대경로 명령 계약", () => {
  it("현재 주체 배지에는 어떤 query도 보내지 않는다", async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ success: true, meta: {}, data: {} }),
    });
    stubBusinessFetch(fetcher);
    await getNotificationUnreadCount();
    expect(fetcher.mock.calls[0][0]).toBe("/api/notifications/unread-count");
  });
  it("읽음 두 명령에는 body·userId·readAt을 보내지 않는다", async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ success: true, meta: {}, data: {} }),
    });
    stubBusinessFetch(fetcher);
    const selected = { id: "912345678901" };
    await saveNotificationRead(selected.id);
    await saveNotificationsReadAll();
    expect(fetcher.mock.calls[0][0]).toBe(
      `/api/notifications/${selected.id}/read`,
    );
    expect(fetcher.mock.calls[1][0]).toBe("/api/notifications/read-all");
    for (const call of fetcher.mock.calls) {
      expect(call[1].method).toBe("POST");
      expect(call[1].body).toBeUndefined();
    }
  });
  it("목록은 계약의 20개와 고정 복합정렬을 사용한다", async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ success: true, meta: {}, data: {} }),
    });
    stubBusinessFetch(fetcher);
    await getNotifications(2);
    expect(fetcher.mock.calls[0][0]).toBe(
      "/api/notifications?page=2&size=20&sort=notifiedAt,desc;id,desc",
    );
  });
});
