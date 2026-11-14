import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { releaseTentativeAssignment } from "./tentativeAssignmentsApi";
const result = {
  changed: true,
  revision: "next",
  warnings: [],
  opportunityId: "O-0201",
  tentativeId: "201",
};
afterEach(() => vi.unstubAllGlobals());
it("posts selected TENT and full revision with no client actor", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data: result }), {
      status: 200,
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  await expect(
    releaseTentativeAssignment(
      result.tentativeId,
      "source-revision",
      "사용자 해제",
    ),
  ).resolves.toEqual(result);
  expect(fetcher).toHaveBeenCalledTimes(1);
  const [path, options] = fetcher.mock.calls[0];
  expect(path).toBe("/api/tentative-assignments/201/release");
  expect(options.method).toBe("POST");
  expect(JSON.parse(options.body)).toEqual({
    expectedRevision: "source-revision",
    reason: "사용자 해제",
  });
});
it("omits absent optional reason and broadcasts successful real changes", async () => {
  const events: Event[] = [];
  const listener = (event: Event) => events.push(event);
  window.addEventListener("sicost:sales-committed", listener);
  try {
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ success: true, meta: {}, data: result }),
          { status: 200 },
        ),
      );
    vi.stubGlobal("fetch", fetcher);
    await releaseTentativeAssignment(result.tentativeId, "revision");
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({
      expectedRevision: "revision",
    });
    expect(events).toHaveLength(1);
    expect((events[0] as CustomEvent).detail).toEqual(result);
  } finally {
    window.removeEventListener("sicost:sales-committed", listener);
  }
});
it("network failure is propagated with no mutation retry", async () => {
  const fetcher = vi.fn().mockRejectedValue(new Error("연결 실패"));
  vi.stubGlobal("fetch", fetcher);
  await expect(releaseTentativeAssignment("201", "revision")).rejects.toThrow(
    "연결 실패",
  );
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it("stale-write envelope is preserved and never retried", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "STALE_WRITE",
        message: "다시 조회해 주세요.",
        requestId: "release-stale",
      }),
      { status: 409 },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await expect(releaseTentativeAssignment("201", "old")).rejects.toBeInstanceOf(
    ApiError,
  );
  expect(fetcher).toHaveBeenCalledTimes(1);
});
