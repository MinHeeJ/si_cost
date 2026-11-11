import { afterEach, expect, it, vi } from "vitest";
import * as api from "./businessUsersApi";
import { ApiError } from "../../shared/api";
afterEach(() => vi.unstubAllGlobals());
it("역할 선택은 필수 purpose를 기존 옵션 클라이언트로 전달한다", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: { items: [], roleOptions: [] },
      }),
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await api.getBusinessRoleOptions();
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/business-users/options?purpose=PM",
  );
});
it("사용자 조회는 상대경로와 실제 필터만 전송한다", async () => {
  const fetcher = vi
    .fn()
    .mockImplementation(
      async () =>
        new Response(
          JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
        ),
    );
  vi.stubGlobal("fetch", fetcher);
  await api.getBusinessUsers();
  expect(fetcher.mock.calls[0][0]).toBe("/api/business-users");
  await api.getBusinessUsers({
    q: "이름",
    businessRole: "PM",
    status: "LOCK",
    page: 2,
    size: 20,
  });
  const query = new URL(fetcher.mock.calls[1][0], "https://test.invalid")
    .searchParams;
  expect(Object.fromEntries(query)).toEqual({
    q: "이름",
    businessRole: "PM",
    status: "LOCK",
    page: "2",
    size: "20",
  });
});
it("생성과 수정은 별도 payload이며 선택 아이디만 경로로 인코딩한다", async () => {
  const fetcher = vi
    .fn()
    .mockImplementation(
      async () =>
        new Response(JSON.stringify({ success: true, meta: {}, data: {} })),
    );
  vi.stubGlobal("fetch", fetcher);
  await api.createBusinessUser({
    userId: "ab12",
    name: "담당",
    email: "user@example.org",
    businessRoleIds: ["PM"],
  });
  await api.putBusinessUser("선택/아이디", {
    name: "수정",
    email: "user@example.org",
    businessRoleIds: [],
    expectedRevision: "revision",
  });
  expect(fetcher.mock.calls[0][1].method).toBe("POST");
  expect(JSON.parse(fetcher.mock.calls[0][1].body).userId).toBe("ab12");
  expect(fetcher.mock.calls[1][0]).toBe(
    `/api/business-users/${encodeURIComponent("선택/아이디")}`,
  );
  expect(JSON.parse(fetcher.mock.calls[1][1].body)).not.toHaveProperty(
    "userId",
  );
  await api.getBusinessUserHistory("선택/아이디", 2);
  expect(fetcher.mock.calls[2][0]).toContain("targetType=USER");
  expect(fetcher.mock.calls[2][0]).toContain("page=2");
});
it("오류 envelope는 root field와 requestId를 유지하고 자동 재시도하지 않는다", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "STALE_WRITE",
        message: "다시 조회해 주세요.",
        field: "name",
        requestId: "trace",
      }),
      { status: 409 },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await expect(api.getBusinessUser("ab12")).rejects.toBeInstanceOf(ApiError);
  expect(fetcher).toHaveBeenCalledTimes(1);
});
