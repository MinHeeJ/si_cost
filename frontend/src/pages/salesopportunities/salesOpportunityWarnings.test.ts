import { beforeEach, expect, it, vi } from "vitest";
import { getNeedWarnings } from "./salesOpportunityApi";
import {
  getSalesOpportunities,
  type Opportunity,
} from "../salesopportunitiesops/salesCommandsApi";
import { getDeveloperAssignments } from "../developers/peopleClient";
import { getMonths } from "../assignmentsops/assignmentActionsApi";
vi.mock("../salesopportunitiesops/salesCommandsApi");
vi.mock("../developers/peopleClient");
vi.mock("../assignmentsops/assignmentActionsApi");
beforeEach(() => vi.resetAllMocks());
it("全ページの現行仮割当と指定月の実MMを集計し同じ機会と取消は除外する", async () => {
  vi.mocked(getSalesOpportunities).mockImplementation(
    async (_status, _stopped, page) => ({
      items: [
        {
          id: page === 1 ? "self" : "other",
          name: "영업기회03",
          tentativeAssignments: [{ developerId: "D-1", status: "ACT" }],
        } as Opportunity,
      ],
      page: page!,
      size: 1,
      total: 2,
      stageTotals: [],
      summary: {
        opportunityCount: 2,
        staleCount: 0,
        amount: "0",
        weightedAmount: "0",
        weightedPeople: "0",
      },
    }),
  );
  vi.mocked(getDeveloperAssignments).mockResolvedValue({
    items: [
      { id: "a", assignmentStatus: "ING" },
      { id: "b", assignmentStatus: "ING" },
      { id: "c", assignmentStatus: "CNCL" },
    ],
    page: 1,
    size: 50,
    total: 3,
  } as Awaited<ReturnType<typeof getDeveloperAssignments>>);
  vi.mocked(getMonths).mockResolvedValue({
    items: [
      { month: "2026-12", monthlyMm: "0.50" },
      { month: "2026-11", monthlyMm: "1.00" },
    ],
    total: 2,
    page: 1,
    size: 50,
  } as Awaited<ReturnType<typeof getMonths>>);
  expect(await getNeedWarnings("D-1", "2026-12", "self")).toEqual([
    "다른 기회(영업기회03)에 가배정되어 있습니다. 저장하면 그 가배정은 풀립니다.",
    "12월에 이미 1.00 MM 투입 중입니다.",
  ]);
  expect(getMonths).toHaveBeenCalledTimes(2);
  expect(getMonths).toHaveBeenCalledWith("a", 2026);
});
