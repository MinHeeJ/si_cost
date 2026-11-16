import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { MenuRegistrationDialog } from "./MenuRegistrationDialog";
import { MenusScreen } from "../menumanagement/MenusScreen";
import * as api from "../menumanagement/menuManagementApi";
vi.mock("../menumanagement/menuManagementApi", async (importOriginal) => ({
  ...(await importOriginal<typeof api>()),
  getMenuManagement: vi.fn(),
  putMenuManagement: vi.fn(),
  getMenuManagementScreens: vi.fn(),
  createManagedMenu: vi.fn(),
  getMenuHistory: vi.fn(),
}));
let tree: api.ManagedMenuTree;
const saved = vi.fn(),
  close = vi.fn();
beforeEach(() => {
  vi.resetAllMocks();
  tree = {
    items: [
      {
        menuId: "G001",
        parentId: null,
        name: "업무",
        level: 1,
        screenId: null,
        sortOrder: 1,
        use: true,
        pcOnly: false,
        locked: false,
        icon: "folder",
      },
      {
        menuId: "M001",
        parentId: "G001",
        name: "중지 화면",
        level: 2,
        screenId: "UI-02",
        sortOrder: 1,
        use: false,
        pcOnly: false,
        locked: false,
        icon: null,
      },
    ],
    revision: "tree-revision",
    developmentMode: true,
    accessEnforcement: false,
  };
  vi.mocked(api.getMenuManagement).mockImplementation(async () =>
    structuredClone(tree),
  );
  vi.mocked(api.getMenuManagementScreens).mockResolvedValue({
    items: [{ screenId: "UI-03", name: "개인 원가·수익표" }],
    revision: tree.revision,
  });
  vi.mocked(api.createManagedMenu).mockResolvedValue({
    value: tree,
    revision: "next",
    changed: true,
  });
});
async function open() {
  render(
    <MenuRegistrationDialog tree={tree} onClose={close} onSaved={saved} />,
  );
  await waitFor(() =>
    expect(api.getMenuManagementScreens).toHaveBeenCalledTimes(1),
  );
}
async function submenu() {
  fireEvent.change(screen.getByLabelText("레벨"), { target: { value: "2" } });
  await screen.findByRole("option", { name: "UI-03 개인 원가·수익표" });
}
it("대메뉴에는 이름과 레벨만·ID/아이콘/잠금/경로 입력 없음", async () => {
  await open();
  expect(screen.queryByLabelText("상위대메뉴")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("화면번호")).not.toBeInTheDocument();
  expect(screen.getByText(/빈 대메뉴는 탐색에서 제외/)).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("이름"), {
    target: { value: " 새 그룹 " },
  });
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await waitFor(() =>
    expect(api.createManagedMenu).toHaveBeenCalledWith({
      level: 1,
      name: "새 그룹",
      expectedRevision: "tree-revision",
    }),
  );
  expect(saved).toHaveBeenCalledWith(tree);
});
it("서브는 선택한 실제부모와 화면·boolean PC만 보낸다", async () => {
  await open();
  await submenu();
  fireEvent.change(screen.getByLabelText("이름"), {
    target: { value: " 원가 " },
  });
  fireEvent.change(screen.getByLabelText("상위대메뉴"), {
    target: { value: "G001" },
  });
  fireEvent.change(screen.getByLabelText("화면번호"), {
    target: { value: "UI-03" },
  });
  expect(screen.getByLabelText("PC전용")).not.toBeChecked();
  await userEvent.click(screen.getByLabelText("PC전용"));
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await waitFor(() =>
    expect(api.createManagedMenu).toHaveBeenCalledWith({
      level: 2,
      name: "원가",
      parentId: "G001",
      screenId: "UI-03",
      pcOnly: true,
      expectedRevision: "tree-revision",
    }),
  );
});
it("대메뉴 전환시 서브값은 제거되고 다시 서브로 와도 초기값", async () => {
  await open();
  await submenu();
  fireEvent.change(screen.getByLabelText("상위대메뉴"), {
    target: { value: "G001" },
  });
  fireEvent.change(screen.getByLabelText("화면번호"), {
    target: { value: "UI-03" },
  });
  await userEvent.click(screen.getByLabelText("PC전용"));
  fireEvent.change(screen.getByLabelText("레벨"), { target: { value: "1" } });
  fireEvent.change(screen.getByLabelText("레벨"), { target: { value: "2" } });
  expect(screen.getByLabelText("상위대메뉴")).toHaveValue("");
  expect(screen.getByLabelText("화면번호")).toHaveValue("");
  expect(screen.getByLabelText("PC전용")).not.toBeChecked();
});
it.each([" ", "가".repeat(51)])(
  "잘못된 이름은 필드오류·등록0: %s",
  async (name) => {
    await open();
    fireEvent.change(screen.getByLabelText("이름"), {
      target: { value: name },
    });
    await userEvent.click(screen.getByRole("button", { name: "등록" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("1~50자");
    expect(api.createManagedMenu).not.toHaveBeenCalled();
  },
);
it("서브의 부모와 화면 누락은 각각 필드오류", async () => {
  await open();
  await submenu();
  fireEvent.change(screen.getByLabelText("이름"), {
    target: { value: "서브" },
  });
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  expect(
    await screen.findByText("상위대메뉴를 선택해 주세요."),
  ).toBeInTheDocument();
  expect(screen.getByText("개발된 화면을 선택해 주세요.")).toBeInTheDocument();
  expect(api.createManagedMenu).not.toHaveBeenCalled();
});
it("미연결0이면 서브만 비활성·가짜화면 없음·그룹등록 가능", async () => {
  vi.mocked(api.getMenuManagementScreens).mockResolvedValue({
    items: [],
    revision: "r",
  });
  await open();
  fireEvent.change(screen.getByLabelText("레벨"), { target: { value: "2" } });
  await screen.findByText("메뉴에 없는 개발된 화면이 없습니다");
  expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
  expect(
    within(screen.getByLabelText("화면번호")).getAllByRole("option"),
  ).toHaveLength(1);
  fireEvent.change(screen.getByLabelText("레벨"), { target: { value: "1" } });
  expect(screen.getByRole("button", { name: "등록" })).toBeEnabled();
});
it("등록부의 준비·로그인·미등록·중지연결화면은 선택할 수 없다", async () => {
  vi.mocked(api.getMenuManagementScreens).mockResolvedValue({
    items: [
      { screenId: "UI-20", name: "제외" },
      { screenId: "UI-22", name: "로그인" },
      { screenId: "UI-01", name: "준비" },
      { screenId: "UI-99", name: "미등록" },
      { screenId: "UI-02", name: "중지연결" },
      { screenId: "UI-03", name: "개인 원가·수익표" },
    ],
    revision: "r",
  });
  await open();
  await submenu();
  expect(
    within(screen.getByLabelText("화면번호")).getAllByRole("option"),
  ).toHaveLength(2);
  expect(
    screen.queryByRole("option", { name: /로그인|중지연결|미등록|준비/ }),
  ).not.toBeInTheDocument();
});
it("조회 로딩중 서브등록불가·실패는 빈결과와 구분하고 읽기재시도", async () => {
  vi.mocked(api.getMenuManagementScreens).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  await open();
  fireEvent.change(screen.getByLabelText("레벨"), { target: { value: "2" } });
  await screen.findByText("조회 실패");
  expect(
    screen.queryByText("메뉴에 없는 개발된 화면이 없습니다"),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
  await userEvent.click(
    screen.getByRole("button", { name: "가능 화면 다시 조회" }),
  );
  await screen.findByRole("option", { name: "UI-03 개인 원가·수익표" });
  expect(api.createManagedMenu).not.toHaveBeenCalled();
});
it.each(["STALE_WRITE", "DUPLICATE_NAME", "DUPLICATE_SCREEN"])(
  "서버 %s 오류는 입력유지·명시적 재시도 전 쓰기0",
  async (code) => {
    vi.mocked(api.createManagedMenu).mockRejectedValue(
      new ApiError(409, {
        success: false,
        meta: {},
        code,
        message: "최신값을 다시 조회해 주세요.",
        field: "name",
        requestId: "r",
        details: {
          violations: [{ field: "name", message: "이름을 확인해 주세요." }],
        },
      }),
    );
    await open();
    fireEvent.change(screen.getByLabelText("이름"), {
      target: { value: "입력 유지" },
    });
    await userEvent.click(screen.getByRole("button", { name: "등록" }));
    await screen.findByText("최신값을 다시 조회해 주세요.");
    expect(screen.getByLabelText("이름")).toHaveValue("입력 유지");
    expect(screen.getByText("이름을 확인해 주세요.")).toBeInTheDocument();
    expect(saved).not.toHaveBeenCalled();
    expect(api.createManagedMenu).toHaveBeenCalledTimes(1);
  },
);
it("등록중 중복클릭·필드편집 불가", async () => {
  let finish!: (result: api.ManagedMenuSaveResult) => void;
  vi.mocked(api.createManagedMenu).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  await open();
  fireEvent.change(screen.getByLabelText("이름"), {
    target: { value: "등록" },
  });
  fireEvent.click(screen.getByRole("button", { name: "등록" }));
  fireEvent.click(screen.getByRole("button", { name: "처리 중…" }));
  expect(api.createManagedMenu).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText("이름")).toBeDisabled();
  finish({ value: tree, revision: "r", changed: true });
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
});
it("취소는 쓰기없이 닫는다", async () => {
  await open();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(close).toHaveBeenCalledTimes(1);
  expect(api.createManagedMenu).not.toHaveBeenCalled();
});
it("등록커밋 후 재조회실패는 완료안내·재조회만·기존트리편집 보존", async () => {
  render(<MenusScreen />);
  await screen.findByLabelText("G001 메뉴이름");
  await userEvent.click(screen.getByRole("button", { name: "메뉴 등록" }));
  const dialog = await screen.findByRole("dialog", { name: "메뉴 등록" });
  fireEvent.change(within(dialog).getByLabelText("이름"), {
    target: { value: "신규 그룹" },
  });
  vi.mocked(api.getMenuManagement).mockRejectedValueOnce(
    new Error("저장 후 조회 실패"),
  );
  await userEvent.click(within(dialog).getByRole("button", { name: "등록" }));
  await screen.findByText(/저장은 완료됐지만/);
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "메뉴 등록" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "저장 후 재조회" }));
  await waitFor(() => expect(api.getMenuManagement).toHaveBeenCalledTimes(3));
  expect(api.createManagedMenu).toHaveBeenCalledTimes(1);
  expect(api.putMenuManagement).not.toHaveBeenCalled();
});
