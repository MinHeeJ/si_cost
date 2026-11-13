import { beforeEach, expect, it, vi } from "vitest";

import {
  createFinancialPreview,
  getFinancialYearStatus,
} from "./financialPreviewApi";
beforeEach(() => vi.stubGlobal("fetch", vi.fn()));
it("연도현황은 상대경로 query와 취소 signal을 사용한다", async () => {
  const data = { year: "2026", months: [] };
  vi.mocked(fetch).mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data })),
  );
  const controller = new AbortController();
  expect(await getFinancialYearStatus("2026", controller.signal)).toEqual(data);
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/financial-items/year-status?year=2026");
  expect(options?.signal).toBe(controller.signal);
  expect(options?.body).toBeUndefined();
});
it("미리보기는 두탭 명시 변경과 문자열 ID·원금액·revision을 그대로 보낸다", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({ success: true, meta: {}, data: { copiedCount: 0 } }),
    ),
  );
  const body = {
    month: "2026-07",
    expectedRevision: "current",
    action: "CALCULATE" as const,
    items: { deleteIds: ["99999"] },
    transfers: {
      update: [
        {
          transferId: "9999999999",
          date: "2026-07-01",
          classCode: "EXP" as const,
          itemName: "이체",
          amount: "999999999999999",
        },
      ],
    },
  };
  await createFinancialPreview(body);
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/financial-items/preview");
  expect(options?.method).toBe("POST");
  expect(JSON.parse(options?.body as string)).toEqual(body);
});
it("충돌 오류는 requestId와 field를 보존하며 자동 재전송하지 않는다", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "STALE_WRITE",
        field: "expectedRevision",
        message: "다시 조회해 주세요.",
        requestId: "preview-stale",
      }),
      { status: 409 },
    ),
  );
  await expect(
    createFinancialPreview({
      month: "2026-07",
      expectedRevision: "old",
      action: "LOAD_PREVIOUS",
    }),
  ).rejects.toMatchObject({
    body: { requestId: "preview-stale", code: "STALE_WRITE" },
  });
  expect(fetch).toHaveBeenCalledOnce();
});
