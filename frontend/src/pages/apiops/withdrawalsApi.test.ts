import { afterEach, expect, it, vi } from "vitest";
import { downloadWithdrawals, getWithdrawals } from "./withdrawalsApi";
import { saveMasterDataDownload } from "../exportsops/masterDataExportApi";
vi.mock("../exportsops/masterDataExportApi", () => ({ saveMasterDataDownload: vi.fn() }));
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });
it("serializes the screen sort and filters for the whole export without page or size", async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ success: true, meta: {}, data: { items: [] } }), { status: 200, headers: { "Content-Type": "application/json" } }));
  vi.stubGlobal("fetch", fetcher);
  vi.stubGlobal("matchMedia", () => ({ matches: true }));
  const query = { year: 2026, period: "SOON", extension: "UNSET", job: "BE", page: 3, size: 20, sort: "history", direction: "DESC" } as const;
  await getWithdrawals(query);
  expect(new URL(String(fetcher.mock.calls[0][0]), "http://localhost").searchParams.get("sort")).toBe("history");
  fetcher.mockResolvedValueOnce(new Response(new Blob(["xlsx"]), { status: 200, headers: { "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Content-Disposition": "attachment; filename=\"withdrawals.xlsx\"" } }));
  await downloadWithdrawals(query);
  const actual = new URL(String(fetcher.mock.calls[1][0]), "http://localhost").searchParams;
  expect(Object.fromEntries(actual)).toEqual({ year: "2026", period: "SOON", extension: "UNSET", job: "BE", sort: "history", direction: "DESC" });
  expect(saveMasterDataDownload).toHaveBeenCalledOnce();
});
