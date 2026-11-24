import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { MenusScreen } from "./MenusScreen";
import * as api from "./menuManagementApi";
vi.mock("./menuManagementApi", async (importOriginal) => ({
  ...(await importOriginal<typeof api>()),
  getMenuManagement: vi.fn(),
  putMenuManagement: vi.fn(),
  getMenuManagementScreens: vi.fn(),
  createManagedMenu: vi.fn(),
  getMenuHistory: vi.fn(),
}));
const root = (id: string, name: string, order: number): api.ManagedMenu => ({
  menuId: id,
  parentId: null,
  name,
  level: 1,
  screenId: null,
  sortOrder: order,
  use: true,
  pcOnly: false,
  locked: false,
  icon: "folder",
});
const child = (
  id: string,
  parent: string,
  name: string,
  screenId: string,
  order = 1,
): api.ManagedMenu => ({
  ...root(id, name, order),
  parentId: parent,
  level: 2,
  screenId,
  icon: null,
});
let tree: api.ManagedMenuTree;
beforeEach(() => {
  vi.resetAllMocks();
  tree = {
    items: [
      root("G1", "업무", 1),
      child("M1", "G1", "프로젝트", "UI-01"),
      { ...child("M2", "G1", "중지 화면", "UI-02", 2), use: false },
      root("G2", "시스템", 2),
      { ...child("M3", "G2", "메뉴", "UI-25"), locked: true, pcOnly: true },
    ],
    revision: "revision",
    developmentMode: true,
    accessEnforcement: false,
  };
  vi.mocked(api.getMenuManagement).mockImplementation(async () =>
    structuredClone(tree),
  );
  vi.mocked(api.putMenuManagement).mockImplementation(async (items) => {
    tree = {
      ...tree,
      items: tree.items.map((row) => ({
        ...row,
        ...items.find((item) => item.menuId === row.menuId),
      })),
      revision: "next",
    };
    return { value: structuredClone(tree), revision: "next", changed: true };
  });
  vi.mocked(api.getMenuManagementScreens).mockResolvedValue({
    items: [],
    revision: "revision",
  });
  vi.mocked(api.getMenuHistory).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
});
async function open() {
  render(<MenusScreen />);
  await screen.findByLabelText("M1 메뉴이름");
}
it("중지 메뉴를 포함해 표시하고 잠금 상태에서도 PC 전용 설정을 편집할 수 있다", async () => {
  await open();
  expect(screen.getByRole("table", { name: "메뉴 설정" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
  expect(screen.getByLabelText("M2 사용")).not.toBeChecked();
  expect(screen.queryByLabelText("G1 사용")).not.toBeInTheDocument();
  expect(screen.getByLabelText("M3 사용")).toBeDisabled();
  expect(screen.getByLabelText("M3 그룹")).toBeDisabled();
  expect(screen.getByLabelText("M3 PC전용")).toBeEnabled();
  expect(screen.getByText("UI-25", { selector: "td" })).toBeInTheDocument();
  expect(api.putMenuManagement).not.toHaveBeenCalled();
});
it("키보드 위아래와 그룹이동은 저장전 호출0·취소 전체복원", async () => {
  await open();
  await userEvent.click(screen.getByRole("button", { name: "M2 위" }));
  fireEvent.change(screen.getByLabelText("M1 그룹"), {
    target: { value: "G2" },
  });
  expect(api.putMenuManagement).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(screen.getByLabelText("M1 그룹")).toHaveValue("G1");
  expect(screen.getByRole("button", { name: "M1 위" })).toBeDisabled();
  fireEvent.keyDown(
    screen.getByRole("button", { name: "프로젝트 이동 손잡이" }),
    { key: "ArrowDown" },
  );
  expect(screen.getByRole("button", { name: "M1 아래" })).toBeDisabled();
  expect(api.putMenuManagement).not.toHaveBeenCalled();
});
it("드래그는 동일레벨만 로컬 이동", async () => {
  await open();
  fireEvent.dragStart(
    screen.getByRole("button", { name: "중지 화면 이동 손잡이" }),
  );
  fireEvent.drop(screen.getByTestId("menu-row-m1"));
  expect(screen.getByRole("button", { name: "M2 위" })).toBeDisabled();
  expect(api.putMenuManagement).not.toHaveBeenCalled();
});
it("전체집합 저장시 immutable echo·루트 플래그/locked/icon 제외 후 조회와 공통갱신", async () => {
  await open();
  fireEvent.change(screen.getByLabelText("M1 메뉴이름"), {
    target: { value: " 새 이름 " },
  });
  const listener = vi.fn();
  window.addEventListener("sicost:configuration-committed", listener);
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("저장했습니다.");
  const [items, revision] = vi.mocked(api.putMenuManagement).mock.calls[0];
  expect(items).toHaveLength(5);
  expect(revision).toBe("revision");
  expect(items[0]).toEqual({
    menuId: "G1",
    name: "업무",
    level: 1,
    parentId: null,
    screenId: null,
    sortOrder: 1,
  });
  expect(items.find((row) => row.menuId === "M1")).toMatchObject({
    name: "새 이름",
    level: 2,
    screenId: "UI-01",
  });
  expect(items.every((row) => !("locked" in row) && !("icon" in row))).toBe(
    true,
  );
  expect(listener).toHaveBeenCalledTimes(1);
  window.removeEventListener("sicost:configuration-committed", listener);
});
it("blank/초과이름은 필드오류이고 저장0", async () => {
  await open();
  fireEvent.change(screen.getByLabelText("M1 메뉴이름"), {
    target: { value: " " },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findByText(/메뉴 이름은 공백 제거/)).toBeInTheDocument();
  expect(api.putMenuManagement).not.toHaveBeenCalled();
});
it("서버 fieldMessages를 행에 표시하고 stale·입력은 보존", async () => {
  await open();
  vi.mocked(api.putMenuManagement).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "최신값을 다시 조회해 주세요.",
      requestId: "r",
      details: {
        violations: [{ field: "items.1.name", message: "중복 이름" }],
      },
    }),
  );
  fireEvent.change(screen.getByLabelText("M1 메뉴이름"), {
    target: { value: "입력 유지" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findByText("중복 이름")).toBeInTheDocument();
  expect(screen.getByLabelText("M1 메뉴이름")).toHaveValue("입력 유지");
  expect(api.putMenuManagement).toHaveBeenCalledTimes(1);
});
it("저장성공 후 조회실패는 재조회만 제공·쓰기를 재실행하지 않는다", async () => {
  await open();
  vi.mocked(api.getMenuManagement).mockRejectedValueOnce(
    new Error("통신 실패"),
  );
  fireEvent.change(screen.getByLabelText("M1 메뉴이름"), {
    target: { value: "수정" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findByText(/저장은 완료됐지만/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "저장 후 재조회" }));
  await waitFor(() => expect(api.getMenuManagement).toHaveBeenCalledTimes(3));
  expect(api.putMenuManagement).toHaveBeenCalledTimes(1);
});
it("미저장 등록 진입 계속작성은 모달 없음·버리기는 저장0으로 등록", async () => {
  await open();
  fireEvent.change(screen.getByLabelText("M1 메뉴이름"), {
    target: { value: "초안" },
  });
  await userEvent.click(screen.getByRole("button", { name: "메뉴 등록" }));
  expect(
    screen.queryByRole("dialog", { name: "메뉴 등록" }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("M1 메뉴이름")).toHaveValue("초안");
  await userEvent.click(screen.getByRole("button", { name: "메뉴 등록" }));
  await userEvent.click(screen.getByRole("button", { name: "버리기/나가기" }));
  await screen.findByRole("dialog", { name: "메뉴 등록" });
  expect(screen.getByLabelText("M1 메뉴이름")).toHaveValue("프로젝트");
  expect(api.putMenuManagement).not.toHaveBeenCalled();
});
it("저장후등록은 저장과 재조회 성공 뒤에만 열린다", async () => {
  await open();
  fireEvent.change(screen.getByLabelText("M1 메뉴이름"), {
    target: { value: "초안" },
  });
  await userEvent.click(screen.getByRole("button", { name: "메뉴 등록" }));
  await userEvent.click(screen.getByRole("button", { name: "저장 후 등록" }));
  await screen.findByRole("dialog", { name: "메뉴 등록" });
  expect(api.putMenuManagement).toHaveBeenCalledTimes(1);
});
it("신규등록은 root만 전송·빈 화면목록은 서브등록불가", async () => {
  await open();
  vi.mocked(api.createManagedMenu).mockResolvedValue({
    value: tree,
    revision: tree.revision,
    changed: true,
  });
  await userEvent.click(screen.getByRole("button", { name: "메뉴 등록" }));
  const dialog = await screen.findByRole("dialog", { name: "메뉴 등록" });
  fireEvent.change(within(dialog).getByLabelText("레벨"), {
    target: { value: "2" },
  });
  await within(dialog).findByText("메뉴에 없는 개발된 화면이 없습니다");
  expect(within(dialog).getByRole("button", { name: "등록" })).toBeDisabled();
  fireEvent.change(within(dialog).getByLabelText("레벨"), {
    target: { value: "1" },
  });
  fireEvent.change(within(dialog).getByLabelText("이름"), {
    target: { value: " 신규 " },
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "등록" }));
  await waitFor(() =>
    expect(api.createManagedMenu).toHaveBeenCalledWith({
      level: 1,
      name: "신규",
      expectedRevision: "revision",
    }),
  );
});
it("dirty 화면이탈 이벤트는 확인 전 이동·자동쓰기0", async () => {
  await open();
  fireEvent.change(screen.getByLabelText("M1 메뉴이름"), {
    target: { value: "초안" },
  });
  const proceed = vi.fn();
  const event = new CustomEvent("sicost:screen-navigation-request", {
    cancelable: true,
    detail: { screenId: "UI-25", label: "다른 화면", proceed },
  });
  act(() => {
    window.dispatchEvent(event);
  });
  expect(event.defaultPrevented).toBe(true);
  expect(proceed).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "버리기/나가기" }));
  expect(proceed).toHaveBeenCalledTimes(1);
  expect(api.putMenuManagement).not.toHaveBeenCalled();
});
it("접기에도 전체초안 보존·이력은 선택 menuId만 조회·닫아도 초안 유지", async () => {
  await open();
  fireEvent.change(screen.getByLabelText("M1 메뉴이름"), {
    target: { value: "초안" },
  });
  await userEvent.click(
    within(screen.getByTestId("menu-row-m1")).getByRole("button", {
      name: "변경 이력",
    }),
  );
  await screen.findByText("변경 이력이 없습니다.");
  expect(api.getMenuHistory).toHaveBeenCalledWith("M1", 1, 50);
  await userEvent.click(screen.getByRole("button", { name: "닫기" }));
  expect(screen.getByLabelText("M1 메뉴이름")).toHaveValue("초안");
  await userEvent.click(
    within(screen.getByTestId("menu-row-g1")).getByRole("button", {
      name: "업무 접기",
    }),
  );
  expect(screen.queryByLabelText("M1 메뉴이름")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(api.putMenuManagement).toHaveBeenCalled());
  expect(vi.mocked(api.putMenuManagement).mock.calls[0][0]).toHaveLength(5);
});
it("빈목록·조회실패는 가짜 자료없이 재조회", async () => {
  vi.mocked(api.getMenuManagement).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  render(<MenusScreen />);
  await screen.findByText("조회 실패");
  expect(api.putMenuManagement).not.toHaveBeenCalled();
  vi.mocked(api.getMenuManagement).mockResolvedValue({ ...tree, items: [] });
  await userEvent.click(
    screen.getByRole("button", { name: "최신값 다시 조회" }),
  );
  await screen.findByText("등록된 메뉴가 없습니다.");
});
