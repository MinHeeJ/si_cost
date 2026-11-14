import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getProjectAnalysisDownload,
  getProjectStatusBoardSummary,
  getProjectStatusIssues,
} from "./projectStatusApi";
const mime =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
afterEach(() => vi.unstubAllGlobals());
describe.each(["statusBoard", "projectCost"] as const)(
  "%s download transport",
  (view) => {
    it("sends filter-wide query only, preserves explicit empty hidden, and validates filename", async () => {
      const filename =
        view === "statusBoard"
          ? "프로젝트현황_20260701_000000.xlsx"
          : "프로젝트 원가_202607_20260701_000000.xlsx";
      const fetcher = vi.fn().mockResolvedValue(
        new Response(new Blob(["xlsx"]), {
          headers: {
            "Content-Type": mime,
            "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
          },
        }),
      );
      vi.stubGlobal("fetch", fetcher);
      const file = await getProjectAnalysisDownload(
        view,
        {
          page: 7,
          size: 20,
          userId: "other",
          contractType: "TK",
          month: "2026-07",
          light: "위험",
        },
        { order: ["projectName"], hidden: [], unit: "원" },
      );
      const path = fetcher.mock.calls[0][0] as string;
      expect(path).toMatch(/^\/api\//);
      expect(path).not.toContain("page=");
      expect(path).not.toContain("size=");
      expect(path).not.toContain("userId=");
      expect(path).toContain("hidden=");
      expect(file.filename).toBe(filename);
      if (view === "statusBoard") expect(path).not.toContain("month=");
      else expect(path).not.toContain("light=");
    });
    it("JSON error never becomes a downloadable file", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue(
          new Response(
            JSON.stringify({
              success: false,
              meta: {},
              code: "INTERNAL_ERROR",
              message: "접근기록 실패",
              requestId: "download-test",
            }),
            { status: 500, headers: { "Content-Type": "application/json" } },
          ),
        ),
      );
      await expect(getProjectAnalysisDownload(view, {})).rejects.toThrow(
        "접근기록 실패",
      );
    });
    it("rejects unexpected MIME and malicious response filename", async () => {
      const fetcher = vi
        .fn()
        .mockResolvedValueOnce(
          new Response("<html>", { headers: { "Content-Type": "text/html" } }),
        )
        .mockResolvedValueOnce(
          new Response("xlsx", {
            headers: {
              "Content-Type": mime,
              "Content-Disposition":
                "attachment; filename*=UTF-8''..%2Ffile.xlsx",
            },
          }),
        );
      vi.stubGlobal("fetch", fetcher);
      await expect(getProjectAnalysisDownload(view, {})).rejects.toThrow(
        "파일을 내려받지 못했습니다",
      );
      await expect(getProjectAnalysisDownload(view, {})).rejects.toThrow(
        "파일명",
      );
    });
  },
);
it("summary strips issue and page filters while issues keeps separate parameters", async () => {
  const fetcher = vi.fn().mockImplementation(() =>
    Promise.resolve(
      new Response(JSON.stringify({ success: true, meta: {}, data: {} }), {
        headers: { "Content-Type": "application/json" },
      }),
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await getProjectStatusBoardSummary({
    light: "위험",
    status: "CLOSED",
    importance: "HIGH",
    page: 2,
  });
  expect(fetcher.mock.calls[0][0]).not.toMatch(/status=|importance=|page=/);
  await getProjectStatusIssues({
    status: "OVERDUE",
    projectId: "0001",
    importance: "HIGH",
    page: 2,
    size: 20,
  });
  expect(fetcher.mock.calls[1][0]).toContain("projectId=0001");
  expect(fetcher.mock.calls[1][0]).not.toContain("light=");
});
