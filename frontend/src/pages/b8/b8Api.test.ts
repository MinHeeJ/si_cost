import { beforeEach, expect, it, vi } from "vitest";
import { createB8Validation, saveB8Apply, getB8Template } from "./b8Api";
import { ApiError } from "../../shared/api";
beforeEach(() => vi.stubGlobal("fetch", vi.fn()));
const summary = {
  totalRows: 1,
  successRows: 1,
  warningRows: 0,
  errorRows: 0,
  fileWarningCount: 0,
};
it("검사에는 원본 File만 multipart로 보내며 JSON Content-Type을 지정하지 않는다", async () => {
  const data = {
    uploadId: 12,
    summary,
    canApply: true,
    issues: { items: [], total: 0, page: 1, size: 50 },
    expectedNumbers: [{ rowNumber: 2, managementNo: "K-0001" }],
  };
  vi.mocked(fetch).mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data }), {
      headers: { "Content-Type": "application/json" },
    }),
  );
  const file = new File(["bytes"], "developers.xlsx");
  expect(await createB8Validation(file)).toEqual(data);
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/excel/B8/validate");
  expect(options?.body).toBeInstanceOf(FormData);
  expect((options?.body as FormData).get("file")).toBe(file);
  expect((options?.body as FormData).has("uploadId")).toBe(false);
  expect(options?.headers).toBeUndefined();
});
it("반영은 같은 파일·숫자 uploadId를 재전송하고 최종번호 DTO를 그대로 반환한다", async () => {
  const data = {
    summary,
    finalNumbers: [{ rowNumber: 2, managementNo: "K-0010", id: "DEV-000010" }],
  };
  vi.mocked(fetch).mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data })),
  );
  const file = new File(["bytes"], "developers.xlsx");
  expect(await saveB8Apply(file, 12)).toEqual(data);
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/excel/B8/apply");
  expect((options?.body as FormData).get("uploadId")).toBe("12");
  expect((options?.body as FormData).get("file")).toBe(file);
});
it("422 진단과 requestId를 잃지 않고 자동 재전송하지 않는다", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "UPLOAD_VALIDATION_FAILED",
        message: "재검사 오류",
        requestId: "request-422",
        details: { summary },
      }),
      { status: 422 },
    ),
  );
  await expect(
    saveB8Apply(new File(["bytes"], "developers.xlsx"), 12),
  ).rejects.toBeInstanceOf(ApiError);
  expect(fetch).toHaveBeenCalledOnce();
});
it("양식은 실제 XLSX Content-Type과 서버 파일명을 확인하고 상대경로로 요청한다", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(new Blob(["workbook"]), {
      headers: {
        "Content-Type":
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent("B8_개발자양식_20260615_090000.xlsx")}`,
      },
    }),
  );
  expect((await getB8Template()).filename).toBe(
    "B8_개발자양식_20260615_090000.xlsx",
  );
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe("/api/excel/B8/template");
});
