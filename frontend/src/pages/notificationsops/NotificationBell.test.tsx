import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { NotificationBell } from "./NotificationBell";
import * as api from "./notificationsApi";
vi.mock("./notificationsApi", async (original) => ({
  ...(await original<typeof import("./notificationsApi")>()),
  getNotificationUnreadCount: vi.fn(),
  getNotifications: vi.fn(),
  saveNotificationRead: vi.fn(),
  saveNotificationsReadAll: vi.fn(),
}));
const observedAt = "2026-07-15T12:00:00+09:00";
const count = (unreadCount = 101): api.NotificationUnreadCount => ({
  unreadCount,
  receivableCount: 4,
  currentMonthUnclosed: true,
  observedAt,
});
const item = (id = "1", read = false): api.NotificationItem => ({
  id,
  read,
  typeCode: "DUN",
  content: `미수 알림 ${id}`,
  sendMethodCode: "BOTH",
  readAt: read ? observedAt : null,
  notifiedAt: "2026-07-15T09:00:00+09:00",
  link: { screenId: "UI-13", linkKey: "B-202607-001" },
});
const page = (
  items = [item()],
  page = 1,
  hasMore = false,
): api.NotificationPage => ({
  items,
  page,
  size: 20,
  total: items.length,
  hasMore,
  observedAt,
});
beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(document, "hidden", {
    configurable: true,
    value: false,
  });
  vi.mocked(api.getNotificationUnreadCount).mockResolvedValue(count());
  vi.mocked(api.getNotifications).mockResolvedValue(page());
  vi.mocked(api.saveNotificationRead).mockResolvedValue({
    notification: item("1", true),
    changed: true,
  });
  vi.mocked(api.saveNotificationsReadAll).mockResolvedValue({
    changedCount: 1,
    cutoffAt: observedAt,
  });
});
afterEach(() => {
  vi.useRealTimers();
});
async function open() {
  await userEvent.click(screen.getByRole("button", { name: "알림" }));
  await screen.findByTestId("notification-row-1");
}

