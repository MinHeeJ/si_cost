import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, request } from "./api";

afterEach(() => vi.unstubAllGlobals());
describe("공통 API 봉투", () => {
  it("동일 origin 상대 API와 success/meta/data를 사용한다", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          meta: {},
          data: { value: "1.2300" },
        }),
        { status: 200 },
      ),
    );
    vi.stubGlobal("fetch", fetcher);
    expect(await request("/api/health")).toEqual({ value: "1.2300" });
    expect(fetcher.mock.calls[0][0]).toBe("/api/health");
  });
  it.each([201, 204])(
    "%i 빈 본문을 JSON으로 파싱하지 않는다",
    async (status) => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue(new Response(null, { status })),
      );
      expect(await request("/api/code-groups")).toBeUndefined();
    },
  );
  it("단일 field와 details.violations를 필드 오류로 투영한다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: false,
            meta: {},
            code: "VALIDATION_ERROR",
            message: "입력 오류",
            requestId: "req-1",
            field: "name",
            details: {
              violations: [{ field: "value", message: "소수 4자리" }],
            },
          }),
          { status: 400 },
        ),
      ),
    );
    try {
      await request("/api/health");
      throw new Error("성공할 수 없음");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).fieldMessages).toEqual({
        name: "입력 오류",
        value: "소수 4자리",
      });
      expect((error as ApiError).body.requestId).toBe("req-1");
    }
  });
  it("HTML 응답과 절대 URL을 거부하며 재시도하지 않는다", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(new Response("<html>failure</html>", { status: 503 }));
    vi.stubGlobal("fetch", fetcher);
    await expect(request("/api/health")).rejects.toThrow("서버 응답");
    expect(fetcher).toHaveBeenCalledTimes(1);
    await expect(request("https://invalid.example/api/health")).rejects.toThrow(
      "상대 경로",
    );
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
});
