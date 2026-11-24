import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { getSharedCodes } from "../shared/sharedApi";
import { getDeveloper } from "../developers/peopleClient";
import { SharedScreenContext } from "../shared/SharedScreenContext";
import { WithdrawalContent } from "./WithdrawalListScreen";
import * as api from "./withdrawalsApi";
vi.mock("./withdrawalsApi", async (original) => ({
  ...(await original<typeof import("./withdrawalsApi")>()),
  getWithdrawals: vi.fn(),
  saveWithdrawalExtensionPossibility: vi.fn(),
  createNextPositionRecommendation: vi.fn(),
  getWithdrawalRecommendation: vi.fn(),
  selectWithdrawalPosition: vi.fn(),
  downloadWithdrawals: vi.fn(),
  withdrawalCommitted: vi.fn(),
}));
vi.mock("../shared/sharedApi");
vi.mock("../developers/peopleClient");
vi.mock("../developers/DeveloperDrawer", () => ({
  DeveloperDrawer: ({ id }: { id: string }) => (
    <div role="dialog">인력 상세 {id}</div>
  ),
}));
vi.mock("../assignmentsops/AssignmentDrawer", () => ({
  AssignmentDrawer: ({ id }: { id: string }) => (
    <div role="dialog">투입 상세 {id}</div>
  ),
}));
function row(
  id = "a-1",
  overrides: Partial<api.WithdrawalRow> = {},
): api.WithdrawalRow {
  return {
    assignmentId: id,
    developer: {
      developerId: "d-1",
      name: "철수 인력",
      managementNumber: "00012",
      jobCode: "BE",
      subJobCode: null,
      gradeCode: "MD",
      employeeTypeCode: "FT",
      detailId: "d-1",
    },
    projectId: "p-1",
    projectName: "현재 프로젝트",
    jobCode: "BE",
    withdrawalDate: "2026-11-15",
    remainingDays: 0,
    extensionCount: 2,
    earlyWithdrawal: true,
    extensionPossibility: null,
    extensionEditable: true,
    nextAssignments: [],
    recommendationCount: 2,
    canRecommend: true,
    canChangeTentative: false,
    revision: "r-source",
    ...overrides,
  };
}
function data(items = [row()]): api.WithdrawalsData {
  return {
    items,
    year: 2026,
    page: 1,
    size: 50,
    totalCount: items.length,
    serverDate: "2026-11-15",
    baseMonth: "2025-01",
    guidance: {
      visible: true,
      withinThreeMonthsPeople: 1,
      withoutNextAssignmentPeople: 1,
      withMatchingPositionsPeople: 1,
      message: "다음 배정 없는 1명 중 1명은 맞는 자리 있음",
    },
  };
}
function round(): api.NextPositionData {
  return {
    rcmdId: "12",
    revision: "r-round",
    message: "추천 완료",
    shortageMonths: [],
    candidates: [
      {
        rank: 1,
        opportunityId: "o-real",
        needSn: "2",
        jobCode: "BE",
        startMonth: "2026-12",
        totalScore: "100",
        revision: "r-card",
        targetRevision: "r-target",
        candidateRevision: "r-person",
        reason: "같은 직무",
        selection: { actionCode: null },
        scoreItems: [
          {
            itemCode: "JOB",
            baseScore: "40",
            weight: "40",
            score: "40",
            basis: "주직무 일치",
          },
        ],
        nextPosition: {
          opportunityName: "다음 기회",
          probability: "70",
          salesStepCode: "QUOTE",
          customerId: "c-1",
          customerName: "고객",
          originalStartMonth: "2026-12",
          actualStartMonth: "2026-12",
          gradeCode: "MD",
          remainingCount: 1,
          gapMonths: 0,
          waitDays: 0,
          alreadyTentativelyAssigned: false,
          reasonChips: ["공백 없음"],
        },
      },
    ],
  };
}
it.each([0, 17])(
  "renders formula-81 wait days %i without changing actual start",
  async (days) => {
    const result = round();
    result.candidates[0].nextPosition = {
      ...result.candidates[0].nextPosition!,
      originalStartMonth: days === 0 ? "2026-12" : "2027-01",
      actualStartMonth: "2027-01",
      waitDays: days,
    };
    vi.mocked(api.createNextPositionRecommendation).mockResolvedValue(result);
    mount();
    fireEvent.click(await screen.findByTestId("withdrawal-recommend-a-1"));
    const panel = await screen.findByTestId("withdrawal-next-panel");
    expect(
      await within(panel).findByText(
        days ? "대기 예상 17일" : "바로 이어서 투입",
      ),
    ).toBeVisible();
    expect(panel).toHaveTextContent("실제 예정 2027-01");
  },
);
const mount = (year = 2026) =>
  render(
    <MemoryRouter>
      <WithdrawalContent year={year} />
    </MemoryRouter>,
  );
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  vi.mocked(getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "BE",
        name: "백엔드 개발",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.mocked(getDeveloper).mockResolvedValue({ skills: ["Java"] } as Awaited<
    ReturnType<typeof getDeveloper>
  >);
  vi.mocked(api.getWithdrawals).mockResolvedValue(data());
  vi.mocked(api.createNextPositionRecommendation).mockResolvedValue(round());
  vi.mocked(api.getWithdrawalRecommendation).mockResolvedValue(round());
  vi.mocked(api.selectWithdrawalPosition).mockResolvedValue({
    changed: true,
    message: "가배정 완료",
  });
  vi.mocked(api.saveWithdrawalExtensionPossibility).mockResolvedValue({
    changed: true,
    revision: "r-new",
  });
  vi.mocked(api.downloadWithdrawals).mockResolvedValue(undefined);
});
it("keeps ASGN rows, full totals and guidance instead of merging the same developer", async () => {
  vi.mocked(api.getWithdrawals).mockResolvedValue({
    ...data([row(), row("a-2")]),
    totalCount: 55,
  });
  mount();
  await screen.findAllByText("00012");
  expect(screen.getAllByText("00012")).toHaveLength(2);
  expect(screen.getByText("총 55건")).toBeInTheDocument();
  expect(screen.getByTestId("withdrawal-guidance")).toHaveTextContent(
    "없는 1명",
  );
  expect(api.createNextPositionRecommendation).not.toHaveBeenCalled();
});
it("applies drafts only on search, distinguishes UNSET from ALL and resets filters", async () => {
  mount();
  await screen.findByText("00012");
  fireEvent.change(screen.getByTestId("withdrawal-extension-filter"), {
    target: { value: "UNSET" },
  });
  expect(api.getWithdrawals).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByTestId("withdrawal-search"));
  await waitFor(() =>
    expect(api.getWithdrawals).toHaveBeenLastCalledWith(
      expect.objectContaining({
        extension: "UNSET",
        period: "ALL",
        page: 1,
        size: 50,
      }),
    ),
  );
  fireEvent.click(screen.getByTestId("withdrawal-reset"));
  await waitFor(() =>
    expect(api.getWithdrawals).toHaveBeenLastCalledWith(
      expect.objectContaining({ extension: "ALL", job: undefined, page: 1 }),
    ),
  );
});
it("uses exact day boundary tones and completed withdrawal display", async () => {
  vi.mocked(api.getWithdrawals).mockResolvedValue(
    data(
      [0, 30, 31, 60, 61, -1].map((days, i) =>
        row(`a-${i}`, {
          remainingDays: days,
          canRecommend: false,
          extensionEditable: days >= 0,
        }),
      ),
    ),
  );
  mount();
  await screen.findByText("D-0");
  for (const d of [0, 30])
    expect(screen.getByText(`D-${d}`)).toHaveClass("sicost-tone-bad");
  for (const d of [31, 60])
    expect(screen.getByText(`D-${d}`)).toHaveClass("sicost-tone-warn");
  expect(screen.getByText("D-61")).toHaveClass("sicost-tone-neutral");
  expect(screen.getByText("철수 완료")).toHaveClass("sicost-tone-lock");
});
it("renders historical read-only rows and hides new recommendation controls", async () => {
  vi.mocked(api.getWithdrawals).mockResolvedValue({
    ...data([
      row("a-old", {
        remainingDays: -365,
        extensionEditable: false,
        canRecommend: false,
        canChangeTentative: false,
      }),
    ]),
    guidance: { ...data().guidance, visible: false },
  });
  mount(2025);
  await screen.findByText("00012");
  expect(screen.getByTestId("withdrawal-extension-a-old")).toBeDisabled();
  expect(screen.queryByTestId("withdrawal-guidance")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /^추천/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByText("지난 연도는 조회만 할 수 있습니다."),
  ).toBeInTheDocument();
});
it("respects high/later/confirmed/candidate restrictions and allows existing ACT change", async () => {
  vi.mocked(api.getWithdrawals).mockResolvedValue(
    data([
      row("a-h", { canRecommend: false, extensionPossibility: "H" }),
      row("a-later", { canRecommend: false, remainingDays: 120 }),
      row("a-confirm", {
        canRecommend: false,
        nextAssignments: [
          {
            kind: "CONFIRMED",
            assignmentId: "a-next",
            tentativeAssignmentId: null,
            projectId: "p-next",
            opportunityId: null,
            name: "확정 프로젝트",
            startDate: "2027-01-01",
            startMonth: null,
          },
        ],
      }),
      row("a-cnvt", {
        canRecommend: false,
        nextAssignments: [
          {
            kind: "CANDIDATE",
            assignmentId: null,
            tentativeAssignmentId: "12",
            projectId: "p-next",
            opportunityId: "o-next",
            name: "후보 프로젝트",
            startDate: null,
            startMonth: "2026-12",
          },
        ],
      }),
      row("a-act", {
        canRecommend: false,
        canChangeTentative: true,
        extensionPossibility: "H",
        nextAssignments: [
          {
            kind: "TENTATIVE",
            assignmentId: null,
            tentativeAssignmentId: "13",
            projectId: null,
            opportunityId: "o-next",
            name: "기존 기회",
            startDate: null,
            startMonth: "2026-12",
          },
        ],
      }),
    ]),
  );
  mount();
  await screen.findByText("확정 프로젝트");
  expect(screen.queryAllByTestId(/^withdrawal-recommend-/)).toHaveLength(0);
  expect(screen.getAllByRole("button", { name: "바꾸기" })).toHaveLength(1);
  expect(screen.getByTestId("withdrawal-change-a-act")).toBeEnabled();
});
it("shows explicit no-match state without a zero-place button", async () => {
  vi.mocked(api.getWithdrawals).mockResolvedValue(
    data([row("a-zero", { recommendationCount: 0 })]),
  );
  mount();
  expect(await screen.findByText("없음 · 맞는 자리 없음")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "추천 0곳" }),
  ).not.toBeInTheDocument();
});
it("separates row, developer, project and extension events and sends null not an empty code", async () => {
  vi.mocked(api.getWithdrawals).mockResolvedValue(
    data([row("a-1", { extensionPossibility: "M" })]),
  );
  mount();
  await screen.findByText("00012");
  fireEvent.change(screen.getByTestId("withdrawal-extension-a-1"), {
    target: { value: "" },
  });
  await waitFor(() =>
    expect(api.saveWithdrawalExtensionPossibility).toHaveBeenCalledWith(
      expect.objectContaining({ assignmentId: "a-1", revision: "r-source" }),
      2026,
      null,
    ),
  );
  expect(screen.queryByText(/투입 상세/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("withdrawal-person-a-1"));
  expect(screen.getByRole("dialog")).toHaveTextContent("인력 상세 d-1");
});
it("opens the exact assignment drawer from the row and links projects to existing detail", async () => {
  mount();
  await screen.findByText("00012");
  expect(screen.getByTestId("withdrawal-project-a-1")).toHaveAttribute(
    "href",
    "/UI-01?projectId=p-1&year=2026",
  );
  fireEvent.click(screen.getByText("2026-11-15"));
  expect(screen.getByRole("dialog")).toHaveTextContent("투입 상세 a-1");
});
it("restores the saved extension and exposes field/requestId when mutation fails", async () => {
  vi.mocked(api.saveWithdrawalExtensionPossibility).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다시 조회하세요",
      field: "expectedRevision",
      requestId: "req-1",
    }),
  );
  mount();
  await screen.findByText("00012");
  fireEvent.change(screen.getByTestId("withdrawal-extension-a-1"), {
    target: { value: "H" },
  });
  expect(await screen.findByRole("alert")).toHaveTextContent("req-1");
  expect(screen.getByTestId("withdrawal-extension-a-1")).toHaveValue("");
  expect(api.getWithdrawals).toHaveBeenCalledTimes(1);
});
it("ignores stale list responses after a new filter is applied", async () => {
  let finish!: (d: api.WithdrawalsData) => void;
  vi.mocked(api.getWithdrawals)
    .mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    )
    .mockResolvedValueOnce(data([row("a-new", { projectName: "새 결과" })]));
  mount();
  fireEvent.change(screen.getByTestId("withdrawal-period"), {
    target: { value: "PAST" },
  });
  fireEvent.click(screen.getByTestId("withdrawal-search"));
  await screen.findByText("새 결과");
  await act(async () =>
    finish(data([row("a-old", { projectName: "이전 결과" })])),
  );
  expect(screen.queryByText("이전 결과")).not.toBeInTheDocument();
});
it("provides loading, empty, and retryable failure states", async () => {
  vi.mocked(api.getWithdrawals)
    .mockRejectedValueOnce(new Error("조회 실패"))
    .mockResolvedValueOnce(data([]));
  mount();
  expect(screen.getByTestId("withdrawal-table")).toHaveAttribute(
    "aria-busy",
    "true",
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("조회 실패");
  fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(
    await screen.findByText("조건에 맞는 결과가 없습니다."),
  ).toBeInTheDocument();
});
it("guidance recommendation is GET-only and does not pick an arbitrary person", async () => {
  mount();
  await screen.findByText("00012");
  fireEvent.click(screen.getByTestId("withdrawal-guidance-recommend"));
  await waitFor(() =>
    expect(api.getWithdrawals).toHaveBeenLastCalledWith(
      expect.objectContaining({ period: "SOON", page: 1 }),
    ),
  );
  expect(api.createNextPositionRecommendation).not.toHaveBeenCalled();
});
it("opens one NEXT round, displays source context and does not post again on redraw", async () => {
  const view = mount();
  await screen.findByText("00012");
  fireEvent.click(screen.getByTestId("withdrawal-recommend-a-1"));
  expect(await screen.findByText("맞는 자리 1곳")).toBeInTheDocument();
  expect(screen.getByTestId("withdrawal-position-1")).toHaveTextContent(
    "수주 확률 70.0%",
  );
  expect(screen.getByTestId("withdrawal-position-1")).not.toHaveTextContent(
    "수주 확률 70%",
  );
  expect(screen.getByTestId("withdrawal-next-panel")).toHaveTextContent("Java");
  expect(screen.getByTestId("withdrawal-next-panel")).toHaveTextContent(
    "바로 이어서 투입",
  );
  view.rerender(
    <MemoryRouter>
      <WithdrawalContent year={2026} />
    </MemoryRouter>,
  );
  expect(api.createNextPositionRecommendation).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByTestId("withdrawal-next-rerun"));
  await waitFor(() =>
    expect(api.createNextPositionRecommendation).toHaveBeenCalledTimes(2),
  );
});
it("confirms server warning codes before retrying tentative action and refreshes using GET only", async () => {
  const warnings = [{ code: "MOVE", message: "이전 가배정이 해제됩니다." }];
  vi.mocked(api.selectWithdrawalPosition)
    .mockRejectedValueOnce(
      new ApiError(409, {
        success: false,
        meta: {},
        code: "INVALID_STATE",
        message: "확인 필요",
        requestId: "req-w",
        details: { workforceRecommendationWarnings: warnings },
      } as ConstructorParameters<typeof ApiError>[1]),
    )
    .mockResolvedValueOnce({ changed: true, message: "완료" });
  mount();
  await screen.findByText("00012");
  fireEvent.click(screen.getByTestId("withdrawal-recommend-a-1"));
  await screen.findByText("맞는 자리 1곳");
  fireEvent.click(screen.getByTestId("withdrawal-position-select-1"));
  expect(
    await screen.findByTestId("withdrawal-position-confirmation"),
  ).toHaveTextContent(warnings[0].message);
  expect(api.selectWithdrawalPosition).toHaveBeenLastCalledWith(
    "12",
    expect.objectContaining({ needSn: "2" }),
    [],
  );
  fireEvent.click(screen.getByRole("button", { name: "확인 후 가배정" }));
  await waitFor(() =>
    expect(api.selectWithdrawalPosition).toHaveBeenLastCalledWith(
      "12",
      expect.anything(),
      ["MOVE"],
    ),
  );
  await waitFor(() =>
    expect(api.getWithdrawalRecommendation).toHaveBeenCalledWith("12"),
  );
  expect(api.createNextPositionRecommendation).toHaveBeenCalledTimes(1);
});
it("keeps committed-but-refresh-failed retries GET-only", async () => {
  vi.mocked(api.getWithdrawalRecommendation)
    .mockRejectedValueOnce(new Error("읽기 실패"))
    .mockResolvedValueOnce(round());
  mount();
  await screen.findByText("00012");
  fireEvent.click(screen.getByTestId("withdrawal-recommend-a-1"));
  await screen.findByText("맞는 자리 1곳");
  fireEvent.click(screen.getByTestId("withdrawal-position-select-1"));
  expect(await screen.findByText(/저장은 완료됐지만/)).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("withdrawal-next-refresh"));
  await waitFor(() =>
    expect(api.getWithdrawalRecommendation).toHaveBeenCalledTimes(2),
  );
  expect(api.selectWithdrawalPosition).toHaveBeenCalledTimes(1);
  expect(api.createNextPositionRecommendation).toHaveBeenCalledTimes(1);
});
it("shows the shared tentative assignment message returned by the server", async () => {
  const message =
    "철수 인력님을 「다음 기회」 2번 자리(백엔드 개발)에 가배정했습니다. (이전 가배정 「이전 기회」은 풀렸습니다)";
  vi.mocked(api.selectWithdrawalPosition).mockResolvedValue({
    changed: true,
    message,
  });
  mount();
  fireEvent.click(await screen.findByTestId("withdrawal-recommend-a-1"));
  fireEvent.click(await screen.findByTestId("withdrawal-position-select-1"));
  expect(await screen.findByText(message)).toBeVisible();
  expect(api.selectWithdrawalPosition).toHaveBeenCalledOnce();
});
it("does not enable action for an already tentative card", async () => {
  const result = round();
  result.candidates[0].nextPosition!.alreadyTentativelyAssigned = true;
  vi.mocked(api.createNextPositionRecommendation).mockResolvedValue(result);
  mount();
  await screen.findByText("00012");
  fireEvent.click(screen.getByTestId("withdrawal-recommend-a-1"));
  expect(
    await screen.findByRole("button", { name: "가배정됨" }),
  ).toBeDisabled();
});
it("exports the applied filters and not an unsubmitted draft", async () => {
  mount();
  await screen.findByText("00012");
  fireEvent.change(screen.getByTestId("withdrawal-period"), {
    target: { value: "SOON" },
  });
  fireEvent.click(screen.getByTestId("withdrawal-download"));
  await waitFor(() =>
    expect(api.downloadWithdrawals).toHaveBeenCalledWith(
      expect.objectContaining({ period: "ALL", year: 2026 }),
    ),
  );
});

