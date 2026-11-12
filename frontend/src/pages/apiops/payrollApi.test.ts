import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import * as api from "./payrollApi";
beforeEach(() => vi.stubGlobal("fetch", vi.fn()));
function response(data: unknown) {
  return new Response(JSON.stringify({ success: true, meta: {}, data }), {
    headers: { "Content-Type": "application/json" },
  });
}
it("급여 조회는 허용 query만 상대경로로 보내고 decimal 원값 보존", async () => {
  const data = {
    items: [],
    total: 0,
    page: 2,
    size: 20,
    month: "2026-07",
    revision: "empty-month",
  };
  vi.mocked(fetch).mockResolvedValue(response(data));
  expect(
    await api.getPayroll({ month: "2026-07", q: " A_% ", page: 2, size: 20 }),
  ).toEqual(data);
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  const query = new URL(String(path), "https://test.invalid").searchParams;
  expect(String(path)).toMatch(/^\/api\/payroll\?/);
  expect([...query.keys()]).toEqual(["month", "page", "size", "q"]);
  expect(query.get("q")).toBe("A_%");
  expect(options?.body).toBeUndefined();
});
it("B1 검사는 월과 File 및 재검사 uploadId만 multipart", async () => {
  vi.mocked(fetch).mockResolvedValue(response({ uploadId: 9 }));
  const file = new File(["bytes"], "payroll.xlsx");
  await api.createB1Validation(file, "2026-07", 9);
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/excel/B1/validate");
  const body = options?.body as FormData;
  expect([...body.keys()]).toEqual(["file", "month", "uploadId"]);
  expect(body.get("file")).toBe(file);
  expect(body.get("month")).toBe("2026-07");
  expect(options?.headers).toBeUndefined();
});
it("반영 exact multipart는 snapshot revision과 전량교체 명시, legacy project 필드 없음", async () => {
  const data = {
    uploadId: 9,
    status: "APPLIED",
    results: [{ rowNumber: 2, developerId: "D-PAY" }],
    revision: "first",
  };
  vi.mocked(fetch).mockResolvedValue(response(data));
  const file = new File(["bytes"], "payroll.xlsx");
  expect(
    await api.createB1Apply(file, {
      month: "2026-07",
      uploadId: 9,
      expectedRevision: "checked",
      confirmFullReplacement: true,
    }),
  ).toEqual(data);
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/excel/B1/apply");
  const body = options?.body as FormData;
  expect([...body.keys()]).toEqual([
    "file",
    "month",
    "uploadId",
    "expectedRevision",
    "confirmFullReplacement",
  ]);
  expect(body.get("confirmFullReplacement")).toBe("true");
  expect(body.get("expectedRevision")).toBe("checked");
});
it("새422 코드를 원형 유지하고 자동재시도 없음", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "REVALIDATION_FAILED",
        message: "급여 오류",
        requestId: "pay-422",
        details: { issueCount: 1 },
      }),
      { status: 422 },
    ),
  );
  await expect(
    api.createB1Apply(new File(["bytes"], "payroll.xlsx"), {
      month: "2026-07",
      uploadId: 9,
      expectedRevision: "checked",
      confirmFullReplacement: true,
    }),
  ).rejects.toMatchObject({
    status: 422,
    body: { code: "REVALIDATION_FAILED", requestId: "pay-422" },
  });
  expect(fetch).toHaveBeenCalledOnce();
});
it("업로드 기록은 선택월/페이지, 결과는 선택 uploadId 사용", async () => {
  vi.mocked(fetch).mockImplementation(async () => response({ items: [] }));
  await api.getPayrollUploads("2026-06", 3, 100);
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
    "/api/payroll/uploads?month=2026-06&page=3&size=100",
  );
  await api.getPayrollResult(29);
  expect(vi.mocked(fetch).mock.calls[1][0]).toBe("/api/uploads/29/result");
});
it("PAYROLL export에 paging 제외·표시단위와 열 전달", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(new Blob(), {
      headers: {
        "Content-Type":
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Content-Disposition": 'attachment; filename="payroll.xlsx"',
      },
    }),
  );
  await api.exportPayroll(
    { month: "2026-07", q: "검색", page: 3, size: 100 },
    ["payAmount", "realPayAmount"],
    "백만원",
  );
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/exports/master-data");
  expect(JSON.parse(String(options?.body))).toEqual({
    view: "PAYROLL",
    filters: { month: "2026-07", q: "검색" },
    columns: ["payAmount", "realPayAmount"],
    amountUnit: "백만원",
  });
});
it("양식 오류는 파일로 저장하지 않고 ApiError 보존", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "INTERNAL_ERROR",
        message: "기록 실패",
        requestId: "pay-download",
      }),
      { status: 500, headers: { "Content-Type": "application/json" } },
    ),
  );
  await expect(api.getPayrollTemplate()).rejects.toBeInstanceOf(ApiError);
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe("/api/excel/B1/template");
});
