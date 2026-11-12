import { afterEach, expect, it, vi } from "vitest";
import {
  assignUserProject,
  detachUserProject,
  getBusinessUserProjects,
  searchUserProjects,
} from "./userProjectsApi";
afterEach(() => vi.unstubAllGlobals());
function transport() {
  const fetcher = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    text: async () =>
      JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
  });
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}
it("encodes selected user identity and adds no unsupported year or query", async () => {
  const fetcher = transport();
  await getBusinessUserProjects("selected/person");
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/business-users/selected%2Fperson/projects",
  );
});
it("PM mutations contain user/revision only, with explicit replacement flag for PUT", async () => {
  const fetcher = transport();
  await assignUserProject(
    "selected-project",
    "selectedperson",
    "pm-revision",
    true,
  );
  await detachUserProject("selected-project", "selectedperson", "pm-revision");
  const [put, del] = fetcher.mock.calls;
  expect(put[0]).toBe("/api/projects/selected-project/pm");
  expect(put[1].method).toBe("PUT");
  expect(JSON.parse(put[1].body)).toEqual({
    userId: "selectedperson",
    expectedRevision: "pm-revision",
    replaceConfirmed: true,
  });
  expect(del[1].method).toBe("DELETE");
  expect(JSON.parse(del[1].body)).toEqual({
    userId: "selectedperson",
    expectedRevision: "pm-revision",
  });
});
it("project search uses getProjects with contract filters rather than purpose PM", async () => {
  const fetcher = transport();
  await searchUserProjects({ year: 2026, status: "PRE", q: " 검색 ", page: 2 });
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/projects?year=2026&status=PRE&page=2&size=20&q=%EA%B2%80%EC%83%89",
  );
});
