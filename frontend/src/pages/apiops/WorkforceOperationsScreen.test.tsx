import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import {
  WorkforceOperationsScreen,
  WorkforceCalendarCell,
} from "./WorkforceOperationsScreen";
import * as api from "./workforceCalendarApi";
import * as shared from "../shared/sharedApi";

vi.mock("./workforceCalendarApi", async (original) => ({
  ...(await original<typeof import("./workforceCalendarApi")>()),
  getWorkforceOperations: vi.fn(),
  downloadWorkforceCalendar: vi.fn(),
}));
vi.mock("../shared/sharedApi", async (original) => ({
  ...(await original<typeof import("../shared/sharedApi")>()),
  getPeriodContext: vi.fn(),
  getSharedCodes: vi.fn(),
}));
vi.mock("../developers/DeveloperDrawer", () => ({
  DeveloperDrawer: ({ id }: { id: string }) => (
    <div data-testid="developer-detail">{id}</div>
  ),
}));
vi.mock("../workforceforecastsops/WorkforceDemandForecastScreen", () => ({
  WorkforceForecastPanel: ({
    query,
    onDownload,
  }: {
    query: { from: string; months: number; includeUnfilled: boolean };
    onDownload?(unit: "MILLION"): Promise<void>;
  }) => (
    <div data-testid="forecast-query">
      {JSON.stringify(query)}
      <button
        data-testid="forecast-test-download"
        onClick={() => void onDownload?.("MILLION")}
      >
        수요공급 다운로드
      </button>
    </div>
  ),
}));
const context = {
  currentDate: "2026-10-12",
  currentMonth: "2026-10",
  cashBaseMonth: "2025-01",
  timezone: "Asia/Seoul",
  monthClose: { month: "2026-10", status: "작업중" },
};
function cell(): api.CalendarCell {
  return {
    month: "2026-10",
    status: "ASSIGNED",
    actualMm: "0.50",
    assignmentCount: 1,
    overAssigned: false,
    leavingSoon: false,
    projects: [
      {
        projectId: "P-1",
        actualMm: "0.50",
        endDate: "2026-12-31",
        extensionPossibility: "H",
        preContract: false,
      },
    ],
    tentativeAssignments: [
      { id: "1", opportunityId: "O-1", startMonth: "2026-10", status: "ACT" },
    ],
    tentativeProbability: "50",
  };
}
function result(): api.CalendarPage {
  return {
    items: [
      {
        developerId: "D-1",
        name: "개발자",
        jobCode: "DEV",
        gradeCode: null,
        employeeType: "FT",
        lastWithdrawalDate: "2026-12-31",
        cells: Array.from({ length: 12 }, (_, i) => ({
          ...cell(),
          month: `2026-${String(i + 1).padStart(2, "0")}`,
          status: i < 9 ? "PAST_EMPTY" : "ASSIGNED",
          projects: i < 9 ? [] : cell().projects,
          tentativeAssignments: i < 9 ? [] : cell().tentativeAssignments,
        })),
      },
    ],
    total: 1,
    page: 1,
    size: 50,
    year: 2026,
    summary: {
      availablePeople: 0,
      remainingMm: "0.50",
      leavingSoonPeople: 1,
      highExtensionPeople: 1,
      concurrentPeople: 0,
      overAssignedPeople: 0,
      thirdMonthRemainingMm: "1.00",
    },
    monthTotals: Array.from({ length: 12 }, (_, i) => ({
      month: `2026-${String(i + 1).padStart(2, "0")}`,
      availablePeople: i < 9 ? null : 0,
      remainingMm: i < 9 ? null : "0.50",
    })),
  };
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(shared.getPeriodContext).mockResolvedValue(context);
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "DEV",
        name: "개발",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.mocked(api.getWorkforceOperations).mockResolvedValue(result());
});
function mount() {
  return render(
    <MemoryRouter>
      <WorkforceOperationsScreen />
    </MemoryRouter>,
  );
}
it("renders twelve months, authoritative cards and read-only controls", async () => {
  mount();
  await screen.findByTestId("calendar-person-D-1");
  expect(screen.getByTestId("calendar-available-card")).toHaveTextContent(
    "0.50",
  );
  expect(screen.getAllByRole("columnheader")).toHaveLength(14);
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("calendar-withdrawal-card")).toHaveAttribute(
    "href",
    expect.stringContaining("/UI-05?"),
  );
});
it("filters only on search, resets and keeps supply query independent", async () => {
  mount();
  await screen.findByTestId("calendar-person-D-1");
  await userEvent.selectOptions(screen.getByLabelText("직무"), "DEV");
  expect(api.getWorkforceOperations).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByTestId("calendar-search"));
  await waitFor(() =>
    expect(api.getWorkforceOperations).toHaveBeenLastCalledWith(
      expect.objectContaining({ jobCode: "DEV", page: 1 }),
      expect.any(AbortSignal),
    ),
  );
  await userEvent.click(screen.getByRole("tab", { name: "수요·공급" }));
  expect(screen.getByTestId("forecast-query")).toHaveTextContent(
    '{"from":"2026-11","months":6,"includeUnfilled":true}',
  );
  await userEvent.click(screen.getByRole("tab", { name: "달력" }));
  expect(screen.getByLabelText("직무")).toHaveValue("DEV");
  await userEvent.click(screen.getByTestId("calendar-reset"));
  await waitFor(() =>
    expect(api.getWorkforceOperations).toHaveBeenLastCalledWith(
      expect.objectContaining({
        jobCode: undefined,
        remainingOnly: false,
        page: 1,
      }),
      expect.any(AbortSignal),
    ),
  );
});
it("opens existing developer detail with selected actual identifier", async () => {
  mount();
  await screen.findByTestId("calendar-person-D-1");
  await userEvent.click(screen.getByRole("button", { name: "개발자" }));
  expect(screen.getByTestId("developer-detail")).toHaveTextContent("D-1");
});
it("keeps failure distinct from empty and retries", async () => {
  vi.mocked(api.getWorkforceOperations).mockRejectedValueOnce(
    new Error("연결 실패"),
  );
  mount();
  expect(await screen.findByRole("alert")).toHaveTextContent("연결 실패");
  expect(screen.queryByText("조회된 인력이 없습니다.")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByTestId("calendar-person-D-1");
});
it("renders successful empty data and no fabricated people", async () => {
  vi.mocked(api.getWorkforceOperations).mockResolvedValue({
    ...result(),
    items: [],
    total: 0,
  });
  mount();
  await screen.findByText("조회된 인력이 없습니다.");
  await userEvent.click(screen.getByRole("button", { name: "조건 초기화" }));
  await waitFor(() => expect(api.getWorkforceOperations).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, remainingOnly: false }), expect.any(AbortSignal)));
  expect(
    screen.queryByRole("button", { name: "개발자" }),
  ).not.toBeInTheDocument();
});
it("refreshes on source writes without automatic mutation", async () => {
  mount();
  await screen.findByTestId("calendar-person-D-1");
  await act(async () => {
    window.dispatchEvent(new Event("sicost:sales-committed"));
  });
  await waitFor(() =>
    expect(api.getWorkforceOperations).toHaveBeenCalledTimes(2),
  );
  expect(api.downloadWorkforceCalendar).not.toHaveBeenCalled();
});
it("downloads the applied whole filter not pending draft", async () => {
  mount();
  await screen.findByTestId("calendar-person-D-1");
  await userEvent.selectOptions(screen.getByLabelText("직무"), "DEV");
  await userEvent.click(screen.getByTestId("calendar-download"));
  await waitFor(() =>
    expect(api.downloadWorkforceCalendar).toHaveBeenCalledWith(
      expect.objectContaining({ jobCode: undefined }),
    ),
  );
});
it("downloads supply with its selected unit and no calendar filters", async () => {
  mount();
  await screen.findByTestId("calendar-person-D-1");
  await userEvent.selectOptions(screen.getByLabelText("직무"), "DEV");
  await userEvent.click(screen.getByTestId("calendar-search"));
  await userEvent.click(screen.getByRole("tab", { name: "수요·공급" }));
  await userEvent.click(screen.getByTestId("forecast-test-download"));
  expect(api.downloadWorkforceCalendar).toHaveBeenCalledWith(
    { year: 2026 },
    "SUPPLY_DEMAND",
    "MILLION",
  );
});
it("keeps rows and applied filters when download logging fails without automatic retry", async () => {
  vi.mocked(api.downloadWorkforceCalendar).mockRejectedValueOnce(
    new Error("접근기록 실패"),
  );
  mount();
  await screen.findByTestId("calendar-person-D-1");
  await userEvent.selectOptions(screen.getByLabelText("직무"), "DEV");
  await userEvent.click(screen.getByTestId("calendar-search"));
  await waitFor(() =>
    expect(api.getWorkforceOperations).toHaveBeenLastCalledWith(
      expect.objectContaining({ jobCode: "DEV" }),
      expect.any(AbortSignal),
    ),
  );
  await screen.findByTestId("calendar-person-D-1");
  await userEvent.click(screen.getByTestId("calendar-download"));
  expect(await screen.findByRole("alert")).toHaveTextContent("접근기록 실패");
  expect(screen.getByTestId("calendar-person-D-1")).toBeInTheDocument();
  expect(screen.getByLabelText("직무")).toHaveValue("DEV");
  expect(api.downloadWorkforceCalendar).toHaveBeenCalledTimes(1);
  expect(screen.queryByText("다운로드했습니다.")).not.toBeInTheDocument();
});
it("shows actual before tentative and opens full cell evidence on activation", async () => {
  const c = cell();
  render(<WorkforceCalendarCell cell={c} />);
  const button = screen.getByRole("button");
  expect(button).toHaveTextContent("투입중 0.50 MM");
  expect(button.parentElement).toHaveClass("workforce-calendar-assigned");
  expect(button.parentElement).not.toHaveClass("workforce-calendar-tentative");
  await userEvent.click(button);
  expect(screen.getByRole("note")).toHaveTextContent("P-1: 0.50 MM");
  expect(screen.getByRole("note")).toHaveTextContent("가배정 O-1");
});
it.each([
  "BEFORE_JOIN",
  "RETIRED",
  "LEAVE",
  "TENTATIVE",
  "AVAILABLE",
  "PAST_EMPTY",
] as const)("renders %s with its distinct state class", (status) => {
  const c = { ...cell(), status };
  render(<WorkforceCalendarCell cell={c} />);
  expect(screen.getByRole("button").parentElement).toHaveClass(
    `workforce-calendar-${status.toLowerCase()}`,
  );
});
it("distinguishes precontract, concurrent and over-one cues", () => {
  const c = {
    ...cell(),
    assignmentCount: 2,
    overAssigned: true,
    projects: [{ ...cell().projects[0], preContract: true }],
  };
  render(<WorkforceCalendarCell cell={c} />);
  expect(screen.getByRole("button")).toHaveTextContent("⚠ 미계약");
  expect(screen.getByRole("button")).toHaveTextContent("2건 · 1.0 초과");
});

it("name header toggles server sorting, resets the page and exports the same ordering", async () => {
  vi.mocked(api.getWorkforceOperations).mockResolvedValue({ ...result(), total: 55 });
  render(<MemoryRouter><WorkforceOperationsScreen /></MemoryRouter>);
  await screen.findByRole("button", { name: "이름 ↕" });
  await userEvent.click(screen.getByTestId("calendar-next"));
  await waitFor(() => expect(api.getWorkforceOperations).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }), expect.any(AbortSignal)));
  await userEvent.click(await screen.findByRole("button", { name: "이름 ↕" }));
  await waitFor(() => expect(api.getWorkforceOperations).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, sort: "name", direction: "ASC" }), expect.any(AbortSignal)));
  await userEvent.click(screen.getByRole("button", { name: "이름 ↑" }));
  await waitFor(() => expect(api.getWorkforceOperations).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, sort: "name", direction: "DESC" }), expect.any(AbortSignal)));
  await userEvent.click(screen.getByTestId("calendar-download"));
  expect(api.downloadWorkforceCalendar).toHaveBeenCalledWith(expect.objectContaining({ sort: "name", direction: "DESC" }));
});
