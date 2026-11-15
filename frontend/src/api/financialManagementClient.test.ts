import { afterEach, expect, it, vi } from "vitest";
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
const expectedRevision = "complete-source-revision";
const closeBody = {
  expectedRevision,
  operationId: "12345678-1234-1234-1234-123456789abc",
  acceptWarnings: false,
};
const reopenBody = {
  expectedRevision,
  operationId: closeBody.operationId,
  reason: "원천 재검토",
  acceptAdjustments: true,
};
const cases = [
  {
    path: "/api/cash-flow?year=2025&unit=MILLION_WON",
    call: () => api.getCashFlow(2025, "MILLION_WON"),
  },
  {
    path: "/api/cash-flow/sources?month=2026-12&item=CORP_TAX&page=2&size=20",
    call: () => api.getCashFlowSources("2026-12", "CORP_TAX", 2, 20),
  },
  {
    path: "/api/month-closes?month=2025-12&page=3&size=100",
    call: () => api.getMonthCloses("2025-12", 3, 100),
  },
  {
    path: "/api/month-closes/2025-12/checks",
    call: () => api.saveMonthCloseChecks("2025-12", expectedRevision),
    body: { expectedRevision },
  },
  {
    path: "/api/month-closes/2025-12/preview",
    call: () => api.createMonthClosePreview("2025-12", expectedRevision),
    body: { expectedRevision },
  },
  {
    path: "/api/month-closes/2025-12/reopen-preview",
    call: () => api.createMonthCloseReopenPreview("2025-12", expectedRevision),
    body: { expectedRevision },
  },
  {
    path: "/api/month-closes/2025-12/close",
    call: () => api.saveMonthClose("2025-12", closeBody),
    body: closeBody,
  },
  {
    path: "/api/month-closes/2025-12/reopen",
    call: () => api.saveMonthCloseReopen("2025-12", reopenBody),
    body: reopenBody,
  },
];
it.each(cases)(
  "$path preserves the feature transport and explicit inputs",
  async (test) => {
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
    expect(options.method ?? "GET").toBe("body" in test ? "POST" : "GET");
    expect(options.body ? JSON.parse(String(options.body)) : undefined).toEqual(
      "body" in test ? test.body : undefined,
    );
    expect(new Headers(options.headers).get("Accept")).toBe("application/json");
  },
);
it("exports the same annual year/unit through the existing binary download", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response("workbook", {
      headers: {
        "Content-Type":
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Content-Disposition": 'attachment; filename="cash-flow.xlsx"',
      },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  await api.getCashFlowExport(2025, "WON");
  expect(fetcher).toHaveBeenCalledOnce();
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/cash-flow/export?year=2025&unit=WON",
  );
  expect(saveDownload).toHaveBeenCalledWith(
    expect.objectContaining({ filename: "cash-flow.xlsx" }),
  );
});
it.each([
  ...cases,
  {
    path: "/api/cash-flow/export",
    call: () => api.getCashFlowExport(2025, "WON"),
  },
])(
  "$path preserves errors and never retries a command or saves an error file",
  async (test) => {
    const body = {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다시 조회해 주세요.",
      field: "expectedRevision",
      requestId: "management-request",
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
  },
);
