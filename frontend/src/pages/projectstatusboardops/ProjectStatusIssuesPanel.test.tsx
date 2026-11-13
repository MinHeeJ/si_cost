import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { ProjectStatusIssuesPanel } from "./ProjectStatusIssuesPanel";
import { ProjectStatusSummary } from "./ProjectStatusSummary";
import { ProjectStatusBoard } from "../apiops/ProjectStatusBoard";
import * as api from "./projectStatusApi";
import * as analysis from "../apiops/projectAnalysisApi";
import * as shared from "../shared/sharedApi";
import * as parts from "../salespartsops/partStandardsApi";
vi.mock("./projectStatusApi", async (original) => ({
  ...(await original<typeof import("./projectStatusApi")>()),
  getProjectStatusIssues: vi.fn(),
  getProjectStatusBoardSummary: vi.fn(),
}));
vi.mock("../apiops/projectAnalysisApi", async (original) => ({
  ...(await original<typeof import("../apiops/projectAnalysisApi")>()),
  getProjectStatusBoard: vi.fn(),
}));
vi.mock("../shared/sharedApi");
vi.mock("../salespartsops/partStandardsApi");
const issue: api.StatusIssue = {
  issueId: 312,
  projectId: "0001",
  projectName: "진행 프로젝트",
  title: "일정 확인",
  importance: "HIGH",
  importanceName: "높음",
  dueDate: "2026-06-30",
  status: "ACT",
  statusName: "조치 중",
  action: null,
  overdue: true,
  registeredBy: { userId: "writer", userName: "최초 담당자" },
  registeredAt: "2026-06-20T09:00:00",
  modifiedBy: null,
  modifiedAt: null,
};
const summary: api.StatusSummaryData = {
  baseDate: "2026-07-01",
  projectCount: 3,
  normalCount: 0,
  warningCount: 0,
  dangerCount: 3,
  openIssueCount: 4,
  openHighIssueCount: 2,
  overdueIssueCount: 1,
  staleProjectCount: 2,
  unknownCostProjectCount: 0,
};
const board: analysis.BoardPage = {
  baseDate: "2026-07-01",
  currentMonth: "2026-07",
  items: [],
  total: 0,
  page: 1,
  size: 50,
  summary,
  staleProjects: [],
  calculationNotice: "일정·원가 신호는 1차 경보입니다.",
};
const mount = (node: React.ReactElement) =>
  render(
    <MemoryRouter initialEntries={["/UI-31?view=issues"]}>{node}</MemoryRouter>,
  );
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getProjectStatusIssues).mockResolvedValue({
    items: [issue],
    total: 57,
    page: 1,
    size: 50,
    openCount: 4,
    baseDate: "2026-07-01",
  });
  vi.mocked(api.getProjectStatusBoardSummary).mockResolvedValue(summary);
  vi.mocked(analysis.getProjectStatusBoard).mockResolvedValue(board);
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "HIGH",
        name: "높음",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.mocked(parts.getSalesPartOptions).mockResolvedValue({ items: [] });
});
describe("현황 대상 이슈 모아 보기", () => {
  it("renders server overdue and state badges, immutable author, real project link and no export", async () => {
    mount(<ProjectStatusIssuesPanel />);
    await screen.findByRole("button", { name: "일정 확인" });
    expect(screen.getByText("높음", { selector: "span" })).toHaveClass(
      "sicost-tone-bad",
    );
    expect(screen.getByText("조치 중")).toHaveClass("sicost-tone-warn");
    expect(screen.getByText("기한 지남", { selector: "span" })).toHaveClass(
      "sicost-tone-bad",
    );
    expect(screen.getByText("최초 담당자")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "진행 프로젝트" })).toHaveAttribute(
      "href",
      expect.stringContaining("projectId=0001&tab=issues"),
    );
    expect(
      screen.queryByRole("button", { name: /엑셀/ }),
    ).not.toBeInTheDocument();
  });
  it("draft filters submit once, reset to defaults and never call summary", async () => {
    mount(<ProjectStatusIssuesPanel />);
    await screen.findByRole("button", { name: "일정 확인" });
    await screen.findByRole("option", { name: "높음" });
    fireEvent.change(screen.getByTestId("status-issue-state"), {
      target: { value: "OVERDUE" },
    });
    fireEvent.change(screen.getByTestId("status-issue-importance"), {
      target: { value: "HIGH" },
    });
    expect(api.getProjectStatusIssues).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByTestId("status-issue-search"));
    await waitFor(() =>
      expect(api.getProjectStatusIssues).toHaveBeenLastCalledWith(
        {
          status: "OVERDUE",
          importance: "HIGH",
          page: 1,
          size: 50,
          projectId: undefined,
        },
        expect.any(AbortSignal),
      ),
    );
    fireEvent.click(screen.getByTestId("status-issue-reset"));
    await waitFor(() =>
      expect(api.getProjectStatusIssues).toHaveBeenLastCalledWith(
        { status: "OPEN_ACTIVE", page: 1, size: 50 },
        expect.any(AbortSignal),
      ),
    );
    expect(api.getProjectStatusBoardSummary).not.toHaveBeenCalled();
  });
  it("row opens supplied shared edit dialog, cancel does not refresh and commit refreshes both sources", async () => {
    const changed = vi.fn();
    mount(
      <ProjectStatusIssuesPanel
        onChanged={changed}
        renderIssue={(selected, close, committed) => (
          <section role="dialog">
            {selected?.title}
            <button onClick={close}>취소</button>
            <button onClick={committed}>저장</button>
          </section>
        )}
      />,
    );
    fireEvent.click(await screen.findByRole("button", { name: "일정 확인" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("일정 확인");
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(changed).not.toHaveBeenCalled();
    expect(api.getProjectStatusIssues).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "일정 확인" }));
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() =>
      expect(api.getProjectStatusIssues).toHaveBeenCalledTimes(2),
    );
    expect(changed).toHaveBeenCalledTimes(1);
  });
  it("project link does not open edit dialog", async () => {
    const dialog = vi.fn(() => null);
    mount(<ProjectStatusIssuesPanel renderIssue={dialog} />);
    fireEvent.click(await screen.findByRole("link", { name: "진행 프로젝트" }));
    expect(dialog).not.toHaveBeenCalled();
  });
  it("closed issue is lock styled, with no client-derived overdue", async () => {
    vi.mocked(api.getProjectStatusIssues).mockResolvedValue({
      items: [
        { ...issue, status: "CLOSE", statusName: "종료", overdue: false },
      ],
      total: 1,
      page: 1,
      size: 50,
      openCount: 0,
      baseDate: "2026-07-01",
    });
    mount(<ProjectStatusIssuesPanel />);
    expect(await screen.findByText("종료", { selector: "span" })).toHaveClass(
      "sicost-tone-lock",
    );
    expect(
      screen.queryByText("기한 지남", { selector: "span" }),
    ).not.toBeInTheDocument();
  });
  it("empty response has no fabricated rows", async () => {
    vi.mocked(api.getProjectStatusIssues).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 50,
      openCount: 0,
      baseDate: "2026-07-01",
    });
    mount(<ProjectStatusIssuesPanel />);
    await screen.findByText("조건에 맞는 이슈가 없습니다.");
  });
  it("request failure retains draft and shows field/request id with retry", async () => {
    vi.mocked(api.getProjectStatusIssues).mockRejectedValueOnce(
      new ApiError(400, {
        success: false,
        meta: {},
        code: "INVALID_INPUT",
        message: "중요도를 확인하세요",
        field: "importance",
        requestId: "status-issues-test",
      }),
    );
    mount(<ProjectStatusIssuesPanel />);
    await screen.findByText(/문의 번호: status-issues-test/);
    expect(
      screen.getByText("importance: 중요도를 확인하세요"),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByRole("button", { name: "일정 확인" });
  });
  it("board hides export on issue tab and preserves whole-board summary while issue filters change", async () => {
    mount(
      <ProjectStatusBoard
        renderExport={() => <button>엑셀 다운로드</button>}
        renderIssues={(_query, changed) => (
          <ProjectStatusIssuesPanel onChanged={changed} />
        )}
      />,
    );
    await screen.findByRole("button", { name: "일정 확인" });
    expect(
      screen.queryByRole("button", { name: "엑셀 다운로드" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByTestId("analysis-summary-danger-count"),
    ).toHaveTextContent("3");
    fireEvent.change(screen.getByTestId("status-issue-state"), {
      target: { value: "CLOSED" },
    });
    fireEvent.click(screen.getByTestId("status-issue-search"));
    await waitFor(() =>
      expect(api.getProjectStatusIssues).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: "CLOSED" }),
        expect.any(AbortSignal),
      ),
    );
    expect(
      screen.getByTestId("analysis-summary-danger-count"),
    ).toHaveTextContent("3");
    fireEvent.click(screen.getByTestId("status-board-tab"));
    expect(
      screen.getByRole("button", { name: "엑셀 다운로드" }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("status-issues-tab"));
    await waitFor(() =>
      expect(screen.getByTestId("status-issue-state")).toHaveValue("CLOSED"),
    );
  });
});
it("summary calls only board filters and ignores issue/page parameters", async () => {
  mount(
    <ProjectStatusSummary
      query={{ light: "위험", status: "CLOSED", importance: "HIGH", page: 7 }}
    />,
  );
  await screen.findByTestId("analysis-summary-danger-count");
  expect(api.getProjectStatusBoardSummary).toHaveBeenCalledWith(
    { light: "위험", contractType: undefined, partId: undefined },
    expect.any(AbortSignal),
  );
  expect(
    screen.getByTestId("analysis-summary-open-issue-count"),
  ).toHaveTextContent("4");
});
