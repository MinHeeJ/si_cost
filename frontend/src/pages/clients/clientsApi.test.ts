import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import * as api from "./clientsApi";
import { clientFixture } from "./clientsFixtures";
afterEach(() => vi.unstubAllGlobals());
function fetcher() {
  const fn = vi
    .fn()
    .mockImplementation(
      async () =>
        new Response(JSON.stringify({ success: true, meta: {}, data: {} })),
    );
  vi.stubGlobal("fetch", fn);
  return fn;
}
it("전체는 useYn을 생략하고 검색과 구분과 페이지를 상대경로로 전송한다", async () => {
  const fetch = fetcher();
  await api.getClients({ type: "CUST", q: "123-45", page: 2, size: 20 });
  const url = fetch.mock.calls[0][0] as string;
  expect(url).toMatch(/^\/api\/clients\?/);
  expect(url).not.toContain("useYn");
  expect(url).toContain("type=CUST");
  expect(url).toContain("q=123-45");
});
it("선택id PUT과 신규POST를 구분하고 단건상태는 bulk 한항목이다", async () => {
  const fetch = fetcher();
  await api.createClient({
    type: "CUST",
    name: "신규",
    businessNo: "1234567890",
    taxEmail: "a@b.test",
  });
  await api.putClient(clientFixture.id, {
    type: "VNDR",
    name: "수정",
    accountAction: "KEEP",
    expectedRevision: clientFixture.revision,
  });
  await api.setClientStatus(clientFixture);
  expect(fetch.mock.calls[0][0]).toBe("/api/clients");
  expect(fetch.mock.calls[0][1].method).toBe("POST");
  expect(fetch.mock.calls[1][0]).toBe(`/api/clients/${clientFixture.id}`);
  expect(fetch.mock.calls[1][1].method).toBe("PUT");
  expect(JSON.parse(fetch.mock.calls[2][1].body)).toEqual({
    targetUseYn: "N",
    items: [{ id: clientFixture.id, expectedRevision: clientFixture.revision }],
  });
});
it("전체계좌는 캐시하지 않고 서버JSON 오류를 유지하며 자동재시도하지 않는다", async () => {
  const fetch = fetcher();
  fetch.mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "ENCRYPTION_CONFIG_ERROR",
        message: "설정 확인",
        requestId: "test",
      }),
      { status: 503 },
    ),
  );
  await expect(api.getClientAccount(clientFixture.id)).rejects.toBeInstanceOf(
    ApiError,
  );
  expect(fetch).toHaveBeenCalledOnce();
  expect(fetch.mock.calls[0][1].cache).toBe("no-store");
});
