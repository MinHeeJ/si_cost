import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { AuthenticationBoundary } from "./AuthenticationBoundary";
import { SharedApplication } from "../shared/SharedApplication";
import * as auth from "./authenticationApi";
import * as shared from "../shared/sharedApi";

vi.mock("./authenticationApi", () => ({
  session: vi.fn(),
  capabilities: vi.fn(),
  authenticate: vi.fn(),
  clearAuthentication: vi.fn(),
  authenticationChannel: null,
}));
vi.mock("../shared/sharedApi");
vi.mock("../shared/HeaderActions", () => ({ HeaderActions: () => null }));
vi.mock("../meops/ProfileDialog", () => ({ ProfileDialog: () => null }));
vi.mock("./SessionIndicator", () => ({ SessionIndicator: () => null }));
const grants: auth.Capabilities = {
  screens: {
    "UI-16": ["INQ"],
    "UI-23": ["INQ"],
    "UI-27": ["INQ"],
    "UI-33": ["INQ"],
  },
  scope: { all: true, partIds: [], projectIds: [] },
  sensitive: false,
  pcOnlyScreens: ["UI-23", "UI-27"],
};
let menuItems: shared.MenuNode[];
let mediaChange: (() => void) | undefined;
function width(value: number) {
  Object.defineProperty(window, "innerWidth", { configurable: true, value });
  act(() => {
    mediaChange?.();
    window.dispatchEvent(new Event("resize"));
  });
}
function Location() {
  return (
    <output data-testid="pc-access-route">{useLocation().pathname}</output>
  );
}
function open(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthenticationBoundary>
        <SharedApplication
          screens={{
            "UI-16": <p data-testid="dashboard-body">대시보드 본문</p>,
            "UI-23": <p data-testid="users-body">사용자 본문</p>,
            "UI-27": <p data-testid="codes-body">공통코드 본문</p>,
            "UI-33": <p data-testid="first-body">첫 화면 본문</p>,
          }}
        />
      </AuthenticationBoundary>
      <Location />
    </MemoryRouter>,
  );
}
beforeEach(() => {
  vi.resetAllMocks();
  mediaChange = undefined;
  width(390);
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation(() => ({
      get matches() {
        return window.innerWidth <= 767;
      },
      addEventListener: (_: string, listener: () => void) => {
        mediaChange = listener;
      },
      removeEventListener: () => {},
    })),
  );
  vi.mocked(auth.session).mockResolvedValue({
    nextStep: "AUTHENTICATED",
    serverTime: "2026-11-19T09:00:00Z",
  });
  vi.mocked(auth.capabilities).mockResolvedValue(grants);
  menuItems = [
    ["UI-16", "대시보드"],
    ["UI-23", "사용자"],
    ["UI-27", "공통코드"],
    ["UI-33", "후보자"],
  ].map(([screenId, name]) => ({
    menuId: screenId,
    parentId: null,
    screenId,
    name,
    pcOnly: ["UI-23", "UI-27"].includes(screenId),
    sortOrder: 1,
    icon: null,
    children: [],
  }));
  vi.mocked(shared.getMenus).mockImplementation(async () => ({
    items: menuItems,
    diagnostics: [],
  }));
  vi.mocked(shared.getCurrentUser).mockResolvedValue({
    userId: "test-viewer",
    name: "시험 사용자",
    roleIds: [],
    developmentMode: false,
  });
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({
    items: [
      {
        type: "FAV",
        key: "*",
        value: ["UI-23", "UI-27"],
        revision: "1",
        stored: true,
      },
      {
        type: "RECENT",
        key: "*",
        value: ["UI-23", "UI-27"],
        revision: "1",
        stored: true,
      },
    ],
  });
  vi.mocked(shared.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, revision: "2", stored: true },
    }),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it.each([
  ["UI-23", "사용자", "users-body"],
  ["UI-27", "공통코드", "codes-body"],
])(
  "인증 경계에서도 %s 안내를 유지하고 클릭으로만 이동한다",
  async (id, name, body) => {
    open(`/${id}`);
    const notice = await screen.findByTestId("pc-only-notice");
    expect(notice).toHaveTextContent(`「${name}」`);
    expect(screen.getByTestId("pc-access-route")).toHaveTextContent(`/${id}`);
    expect(screen.queryByTestId(body)).not.toBeInTheDocument();
    expect(shared.putSharedPreference).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    width(767);
    expect(screen.getByTestId("pc-only-notice")).toBeVisible();
    width(768);
    const desktopBody = await screen.findByTestId(body);
    expect(desktopBody).toBeVisible();
    width(390);
    // Direct mobile entry never mounts the body; shrinking an existing desktop
    // editor only hides it, preserving the shell's existing unsaved form state.
    expect(screen.getByTestId(body)).toBe(desktopBody);
    expect(desktopBody).not.toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "대시보드로" }));
    await waitFor(() =>
      expect(screen.getByTestId("pc-access-route")).toHaveTextContent("/UI-16"),
    );
    expect(screen.queryByTestId("pc-only-notice")).not.toBeInTheDocument();
  },
);
it("대시보드 권한이 없으면 기기에서 사용 가능한 기존 첫 화면으로 간다", async () => {
  vi.mocked(auth.capabilities).mockResolvedValue({
    ...grants,
    screens: { "UI-23": ["INQ"], "UI-33": ["INQ"] },
  });
  menuItems = menuItems.filter((m) => ["UI-23", "UI-33"].includes(m.screenId!));
  open("/UI-23");
  fireEvent.click(await screen.findByTestId("pc-only-dashboard-button"));
  expect(await screen.findByTestId("first-body")).toBeVisible();
  expect(screen.getByTestId("pc-access-route")).toHaveTextContent("/UI-33");
});
it("메뉴의 현재 pcOnly=false 설정은 화면 번호와 무관하게 적용한다", async () => {
  menuItems.find((m) => m.screenId === "UI-27")!.pcOnly = false;
  open("/UI-27");
  expect(await screen.findByTestId("codes-body")).toBeVisible();
  expect(screen.queryByTestId("pc-only-notice")).not.toBeInTheDocument();
});
it("PC 화면만 허용되어도 요청한 화면명 안내를 먼저 보이고 클릭 뒤 기존 PC 필요 흐름으로 간다", async () => {
  vi.mocked(auth.capabilities).mockResolvedValue({
    ...grants,
    screens: { "UI-27": ["INQ"] },
  });
  menuItems = menuItems.filter((m) => m.screenId === "UI-27");
  open("/UI-27");
  expect(await screen.findByTestId("pc-only-notice")).toHaveTextContent(
    "「공통코드」",
  );
  expect(screen.getByTestId("pc-access-route")).toHaveTextContent("/UI-27");
  fireEvent.click(screen.getByTestId("pc-only-dashboard-button"));
  expect(await screen.findByTestId("authentication-pc-required")).toBeVisible();
  expect(screen.queryByTestId("codes-body")).not.toBeInTheDocument();
});
it("모바일 사용 제한과 실제 권한 없음을 혼동하지 않는다", async () => {
  vi.mocked(auth.capabilities).mockResolvedValue({
    ...grants,
    screens: { "UI-16": ["INQ"] },
  });
  open("/UI-27");
  expect(
    await screen.findByText("이 화면을 볼 권한이 없습니다."),
  ).toBeVisible();
  expect(screen.getByTestId("pc-access-route")).toHaveTextContent("/UI-16");
  expect(screen.queryByTestId("pc-only-notice")).not.toBeInTheDocument();
  expect(screen.queryByTestId("codes-body")).not.toBeInTheDocument();
});
it("미인증 직접 접근은 기존 로그인 화면을 유지한다", async () => {
  vi.mocked(auth.session).mockResolvedValue({
    nextStep: "LOGIN",
    serverTime: "2026-11-19T09:00:00Z",
  });
  open("/UI-23");
  expect(await screen.findByTestId("authentication-page")).toBeVisible();
  expect(shared.getMenus).not.toHaveBeenCalled();
  expect(screen.queryByTestId("pc-only-notice")).not.toBeInTheDocument();
});