it("resets applied withdrawal filters from inside an empty table", async () => {
  vi.mocked(api.getWithdrawals).mockResolvedValue(data([]));
  mount();
  await screen.findByText("조건에 맞는 결과가 없습니다.");
  fireEvent.change(screen.getByTestId("withdrawal-period"), {
    target: { value: "PAST" },
  });
  fireEvent.change(screen.getByTestId("withdrawal-extension-filter"), {
    target: { value: "UNSET" },
  });
  fireEvent.click(screen.getByTestId("withdrawal-search"));
  await waitFor(() =>
    expect(api.getWithdrawals).toHaveBeenLastCalledWith(
      expect.objectContaining({ period: "PAST", extension: "UNSET" }),
    ),
  );
  const reset = within(screen.getByTestId("withdrawal-table")).getByRole(
    "button",
    { name: "조건 초기화" },
  );
  fireEvent.click(reset);
  await waitFor(() =>
    expect(api.getWithdrawals).toHaveBeenLastCalledWith(
      expect.objectContaining({ period: "ALL", extension: "ALL", page: 1 }),
    ),
  );
  expect(screen.getByTestId("withdrawal-period")).toHaveValue("ALL");
  expect(screen.getByTestId("withdrawal-extension-filter")).toHaveValue("ALL");
});

