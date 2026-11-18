import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import {
  WorkforceRecommendationCandidates,
  type RecommendationCandidatesProps,
} from "./WorkforceRecommendationCandidates";
import * as api from "./recommendationActionsApi";
import * as shared from "../shared/sharedApi";
import * as assignments from "../assignmentsops/assignmentActionsApi";
import { ApiError } from "../../shared/api";
vi.mock("./recommendationActionsApi");
vi.mock("../shared/sharedApi");
vi.mock("../assignmentsops/assignmentActionsApi");
vi.mock("../assignmentsops/AssignmentCreateForm", () => ({
  AssignmentCreateForm: ({
    initialValues,
    onClose,
  }: {
    initialValues: assignments.Create;
    onClose(): void;
  }) => (
    <div role="dialog" aria-label="기존 배정 폼">
      <pre>{JSON.stringify(initialValues)}</pre>
      <button onClick={onClose}>닫기</button>
    </div>
  ),
}));
const selection: api.RecommendationSelection = {
  actionCode: null,
  exclusionReasonCode: null,
  actedBy: null,
  actedAt: null,
  assignmentId: null,
  tentativeAssignmentId: null,
  finalOpportunityId: null,
  finalNeedSn: null,
};
const candidate: api.RecommendationCandidate = {
  rank: 3,
  developer: {
    developerId: "D-0101",
    name: "검토 인력",
    managementNumber: "000101",
    jobCode: "BE",
    subJobCode: null,
    gradeCode: "SR",
    employeeTypeCode: "FT",
    detailId: "D-0101",
  },
  opportunityId: "O-01",
  needSn: "1",
  jobCode: "BE",
  startMonth: "2026-07",
  requestedGradeCode: "SR",
  totalScore: "82.3",
  scoreItems: [
    {
      itemCode: "JOB",
      baseScore: "100",
      weight: "0",
      score: "0",
      basis: "배점 없음",
    },
  ],
  reason: "추천 근거",
  otherTentativeAssignments: [],
  selection,
  revision: "original",
  targetRevision: "target",
  candidateRevision: "candidate",
  nextPosition: null,
};
const onRefresh = vi.fn(),
  onDeveloperDetails = vi.fn(),
  onTargetDetails = vi.fn();
