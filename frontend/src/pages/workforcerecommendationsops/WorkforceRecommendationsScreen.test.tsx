import { StrictMode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { WorkforceRecommendationsScreen } from "./WorkforceRecommendationsScreen";
import * as api from "./recommendationBrowseApi";
import * as shared from "../shared/sharedApi";
vi.mock("./recommendationBrowseApi");
vi.mock("../shared/sharedApi");
const target = (
  id: string,
  type: api.TargetType = "OPPORTUNITY",
): api.RecommendationTarget => ({
  targetId: id,
  targetType: type,
  name: id,
  contractTypeCode: null,
  projectIdAfterWin: null,
  contractStartDate: null,
  contractEndDate: null,
  needs: [
    {
      needSn: "2",
      jobCode: "BE",
      gradeCode: "MD",
      needCount: 1,
      startMonth: "2026-12",
      periodMonths: 3,
      monthlyMm: "1",
      activeCount: 0,
      remainingCount: 1,
      revision: "n",
    },
  ],
  defaults: {
    jobCode: "FE",
    startMonth: "2026-11",
    requestedGradeCode: "SR",
    needSn: type === "PROJECT" ? null : "2",
  },
  revision: "t",
});
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(shared.getSharedCodes).mockResolvedValue({ items: [] });
  vi.mocked(api.getWorkforceRecommendationTargets).mockResolvedValue({
    items: [target("O-1"), target("O-3"), target("P-1", "PROJECT")],
    firstTargetId: "O-1",
    serverDate: "2026-11-01",
    currentMonth: "2026-11",
    baseMonth: "2026-01",
    revision: "all",
  });
  vi.mocked(api.requestTargetRecommendation).mockImplementation(
    async (condition) => ({
      rcmdId: "1",
      recommendationType: "AI",
      appliedCondition: condition,
      sourceAssignmentId: null,
      executedAt: null,
      executedBy: null,
      candidates: [],
      message: "완료",
      revision: "r",
    }),
  );
});
it.each([
  ["OPPORTUNITY", "O-3", "2"],
  ["PROJECT", "P-1", null],
] as const)(
  "주소의 %s 대상·직무·시작월로 자동 실행하며 재마운트에도 유지한다",
  async (type, id, needSn) => {
    const props = {
      entrySearch: `?targetType=${type}&targetId=${id}&jobCode=BE&startMonth=2026-12`,
    };
    const view = render(
      <StrictMode>
        <WorkforceRecommendationsScreen {...props} />
      </StrictMode>,
    );
    await waitFor(() =>
      expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(1),
    );
    expect(api.requestTargetRecommendation).toHaveBeenLastCalledWith(
      expect.objectContaining({
        targetType: type,
        targetId: id,
        jobCode: "BE",
        startMonth: "2026-12",
        needSn,
      }),
      expect.any(AbortSignal),
    );
    expect(screen.getByTestId("recommendation-target")).toHaveValue(
      `${type}:${id}`,
    );
    const note = screen.getByRole("note");
    expect(note).toHaveTextContent(
      "안내 휴직·퇴사 예정자와 그 기간 MM 1.0인 사람 제외, 나이·성별·학력 미사용 (추천은 참고용이며 실제 배정은 담당자가 정합니다.)",
    );
    expect(note.parentElement).toContainElement(
      screen.getByTestId("recommendation-run"),
    );
    view.unmount();
    render(<WorkforceRecommendationsScreen {...props} />);
    await waitFor(() =>
      expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(2),
    );
    expect(screen.getByTestId("recommendation-start-month")).toHaveValue(
      "2026-12",
    );
  },
);
it("주소가 없으면 첫 대상을 추천하고 새로 조회할 때 마지막 대상을 보존한다", async () => {
  render(<WorkforceRecommendationsScreen />);
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(1),
  );
  fireEvent.change(screen.getByTestId("recommendation-target"), {
    target: { value: "OPPORTUNITY:O-3" },
  });
  await waitFor(() =>
    expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(2),
  );
  fireEvent.click(screen.getByTestId("recommendation-target-refresh"));
  await waitFor(() =>
    expect(api.getWorkforceRecommendationTargets).toHaveBeenCalledTimes(2),
  );
  expect(screen.getByTestId("recommendation-target")).toHaveValue(
    "OPPORTUNITY:O-3",
  );
  expect(api.requestTargetRecommendation).toHaveBeenCalledTimes(2);
});
it("없는 주소 대상은 다른 기회로 추천하지 않고 오류를 보인다", async () => {
  render(
    <WorkforceRecommendationsScreen entrySearch="?targetType=OPPORTUNITY&targetId=missing" />,
  );
  await screen.findByText(
    "지정한 추천 대상이 진행 중이 아니거나 조회할 수 없습니다.",
  );
  expect(api.requestTargetRecommendation).not.toHaveBeenCalled();
});
