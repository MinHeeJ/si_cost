import { afterEach, describe, it, expect, vi } from "vitest";
import { sendQuestion } from "./analysisClient";
function response(text: string, splitEveryByte = false) {
  const bytes = new TextEncoder().encode(text);
  return new Response(
    new ReadableStream({
      start(controller) {
        if (splitEveryByte) {
          for (const byte of bytes) controller.enqueue(new Uint8Array([byte]));
        } else controller.enqueue(bytes);
        controller.close();
      },
    }),
    { headers: { "Content-Type": "text/event-stream" } },
  );
}
afterEach(() => vi.unstubAllGlobals());
describe("analysis SSE transport", () => {
  it("decodes Korean and CRLF even when every byte/line ending crosses a read boundary", async () => {
    const final = {
      persisted: false,
      qnaId: null,
      status: "ERR",
      result: null,
      serverErrorId: "error-ref",
      message: "기록을 확인하세요",
    };
    const progress = {
      phase: "FINDING_DATA",
      message: "데이터 찾는 중",
      acceptedAt: "2026-11-16T10:00:00+09:00",
    };
    const fetch = vi
      .fn()
      .mockResolvedValue(
        response(
          `event:progress\r\ndata:${JSON.stringify({ success: true, data: progress })}\r\n\r\nevent:final\r\ndata:${JSON.stringify({ success: true, data: final })}\r\n\r\n`,
          true,
        ),
      );
    vi.stubGlobal("fetch", fetch);
    const onProgress = vi.fn();
    expect(
      await sendQuestion(
        "매출",
        { screenId: "UI-08", baseMonth: "2026-10" },
        onProgress,
      ),
    ).toEqual(final);
    expect(onProgress).toHaveBeenCalledTimes(1);
    expect(onProgress).toHaveBeenCalledWith(progress);
    expect(fetch).toHaveBeenCalledOnce();
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      question: "매출",
      context: { screenId: "UI-08", baseMonth: "2026-10" },
    });
  });
  it("never retries a disconnect and tells the user to check saved history", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(
        response(
          'event:progress\ndata:{"success":true,"data":{"phase":"FINDING_DATA"}}\n\n',
        ),
      );
    vi.stubGlobal("fetch", fetch);
    await expect(sendQuestion("매출", {}, () => {})).rejects.toThrow(
      "자동 재전송하지 않습니다",
    );
    expect(fetch).toHaveBeenCalledOnce();
  });
  it("does not call a non-2xx response a completed SSE question", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: false,
            code: "INVALID_INPUT",
            message: "입력 오류",
            requestId: "validation-ref",
          }),
          { status: 400, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );
    await expect(sendQuestion("", {}, () => {})).rejects.toMatchObject({
      status: 400,
    });
  });
});
