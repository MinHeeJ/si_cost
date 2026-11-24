import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { ApplicationRoutes } from "./router";
import * as shared from "../pages/shared/sharedApi";
import * as prefs from "../pages/me/preferencesApi";
import * as roles from "../pages/roles/rolesApi";
import * as menus from "../pages/menumanagement/menuManagementApi";
import * as settings from "../pages/currentuserops/permissionSettingsApi";

vi.mock("../pages/shared/sharedApi");
vi.mock("../pages/roles/rolesApi", async (original) => ({
  ...(await original<typeof import("../pages/roles/rolesApi")>()),
  getRoles: vi.fn(),
  getRole: vi.fn(),
  getRoleMenus: vi.fn(),
  putRole: vi.fn(),
}));
vi.mock("../pages/menumanagement/menuManagementApi", async (original) => ({
  ...(await original<
    typeof import("../pages/menumanagement/menuManagementApi")
  >()),
  getMenuManagement: vi.fn(),
  putMenuManagement: vi.fn(),
}));
vi.mock("../pages/currentuserops/permissionSettingsApi");
vi.mock("../pages/shared/HeaderActions", () => ({ HeaderActions: () => null }));
vi.mock("../pages/meops/ProfileDialog", () => ({ ProfileDialog: () => null }));
vi.mock("../pages/me/preferencesApi", async (original) => ({
  ...(await original<typeof import("../pages/me/preferencesApi")>()),
  getPreferences: vi.fn(),
  putPreference: vi.fn(),
  deletePreference: vi.fn(),
  preferencesApi: {
    getPreferences: () => prefs.getPreferences(),
    putPreference: (...args: Parameters<typeof prefs.putPreference>) =>
      prefs.putPreference(...args),
    deletePreference: (...args: Parameters<typeof prefs.deletePreference>) =>
      prefs.deletePreference(...args),
  },
}));
const role: roles.ManagedRoleDetail = {
  roleId: "ADMIN",
  name: "관리자",
  dataScope: "ALL",
  sensitiveView: false,
  systemRole: true,
  use: true,
  sortOrder: 1,
  revision: "role-r",
  settingsRevision: "settings-r",
  developmentMode: true,
  accessEnforcement: false,
  menuPermissions: [],
};
let tree: menus.ManagedMenuTree;
beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 1280,
  });
  const root: menus.ManagedMenu = {
    menuId: "SYSTEM",
    parentId: null,
    name: "시스템",
    level: 1,
    screenId: null,
    sortOrder: 1,
    use: true,
    pcOnly: false,
    locked: false,
    icon: null,
  };
  tree = {
    items: [
      root,
      ...["UI-24", "UI-25"].map((id, index) => ({
        ...root,
        menuId: id,
        parentId: root.menuId,
        name: id === "UI-24" ? "권한" : "메뉴",
        level: 2 as const,
        screenId: id,
        sortOrder: index + 1,
      })),
    ],
    revision: "menu-r",
    developmentMode: true,
    accessEnforcement: false,
  };
  vi.mocked(menus.getMenuManagement).mockImplementation(async () =>
    structuredClone(tree),
  );
  vi.mocked(shared.getMenus).mockImplementation(async () => ({
    items: [
      {
        ...tree.items[0],
        children: tree.items.slice(1).map((row) => ({ ...row, children: [] })),
      },
    ],
    diagnostics: [],
  }));
  vi.mocked(shared.getCurrentUser).mockResolvedValue({
    userId: "admin",
    name: "DB 관리자",
    roleIds: ["ADMIN"],
    developmentMode: true,
  });
  vi.mocked(prefs.getPreferences).mockResolvedValue({ items: [] });
  vi.mocked(prefs.putPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, revision: "saved", stored: true },
    }),
  );
  vi.mocked(roles.getRoles).mockResolvedValue({
    items: [role],
    developmentMode: true,
    accessEnforcement: false,
    settingsRevision: "settings-r",
  });
  vi.mocked(roles.getRole).mockResolvedValue(role);
  vi.mocked(roles.getRoleMenus).mockResolvedValue({ items: tree.items });
  vi.mocked(settings.getCurrentUserPermissionSettings).mockResolvedValue({
    userId: "admin",
    roleIds: ["ADMIN"],
    menuPermissions: [],
    sensitiveView: false,
    rowScope: { all: true, partIds: [], projectIds: [] },
    assignedParts: [],
    assignedProjects: [],
    developmentMode: true,
    accessEnforcement: false,
    settingsRevision: "settings-r",
  });
});
afterEach(() => vi.restoreAllMocks());
function open(path = "/UI-24") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <ApplicationRoutes />
    </MemoryRouter>,
  );
}
async function openCurrentPermissions() {
  await userEvent.click(await screen.findByRole("button", { name: "내 저장 권한" }));
  return screen.getByRole("dialog", { name: "내 저장 권한" });
}
it("UI-24 registers the real role editor and current-user union without enforcing saved permissions", async () => {
  open();
  expect(
    await screen.findByRole("checkbox", { name: "민감항목 보기" }),
  ).toBeEnabled();
  expect(settings.getCurrentUserPermissionSettings).not.toHaveBeenCalled();
  const dialog = await openCurrentPermissions();
  expect(
    await within(dialog).findByText("저장된 메뉴 권한이 없습니다."),
  ).toBeVisible();
  expect(screen.getByTestId("roles-screen")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-24",
  );
  expect(
    screen.queryByText("이 화면은 다음 단계에서 만듭니다."),
  ).not.toBeInTheDocument();
});
it("UI-24 passes dirty state to shell navigation and tab close without saving", async () => {
  open();
  await userEvent.click(
    await screen.findByRole("checkbox", { name: "민감항목 보기" }),
  );
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  await userEvent.click(screen.getByRole("link", { name: "메뉴" }));
  expect(confirm).toHaveBeenCalledWith("저장하지 않은 변경을 버릴까요?");
  expect(screen.getByRole("checkbox", { name: "민감항목 보기" })).toBeChecked();
  await userEvent.click(screen.getByRole("button", { name: "권한 탭 닫기" }));
  expect(confirm).toHaveBeenCalledTimes(2);
  expect(roles.putRole).not.toHaveBeenCalled();
});
it.each([
  "sicost:roles-committed",
  "sicost:business-users-committed",
  "sicost:menus-committed",
  "sicost:permission-settings-committed",
  "sicost:role-settings-committed",
])(
  "%s refreshes shell and union without replacing the role draft",
  async (event) => {
    open();
    await userEvent.click(
      await screen.findByRole("checkbox", { name: "민감항목 보기" }),
    );
    const dialog = await openCurrentPermissions();
    await waitFor(() =>
      expect(settings.getCurrentUserPermissionSettings).toHaveBeenCalledTimes(
        1,
      ),
    );
    const reads = vi.mocked(shared.getMenus).mock.calls.length;
    act(() => {
      window.dispatchEvent(new Event(event));
    });
    await waitFor(() =>
      expect(settings.getCurrentUserPermissionSettings).toHaveBeenCalledTimes(
        2,
      ),
    );
    expect(vi.mocked(shared.getMenus).mock.calls.length).toBeGreaterThan(reads);
    await userEvent.click(within(dialog).getByRole("button", { name: "닫기" }));
    expect(
      screen.getByRole("checkbox", { name: "민감항목 보기" }),
    ).toBeChecked();
    expect(roles.putRole).not.toHaveBeenCalled();
  },
);
it("UI-25 registers the existing menu editor and its unsaved departure dialog", async () => {
  open("/UI-25");
  const input = await screen.findByRole("textbox", { name: "UI-24 메뉴이름" });
  fireEvent.change(input, { target: { value: "편집 중 권한" } });
  await userEvent.click(screen.getByRole("link", { name: "권한" }));
  const dialog = await screen.findByRole("dialog", {
    name: "저장하지 않은 내용이 있습니다.",
  });
  await userEvent.click(
    within(dialog).getByRole("button", { name: "계속 작성" }),
  );
  expect(input).toHaveValue("편집 중 권한");
  expect(menus.putMenuManagement).not.toHaveBeenCalled();
  expect(screen.getByTestId("menus-screen")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-25",
  );
});
it("registered UI-24 preserves its own read error while the union remains usable", async () => {
  vi.mocked(roles.getRoles).mockRejectedValue(new Error("역할 조회 실패"));
  open();
  expect(await screen.findByText("역할 조회 실패")).toBeVisible();
  const dialog = await openCurrentPermissions();
  expect(
    await within(dialog).findByText("저장된 메뉴 권한이 없습니다."),
  ).toBeVisible();
});
