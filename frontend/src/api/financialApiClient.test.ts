import { afterEach, describe, expect, it, vi } from "vitest";
import * as api from "./apiClient";
import { saveDownload } from "../pages/b7/b7Api";

vi.mock("../pages/b7/b7Api", async (original) => ({
  ...(await original<typeof import("../pages/b7/b7Api")>()),
  saveDownload: vi.fn(),
}));
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

const reads = [
  {
    path: "/api/profit-loss/structure?year=2026&month=2026-07&aggregation=QUARTER",
    call: () => api.getProfitLossStructure(2026, "2026-07", "QUARTER"),
  },
  {
    path: "/api/profit-loss/operating?year=2026&month=2026-07",
    call: () => api.getOperatingProfitLoss(2026, "2026-07"),
  },
  {
    path: "/api/profit-loss/net?year=2026&month=2026-07",
    call: () => api.getNetProfitLoss(2026, "2026-07"),
  },
  {
    path: "/api/business-plans/performance?year=2026&month=2026-07",
    call: () => api.getBusinessPlanPerformance(2026, "2026-07"),
  },
  {
    path: "/api/uploads/999999999999/result",
    call: () => api.getBusinessPlanUploadResult("999999999999"),
  },
  {
    path: "/api/uploads/999999999999/result",
    call: () => api.getHistoricalProfitLossUploadResult("999999999999"),
  },
  {
    path: "/api/uploads/999999999999/issues?page=2&size=20&severity=WARN",
    call: () => api.getUploadIssues("999999999999", "WARN", 2, 20),
  },
  {
    path: "/api/financial-history?month=2026-07&scope=BUSINESS_PLAN&page=1&size=50",
    call: () => api.getFinancialHistory("2026-07", "BUSINESS_PLAN"),
  },
  {
    path: "/api/financial-history?month=2026-07&scope=HISTORICAL_PROFIT_LOSS&page=1&size=50",
    call: () => api.getFinancialHistory("2026-07", "HISTORICAL_PROFIT_LOSS"),
  },
];
const uploads = [
  {
    path: "/api/business-plans/imports/validate",
    fields: { year: "2026" },
    call: (file: File) => api.createBusinessPlanImportValidation(file, 2026),
  },
  {
    path: "/api/business-plans/imports/apply",
    fields: {
      year: "2026",
      uploadId: "999999999999",
      expectedRevision: "revision",
      mode: "KEEP_CLOSED",
    },
    call: (file: File) =>
      api.saveBusinessPlanImport(
        file,
        2026,
        "999999999999",
        "revision",
        "KEEP_CLOSED",
      ),
  },
  {
    path: "/api/profit-loss/migration/imports/validate",
    fields: {},
    call: (file: File) => api.createHistoricalProfitLossImportValidation(file),
  },
  {
    path: "/api/profit-loss/migration/imports/apply",
    fields: { uploadId: "999999999999", expectedRevision: "revision" },
    call: (file: File) =>
      api.saveHistoricalProfitLossImport(file, "999999999999", "revision"),
  },
];
const downloads = [
  {
    path: "/api/business-plans/imports/template?year=2025",
    call: () => api.getBusinessPlanImportTemplate(2025),
    saved: false,
  },
  {
    path: "/api/profit-loss/migration/imports/template",
    call: () => api.getHistoricalProfitLossImportTemplate(),
    saved: false,
  },
  {
    path: "/api/profit-loss/structure/export?year=2026&month=2026-07&aggregation=YTD&amountUnit=WON",
    call: () => api.downloadProfitLossStructure(2026, "2026-07", "YTD", "WON"),
    saved: true,
  },
  {
    path: "/api/profit-loss/operating/export?year=2026&month=2026-07&amountUnit=THOUSAND_WON",
    call: () =>
      api.downloadOperatingProfitLoss(2026, "2026-07", "THOUSAND_WON"),
    saved: true,
  },
  {
    path: "/api/profit-loss/net/export?year=2026&month=2026-07&amountUnit=MILLION_WON",
    call: () => api.downloadNetProfitLoss(2026, "2026-07", "MILLION_WON"),
    saved: true,
  },
  {
    path: "/api/uploads/999999999999/issues.xlsx?severity=ERR",
    call: () => api.getUploadIssuesXlsx("999999999999", "ERR"),
    saved: false,
  },
];

describe("회사 재무 API 등록", () => {
  it.each(reads)("$path uses the existing JSON transport", async (test) => {
    const data = { amount: "9007199254740993", optionalAmount: null };
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ success: true, meta: {}, data })),
      );
    vi.stubGlobal("fetch", fetcher);
    expect(await test.call()).toEqual(data);
    expect(fetcher).toHaveBeenCalledOnce();
    const [path, options] = fetcher.mock.calls[0] as [string, RequestInit];
    expect(path).toBe(test.path);
    expect(options.method ?? "GET").toBe("GET");
    expect(new Headers(options.headers).get("Accept")).toBe("application/json");
  });
  it.each(uploads)(
    "$path preserves the browser File and explicit context",
    async (test) => {
      const data = { uploadId: "999999999999" };
      const fetcher = vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ success: true, meta: {}, data })),
        );
      vi.stubGlobal("fetch", fetcher);
      const file = new File(["workbook"], "financial.xlsx");
      expect(await test.call(file)).toEqual(data);
      expect(fetcher).toHaveBeenCalledOnce();
      const [path, options] = fetcher.mock.calls[0] as [string, RequestInit];
      expect(path).toBe(test.path);
      expect(options.method).toBe("POST");
      expect(new Headers(options.headers).has("Content-Type")).toBe(false);
      const body = options.body as FormData;
      expect(body.get("file")).toBe(file);
      expect(
        Object.fromEntries(
          Array.from(body.entries()).filter(([key]) => key !== "file"),
        ),
      ).toEqual(test.fields);
      expect(body.get("userId")).toBeNull();
      expect(body.get("month")).toBeNull();
    },
  );
  it.each(downloads)("$path retains binary downloads", async (test) => {
    const filename = "재무자료.xlsx";
    const fetcher = vi.fn().mockResolvedValue(
      new Response("workbook", {
        headers: {
          "Content-Type":
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
        },
      }),
    );
    vi.stubGlobal("fetch", fetcher);
    const result = await test.call();
    const download = test.saved
      ? vi.mocked(saveDownload).mock.calls[0]?.[0]
      : result;
    expect(download).toMatchObject({ filename });
    expect(download?.blob.size).toBeGreaterThan(0);
    expect(fetcher).toHaveBeenCalledOnce();
    expect(fetcher.mock.calls[0][0]).toBe(test.path);
  });
  it.each([
    ...reads.map((test) => ({ path: test.path, call: test.call })),
    ...uploads.map((test) => ({
      path: test.path,
      call: () => test.call(new File(["workbook"], "financial.xlsx")),
    })),
    ...downloads.map((test) => ({ path: test.path, call: test.call })),
  ])("$path preserves API errors without retrying", async (test) => {
    const body = {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다시 검사해 주세요.",
      field: "expectedRevision",
      requestId: "financial-request",
      details: { changedMonths: [] },
    };
    const fetcher = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(body), {
        status: 409,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetcher);
    await expect(test.call()).rejects.toMatchObject({ status: 409, body });
    expect(fetcher).toHaveBeenCalledOnce();
    expect(saveDownload).not.toHaveBeenCalled();
  });
});
