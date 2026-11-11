import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  getDeveloperStates,
  createDeveloperState,
  putDeveloperStateEnd,
  saveDeveloperStateCancel,
} from "./developerStatesApi";
beforeEach(() => vi.stubGlobal("fetch", vi.fn()));
const reply = (data: unknown) =>
  new Response(JSON.stringify({ success: true, meta: {}, data }));
it("전체 이력은 페이지·actor 없이 선택한 개발자의 상대경로로 조회한다", async () => {
  const data = { items: [], hireDate: null, revision: "opaque" };
  vi.mocked(fetch).mockResolvedValue(reply(data));
  expect(await getDeveloperStates("DEV-000019")).toEqual(data);
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
    "/api/developers/DEV-000019/states",
  );
});
it("생성은 POST·숫자sn·warnings와 계약 필드만 전송한다", async () => {
  vi.mocked(fetch).mockResolvedValue(reply({ sn: 31, warnings: [] }));
  const body = {
    state: "WORK" as const,
    startDate: "2026-06-16",
    reason: null,
    expectedRevision: "opaque",
  };
  expect((await createDeveloperState("DEV-000019", body)).sn).toBe(31);
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/developers/DEV-000019/states");
  expect(options?.method).toBe("POST");
  expect(JSON.parse(String(options?.body))).toEqual(body);
});
it("종료일수정은 PATCH·숫자sn의 end 경로와 explicit NULL", async () => {
  vi.mocked(fetch).mockResolvedValue(reply({ changed: true }));
  await putDeveloperStateEnd("DEV-000019", 31, {
    endDate: null,
    expectedRevision: "opaque",
  });
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/developers/DEV-000019/states/31/end");
  expect(options?.method).toBe("PATCH");
  expect(JSON.parse(String(options?.body))).toEqual({
    endDate: null,
    expectedRevision: "opaque",
  });
});
it("취소는 DELETE가 아닌 명시POST이고 오류/requestId를 보존하며 재전송하지 않는다", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "CLOSED_MONTH",
        message: "복귀월 마감",
        requestId: "request-cancel",
      }),
      { status: 409 },
    ),
  );
  await expect(
    saveDeveloperStateCancel("DEV-000019", 31, {
      reason: "오입력",
      expectedRevision: "opaque",
    }),
  ).rejects.toBeInstanceOf(ApiError);
  expect(fetch).toHaveBeenCalledOnce();
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/developers/DEV-000019/states/31/cancel");
  expect(options?.method).toBe("POST");
});
