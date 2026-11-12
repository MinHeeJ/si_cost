import { afterEach, expect, it, vi } from "vitest";
import {
  createB9Validation,
  saveB9Apply,
  getB9Template,
  getUploadResult,
} from "./b9Api";
import { ApiError } from "../../shared/api";
afterEach(() => vi.unstubAllGlobals());
it("검사와 반영은 파일·year·uploadId만 FormData로 전송하고 Content-Type을 브라우저가 정한다", async () => {
  const fetcher = vi
    .fn()
    .mockImplementation(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({ success: true, meta: {}, data: { uploadId: 71 } }),
          { headers: { "Content-Type": "application/json" } },
        ),
      ),
    );
  vi.stubGlobal("fetch", fetcher);
  const file = new File(["workbook"], "selected.xlsx");
  await createB9Validation(file, 2027);
  const [path, options] = fetcher.mock.calls[0] as [string, RequestInit];
  expect(path).toBe("/api/excel/B9/validate");
  const form = options.body as FormData;
  expect([...form.keys()]).toEqual(["file", "year"]);
  expect(form.get("year")).toBe("2027");
  expect((form.get("file") as File).name).toBe("selected.xlsx");
  expect(new Headers(options.headers).has("Content-Type")).toBe(false);
  expect(options.redirect).toBe("error");
  await saveB9Apply(file, 71, 2027);
  const [applyPath, applyOptions] = fetcher.mock.calls[1] as [
    string,
    RequestInit,
  ];
  expect(applyPath).toBe("/api/excel/B9/apply");
  expect((applyOptions.body as FormData).get("uploadId")).toBe("71");
  expect([...(applyOptions.body as FormData).keys()]).toEqual([
    "file",
    "year",
    "uploadId",
  ]);
});
it("422 API오류의 details와 requestId를 보존하고 자동재시도하지 않는다", async () => {
  const body = {
    success: false,
    meta: {},
    code: "UPLOAD_VALIDATION_FAILED",
    message: "최신 기준 오류",
    field: "file",
    requestId: "b9-request",
    details: { uploadId: 71 },
  };
  const fetcher = vi
    .fn()
    .mockResolvedValue(new Response(JSON.stringify(body), { status: 422 }));
  vi.stubGlobal("fetch", fetcher);
  await expect(
    saveB9Apply(new File(["x"], "a.xlsx"), 71, 2026),
  ).rejects.toMatchObject({ status: 422, body });
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it("결과는 실제 선택 uploadId 경로로 조회하고 양식에는 year 쿼리를 보내지 않는다", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({ success: true, meta: {}, data: { uploadId: 82 } }),
        { headers: { "Content-Type": "application/json" } },
      ),
    )
    .mockResolvedValueOnce(
      new Response(new Blob(["xlsx bytes"]), {
        headers: {
          "Content-Type":
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "Content-Disposition": 'attachment; filename="projects.xlsx"',
        },
      }),
    );
  vi.stubGlobal("fetch", fetcher);
  await getUploadResult(82);
  await getB9Template();
  expect(fetcher.mock.calls[0][0]).toBe("/api/uploads/82/result");
  expect(fetcher.mock.calls[1][0]).toBe("/api/excel/B9/template");
});
it("실패 JSON을 파일로 저장하지 않는다", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "기록 실패",
          requestId: "download-id",
        }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      ),
    ),
  );
  await expect(getB9Template()).rejects.toBeInstanceOf(ApiError);
});
