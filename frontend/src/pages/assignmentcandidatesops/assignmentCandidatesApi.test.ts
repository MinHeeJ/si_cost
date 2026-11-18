import { stubBusinessFetch } from "../../shared/testSupport/businessFixtures";
import { afterEach, expect, it, vi } from "vitest";
import {
  confirmAssignmentCandidate,
  dropAssignmentCandidate,
  getAssignmentCandidates,
  type AssignmentCandidate,
} from "./assignmentCandidatesApi";
const candidate: AssignmentCandidate = {
  id: "201",
  opportunityId: "O-0201",
  projectId: "P-201",
  projectName: "프로젝트",
  developerId: "D-201",
  developerName: "개발자",
  jobCode: "BE",
  startDate: "2026-07-10",
  endDate: "2026-08-31",
  contractMm: "0.50",
  billingUnitPrice: "101",
  revision: "complete-source-revision",
};
afterEach(() => vi.unstubAllGlobals());
it("sends reviewed NEW values with path TENT, sourceTentId and expectedRevision, never ordinary create", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: {
          changed: true,
          revision: "next",
          warnings: [],
          opportunityId: candidate.opportunityId,
          tentativeId: candidate.id,
          assignmentId: "A-0201",
        },
      }),
      { status: 200 },
    ),
  );
  stubBusinessFetch(fetcher);
  await confirmAssignmentCandidate(candidate, {
    year: 2026,
    projectId: candidate.projectId,
    developerId: candidate.developerId,
    jobCode: candidate.jobCode,
    startDate: candidate.startDate,
    endDate: candidate.endDate,
    contractMm: "0.75",
    billingUnitPrice: "123",
  });
  const [path, options] = fetcher.mock.calls[0];
  expect(path).toBe("/api/assignment-candidates/201/confirm");
  expect(JSON.parse(options.body)).toMatchObject({
    sourceTentId: candidate.id,
    expectedRevision: candidate.revision,
    inputType: "NEW",
    contractDeveloperId: null,
    contractMm: "0.75",
    billingUnitPrice: "123",
  });
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it("drop sends only revision and no client actor", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: {
          changed: true,
          revision: "next",
          warnings: [],
          opportunityId: candidate.opportunityId,
          tentativeId: candidate.id,
        },
      }),
      { status: 200 },
    ),
  );
  stubBusinessFetch(fetcher);
  await dropAssignmentCandidate(candidate);
  expect(fetcher.mock.calls[0][0]).toBe("/api/assignment-candidates/201/drop");
  expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({
    expectedRevision: candidate.revision,
  });
});
it("network failure is returned without automatic mutation retry", async () => {
  const fetcher = vi.fn().mockRejectedValue(new Error("연결 실패"));
  stubBusinessFetch(fetcher);
  await expect(dropAssignmentCandidate(candidate)).rejects.toThrow("연결 실패");
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it("candidate list paging does not send assignment year", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: { items: [], total: 0, page: 2, size: 50 },
      }),
      { status: 200 },
    ),
  );
  stubBusinessFetch(fetcher);
  await getAssignmentCandidates(2);
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/assignment-candidates?page=2&size=50",
  );
});
