import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  getProject,
  getProjects,
  createProject,
  putProject,
  getProjectDefaults,
} from "./projectsApi";
beforeEach(() =>
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ success: true, meta: {}, data: {} })),
      ),
  ),
);
it("list preserves exact query names, literal search and integer year", async () => {
  await getProjects({
    year: 2027,
    q: "A%_",
    contractType: "TK",
    status: "PRE",
    partId: "UNASSIGNED",
    page: 2,
    size: 20,
    sort: "startDate",
    direction: "DESC",
  });
  const url = String(vi.mocked(fetch).mock.calls[0][0]);
  expect(url.startsWith("/api/projects?")).toBe(true);
  const query = new URLSearchParams(url.split("?")[1]);
  expect(Object.fromEntries(query)).toEqual({
    year: "2027",
    q: "A%_",
    contractType: "TK",
    status: "PRE",
    partId: "UNASSIGNED",
    page: "2",
    size: "20",
    sort: "startDate",
    direction: "DESC",
  });
});
it("detail encodes selected domain ID and never sends unapproved year", async () => {
  await getProject("selected/project", true);
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
    "/api/projects/selected%2Fproject?includeCancelled=true",
  );
});
it("create and general update retain exact string money and POST versus path identity", async () => {
  const fields = {
    year: 2026,
    name: "프로젝트",
    startDate: "2026-01-01",
    endDate: "2027-12-31",
    contractAmount: "999999999999999",
  };
  await createProject({ ...fields, contractType: "IN", initialStatus: "ING" });
  let [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/projects");
  expect(options?.method).toBe("POST");
  expect(JSON.parse(options?.body as string)).toEqual({
    ...fields,
    contractType: "IN",
    initialStatus: "ING",
  });
  vi.mocked(fetch).mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data: {} })),
  );
  await putProject("chosen-id", { ...fields, expectedRevision: "opaque" });
  [path, options] = vi.mocked(fetch).mock.calls[1];
  expect(path).toBe("/api/projects/chosen-id");
  expect(options?.method).toBe("PUT");
  expect(JSON.parse(options?.body as string)).toEqual({
    ...fields,
    expectedRevision: "opaque",
  });
});
it("server defaults use approved parameter names and no browser calculation", async () => {
  await getProjectDefaults("999999999999999", "TK");
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
    "/api/projects/defaults?contractAmount=999999999999999&contractType=TK",
  );
});
it("root field errors and request ID propagate without retry", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "INVALID_INPUT",
        field: "name",
        message: "이름 오류",
        requestId: "project-error",
      }),
      { status: 400 },
    ),
  );
  const error = await getProject("chosen-id").catch((e) => e);
  expect(error).toBeInstanceOf(ApiError);
  expect(error.fieldMessages).toEqual({ name: "이름 오류" });
  expect(error.body.requestId).toBe("project-error");
  expect(fetch).toHaveBeenCalledOnce();
});
