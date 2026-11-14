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
it("sends the exact month-end bound and retains active-status filtering before local paging", async () => {
  const base = {
    id: "C-1",
    name: "후보",
    employeeType: "FL",
    companyName: null,
    job: "개발",
    grade: "중급",
    skills: [],
    homeAddress: null,
    desiredRate: "750000",
    availableDate: "2027-06-30",
    contactStatus: "DONE" as const,
    lastContactDate: null,
    source: "사이트",
    revision: "r",
  };
  vi.mocked(people.getCandidates)
    .mockResolvedValueOnce({
      items: [
        { ...base, id: "C-next", availableDate: "2027-07-01" },
        { ...base, id: "C-hold", contactStatus: "HOLD" },
        { ...base, id: "C-contract", contactStatus: "CTRT" },
        base,
      ],
      total: 101,
      page: 1,
      size: 100,
    })
    .mockResolvedValueOnce({
      items: [{ ...base, id: "C-last" }],
      total: 101,
      page: 2,
      size: 100,
    });
  const result = await getShortageCandidates(
    { page: 1, size: 20, availability: "NEXT_MONTH" },
    { jobCode: "DEV", availableUntil: "2027-06-30" },
  );
  expect(result.total).toBe(2);
  expect(result.items.map((row) => row.id)).toEqual(["C-1", "C-last"]);
  expect(people.getCandidates).toHaveBeenNthCalledWith(
    1,
    {
      page: 1,
      size: 100,
      jobCode: "DEV",
      availability: undefined,
      availableUntil: "2027-06-30",
    },
    undefined,
  );
  expect(people.getCandidates).toHaveBeenNthCalledWith(
    2,
    {
      page: 2,
      size: 100,
      jobCode: "DEV",
      availability: undefined,
      availableUntil: "2027-06-30",
    },
    undefined,
  );
});
it("a failed later page fails the whole view rather than reporting a partial count", async () => {
  vi.mocked(people.getCandidates)
    .mockResolvedValueOnce({ items: [], total: 101, page: 1, size: 100 })
    .mockRejectedValueOnce(new Error("두 번째 페이지 실패"));
  await expect(
    getShortageCandidates(
      { page: 1, size: 50 },
      { jobCode: "DEV", availableUntil: "2027-06-30" },
    ),
  ).rejects.toThrow("두 번째 페이지 실패");
});
