import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { getCandidateSummary } from "./candidateSummaryApi";

afterEach(() => vi.unstubAllGlobals());
it("uses the public relative summary path without pagination or list filters", async () => {
  const data = {
    total: 1,
    inProgress: 1,
    interviewScheduled: 0,
    availableByNextMonthStart: 1,
    overdueContact: 0,
  };
  const fetcher = vi
    .fn()
    .mockResolvedValue(
      new Response(JSON.stringify({ success: true, meta: {}, data })),
    );
  vi.stubGlobal("fetch", fetcher);
  const controller = new AbortController();
  expect(await getCandidateSummary(controller.signal)).toEqual(data);
  expect(fetcher.mock.calls[0][0]).toBe("/api/candidates/summary");
  expect(fetcher.mock.calls[0][1]).toMatchObject({
    cache: "no-store",
    signal: controller.signal,
    redirect: "error",
  });
  expect(fetcher.mock.calls[0][1].headers.has("Authorization")).toBe(false);
});
it("preserves error code and requestId and never retries a failed request automatically", async () => {
  const body = {
    success: false,
    meta: {},
    code: "INTERNAL_ERROR",
    message: "자료 처리 중 오류가 발생했습니다.",
    requestId: "summary-trace",
  };
  const fetcher = vi
    .fn()
    .mockResolvedValue(new Response(JSON.stringify(body), { status: 500 }));
  vi.stubGlobal("fetch", fetcher);
  await expect(getCandidateSummary()).rejects.toMatchObject({
    status: 500,
    body,
  });
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it("a non-JSON response is not treated as empty successful summary data", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(new Response("<html>unavailable</html>")),
  );
  await expect(getCandidateSummary()).rejects.toThrow(
    "서버 응답을 확인할 수 없습니다.",
  );
});
it("uses shared ApiError for contract validation responses", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "UNKNOWN_FIELD",
          field: "actor",
          message: "허용되지 않은 입력 항목입니다.",
          requestId: "summary-query",
        }),
        { status: 400 },
      ),
    ),
  );
  await expect(getCandidateSummary()).rejects.toBeInstanceOf(ApiError);
});
