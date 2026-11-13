import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import * as api from "./corporateTaxApi";
afterEach(() => vi.unstubAllGlobals());
function response(data: unknown) {
  const fetcher = vi
    .fn()
    .mockResolvedValue(
      new Response(JSON.stringify({ success: true, meta: {}, data })),
    );
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}
it("allocation uses only selected year and public relative GET", async () => {
  const fetcher = response({ year: "2026" });
  await api.getCorporateTaxAllocation("2026");
  expect(fetcher.mock.calls[0][0]).toBe("/api/corporate-taxes/2026/allocation");
  expect(fetcher.mock.calls[0][1].headers.has("Authorization")).toBe(false);
});
it("initialize sends month only without revision or actor", async () => {
  const fetcher = response({ changed: false });
  await api.createCorporateTaxDefaults("2026-10");
  expect(fetcher.mock.calls[0][0]).toBe("/api/corporate-taxes/initialize");
  expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({
    month: "2026-10",
  });
});
it("confirmation preserves explicit zero/string precision and selected identity", async () => {
  const fetcher = response({ changed: true });
  const input = {
    month: "2026-10",
    expectedRevision: "current",
    paidDate: "2026-09-30",
    payAmount: "0",
  };
  await api.saveCorporateTaxPaymentConfirmation("2025", "CNFM", input);
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/corporate-taxes/2025/payments/CNFM/confirmation",
  );
  expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual(input);
});
it("conflict preserves request id and has no automatic retry", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "STALE_WRITE",
        message: "다시 조회",
        requestId: "tax-trace",
      }),
      { status: 409 },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await expect(
    api.saveCorporateTaxPaymentConfirmation("2025", "CNFM", {
      month: "2026-10",
      expectedRevision: "old",
      paidDate: "2026-09-30",
      payAmount: "0",
    }),
  ).rejects.toBeInstanceOf(ApiError);
  expect(fetcher).toHaveBeenCalledOnce();
});
