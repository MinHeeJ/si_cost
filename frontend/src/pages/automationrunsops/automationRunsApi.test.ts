import { afterEach, expect, it, vi } from "vitest";
import {
  createAutomationRunToday,
  getAutomationRuns,
} from "./automationRunsApi";
afterEach(() => vi.unstubAllGlobals());
it("오늘 명령은 두 작업의 경로만 사용하고 본문·날짜·강제 필드를 보내지 않는다", async () => {
  const fetcher = vi
    .fn()
    .mockImplementation(
      async () =>
        new Response(
          JSON.stringify({ success: true, meta: {}, data: { accepted: true } }),
          { status: 202 },
        ),
    );
  vi.stubGlobal("fetch", fetcher);
  for (const job of ["DAILY_OPERATIONS", "RECEIVABLE_DUNNING"] as const) {
    await createAutomationRunToday(job);
    expect(fetcher).toHaveBeenLastCalledWith(
      `/api/automation-runs/${job}/run-today`,
      expect.objectContaining({ method: "POST" }),
    );
    expect(fetcher.mock.lastCall?.[1].body).toBeUndefined();
  }
});
it("목록은 승인한 페이지·크기·정렬만 보내며 닫힘 취소 신호를 전달한다", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValue(
      new Response(
        JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
      ),
    );
  vi.stubGlobal("fetch", fetcher);
  const controller = new AbortController();
  await getAutomationRuns({ page: 2, size: 20 }, controller.signal);
  const [path, options] = fetcher.mock.lastCall!;
  const params = new URLSearchParams(path.split("?")[1]);
  expect(params.get("page")).toBe("2");
  expect(params.get("size")).toBe("20");
  expect(params.get("sort")).toBe("runDate,desc;jobCode,asc");
  expect([...params.keys()]).toEqual(["page", "size", "sort"]);
  expect(options.signal).toBe(controller.signal);
});
