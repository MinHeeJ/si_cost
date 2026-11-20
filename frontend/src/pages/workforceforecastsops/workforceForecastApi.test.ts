import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  getWorkforceForecastExport,
  getWorkforceForecasts,
} from "./workforceForecastApi";
const query = { from: "2027-01", months: 12, includeUnfilled: true };
const mime =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
const filename = "인력 수요 예측_2027_20261012_090000.xlsx";
afterEach(() => vi.unstubAllGlobals());
it("read sends only approved query fields and unwraps the response", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: { ...query, totals: [] },
      }),
      { headers: { "Content-Type": "application/json" } },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  expect(await getWorkforceForecasts(query)).toEqual({ ...query, totals: [] });
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/workforce-forecasts?from=2027-01&months=12&includeUnfilled=true",
  );
});
it("download preserves exact filter and display unit and accepts trusted filename", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(new Blob(["xlsx"]), {
      headers: {
        "Content-Type": mime,
        "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
      },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const result = await getWorkforceForecastExport(query, "MILLION");
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/workforce-forecasts/export?from=2027-01&months=12&includeUnfilled=true&amountUnit=MILLION",
  );
  expect(result.filename).toBe(filename);
  expect(result.blob.size).toBeGreaterThan(0);
});
it("failed access logging returns ApiError instead of a file", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "접근기록 실패",
          requestId: "workforce-log",
        }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      ),
    ),
  );
  await expect(getWorkforceForecastExport(query, "WON")).rejects.toBeInstanceOf(
    ApiError,
  );
});
it.each(["text/html", "application/octet-stream"])(
  "rejects non-xlsx %s",
  async (type) => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response("not xlsx", { headers: { "Content-Type": type } }),
        ),
    );
    await expect(getWorkforceForecastExport(query, "WON")).rejects.toThrow(
      "파일을 내려받지 못했습니다",
    );
  },
);
it.each(["../export.xlsx", "=forecast.xlsx", "%broken", "인력수요예측.xlsx", "../인력 수요 예측_2027_20261012_090000.xlsx", "=인력 수요 예측_2027_20261012_090000.xlsx", "인력\n수요 예측_2027_20261012_090000.xlsx"])(
  "rejects unsafe or unexpected filename %s",
  async (name) => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(new Blob(["xlsx"]), {
          headers: {
            "Content-Type": mime,
            "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(name)}`,
          },
        }),
      ),
    );
    await expect(getWorkforceForecastExport(query, "WON")).rejects.toThrow(
      "파일명을 확인할 수 없습니다",
    );
  },
);
it("rejects empty xlsx", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(null, {
        headers: {
          "Content-Type": mime,
          "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
        },
      }),
    ),
  );
  await expect(getWorkforceForecastExport(query, "WON")).rejects.toThrow(
    "파일이 비어 있습니다",
  );
});