const props: RecommendationCandidatesProps = {
  rcmdId: "101",
  type: "AI",
  target: { type: "OPPORTUNITY", id: "O-01", name: "현재 기회" },
  candidates: [candidate],
  selectedNeedSn: "2",
  year: 2026,
  today: "2026-07-01",
  onRefresh,
  onDeveloperDetails,
  onTargetDetails,
};
function result(action: "EXCL" | "TENT"): api.RecommendationActionResult {
  return {
    rcmdId: "101",
    rank: 3,
    changed: true,
    selection: {
      ...selection,
      actionCode: action,
      finalNeedSn: action === "TENT" ? "2" : null,
    },
    revision: "after",
    message:
      action === "TENT"
        ? "현재 기회 / 2행 / FE 가배정했습니다. 이전 가배정을 해제했습니다."
        : "제외했습니다.",
    releasedTentativeAssignmentId: null,
  };
}
function warning(
  message = "다른 직무를 확인해 주세요.",
  code = "JOB_MISMATCH",
) {
  return new ApiError(409, {
    success: false,
    meta: {},
    code: "INVALID_STATE",
    message: "최신 경고를 확인해 주세요.",
    requestId: "test-request",
    details: { workforceRecommendationWarnings: [{ code, message }] },
  } as unknown as ConstructorParameters<typeof ApiError>[1]);
}
beforeEach(() => {
  vi.resetAllMocks();
  onRefresh.mockResolvedValue(undefined);
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "SCHD",
        name: "일정",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.mocked(api.createWorkforceRecommendationExclusion).mockResolvedValue(
    result("EXCL"),
  );
  vi.mocked(
    api.createWorkforceRecommendationTentativeAssignment,
  ).mockResolvedValue(result("TENT"));
  vi.mocked(assignments.projectOptions).mockResolvedValue({
    items: [
      {
        id: "P-01",
        name: "프로젝트",
        contractType: "MO",
        status: "ING",
        startDate: "2026-07-10",
        endDate: "2027-03-31",
      },
    ],
  });
});
it("renders immutable rank and score, zero-weight empty bar, and reuses detail actions", async () => {
  render(<WorkforceRecommendationCandidates {...props} />);
  expect(screen.getByText("순위 3")).toBeInTheDocument();
  expect(screen.getByText("총점 82.3")).toBeInTheDocument();
  expect(screen.getByRole("progressbar")).toHaveAttribute("value", "0");
  await userEvent.click(screen.getByRole("button", { name: "검토 인력" }));
  expect(onDeveloperDetails).toHaveBeenCalledWith("D-0101");
  await userEvent.click(screen.getByRole("button", { name: "현재 기회" }));
  expect(onTargetDetails).toHaveBeenCalledWith("OPPORTUNITY", "O-01");
});
it("opening and cancelling exclusion does not post", async () => {
  render(<WorkforceRecommendationCandidates {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "제외" }));
  await screen.findByRole("option", { name: "일정" });
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.createWorkforceRecommendationExclusion).not.toHaveBeenCalled();
  expect(screen.getByTestId("recommendation-card-3")).toBeInTheDocument();
});
it("requires reason and uses active server codes", async () => {
  render(<WorkforceRecommendationCandidates {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "제외" }));
  await screen.findByRole("option", { name: "일정" });
  await userEvent.click(screen.getByRole("button", { name: "확정" }));
  expect(screen.getByText("사유를 선택해 주세요.")).toBeInTheDocument();
  expect(api.createWorkforceRecommendationExclusion).not.toHaveBeenCalled();
  expect(shared.getSharedCodes).toHaveBeenCalledWith("RCMD_EXCL");
});
it("exclusion removes only that card and never renumbers remaining rows", async () => {
  render(
    <WorkforceRecommendationCandidates
      {...props}
      candidates={[candidate, { ...candidate, rank: 5 }]}
    />,
  );
  await userEvent.click(screen.getByTestId("recommendation-exclude-3"));
  await screen.findByRole("option", { name: "일정" });
  await userEvent.selectOptions(screen.getByLabelText("제외 사유*"), "SCHD");
  await userEvent.click(screen.getByRole("button", { name: "확정" }));
  await waitFor(() =>
    expect(
      screen.queryByTestId("recommendation-card-3"),
    ).not.toBeInTheDocument(),
  );
  expect(screen.getByText("순위 5")).toBeInTheDocument();
  expect(screen.getByText("총점 82.3")).toBeInTheDocument();
  expect(api.createWorkforceRecommendationExclusion).toHaveBeenCalledWith(
    "101",
    3,
    { reasonCode: "SCHD", expectedRevision: "original" },
  );
});
it("field validation remains beside reason and keeps modal input", async () => {
  vi.mocked(api.createWorkforceRecommendationExclusion).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "비활성 사유입니다.",
      field: "reasonCode",
      requestId: "test",
    }),
  );
  render(<WorkforceRecommendationCandidates {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "제외" }));
  await screen.findByRole("option", { name: "일정" });
  await userEvent.selectOptions(screen.getByLabelText("제외 사유*"), "SCHD");
  await userEvent.click(screen.getByRole("button", { name: "확정" }));
  await waitFor(() =>
    expect(screen.getByLabelText("제외 사유*")).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(screen.getByLabelText("제외 사유*")).toHaveValue("SCHD");
  expect(screen.getByRole("dialog")).toBeInTheDocument();
});
it("shows current server warnings before mutation confirmation and sends current seat", async () => {
  vi.mocked(api.createWorkforceRecommendationTentativeAssignment)
    .mockRejectedValueOnce(warning())
    .mockResolvedValue(result("TENT"));
  render(<WorkforceRecommendationCandidates {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "가배정" }));
  await screen.findByRole("dialog");
  expect(screen.getByText("다른 직무를 확인해 주세요.")).toBeInTheDocument();
  expect(
    api.createWorkforceRecommendationTentativeAssignment,
  ).toHaveBeenNthCalledWith(1, "101", 3, {
    needSn: "2",
    expectedRevision: "original",
    targetRevision: "target",
    candidateRevision: "candidate",
    confirmedWarnings: [],
  });
  await userEvent.click(screen.getByRole("button", { name: "확인 후 가배정" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(
    api.createWorkforceRecommendationTentativeAssignment,
  ).toHaveBeenNthCalledWith(2, "101", 3, {
    needSn: "2",
    expectedRevision: "original",
    targetRevision: "target",
    candidateRevision: "candidate",
    confirmedWarnings: ["JOB_MISMATCH"],
  });
  expect(
    screen.getByText(/현재 기회 \/ 2행 \/ FE 가배정했습니다/),
  ).toBeInTheDocument();
});
it("cancelling warning sends no second action", async () => {
  vi.mocked(
    api.createWorkforceRecommendationTentativeAssignment,
  ).mockRejectedValue(warning("이전 가배정을 이동합니다.", "TENTATIVE_MOVE"));
  render(<WorkforceRecommendationCandidates {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "가배정" }));
  await screen.findByRole("dialog");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(
    api.createWorkforceRecommendationTentativeAssignment,
  ).toHaveBeenCalledTimes(1);
});
it("changing seat during confirmation cannot save the old seat", async () => {
  vi.mocked(
    api.createWorkforceRecommendationTentativeAssignment,
  ).mockRejectedValue(warning());
  const view = render(<WorkforceRecommendationCandidates {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "가배정" }));
  await screen.findByRole("dialog");
  view.rerender(
    <WorkforceRecommendationCandidates {...props} selectedNeedSn="3" />,
  );
  expect(screen.getByRole("button", { name: "확인 후 가배정" })).toBeDisabled();
});
it("committed refresh failure offers GET-only retry and leaves final selection disabled", async () => {
  onRefresh
    .mockRejectedValueOnce(new Error("GET failed"))
    .mockResolvedValue(undefined);
  render(<WorkforceRecommendationCandidates {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "가배정" }));
  await screen.findByText("저장됐지만 다시 불러오지 못했습니다");
  expect(
    screen.queryByRole("button", { name: "가배정" }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "재조회" }));
  expect(
    api.createWorkforceRecommendationTentativeAssignment,
  ).toHaveBeenCalledTimes(1);
  expect(onRefresh).toHaveBeenCalledTimes(2);
});
it("rerender and target changes never generate another recommendation", async () => {
  const view = render(<WorkforceRecommendationCandidates {...props} />);
  view.rerender(
    <WorkforceRecommendationCandidates {...props} selectedNeedSn="3" />,
  );
  expect(
    api.createWorkforceRecommendationTentativeAssignment,
  ).not.toHaveBeenCalled();
  expect(api.createWorkforceRecommendationExclusion).not.toHaveBeenCalled();
});
it("does not apply late save results to a different target", async () => {
  let resolve!: (r: api.RecommendationActionResult) => void;
  vi.mocked(
    api.createWorkforceRecommendationTentativeAssignment,
  ).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  const view = render(<WorkforceRecommendationCandidates {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "가배정" }));
  view.rerender(
    <WorkforceRecommendationCandidates
      {...props}
      rcmdId="102"
      target={{ type: "OPPORTUNITY", id: "O-02", name: "다른 기회" }}
    />,
  );
  resolve(result("TENT"));
  await waitFor(() =>
    expect(api.notifyRecommendationCommitted).toHaveBeenCalled(),
  );
  expect(screen.queryByText(/가배정됨/)).not.toBeInTheDocument();
  expect(onRefresh).not.toHaveBeenCalled();
});
it("project action opens existing form with max start, end, NEW, one MM and optional provenance", async () => {
  render(
    <WorkforceRecommendationCandidates
      {...props}
      target={{ type: "PROJECT", id: "P-01", name: "프로젝트" }}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "배정" }));
  const dialog = await screen.findByRole("dialog", { name: "기존 배정 폼" });
  const value = JSON.parse(dialog.querySelector("pre")!.textContent!);
  expect(value).toMatchObject({
    startDate: "2026-07-10",
    endDate: "2027-03-31",
    contractMm: "1.00",
    inputType: "NEW",
    jobCode: "BE",
    recommendationRef: { rcmdId: "101", rank: 3 },
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "닫기" }));
  expect(
    api.createWorkforceRecommendationTentativeAssignment,
  ).not.toHaveBeenCalled();
});
it("NEXT has no exclusion and uses card seat rather than AI selected seat", async () => {
  render(<WorkforceRecommendationCandidates {...props} type="NEXT" />);
  expect(
    screen.queryByRole("button", { name: "제외" }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "가배정" }));
  expect(
    api.createWorkforceRecommendationTentativeAssignment,
  ).toHaveBeenCalledWith("101", 3, expect.objectContaining({ needSn: "1" }));
});
it("loading and failed-target controls are disabled without invented role restrictions", () => {
  const view = render(<WorkforceRecommendationCandidates {...props} loading />);
  expect(screen.getByText("추천을 불러오는 중입니다.")).toBeInTheDocument();
  view.rerender(
    <WorkforceRecommendationCandidates {...props} actionsEnabled={false} />,
  );
  expect(screen.getByRole("button", { name: "가배정" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "제외" })).toBeDisabled();
});
it("empty state and request error remain actionable", () => {
  const view = render(
    <WorkforceRecommendationCandidates {...props} candidates={[]} />,
  );
  expect(screen.getByText("맞는 인력이 없습니다.")).toBeInTheDocument();
  view.rerender(
    <WorkforceRecommendationCandidates
      {...props}
      error={new Error("다시 조회해 주세요.")}
    />,
  );
  expect(screen.getByText("다시 조회해 주세요.")).toBeInTheDocument();
});
it("Escape closes exclusion without changing business data", async () => {
  render(<WorkforceRecommendationCandidates {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "제외" }));
  await screen.findByRole("dialog");
  fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.createWorkforceRecommendationExclusion).not.toHaveBeenCalled();
});

