import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ProjectIssuesPanel } from "./ProjectIssuesPanel";
import { ProjectStatusIssuesPanel } from "./ProjectStatusIssuesPanel";
import { ProjectIssueDialog } from "./ProjectIssueDialog";
import { ApiError } from "../../shared/api";
import * as api from "./projectIssuesApi";
import * as shared from "../shared/sharedApi";
import * as projects from "../projects/projectsApi";
vi.mock("./projectIssuesApi");
vi.mock("../shared/sharedApi");
vi.mock("../projects/projectsApi");
vi.mock("../me/usePreferences", () => ({
  usePreferences: () => ({ get: () => undefined }),
}));
const actor = { userId: "creator", userName: "최초 담당자" };
const row: api.ProjectIssue = {
  issueId: 18,
  projectId: "P-009",
  projectName: "실제 프로젝트",
  title: "일정 확인",
  importance: "HIGH",
  importanceName: "높음",
  dueDate: "2026-06-30",
  status: "OPEN",
  statusName: "열림",
  action: "진행 확인 필요",
  overdue: true,
  registeredBy: actor,
  registeredAt: "2026-06-01T09:00:00+09:00",
  modifiedBy: { userId: "other", userName: "다른 수정자" },
  modifiedAt: "2026-06-02T09:00:00+09:00",
};
const page: api.ProjectIssuesPage = {
  items: [row],
  total: 1,
  page: 1,
  size: 50,
  openCount: 1,
  baseDate: "2026-07-01",
};
const progress: api.IssueProgress = {
  projectId: row.projectId,
  year: 2026,
  progressRevision: "raw-progress",
  readOnly: false,
  readOnlyReasons: [],
  issues: [row],
};
const saved: api.IssueResult = {
  projectId: row.projectId,
  changed: true,
  message: "저장했습니다.",
  progressRevision: "after",
  progress: { ...progress, progressRevision: "after" },
  memo: null,
  issue: row,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getProjectIssues).mockResolvedValue(page);
  vi.mocked(api.getProjectStatusIssues).mockResolvedValue(page);
  vi.mocked(api.getIssueProgress).mockResolvedValue(progress);
  vi.mocked(api.createProjectIssue).mockResolvedValue(saved);
  vi.mocked(api.putProjectIssue).mockResolvedValue(saved);
  vi.mocked(shared.getPeriodContext).mockResolvedValue({
    currentDate: "2026-07-01",
    currentMonth: "2026-07",
    cashBaseMonth: "2025-01",
    timezone: "Asia/Seoul",
    monthClose: { month: "2026-07", status: "마감" },
  });
  vi.mocked(shared.getCurrentUser).mockResolvedValue({
    userId: "admin",
    name: "임시 관리자",
    roleIds: ["ADMIN"],
    developmentMode: true,
  });
  vi.mocked(shared.getSharedCodes).mockImplementation(async (group) => ({
    items: (group === "IMPR"
      ? [
          ["HIGH", "높음"],
          ["MID", "보통"],
          ["LOW", "낮음"],
          ["EXTRA", "추가중요도"],
        ]
      : [
          ["OPEN", "열림"],
          ["ACT", "조치 중"],
          ["CLOSE", "종료"],
          ["REVIEW", "검토"],
        ]
    ).map(([code, name]) => ({
      code,
      name,
      addVal1: null,
      addVal2: null,
      addVal3: null,
      codeVal: null,
      unitNm: null,
    })),
  }));
  vi.mocked(projects.getProjects).mockResolvedValue({
    items: [],
    total: 0,
    unassignedCount: 0,
    page: 1,
    size: 100,
    year: 2026,
  });
});
function draw(
  overrides: Partial<Parameters<typeof ProjectIssuesPanel>[0]> = {},
) {
  const changed = vi.fn().mockResolvedValue(undefined);
  render(
    <MemoryRouter>
      <ProjectIssuesPanel
        projectId={row.projectId}
        projectName={row.projectName}
        year={2026}
        onChanged={changed}
        {...overrides}
      />
    </MemoryRouter>,
  );
  return changed;
}
async function ready() {
  await screen.findByText(row.action!);
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "이슈 등록" }),
    ).not.toBeDisabled(),
  );
}
async function openCreate() {
  draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "이슈 등록" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
}
it("전체 이력·담당자·수정자·날짜와 서로 다른 상태 색을 표시한다", async () => {
  draw();
  await ready();
  expect(screen.getByText("최초 담당자")).toBeInTheDocument();
  expect(screen.getByText("다른 수정자")).toBeInTheDocument();
  expect(
    screen.getByText("높음", { selector: "span.sicost-badge" }),
  ).toHaveClass("sicost-tone-bad");
  expect(
    screen.getByText("기한 지남", { selector: "span.sicost-badge" }),
  ).toHaveClass("sicost-tone-bad");
  expect(screen.getByText(row.registeredAt)).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "삭제" }),
  ).not.toBeInTheDocument();
});
it("조회 실패와 빈 목록을 구별하고 다시 조회만 한다", async () => {
  vi.mocked(api.getProjectIssues).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "issues-read",
    }),
  );
  draw();
  expect(await screen.findByRole("alert")).toHaveTextContent("issues-read");
  vi.mocked(api.getProjectIssues).mockResolvedValue({
    ...page,
    items: [],
    total: 0,
  });
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("등록된 이슈가 없습니다.");
  expect(api.createProjectIssue).not.toHaveBeenCalled();
});
it("현재연도가 아니면 등록을 막고 이력은 유지한다", async () => {
  draw({ year: 2025 });
  await screen.findByText(row.action!);
  expect(screen.getByRole("button", { name: "이슈 등록" })).toBeDisabled();
  expect(screen.getByText(/이력 조회만/)).toBeInTheDocument();
});
it("종료 프로젝트는 읽기전용이고 신규 쓰기를 제공하지 않는다", async () => {
  draw({ readOnly: true });
  await screen.findByText(row.action!);
  expect(screen.getByRole("button", { name: "이슈 등록" })).toBeDisabled();
  expect(api.createProjectIssue).not.toHaveBeenCalled();
});
it("상태 필터를 서버에 전송하며 월마감은 등록을 막지 않는다", async () => {
  draw();
  await ready();
  fireEvent.change(screen.getByLabelText("상태"), { target: { value: "ALL" } });
  await userEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() =>
    expect(api.getProjectIssues).toHaveBeenLastCalledWith(
      row.projectId,
      expect.objectContaining({ status: "ALL", page: 1 }),
    ),
  );
});
it("신규 기본값은 서버 오늘+7/MID/OPEN이고 담당자는 고정이다", async () => {
  await openCreate();
  expect(screen.getByLabelText("기한 *")).toHaveValue("2026-07-08");
  expect(screen.getByLabelText("중요도 *")).toHaveValue("MID");
  expect(screen.getByLabelText("상태 *")).toHaveValue("OPEN");
  expect(screen.getByText(/임시 관리자 \(읽기 전용\)/)).toBeInTheDocument();
  expect(screen.queryByLabelText("담당자")).not.toBeInTheDocument();
});
it("등록 성공은 경로/진행토큰과 수정가능필드만 전송하고 같은 목록을 즉시 재조회한다", async () => {
  await openCreate();
  fireEvent.change(screen.getByLabelText("제목 *"), {
    target: { value: "  새 이슈  " },
  });
  fireEvent.change(screen.getByLabelText("기한 *"), {
    target: { value: "2025-01-01" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createProjectIssue).toHaveBeenCalledWith(row.projectId, {
      year: 2026,
      expectedRevision: "raw-progress",
      title: "새 이슈",
      dueDate: "2025-01-01",
      importance: "MID",
      status: "OPEN",
      action: null,
    }),
  );
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(api.getProjectIssues).toHaveBeenCalledTimes(2);
  expect(projects.notifyProjectsCommitted).toHaveBeenCalledWith(row.projectId);
});
it("신규 취소는 dirty 확인을 거쳐 쓰기 없이 닫는다", async () => {
  await openCreate();
  fireEvent.change(screen.getByLabelText("제목 *"), {
    target: { value: "초안" },
  });
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(screen.getByRole("button", { name: "계속 작성" })).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("제목 *")).toHaveValue("초안");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(api.createProjectIssue).not.toHaveBeenCalled();
});
it("수정 종료는 최초 소속과 담당자를 전송하지 않고 PUT 후 재조회한다", async () => {
  draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: row.title }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
  fireEvent.change(screen.getByLabelText("상태 *"), {
    target: { value: "CLOSE" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.putProjectIssue).toHaveBeenCalledWith(
      row.projectId,
      row.issueId,
      expect.objectContaining({
        status: "CLOSE",
        expectedRevision: "raw-progress",
      }),
    ),
  );
  const body = vi.mocked(api.putProjectIssue).mock.calls[0][2];
  expect(body).not.toHaveProperty("registeredBy");
  expect(body).not.toHaveProperty("projectId");
});
it("수정 취소·Escape는 dirty 확인 후 원래 값을 유지한다", async () => {
  draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: row.title }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
  fireEvent.change(screen.getByLabelText("제목 *"), {
    target: { value: "변경" },
  });
  await userEvent.click(screen.getByLabelText("제목 *"));
  await userEvent.keyboard("{Escape}");
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(api.putProjectIssue).not.toHaveBeenCalled();
  expect(screen.getByText(row.action!)).toBeInTheDocument();
});
it("400 필드 오류와 requestId를 보이고 초안은 유지한다", async () => {
  vi.mocked(api.createProjectIssue).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_INPUT",
      message: "등록되지 않은 중요도입니다.",
      field: "importance",
      requestId: "issue-invalid",
    }),
  );
  await openCreate();
  fireEvent.change(screen.getByLabelText("제목 *"), {
    target: { value: "초안" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(screen.getByLabelText("중요도 *")).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(screen.getByRole("alert")).toHaveTextContent("issue-invalid");
  expect(screen.getByLabelText("제목 *")).toHaveValue("초안");
});
it("409는 자동재전송 없이 최신 토큰을 수동 조회한 뒤 저장한다", async () => {
  vi.mocked(api.createProjectIssue).mockRejectedValueOnce(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다른 변경이 있습니다.",
      requestId: "issue-stale",
    }),
  );
  await openCreate();
  fireEvent.change(screen.getByLabelText("제목 *"), {
    target: { value: "초안" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/최신 자료를 다시 조회/);
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  vi.mocked(api.getIssueProgress).mockResolvedValue({
    ...progress,
    progressRevision: "latest",
  });
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
  expect(api.createProjectIssue).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createProjectIssue).toHaveBeenLastCalledWith(
      row.projectId,
      expect.objectContaining({ expectedRevision: "latest" }),
    ),
  );
});
it("저장 성공 뒤 재조회 실패는 재조회만 허용하고 중복 저장하지 않는다", async () => {
  await openCreate();
  vi.mocked(api.getProjectIssues).mockRejectedValueOnce(
    new Error("새로 조회 실패"),
  );
  fireEvent.change(screen.getByLabelText("제목 *"), {
    target: { value: "새 내용" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/저장됐지만 다시 불러오지/);
  expect(screen.getByRole("button", { name: "이슈 등록" })).toBeDisabled();
  expect(api.createProjectIssue).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "재조회만" }));
  await screen.findByText("다시 조회했습니다.");
  expect(api.createProjectIssue).toHaveBeenCalledTimes(1);
});
it("저장중 이중클릭과 취소를 막는다", async () => {
  let finish!: (v: api.IssueResult) => void;
  vi.mocked(api.createProjectIssue).mockReturnValue(
    new Promise((r) => {
      finish = r;
    }),
  );
  await openCreate();
  fireEvent.change(screen.getByLabelText("제목 *"), {
    target: { value: "초안" },
  });
  await userEvent.dblClick(screen.getByRole("button", { name: "저장" }));
  expect(api.createProjectIssue).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
  finish(saved);
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
});
it("추가 상태와 중요도 원값을 보존한다", async () => {
  const extra = {
    ...row,
    status: "REVIEW",
    statusName: "검토",
    importance: "EXTRA",
  };
  vi.mocked(api.getIssueProgress).mockResolvedValue({
    ...progress,
    issues: [extra],
  });
  render(
    <ProjectIssueDialog
      issue={extra}
      year={2026}
      baseDate={page.baseDate}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
  expect(screen.getByLabelText("상태 *")).toHaveValue("REVIEW");
  expect(screen.getByLabelText("중요도 *")).toHaveValue("EXTRA");
});
it("현황 이슈의 프로젝트 링크는 UI-01 issues 탭으로만 이동한다", async () => {
  render(
    <MemoryRouter>
      <ProjectStatusIssuesPanel />
    </MemoryRouter>,
  );
  await screen.findByText(row.action!);
  const link = screen.getByRole("link", { name: row.projectName });
  expect(link).toHaveAttribute("href", "/UI-01?projectId=P-009&tab=issues");
  await userEvent.click(link);
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.getProjectStatusIssues).toHaveBeenCalledWith(
    expect.objectContaining({ status: "OPEN_ACTIVE" }),
  );
});
it("프로젝트 변경 후 이전 프로젝트 토큰을 제출하지 않는다", async () => {
  const close = vi.fn(),
    done = vi.fn();
  vi.mocked(api.getIssueProgress).mockImplementation(async (id) => ({
    ...progress,
    projectId: id,
    progressRevision: `token-${id}`,
  }));
  render(
    <ProjectIssueDialog
      projects={[
        { id: "A", name: "첫 프로젝트" },
        { id: "B", name: "둘째 프로젝트" },
      ]}
      year={2026}
      baseDate={page.baseDate}
      host="UI-31"
      onClose={close}
      onSaved={done}
    />,
  );
  fireEvent.change(screen.getByLabelText("프로젝트 *"), {
    target: { value: "A" },
  });
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
  fireEvent.change(screen.getByLabelText("프로젝트 *"), {
    target: { value: "B" },
  });
  await waitFor(() =>
    expect(api.getIssueProgress).toHaveBeenLastCalledWith("B", 2026),
  );
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
  fireEvent.change(screen.getByLabelText("제목 *"), {
    target: { value: "새 이슈" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createProjectIssue).toHaveBeenCalledWith(
      "B",
      expect.objectContaining({ expectedRevision: "token-B" }),
    ),
  );
});
