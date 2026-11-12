import { afterEach, expect, it, vi } from "vitest";
import {
  createB6Validation,
  saveB6Apply,
  getB6Template,
  getUploadResult,
  exportDistribution,
} from "./b6Api";
import { ApiError } from "../../shared/api";
afterEach(() => vi.unstubAllGlobals());
const file = new File(["workbook"], "selected.xlsx");
const context = { projectId: "P-selected", year: 2026, step: "PLAN" as const };
it("multipart는 계약의 file/year/projectId/step/ID/revision만 보내고 API 상대경로를 사용한다", async () => {
  const fetcher = vi
    .fn()
    .mockImplementation(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({ success: true, meta: {}, data: { uploadId: 71 } }),
        ),
      ),
    );
  vi.stubGlobal("fetch", fetcher);
  await createB6Validation(file, context);
  await saveB6Apply(file, 71, context, "opaque-revision");
  expect(fetcher.mock.calls[0][0]).toBe("/api/excel/B6/validate");
  const check = fetcher.mock.calls[0][1] as RequestInit;
  expect([...(check.body as FormData).keys()]).toEqual([
    "file",
    "year",
    "projectId",
    "step",
  ]);
  expect(new Headers(check.headers).has("Content-Type")).toBe(false);
  expect(fetcher.mock.calls[1][0]).toBe("/api/excel/B6/apply");
  const apply = fetcher.mock.calls[1][1] as RequestInit;
  expect([...(apply.body as FormData).keys()]).toEqual([
    "file",
    "year",
    "projectId",
    "step",
    "uploadId",
    "expectedRevision",
  ]);
  expect((apply.body as FormData).get("expectedRevision")).toBe(
    "opaque-revision",
  );
  expect((apply.body as FormData).get("projectId")).toBe("P-selected");
});
it("보호월의 월·차이·field/requestId/details를 표시할 수 있도록 보존하며 재시도하지 않는다", async () => {
  const details = { differences: [{ month: "2026-06", difference: "1" }] };
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "CLOSED_MONTH",
        message: "보호월 변경",
        field: "step",
        requestId: "b6-protected",
        details,
      }),
      { status: 409 },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await expect(
    saveB6Apply(file, 71, context, "revision"),
  ).rejects.toMatchObject({
    status: 409,
    message: "보호월 변경 (2026-06 차이 1원) [step]",
    body: { requestId: "b6-protected", details },
  });
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it("양식에는 projectId만, 결과에는 uploadId를, 조회출력에는 조회단계와 단위를 보낸다", async () => {
  const xlsx = () =>
    new Response(new Blob(["xlsx"]), {
      headers: {
        "Content-Type":
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Content-Disposition": 'attachment; filename="distribution.xlsx"',
      },
    });
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(xlsx())
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({ success: true, meta: {}, data: { uploadId: 82 } }),
      ),
    )
    .mockResolvedValueOnce(xlsx());
  vi.stubGlobal("fetch", fetcher);
  await getB6Template("P-selected");
  await getUploadResult(82);
  await exportDistribution("P-selected", "EST", "천원");
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/excel/B6/template?projectId=P-selected",
  );
  expect(fetcher.mock.calls[1][0]).toBe("/api/uploads/82/result");
  expect(fetcher.mock.calls[2][0]).toBe("/api/exports/master-data");
  expect(JSON.parse(fetcher.mock.calls[2][1].body)).toEqual({
    view: "PROJECT_DISTRIBUTION",
    projectId: "P-selected",
    step: "EST",
    amountUnit: "천원",
  });
});
it("실패 JSON을 XLSX로 저장하지 않는다", async () => {
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
  await expect(getB6Template("P-selected")).rejects.toBeInstanceOf(ApiError);
});
