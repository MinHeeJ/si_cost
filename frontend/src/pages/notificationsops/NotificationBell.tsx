import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { Icon } from "../../shared/Icon";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import * as api from "./notificationsApi";
import "./notifications.css";

export type NotificationBellProps = {
  onOpenTarget?(link: api.NotificationLink): boolean | Promise<boolean>;
  onOpenAutomation?(): void;
  onBadges?(badges: api.NotificationUnreadCount): void;
};
const labels: Record<api.NotificationItem["typeCode"], string> = {
  DUN: "미수 알림",
  WTHDR: "철수 알림",
  CASH_SHORT: "자금 부족",
  UPLD_DONE: "업로드 완료",
  AI_DONE: "AI 예측 완료",
  QUOTE_REJ: "견적 반려",
};
function relativeTime(value: string, basis: string) {
  const options = { timeZone: "Asia/Seoul" };
  const date = new Date(value),
    today = new Date(basis);
  const day = new Intl.DateTimeFormat("sv-SE", {
    ...options,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const time = new Intl.DateTimeFormat("ko-KR", {
    ...options,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
  const targetDay = day.format(date),
    currentDay = day.format(today);
  const yesterday = day.format(new Date(today.getTime() - 86400000));
  return `${targetDay === currentDay ? "오늘" : targetDay === yesterday ? "어제" : targetDay.slice(0, 4) === currentDay.slice(0, 4) ? targetDay.slice(5) : targetDay} ${time}`;
}
export function NotificationBell({
  onOpenTarget,
  onBadges,
  onOpenAutomation,
}: NotificationBellProps) {
  const [open, setOpen] = useState(false);
  const [badges, setBadges] = useState<api.NotificationUnreadCount>();
  const [countError, setCountError] = useState<unknown>();
  const [listError, setListError] = useState<unknown>();
  const [page, setPage] = useState<api.NotificationPage>();
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("");
  const anchor = useRef<HTMLDivElement>(null),
    trigger = useRef<HTMLButtonElement>(null);
  const alive = useRef(true),
    countGeneration = useRef(0),
    listGeneration = useRef(0);
  const dispatching = useRef(false);
  const openRef = useRef(false),
    pageRef = useRef<api.NotificationPage>();
  const callbacks = useRef({ onBadges });
  callbacks.current = { onBadges };
  const { pending, message, commit } = useCommitRefresh(
    "읽음 처리는 완료됐지만 알림을 다시 불러오지 못했습니다. 재조회해 주세요.",
  );
  const refreshCount = useCallback(async (background = false) => {
    const generation = ++countGeneration.current;
    try {
      const result = await (background
        ? api.getNotificationUnreadCount(true)
        : api.getNotificationUnreadCount());
      if (alive.current && generation === countGeneration.current) {
        setBadges(result);
        setCountError(undefined);
        callbacks.current.onBadges?.(result);
      }
    } catch (error) {
      if (alive.current && generation === countGeneration.current)
        setCountError(error);
      throw error;
    }
  }, []);
  const load = useCallback(async (next = 1, background = false) => {
    const generation = ++listGeneration.current;
    setLoading(true);
    try {
      const result = await (background
        ? api.getNotifications(next, true)
        : api.getNotifications(next));
      if (alive.current && generation === listGeneration.current) {
        const items =
          next === 1
            ? result.items
            : [...(pageRef.current?.items ?? []), ...result.items];
        const unique = Array.from(
          new Map(items.map((item) => [item.id, item])).values(),
        );
        pageRef.current = { ...result, items: unique };
        setPage(pageRef.current);
        setListError(undefined);
      }
    } catch (error) {
      if (alive.current && generation === listGeneration.current)
        setListError(error);
      throw error;
    } finally {
      if (alive.current && generation === listGeneration.current)
        setLoading(false);
    }
  }, []);
  const refresh = useCallback(
    async (background = false) => {
      await Promise.all([
        refreshCount(background),
        ...(openRef.current ? [load(1, background)] : []),
      ]);
    },
    [load, refreshCount],
  );
  useEffect(() => {
    alive.current = true;
    const quietRefresh = () => {
      if (!document.hidden && !dispatching.current)
        void refresh(true).catch(() => {});
    };
    const poll = () => {
      if (!document.hidden) void refreshCount(true).catch(() => {});
    };
    quietRefresh();
    const timer = window.setInterval(poll, 30000);
    const events = [
      api.NOTIFICATIONS_COMMITTED,
      "sicost:bills-committed",
      "sicost:billing-committed",
      "sicost:receipts-committed",
      "sicost:uploads-committed",
      "sicost:sales-committed",
    ];
    document.addEventListener("visibilitychange", quietRefresh);
    window.addEventListener("focus", quietRefresh);
    events.forEach((event) => window.addEventListener(event, quietRefresh));
    return () => {
      alive.current = false;
      ++countGeneration.current;
      ++listGeneration.current;
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", quietRefresh);
      window.removeEventListener("focus", quietRefresh);
      events.forEach((event) =>
        window.removeEventListener(event, quietRefresh),
      );
    };
  }, [refresh, refreshCount]);
  const close = useCallback((focus = true) => {
    openRef.current = false;
    setOpen(false);
    ++listGeneration.current;
    setLoading(false);
    if (focus) trigger.current?.focus();
  }, []);
  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (!anchor.current?.contains(event.target as Node)) close(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.stopPropagation();
        close();
      }
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
    };
  }, [open, close]);
  const committedRefresh = async () => {
    ++countGeneration.current;
    ++listGeneration.current;
    dispatching.current = true;
    try {
      window.dispatchEvent(new Event(api.NOTIFICATIONS_COMMITTED));
    } finally {
      dispatching.current = false;
    }
    await refresh();
  };
  const read = async (item: api.NotificationItem) => {
    setNotice("");
    const result: { notification?: api.NotificationItem } = {};
    try {
      await commit(
        async () => {
          result.notification = (
            await api.saveNotificationRead(item.id)
          ).notification;
        },
        committedRefresh,
        { quiet: true },
      );
      const saved = result.notification;
      if (!saved) return;
      if (saved.link && onOpenTarget && (await onOpenTarget(saved.link)))
        close();
      else
        setNotice(
          "알림은 읽음 처리됐습니다. 대상이 없거나 연결 화면이 준비 중입니다.",
        );
    } catch {
      /* useCommitRefresh retains the localized command error. */
    }
  };
  const errorBanner = (error: unknown, retry: () => void, id: string) =>
    error ? (
      <ErrorBanner
        message={
          error instanceof Error ? error.message : "알림을 불러오지 못했습니다."
        }
        requestId={error instanceof ApiError ? error.body.requestId : undefined}
        onRetry={retry}
        retryTestId={id}
      />
    ) : null;
  return (
    <div
      ref={anchor}
      className="notification-anchor"
      data-testid="notification-bell"
    >
      <button
        ref={trigger}
        type="button"
        className="sicost-icon-button notification-trigger"
        aria-label="알림"
        aria-expanded={open}
        aria-controls={open ? "notification-popover" : undefined}
        data-testid="notification-open-button"
        onClick={() => {
          trigger.current?.classList.remove("sicost-bell-click");
          if (trigger.current) { void trigger.current.offsetWidth; trigger.current.classList.add("sicost-bell-click"); }
          if (open) close();
          else {
            openRef.current = true;
            setOpen(true);
            setNotice("");
            void refresh().catch(() => {});
          }
        }}
      >
        <Icon name="bell" />
        {badges && badges.unreadCount > 0 && (
          <span
            className="notification-count"
            data-testid="notification-count"
            aria-label={`미읽음 ${badges.unreadCount}개`}
          >
            {badges.unreadCount > 99 ? "99+" : badges.unreadCount}
          </span>
        )}
      </button>
      {!open && countError != null && (
        <button
          type="button"
          className="sicost-link"
          data-testid="notification-count-retry"
          aria-label="알림 수 다시 조회"
          onClick={() => void refreshCount().catch(() => {})}
        >
          !
        </button>
      )}
      {open && (
        <section
          id="notification-popover"
          role="region"
          aria-label="알림 목록"
          className="sicost-header-popover notification-popover"
          data-testid="notification-popover"
        >
          <header>
            <strong>
              알림 {badges ? `· 미읽음 ${badges.unreadCount}` : ""}
            </strong>
            <button
              type="button"
              data-testid="notification-read-all-button"
              disabled={pending}
              onClick={() => {
                setNotice("");
                void commit(
                  api.saveNotificationsReadAll,
                  committedRefresh,
                ).catch(() => {});
              }}
            >
              모두 읽음
            </button>
            <button
              type="button"
              data-testid="notification-close-button"
              aria-label="알림 닫기"
              onClick={() => close()}
            >
              닫기
            </button>
          </header>
          {errorBanner(
            countError,
            () => void refreshCount().catch(() => {}),
            "notification-badge-retry",
          )}
          {errorBanner(
            listError,
            () => void load().catch(() => {}),
            "notification-list-retry",
          )}
          {message && <p role="status">{message}</p>}
          {notice && <p role="status">{notice}</p>}
          {loading && <p role="status">알림을 불러오는 중입니다.</p>}
          {page && page.items.length === 0 && !listError && (
            <div className="notification-empty">알림이 없습니다.</div>
          )}
          <ul>
            {page?.items.map((item) => (
              <li
                key={item.id}
                data-testid={`notification-row-${item.id}`}
                className={
                  item.read ? "notification-read" : "notification-unread"
                }
              >
                <button
                  type="button"
                  disabled={pending}
                  data-testid={`notification-read-${item.id}`}
                  onClick={() => void read(item)}
                >
                  <span>{item.content}</span>
                  <span className="notification-metadata">
                    <StatusBadge
                      tone={
                        item.typeCode === "DUN" ||
                        item.typeCode === "CASH_SHORT"
                          ? "warn"
                          : item.typeCode === "QUOTE_REJ"
                            ? "bad"
                            : "info"
                      }
                    >
                      {labels[item.typeCode]}
                    </StatusBadge>
                    <span>{item.read ? "읽음" : "미읽음"}</span>
                    <time dateTime={item.notifiedAt} title={item.notifiedAt}>
                      {relativeTime(item.notifiedAt, page.observedAt)}
                    </time>
                  </span>
                </button>
              </li>
            ))}
          </ul>
          <footer>
            {onOpenAutomation && (
              <button
                type="button"
                onClick={() => {
                  close();
                  onOpenAutomation();
                }}
              >
                자동처리상태
              </button>
            )}
            {page?.hasMore && (
              <button
                type="button"
                disabled={loading || pending}
                data-testid="notification-more-button"
                onClick={() => void load(page.page + 1).catch(() => {})}
              >
                더보기 20개
              </button>
            )}
            <button
              type="button"
              disabled={loading}
              data-testid="notification-refresh-button"
              onClick={() => void refresh().catch(() => {})}
            >
              재조회
            </button>
            <small>알림은 30일 보관</small>
          </footer>
        </section>
      )}
    </div>
  );
}
