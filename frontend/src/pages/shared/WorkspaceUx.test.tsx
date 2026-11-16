import * as dashboard from "../dashboardops/dashboardApi";
import { dashboardFixture } from "../dashboardops/dashboardFixture";
import type { ReactElement } from "react";
import { PreferencesProvider } from "../me/usePreferences";
import {
  act,
  fireEvent,
  render as renderUi,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SharedApplication } from "./SharedApplication";
import { ChangeHistoryPanel } from "./ChangeHistoryPanel";
import { HeaderActions } from "./HeaderActions";
import { AnalysisAssistantProvider } from "./AnalysisAssistantContext";
import * as analysis from "./analysisClient";
vi.mock("./analysisClient", async (original) => ({
  ...(await original<typeof import("./analysisClient")>()),
  listQuestions: vi.fn(async () => ({ items: [], nextCursor: null, hasMore: false })),
}));
import * as api from "./sharedApi";
import * as notices from "../notificationsops/notificationsApi";
vi.mock("./sharedApi");
vi.mock("../dashboardops/dashboardApi", async (original) => ({ ...(await original<typeof import("../dashboardops/dashboardApi")>()), getDashboard: vi.fn(async () => dashboardFixture()) }));
vi.mock("../notificationsops/notificationsApi", async (original) => ({
  ...(await original<typeof import("../notificationsops/notificationsApi")>()),
  getNotificationUnreadCount: vi.fn(),
  getNotifications: vi.fn(),
}));
const originalWidth = window.innerWidth;
const leaf = (id: string, name: string, pcOnly: boolean): api.MenuNode => ({
  menuId: id,
  parentId: "G",
  name,
  screenId: id,
  sortOrder: 1,
  icon: null,
  pcOnly,
  children: [],
});
const menus: api.MenuNode[] = [
  {
    menuId: "G",
    parentId: null,
    name: "시스템",
    screenId: null,
    sortOrder: 1,
    icon: "gear",
    pcOnly: false,
    children: [
      leaf("UI-16", "대시보드", false),
      leaf("UI-27", "공통코드", true),
    ],
  },
];
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(dashboard.getDashboard).mockResolvedValue(dashboardFixture());
  vi.mocked(notices.getNotificationUnreadCount).mockResolvedValue({
    unreadCount: 0,
    receivableCount: 0,
    currentMonthUnclosed: false,
    observedAt: "2026-07-15T09:00:00+09:00",
  });
  vi.mocked(notices.getNotifications).mockResolvedValue({
    items: [],
    page: 1,
    size: 20,
    total: 0,
    hasMore: false,
    observedAt: "2026-07-15T09:00:00+09:00",
  });
  vi.mocked(api.getMenus).mockResolvedValue({ items: menus, diagnostics: [] });
  vi.mocked(api.getCurrentUser).mockResolvedValue({
    userId: "admin",
    name: "임시 관리자",
    roleIds: ["ADMIN"],
    developmentMode: true,
  });
  vi.mocked(api.getSharedPreferences).mockResolvedValue({
    items: [
      {
        type: "FAV",
        key: "*",
        value: ["UI-27"],
        stored: true,
        revision: "fav",
      },
    ],
  });
  vi.mocked(api.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, stored: true, revision: "next" },
    }),
  );
  vi.mocked(api.getPeriodContext).mockResolvedValue({
    currentDate: "2026-11-10",
    currentMonth: "2026-11",
    cashBaseMonth: "2026-01",
    timezone: "Asia/Seoul",
    monthClose: { month: "2026-11", status: "작업중" },
  });
  vi.mocked(api.getMonthClose).mockResolvedValue({ items: [] });
  vi.mocked(api.getCommonCodeHistory).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
});
afterEach(() => {
  Object.defineProperty(window, "innerWidth", {
    value: originalWidth,
    configurable: true,
  });
});

