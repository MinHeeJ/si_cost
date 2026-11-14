import { beforeEach, expect, it, vi } from "vitest";
import {
  getCandidatePositions,
  getCandidateShortages,
  getShortageCandidates,
} from "./candidateMatchingApi";
import * as people from "../candidates/peopleClient";
vi.mock("../candidates/peopleClient", () => ({ getCandidates: vi.fn() }));
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("fetch", vi.fn());
});
it("uses approved relative GET paths and preserves unknown-source issues in metadata", async () => {
  vi.mocked(fetch).mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          success: true,
          meta: {
            issues: [
              {
                field: "TEAM_FEE",
                reason: "미확인",
                status: "UNKNOWN",
                needSn: null,
              },
            ],
          },
          data: { items: [], candidateId: "선택 후보", total: 0, limit: 4 },
        }),
        { status: 200 },
      ),
  );
  const result = await getCandidatePositions("선택 후보");
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
    `/api/candidates/${encodeURIComponent("선택 후보")}/positions`,
  );
  expect(result.issues[0].reason).toBe("미확인");
  await getCandidateShortages({
    page: 1,
    size: 50,
    sort: "jobCode",
    direction: "DESC",
  });
  expect(vi.mocked(fetch).mock.calls[1][0]).toBe(
    "/api/candidates/shortages?page=1&size=50&sort=jobCode&direction=DESC",
  );
});
it("uses the same server cutoff, active filter, ordering and pagination as export", async () => {
  const page = { items: [], total: 0, page: 3, size: 20 as const };
  vi.mocked(people.getCandidates).mockResolvedValue(page);
  const result = await getShortageCandidates(
    { page: 3, size: 20, availability: "NEXT_MONTH", sort: "desiredRate", direction: "DESC" },
    { jobCode: "DEV", availableUntil: "2027-06-30" },
  );
  expect(result).toBe(page);
  expect(people.getCandidates).toHaveBeenCalledTimes(1);
  expect(people.getCandidates).toHaveBeenCalledWith({
    page: 3, size: 20, jobCode: "DEV", availability: undefined,
    availableUntil: "2027-06-30", activeOnly: true, sort: "desiredRate", direction: "DESC",
  }, undefined);
});
it("propagates a server error without returning partial results", async () => {
  vi.mocked(people.getCandidates).mockRejectedValue(new Error("조회 실패"));
  await expect(getShortageCandidates({ page: 1, size: 50 },
    { jobCode: "DEV", availableUntil: "2027-06-30" })).rejects.toThrow("조회 실패");
});