describe("알림 배지와 읽음", () => {
  it("전체 101건을 99+로 표시하고 실제 업무 배지를 상위 메뉴에 전달한다", async () => {
    const onBadges = vi.fn();
    render(<NotificationBell onBadges={onBadges} />);
    expect(await screen.findByTestId("notification-count")).toHaveTextContent(
      "99+",
    );
    expect(screen.getByLabelText("미읽음 101개")).toBeInTheDocument();
    expect(onBadges).toHaveBeenCalledWith(count());
    expect(api.getNotifications).not.toHaveBeenCalled();
  });
  it("0건은 배지를 숨긴다", async () => {
    vi.mocked(api.getNotificationUnreadCount).mockResolvedValue(count(0));
    render(<NotificationBell />);
    await waitFor(() =>
      expect(api.getNotificationUnreadCount).toHaveBeenCalled(),
    );
    expect(screen.queryByTestId("notification-count")).not.toBeInTheDocument();
  });
  it("목록 열기는 읽음이나 메일 명령을 호출하지 않고 최신 목록과 배지만 조회한다", async () => {
    render(<NotificationBell />);
    await open();
    expect(api.getNotifications).toHaveBeenCalledWith(1);
    expect(api.saveNotificationRead).not.toHaveBeenCalled();
    expect(api.saveNotificationsReadAll).not.toHaveBeenCalled();
    expect(screen.getByTestId("notification-row-1")).toHaveClass(
      "notification-unread",
    );
    expect(screen.getByText("오늘 09:00")).toHaveAttribute(
      "title",
      item().notifiedAt,
    );
    expect(screen.getByText("미수 알림")).toHaveClass("sicost-tone-warn");
  });
  it("읽음 commit 응답 뒤 반환된 실제 키로 이동하고 벨과 목록을 갱신한다", async () => {
    const onOpenTarget = vi.fn().mockResolvedValue(true);
    let resolve!: (value: {
      notification: api.NotificationItem;
      changed: boolean;
    }) => void;
    vi.mocked(api.saveNotificationRead).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    render(<NotificationBell onOpenTarget={onOpenTarget} />);
    await open();
    await userEvent.click(screen.getByTestId("notification-read-1"));
    expect(onOpenTarget).not.toHaveBeenCalled();
    expect(screen.getByTestId("notification-read-1")).toBeDisabled();
    vi.mocked(api.getNotificationUnreadCount).mockResolvedValue(count(100));
    vi.mocked(api.getNotifications).mockResolvedValue(page([item("1", true)]));
    await act(async () =>
      resolve({ notification: item("1", true), changed: true }),
    );
    await waitFor(() => expect(onOpenTarget).toHaveBeenCalledWith(item().link));
    expect(api.saveNotificationRead).toHaveBeenCalledWith("1");
    expect(api.getNotifications).toHaveBeenCalledTimes(2);
    expect(
      screen.queryByTestId("notification-popover"),
    ).not.toBeInTheDocument();
  });
  it("이미 읽은 알림도 같은 명령 응답으로 이동하며 최초시각을 브라우저에서 만들지 않는다", async () => {
    vi.mocked(api.getNotifications).mockResolvedValue(page([item("1", true)]));
    vi.mocked(api.saveNotificationRead).mockResolvedValue({
      notification: item("1", true),
      changed: false,
    });
    const onOpenTarget = vi.fn().mockReturnValue(true);
    render(<NotificationBell onOpenTarget={onOpenTarget} />);
    await open();
    await userEvent.click(screen.getByTestId("notification-read-1"));
    await waitFor(() => expect(onOpenTarget).toHaveBeenCalledWith(item().link));
  });
  it("삭제되거나 준비 중인 대상은 읽음을 유지하고 안내하며 외부 이동하지 않는다", async () => {
    vi.mocked(api.saveNotificationRead).mockResolvedValue({
      notification: { ...item("1", true), link: null },
      changed: true,
    });
    vi.mocked(api.getNotifications).mockResolvedValue(page([item("1", true)]));
    const onOpenTarget = vi.fn();
    render(<NotificationBell onOpenTarget={onOpenTarget} />);
    await open();
    await userEvent.click(screen.getByTestId("notification-read-1"));
    expect(
      await screen.findByText(/대상이 없거나 연결 화면이 준비 중/),
    ).toBeInTheDocument();
    expect(onOpenTarget).not.toHaveBeenCalled();
    expect(screen.getByTestId("notification-row-1")).toHaveClass(
      "notification-read",
    );
  });
  it("읽음 실패는 이동하지 않고 오류를 표시한다", async () => {
    vi.mocked(api.saveNotificationRead).mockRejectedValue(
      new Error("알림을 찾을 수 없습니다."),
    );
    const onOpenTarget = vi.fn();
    render(<NotificationBell onOpenTarget={onOpenTarget} />);
    await open();
    await userEvent.click(screen.getByTestId("notification-read-1"));
    expect(
      await screen.findByText("알림을 찾을 수 없습니다."),
    ).toBeInTheDocument();
    expect(onOpenTarget).not.toHaveBeenCalled();
    expect(screen.getByTestId("notification-row-1")).toHaveClass(
      "notification-unread",
    );
  });
  it("모두 읽음 후 한 번만 명령하고 벨·목록을 재조회한다", async () => {
    render(<NotificationBell />);
    await open();
    vi.mocked(api.getNotificationUnreadCount).mockResolvedValue(count(0));
    vi.mocked(api.getNotifications).mockResolvedValue(page([item("1", true)]));
    await userEvent.click(screen.getByRole("button", { name: "모두 읽음" }));
    await waitFor(() =>
      expect(screen.getByTestId("notification-row-1")).toHaveClass(
        "notification-read",
      ),
    );
    expect(api.saveNotificationsReadAll).toHaveBeenCalledTimes(1);
    expect(api.getNotifications).toHaveBeenCalledTimes(2);
    expect(screen.queryByTestId("notification-count")).not.toBeInTheDocument();
  });
  it("모두 읽음 실패 시 재전송하지 않고 기존 목록을 유지한다", async () => {
    vi.mocked(api.saveNotificationsReadAll).mockRejectedValue(
      new Error("읽음 처리 실패"),
    );
    render(<NotificationBell />);
    await open();
    await userEvent.click(screen.getByRole("button", { name: "모두 읽음" }));
    expect(await screen.findByText("읽음 처리 실패")).toBeInTheDocument();
    expect(api.getNotifications).toHaveBeenCalledTimes(1);
  });
  it("읽음 성공 후 조회 실패를 저장 실패와 구별하고 배지를 0으로 바꾸지 않는다", async () => {
    render(<NotificationBell />);
    await open();
    vi.mocked(api.getNotificationUnreadCount).mockRejectedValue(
      new Error("배지 조회 실패"),
    );
    vi.mocked(api.getNotifications).mockRejectedValue(
      new Error("목록 조회 실패"),
    );
    await userEvent.click(screen.getByRole("button", { name: "모두 읽음" }));
    expect(
      await screen.findByText(/읽음 처리는 완료됐지만/),
    ).toBeInTheDocument();
    expect(screen.getByTestId("notification-count")).toHaveTextContent("99+");
    expect(api.saveNotificationsReadAll).toHaveBeenCalledTimes(1);
  });
  it("활성 30초 조회는 초점·읽음 상태를 바꾸지 않고 비활성 때 멈춘다", async () => {
    vi.useFakeTimers();
    const result = render(
      <>
        <input aria-label="원업무 입력" />
        <NotificationBell />
      </>,
    );
    await act(async () => {});
    const input = screen.getByRole("textbox");
    input.focus();
    const initial = vi.mocked(api.getNotificationUnreadCount).mock.calls.length;
    await act(async () => vi.advanceTimersByTime(30000));
    expect(api.getNotificationUnreadCount).toHaveBeenCalledTimes(initial + 1);
    expect(input).toHaveFocus();
    Object.defineProperty(document, "hidden", {
      configurable: true,
      value: true,
    });
    await act(async () => vi.advanceTimersByTime(60000));
    expect(api.getNotificationUnreadCount).toHaveBeenCalledTimes(initial + 1);
    Object.defineProperty(document, "hidden", {
      configurable: true,
      value: false,
    });
    await act(async () =>
      document.dispatchEvent(new Event("visibilitychange")),
    );
    expect(api.getNotificationUnreadCount).toHaveBeenCalledTimes(initial + 2);
    expect(api.saveNotificationRead).not.toHaveBeenCalled();
    expect(api.saveNotificationsReadAll).not.toHaveBeenCalled();
    result.unmount();
    await act(async () => vi.advanceTimersByTime(30000));
    expect(api.getNotificationUnreadCount).toHaveBeenCalledTimes(initial + 2);
  });
  it("실패한 가벼운 조회는 직전 배지와 초점을 보존하고 재시도로 회복한다", async () => {
    render(<NotificationBell />);
    await screen.findByTestId("notification-count");
    vi.mocked(api.getNotificationUnreadCount).mockRejectedValueOnce(
      new Error("조회 실패"),
    );
    act(() => window.dispatchEvent(new Event("focus")));
    await screen.findByRole("button", { name: "알림 수 다시 조회" });
    expect(screen.getByTestId("notification-count")).toHaveTextContent("99+");
    vi.mocked(api.getNotificationUnreadCount).mockResolvedValue(count(2));
    await userEvent.click(
      screen.getByRole("button", { name: "알림 수 다시 조회" }),
    );
    await waitFor(() =>
      expect(screen.getByTestId("notification-count")).toHaveTextContent("2"),
    );
  });
  it("발행 commit과 활성 복귀에 새 배지를 조회한다", async () => {
    render(<NotificationBell />);
    await screen.findByTestId("notification-count");
    const initial = vi.mocked(api.getNotificationUnreadCount).mock.calls.length;
    act(() => window.dispatchEvent(new Event(api.NOTIFICATIONS_COMMITTED)));
    await waitFor(() =>
      expect(api.getNotificationUnreadCount).toHaveBeenCalledTimes(initial + 1),
    );
  });
  it("목록 없음과 조회 오류를 구별하고 requestId와 재시도를 제공한다", async () => {
    vi.mocked(api.getNotifications).mockRejectedValueOnce(
      new ApiError(500, {
        success: false,
        meta: {},
        code: "INTERNAL_ERROR",
        message: "알림 조회 실패",
        requestId: "notice-request",
      }),
    );
    render(<NotificationBell />);
    await userEvent.click(screen.getByRole("button", { name: "알림" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "notice-request",
    );
    expect(screen.queryByText("알림이 없습니다.")).not.toBeInTheDocument();
    vi.mocked(api.getNotifications).mockResolvedValue(page([]));
    await userEvent.click(screen.getByTestId("notification-list-retry"));
    expect(await screen.findByText("알림이 없습니다.")).toBeInTheDocument();
  });
  it("더보기는 다음 20개만 조회하고 중복 id를 제거한다", async () => {
    vi.mocked(api.getNotifications)
      .mockResolvedValueOnce(page([item()], 1, true))
      .mockResolvedValueOnce(page([item(), item("2")], 2, false));
    render(<NotificationBell />);
    await open();
    await userEvent.click(screen.getByRole("button", { name: "더보기 20개" }));
    await screen.findByTestId("notification-row-2");
    expect(screen.getAllByTestId("notification-row-1")).toHaveLength(1);
    expect(api.getNotifications).toHaveBeenLastCalledWith(2);
    expect(
      screen.queryByRole("button", { name: "더보기 20개" }),
    ).not.toBeInTheDocument();
  });
  it("Esc는 닫고 벨에 초점을 반환하며 외부 클릭도 쓰기 없이 닫는다", async () => {
    render(<NotificationBell />);
    await open();
    await userEvent.keyboard("{Escape}");
    expect(screen.getByRole("button", { name: "알림" })).toHaveFocus();
    expect(
      screen.queryByTestId("notification-popover"),
    ).not.toBeInTheDocument();
    await open();
    fireEvent.pointerDown(document.body);
    expect(
      screen.queryByTestId("notification-popover"),
    ).not.toBeInTheDocument();
    expect(api.saveNotificationRead).not.toHaveBeenCalled();
  });
  it("서버 링크의 외부URL·스크립트·미지원 키를 실제 화면으로 바꾸지 않는다", () => {
    for (const key of [
      "https://evil.test",
      "javascript:alert(1)",
      "../secret",
      "B-202607-001?x=1",
    ])
      expect(
        api.notificationDestination({ screenId: "UI-13", linkKey: key }),
      ).toBeNull();
    expect(
      api.notificationDestination({
        screenId: "https://evil.test",
        linkKey: null,
      }),
    ).toBeNull();
    expect(
      api.notificationDestination({
        screenId: "UI-13",
        linkKey: "B-202607-001",
      }),
    ).toEqual({
      screenId: "UI-13",
      state: { fromNotification: true, billId: "B-202607-001" },
    });
  });
});
