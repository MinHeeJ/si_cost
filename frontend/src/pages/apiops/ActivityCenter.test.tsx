import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ActivityCenter } from "./ActivityCenter";
import type { AutomationPage } from "../automationrunsops/automationRunsApi";
import type { NotificationPage } from "../notificationsops/notificationsApi";

let automation: AutomationPage;
let notifications: NotificationPage;
let failRuns: boolean;
const calls: { path: string; method: string }[] = [];
const at = "2026-07-15T09:00:00+09:00";
beforeEach(() => {
  calls.length = 0;
  failRuns = false;
  automation = {
    items: [],
    page: 1,
    size: 50,
    total: 0,
    hasMore: false,
    manualRunEnabled: false,
    observedAt: at,
  };
  notifications = {
    items: [],
    page: 1,
    size: 20,
    total: 0,
    hasMore: false,
    observedAt: at,
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(async (path: string, options?: RequestInit) => {
      calls.push({ path, method: options?.method ?? "GET" });
      const route = path.split("?")[0];
      if (route === "/api/automation-runs" && failRuns)
        return new Response(
          JSON.stringify({
            success: false,
            meta: {},
            code: "INTERNAL_ERROR",
            message: "실행 기록 조회 실패",
            requestId: "activity-trace",
          }),
          { status: 500 },
        );
      const data =
        route === "/api/notifications/unread-count"
          ? {
              unreadCount: 0,
              receivableCount: 0,
              currentMonthUnclosed: true,
              observedAt: at,
            }
          : route === "/api/notifications"
            ? notifications
            : automation;
      return new Response(JSON.stringify({ success: true, meta: {}, data }), {
        status: 200,
      });
    }),
  );
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
it("opens existing notification and automation regions without introducing another route or firing a command", async () => {
  render(<ActivityCenter />);
  await userEvent.click(screen.getByRole("button", { name: "알림" }));
  expect(await screen.findByText("알림이 없습니다.")).toBeInTheDocument();
  const list = calls.find((c) => c.path.startsWith("/api/notifications?"));
  expect(new URLSearchParams(list?.path.split("?")[1]).get("sort")).toBe(
    "notifiedAt,desc;id,desc",
  );
  await userEvent.click(within(screen.getByTestId("notification-popover")).getByRole("button", { name: "자동처리상태" }));
  expect(
    await screen.findByText("자동 처리 실행 기록이 없습니다."),
  ).toBeInTheDocument();
  const runs = calls.find((c) => c.path.startsWith("/api/automation-runs?"));
  expect(new URLSearchParams(runs?.path.split("?")[1]).get("sort")).toBe(
    "runDate,desc;jobCode,asc",
  );
  expect(calls.every((c) => c.method === "GET")).toBe(true);
  expect(
    screen.queryByRole("button", { name: "오늘02시작업실행" }),
  ).not.toBeInTheDocument();
});
it("uses actual failed state and opens the existing billing host for delivery diagnostics", async () => {
  automation.items = [
    {
      jobCode: "RECEIVABLE_DUNNING",
      runDate: "2026-07-15",
      state: "FAILED",
      lastStep: 0,
      startedAt: at,
      endedAt: at,
      errorMessage: "대상 청구 확인 실패",
    },
  ];
  automation.total = 1;
  const target = vi.fn().mockResolvedValue(true);
  render(<ActivityCenter onOpenTarget={target} />);
  await userEvent.click(screen.getByRole("button", { name: "자동처리상태" }));
  expect(await screen.findByText("실패")).toHaveClass("sicost-tone-bad");
  expect(screen.getByText("대상 청구 확인 실패")).toBeInTheDocument();
  await userEvent.click(
    screen.getByRole("button", { name: "청구·입금에서 전달상태 확인" }),
  );
  await waitFor(() =>
    expect(target).toHaveBeenCalledWith({ screenId: "UI-13", linkKey: null }),
  );
  await waitFor(() =>
    expect(
      screen.queryByTestId("automation-runs-panel"),
    ).not.toBeInTheDocument(),
  );
});
it("keeps a failed read distinct from empty and retries without a write", async () => {
  failRuns = true;
  render(<ActivityCenter />);
  await userEvent.click(screen.getByRole("button", { name: "자동처리상태" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("activity-trace");
  expect(
    screen.queryByText("자동 처리 실행 기록이 없습니다."),
  ).not.toBeInTheDocument();
  failRuns = false;
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(
    await screen.findByText("자동 처리 실행 기록이 없습니다."),
  ).toBeInTheDocument();
  expect(calls.every((c) => c.method === "GET")).toBe(true);
});
it("does not fetch run state before opening and stops fetching when dismissed", async () => {
  render(<ActivityCenter />);
  await waitFor(() => expect(calls.length).toBeGreaterThan(0));
  expect(calls.some((c) => c.path.startsWith("/api/automation-runs"))).toBe(
    false,
  );
  await userEvent.click(screen.getByRole("button", { name: "자동처리상태" }));
  await screen.findByText("자동 처리 실행 기록이 없습니다.");
  await userEvent.click(screen.getByTestId("automation-runs-close"));
  expect(screen.queryByTestId("automation-runs-panel")).not.toBeInTheDocument();
});
it("shows exact notification time and server content without copying reference notifications", async () => {
  notifications.items = [
    {
      id: "137",
      typeCode: "DUN",
      content: "선택된 청구의 실제 미수 알림",
      sendMethodCode: "SYS",
      read: false,
      readAt: null,
      notifiedAt: at,
      link: null,
    },
  ];
  notifications.total = 1;
  render(<ActivityCenter />);
  await userEvent.click(screen.getByRole("button", { name: "알림" }));
  expect(
    await screen.findByText("선택된 청구의 실제 미수 알림"),
  ).toBeInTheDocument();
  expect(screen.getByTitle(at)).toHaveAttribute("dateTime", at);
});
it("polls running jobs by read only and cancels the timer on close", async () => {
  automation.items = [
    {
      jobCode: "DAILY_OPERATIONS",
      runDate: "2026-07-15",
      state: "RUNNING",
      lastStep: 2,
      startedAt: at,
      endedAt: null,
      errorMessage: null,
    },
  ];
  automation.total = 1;
  render(<ActivityCenter />);
  await userEvent.click(screen.getByRole("button", { name: "자동처리상태" }));
  await screen.findByText("실행 중");
  vi.useFakeTimers();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(2100);
  });
  expect(calls.every((c) => c.method === "GET")).toBe(true);
  vi.useRealTimers();
  await userEvent.click(screen.getByTestId("automation-runs-close"));
  const count = calls.filter((c) =>
    c.path.startsWith("/api/automation-runs"),
  ).length;
  vi.useFakeTimers();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(4000);
  });
  expect(
    calls.filter((c) => c.path.startsWith("/api/automation-runs")),
  ).toHaveLength(count);
});
