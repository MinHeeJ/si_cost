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
import type { BusinessUser } from "../businessusers/businessUsersApi";
import { BusinessUserDetail } from "../businessusers/BusinessUserDetail";
import * as users from "../businessusers/businessUsersApi";
import { UserProjectsPanel } from "./UserProjectsPanel";
import * as api from "./userProjectsApi";
import * as options from "./businessUserOptionsApi";
import * as periods from "../shared/sharedApi";
vi.mock("./userProjectsApi", async (original) => ({
  ...(await original<typeof api>()),
  getBusinessUserProjects: vi.fn(),
  getUserProjectPm: vi.fn(),
  searchUserProjects: vi.fn(),
  assignUserProject: vi.fn(),
  detachUserProject: vi.fn(),
}));
vi.mock("./businessUserOptionsApi", async (original) => ({
  ...(await original<typeof options>()),
  getBusinessUserOptions: vi.fn(),
  notifyBusinessUsersCommitted: vi.fn(),
}));
vi.mock("../shared/sharedApi", async (original) => ({
  ...(await original<typeof periods>()),
  getPeriodContext: vi.fn(),
}));
vi.mock("../businessusers/businessUsersApi", async (original) => ({
  ...(await original<typeof users>()),
  getBusinessUser: vi.fn(),
  getBusinessUserHistory: vi.fn(),
}));
const user: BusinessUser = {
  userId: "targetperson",
  name: "대상 사용자",
  email: "target@example.org",
  roles: ["프로젝트 담당"],
  businessRoleIds: ["PM"],
  status: "USE",
  lastLoginAt: null,
  revision: "user-revision",
};
const project = (
  id = "P-601",
  status: api.UserProjectListItem["status"] = "END",
): api.UserProjectListItem => ({
  id,
  name: `계약 ${id}`,
  status,
  contractType: "TK",
  startDate: "2024-01-01",
  endDate: "2024-12-31",
  contractAmount: "999999999999999",
  customerName: null,
  personCount: 0,
  partName: null,
  distribution: null,
});
const legacyPm: api.UserProjectPmData = {
  items: [
    { userId: "targetperson", name: "대상 사용자", scopeSn: 1 },
    { userId: "otherperson", name: "기존 담당자", scopeSn: 8 },
  ],
  revision: "project-pm-revision",
};
const saved: api.UserProjectPmResult = {
  value: legacyPm,
  revision: "final-revision",
  changed: true,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getBusinessUserProjects).mockResolvedValue({
    userId: user.userId,
    items: [project()],
    revision: "listing-revision",
  });
  vi.mocked(api.getUserProjectPm).mockResolvedValue(legacyPm);
  vi.mocked(api.searchUserProjects).mockResolvedValue({
    items: [project("P-602", "ING")],
    total: 1,
    page: 1,
    size: 20,
    year: 2026,
    unassignedCount: 0,
  });
  vi.mocked(api.assignUserProject).mockResolvedValue(saved);
  vi.mocked(api.detachUserProject).mockResolvedValue(saved);
  vi.mocked(options.getBusinessUserOptions).mockResolvedValue({
    items: [{ userId: user.userId, name: user.name, label: user.name }],
    roleOptions: [],
  });
  vi.mocked(periods.getPeriodContext).mockResolvedValue({
    currentDate: "2026-06-15",
    currentMonth: "2026-06",
    cashBaseMonth: "2024-01",
    timezone: "Asia/Seoul",
    monthClose: { month: "2026-06", status: "WORK" },
  });
  vi.mocked(users.getBusinessUser).mockResolvedValue(user);
  vi.mocked(users.getBusinessUserHistory).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
});
async function open() {
  render(<UserProjectsPanel user={user} />);
  await screen.findByText("계약 P-601");
  await userEvent.click(
    screen.getByRole("button", { name: "담당 프로젝트 추가" }),
  );
  return screen.findByRole("dialog", { name: "담당 프로젝트 추가" });
}
it("shows ended and lost connections, every legacy PM, and distinct status tones", async () => {
  vi.mocked(api.getBusinessUserProjects).mockResolvedValue({
    userId: user.userId,
    items: [project(), project("P-603", "LOSE")],
    revision: "revision",
  });
  render(<UserProjectsPanel user={user} />);
  expect(await screen.findByText("종료")).toHaveClass("sicost-tone-lock");
  expect(screen.getByText("실주")).toHaveClass("sicost-tone-bad");
  expect(
    screen.getAllByText(
      "대상 사용자 (targetperson), 기존 담당자 (otherperson)",
    ),
  ).toHaveLength(2);
  expect(screen.getAllByRole("button", { name: "해제" })).toHaveLength(2);
});
it("simple removal uses selected project PM revision, no year and no confirmation", async () => {
  render(<UserProjectsPanel user={user} />);
  await userEvent.click(await screen.findByRole("button", { name: "해제" }));
  await waitFor(() =>
    expect(api.detachUserProject).toHaveBeenCalledWith(
      "P-601",
      "targetperson",
      "project-pm-revision",
    ),
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  await screen.findByText("저장했습니다.");
});
it.each(["STOP", "LOCK"] as const)(
  "%s user keeps removal available but cannot add",
  async (status) => {
    render(<UserProjectsPanel user={{ ...user, status }} />);
    expect(
      screen.getByRole("button", { name: "담당 프로젝트 추가" }),
    ).toBeDisabled();
    expect(await screen.findByRole("button", { name: "해제" })).toBeEnabled();
  },
);
it("missing PM role disables add, not existing connections", async () => {
  render(
    <UserProjectsPanel user={{ ...user, businessRoleIds: ["SALES_LDR"] }} />,
  );
  expect(
    screen.getByRole("button", { name: "담당 프로젝트 추가" }),
  ).toBeDisabled();
  expect(await screen.findByRole("button", { name: "해제" })).toBeEnabled();
});
it("loading is not the empty state", async () => {
  let resolve!: (data: api.UserProjectsData) => void;
  vi.mocked(api.getBusinessUserProjects).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  render(<UserProjectsPanel user={user} />);
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  expect(
    screen.queryByText("담당 프로젝트가 없습니다."),
  ).not.toBeInTheDocument();
  await act(async () =>
    resolve({ userId: user.userId, items: [], revision: "empty" }),
  );
  expect(
    await screen.findByText("담당 프로젝트가 없습니다."),
  ).toBeInTheDocument();
});
it("failed list shows request ID and only retries on demand", async () => {
  vi.mocked(api.getBusinessUserProjects).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "query-id",
    }),
  );
  render(<UserProjectsPanel user={user} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("query-id");
  expect(
    screen.queryByText("담당 프로젝트가 없습니다."),
  ).not.toBeInTheDocument();
  expect(api.getBusinessUserProjects).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("계약 P-601");
});
it("search uses approved year/status/q and never project-options purpose PM", async () => {
  const dialog = await open();
  await within(dialog).findByText("계약 P-602");
  await userEvent.selectOptions(
    within(dialog).getByLabelText("조회 연도"),
    "2025",
  );
  await userEvent.selectOptions(within(dialog).getByLabelText("상태"), "PRE");
  await userEvent.type(
    within(dialog).getByLabelText("프로젝트 검색"),
    "장기 계약",
  );
  await userEvent.click(within(dialog).getByRole("button", { name: "조회" }));
  await waitFor(() =>
    expect(api.searchUserProjects).toHaveBeenLastCalledWith(
      { year: 2025, status: "PRE", q: "장기 계약", page: 1 },
      expect.any(AbortSignal),
    ),
  );
  expect(options.getBusinessUserOptions).toHaveBeenCalledWith("PM");
});
it("other PM requires explicit replacement showing all identities before writing", async () => {
  const dialog = await open();
  await userEvent.click(
    await within(dialog).findByRole("button", { name: "선택" }),
  );
  await userEvent.click(within(dialog).getByRole("button", { name: "지정" }));
  const confirmation = screen.getByRole("dialog", { name: "PM 교체 확인" });
  expect(confirmation).toHaveTextContent("기존 담당자 (otherperson)");
  expect(confirmation).toHaveTextContent("대상 사용자 (targetperson)");
  expect(api.assignUserProject).not.toHaveBeenCalled();
  await userEvent.click(
    within(confirmation).getByRole("button", { name: "교체 확인" }),
  );
  await waitFor(() =>
    expect(api.assignUserProject).toHaveBeenCalledWith(
      "P-602",
      user.userId,
      "project-pm-revision",
      true,
    ),
  );
  expect(options.notifyBusinessUsersCommitted).toHaveBeenCalledTimes(1);
});
it("same PM can explicitly submit without replacement and does not use listing revision", async () => {
  vi.mocked(api.getUserProjectPm).mockResolvedValue({
    ...legacyPm,
    items: [legacyPm.items[0]],
  });
  const dialog = await open();
  await userEvent.click(
    await within(dialog).findByRole("button", { name: "선택" }),
  );
  await userEvent.click(within(dialog).getByRole("button", { name: "지정" }));
  await waitFor(() =>
    expect(api.assignUserProject).toHaveBeenCalledWith(
      "P-602",
      user.userId,
      "project-pm-revision",
      false,
    ),
  );
  expect(
    screen.queryByRole("dialog", { name: "PM 교체 확인" }),
  ).not.toBeInTheDocument();
});
it("inactive PM qualification prevents selection even if user role is stale", async () => {
  vi.mocked(options.getBusinessUserOptions).mockResolvedValue({
    items: [],
    roleOptions: [],
  });
  const dialog = await open();
  expect(
    await within(dialog).findByRole("button", { name: "선택" }),
  ).toBeDisabled();
  expect(within(dialog).getByRole("button", { name: "지정" })).toBeDisabled();
});
it("ended and lost candidates are never selectable in add dialog", async () => {
  vi.mocked(api.searchUserProjects).mockResolvedValue({
    items: [project()],
    total: 1,
    page: 1,
    size: 20,
    year: 2026,
    unassignedCount: 0,
  });
  const dialog = await open();
  expect(
    await within(dialog).findByText("선택할 수 있는 프로젝트가 없습니다."),
  ).toBeInTheDocument();
  expect(
    within(dialog).queryByRole("button", { name: "선택" }),
  ).not.toBeInTheDocument();
});
it("dirty search cancel offers continue writing without losing input", async () => {
  const dirty = vi.fn();
  render(<UserProjectsPanel user={user} onDirtyChange={dirty} />);
  await screen.findByText("계약 P-601");
  await userEvent.click(
    screen.getByRole("button", { name: "담당 프로젝트 추가" }),
  );
  const dialog = await screen.findByRole("dialog", {
    name: "담당 프로젝트 추가",
  });
  await userEvent.type(
    within(dialog).getByLabelText("프로젝트 검색"),
    "미저장 검색",
  );
  await waitFor(() => expect(dirty).toHaveBeenLastCalledWith(true));
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("프로젝트 검색")).toHaveValue("미저장 검색");
});
it("pending mutation prevents duplicate detach", async () => {
  let resolve!: (result: api.UserProjectPmResult) => void;
  vi.mocked(api.detachUserProject).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  render(<UserProjectsPanel user={user} />);
  const button = await screen.findByRole("button", { name: "해제" });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(api.detachUserProject).toHaveBeenCalledTimes(1);
  expect(button).toBeDisabled();
  await act(async () => resolve(saved));
});
it("commit followed by refresh failure never replays the write", async () => {
  render(<UserProjectsPanel user={user} />);
  const button = await screen.findByRole("button", { name: "해제" });
  vi.mocked(api.getBusinessUserProjects).mockRejectedValue(
    new Error("재조회 실패"),
  );
  await userEvent.click(button);
  expect(await screen.findByText(/저장은 완료됐지만/)).toBeInTheDocument();
  expect(api.detachUserProject).toHaveBeenCalledTimes(1);
});
it("existing detail basic information and history remain alongside assigned projects", async () => {
  render(
    <BusinessUserDetail id={user.userId} onClose={vi.fn()} onEdit={vi.fn()} />,
  );
  expect(await screen.findByText(user.email)).toBeInTheDocument();
  expect(await screen.findByText("계약 P-601")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("tab", { name: "변경이력" }));
  expect(await screen.findByText("변경 이력이 없습니다.")).toBeInTheDocument();
  expect(screen.getByText("계약 P-601")).toBeInTheDocument();
});