it("persists density and keeps the saved density if preference saving fails", async () => {
  const saveDense = vi
    .fn()
    .mockResolvedValueOnce(undefined)
    .mockRejectedValueOnce(new Error("밀도 저장 실패"));
  render(
    <MemoryRouter>
      <SharedScreenContext.Provider
        value={{
          menus: [],
          favorites: [],
          star: vi.fn(),
          pending: false,
          dense: false,
          saveDense,
        }}
      >
        <WithdrawalContent year={2026} />
      </SharedScreenContext.Provider>
    </MemoryRouter>,
  );
  await screen.findByText("00012");
  fireEvent.click(screen.getByTestId("withdrawal-density"));
  await waitFor(() =>
    expect(screen.getByTestId("withdrawal-density")).toHaveAttribute(
      "aria-pressed",
      "true",
    ),
  );
  expect(saveDense).toHaveBeenLastCalledWith(true);
  fireEvent.click(screen.getByTestId("withdrawal-density"));
  expect(await screen.findByRole("alert")).toHaveTextContent("밀도 저장 실패");
  expect(screen.getByTestId("withdrawal-density")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  expect(saveDense).toHaveBeenLastCalledWith(false);
});

it("a page preference failure keeps the last page size and shows an error instead of an unhandled rejection", async () => {
  const savePageSize = vi
    .fn()
    .mockRejectedValue(new Error("페이지 설정 저장 실패"));
  render(
    <MemoryRouter>
      <SharedScreenContext.Provider
        value={{
          menus: [],
          favorites: [],
          star: vi.fn(),
          pending: false,
          pageSize: 50,
          savePageSize,
        }}
      >
        <WithdrawalContent year={2026} />
      </SharedScreenContext.Provider>
    </MemoryRouter>,
  );
  await screen.findByText("00012");
  fireEvent.change(screen.getByLabelText("한 화면 건수"), {
    target: { value: "20" },
  });
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "페이지 설정 저장 실패",
  );
  expect(savePageSize).toHaveBeenCalledWith(20);
  expect(screen.getByLabelText("한 화면 건수")).toHaveValue("50");
  expect(api.getWithdrawals).toHaveBeenCalledTimes(1);
});

