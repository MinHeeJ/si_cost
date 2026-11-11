import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { getUploadIssues, getUploadIssuesXlsx } from "./uploadIssuesApi";
const mime =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
it("조회는 선택한 ID·severity·페이지를 사용하고 전체는 severity를 생략한다", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: { items: [], total: 0, page: 2, size: 20 },
      }),
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await getUploadIssues(17, "WARN", 2, 20);
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/uploads/17/issues?page=2&size=20&severity=WARN",
  );
  fetcher.mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: { items: [], total: 0, page: 1, size: 50 },
      }),
    ),
  );
  await getUploadIssues(18, "", 1, 50);
  expect(fetcher.mock.calls[1][0]).toBe(
    "/api/uploads/18/issues?page=1&size=50",
  );
});
it("다운로드는 페이지·선택 인자 없이 요청하고 XLSX와 서버 파일명을 반환한다", async () => {
  const filename = "거래처_업로드검사_17_20260615_090000.xlsx";
  const fetcher = vi.fn().mockResolvedValue(
    new Response(new Uint8Array([80, 75, 3, 4]), {
      headers: {
        "Content-Type": mime,
        "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
      },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const file = await getUploadIssuesXlsx(17, "WARN");
  expect(file.filename).toBe(filename);
  expect(file.blob.size).toBe(4);
  expect(fetcher).toHaveBeenCalledWith(
    "/api/uploads/17/issues.xlsx?severity=WARN",
    { redirect: "error" },
  );
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it.each([400, 404, 500])(
  "다운로드 오류 %s는 JSON 오류를 먼저 해석하고 재시도하지 않는다",
  async (status) => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "다운로드 실패",
          requestId: "test",
        }),
        { status, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetcher);
    await expect(getUploadIssuesXlsx(17, "")).rejects.toBeInstanceOf(ApiError);
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0][0]).toBe("/api/uploads/17/issues.xlsx");
  },
);
it("HTML 성공과 JSON 성공은 다운로드 파일로 저장하지 않는다", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(
      new Response("<html>login</html>", {
        headers: { "Content-Type": "text/html" },
      }),
    )
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ success: true }), {
        headers: { "Content-Type": "application/json" },
      }),
    );
  vi.stubGlobal("fetch", fetcher);
  await expect(getUploadIssuesXlsx(17, "ERR")).rejects.toThrow();
  await expect(getUploadIssuesXlsx(17, "ERR")).rejects.toThrow();
});
