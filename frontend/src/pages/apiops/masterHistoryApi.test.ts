import { afterEach, expect, it, vi } from "vitest";
import { getMasterHistory } from "./masterHistoryApi";
import { ApiError } from "../../shared/api";

afterEach(() => vi.unstubAllGlobals());
it("대상ID를 안전하게 query로 인코딩하고 공통 envelope를 해제한다", async () => {
  const data = { items: [], total: 0, page: 1, size: 50 };
  const fetcher = vi
    .fn()
    .mockResolvedValue(
      new Response(JSON.stringify({ success: true, meta: {}, data })),
    );
  vi.stubGlobal("fetch", fetcher);
  expect(await getMasterHistory("USER", "선택/대상&sort=DESC")).toEqual(data);
  const path = fetcher.mock.calls[0][0] as string;
  expect(path.startsWith("/api/master-history?")).toBe(true);
  const query = new URLSearchParams(path.split("?")[1]);
  expect([...query.keys()]).toEqual(["targetType", "targetId", "page", "size"]);
  expect(query.get("targetId")).toBe("선택/대상&sort=DESC");
  expect(query.get("size")).toBe("50");
});
it("오류는 로그인 전환 없이 root ApiError와 추적값으로 전달한다", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "NOT_FOUND",
          message: "대상 자료 없음",
          requestId: "history-trace",
        }),
        { status: 404 },
      ),
    ),
  );
  await expect(
    getMasterHistory("PART", "PART-001", 2, 20),
  ).rejects.toBeInstanceOf(ApiError);
});
it("대상 변경이나 닫기에서 호출자가 취소할 수 있다", async () => {
  const controller = new AbortController();
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: { items: [], total: 0, page: 2, size: 100 },
      }),
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await getMasterHistory("CLIENT", "C-001", 2, 100, controller.signal);
  expect(fetcher.mock.calls[0][1]).toEqual(
    expect.objectContaining({ signal: controller.signal, redirect: "error" }),
  );
});
