import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { getPeopleHistory } from "./peopleClient";
import { ApiError } from "../../shared/api";

beforeEach(() => vi.resetAllMocks());
afterEach(() => vi.unstubAllGlobals());
it.each(["DEVELOPER", "CANDIDATE"] as const)(
  "%s 사람 이력 전용 상대 경로와 승인된 쿼리만 전송한다",
  async (type) => {
    const data = { items: [], total: 0, page: 1, size: 50 };
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ success: true, meta: {}, data })),
      );
    vi.stubGlobal("fetch", fetcher);
    expect(await getPeopleHistory(type, "선택/대상&actor=other")).toEqual(data);
    const path = fetcher.mock.calls[0][0] as string;
    expect(path.startsWith("/api/people-history?")).toBe(true);
    const query = new URLSearchParams(path.split("?")[1]);
    expect([...query.keys()]).toEqual([
      "targetType",
      "targetId",
      "page",
      "size",
    ]);
    expect(query.get("targetType")).toBe(type);
    expect(query.get("targetId")).toBe("선택/대상&actor=other");
    expect(query.get("page")).toBe("1");
    expect(query.get("size")).toBe("50");
  },
);
it("root ApiError를 문의 번호와 함께 보존하며 로그인/재저장을 하지 않는다", async () => {
  const body = {
    success: false,
    meta: {},
    code: "NOT_FOUND",
    message: "인력을 찾을 수 없습니다.",
    requestId: "people-trace",
  };
  const fetcher = vi
    .fn()
    .mockImplementation(
      async () => new Response(JSON.stringify(body), { status: 404 }),
    );
  vi.stubGlobal("fetch", fetcher);
  await expect(
    getPeopleHistory("CANDIDATE", "selected-person"),
  ).rejects.toMatchObject({ body, status: 404 });
  await expect(
    getPeopleHistory("CANDIDATE", "selected-person"),
  ).rejects.toBeInstanceOf(ApiError);
});
it("호출자가 닫기/대상 변경에서 요청을 취소할 수 있다", async () => {
  const controller = new AbortController();
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: { items: [], total: 0, page: 2, size: 20 },
      }),
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await getPeopleHistory(
    "DEVELOPER",
    "selected-person",
    2,
    20,
    controller.signal,
  );
  expect(fetcher.mock.calls[0][1]).toEqual(
    expect.objectContaining({ signal: controller.signal, redirect: "error" }),
  );
});
