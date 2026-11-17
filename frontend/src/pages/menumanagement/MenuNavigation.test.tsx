import {
  act,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { SharedApplication } from "../shared/SharedApplication";
import * as shared from "../shared/sharedApi";
import * as managed from "./menuManagementApi";
vi.mock("../shared/sharedApi");
vi.mock("./menuManagementApi");
vi.mock("../shared/HeaderActions", () => ({ HeaderActions: () => null }));
vi.mock("../meops/ProfileDialog", () => ({ ProfileDialog: () => null }));
let tree: managed.ManagedMenuTree;
function node(row: managed.ManagedMenu): shared.MenuNode {
  return {
    menuId: row.menuId,
    parentId: row.parentId,
    name: row.name,
    screenId: row.screenId,
    sortOrder: row.sortOrder,
    icon: row.icon,
    pcOnly: row.pcOnly,
    children: [],
  };
}
function Location() {
  return <output data-testid="current-path">{useLocation().pathname}</output>;
}
beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 1280,
  });
  const root: managed.ManagedMenu = {
    menuId: "G",
    parentId: null,
    name: "시스템",
    level: 1,
    screenId: null,
    sortOrder: 1,
    use: true,
    pcOnly: false,
    locked: false,
    icon: "folder",
  };
  tree = {
    items: [
      root,
      {
        ...root,
        menuId: "D",
        parentId: "G",
        name: "대시보드",
        level: 2,
        screenId: "UI-16",
        sortOrder: 1,
      },
      {
        ...root,
        menuId: "M",
        parentId: "G",
        name: "메뉴 편집",
        level: 2,
        screenId: "UI-25",
        sortOrder: 2,
        pcOnly: true,
      },
    ],
    revision: "r",
    developmentMode: true,
    accessEnforcement: false,
  };
  vi.mocked(managed.getMenuManagement).mockImplementation(async () =>
    structuredClone(tree),
  );
  vi.mocked(shared.getMenus).mockImplementation(async () => ({
    items: [
      {
        ...node(tree.items[0]),
        children: tree.items.filter((r) => r.level === 2 && r.use).map(node),
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
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({
    items: [
      { type: "FAV", key: "*", value: ["UI-25"], revision: "p", stored: true },
      {
        type: "RECENT",
        key: "*",
        value: ["UI-25"],
        revision: "p",
        stored: true,
      },
    ],
  });
  vi.mocked(shared.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, revision: "saved", stored: true },
    }),
  );
});
function open(path = "/UI-25", dirty = false) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <SharedApplication
        screens={{
          "UI-16": <p>대시보드 본문</p>,
          "UI-25": <p>메뉴 편집 본문</p>,
        }}
        isDirty={() => dirty}
      />
      <Location />
    </MemoryRouter>,
  );
}
it("767px는 저장 PC설정으로 직접주소 안내·768px는 진입", async () => {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 767,
  });
  open();
  await screen.findByText("PC에서 이용해 주세요. PC전용 메뉴입니다.");
  expect(screen.getByTestId("current-path")).toHaveTextContent("/UI-25");
  expect(screen.queryByText("메뉴 편집 본문")).not.toBeInTheDocument();
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 768,
  });
  act(() => {
    window.dispatchEvent(new Event("resize"));
  });
  expect(await screen.findByText("메뉴 편집 본문")).toBeVisible();
});
it("저장 pcOnly=false면 UI25도 모바일 진입·고정6개 판정 없음", async () => {
  tree.items[2].pcOnly = false;
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 767,
  });
  open();
  expect(await screen.findByText("메뉴 편집 본문")).toBeVisible();
});
it("중지 직접주소는 대시보드로 우회하지 않고 중지 안내·선호 저장0", async () => {
  tree.items[2].use = false;
  open();
  await screen.findByText("사용 중지된 메뉴입니다.");
  expect(screen.getByTestId("current-path")).toHaveTextContent("/UI-25");
  expect(shared.putSharedPreference).not.toHaveBeenCalled();
});
it("설정갱신은 명칭을 탐색·열린탭·즐겨찾기에 반영", async () => {
  open();
  await screen.findByText("메뉴 편집 본문");
  tree.items[2].name = "변경된 이름";
  act(() => {
    window.dispatchEvent(new Event("sicost:configuration-committed"));
  });
  await waitFor(() =>
    expect(screen.getAllByText("변경된 이름").length).toBeGreaterThanOrEqual(3),
  );
});
it("열린 dirty 메뉴 중지는 나가기 확인전 입력보존·계속작성후도 저장0", async () => {
  open("/UI-25", true);
  await screen.findByText("메뉴 편집 본문");
  vi.mocked(shared.putSharedPreference).mockClear();
  tree.items[2].use = false;
  act(() => {
    window.dispatchEvent(new Event("sicost:configuration-committed"));
  });
  const dialog = await screen.findByRole("dialog", {
    name: "저장하지 않은 내용이 있습니다.",
  });
  expect(screen.getByText("메뉴 편집 본문")).toBeVisible();
  expect(screen.getByTestId("current-path")).toHaveTextContent("/UI-25");
  await userEvent.click(
    within(dialog).getByRole("button", { name: "버리기/나가기" }),
  );
  await waitFor(() =>
    expect(screen.getByTestId("current-path")).toHaveTextContent("/UI-16"),
  );
  expect(managed.putMenuManagement).not.toHaveBeenCalled();
  for (const call of vi.mocked(shared.putSharedPreference).mock.calls)
    if (call[0] === "RECENT") expect(call[2]).toContain("UI-25");
});
it("메뉴 새조회 실패에도 기존 편집화면은 제거하지 않는다", async () => {
  open("/UI-25", true);
  await screen.findByText("메뉴 편집 본문");
  vi.mocked(shared.getMenus).mockRejectedValueOnce(
    new Error("탐색 재조회 실패"),
  );
  act(() => {
    window.dispatchEvent(new Event("sicost:configuration-committed"));
  });
  await screen.findByText("탐색 재조회 실패");
  expect(screen.getByText("메뉴 편집 본문")).toBeVisible();
});
