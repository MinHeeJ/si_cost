import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { PermissionsScreen } from "./PermissionsScreen";
import * as api from "./rolesApi";
vi.mock("./rolesApi", async (original) => ({
  ...(await original<typeof api>()),
  getRoles: vi.fn(),
  getRole: vi.fn(),
  createRole: vi.fn(),
  putRole: vi.fn(),
  getRoleMenus: vi.fn(),
  getRoleHistory: vi.fn(),
  stopRole: vi.fn(),
}));
const permission: api.RolePermission = {
  menuId: "M-role",
  parentId: "M-root",
  name: "권한",
  screenId: "UI-24",
  use: true,
  inquire: true,
  register: false,
  update: true,
  delete: false,
  upload: false,
  download: false,
  confirm: false,
};
const role: api.ManagedRoleDetail = {
  roleId: "ADMIN",
  name: "시스템 관리자",
  dataScope: "ALL",
  sensitiveView: false,
  systemRole: true,
  use: true,
  sortOrder: 1,
  revision: "role-revision",
  menuPermissions: [
    permission,
    {
      ...permission,
      menuId: "M-stopped",
      name: "중지 메뉴",
      use: false,
      inquire: false,
    },
  ],
  developmentMode: true,
  accessEnforcement: false,
  settingsRevision: "settings-revision",
};
const another: api.ManagedRoleDetail = {
  ...role,
  roleId: "R-017",
  name: "추가 검토자",
  systemRole: false,
  sortOrder: 6,
  dataScope: "PART",
};
function error(code = "STALE_WRITE", field?: string) {
  return new ApiError(409, {
    success: false,
    meta: {},
    code,
    message: "다시 조회한 뒤 저장해 주세요.",
    requestId: "trace",
    field,
  });
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getRoles).mockResolvedValue({
    items: [role, another],
    developmentMode: true,
    accessEnforcement: false,
    settingsRevision: "settings",
  });
  vi.mocked(api.getRole).mockImplementation(async (id) =>
    id === another.roleId ? another : role,
  );
  vi.mocked(api.getRoleMenus).mockResolvedValue({
    items: [
      {
        menuId: "M-root",
        parentId: null,
        name: "시스템",
        level: 1,
        screenId: null,
        use: true,
      },
    ],
  });
  vi.mocked(api.getRoleHistory).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(api.putRole).mockResolvedValue({
    value: { ...role, dataScope: "PART" },
    revision: "next",
    changed: true,
  });
  vi.mocked(api.createRole).mockResolvedValue({
    value: another,
    revision: "new",
    changed: true,
  });
  vi.mocked(api.stopRole).mockResolvedValue({
    value: { ...another, use: false },
    revision: "stop",
    changed: true,
  });
});
async function mount() {
  render(<PermissionsScreen />);
  await screen.findByRole("checkbox", { name: "권한 조회" });
}
it("DB 역할 탭·범위·민감·중지메뉴와 독립 7동작을 표시하고 시스템 중지는 숨긴다", async () => {
  await mount();
  expect(screen.getByRole("tab", { name: role.name })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(screen.getByLabelText("데이터범위")).toHaveValue("ALL");
  expect(screen.getByLabelText("민감항목 보기")).not.toBeChecked();
  expect(screen.getByText("사용 중지")).toHaveClass("sicost-tone-lock");
  expect(screen.getByText("시스템 역할")).toHaveClass("sicost-tone-info");
  expect(screen.queryByTestId("role-stop-open")).not.toBeInTheDocument();
  expect(
    screen.getByText(
      "권한 설정을 저장합니다. 실제 접근권한 적용 전인 개발·검증 모드입니다.",
    ),
  ).toHaveClass("sicost-tone-info");
  expect(screen.getByRole("checkbox", { name: "권한 수정" })).toBeChecked();
  await userEvent.click(screen.getByRole("checkbox", { name: "권한 조회" }));
  expect(screen.getByRole("checkbox", { name: "권한 수정" })).toBeChecked();
  expect(api.putRole).not.toHaveBeenCalled();
});
it("전체 권한·중지행과 revision만 저장하고 읽기 메타데이터는 보내지 않는다", async () => {
  await mount();
  await userEvent.selectOptions(screen.getByLabelText("데이터범위"), "PART");
  await userEvent.click(screen.getByLabelText("민감항목 보기"));
  await userEvent.click(screen.getByRole("checkbox", { name: "권한 조회" }));
  await userEvent.click(screen.getByTestId("role-save"));
  await waitFor(() => expect(api.putRole).toHaveBeenCalledTimes(1));
  const [id, body] = vi.mocked(api.putRole).mock.calls[0];
  expect(id).toBe("ADMIN");
  expect(body).toEqual({
    ...api.roleInput(role),
    dataScope: "PART",
    sensitiveView: true,
    menuPermissions: [
      { ...api.roleInput(role).menuPermissions[0], inquire: false },
      api.roleInput(role).menuPermissions[1],
    ],
  });
  await screen.findByText("저장했습니다.");
});
it("취소는 마지막 저장값을 복원하고 DB 요청을 보내지 않는다", async () => {
  await mount();
  await userEvent.selectOptions(screen.getByLabelText("데이터범위"), "PART");
  await userEvent.click(screen.getByTestId("role-cancel"));
  expect(screen.getByLabelText("데이터범위")).toHaveValue("ALL");
  expect(api.putRole).not.toHaveBeenCalled();
});
it("다른 역할 이동은 미저장 Dialog에서 계속 작성 또는 명시 버리기를 따른다", async () => {
  await mount();
  await userEvent.selectOptions(screen.getByLabelText("데이터범위"), "PROJ");
  await userEvent.click(screen.getByRole("tab", { name: another.name }));
  await userEvent.click(screen.getByTestId("role-dirty-stay"));
  expect(screen.getByLabelText("데이터범위")).toHaveValue("PROJ");
  await userEvent.click(screen.getByRole("tab", { name: another.name }));
  await userEvent.click(screen.getByTestId("role-dirty-discard"));
  await waitFor(() =>
    expect(screen.getByRole("tab", { name: another.name })).toHaveAttribute(
      "aria-selected",
      "true",
    ),
  );
  expect(api.putRole).not.toHaveBeenCalled();
});
it("페이지 전환은 모든 초안을 보존하고 숨은 행까지 저장한다", async () => {
  const many = {
    ...role,
    menuPermissions: Array.from({ length: 25 }, (_, i) => ({
      ...permission,
      menuId: `menu-${i}`,
      name: `메뉴 ${i}`,
    })),
  };
  vi.mocked(api.getRole).mockResolvedValue(many);
  await mountMany();
  await userEvent.click(screen.getByRole("checkbox", { name: "메뉴 0 조회" }));
  await userEvent.click(screen.getByTestId("role-page-next"));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  await userEvent.click(
    screen.getByRole("checkbox", { name: "메뉴 24 다운로드" }),
  );
  await userEvent.click(screen.getByTestId("role-save"));
  await waitFor(() => expect(api.putRole).toHaveBeenCalled());
  const body = vi.mocked(api.putRole).mock.calls[0][1];
  expect(body.menuPermissions).toHaveLength(25);
  expect(body.menuPermissions[0].inquire).toBe(false);
  expect(body.menuPermissions[24].download).toBe(true);
});
async function mountMany() {
  render(<PermissionsScreen />);
  await screen.findByRole("checkbox", { name: "메뉴 0 조회" });
}
it("빈 목록은 실제 빈 안내와 역할 추가를 제공한다", async () => {
  vi.mocked(api.getRoles).mockResolvedValue({
    items: [],
    developmentMode: true,
    accessEnforcement: false,
    settingsRevision: "empty",
  });
  render(<PermissionsScreen />);
  await screen.findByText("사용 중인 역할이 없습니다. 역할을 추가해 주세요.");
  expect(screen.getByTestId("role-copy-open")).toBeDisabled();
  expect(api.getRole).not.toHaveBeenCalled();
});
it("조회 실패는 오류와 읽기 전용 재시도를 제공한다", async () => {
  vi.mocked(api.getRoles).mockRejectedValueOnce(new Error("조회 실패"));
  render(<PermissionsScreen />);
  await screen.findByText("조회 실패");
  await userEvent.click(screen.getByTestId("role-refresh"));
  await screen.findByRole("checkbox", { name: "권한 조회" });
  expect(api.createRole).not.toHaveBeenCalled();
});
it("400 필드 오류와 409 보호는 초안과 입력을 유지하며 강제 권한차단하지 않는다", async () => {
  vi.mocked(api.putRole).mockRejectedValue(
    error("ADMIN_PERMISSION_PROTECTED", "menuPermissions"),
  );
  await mount();
  await userEvent.click(screen.getByLabelText("민감항목 보기"));
  await userEvent.click(screen.getByTestId("role-save"));
  await screen.findByText("menuPermissions: 다시 조회한 뒤 저장해 주세요.");
  expect(screen.getByLabelText("민감항목 보기")).toBeChecked();
  expect(screen.getByTestId("role-save")).toBeEnabled();
  expect(api.putRole).toHaveBeenCalledTimes(1);
});
it("커밋 후 재조회 실패는 재저장하지 않고 조회만 재시도한다", async () => {
  await mount();
  vi.mocked(api.getRoles).mockRejectedValueOnce(new Error("네트워크"));
  await userEvent.selectOptions(screen.getByLabelText("데이터범위"), "PART");
  await userEvent.click(screen.getByTestId("role-save"));
  await screen.findByText(
    "저장은 완료됐지만 목록을 다시 불러오지 못했습니다. 다시 조회해 주세요.",
  );
  await userEvent.click(screen.getByTestId("role-refresh"));
  await waitFor(() => expect(screen.getByTestId("role-refresh")).toBeEnabled());
  expect(api.putRole).toHaveBeenCalledTimes(1);
});
it("역할 추가는 기본 PROJ·이름 검증 뒤 NEW 전용 payload와 신규 탭 이동을 수행한다", async () => {
  await mount();
  await userEvent.click(screen.getByTestId("role-new-open"));
  const dialog = screen.getByRole("dialog", { name: "역할 추가" });
  expect(within(dialog).getByLabelText("데이터범위")).toHaveValue("PROJ");
  await userEvent.click(screen.getByTestId("role-create-confirm"));
  await screen.findByText("이름은 1~50자로 입력해 주세요.");
  expect(api.createRole).not.toHaveBeenCalled();
  fireEvent.change(within(dialog).getByLabelText("이름"), {
    target: { value: " 새 역할 " },
  });
  await userEvent.selectOptions(
    within(dialog).getByLabelText("데이터범위"),
    "PART",
  );
  await userEvent.click(screen.getByTestId("role-create-confirm"));
  await waitFor(() =>
    expect(api.createRole).toHaveBeenCalledWith({
      mode: "NEW",
      name: "새 역할",
      dataScope: "PART",
    }),
  );
  await waitFor(() =>
    expect(screen.getByRole("tab", { name: another.name })).toHaveAttribute(
      "aria-selected",
      "true",
    ),
  );
});
it("역할 추가 중복 오류는 이름을 보존하고 취소는 쓰지 않는다", async () => {
  vi.mocked(api.createRole).mockRejectedValue(error("DUPLICATE_NAME", "name"));
  await mount();
  await userEvent.click(screen.getByTestId("role-new-open"));
  const dialog = screen.getByRole("dialog");
  fireEvent.change(within(dialog).getByLabelText("이름"), {
    target: { value: "중복" },
  });
  await userEvent.click(screen.getByTestId("role-create-confirm"));
  await waitFor(() =>
    expect(within(dialog).getByLabelText("이름")).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(within(dialog).getByLabelText("이름")).toHaveValue("중복");
  await userEvent.click(screen.getByTestId("role-create-cancel"));
  expect(api.createRole).toHaveBeenCalledTimes(1);
});
it("COPY는 미저장 초안이 아닌 저장 원본·revision을 보내고 취소는 초안을 지킨다", async () => {
  await mount();
  await userEvent.selectOptions(screen.getByLabelText("데이터범위"), "PROJ");
  await userEvent.click(screen.getByTestId("role-copy-open"));
  await screen.findByText(/저장 원본: 전체/);
  await userEvent.click(screen.getByTestId("role-create-cancel"));
  expect(screen.getByLabelText("데이터범위")).toHaveValue("PROJ");
  await userEvent.click(screen.getByTestId("role-copy-open"));
  await screen.findByText(/저장 원본: 전체/);
  fireEvent.change(within(screen.getByRole("dialog")).getByLabelText("이름"), {
    target: { value: "복사 역할" },
  });
  await userEvent.click(screen.getByTestId("role-create-confirm"));
  await waitFor(() =>
    expect(api.createRole).toHaveBeenCalledWith({
      mode: "COPY",
      name: "복사 역할",
      sourceRoleId: "ADMIN",
      sourceRevision: "role-revision",
    }),
  );
  expect(api.putRole).not.toHaveBeenCalled();
});
it("복사 원본 실패는 확인을 비활성화한다", async () => {
  await mount();
  vi.mocked(api.getRole).mockRejectedValueOnce(new Error("원본 실패"));
  await userEvent.click(screen.getByTestId("role-copy-open"));
  await screen.findByText("원본 실패");
  expect(screen.getByTestId("role-create-confirm")).toBeDisabled();
  expect(api.createRole).not.toHaveBeenCalled();
});
it("추가 역할 중지는 검토 Dialog의 명시 확인에만 한 번 전송한다", async () => {
  await mount();
  await userEvent.click(screen.getByRole("tab", { name: another.name }));
  await screen.findByTestId("role-stop-open");
  await userEvent.click(screen.getByTestId("role-stop-open"));
  expect(api.stopRole).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("role-stop-cancel"));
  expect(api.stopRole).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("role-stop-open"));
  await userEvent.click(screen.getByTestId("role-stop-confirm"));
  await waitFor(() =>
    expect(api.stopRole).toHaveBeenCalledWith(another.roleId, another.revision),
  );
  expect(api.stopRole).toHaveBeenCalledTimes(1);
});
it("변경 이력은 읽기 전용·빈 상태와 새로고침을 제공하고 초안을 보존한다", async () => {
  await mount();
  await userEvent.click(screen.getByLabelText("민감항목 보기"));
  await userEvent.click(screen.getByTestId("role-history-open"));
  await screen.findByText("변경 이력이 없습니다.");
  expect(api.getRoleHistory).toHaveBeenCalledWith("ADMIN", 1, 50);
  await userEvent.click(screen.getByTestId("role-history-refresh"));
  await waitFor(() => expect(api.getRoleHistory).toHaveBeenCalledTimes(2));
  await userEvent.click(screen.getByTestId("role-history-close"));
  expect(screen.getByLabelText("민감항목 보기")).toBeChecked();
  expect(api.putRole).not.toHaveBeenCalled();
});
