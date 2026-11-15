import { StrictMode } from "react";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import {
  WorkforceRecommendationsScreen,
  RecommendationCards,
} from "./WorkforceRecommendationsScreen";
import * as api from "./workforceRecommendationsApi";
import * as shared from "../shared/sharedApi";
import { ApiError } from "../../shared/api";
vi.mock("./workforceRecommendationsApi");
vi.mock("../shared/sharedApi");
vi.mock("../developers/DeveloperDrawer", () => ({
  DeveloperDrawer: ({ id }: { id: string }) => <div>인력 상세 {id}</div>,
}));
vi.mock("../assignmentsops/AssignmentCreateForm", () => ({
  AssignmentCreateForm: ({
    initialValues,
  }: {
    initialValues: { developerId: string; projectId: string; jobCode: string };
  }) => (
    <div data-testid="assignment-create-form">
      신규 배정 {initialValues.developerId} {initialValues.projectId}{" "}
      {initialValues.jobCode}
    </div>
  ),
}));
const condition: api.Condition = {
  targetType: "OPPORTUNITY",
  targetId: "O-1",
  jobCode: "BE",
  startMonth: "2026-07",
  requestedGradeCode: "MD",
  needSn: "7",
};
const selection: api.Selection = {
  actionCode: null,
  exclusionReasonCode: null,
  actedBy: null,
  actedAt: null,
  assignmentId: null,
  tentativeAssignmentId: null,
  finalOpportunityId: null,
  finalNeedSn: null,
};
function candidate(): api.Candidate {
  return {
    rank: 1,
    developer: {
      developerId: "D-1",
      name: "검토 인력",
      managementNumber: "001",
      jobCode: "BE",
      subJobCode: null,
      gradeCode: "MD",
      employeeTypeCode: "FT",
      detailId: "D-1",
    },
    opportunityId: "O-1",
    needSn: "7",
    jobCode: "BE",
    startMonth: "2026-07",
    requestedGradeCode: "MD",
    totalScore: "85.5",
    scoreItems: [
      {
        itemCode: "JOB",
        baseScore: "25",
        weight: "25",
        score: "25",
        basis: "주직무 일치",
      },
      {
        itemCode: "COST",
        baseScore: "0",
        weight: "0",
        score: "0",
        basis: "개인 인건비 미확인",
      },
    ],
    scoreDetail: {},
    reason: "주직무 일치로 추천했습니다.",
    otherTentativeAssignments: [],
    selection: { ...selection },
    revision: "row",
    targetRevision: "target",
    candidateRevision: "person",
    nextPosition: null,
  };
}
function result(c = condition): api.Data {
  return {
    rcmdId: "12",
    recommendationType: "AI",
    appliedCondition: c,
    sourceAssignmentId: null,
    executedAt: "2026-07-01 00:00:00",
    executedBy: "admin",
    candidates: [candidate()],
    exclusions: {
      totalCandidateCount: 2,
      excludedUniqueCount: 1,
      byReason: [
        { reason: "휴직", count: 1 },
        { reason: "퇴사 예정", count: 1 },
      ],
      diagnostics: [{ developerId: "D-2", reasons: ["휴직", "퇴사 예정"] }],
      reasonCountsOverlap: true,
    },
    message: "추천 참고",
    revision: "round",
    shortageMonths: [],
  };
}
function target(id = "O-1"): api.Target {
  return {
    targetType: "OPPORTUNITY",
    targetId: id,
    name: id === "O-1" ? "첫 기회" : "두 번째 기회",
    contractTypeCode: null,
    projectIdAfterWin: null,
    contractStartDate: null,
    contractEndDate: null,
    needs: [
      {
        needSn: "7",
        jobCode: "BE",
        gradeCode: "MD",
        needCount: 1,
        startMonth: "2026-07",
        periodMonths: 3,
        monthlyMm: "1",
        activeCount: 0,
        remainingCount: 1,
        revision: "need",
      },
      {
        needSn: "9",
        jobCode: "FE",
        gradeCode: "SR",
        needCount: 2,
        startMonth: "2026-08",
        periodMonths: 3,
        monthlyMm: "0.5",
        activeCount: 0,
        remainingCount: 2,
        revision: "need-two",
      },
    ],
    defaults: {
      jobCode: "BE",
      startMonth: "2026-07",
      requestedGradeCode: "MD",
      needSn: "7",
    },
    revision: "target",
  };
}
function targets(): api.Targets {
  return {
    items: [target(), target("O-2")],
    firstTargetId: "O-1",
    serverDate: "2026-07-01",
    currentMonth: "2026-07",
    baseMonth: "2025-01",
    revision: "targets",
  };
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getTargets).mockResolvedValue(targets());
  vi.mocked(api.createRecommendation).mockImplementation(async (c) =>
    result(c),
  );
  vi.mocked(api.getRecommendation).mockResolvedValue(result());
  vi.mocked(shared.getSharedCodes).mockImplementation(async (group) => ({
    items: (group === "JOB"
      ? [
          ["BE", "백엔드"],
          ["FE", "프론트"],
        ]
      : group === "GRADE"
        ? [
            ["MD", "중급"],
            ["SR", "고급"],
          ]
        : [["SCHD", "일정이 맞지 않음"]]
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
});
async function open() {
  const view = render(<WorkforceRecommendationsScreen />);
  await screen.findByText("검토 인력");
  return view;
}
it("최초 유효 조건으로 한 회 실행하고 재렌더는 회차를 만들지 않는다", async () => {
  const v = await open();
  expect(api.createRecommendation).toHaveBeenCalledTimes(1);
  v.rerender(<WorkforceRecommendationsScreen />);
  expect(api.createRecommendation).toHaveBeenCalledTimes(1);
  expect(api.createRecommendation).toHaveBeenCalledWith(condition);
});
it("StrictMode에서도 최초 회차는 한 번이다", async () => {
  render(
    <StrictMode>
      <WorkforceRecommendationsScreen />
    </StrictMode>,
  );
  await screen.findByText("검토 인력");
  expect(api.createRecommendation).toHaveBeenCalledTimes(1);
});
it("대상 0은 실행하지 않고 빈 안내와 비활성 실행이다", async () => {
  vi.mocked(api.getTargets).mockResolvedValue({
    ...targets(),
    items: [],
    firstTargetId: null,
  });
  render(<WorkforceRecommendationsScreen />);
  await screen.findByText("추천할 진행 대상이 없습니다.");
  expect(screen.getByTestId("recommendation-run")).toBeDisabled();
  expect(api.createRecommendation).not.toHaveBeenCalled();
});
it("조건 입력만으로 카드 제목 회차를 바꾸지 않고 명시 실행에서 함께 적용한다", async () => {
  await open();
  const title = screen.getByTestId("recommendation-applied").textContent;
  fireEvent.change(screen.getByTestId("recommendation-job"), {
    target: { value: "FE" },
  });
  fireEvent.change(screen.getByTestId("recommendation-month"), {
    target: { value: "2026-08" },
  });
  fireEvent.change(screen.getByTestId("recommendation-grade"), {
    target: { value: "SR" },
  });
  expect(screen.getByTestId("recommendation-applied")).toHaveTextContent(
    title!,
  );
  expect(api.createRecommendation).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByTestId("recommendation-run"));
  await waitFor(() =>
    expect(api.createRecommendation).toHaveBeenCalledTimes(2),
  );
  expect(api.createRecommendation).toHaveBeenLastCalledWith({
    ...condition,
    jobCode: "FE",
    startMonth: "2026-08",
    requestedGradeCode: "SR",
  });
});
it("자리 변경은 점수를 재생성하지 않고 가배정에는 현재 문자열 PK를 쓴다", async () => {
  await open();
  fireEvent.change(screen.getByTestId("recommendation-need"), {
    target: { value: "9" },
  });
  vi.mocked(api.selectTentative).mockResolvedValue({
    rcmdId: "12",
    rank: 1,
    changed: true,
    selection,
    revision: "new",
    message: "저장",
    releasedTentativeAssignmentId: null,
  });
  fireEvent.click(screen.getByTestId("recommendation-select-1"));
  await waitFor(() =>
    expect(api.selectTentative).toHaveBeenCalledWith(
      "12",
      expect.anything(),
      "9",
      [],
    ),
  );
  expect(api.createRecommendation).toHaveBeenCalledTimes(1);
});
it("대상 전환은 현재 초안을 적용하고 늦은 이전 응답을 폐기한다", async () => {
  let late!: (d: api.Data) => void;
  vi.mocked(api.createRecommendation)
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          late = resolve;
        }),
    )
    .mockImplementation(async (c) => ({
      ...result(c),
      candidates: [
        {
          ...candidate(),
          developer: { ...candidate().developer, name: "새 대상 후보" },
        },
      ],
    }));
  render(<WorkforceRecommendationsScreen />);
  await waitFor(() =>
    expect(api.createRecommendation).toHaveBeenCalledTimes(1),
  );
  fireEvent.change(screen.getByTestId("recommendation-target"), {
    target: { value: "OPPORTUNITY:O-2" },
  });
  await screen.findByText("새 대상 후보");
  late(result());
  await waitFor(() =>
    expect(screen.queryByText("검토 인력")).not.toBeInTheDocument(),
  );
  expect(screen.getByTestId("recommendation-applied")).toHaveTextContent(
    "두 번째 기회",
  );
});
it("전환 실패 후 이전 카드의 행동을 차단한다", async () => {
  await open();
  vi.mocked(api.createRecommendation).mockRejectedValueOnce(
    new Error("추천 실패"),
  );
  fireEvent.change(screen.getByTestId("recommendation-target"), {
    target: { value: "OPPORTUNITY:O-2" },
  });
  await screen.findByText("추천 실패");
  expect(screen.getByTestId("recommendation-select-1")).toBeDisabled();
  expect(screen.getByTestId("recommendation-exclude-1")).toBeDisabled();
});
it("빈 결과는 제외 고유 인원과 중첩 사유를 구별해 표시한다", async () => {
  vi.mocked(api.createRecommendation).mockResolvedValue({
    ...result(),
    rcmdId: null,
    candidates: [],
  });
  render(<WorkforceRecommendationsScreen />);
  await screen.findByTestId("recommendation-empty");
  expect(screen.getByTestId("recommendation-exclusions")).toHaveTextContent(
    "전체 후보 2명 · 고유 제외 1명",
  );
  expect(screen.getByText("휴직 1명")).toBeInTheDocument();
});
it("0배점은0/0 빈 막대이고 참고 안내와 비용 미확인을 표시한다", async () => {
  await open();
  const bar = screen.getByRole("progressbar", { name: "1위 비용" });
  expect(bar).toHaveAttribute("value", "0");
  expect(bar).toHaveAttribute("max", "1");
  expect(screen.getByText("0/0")).toBeInTheDocument();
  expect(screen.getByText("개인 인건비 미확인")).toBeInTheDocument();
  expect(
    screen.getByText("추천은 참고용입니다. 배정은 담당자가 정합니다"),
  ).toBeInTheDocument();
});
it("선택 완료 카드는 읽기 전용이다", () => {
  const data = result();
  data.candidates[0].selection.actionCode = "TENT";
  render(<RecommendationCards data={data} onAction={vi.fn()} />);
  expect(screen.getByTestId("recommendation-select-1")).toBeDisabled();
  expect(screen.getByTestId("recommendation-exclude-1")).toBeDisabled();
});
it("제외 모달은 취소 시 쓰지 않고 사유 선택 후에만 저장한다", async () => {
  await open();
  fireEvent.click(screen.getByTestId("recommendation-exclude-1"));
  let dialog = screen.getByRole("dialog");
  expect(within(dialog).getByRole("button", { name: "확정" })).toBeDisabled();
  fireEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(api.excludeCandidate).not.toHaveBeenCalled();
  fireEvent.click(screen.getByTestId("recommendation-exclude-1"));
  dialog = screen.getByRole("dialog");
  fireEvent.change(screen.getByTestId("recommendation-exclude-reason"), {
    target: { value: "SCHD" },
  });
  vi.mocked(api.excludeCandidate).mockResolvedValue({
    rcmdId: "12",
    rank: 1,
    changed: true,
    selection,
    revision: "new",
    message: "제외",
    releasedTentativeAssignmentId: null,
  });
  vi.mocked(api.getRecommendation).mockResolvedValue({
    ...result(),
    candidates: [
      { ...candidate(), selection: { ...selection, actionCode: "EXCL" } },
    ],
  });
  fireEvent.click(within(dialog).getByRole("button", { name: "확정" }));
  await waitFor(() => expect(api.excludeCandidate).toHaveBeenCalledTimes(1));
  await waitFor(() =>
    expect(
      screen.queryByTestId("recommendation-card-1"),
    ).not.toBeInTheDocument(),
  );
  expect(api.createRecommendation).toHaveBeenCalledTimes(1);
});
it("필수 서버 경고를 보여주고 반환된 코드만 확인 후 재전송한다", async () => {
  await open();
  vi.mocked(api.selectTentative)
    .mockRejectedValueOnce(
      new ApiError(409, {
        success: false,
        meta: {},
        code: "INVALID_STATE",
        message: "확인 필요",
        requestId: "r",
        details: {
          workforceRecommendationWarnings: [
            { code: "MOVE", message: "이전 가배정을 해제합니다" },
          ],
        },
      } as ConstructorParameters<typeof ApiError>[1]),
    )
    .mockResolvedValue({
      rcmdId: "12",
      rank: 1,
      changed: true,
      selection,
      revision: "new",
      message: "가배정 완료",
      releasedTentativeAssignmentId: "1",
    });
  fireEvent.click(screen.getByTestId("recommendation-select-1"));
  await screen.findByText("이전 가배정을 해제합니다");
  expect(api.selectTentative).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "확인 후 가배정" }));
  await waitFor(() =>
    expect(api.selectTentative).toHaveBeenLastCalledWith(
      "12",
      expect.anything(),
      "7",
      ["MOVE"],
    ),
  );
});
it("저장 후 조회 실패는 재조회만 제공하고 업무를 자동 재실행하지 않는다", async () => {
  await open();
  vi.mocked(api.selectTentative).mockResolvedValue({
    rcmdId: "12",
    rank: 1,
    changed: true,
    selection,
    revision: "new",
    message: "가배정",
    releasedTentativeAssignmentId: null,
  });
  vi.mocked(api.getRecommendation).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  fireEvent.click(screen.getByTestId("recommendation-select-1"));
  await screen.findByText("저장됐지만 다시 불러오지 못했습니다");
  expect(screen.getByTestId("recommendation-run")).toBeDisabled();
  fireEvent.click(screen.getByTestId("recommendation-reload"));
  await waitFor(() => expect(api.getRecommendation).toHaveBeenCalledTimes(2));
  expect(api.selectTentative).toHaveBeenCalledTimes(1);
  expect(api.createRecommendation).toHaveBeenCalledTimes(1);
});
it("프로젝트 추천은 적용 조건으로 기존 배정 폼을 열고 열기 자체는 저장하지 않는다", async () => {
  const t = {
    ...target(),
    targetType: "PROJECT" as const,
    targetId: "P-1",
    defaults: { ...target().defaults, needSn: null },
    contractEndDate: "2026-12-31",
  };
  vi.mocked(api.getTargets).mockResolvedValue({
    ...targets(),
    items: [t],
    firstTargetId: "P-1",
  });
  await open();
  fireEvent.click(screen.getByTestId("recommendation-select-1"));
  expect(await screen.findByTestId("assignment-create-form")).toHaveTextContent(
    "신규 배정 D-1 P-1 BE",
  );
  expect(api.saveRecommendedAssignment).not.toHaveBeenCalled();
});
it("추천 기록 열기와 상세는 GET만 사용한다", async () => {
  vi.mocked(api.getHistory).mockResolvedValue({
    items: [
      {
        rcmdId: "8",
        recommendationType: "AI",
        targetId: "삭제된 대상",
        jobCode: "BE",
        startMonth: "2025-07",
        requestedGradeCode: "MD",
        executedAt: "2025-07-01 00:00:00",
        executedBy: "admin",
        candidateCount: 1,
      },
    ],
    page: 1,
    size: 50,
    totalCount: 1,
  });
  await open();
  fireEvent.click(screen.getByTestId("recommendation-history"));
  fireEvent.click(await screen.findByTestId("recommendation-round-8"));
  await waitFor(() => expect(api.getRecommendation).toHaveBeenCalledWith("8"));
  expect(api.createRecommendation).toHaveBeenCalledTimes(1);
});
