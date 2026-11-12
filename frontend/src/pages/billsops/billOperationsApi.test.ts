import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import * as api from "./billOperationsApi";
afterEach(() => vi.unstubAllGlobals());
it("six contracted operations use relative paths and selected identities without authentication", async () => {
  const fetcher = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, meta: {}, data: {} }),
  });
  vi.stubGlobal("fetch", fetcher);
  await api.getBillEstimate("selected project", "2026-08");
  await api.getBillsSummary(2026, false);
  await api.getBillSelection({
    year: 2026,
    ids: ["B-202607-011", "B-202607-012"],
    excludeIds: ["B-202607-012"],
  });
  await api.getBillCopyDraft("B-202607-011", 2026);
  await api.saveBillCancellation("B-202607-011", {
    year: 2026,
    expectedRevision: "before",
    reason: "취소",
  });
  await api.createNextMonthBill("B-202607-011", {
    year: 2027,
    expectedRevision: "before",
  });
  expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
    "/api/bills/estimate?projectId=selected+project&month=2026-08",
    "/api/bills/summary?year=2026&includeCarryover=false",
    "/api/bills/selection?year=2026&ids=B-202607-011&ids=B-202607-012&excludeIds=B-202607-012",
    "/api/bills/B-202607-011/copy-draft?year=2026",
    "/api/bills/B-202607-011/cancel",
    "/api/bills/B-202607-011/next-month",
  ]);
  for (const call of fetcher.mock.calls)
    expect(call[1].headers.has("Authorization")).toBe(false);
  expect(JSON.parse(fetcher.mock.calls[5][1].body)).toEqual({
    year: 2027,
    expectedRevision: "before",
  });
});
it("201 mutation result is consumed rather than treated as an empty acknowledgement", async () => {
  const data = {
    changed: true,
    bill: { id: "B-202608-011" },
    revision: "after",
    warnings: [],
  };
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      headers: new Headers({ "Content-Type": "application/json" }),
      text: async () => JSON.stringify({ success: true, meta: {}, data }),
    }),
  );
  expect(
    await api.createNextMonthBill("B-202607-011", {
      year: 2026,
      expectedRevision: "before",
    }),
  ).toEqual(data);
});
it("409 is surfaced once without automatically replaying the write", async () => {
  const fetcher = vi.fn().mockResolvedValue({
    ok: false,
    status: 409,
    text: async () =>
      JSON.stringify({
        success: false,
        meta: {},
        code: "STALE_WRITE",
        message: "다시 조회",
        requestId: "bill-request",
      }),
  });
  vi.stubGlobal("fetch", fetcher);
  await expect(
    api.createNextMonthBill("B-202607-011", {
      year: 2026,
      expectedRevision: "before",
    }),
  ).rejects.toBeInstanceOf(ApiError);
  expect(fetcher).toHaveBeenCalledOnce();
});
