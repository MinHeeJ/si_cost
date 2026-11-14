import { afterEach, expect, it, vi } from "vitest";
import {
  getSalesPartSettlement,
  getSalesPartSettlements,
} from "./salesPartSettlementsApi";
afterEach(() => vi.unstubAllGlobals());
it("uses optional server month for tabs and only the approved detail parameters", async () => {
  const fetcher = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, meta: {}, data: {} }),
  });
  vi.stubGlobal("fetch", fetcher);
  await getSalesPartSettlements();
  expect(fetcher.mock.calls[0][0]).toBe("/api/sales-part-settlements");
  await getSalesPartSettlements("2026-07");
  expect(fetcher.mock.calls[1][0]).toBe(
    "/api/sales-part-settlements?month=2026-07",
  );
  const selected = { partId: "선택 파트" };
  await getSalesPartSettlement(selected.partId, {
    month: "2026-07",
    page: 2,
    size: 20,
    sort: "profitAmount",
    direction: "DESC",
  });
  const path = fetcher.mock.calls[2][0] as string;
  expect(
    path.startsWith(
      `/api/sales-part-settlements/${encodeURIComponent(selected.partId)}?`,
    ),
  ).toBe(true);
  expect(Object.fromEntries(new URLSearchParams(path.split("?")[1]))).toEqual({
    month: "2026-07",
    page: "2",
    size: "20",
    sort: "profitAmount",
    direction: "DESC",
  });
  expect(fetcher.mock.calls[2][1].body).toBeUndefined();
});
