import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { RoleStopAction } from "./RoleStopAction";
import * as api from "./roleStopApi";
vi.mock("./roleStopApi", async (original) => ({
  ...(await original<typeof api>()),
  saveRoleStop: vi.fn(),
}));
const role: api.RoleStopTarget = {
  roleId: "R-801",
  name: "추가 역할",
  systemRole: false,
  use: true,
  revision: "saved-role",
};
const result: api.RoleStopResult = {
  changed: true,
  revision: "stopped-role",
  value: {
    ...role,
    use: false,
    revision: "stopped-role",
    dataScope: "PROJ",
    sensitiveView: false,
    sortOrder: 801,
    menuPermissions: [],
    developmentMode: true,
    accessEnforcement: false,
    settingsRevision: "settings",
  },
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.saveRoleStop).mockResolvedValue(result);
});
async function open(refresh = vi.fn().mockResolvedValue(undefined)) {
  render(<RoleStopAction role={role} onRefresh={refresh} />);
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  return screen.getByRole("dialog", { name: "추가 역할 사용 중지" });
}
it("review names selected role and cancel performs no write", async () => {
  const dialog = await open();
  expect(dialog).toHaveTextContent("추가 역할 (R-801)");
  expect(dialog).toHaveTextContent("권한·번호·기록은 보존");
  expect(within(dialog).queryByRole("textbox")).not.toBeInTheDocument();
  expect(api.saveRoleStop).not.toHaveBeenCalled();
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.saveRoleStop).not.toHaveBeenCalled();
});
it.each(["ADMIN", "EXEC", "FIN", "SALES_LDR", "PM"])(
  "%s is protected independently of system flag",
  (roleId) => {
    render(<RoleStopAction role={{ ...role, roleId }} onRefresh={vi.fn()} />);
    expect(
      screen.queryByRole("button", { name: "사용 중지" }),
    ).not.toBeInTheDocument();
  },
);
it("system flag hides stop on an additional role", () => {
  render(
    <RoleStopAction role={{ ...role, systemRole: true }} onRefresh={vi.fn()} />,
  );
  expect(
    screen.queryByRole("button", { name: "사용 중지" }),
  ).not.toBeInTheDocument();
});
it("already stopped has no resume/delete/stop action", () => {
  render(<RoleStopAction role={{ ...role, use: false }} onRefresh={vi.fn()} />);
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
});
it("dirty editor requires saving or cancelling draft first", () => {
  render(<RoleStopAction role={role} dirty onRefresh={vi.fn()} />);
  expect(screen.getByRole("button", { name: "사용 중지" })).toBeDisabled();
  expect(screen.getByText(/변경 내용을 저장하거나 취소/)).toHaveClass(
    "sicost-tone-warn",
  );
});
it("confirm sends saved role revision once then refreshes and hides stopped action", async () => {
  const refresh = vi.fn().mockResolvedValue(undefined);
  const dialog = await open(refresh);
  await userEvent.click(within(dialog).getByRole("button", { name: "중지" }));
  await waitFor(() =>
    expect(api.saveRoleStop).toHaveBeenCalledWith(role.roleId, role.revision),
  );
  expect(api.saveRoleStop).toHaveBeenCalledTimes(1);
  expect(refresh).toHaveBeenCalledTimes(1);
  expect(await screen.findByText("저장했습니다.")).toBeInTheDocument();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "사용 중지" }),
  ).not.toBeInTheDocument();
});
it("pending prevents duplicate confirm and cancellation", async () => {
  let resolve!: (result: api.RoleStopResult) => void;
  vi.mocked(api.saveRoleStop).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  const dialog = await open();
  const confirm = within(dialog).getByRole("button", { name: "중지" });
  fireEvent.click(confirm);
  fireEvent.click(confirm);
  expect(api.saveRoleStop).toHaveBeenCalledTimes(1);
  expect(within(dialog).getByRole("button", { name: "취소" })).toBeDisabled();
  await act(async () => resolve(result));
});
it.each(["ROLE_IN_USE", "SYSTEM_ROLE_PROTECTED"])(
  "%s keeps target and shows server protection message/request ID",
  async (code) => {
    vi.mocked(api.saveRoleStop).mockRejectedValue(
      new ApiError(409, {
        success: false,
        meta: {},
        code,
        message:
          "1명이 이 역할을 쓰고 있습니다. 시스템 › 사용자에서 먼저 빼 주세요.",
        requestId: "role-request",
      }),
    );
    const refresh = vi.fn();
    const dialog = await open(refresh);
    await userEvent.click(within(dialog).getByRole("button", { name: "중지" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "role-request",
    );
    expect(dialog).toHaveTextContent("시스템 › 사용자에서 먼저 빼 주세요.");
    expect(refresh).not.toHaveBeenCalled();
    expect(api.saveRoleStop).toHaveBeenCalledTimes(1);
  },
);
it("stale offers read-only latest refresh and keeps error field messages", async () => {
  vi.mocked(api.saveRoleStop).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다른 사용자가 변경했습니다.",
      requestId: "stale",
      field: "expectedRevision",
      details: { violations: [{ field: "role", message: "최신값 필요" }] },
    }),
  );
  const refresh = vi.fn().mockResolvedValue(undefined);
  const dialog = await open(refresh);
  await userEvent.click(within(dialog).getByRole("button", { name: "중지" }));
  expect(
    await within(dialog).findByText("role: 최신값 필요"),
  ).toBeInTheDocument();
  await userEvent.click(
    within(dialog).getByRole("button", { name: "최신값 다시 조회" }),
  );
  expect(refresh).toHaveBeenCalledTimes(1);
  expect(api.saveRoleStop).toHaveBeenCalledTimes(1);
});
it("committed refresh failure never replays stop; explicit retry only reads", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("조회 실패"))
    .mockResolvedValue(undefined);
  const dialog = await open(refresh);
  await userEvent.click(within(dialog).getByRole("button", { name: "중지" }));
  expect(await screen.findByText(/저장은 완료됐지만/)).toHaveClass(
    "sicost-tone-warn",
  );
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(refresh).toHaveBeenCalledTimes(2);
  expect(api.saveRoleStop).toHaveBeenCalledTimes(1);
});
it("changing selection after a committed stop permits the next additional role", async () => {
  const refresh = vi.fn().mockResolvedValue(undefined);
  const view = render(<RoleStopAction role={role} onRefresh={refresh} />);
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "중지" }));
  await screen.findByText("저장했습니다.");
  view.rerender(
    <RoleStopAction role={{ ...role, roleId: "R-802" }} onRefresh={refresh} />,
  );
  expect(screen.getByRole("button", { name: "사용 중지" })).toBeEnabled();
});
