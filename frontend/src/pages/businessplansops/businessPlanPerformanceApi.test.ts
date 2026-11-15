import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  getBusinessPlanPerformance,
  validateBusinessPlan,
  applyBusinessPlan,
  BUSINESS_PLAN_COMMITTED,
  type PlanValidation,
} from "./businessPlanPerformanceApi";
beforeEach(() => vi.stubGlobal("fetch", vi.fn()));
afterEach(() => vi.unstubAllGlobals());
const json = (data: unknown) =>
  new Response(JSON.stringify({ success: true, meta: {}, data }));
it("performance sends only approved year and month query, no actor or aggregation", async () => {
  vi.mocked(fetch).mockResolvedValue(json({ targetStatus: "UNREGISTERED" }));
  await getBusinessPlanPerformance(2026, "2026-07");
  expect(fetch).toHaveBeenCalledWith(
    "/api/business-plans/performance?year=2026&month=2026-07",
    expect.objectContaining({ redirect: "error" }),
  );
});
it("validation sends original file and current year as multipart without JSON Content-Type", async () => {
  vi.mocked(fetch).mockResolvedValue(json({ uploadId: "123" }));
  const file = new File(["transport"], "targets.xlsx");
  await validateBusinessPlan(file, 2026);
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/business-plans/imports/validate");
  expect(options?.headers).toBeUndefined();
  expect([...(options?.body as FormData).keys()]).toEqual(["file", "year"]);
  expect((options?.body as FormData).get("file")).toBe(file);
});
it("apply resends same file, validation year and revision with explicit KEEP_CLOSED and emits only after success", async () => {
  vi.mocked(fetch).mockResolvedValue(json({ status: "APPLIED" }));
  const listener = vi.fn();
  window.addEventListener(BUSINESS_PLAN_COMMITTED, listener);
  try {
    const file = new File(["transport"], "targets.xlsx");
    await applyBusinessPlan(
      file,
      {
        uploadId: "123",
        year: 2026,
        expectedRevision: "owned",
      } as PlanValidation,
      "KEEP_CLOSED",
    );
    const [, options] = vi.mocked(fetch).mock.calls[0],
      body = options?.body as FormData;
    expect(body.get("file")).toBe(file);
    expect(body.get("year")).toBe("2026");
    expect(body.get("mode")).toBe("KEEP_CLOSED");
    expect(body.get("expectedRevision")).toBe("owned");
    expect(body.get("uploadId")).toBe("123");
    expect(listener).toHaveBeenCalledOnce();
  } finally {
    window.removeEventListener(BUSINESS_PLAN_COMMITTED, listener);
  }
});
it("validation diagnostics preserve requestId and field without automatic retry", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "INVALID_INPUT",
        message: "양식 오류",
        field: "file",
        requestId: "owned",
      }),
      { status: 400 },
    ),
  );
  await expect(
    validateBusinessPlan(new File(["transport"], "targets.xlsx"), 2026),
  ).rejects.toMatchObject({ body: { requestId: "owned", field: "file" } });
  expect(fetch).toHaveBeenCalledOnce();
});
