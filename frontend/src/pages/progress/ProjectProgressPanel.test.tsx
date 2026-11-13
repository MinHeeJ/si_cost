import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ProjectProgressPanel } from "./ProjectProgressPanel";
import { ApiError } from "../../shared/api";
import * as api from "./projectProgressApi";
vi.mock("./projectProgressApi", async (original) => ({
  ...(await original<typeof import("./projectProgressApi")>()),
  getProjectProgress: vi.fn(),
  getProjectProgressHistory: vi.fn(),
  createProjectProgressSteps: vi.fn(),
  createProjectStepDatesPreview: vi.fn(),
  saveProjectStepDates: vi.fn(),
  saveProjectStepCompletion: vi.fn(),
  createProjectWeekMemo: vi.fn(),
  putProjectWeekMemo: vi.fn(),
}));
const actor = { userId: "admin", userName: "관리자" };
const step: api.ProgressStep = {
  projectId: "PROGRESS-1",
  stepSn: 1,
  stepCode: "TK_KICK",
  stepName: "착수",
  weight: "5.00",
  startDate: "2026-01-01",
  endDate: "2026-01-10",
  completedDate: null,
  status: "지연",
  registeredBy: actor,
  registeredAt: "2026-01-01T00:00:00+09:00",
  modifiedBy: null,
  modifiedAt: null,
};
const memo: api.WeekMemo = {
  projectId: "PROGRESS-1",
  memoSn: 3,
  writtenDate: "2026-06-01",
  thisWeek: "지난 작업",
  nextWeek: "계획",
  request: null,
  actualProgress: 0,
  registeredBy: { userId: "other", userName: "다른 작성자" },
  registeredAt: "2026-06-01T09:00:00+09:00",
  modifiedBy: null,
  modifiedAt: null,
};
const original: api.ProjectProgress = {
  projectId: "PROGRESS-1",
  year: 2026,
  progressRevision: "progress-token",
  readOnly: false,
  readOnlyReasons: [],
  status: {
    projectId: "PROGRESS-1",
    projectName: "진행 프로젝트",
    contractType: "TK",
    sourceStatus: "ING",
    partId: "HQ",
    partName: "본사",
    hq: true,
    pm: [actor],
    light: "주의",
    reasons: [
      {
        kind: "메모경과",
        light: "주의",
        value: "30",
        threshold: ">14",
        formula: "기준일 − MAX(WRT_DT)",
      },
    ],
    costUnknown: true,
    planProgress: "65.5",
    actualProgress: "0.0",
    costUsage: null,
    scheduleGap: "65.5",
    costProgressGap: null,
    currentStep: step,
    allStepsCompleted: false,
    stepsInitialized: true,
    planBasis: "단계 기준",
    openIssueCount: 0,
    overdueIssueCount: 0,
    lastUpdatedDate: "2026-06-01",
    elapsedDays: 30,
    updateWarning: true,
    baseDate: "2026-07-01",
    costMonth: "2026-07",
    completeness: {
      costComplete: false,
      revenueComplete: false,
      estimated: false,
      issues: [
        {
          code: "MISSING",
          message: "원가 자료 확인 필요",
          month: null,
          personId: null,
          assignmentId: null,
          metric: "cumulativeCost",
        },
      ],
      unknownProjectCount: 1,
      rangeStartMonth: "2026-01",
      rangeEndMonth: "2026-07",
      missingBeforeMonth: null,
    },
  },
  steps: [
    step,
    {
      ...step,
      stepSn: 2,
      stepName: "개발",
      startDate: "2026-01-11",
      endDate: "2026-12-31",
      weight: "95.00",
      status: "진행중",
    },
  ],
  memos: [memo],
  issues: [],
  initializationMessage: null,
  calculationNotice:
    "일정·원가 신호는 1차 경보입니다. 실제 판단은 프로젝트 원가 분석의 상세 내역을 확인하세요.",
};
function changed(progress = original): api.ProgressMutation {
  return {
    projectId: progress.projectId,
    changed: true,
    message: "저장했습니다.",
    progressRevision: "after",
    progress: { ...progress, progressRevision: "after" },
    memo: null,
    issue: null,
  };
}
function draw(
  data = original,
  props: {
    onChanged?: () => Promise<void>;
    onDirtyChange?: (value: boolean) => void;
  } = {},
) {
  vi.mocked(api.getProjectProgress).mockResolvedValue(data);
  return render(
    <ProjectProgressPanel
      projectId={data.projectId}
      year={data.year}
      {...props}
    />,
  );
}
async function openMemo() {
  await userEvent.click(
    await screen.findByRole("button", { name: "이번 주 상태 적기" }),
  );
  return screen.getByRole("dialog", { name: "이번 주 상태 적기" });
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getProjectProgressHistory).mockResolvedValue({
    projectId: original.projectId,
    items: [],
    total: 0,
    page: 1,
    size: 20,
  });
  for (const fn of [
    api.createProjectProgressSteps,
    api.saveProjectStepDates,
    api.saveProjectStepCompletion,
    api.createProjectWeekMemo,
    api.putProjectWeekMemo,
  ])
    vi.mocked(fn).mockResolvedValue(changed());
  vi.mocked(api.createProjectStepDatesPreview).mockResolvedValue({
    projectId: original.projectId,
    progressRevision: "progress-token",
    steps: original.steps,
  });
});
it("서버 계획/실적/단계/PM/메모/업데이트와 위험 종류별 공통 토큰을 표시한다", async () => {
  draw();
  await screen.findByText("65.5%");
  expect(screen.getByText("0.0%")).toBeInTheDocument();
  expect(screen.getByText("원가 미확인")).toHaveClass("sicost-tone-neutral");
  expect(screen.getByText("지연")).toHaveClass("sicost-tone-bad");
  expect(screen.getByText("진행중")).toHaveClass("sicost-tone-info");
  expect(screen.getByText(/마지막 업데이트/)).toHaveTextContent("2026-06-01");
  expect(screen.getByText(/현재 단계/)).toHaveTextContent("관리자");
  expect(screen.getByText("원가 자료 확인 필요")).toBeInTheDocument();
  expect(api.createProjectProgressSteps).not.toHaveBeenCalled();
});
it("계산 방법은 기준일과 원문 1차 경보 및 수식을 설명한다", async () => {
  draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "계산 방법" }),
  );
  expect(screen.getByTestId("progress-calculation-panel")).toHaveTextContent(
    "1차 경보",
  );
  expect(screen.getByTestId("progress-calculation-panel")).toHaveTextContent(
    "2026-07-01",
  );
  expect(screen.getByTestId("progress-calculation-panel")).toHaveTextContent(
    "P − A",
  );
});
it("빈 단계와 메모에서 GET 자동 생성 없이 초기화를 확인한다", async () => {
  draw({
    ...original,
    steps: [],
    memos: [],
    initializationMessage: "기본 단계가 없습니다.",
  });
  await userEvent.click(
    await screen.findByRole("button", { name: "기본 단계 초기화" }),
  );
  expect(
    screen.getByRole("dialog", { name: "기본 단계 준비" }),
  ).toHaveTextContent("덮어쓰지 않습니다");
  expect(api.createProjectProgressSteps).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "초기화" }));
  await waitFor(() =>
    expect(api.createProjectProgressSteps).toHaveBeenCalledWith("PROGRESS-1", {
      year: 2026,
      expectedRevision: "progress-token",
    }),
  );
});
it("초기화 취소는 요청하지 않는다", async () => {
  draw({ ...original, steps: [] });
  await userEvent.click(
    await screen.findByRole("button", { name: "기본 단계 초기화" }),
  );
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.createProjectProgressSteps).not.toHaveBeenCalled();
});
it("조회 전 실패와 수동 재조회를 처리한다", async () => {
  vi.mocked(api.getProjectProgress)
    .mockRejectedValueOnce(new Error("조회 실패"))
    .mockResolvedValue(original);
  render(<ProjectProgressPanel projectId="PROGRESS-1" year={2026} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("65.5%");
});
it("지난 연도 및 종료는 모든 쓰기 버튼을 잠근다", async () => {
  draw({
    ...original,
    readOnly: true,
    readOnlyReasons: ["종료 프로젝트는 조회만 할 수 있습니다."],
  });
  expect(
    await screen.findByRole("button", { name: "이번 주 상태 적기" }),
  ).toBeDisabled();
  expect(screen.getByRole("button", { name: "단계 계획 입력" })).toBeDisabled();
  expect(screen.getByTestId("progress-complete-1")).toBeDisabled();
  expect(screen.getByTestId("progress-memo-edit-3")).toBeDisabled();
});
it("미래 단계는 완료할 수 없고 완료 단계에는 취소 API가 없다", async () => {
  draw({
    ...original,
    steps: [
      { ...step, startDate: "2026-08-01", status: "예정" },
      { ...step, stepSn: 2, completedDate: "2026-06-01", status: "완료" },
    ],
  });
  expect(await screen.findByTestId("progress-complete-1")).toBeDisabled();
  expect(screen.queryByTestId("progress-complete-2")).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("progress-step-2")).getByText("완료"),
  ).toHaveClass("sicost-tone-ok");
});
it("완료 확인은 서버 오늘 안내와 경로 순번만 전송한다", async () => {
  draw();
  await userEvent.click(await screen.findByTestId("progress-complete-1"));
  const dialog = screen.getByRole("dialog", { name: "단계 완료 확인" });
  expect(dialog).toHaveTextContent("2026-07-01");
  expect(dialog).toHaveTextContent("자동 증가시키지 않습니다");
  await userEvent.click(within(dialog).getByRole("button", { name: "완료" }));
  await waitFor(() =>
    expect(api.saveProjectStepCompletion).toHaveBeenCalledWith(
      "PROGRESS-1",
      1,
      { year: 2026, expectedRevision: "progress-token" },
    ),
  );
});
it("단계 날짜는 중간 종료일만 편집하고 미리보기는 서버 결과를 보인다", async () => {
  draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "단계 계획 입력" }),
  );
  expect(screen.getByLabelText("개발 계획 종료일")).toBeDisabled();
  fireEvent.change(screen.getByLabelText("착수 계획 종료일"), {
    target: { value: "2026-01-12" },
  });
  await userEvent.click(screen.getByRole("button", { name: "미리보기" }));
  await screen.findByText(/미리보기 · 저장되지 않음/);
  expect(api.createProjectStepDatesPreview).toHaveBeenCalledWith("PROGRESS-1", {
    year: 2026,
    steps: [
      { stepSn: 1, endDate: "2026-01-12" },
      { stepSn: 2, endDate: "2026-12-31" },
    ],
  });
  await userEvent.click(screen.getByRole("button", { name: "전체 저장" }));
  await waitFor(() =>
    expect(api.saveProjectStepDates).toHaveBeenCalledWith(
      "PROGRESS-1",
      expect.objectContaining({ expectedRevision: "progress-token" }),
    ),
  );
});
it("완료행 날짜를 잠그며 다시 입력하면 미리보기 결과를 제거한다", async () => {
  draw({
    ...original,
    steps: [
      { ...step, completedDate: "2026-01-10", status: "완료" },
      original.steps[1],
    ],
  });
  await userEvent.click(
    await screen.findByRole("button", { name: "단계 계획 입력" }),
  );
  expect(screen.getByLabelText("착수 계획 종료일")).toBeDisabled();
});
it("미리보기 실패는 입력 유지·필드 오류 및 문의번호를 표시한다", async () => {
  vi.mocked(api.createProjectStepDatesPreview).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_INPUT",
      message: "종료일 확인",
      field: "steps",
      requestId: "dates-request",
    }),
  );
  draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "단계 계획 입력" }),
  );
  fireEvent.change(screen.getByLabelText("착수 계획 종료일"), {
    target: { value: "2026-01-12" },
  });
  await userEvent.click(screen.getByRole("button", { name: "미리보기" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("dates-request");
  expect(screen.getByLabelText("착수 계획 종료일")).toHaveValue("2026-01-12");
  expect(api.saveProjectStepDates).not.toHaveBeenCalled();
});
it("신규 실적은 빈칸이며 명시0은 정수로 서버소유값 없이 저장한다", async () => {
  draw();
  const dialog = await openMemo();
  expect(within(dialog).getByLabelText("실적 진척률")).toHaveValue("");
  fireEvent.change(within(dialog).getByLabelText("실적 진척률"), {
    target: { value: "0" },
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createProjectWeekMemo).toHaveBeenCalledWith("PROGRESS-1", {
      year: 2026,
      expectedRevision: "progress-token",
      thisWeek: null,
      nextWeek: null,
      request: null,
      actualProgress: 0,
    }),
  );
});
it("빈 메모는 저장하지 않고 입력 오류를 보여준다", async () => {
  draw();
  const dialog = await openMemo();
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  expect(
    await screen.findByText("내용 또는 허용된 실적을 하나 이상 입력해 주세요."),
  ).toBeInTheDocument();
  expect(api.createProjectWeekMemo).not.toHaveBeenCalled();
});
it.each(["1.5", "-1", "101"])("실적 %s는 보정 없이 거부한다", async (value) => {
  draw();
  const dialog = await openMemo();
  fireEvent.change(within(dialog).getByLabelText("실적 진척률"), {
    target: { value },
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  expect(
    await screen.findByText("실적은 0~100 정수로 입력해 주세요."),
  ).toBeInTheDocument();
  expect(api.createProjectWeekMemo).not.toHaveBeenCalled();
});
it.each(["MO", "DP"] as const)(
  "%s는 실적을 숨기고 NULL도 보내지 않는다",
  async (contractType) => {
    draw({ ...original, status: { ...original.status, contractType } });
    const dialog = await openMemo();
    expect(
      within(dialog).queryByLabelText("실적 진척률"),
    ).not.toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText("이번 주 한 일"), {
      target: { value: " 운영 " },
    });
    await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
    await waitFor(() =>
      expect(api.createProjectWeekMemo).toHaveBeenCalledWith("PROGRESS-1", {
        year: 2026,
        expectedRevision: "progress-token",
        thisWeek: "운영",
        nextWeek: null,
        request: null,
      }),
    );
  },
);
it("다른 작성자의 과거 메모 수정창은 등록정보를 고정하고 내용만 보낸다", async () => {
  draw();
  await userEvent.click(await screen.findByTestId("progress-memo-edit-3"));
  const dialog = screen.getByRole("dialog", { name: "주간 메모 수정" });
  expect(dialog).toHaveTextContent("다른 작성자");
  expect(dialog).toHaveTextContent("2026-06-01");
  fireEvent.change(within(dialog).getByLabelText("이번 주 한 일"), {
    target: { value: "수정" },
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.putProjectWeekMemo).toHaveBeenCalledWith("PROGRESS-1", 3, {
      year: 2026,
      expectedRevision: "progress-token",
      thisWeek: "수정",
      nextWeek: "계획",
      request: null,
      actualProgress: 0,
    }),
  );
});
it("dirty 취소에서 계속작성은 초안 유지하고 나가기는 쓰지 않는다", async () => {
  const dirty = vi.fn();
  draw(original, { onDirtyChange: dirty });
  const dialog = await openMemo();
  fireEvent.change(within(dialog).getByLabelText("이번 주 한 일"), {
    target: { value: "초안" },
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(within(dialog).getByLabelText("이번 주 한 일")).toHaveValue("초안");
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(api.createProjectWeekMemo).not.toHaveBeenCalled();
  expect(dirty).toHaveBeenLastCalledWith(false);
});
it("stale 실패는 초안/오류 유지 및 중복 저장 잠금이다", async () => {
  vi.mocked(api.createProjectWeekMemo).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다른 변경",
      requestId: "stale-request",
    }),
  );
  draw();
  const dialog = await openMemo();
  fireEvent.change(within(dialog).getByLabelText("이번 주 한 일"), {
    target: { value: "초안" },
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("stale-request");
  expect(within(dialog).getByRole("button", { name: "저장" })).toBeDisabled();
  expect(within(dialog).getByLabelText("이번 주 한 일")).toHaveValue("초안");
});
it("충돌 뒤 재조회는 초안 폐기 확인 후 GET만 실행하고 최신 revision으로 새 입력을 연다", async () => {
  vi.mocked(api.createProjectWeekMemo).mockRejectedValueOnce(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다른 변경",
      requestId: "stale-reload",
    }),
  );
  draw();
  const dialog = await openMemo();
  fireEvent.change(within(dialog).getByLabelText("이번 주 한 일"), {
    target: { value: "보존할 초안" },
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  await screen.findByTestId("progress-stale-reload");
  await userEvent.click(screen.getByTestId("progress-stale-reload"));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(within(dialog).getByLabelText("이번 주 한 일")).toHaveValue(
    "보존할 초안",
  );
  expect(api.getProjectProgress).toHaveBeenCalledTimes(1);
  vi.mocked(api.getProjectProgress).mockResolvedValue({
    ...original,
    progressRevision: "fresh-revision",
  });
  await userEvent.click(screen.getByTestId("progress-stale-reload"));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  await waitFor(() => expect(api.getProjectProgress).toHaveBeenCalledTimes(2));
  expect(api.createProjectWeekMemo).toHaveBeenCalledTimes(1);
  vi.mocked(api.createProjectWeekMemo).mockResolvedValue(changed());
  const fresh = await openMemo();
  fireEvent.change(within(fresh).getByLabelText("이번 주 한 일"), {
    target: { value: "새 입력" },
  });
  await userEvent.click(within(fresh).getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createProjectWeekMemo).toHaveBeenLastCalledWith(
      original.projectId,
      expect.objectContaining({
        expectedRevision: "fresh-revision",
        thisWeek: "새 입력",
      }),
    ),
  );
});
it("저장 중 중복 클릭을 막고 commit 후 관련 화면을 재조회한다", async () => {
  let resolve!: (value: api.ProgressMutation) => void;
  vi.mocked(api.createProjectWeekMemo).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  const onChanged = vi.fn().mockResolvedValue(undefined);
  draw(original, { onChanged });
  const dialog = await openMemo();
  fireEvent.change(within(dialog).getByLabelText("실적 진척률"), {
    target: { value: "0" },
  });
  const save = within(dialog).getByRole("button", { name: "저장" });
  fireEvent.click(save);
  fireEvent.click(save);
  expect(api.createProjectWeekMemo).toHaveBeenCalledTimes(1);
  expect(
    within(dialog).getByRole("button", { name: "처리 중…" }),
  ).toBeDisabled();
  resolve(changed());
  await screen.findByText("저장했습니다.");
  expect(onChanged).toHaveBeenCalledTimes(1);
  expect(api.getProjectProgress).toHaveBeenCalledTimes(2);
});
it("저장 후 재조회 실패는 재저장하지 않고 조회만 재시도한다", async () => {
  draw();
  const dialog = await openMemo();
  vi.mocked(api.getProjectProgress).mockRejectedValueOnce(
    new Error("재조회 실패"),
  );
  fireEvent.change(within(dialog).getByLabelText("실적 진척률"), {
    target: { value: "0" },
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  await screen.findByText(
    "저장됐지만 다시 불러오지 못했습니다. 다시 조회해 주세요.",
  );
  expect(
    screen.getByRole("button", { name: "이번 주 상태 적기" }),
  ).toBeDisabled();
  vi.mocked(api.getProjectProgress).mockResolvedValue(original);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "이번 주 상태 적기" }),
    ).toBeEnabled(),
  );
  expect(api.createProjectWeekMemo).toHaveBeenCalledTimes(1);
});
it("관련 이력은 별도 API로 실제 전후값과 서버처리자를 보인다", async () => {
  vi.mocked(api.getProjectProgressHistory).mockResolvedValue({
    projectId: original.projectId,
    items: [
      {
        historyId: "1",
        targetTable: "TB_PJ_STEP",
        targetKey: "PROGRESS-1:1",
        changeType: "UPD",
        itemName: "CMPL_DT",
        before: null,
        after: "2026-07-01",
        reason: null,
        registeredBy: actor,
        registeredAt: "2026-07-01T00:00:00+09:00",
      },
    ],
    total: 1,
    page: 1,
    size: 20,
  });
  draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "관련 변경 이력" }),
  );
  expect(await screen.findByTestId("progress-history-1")).toHaveTextContent(
    "CMPL_DT",
  );
  expect(screen.getByTestId("progress-history-1")).toHaveTextContent("관리자");
  expect(screen.getByRole("button", { name: "다음" })).toBeDisabled();
});
