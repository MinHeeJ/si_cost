import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  getSalesOpportunityForecast,
  getSalesOpportunityForecastExport,
} from "./salesForecastApi";
const mime =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
afterEach(() => vi.unstubAllGlobals());
it("forecast sends no period, page or caller fields", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: { from: "2026-08", months: 6 },
      }),
      { headers: { "Content-Type": "application/json" } },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  expect(await getSalesOpportunityForecast()).toEqual({
    from: "2026-08",
    months: 6,
  });
  expect(fetcher.mock.calls[0][0]).toBe("/api/sales-opportunities/forecast");
});
it("export sends only amountUnit and accepts an actual server xlsx filename", async () => {
  const filename = "영업 파이프라인_2026_20260715_090000.xlsx";
  const fetcher = vi.fn().mockResolvedValue(
    new Response(new Blob(["xlsx"]), {
      headers: {
        "Content-Type": mime,
        "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
      },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const result = await getSalesOpportunityForecastExport("MILLION");
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/sales-opportunities/forecast/export?amountUnit=MILLION",
  );
  expect(result.filename).toBe(filename);
  expect(result.blob.size).toBeGreaterThan(0);
});
it("log failure JSON is an ApiError, never downloaded as an xlsx", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "접근기록 실패",
          requestId: "export-id",
        }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      ),
    ),
  );
  await expect(getSalesOpportunityForecastExport("WON")).rejects.toBeInstanceOf(
    ApiError,
  );
});
it.each(["text/html", "application/octet-stream"])(
  "rejects %s instead of an xlsx",
  async (type) => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response("not an xlsx", { headers: { "Content-Type": type } }),
        ),
    );
    await expect(getSalesOpportunityForecastExport("WON")).rejects.toThrow(
      "파일을 내려받지 못했습니다",
    );
  },
);
it.each(["../forecast.xlsx", "=forecast.xlsx", "영업예측.xlsx", "%broken", "../영업 파이프라인_2026_20260715_090000.xlsx", "=영업 파이프라인_2026_20260715_090000.xlsx", "영업\n파이프라인_2026_20260715_090000.xlsx"])(
  "rejects an untrusted filename %s",
  async (filename) => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(new Blob(["xlsx"]), {
          headers: {
            "Content-Type": mime,
            "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
          },
        }),
      ),
    );
    await expect(getSalesOpportunityForecastExport("WON")).rejects.toThrow(
      "파일명을 확인할 수 없습니다",
    );
  },
);
it("rejects an empty successful xlsx response", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(null, {
        headers: {
          "Content-Type": mime,
          "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent("영업 파이프라인_2026_20260715_090000.xlsx")}`,
        },
      }),
    ),
  );
  await expect(getSalesOpportunityForecastExport("WON")).rejects.toThrow(
    "다운로드 파일이 비어 있습니다",
  );
});
