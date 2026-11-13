import { afterEach, expect, it, vi } from "vitest";
import {
  getProjectCostExport,
  getProjectCostSummary,
} from "./projectCostExportApi";
const mime =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
afterEach(() => vi.unstubAllGlobals());
it("export sends all filters and settings but never page, size or caller identity", async () => {
  const filename = "프로젝트 원가_202606_20260701_000000.xlsx";
  const fetcher = vi.fn().mockResolvedValue(
    new Response(new Blob(["xlsx"]), {
      headers: {
        "Content-Type": mime,
        "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
      },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const controller = new AbortController();
  const file = await getProjectCostExport(
    {
      month: "2026-06",
      projectId: "0001",
      contractType: "TK",
      status: "ING",
      alertOnly: true,
      sort: "cumulativeCost",
      direction: "DESC",
      page: 3,
      size: 20,
      userId: "other",
    },
    { order: ["projectName"], hidden: [], unit: "천원" },
    controller.signal,
  );
  const path = fetcher.mock.calls[0][0] as string;
  const params = new URLSearchParams(path.split("?")[1]);
  expect(path).toMatch(/^\/api\/project-costs\/export\?/);
  expect(params.get("projectId")).toBe("0001");
  expect(params.get("sort")).toBe("cumulativeCost");
  expect(params.get("hidden")).toBe("");
  expect(params.get("unit")).toBe("천원");
  for (const key of ["page", "size", "userId"])
    expect(params.has(key)).toBe(false);
  expect(fetcher.mock.calls[0][1].signal).toBe(controller.signal);
  expect(file.filename).toBe(filename);
});
it("summary sends only its five filters and preserves null unknown metrics", async () => {
  const data = {
    month: "2026-06",
    total: 24,
    cumulativeRevenue: null,
    cumulativeProfit: null,
    researchCost: "7",
    lowMarginCount: 0,
    budgetOverCount: 0,
  };
  const fetcher = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data }), {
      headers: { "Content-Type": "application/json" },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const result = await getProjectCostSummary({
    month: "2026-06",
    projectId: "0001",
    status: "ING",
    contractType: "MO",
    alertOnly: false,
    page: 3,
    size: 20,
    sort: "projectName",
    userId: "other",
  });
  const path = fetcher.mock.calls[0][0] as string;
  expect(path).toMatch(/^\/api\/project-costs\/summary\?/);
  expect(path).not.toMatch(/page=|size=|sort=|userId=/);
  expect(path).toContain("alertOnly=false");
  expect(result).toEqual(data);
});
it("server JSON audit failure never becomes a downloadable file", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "접근기록 실패",
          requestId: "cost-export-test",
        }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      ),
    ),
  );
  await expect(getProjectCostExport({})).rejects.toThrow("접근기록 실패");
});
it("rejects HTML, untrusted filename and empty body", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(
      new Response("<html>", { headers: { "Content-Type": "text/html" } }),
    )
    .mockResolvedValueOnce(
      new Response("xlsx", {
        headers: {
          "Content-Type": mime,
          "Content-Disposition": "attachment; filename*=UTF-8''..%2Ffile.xlsx",
        },
      }),
    )
    .mockResolvedValueOnce(
      new Response("", {
        headers: {
          "Content-Type": mime,
          "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent("프로젝트 원가_202606_20260701_000000.xlsx")}`,
        },
      }),
    );
  vi.stubGlobal("fetch", fetcher);
  await expect(getProjectCostExport({})).rejects.toThrow(
    "파일을 내려받지 못했습니다",
  );
  await expect(getProjectCostExport({})).rejects.toThrow("파일명");
  await expect(getProjectCostExport({})).rejects.toThrow("비어 있습니다");
});
