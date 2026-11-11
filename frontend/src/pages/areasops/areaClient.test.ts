import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getAreaChildren,
  getAreaPath,
  getAreaSidos,
} from "../../shared/areaClient";
import { ApiError } from "../../shared/api";

afterEach(() => vi.unstubAllGlobals());
describe("행정구역 HTTP 클라이언트", () => {
  it("세 연산은 상대경로와 선택된 코드·단계만 사용한다", async () => {
    const fetcher = vi
      .fn()
      .mockImplementation(
        async () =>
          new Response(
            JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
            { status: 200 },
          ),
      );
    vi.stubGlobal("fetch", fetcher);
    const signal = new AbortController().signal;
    await getAreaSidos(signal);
    await getAreaChildren("0100000000", "SGG", signal);
    await getAreaPath("0101010000", signal);
    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      "/api/areas/sidos",
      "/api/areas/children?parentCode=0100000000&level=SGG",
      "/api/areas/0101010000/path",
    ]);
    expect(
      fetcher.mock.calls.every(
        (call) => call[1].signal === signal && call[1].body == null,
      ),
    ).toBe(true);
  });
  it("코드에 경로문자가 있더라도 세그먼트 밖으로 나갈 수 없다", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ success: true, meta: {}, data: {} })),
      );
    vi.stubGlobal("fetch", fetcher);
    await getAreaPath("invalid/code");
    expect(fetcher.mock.calls[0][0]).toBe("/api/areas/invalid%2Fcode/path");
  });
  it("서버 오류형식과 문의번호를 보존한다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: false,
            meta: {},
            code: "NOT_FOUND",
            message: "행정구역을 찾을 수 없습니다.",
            requestId: "area-request",
          }),
          { status: 404 },
        ),
      ),
    );
    await expect(getAreaPath("9999999999")).rejects.toBeInstanceOf(ApiError);
  });
});
