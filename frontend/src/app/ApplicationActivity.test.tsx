import { stubBusinessFetch } from "../shared/testSupport/businessFixtures";
import {
  render,
  screen,
  waitFor,
  within,
} from "../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ApplicationRoutes, applicationRoutes } from "./router";
import { dashboardFixture } from "../pages/dashboardops/dashboardFixture";

const calls: { path: string; method: string }[] = [];
let profileFailure = false;
const observedAt = "2026-07-15T09:00:00+09:00";
beforeEach(() => {
  calls.length = 0;
  profileFailure = false;
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 1280,
  });
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  );
  stubBusinessFetch(
    vi.fn(async (path: string, options?: RequestInit) => {
      const method = options?.method ?? "GET";
      calls.push({ path, method });
      const route = path.split("?")[0];
      let data: unknown;
      if (route === "/api/menus")
        data = {
          items: ["UI-16", "UI-13"].map((screenId, index) => ({
            menuId: screenId,
            parentId: null,
            name: index ? "청구·입금" : "대시보드",
            screenId,
            sortOrder: index,
            icon: null,
            pcOnly: false,
            children: [],
          })),
          diagnostics: [],
        };
      else if (route === "/api/current-user")
        data = {
          userId: "admin",
          name: "임시 관리자",
          roleIds: ["ADMIN"],
          developmentMode: true,
        };
      else if (route === "/api/me/preferences")
        data = {
          items: [
            {
              type: "RECENT",
              key: "*",
              value: [],
              stored: false,
              revision: null,
            },
            { type: "FAV", key: "*", value: [], stored: false, revision: null },
          ],
        };
      else if (method === "PUT" && route.startsWith("/api/me/preferences/"))
        data = {
          changed: true,
          preference: {
            type: "RECENT",
            key: "*",
            value: ["UI-16"],
            stored: true,
            revision: "saved",
          },
        };
      else if (route === "/api/period-context")
        data = {
          currentDate: "2026-07-15",
          currentMonth: "2026-07",
          cashBaseMonth: "2026-01",
          timezone: "Asia/Seoul",
          monthClose: { month: "2026-07", status: "WORK" },
        };
      else if (route === "/api/month-close") data = { items: [] };
      else if (route === "/api/dashboard") data = dashboardFixture();
      else if (route === "/api/notifications/unread-count")
        data = {
          unreadCount: 0,
          receivableCount: 0,
          currentMonthUnclosed: true,
          observedAt,
        };
      else if (route === "/api/notifications")
        data = {
          items: [],
          total: 0,
          page: 1,
          size: 20,
          hasMore: false,
          observedAt,
        };
      else if (route === "/api/automation-runs")
        data = {
          items: [],
          total: 0,
          page: 1,
          size: 50,
          hasMore: false,
          manualRunEnabled: false,
          observedAt,
        };
      else if (route === "/api/me/profile" && !profileFailure)
        data = {
          userId: "admin",
          userName: "실제 조회 사용자",
          activeRoles: [],
          parts: [],
          basisMonth: "2026-07",
          editGuidance: "이름·역할은 관리자에게 문의하세요.",
        };
      else if (route === "/api/receivables")
        data = {
          items: [],
          total: 0,
          tabCount: 0,
          page: 1,
          size: 50,
          asOfDate: "2026-07-15",
          cards: [],
        };
      else if (route === "/api/bills")
        data = {
          items: [],
          total: 0,
          page: 1,
          size: 50,
          totals: {
            supplyAmount: "0",
            vatAmount: "0",
            totalAmount: "0",
            receiptAmount: "0",
            unpaidAmount: "0",
          },
        };
      else if (route === "/api/bills/summary")
        data = {
          year: 2026,
          includeCarryover: true,
          billed: { amount: "0", count: 0, scope: "선택연도" },
          currentMonth: { amount: "0", count: 0, scope: "이번달" },
          unpaid: { amount: "0", count: 0, scope: "이월 포함" },
          lateCount: 0,
          lateScope: "이월 포함",
        };
      else if (route === "/api/projects/options") data = { items: [] };
      else
        return new Response(
          JSON.stringify({
            success: false,
            meta: {},
            code: "INTERNAL_ERROR",
            message: "원천 조회 실패",
            requestId: "activity-trace",
          }),
          { status: 500 },
        );
      return new Response(JSON.stringify({ success: true, meta: {}, data }));
    }),
  );
});
afterEach(() => {
  vi.unstubAllGlobals();
});
function open(path = "/UI-16", state?: unknown) {
  render(
    <MemoryRouter initialEntries={[{ pathname: path, state }]}>
      <ApplicationRoutes />
    </MemoryRouter>,
  );
}
it("registers the real dashboard once and keeps internal regions out of routes", async () => {
  open();
  expect(await screen.findByTestId("dashboard-metric-sales")).toBeVisible();
  for (const path of [
    "/UI-16",
    "/UI-13",
    "/UI-11",
    "/UI-17",
    "/UI-26",
    "/UI-24",
    "/UI-25",
  ])
    expect(
      applicationRoutes.filter((route) => route.path === path),
    ).toHaveLength(1);
  expect(
    applicationRoutes.some((route) => /UI-(16|13)-/.test(route.path ?? "")),
  ).toBe(false);
  expect(calls.some((call) => call.path.startsWith("/api/dashboard"))).toBe(
    true,
  );
});
it("opens profile from the persistent shell and restores header focus after Escape", async () => {
  open();
  const header = within(await screen.findByRole("banner"));
  const user = header.getByRole("button", { name: "임시 관리자" });
  expect(calls.some((call) => call.path === "/api/me/profile")).toBe(false);
  await userEvent.click(user);
  await userEvent.click(header.getByRole("button", { name: "내 정보" }));
  const dialog = await screen.findByRole("dialog", { name: "내 정보" });
  expect(await within(dialog).findByText("실제 조회 사용자")).toBeVisible();
  expect(within(dialog).queryByRole("textbox")).not.toBeInTheDocument();
  await userEvent.keyboard("{Escape}");
  await waitFor(() =>
    expect(
      screen.queryByRole("dialog", { name: "내 정보" }),
    ).not.toBeInTheDocument(),
  );
  expect(user).toHaveFocus();
  expect(calls.filter((call) => call.path === "/api/me/profile")).toHaveLength(
    1,
  );
});
it("keeps profile read errors visible and retries only on request", async () => {
  profileFailure = true;
  open();
  await userEvent.click(
    await screen.findByRole("button", { name: "임시 관리자" }),
  );
  await userEvent.click(screen.getByRole("button", { name: "내 정보" }));
  const dialog = await screen.findByRole("dialog", { name: "내 정보" });
  expect(await within(dialog).findByRole("alert")).toHaveTextContent(
    "activity-trace",
  );
  profileFailure = false;
  await userEvent.click(
    within(dialog).getByRole("button", { name: "다시 조회" }),
  );
  await within(dialog).findByText("실제 조회 사용자");
  expect(calls.filter((call) => call.path === "/api/me/profile")).toHaveLength(
    2,
  );
});
it("mounts notifications and automation in the header and opens the existing receivable tab", async () => {
  open();
  await userEvent.click(await screen.findByRole("button", { name: "알림" }));
  await screen.findByText("알림이 없습니다.");
  await userEvent.click(
    within(screen.getByTestId("notification-popover")).getByRole("button", {
      name: "자동처리상태",
    }),
  );
  await screen.findByText("자동 처리 실행 기록이 없습니다.");
  expect(screen.queryByTestId("automation-run-daily")).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("automation-runs-deliveries"));
  expect(await screen.findByRole("tab", { name: /미수 알림/ })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(screen.queryByTestId("automation-runs-panel")).not.toBeInTheDocument();
  expect(calls.some((call) => call.method === "POST")).toBe(false);
});
it("preserves dashboard receivable context without adding a bill identifier", async () => {
  open("/UI-13", {
    fromDashboard: true,
    section: "receivables",
    filter: "overdue",
  });
  expect(await screen.findByRole("tab", { name: /미수 알림/ })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(calls.some((call) => /\/api\/bills\/B-/.test(call.path))).toBe(false);
});
