import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import * as api from "./peopleClient";
afterEach(() => vi.unstubAllGlobals());
function fetcher() {
  const fn = vi
    .fn()
    .mockImplementation(
      async () =>
        new Response(JSON.stringify({ success: true, meta: {}, data: {} })),
    );
  vi.stubGlobal("fetch", fn);
  return fn;
}
it("get uses selected candidate, relative path, default and explicit paging, and abort signal", async () => {
  const fetch = fetcher(),
    controller = new AbortController();
  await api.getCandidateContacts("CAND-900001");
  expect(fetch.mock.calls[0][0]).toBe(
    "/api/candidates/CAND-900001/contacts?page=1&size=50",
  );
  await api.getCandidateContacts("CAND-900002", 2, 20, controller.signal);
  expect(fetch.mock.calls[1][0]).toBe(
    "/api/candidates/CAND-900002/contacts?page=2&size=20",
  );
  expect(fetch.mock.calls[1][1].signal).toBe(controller.signal);
});
it("post sends only contract fields without date or actor echoes", async () => {
  const fetch = fetcher();
  const input = {
    contactStatus: "HOLD" as const,
    memo: "메모",
    expectedRevision: "opaque",
    actor: "other",
    contactDate: "2026-01-01",
  };
  await api.createCandidateContact("CAND-900003", input);
  expect(fetch.mock.calls[0][0]).toBe("/api/candidates/CAND-900003/contacts");
  expect(fetch.mock.calls[0][1].method).toBe("POST");
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
    contactStatus: "HOLD",
    memo: "메모",
    expectedRevision: "opaque",
  });
});
it("refresh reads the existing candidate detail endpoint instead of adding a route", async () => {
  const fetch = fetcher();
  await api.getCandidateContactContext("CAND-900001");
  expect(fetch.mock.calls[0][0]).toBe("/api/candidates/CAND-900001");
});
it("stale errors retain field and requestId and do not retry a POST", async () => {
  const fetch = fetcher();
  fetch.mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "STALE_WRITE",
        message: "다시 조회해 주세요.",
        field: "expectedRevision",
        requestId: "contact-request",
      }),
      { status: 409 },
    ),
  );
  await expect(
    api.createCandidateContact("CAND-900001", {
      contactStatus: "DONE",
      memo: "기록",
      expectedRevision: "old",
    }),
  ).rejects.toMatchObject({
    body: { requestId: "contact-request", code: "STALE_WRITE" },
  });
  expect(fetch).toHaveBeenCalledOnce();
});
it("HTML failure is not a successful contact response", async () => {
  const fetch = fetcher();
  fetch.mockResolvedValue(
    new Response("<html>unavailable</html>", { status: 500 }),
  );
  await expect(
    api.getCandidateContacts("CAND-900001"),
  ).rejects.not.toBeInstanceOf(ApiError);
  expect(fetch).toHaveBeenCalledOnce();
});
