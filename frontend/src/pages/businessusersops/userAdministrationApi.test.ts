import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import * as api from "./userAdministrationApi";
afterEach(() => vi.unstubAllGlobals());
it("담당과 활동은 선택ID를 인코딩하고 계약의 페이지 인자만 전송한다", async () => {
  const fetcher = vi
    .fn()
    .mockImplementation(
      async () =>
        new Response(JSON.stringify({ success: true, meta: {}, data: {} })),
    );
  vi.stubGlobal("fetch", fetcher);
  await api.getBusinessUserScopes("선택/아이디");
  await api.getBusinessUserAccessLogs("선택/아이디", 2, 20);
  expect(fetcher.mock.calls[0][0]).toBe(
    `/api/business-users/${encodeURIComponent("선택/아이디")}/scopes`,
  );
  expect(fetcher.mock.calls[1][0]).toBe(
    `/api/business-users/${encodeURIComponent("선택/아이디")}/access-logs?page=2&size=20`,
  );
});
it.each(["STOP", "RESUME", "UNLOCK"] as const)(
  "%s 명령은 revision만 보내고 사용자ID를 본문에 쓰지 않는다",
  async (action) => {
    const fetcher = vi
      .fn()
      .mockImplementation(
        async () =>
          new Response(JSON.stringify({ success: true, meta: {}, data: {} })),
      );
    vi.stubGlobal("fetch", fetcher);
    await api.changeBusinessUserState("selected", action, "snapshot");
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0][0]).toBe(
      `/api/business-users/selected/${action.toLowerCase()}`,
    );
    expect(fetcher.mock.calls[0][1].method).toBe("POST");
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({
      expectedRevision: "snapshot",
    });
  },
);
it("일괄은 action과 전체 선택 snapshot을 한 요청으로 전송한다", async () => {
  const fetcher = vi
    .fn()
    .mockImplementation(
      async () =>
        new Response(JSON.stringify({ success: true, meta: {}, data: {} })),
    );
  vi.stubGlobal("fetch", fetcher);
  const items = [{ userId: "selected", expectedRevision: "r" }];
  await api.saveBusinessUserStatusChanges("STOP", items);
  expect(fetcher.mock.calls[0][0]).toBe("/api/business-users/status-changes");
  expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({
    action: "STOP",
    items,
  });
});
it("쓰기 실패는 ApiError를 보존하고 자동 재전송하지 않는다", async () => {
  const fetcher = vi.fn().mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "STALE_WRITE",
          message: "최신값을 조회해 주세요.",
          requestId: "trace",
        }),
        { status: 409 },
      ),
  );
  vi.stubGlobal("fetch", fetcher);
  await expect(
    api.changeBusinessUserState("selected", "STOP", "old"),
  ).rejects.toBeInstanceOf(ApiError);
  expect(fetcher).toHaveBeenCalledTimes(1);
});
