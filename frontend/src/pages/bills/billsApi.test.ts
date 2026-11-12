import { afterEach, expect, it, vi } from "vitest";
import {
  getBills,
  getBill,
  createBill,
  putBill,
  getEstimate,
  getSummary,
  type CreateBill,
} from "./billsApi";
afterEach(() => vi.unstubAllGlobals());
function fetcher(data: unknown = {}, status = 200) {
  const fn = vi.fn().mockImplementation(
    async () =>
      new Response(JSON.stringify({ success: true, meta: {}, data }), {
        status,
      }),
  );
  vi.stubGlobal("fetch", fn);
  return fn;
}
it("uses relative bill paths with exact contract query values", async () => {
  const fn = fetcher();
  await getBills({
    year: 2026,
    includeCarryover: false,
    month: "2026-07",
    status: "CNCL",
    page: 2,
    size: 20,
    sort: "supplyAmount",
    direction: "DESC",
  });
  const url = new URL(fn.mock.calls[0][0], "https://test.invalid");
  expect(url.pathname).toBe("/api/bills");
  expect(Object.fromEntries(url.searchParams)).toEqual({
    year: "2026",
    includeCarryover: "false",
    month: "2026-07",
    status: "CNCL",
    page: "2",
    size: "20",
    sort: "supplyAmount",
    direction: "DESC",
  });
  await getBill("B-202607-001", 2026);
  expect(fn.mock.calls[1][0]).toBe("/api/bills/B-202607-001?year=2026");
  await getEstimate("P-TEST", "2026-07");
  expect(fn.mock.calls[2][0]).toBe(
    "/api/bills/estimate?projectId=P-TEST&month=2026-07",
  );
  await getSummary(2026, true);
  expect(fn.mock.calls[3][0]).toBe(
    "/api/bills/summary?year=2026&includeCarryover=true",
  );
});
it("creates with exact decimal strings and accepts the real 201 JSON body", async () => {
  const result = { changed: true, revision: "saved" };
  const fn = fetcher(result, 201);
  const body: CreateBill = {
    year: 2026,
    projectId: "P-TEST",
    billingMonth: "2026-07",
    supplyAmount: "999999999999999",
    vatRate: "0",
    billingDate: "2026-07-15",
    dueDate: "2026-08-25",
    taxInvoiceNo: null,
  };
  expect(await createBill(body)).toEqual(result);
  expect(fn.mock.calls[0][0]).toBe("/api/bills");
  expect(JSON.parse(fn.mock.calls[0][1].body)).toEqual(body);
  expect(fn.mock.calls[0][1].method).toBe("POST");
  expect(fn.mock.calls[0][1].headers.has("Authorization")).toBe(false);
});
it("updates only the addressed bill and never automatically retries an error", async () => {
  const fn = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "STALE_WRITE",
        message: "다시 조회",
        requestId: "trace",
        field: "supplyAmount",
      }),
      { status: 409 },
    ),
  );
  vi.stubGlobal("fetch", fn);
  await expect(
    putBill("B-202607-001", {
      year: 2026,
      expectedRevision: "old",
      supplyAmount: "101",
      vatRate: "10",
      billingDate: "2026-07-15",
      dueDate: "2026-08-25",
      taxInvoiceNo: null,
    }),
  ).rejects.toMatchObject({
    status: 409,
    body: { code: "STALE_WRITE", requestId: "trace" },
  });
  expect(fn).toHaveBeenCalledTimes(1);
  expect(fn.mock.calls[0][0]).toBe("/api/bills/B-202607-001");
  expect(fn.mock.calls[0][1].method).toBe("PUT");
});
