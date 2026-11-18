import { request } from "../../shared/api";

export type NotificationLink = { screenId: string; linkKey: string | null };
export type NotificationItem = {
  id: string;
  typeCode:
    | "DUN"
    | "WTHDR"
    | "CASH_SHORT"
    | "UPLD_DONE"
    | "AI_DONE"
    | "QUOTE_REJ";
  content: string;
  sendMethodCode: "SYS" | "BOTH";
  read: boolean;
  readAt: string | null;
  notifiedAt: string;
  link: NotificationLink | null;
};
export type NotificationPage = {
  items: NotificationItem[];
  page: number;
  size: 20 | 50 | 100;
  total: number;
  hasMore: boolean;
  observedAt: string;
};
export type NotificationUnreadCount = {
  unreadCount: number;
  receivableCount?: number;
  currentMonthUnclosed?: boolean;
  observedAt: string;
};
export const getNotificationUnreadCount = (background = false) =>
  request<NotificationUnreadCount>(
    "/api/notifications/unread-count",
    background ? { headers: { "X-Background-Request": "true" } } : {},
  );
export const getNotifications = (page = 1, background = false) =>
  request<NotificationPage>(
    `/api/notifications?page=${page}&size=20&sort=notifiedAt,desc;id,desc`,
    background ? { headers: { "X-Background-Request": "true" } } : {},
  );
export const saveNotificationRead = (id: string) =>
  request<{ notification: NotificationItem; changed: boolean }>(
    `/api/notifications/${encodeURIComponent(id)}/read`,
    { method: "POST" },
  );
export const saveNotificationsReadAll = () =>
  request<{ changedCount: number; cutoffAt: string }>(
    "/api/notifications/read-all",
    { method: "POST" },
  );
export const NOTIFICATIONS_COMMITTED = "sicost:notifications-committed";

export function notificationDestination(link: NotificationLink) {
  if (
    !/^UI-\d{2}$/.test(link.screenId) ||
    (link.linkKey !== null && !/^[A-Za-z0-9_-]{1,50}$/.test(link.linkKey))
  )
    return null;
  const key = link.linkKey;
  if (key === null) return { screenId: link.screenId, state: undefined };
  if (link.screenId === "UI-13" && /^B-\d{6}-\d{3}$/.test(key))
    return {
      screenId: link.screenId,
      state: { fromNotification: true, billId: key },
    };
  if (link.screenId === "UI-29")
    return { screenId: link.screenId, state: { opportunityId: key } };
  return null;
}