describe("레퍼런스 공통 UX", () => {
  it("미수 메뉴는 전체 현재 미수건수·마감 메뉴는 현재월 미마감 경고를 표시한다", async () => {
    vi.mocked(api.getMenus).mockResolvedValue({
      items: [
        {
          ...menus[0],
          children: [
            ...menus[0].children,
            leaf("UI-13", "청구·입금", false),
            leaf("UI-14", "월 마감", false),
          ],
        },
      ],
      diagnostics: [],
    });
    vi.mocked(notices.getNotificationUnreadCount).mockResolvedValue({
      unreadCount: 101,
      receivableCount: 7,
      currentMonthUnclosed: true,
      observedAt: "2026-07-15T09:00:00+09:00",
    });
    render(
      <MemoryRouter initialEntries={["/UI-16"]}>
        <SharedApplication />
      </MemoryRouter>,
    );
    expect(
      await screen.findByTestId("receivable-menu-count"),
    ).toHaveTextContent("7");
    expect(screen.getByTestId("month-close-menu-warning")).toHaveTextContent(
      "!",
    );
    vi.mocked(notices.getNotificationUnreadCount).mockRejectedValueOnce(
      new Error("가벼운 조회 실패"),
    );
    act(() => window.dispatchEvent(new Event("focus")));
    await screen.findByRole("button", { name: "알림 수 다시 조회" });
    expect(screen.getByTestId("receivable-menu-count")).toHaveTextContent("7");
    expect(screen.getByTestId("month-close-menu-warning")).toHaveTextContent(
      "!",
    );
  });
  it("AI 대화창을 열고 알림은 실제 조회 결과를 표시한다", async () => {
    vi.mocked(analysis.listQuestions).mockResolvedValue({ items: [], nextCursor: null, hasMore: false });
    render(<MemoryRouter><AnalysisAssistantProvider><HeaderActions /></AnalysisAssistantProvider></MemoryRouter>);
    await userEvent.click(screen.getByRole("button", { name: "AI에게 묻기" }));
    expect(screen.getByRole("dialog", { name: "AI에게 묻기 대화창" })).toHaveTextContent("조회·해석 전용");
    await userEvent.click(screen.getByRole("button", { name: "알림" }));
    expect(await screen.findByText("알림이 없습니다.")).toBeInTheDocument();
    expect(notices.getNotifications).toHaveBeenCalledWith(1);
    await userEvent.keyboard("{Escape}");
    expect(
      screen.queryByRole("region", { name: "알림 목록" }),
    ).not.toBeInTheDocument();
    expect(api.getCommonCodeHistory).not.toHaveBeenCalled();
  });
  it("임시 ADMIN 메뉴는 DB 이름으로 표시하고 모바일의 PC 항목만 숨겼다가 복원한다", async () => {
    Object.defineProperty(window, "innerWidth", {
      value: 1280,
      configurable: true,
    });
    render(
      <MemoryRouter initialEntries={["/UI-16"]}>
        <SharedApplication />
      </MemoryRouter>,
    );
    await screen.findByRole("heading", { name: "대시보드" });
    const sidebar = within(
      screen.getByRole("complementary", { name: "주 메뉴" }),
    );
    expect(sidebar.getAllByRole("link", { name: /공통코드/ })).toHaveLength(2);
    act(() => {
      Object.defineProperty(window, "innerWidth", {
        value: 390,
        configurable: true,
      });
      window.dispatchEvent(new Event("resize"));
    });
    expect(
      sidebar.queryByRole("link", { name: /공통코드/ }),
    ).not.toBeInTheDocument();
    expect(api.putSharedPreference).not.toHaveBeenCalledWith(
      "FAV",
      expect.anything(),
      expect.anything(),
      expect.anything(),
    );
    act(() => {
      Object.defineProperty(window, "innerWidth", {
        value: 1280,
        configurable: true,
      });
      window.dispatchEvent(new Event("resize"));
    });
    expect(sidebar.getAllByRole("link", { name: /공통코드/ })).toHaveLength(2);
    expect(
      screen.getByRole("button", { name: "임시 관리자" }),
    ).toBeInTheDocument();
  });
  it("로고는 실제 대시보드 경로로 이동한다", async () => {
    render(
      <MemoryRouter initialEntries={["/UI-27"]}>
        <SharedApplication codes={<p>코드 편집 영역</p>} />
      </MemoryRouter>,
    );
    await screen.findByRole("heading", { name: "공통코드" });
    await userEvent.click(
      screen.getByRole("link", { name: /프로젝트·원가 관리/ }),
    );
    await screen.findByRole("heading", { name: "대시보드" });
  });
  it("변경 이력 필터는 조회 버튼 전까지 API 조건을 바꾸지 않는다", async () => {
    render(
      <ChangeHistoryPanel groupOptions={[{ groupId: "JOB", name: "직무" }]} />,
    );
    await screen.findByText("조회된 변경 이력이 없습니다.");
    fireEvent.change(screen.getByRole("combobox", { name: "이력 대상" }), {
      target: { value: "CODE" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "이력 그룹" }), {
      target: { value: "JOB" },
    });
    expect(api.getCommonCodeHistory).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole("button", { name: "조회" }));
    await waitFor(() =>
      expect(api.getCommonCodeHistory).toHaveBeenLastCalledWith({
        targetType: "CODE",
        groupId: "JOB",
        page: 1,
        size: 50,
      }),
    );
    expect(
      screen.queryByRole("button", { name: "삭제" }),
    ).not.toBeInTheDocument();
  });
  it("이력 조회 오류는 빈 결과로 표시하지 않고 재시도한다", async () => {
    vi.mocked(api.getCommonCodeHistory).mockRejectedValueOnce(
      new Error("이력 연결 실패"),
    );
    render(<ChangeHistoryPanel />);
    await screen.findByRole("alert");
    expect(
      screen.queryByText("조회된 변경 이력이 없습니다."),
    ).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByText("조회된 변경 이력이 없습니다.");
  });
});

const render = (ui: ReactElement) => renderUi(<PreferencesProvider api={{
  getPreferences: () => api.getSharedPreferences() as ReturnType<import("../me/preferencesApi").PreferenceApi["getPreferences"]>,
  putPreference: async (...args) => { const saved = await api.putSharedPreference(...args); return { ...saved, preference: saved.preference as import("../me/preferencesApi").Preference }; },
  deletePreference: api.deleteSharedPreference,
}}>{ui}</PreferencesProvider>);
