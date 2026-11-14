import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { saveMasterDataDownload } from "../exportsops/masterDataExportApi";
import { downloadWorkforceCalendar } from "./workforceCalendarApi";

vi.mock("../exportsops/masterDataExportApi", () => ({
  saveMasterDataDownload: vi.fn(),
}));
const mime =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
const filename = "인력운영달력_2026_20261012_090000.xlsx";
function response() {
  return new Response(new Blob(["xlsx"]), {
    headers: {
      "Content-Type": mime,
      "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
    },
  });
}
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});
it("downloads all applied calendar filters without list pagination or sorting", async () => {
  const fetcher = vi.fn().mockResolvedValue(response());
  vi.stubGlobal("fetch", fetcher);
  await downloadWorkforceCalendar({
    year: 2026,
    jobCode: "DEV",
    gradeCode: "HIGH",
    employeeType: "FT",
    remainingOnly: true,
    page: 3,
    size: 20,
    sort: "name",
    direction: "DESC",
  });
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/workforce-operations/export?year=2026&jobCode=DEV&gradeCode=HIGH&employeeType=FT&remainingOnly=true&tab=CALENDAR&amountUnit=THOUSAND",
  );
  expect(saveMasterDataDownload).toHaveBeenCalledWith({
    filename,
    blob: expect.any(Blob),
  });
});
it("supply uses its selected unit but excludes all calendar filters", async () => {
  const fetcher = vi.fn().mockResolvedValue(response());
  vi.stubGlobal("fetch", fetcher);
  await downloadWorkforceCalendar(
    {
      year: 2025,
      jobCode: "DEV",
      gradeCode: "HIGH",
      employeeType: "FT",
      remainingOnly: true,
      page: 3,
      size: 20,
    },
    "SUPPLY_DEMAND",
    "MILLION",
  );
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/workforce-operations/export?year=2025&tab=SUPPLY_DEMAND&amountUnit=MILLION",
  );
});
it("does not save a file when DNLD logging fails", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "접근기록 실패",
          requestId: "download-error",
        }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      ),
    ),
  );
  await expect(
    downloadWorkforceCalendar({ year: 2026 }),
  ).rejects.toBeInstanceOf(ApiError);
  expect(saveMasterDataDownload).not.toHaveBeenCalled();
});
it.each(["text/html", "application/octet-stream"])(
  "rejects unexpected content type %s",
  async (type) => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response("not xlsx", { headers: { "Content-Type": type } }),
        ),
    );
    await expect(downloadWorkforceCalendar({ year: 2026 })).rejects.toThrow(
      "파일을 내려받지 못했습니다",
    );
    expect(saveMasterDataDownload).not.toHaveBeenCalled();
  },
);
it("rejects an empty workbook", async () => {
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
  await expect(downloadWorkforceCalendar({ year: 2026 })).rejects.toThrow(
    "파일이 비어 있습니다",
  );
  expect(saveMasterDataDownload).not.toHaveBeenCalled();
});
