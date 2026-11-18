import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { BusinessUserForm } from "./BusinessUserForm";
import * as api from "./businessUsersApi";
import * as projects from "../businessusersops/userProjectsApi";
import { getPeriodContext } from "../shared/sharedApi";
vi.mock("./businessUsersApi");
vi.mock("../businessusersops/userProjectsApi");
vi.mock("../shared/sharedApi");
const saved: api.BusinessUser = {
  userId: "qa17user",
  name: "사용자",
  email: "qa@example.test",
  roles: ["PM", "FIN"],
  roleIds: ["PM", "FIN"],
  businessRoleIds: ["PM"],
  status: "USE",
  lastLoginAt: null,
  revision: "rev",
  headOffice: true,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getManagedUserRoleOptions).mockResolvedValue([
    { roleId: "PM", name: "프로젝트 담당" },
    { roleId: "FIN", name: "재무 담당" },
    { roleId: "EXEC", name: "경영진" },
  ]);
  vi.mocked(api.createBusinessUser).mockResolvedValue({
    changed: true,
    message: "등록했습니다.",
    user: saved,
  });
  vi.mocked(api.putBusinessUser).mockResolvedValue({
    changed: true,
    message: "저장했습니다.",
    user: saved,
  });
});
async function fill(id = "qa17user") {
  await screen.findByLabelText("재무 담당");
  fireEvent.change(screen.getByLabelText("아이디"), { target: { value: id } });
  fireEvent.change(screen.getByLabelText("이름"), {
    target: { value: "사용자" },
  });
  fireEvent.change(screen.getByLabelText("이메일"), {
    target: { value: "qa@example.test" },
  });
}
it("전체 역할과 본사를 함께 등록하고 인증 발송을 주장하지 않는다", async () => {
  render(<BusinessUserForm onClose={vi.fn()} onSaved={vi.fn()} />);
  await fill();
  await userEvent.click(screen.getByLabelText("재무 담당"));
  await userEvent.selectOptions(screen.getByLabelText("담당파트"), "HQ");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(api.createBusinessUser).toHaveBeenCalledWith({
    userId: "qa17user",
    name: "사용자",
    email: "qa@example.test",
    roleIds: ["PM", "FIN"],
    headOffice: true,
    projectAssignments: [],
  });
  expect(screen.queryByText(/발송했습니다/)).not.toBeInTheDocument();
});
it("수정은 ADMIN 등 전체 역할을 보존하며 FIN을 EXEC로 바꾸고 본사만 해제한다", async () => {
  render(
    <BusinessUserForm
      user={{ ...saved, roleIds: ["ADMIN", "PM", "FIN"] }}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await screen.findByLabelText("재무 담당");
  await userEvent.click(screen.getByLabelText("재무 담당"));
  await userEvent.click(screen.getByLabelText("경영진"));
  await userEvent.selectOptions(screen.getByLabelText("담당파트"), "");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(api.putBusinessUser).toHaveBeenCalledWith("qa17user", {
    name: saved.name,
    email: saved.email,
    roleIds: ["ADMIN", "PM", "EXEC"],
    headOffice: false,
    expectedRevision: "rev",
  });
});
it.each(["Abc1", "abc", "a".repeat(21)])(
  "신규 ID 경계 %s는 입력을 유지하고 저장하지 않는다",
  async (id) => {
    render(<BusinessUserForm onClose={vi.fn()} onSaved={vi.fn()} />);
    await fill(id);
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(api.createBusinessUser).not.toHaveBeenCalled();
    expect(screen.getByLabelText("아이디")).toHaveValue(id);
  },
);
it.each(["abcd", "a".repeat(20)])("신규 ID 허용 경계 %s", async (id) => {
  render(<BusinessUserForm onClose={vi.fn()} onSaved={vi.fn()} />);
  await fill(id);
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(api.createBusinessUser).toHaveBeenCalledWith(
    expect.objectContaining({ userId: id }),
  );
});
it("기존 짧은 ID의 수정과 최종0역할 거부", async () => {
  render(
    <BusinessUserForm
      user={{ ...saved, userId: "abc", roleIds: ["PM"] }}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await screen.findByLabelText("프로젝트 담당");
  await userEvent.click(screen.getByLabelText("프로젝트 담당"));
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(api.putBusinessUser).not.toHaveBeenCalled();
  expect(screen.getByRole("alert")).toHaveTextContent("역할을 하나 이상");
  await userEvent.click(screen.getByLabelText("재무 담당"));
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(api.putBusinessUser).toHaveBeenCalledWith(
    "abc",
    expect.objectContaining({ roleIds: ["FIN"] }),
  );
});
it("신규 프로젝트 복수 선택은 기존 PM 이전 확인과 revision을 한 등록 요청에 담는다", async () => {
  vi.mocked(getPeriodContext).mockResolvedValue({
    currentDate: "2026-11-17",
    cashBaseMonth: "2026-01",
  } as Awaited<ReturnType<typeof getPeriodContext>>);
  const project = {
    id: "P1",
    name: "검증 프로젝트",
    status: "ING",
    endDate: "2027-01-01",
  } as projects.UserProjectListItem;
  vi.mocked(projects.searchUserProjects).mockResolvedValue({
    items: [project],
    total: 1,
    page: 1,
    size: 20,
    year: 2026,
    unassignedCount: 0,
  });
  vi.mocked(projects.getUserProjectPm).mockResolvedValue({
    items: [{ userId: "oldpm", name: "이전 PM", scopeSn: 1 }],
    revision: "project-rev",
  });
  render(<BusinessUserForm onClose={vi.fn()} onSaved={vi.fn()} />);
  await fill();
  await userEvent.click(
    screen.getByRole("button", { name: "담당 프로젝트 추가" }),
  );
  await userEvent.click(await screen.findByTestId("user-project-select-P1"));
  await userEvent.click(screen.getByRole("button", { name: "지정" }));
  const confirm = screen.getByRole("dialog", { name: "PM 교체 확인" });
  expect(within(confirm).getByText(/이전 PM/)).toBeInTheDocument();
  await userEvent.click(
    within(confirm).getByRole("button", { name: "교체 확인" }),
  );
  // Second choice uses another project without an existing PM.
  vi.mocked(projects.searchUserProjects).mockResolvedValue({
    items: [{ ...project, id: "P2" }],
    total: 1,
    page: 1,
    size: 20,
    year: 2026,
    unassignedCount: 1,
  });
  vi.mocked(projects.getUserProjectPm).mockResolvedValue({
    items: [],
    revision: "second-rev",
  });
  await userEvent.click(
    screen.getByRole("button", { name: "담당 프로젝트 추가" }),
  );
  await userEvent.click(await screen.findByTestId("user-project-select-P2"));
  await userEvent.click(screen.getByRole("button", { name: "지정" }));
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createBusinessUser).toHaveBeenCalledWith(
      expect.objectContaining({
        projectAssignments: [
          {
            projectId: "P1",
            expectedRevision: "project-rev",
            replaceConfirmed: true,
          },
          {
            projectId: "P2",
            expectedRevision: "second-rev",
            replaceConfirmed: false,
          },
        ],
      }),
    ),
  );
  expect(projects.assignUserProject).not.toHaveBeenCalled();
});