it("sends every header ASC/DESC to the server, resets page and exports the same sort", async () => {
  vi.mocked(api.getWithdrawals).mockResolvedValue({
    ...data(),
    totalCount: 120,
  });
  render(
    <MemoryRouter>
      <WithdrawalContent year={2026} />
    </MemoryRouter>,
  );
  const table = await screen.findByTestId("withdrawal-table");
  for (const [id, label] of [
    ["name", "이름"],
    ["project", "프로젝트"],
    ["job", "직무"],
    ["date", "철수일"],
    ["days", "남은 일수"],
    ["history", "연장·조기 철수"],
    ["extension", "연장 가능성"],
    ["next", "다음 배정"],
  ]) {
    fireEvent.click(
      within(table).getByRole("button", { name: new RegExp(`^${label}`) }),
    );
    await waitFor(() =>
      expect(api.getWithdrawals).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort: id, direction: "ASC", page: 1 }),
      ),
    );
    fireEvent.click(
      within(table).getByRole("button", { name: new RegExp(`^${label}`) }),
    );
    await waitFor(() =>
      expect(api.getWithdrawals).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort: id, direction: "DESC", page: 1 }),
      ),
    );
  }
  fireEvent.click(screen.getByTestId("withdrawal-table-next-page"));
  await waitFor(() =>
    expect(api.getWithdrawals).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 2 }),
    ),
  );
  fireEvent.click(within(table).getByRole("button", { name: /^이름/ }));
  await waitFor(() =>
    expect(api.getWithdrawals).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: "name", direction: "ASC", page: 1 }),
    ),
  );
  await waitFor(() =>
    expect(screen.getByTestId("withdrawal-download")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("withdrawal-download"));
  await waitFor(() =>
    expect(api.downloadWithdrawals).toHaveBeenCalledWith(
      expect.objectContaining({ sort: "name", direction: "ASC", page: 1 }),
    ),
  );
});