it("empty recommendations offer the supplied condition reset without writing an action", async () => {
  const onReset = vi.fn();
  render(<WorkforceRecommendationCandidates {...props} candidates={[]} onReset={onReset} />);
  const empty = screen.getByRole("heading", { name: "맞는 인력이 없습니다." }).parentElement!;
  await userEvent.click(within(empty).getByRole("button", { name: "조건 초기화" }));
  expect(onReset).toHaveBeenCalledTimes(1);
  expect(api.createWorkforceRecommendationExclusion).not.toHaveBeenCalled();
  expect(api.createWorkforceRecommendationTentativeAssignment).not.toHaveBeenCalled();
});

it.each([
  ["천원", "1000.001", "1,000"],
  ["원", "1000001", "1,000,001"],
  ["백만원", "1.000001", "1"],
] as const)("cost evidence displays integer won exactly in %s without changing the saved score", (unit, _decimal, expected) => {
  render(<WorkforceRecommendationCandidates {...props} amountUnit={unit} candidates={[{ ...candidate,
    scoreDetail: { m: "202611", c: "1000001", u: "0", f: 1 },
    scoreItems: [{ itemCode: "COST", baseScore: "0", weight: "5", score: "0", basis: "raw cost" }],
  }]} />);
  expect(screen.getByText(new RegExp(`인건비 ${expected.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")} ${unit}`))).toHaveTextContent(`단가 0 ${unit}`);
  expect(screen.getByText(/총점/)).toHaveTextContent("82.3");
  expect(api.createWorkforceRecommendationTentativeAssignment).not.toHaveBeenCalled();
});

it("cost evidence keeps unknown distinct from zero", () => {
  render(<WorkforceRecommendationCandidates {...props} candidates={[{ ...candidate,
    scoreDetail: { c: null, u: "0", f: 0 },
    scoreItems: [{ itemCode: "COST", baseScore: "0", weight: "5", score: "0", basis: "raw cost" }],
  }]} />);
  expect(screen.getByText(/인건비 – 천원/)).toHaveTextContent("단가 0 천원 · 원천 미확인");
});

it.each([["1000000", "1,000"], ["-1000001", "−1,000"], ["5561666", "5,562"]])("cost evidence rounds %s for display without changing saved values", (value, expected) => {
  render(<WorkforceRecommendationCandidates {...props} candidates={[{ ...candidate,
    scoreDetail: { c: value, u: "8000000" },
    scoreItems: [{ itemCode: "COST", baseScore: "5", weight: "5", score: "5", basis: "raw" }],
  }]} />);
  expect(screen.getByText(`인건비 ${expected} 천원 · 단가 8,000 천원`)).toBeInTheDocument();
});
