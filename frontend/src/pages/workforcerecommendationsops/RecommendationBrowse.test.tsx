import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { RecommendationHistoryPanel } from "./RecommendationHistoryPanel";
import { WorkforceRecommendationsScreen } from "./WorkforceRecommendationsScreen";
import * as api from "./recommendationBrowseApi";
import * as shared from "../shared/sharedApi";
import { ApiError } from "../../shared/api";
vi.mock("./recommendationBrowseApi");
vi.mock("../shared/sharedApi");

const need: api.RecommendationNeed = {
  needSn: "7",
  jobCode: "BE",
  gradeCode: "SR",
  needCount: 2,
  startMonth: "2026-07",
  periodMonths: 3,
  monthlyMm: "1.00",
  activeCount: 0,
  remainingCount: 2,
  revision: "need-seven",
};
const opportunity: api.RecommendationTarget = {
  targetType: "OPPORTUNITY",
  targetId: "O-001",
  name: "진행 기회",
  contractTypeCode: "MO",
  projectIdAfterWin: "P-002",
  contractStartDate: null,
  contractEndDate: null,
  needs: [need, { ...need, needSn: "19", jobCode: "PM" }],
  defaults: {
    jobCode: "BE",
    startMonth: "2026-07",
    requestedGradeCode: "SR",
    needSn: "7",
  },
  revision: "opp-revision",
};
const project: api.RecommendationTarget = {
  ...opportunity,
  targetType: "PROJECT",
  targetId: "P-002",
  name: "진행 프로젝트",
  projectIdAfterWin: null,
  contractStartDate: "2026-08-01",
  contractEndDate: "2026-12-31",
  needs: [],
  defaults: {
    jobCode: "BE",
    startMonth: "2026-08",
    requestedGradeCode: "SR",
    needSn: null,
  },
};
const targets: api.RecommendationTargets = {
  items: [opportunity, project],
  firstTargetId: opportunity.targetId,
  serverDate: "2026-07-01",
  currentMonth: "2026-07",
  baseMonth: "2025-01",
  revision: "targets",
};
const round: api.RecommendationHistoryItem = {
  rcmdId: "12",
  recommendationType: "AI",
  targetId: "REMOVED-TARGET",
  jobCode: "OLD",
  startMonth: "2025-06",
  requestedGradeCode: "SR",
  executedAt: "2025-06-01 10:00:00",
  executedBy: "past-user",
  candidateCount: 1,
};
const snapshot: api.RecommendationSnapshot = {
  rcmdId: "12",
  recommendationType: "AI",
  appliedCondition: {
    targetType: "OPPORTUNITY",
    targetId: "REMOVED-TARGET",
    jobCode: "OLD",
    startMonth: "2025-06",
    requestedGradeCode: "SR",
    needSn: "7",
  },
  sourceAssignmentId: null,
  executedAt: round.executedAt,
  executedBy: round.executedBy,
  candidates: [
    {
      rank: 1,
      developer: {
        developerId: "D-001",
        name: "저장된 인력",
        managementNumber: "M-001",
        jobCode: "OLD",
        subJobCode: null,
        detailId: "D-001",
        gradeCode: "SR",
        employeeTypeCode: "FT",
      },
      opportunityId: "O-OLD",
      needSn: "7",
      otherTentativeAssignments: [],
      revision: "saved-row",
      targetRevision: "saved-target",
      candidateRevision: "saved-candidate",
      nextPosition: null,
      jobCode: "OLD",
      startMonth: "2025-06",
      requestedGradeCode: "SR",
      totalScore: "87.5",
      scoreItems: [
        {
          itemCode: "JOB",
          baseScore: "100",
          weight: "20",
          score: "20",
          basis: "당시 직무 일치",
        },
      ],
      scoreDetail: { v: 1 },
      reason: "저장된 추천 근거",
      selection: {
        actionCode: "TENT",
        exclusionReasonCode: null,
        actedBy: "past-actor",
        actedAt: "2025-06-01 11:00:00",
        assignmentId: null,
        tentativeAssignmentId: "42",
        finalOpportunityId: "O-OLD",
        finalNeedSn: "7",
      },
    },
  ],
  message: "저장 사본",
  revision: "saved",
};
const failed = new ApiError(500, {
  success: false,
  meta: {},
  code: "INTERNAL_ERROR",
  message: "조회 실패",
  requestId: "request-one",
});
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getWorkforceRecommendationTargets).mockResolvedValue(targets);
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: ["BE", "PM"].map((code) => ({
      code,
      name: code === "BE" ? "백엔드" : "리더",
      addVal1: null,
      addVal2: null,
      addVal3: null,
      codeVal: null,
      unitNm: null,
    })),
  });
  vi.mocked(api.getWorkforceRecommendationHistory).mockResolvedValue({
    items: [round],
    page: 1,
    size: 50,
    totalCount: 101,
  });
  vi.mocked(api.getRecommendationHistorySnapshot).mockResolvedValue(snapshot);
  vi.mocked(api.requestTargetRecommendation).mockImplementation(
    async (condition) => ({ ...snapshot, appliedCondition: condition }),
  );
});
async function loadedScreen() {
  const view = render(<WorkforceRecommendationsScreen />);
  await waitFor(() =>
    expect(screen.getByTestId("recommendation-run")).toBeEnabled(),
  );
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(1),
  );
  return view;
}
it("직무가 일치하는 자리가 없으면 첫 자리를 선택하되 추천 직무는 유지한다", async () => {
  vi.mocked(api.getWorkforceRecommendationTargets).mockResolvedValue({
    ...targets,
    items: [{ ...opportunity, needs: [need] }, project],
  });
  await loadedScreen();
  await userEvent.selectOptions(screen.getByTestId("recommendation-job"), "PM");
  expect(screen.getByTestId("recommendation-need")).toHaveValue("7");
  expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByTestId("recommendation-run"));
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenLastCalledWith(
      expect.objectContaining({ jobCode: "PM", needSn: "7" }),
      expect.any(AbortSignal),
    ),
  );
});
it("대상을 변경해도 현재 입력 직무를 새 대상의 기본값으로 덮어쓰지 않는다", async () => {
  await loadedScreen();
  await userEvent.selectOptions(screen.getByTestId("recommendation-job"), "PM");
  await userEvent.selectOptions(
    screen.getByTestId("recommendation-target"),
    "PROJECT:P-002",
  );
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenLastCalledWith(
      expect.objectContaining({
        targetId: "P-002",
        jobCode: "PM",
        needSn: null,
        startMonth: "2026-08",
      }),
      expect.any(AbortSignal),
    ),
  );
});
it("기록 열기·페이지·닫기는 GET만 사용하고 조건과 열린 화면의 대상을 유지한다", async () => {
  await loadedScreen();
  await userEvent.selectOptions(
    screen.getByLabelText("프로젝트·영업 기회 *"),
    "PROJECT:P-002",
  );
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(2),
  );
  await userEvent.click(screen.getByTestId("recommendation-history-open"));
  await screen.findByRole("button", { name: /^12$/ });
  expect(api.getWorkforceRecommendationHistory).toHaveBeenCalledWith(
    { page: 1, size: 50 },
    expect.any(AbortSignal),
  );
  await userEvent.click(screen.getByTestId("history-next-page"));
  await waitFor(() =>
    expect(api.getWorkforceRecommendationHistory).toHaveBeenLastCalledWith(
      { page: 2, size: 50 },
      expect.any(AbortSignal),
    ),
  );
  await waitFor(() =>
    expect(screen.getByTestId("history-page-size")).toBeInTheDocument(),
  );
  await userEvent.selectOptions(screen.getByTestId("history-page-size"), "20");
  await waitFor(() =>
    expect(api.getWorkforceRecommendationHistory).toHaveBeenLastCalledWith(
      { page: 1, size: 20 },
      expect.any(AbortSignal),
    ),
  );
  await userEvent.click(screen.getByTestId("recommendation-history-close"));
  expect(
    screen.queryByRole("dialog", { name: "추천 기록" }),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("recommendation-target")).toHaveValue(
    "PROJECT:P-002",
  );
  expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(2);
});
it("재진입하면 첫 대상으로 초기화하고 새로고침은 추천 회차를 만들지 않는다", async () => {
  const view = await loadedScreen();
  await userEvent.selectOptions(
    screen.getByTestId("recommendation-target"),
    "PROJECT:P-002",
  );
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(2),
  );
  await userEvent.click(screen.getByTestId("recommendation-target-refresh"));
  await waitFor(() =>
    expect(api.getWorkforceRecommendationTargets).toHaveBeenCalledTimes(2),
  );
  expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(2);
  expect(screen.getByTestId("recommendation-target")).toHaveValue(
    "PROJECT:P-002",
  );
  view.unmount();
  await loadedScreenAfterRemount();
  expect(screen.getByTestId("recommendation-target")).toHaveValue(
    "OPPORTUNITY:O-001",
  );
});
async function loadedScreenAfterRemount() {
  render(<WorkforceRecommendationsScreen />);
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(3),
  );
}
it("직무 변경은 일치 NEED_SN을 자동 선택하고 직접 자리 선택은 PK를 전송한다", async () => {
  await loadedScreen();
  expect(screen.getByTestId("recommendation-grade")).toHaveValue("SR");
  await userEvent.selectOptions(screen.getByTestId("recommendation-job"), "PM");
  expect(screen.getByTestId("recommendation-need")).toHaveValue("19");
  await userEvent.selectOptions(screen.getByTestId("recommendation-need"), "7");
  expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByTestId("recommendation-run"));
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenLastCalledWith(
      expect.objectContaining({ jobCode: "PM", needSn: "7" }),
      expect.any(AbortSignal),
    ),
  );
});
it("프로젝트 시작 이전월은 계약 시작월로 올리고 기회 전용 자리는 숨긴다", async () => {
  await loadedScreen();
  await userEvent.selectOptions(
    screen.getByTestId("recommendation-target"),
    "PROJECT:P-002",
  );
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenLastCalledWith(
      expect.objectContaining({
        targetId: "P-002",
        startMonth: "2026-08",
        needSn: null,
      }),
      expect.any(AbortSignal),
    ),
  );
  expect(screen.queryByTestId("recommendation-need")).not.toBeInTheDocument();
});
it("프로젝트 종료월 뒤는 필드 오류이며 요청하지 않는다", async () => {
  await loadedScreen();
  await userEvent.selectOptions(
    screen.getByTestId("recommendation-target"),
    "PROJECT:P-002",
  );
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(2),
  );
  fireEvent.change(screen.getByTestId("recommendation-start-month"), {
    target: { value: "2027-01" },
  });
  fireEvent.submit(screen.getByTestId("recommendation-run").closest("form")!);
  expect(
    await screen.findByText("프로젝트 계약 종료월을 넘을 수 없습니다."),
  ).toBeVisible();
  expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(2);
});
it("전환 실패는 이전 결과의 행동을 막고 명시 재시도만 한다", async () => {
  render(
    <WorkforceRecommendationsScreen
      renderRecommendation={(_value, _need, canAct) => (
        <button disabled={!canAct}>후보 행동</button>
      )}
    />,
  );
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "후보 행동" })).toBeEnabled(),
  );
  vi.mocked(api.requestTargetRecommendation).mockRejectedValueOnce(failed);
  await userEvent.selectOptions(
    screen.getByTestId("recommendation-target"),
    "PROJECT:P-002",
  );
  expect(await screen.findByText("조회 실패")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "후보 행동" }),
  ).not.toBeInTheDocument();
  expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(2);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(3),
  );
});
it("대상 목록 조회 실패는 기존 카드 행동을 잠그고 GET 재조회만 한다", async () => {
  render(
    <WorkforceRecommendationsScreen
      renderRecommendation={(_value, _need, canAct) => (
        <button disabled={!canAct}>후보 행동</button>
      )}
    />,
  );
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "후보 행동" })).toBeEnabled(),
  );
  vi.mocked(api.getWorkforceRecommendationTargets).mockRejectedValueOnce(
    failed,
  );
  await userEvent.click(screen.getByTestId("recommendation-target-refresh"));
  await screen.findByText("조회 실패");
  expect(screen.getByRole("button", { name: "후보 행동" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "후보 행동" })).toBeEnabled(),
  );
  expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(1);
});
it("닫힌 대상은 수주 후 진행 프로젝트로 전환하되 목록 갱신으로 POST하지 않는다", async () => {
  await loadedScreen();
  vi.mocked(api.getWorkforceRecommendationTargets).mockResolvedValue({
    ...targets,
    items: [project],
    firstTargetId: project.targetId,
  });
  await userEvent.click(screen.getByTestId("recommendation-target-refresh"));
  await waitFor(() =>
    expect(screen.getByTestId("recommendation-target")).toHaveValue(
      "PROJECT:P-002",
    ),
  );
  expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(1);
  expect(
    screen.queryByTestId("recommendation-results"),
  ).not.toBeInTheDocument();
});
it("진행 대상 없음은 빈 안내와 기록 접근을 제공하고 추천 POST가 없다", async () => {
  vi.mocked(api.getWorkforceRecommendationTargets).mockResolvedValue({
    ...targets,
    items: [],
    firstTargetId: null,
  });
  render(<WorkforceRecommendationsScreen />);
  await screen.findByText("진행 중인 추천 대상이 없습니다.");
  expect(screen.queryByTestId("recommendation-run")).not.toBeInTheDocument();
  expect(api.requestTargetRecommendation).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("recommendation-history-open"));
  await screen.findByRole("dialog", { name: "추천 기록" });
});
it("기록 필터는 삭제된 대상 ID와 직무도 조회하며 초안 변경만으로 재조회하지 않는다", async () => {
  render(<RecommendationHistoryPanel onClose={vi.fn()} />);
  await screen.findByRole("button", { name: /^12$/ });
  await userEvent.selectOptions(
    screen.getByTestId("history-target-type"),
    "PROJECT",
  );
  await userEvent.type(
    screen.getByTestId("history-target-id"),
    "REMOVED-TARGET",
  );
  await userEvent.type(screen.getByTestId("history-job"), "OLD");
  expect(api.getWorkforceRecommendationHistory).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByTestId("history-search"));
  await waitFor(() =>
    expect(api.getWorkforceRecommendationHistory).toHaveBeenLastCalledWith(
      {
        page: 1,
        size: 50,
        targetType: "PROJECT",
        targetId: "REMOVED-TARGET",
        job: "OLD",
      },
      expect.any(AbortSignal),
    ),
  );
});
it("대상 필터 짝 누락은 필드 오류이고 서버 요청이 없다", async () => {
  render(<RecommendationHistoryPanel onClose={vi.fn()} />);
  await screen.findByRole("button", { name: /^12$/ });
  await userEvent.type(screen.getByTestId("history-target-id"), "P-001");
  await userEvent.click(screen.getByTestId("history-search"));
  expect(screen.getByRole("alert")).toHaveTextContent(
    "대상 유형과 대상 ID를 함께 지정해 주세요.",
  );
  expect(api.getWorkforceRecommendationHistory).toHaveBeenCalledTimes(1);
});
it("저장 회차 상세는 당시 점수·배점·조건·선택·사용자만 읽기 전용으로 보인다", async () => {
  render(<RecommendationHistoryPanel onClose={vi.fn()} />);
  await userEvent.click(await screen.findByRole("button", { name: /^12$/ }));
  const saved = await screen.findByTestId("recommendation-saved-values");
  expect(saved).toHaveTextContent("87.5");
  expect(saved).toHaveTextContent("당시 배점");
  expect(saved).toHaveTextContent("OLD");
  expect(saved).toHaveTextContent("past-actor");
  expect(saved).toHaveTextContent("42");
  expect(within(saved).queryByRole("button")).not.toBeInTheDocument();
  expect(api.getRecommendationHistorySnapshot).toHaveBeenCalledWith(
    "12",
    expect.any(AbortSignal),
  );
  expect(api.requestTargetRecommendation).not.toHaveBeenCalled();
  expect(shared.getSharedCodes).toHaveBeenCalledWith("JOB", true);
});
it("기록 빈 목록은 별도 안내이며 이전·다음이 비활성이다", async () => {
  vi.mocked(api.getWorkforceRecommendationHistory).mockResolvedValue({
    items: [],
    page: 1,
    size: 50,
    totalCount: 0,
  });
  render(<RecommendationHistoryPanel onClose={vi.fn()} />);
  await screen.findByText("추천 기록이 없습니다.");
  expect(screen.getByTestId("history-next-page")).toBeDisabled();
  expect(screen.getByTestId("history-previous-page")).toBeDisabled();
});
it("기록 오류는 문의번호·재조회이고 재시도도 GET만 한다", async () => {
  vi.mocked(api.getWorkforceRecommendationHistory).mockRejectedValueOnce(
    failed,
  );
  render(<RecommendationHistoryPanel onClose={vi.fn()} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("request-one");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByRole("button", { name: /^12$/ });
  expect(api.getWorkforceRecommendationHistory).toHaveBeenCalledTimes(2);
  expect(api.requestTargetRecommendation).not.toHaveBeenCalled();
});
it("상세 실패는 옛 상세를 표시하지 않고 상세 GET만 재시도한다", async () => {
  vi.mocked(api.getRecommendationHistorySnapshot).mockRejectedValueOnce(failed);
  render(<RecommendationHistoryPanel onClose={vi.fn()} />);
  await userEvent.click(await screen.findByRole("button", { name: /^12$/ }));
  await screen.findByText("조회 실패");
  expect(
    screen.queryByTestId("recommendation-saved-values"),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByTestId("recommendation-saved-values");
  expect(api.getRecommendationHistorySnapshot).toHaveBeenCalledTimes(2);
  expect(api.requestTargetRecommendation).not.toHaveBeenCalled();
});
it("Escape로 닫기는 쓰기나 추가 조회 없이 호출자를 알린다", async () => {
  const close = vi.fn();
  render(<RecommendationHistoryPanel onClose={close} />);
  const dialog = screen.getByRole("dialog", { name: "추천 기록" });
  fireEvent.keyDown(dialog, { key: "Escape" });
  expect(close).toHaveBeenCalledOnce();
  expect(api.requestTargetRecommendation).not.toHaveBeenCalled();
});
it("로딩 상태는 기록 조회 중을 표시하고 서버 응답 전 행동을 노출하지 않는다", async () => {
  vi.mocked(api.getWorkforceRecommendationHistory).mockReturnValue(
    new Promise(() => undefined),
  );
  render(<RecommendationHistoryPanel onClose={vi.fn()} />);
  expect(screen.getByRole("status")).toHaveTextContent("기록 조회 중");
  expect(screen.getByTestId("history-search")).toBeDisabled();
  expect(screen.queryByTestId("history-next-page")).not.toBeInTheDocument();
});

it("빈 추천에서도 서버의 제외 집계와 겹치는 개인 사유를 표시한다", async () => {
  vi.mocked(api.requestTargetRecommendation).mockResolvedValue({
    ...snapshot,
    rcmdId: null,
    candidates: [],
    exclusions: {
      totalCandidateCount: 3,
      excludedUniqueCount: 2,
      reasonCountsOverlap: true,
      byReason: [
        { reason: "휴직", count: 2 },
        { reason: "퇴사 또는 퇴사 예정", count: 1 },
      ],
      diagnostics: [
        { developerId: "D-002", reasons: ["휴직", "퇴사 또는 퇴사 예정"] },
      ],
    },
  });
  render(<WorkforceRecommendationsScreen />);
  const diagnostics = await screen.findByTestId("recommendation-exclusions");
  const summary = within(diagnostics).getByLabelText("후보 검토 요약");
  for (const [label, count] of [
    ["전체 검토", "3명"],
    ["채점 대상", "1명"],
    ["제외 인원", "2명"],
  ]) {
    expect(within(summary).getByText(label).parentElement).toHaveTextContent(count);
  }
  const reasons = within(diagnostics).getByRole("list", { name: "제외 사유별 인원" });
  expect(
    within(reasons).getByText("휴직").closest("li"),
  ).toHaveTextContent("2명");
  expect(
    within(reasons).getByText("퇴사 또는 퇴사 예정").closest("li"),
  ).toHaveTextContent("1명");
  expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  await userEvent.click(
    within(diagnostics).getByRole("button", { name: "제외 사유별 인원 도움말" }),
  );
  expect(screen.getByRole("tooltip")).toHaveTextContent(/제외 인원에는 한 번만 셉니다/);
  await userEvent.keyboard("{Escape}");
  const detail = within(diagnostics).getByText("개인별 제외 사유", { selector: "summary" });
  expect(detail.parentElement).not.toHaveAttribute("open");
  await userEvent.click(detail);
  const individual = within(diagnostics).getByRole("table", { name: "개인별 제외 사유" });
  expect(
    within(individual).getByRole("row", { name: "D-002 휴직 · 퇴사 또는 퇴사 예정" }),
  ).toBeVisible();
  expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(1);
});

it("후보를 다시 읽어도 추천 실행 당시 제외 진단을 유지한다", async () => {
  vi.mocked(api.requestTargetRecommendation).mockImplementation(async (condition) => ({
    ...snapshot,
    appliedCondition: condition,
    exclusions: {
      totalCandidateCount: 17,
      excludedUniqueCount: 12,
      reasonCountsOverlap: true,
      byReason: [{ reason: "휴직", count: 12 }],
      diagnostics: [],
    },
  }));
  vi.mocked(api.getRecommendationHistorySnapshot).mockResolvedValue({
    ...snapshot,
    message: "후보 조회 완료",
    exclusions: {
      totalCandidateCount: 1,
      excludedUniqueCount: 0,
      reasonCountsOverlap: true,
      byReason: [],
      diagnostics: [],
    },
  });
  render(
    <WorkforceRecommendationsScreen
      renderRecommendation={(value, _need, _canAct, { onRefresh }) => (
        <button type="button" onClick={() => void onRefresh()}>
          {value.message === "후보 조회 완료" ? value.message : "후보 새로고침"}
        </button>
      )}
    />,
  );
  await userEvent.click(await screen.findByRole("button", { name: "후보 새로고침" }));
  await screen.findByRole("button", { name: "후보 조회 완료" });
  const summary = screen.getByLabelText("후보 검토 요약");
  expect(within(summary).getByText("전체 검토").parentElement).toHaveTextContent("17명");
  expect(within(summary).getByText("제외 인원").parentElement).toHaveTextContent("12명");
});

it("기록의 빈 결과 안에서 조건 초기화하면 필터와 오류를 지우고 전체를 다시 조회한다", async () => {
  vi.mocked(api.getWorkforceRecommendationHistory).mockResolvedValue({
    items: [],
    page: 1,
    size: 50,
    totalCount: 0,
  });
  render(<RecommendationHistoryPanel onClose={vi.fn()} />);
  await screen.findByText("추천 기록이 없습니다.");
  await userEvent.type(screen.getByTestId("history-target-id"), "REMOVED");
  await userEvent.click(screen.getByTestId("history-search"));
  expect(screen.getByRole("alert")).toHaveTextContent("대상 유형과 대상 ID");
  await userEvent.click(screen.getByRole("button", { name: "조건 초기화" }));
  await waitFor(() =>
    expect(api.getWorkforceRecommendationHistory).toHaveBeenLastCalledWith(
      { page: 1, size: 50 },
      expect.any(AbortSignal),
    ),
  );
  expect(screen.getByTestId("history-target-id")).toHaveValue("");
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.getByTestId("history-filters")).toHaveClass("sicost-filterbar");
  expect(screen.getByTestId("history-search")).toHaveClass("sicost-primary");
});

it("history headers request every ASC/DESC server sort and return to page one", async () => {
  vi.mocked(api.getWorkforceRecommendationHistory).mockResolvedValue({
    items: [round],
    page: 1,
    size: 50,
    totalCount: 120,
  });
  render(<RecommendationHistoryPanel onClose={() => undefined} />);
  await screen.findByTestId("history-round-12");
  for (const [id, label] of [
    ["rcmdId", "회차"],
    ["recommendationType", "유형"],
    ["targetId", "대상"],
    ["jobCode", "직무"],
    ["startMonth", "시작월"],
    ["requestedGradeCode", "요청등급"],
    ["executedAt", "실행시각"],
    ["executedBy", "사용자"],
    ["candidateCount", "후보 수"],
  ]) {
    for (const direction of ["ASC", "DESC"]) {
      fireEvent.click(
        screen.getByRole("button", { name: new RegExp(`^${label}( |$)`) }),
      );
      await waitFor(() =>
        expect(api.getWorkforceRecommendationHistory).toHaveBeenLastCalledWith(
          expect.objectContaining({ sort: id, direction, page: 1 }),
          expect.any(AbortSignal),
        ),
      );
      await screen.findByTestId("history-round-12");
    }
  }
  fireEvent.click(screen.getByTestId("history-next-page"));
  await waitFor(() =>
    expect(api.getWorkforceRecommendationHistory).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 2 }),
      expect.any(AbortSignal),
    ),
  );
  await screen.findByTestId("history-round-12");
  fireEvent.click(screen.getByRole("button", { name: /^회차/ }));
  await waitFor(() =>
    expect(api.getWorkforceRecommendationHistory).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, sort: "rcmdId", direction: "ASC" }),
      expect.any(AbortSignal),
    ),
  );
});
