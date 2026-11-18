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
import { UserStateActions } from "./UserStateActions";
import { UserActivityPanel, UserScopesPanel } from "./UserActivityPanel";
import {
  UserBulkStatusToolbar,
  UserStatusResultsDialog,
} from "./UserBulkStatusToolbar";
import { BusinessUserDetail } from "../businessusers/BusinessUserDetail";
import { BusinessUsersScreen } from "../businessusers/BusinessUsersScreen";
import type { BusinessUser } from "../businessusers/businessUsersApi";
import * as users from "../businessusers/businessUsersApi";
import * as api from "./userAdministrationApi";
vi.mock("./userAdministrationApi");
vi.mock("../businessusers/businessUsersApi");
vi.mock("./UserProjectsPanel", () => ({
  UserProjectsPanel: () => (
    <section data-testid="existing-project-panel">담당 프로젝트 원천</section>
  ),
}));
const user: BusinessUser = {
  userId: "ab12",
  name: "업무 담당",
  email: "user@example.org",
  roles: ["PM"],
  businessRoleIds: ["PM"],
  status: "USE",
  lastLoginAt: null,
  revision: "raw-revision",
  assignedParts: [{ partId: "HQ", name: "본사", headOffice: true }],
};
const rejected: api.UserStateResults = {
  applied: 0,
  unchanged: 0,
  rejected: 1,
  items: [
    {
      userId: user.userId,
      outcome: "REJECTED",
      code: "STALE_WRITE",
      reason: "최신값을 다시 조회해 주세요.",
      changed: false,
      revision: null,
    },
  ],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(users.getManagedUserRoleOptions).mockResolvedValue([]);
  vi.mocked(users.getBusinessUser).mockResolvedValue(user);
  vi.mocked(users.getBusinessUsers).mockResolvedValue({
    items: [user],
    total: 1,
    page: 1,
    size: 50,
  });
  vi.mocked(users.getBusinessUserHistory).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(api.changeBusinessUserState).mockResolvedValue({
    changed: true,
    message: "저장했습니다.",
    user: { ...user, status: "STOP" },
  });
  vi.mocked(api.getBusinessUserScopes).mockResolvedValue({
    userId: user.userId,
    assignedParts: [],
    assignedProjects: [],
    revision: user.revision,
  });
  vi.mocked(api.getBusinessUserAccessLogs).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(api.saveBusinessUserStatusChanges).mockResolvedValue(rejected);
});
it("중지는 대상 확인 후 한 번만 전송하고 취소는 쓰지 않는다", async () => {
  const refreshed = vi.fn().mockResolvedValue(undefined);
  render(<UserStateActions user={user} onCommitted={refreshed} />);
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  expect(screen.getByRole("dialog")).toHaveTextContent("업무 담당 (ab12)");
  expect(api.changeBusinessUserState).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.changeBusinessUserState).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByTestId("user-state-confirm-save"));
  await waitFor(() => expect(refreshed).toHaveBeenCalledTimes(1));
  expect(api.changeBusinessUserState).toHaveBeenCalledTimes(1);
  expect(api.changeBusinessUserState).toHaveBeenCalledWith(
    user.userId,
    "STOP",
    user.revision,
  );
});
it.each(["USE", "STOP", "LOCK"] as const)(
  "상태 %s에 맞는 명령과 실제 비밀번호 재발급을 제공한다",
  async (status) => {
    vi.mocked(users.getBusinessUser).mockResolvedValue({ ...user, status });
    render(
      <BusinessUserDetail id={user.userId} onClose={vi.fn()} onEdit={vi.fn()} />,
    );
    const reset = await screen.findByRole("button", {
      name: "새 비밀번호 다시 발급",
    });
    if (status === "STOP") expect(reset).toBeDisabled();
    else {
      expect(reset).toBeEnabled();
      await userEvent.click(reset);
      const dialog = screen.getByRole("dialog", { name: "비밀번호 초기화" });
      expect(dialog).toHaveTextContent("u***@example.org");
      expect(dialog).toHaveTextContent(
        "이전 비밀번호와 모든 로그인 세션은 무효화됩니다",
      );
      expect(
        within(dialog).getByRole("button", { name: "새 비밀번호 발급" }),
      ).toBeEnabled();
      await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
    }
    expect(screen.queryByRole("button", { name: "잠금 해제" }) !== null).toBe(
      status === "LOCK",
    );
    expect(screen.queryByRole("button", { name: "다시 사용" }) !== null).toBe(
      status === "STOP",
    );
    expect(screen.queryByRole("button", { name: "사용 중지" }) !== null).toBe(
      status !== "STOP",
    );
    expect(api.changeBusinessUserState).not.toHaveBeenCalled();
  },
);
it("재개는 확인하고 잠금 해제는 추가 확인 없이 실행한다", async () => {
  const { rerender } = render(
    <UserStateActions
      user={{ ...user, status: "STOP" }}
      onCommitted={vi.fn().mockResolvedValue(undefined)}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "다시 사용" }));
  await userEvent.click(screen.getByTestId("user-state-confirm-save"));
  expect(api.changeBusinessUserState).toHaveBeenCalledWith(
    user.userId,
    "RESUME",
    user.revision,
  );
  rerender(
    <UserStateActions
      user={{ ...user, status: "LOCK" }}
      onCommitted={vi.fn().mockResolvedValue(undefined)}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "잠금 해제" }));
  expect(api.changeBusinessUserState).toHaveBeenLastCalledWith(
    user.userId,
    "UNLOCK",
    user.revision,
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("전송 중 연속 클릭은 한 요청이고 실패 뒤 자동 재전송하지 않는다", async () => {
  let reject!: (error: Error) => void;
  vi.mocked(api.changeBusinessUserState).mockReturnValueOnce(
    new Promise((_, no) => {
      reject = no;
    }),
  );
  render(
    <UserStateActions
      user={{ ...user, status: "LOCK" }}
      onCommitted={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByTestId("user-unlock-save"));
  fireEvent.click(screen.getByTestId("user-unlock-save"));
  expect(api.changeBusinessUserState).toHaveBeenCalledTimes(1);
  await act(async () => reject(new Error("요청 결과 확인 불가")));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "요청 결과 확인 불가",
  );
  expect(api.changeBusinessUserState).toHaveBeenCalledTimes(1);
});
it("보호와 stale 오류를 유지하며 최신조회는 쓰기를 다시 실행하지 않는다", async () => {
  vi.mocked(api.changeBusinessUserState).mockRejectedValueOnce(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "USER_IS_PART_LEADER",
      message: "미래 파트, 중지 파트: 영업 파트에서 먼저 변경해 주세요.",
      requestId: "trace",
    }),
  );
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(<UserStateActions user={user} onCommitted={refresh} />);
  await userEvent.click(screen.getByTestId("user-stop-open"));
  await userEvent.click(screen.getByTestId("user-state-confirm-save"));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "미래 파트, 중지 파트",
  );
  expect(screen.getByRole("dialog")).toBeInTheDocument();
  await userEvent.click(screen.getByTestId("user-state-latest"));
  expect(refresh).toHaveBeenCalledTimes(1);
  expect(api.changeBusinessUserState).toHaveBeenCalledTimes(1);
});
it("저장 후 재조회 실패는 별도로 안내하고 재조회만 제공한다", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("조회 실패"))
    .mockResolvedValue(undefined);
  render(<UserStateActions user={user} onCommitted={refresh} />);
  await userEvent.click(screen.getByTestId("user-stop-open"));
  await userEvent.click(screen.getByTestId("user-state-confirm-save"));
  expect(await screen.findByRole("status")).toHaveTextContent(
    "저장은 완료됐지만",
  );
  await userEvent.click(screen.getByTestId("user-state-latest"));
  expect(refresh).toHaveBeenCalledTimes(2);
  expect(api.changeBusinessUserState).toHaveBeenCalledTimes(1);
});
it("실제 접근 기록과 NULL 대체 대시를 표시하고 건수 변경은 첫 페이지를 조회한다", async () => {
  vi.mocked(api.getBusinessUserAccessLogs).mockResolvedValueOnce({
    items: [
      {
        logId: 12,
        userId: user.userId,
        actedAt: "2026-06-15 09:00",
        actionType: "USER_REG",
        screenId: "",
        targetDescription: "",
      },
    ],
    total: 1,
    page: 1,
    size: 50,
  });
  render(<UserActivityPanel id={user.userId} />);
  expect(await screen.findByText("USER_REG")).toBeInTheDocument();
  expect(screen.getAllByText("–")).toHaveLength(2);
  fireEvent.change(screen.getByLabelText("한 화면 건수"), {
    target: { value: "20" },
  });
  await waitFor(() =>
    expect(api.getBusinessUserAccessLogs).toHaveBeenLastCalledWith(
      user.userId,
      1,
      20,
      expect.any(AbortSignal),
    ),
  );
  expect(api.changeBusinessUserState).not.toHaveBeenCalled();
});
it("접근기록 로딩·빈목록·조회실패를 구별하고 읽기만 재시도한다", async () => {
  let resolve!: (value: api.UserActivities) => void;
  vi.mocked(api.getBusinessUserAccessLogs).mockReturnValueOnce(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  const { rerender } = render(<UserActivityPanel id={user.userId} />);
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  await act(async () => resolve({ items: [], total: 0, page: 1, size: 50 }));
  expect(screen.getByText("접근 기록이 없습니다.")).toBeInTheDocument();
  vi.mocked(api.getBusinessUserAccessLogs).mockRejectedValueOnce(
    new Error("실제 조회 실패"),
  );
  rerender(<UserActivityPanel id="other" />);
  expect(await screen.findByRole("alert")).toHaveTextContent("실제 조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(await screen.findByText("접근 기록이 없습니다.")).toBeInTheDocument();
  expect(api.changeBusinessUserState).not.toHaveBeenCalled();
});
it("담당파트는 API 전체 원천을 보여주고 자격 자동부여가 아님을 안내한다", async () => {
  vi.mocked(api.getBusinessUserScopes).mockResolvedValue({
    userId: user.userId,
    assignedParts: [
      { partId: "HQ", name: "본사", headOffice: true },
      { partId: "P2", name: "영업2", headOffice: false },
    ],
    assignedProjects: [],
    revision: "r",
  });
  render(<UserScopesPanel id={user.userId} />);
  expect(await screen.findByText("본사, 영업2")).toBeInTheDocument();
  expect(screen.getByText(/본사 연결만으로/)).toBeInTheDocument();
});
it("일괄 확인 취소는 미전송이고 전체 거부도 결과표로 표시한다", async () => {
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(<UserBulkStatusToolbar selected={[user]} onCommitted={refresh} />);
  await userEvent.click(screen.getByTestId("user-bulk-resume"));
  await userEvent.click(screen.getByTestId("user-bulk-confirm-cancel"));
  expect(api.saveBusinessUserStatusChanges).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("user-bulk-stop"));
  await userEvent.click(screen.getByTestId("user-bulk-confirm-save"));
  expect(
    await screen.findByRole("dialog", { name: "일괄 상태 변경 결과" }),
  ).toHaveTextContent("STALE_WRITE");
  expect(screen.getByText("적용 0 / 이미 동일 0 / 거부 1")).toBeInTheDocument();
  expect(api.saveBusinessUserStatusChanges).toHaveBeenCalledTimes(1);
  expect(api.saveBusinessUserStatusChanges).toHaveBeenCalledWith("STOP", [
    { userId: user.userId, expectedRevision: user.revision },
  ]);
});
it("선택 없을 때 일괄 명령이 비활성이고 회계 불일치는 오류다", () => {
  render(
    <>
      <UserBulkStatusToolbar selected={[]} onCommitted={vi.fn()} />
      <UserStatusResultsDialog
        result={{ ...rejected, applied: 1 }}
        onClose={vi.fn()}
      />
    </>,
  );
  expect(screen.queryByTestId("user-bulk-stop")).not.toBeInTheDocument();
  expect(screen.queryByText("선택 0건")).not.toBeInTheDocument();
  expect(screen.getByRole("alert")).toHaveTextContent("건수가 일치하지");
});
it("부분 성공 결과는 각 ID·결과·코드·이유를 보존한다", () => {
  render(
    <UserStatusResultsDialog
      result={{
        applied: 1,
        unchanged: 1,
        rejected: 1,
        items: [
          {
            userId: "a",
            outcome: "APPLIED",
            code: "APPLIED",
            reason: "저장했습니다.",
            changed: true,
            revision: "a",
          },
          {
            userId: "b",
            outcome: "UNCHANGED",
            code: "UNCHANGED",
            reason: "동일합니다.",
            changed: false,
            revision: "b",
          },
          ...rejected.items,
        ],
      }}
      onClose={vi.fn()}
    />,
  );
  expect(screen.getByText("적용 1 / 이미 동일 1 / 거부 1")).toBeInTheDocument();
  expect(screen.getByText("동일합니다.")).toBeInTheDocument();
  expect(screen.getByText("STALE_WRITE")).toBeInTheDocument();
});
it("기존 사용자 상세는 네 탭에서 API를 연결하며 독립 계정 원장을 만들지 않는다", async () => {
  render(
    <BusinessUserDetail id={user.userId} onClose={vi.fn()} onEdit={vi.fn()} />,
  );
  await screen.findByText(user.email);
  expect(screen.getAllByRole("tab")).toHaveLength(4);
  await userEvent.click(screen.getByRole("tab", { name: "접근 기록" }));
  expect(await screen.findByText("접근 기록이 없습니다.")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("tab", { name: "담당 프로젝트" }));
  expect(screen.getByTestId("existing-project-panel")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("tab", { name: "변경이력" }));
  expect(await screen.findByText("변경 이력이 없습니다.")).toBeInTheDocument();
});
it("목록 선택은 조회 조건 변경에 해제되고 과거 열설정 끝에 담당파트가 추가된다", async () => {
  render(
    <BusinessUsersScreen
      columnView={{
        order: ["userId", "name", "email", "roles", "status", "lastLoginAt"],
        hidden: ["email", "userId"],
      }}
    />,
  );
  expect(
    await screen.findByRole("button", { name: user.userId }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("columnheader", { name: "담당파트" }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("columnheader", { name: "이메일" }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByLabelText(`${user.userId} 선택`));
  expect(screen.getByTestId("user-bulk-stop")).not.toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() =>
    expect(screen.queryByTestId("user-bulk-stop")).not.toBeInTheDocument(),
  );
  expect(screen.queryByText(/실제 접근권한 적용 전인/)).not.toBeInTheDocument();
});
