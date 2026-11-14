import { afterEach, expect, it, vi } from "vitest";
import {
  comparisonProjects,
  downloadStaffingComparison,
  getStaffingComparison,
} from "./staffingComparisonApi";
const mime =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
afterEach(() => vi.unstubAllGlobals());
it("sends only approved export filters without page, sort, or injected actor", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response("xlsx", {
      headers: {
        "Content-Type": mime,
        "Content-Disposition": "attachment; filename*=UTF-8''comparison.xlsx",
      },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const full = {
    year: 2026,
    projectId: "P+&1",
    difference: "SHORT" as const,
    page: 7,
    size: 20,
    actor: "other",
    sort: "differenceMm",
  };
  const file = await downloadStaffingComparison(
    full,
    "MILLION",
    new AbortController().signal,
  );
  const path = fetcher.mock.calls[0][0] as string;
  expect(path).toBe(
    "/api/staffing-plan-comparison/export?year=2026&projectId=P%2B%261&difference=SHORT&amountUnit=MILLION",
  );
  expect(file.filename).toBe("comparison.xlsx");
});
it("JSON audit error does not become a downloadable file", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "접근기록 실패",
          requestId: "comparison-audit",
        }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      ),
    ),
  );
  await expect(
    downloadStaffingComparison(
      { year: 2026, difference: "ALL" },
      "WON",
      new AbortController().signal,
    ),
  ).rejects.toThrow("접근기록 실패");
});
it("HTML is rejected and unsafe filenames use safe fallback", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(
      new Response("html", { headers: { "Content-Type": "text/html" } }),
    )
    .mockResolvedValueOnce(
      new Response("xlsx", {
        headers: {
          "Content-Type": mime,
          "Content-Disposition": "attachment; filename*=UTF-8''..%2Fevil.xlsx",
        },
      }),
    );
  vi.stubGlobal("fetch", fetcher);
  await expect(
    downloadStaffingComparison(
      { year: 2026, difference: "ALL" },
      "WON",
      new AbortController().signal,
    ),
  ).rejects.toThrow("엑셀 응답");
  expect(
    (
      await downloadStaffingComparison(
        { year: 2026, difference: "ALL" },
        "WON",
        new AbortController().signal,
      )
    ).filename,
  ).toBe("계획대비투입.xlsx");
});
it("list uses the approved page and typed decimal response unchanged", async () => {
  const data = {
    items: [{ planMm: "9007199254740993.01" }],
    total: 1,
    page: 1,
    size: 20,
    year: 2026,
    totals: { planMm: "9007199254740993.01" },
  };
  const fetcher = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data }), {
      headers: { "Content-Type": "application/json" },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const result = await getStaffingComparison({
    year: 2026,
    difference: "ALL",
    page: 1,
    size: 20,
  });
  expect(result.items[0].planMm).toBe("9007199254740993.01");
  expect(fetcher.mock.calls[0][0]).toContain("/api/staffing-plan-comparison?");
});
it("loads every existing project page for the selected year without a new option API", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          meta: {},
          data: {
            items: [{ id: "P-a", name: "가" }],
            total: 101,
            page: 1,
            size: 100,
          },
        }),
      ),
    )
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          meta: {},
          data: {
            items: [{ id: "P-b", name: "나" }],
            total: 101,
            page: 2,
            size: 100,
          },
        }),
      ),
    );
  vi.stubGlobal("fetch", fetcher);
  expect(await comparisonProjects(2026)).toEqual([
    { id: "P-a", name: "가" },
    { id: "P-b", name: "나" },
  ]);
  expect(fetcher.mock.calls[0][0]).toContain("/api/projects?");
  expect(fetcher.mock.calls[1][0]).toContain("page=2");
});
