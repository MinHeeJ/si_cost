import { act, renderHook } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, it, expect, vi } from "vitest";
import type { ReactNode } from "react";
import {
  AnalysisAssistantProvider,
  useAnalysisAssistant,
} from "./AnalysisAssistantContext";
import type { AnalysisDetail, AnalysisFinal } from "./analysisClient";
const transport = vi.hoisted(() => ({
  getQuestion: vi.fn(),
  sendQuestion: vi.fn(),
  listQuestions: vi.fn(),
}));
vi.mock("./analysisClient", () => transport);
function wrapper({ children }: { children: ReactNode }) {
  return (
    <MemoryRouter initialEntries={["/UI-04"]}>
      <AnalysisAssistantProvider>{children}</AnalysisAssistantProvider>
    </MemoryRouter>
  );
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => (resolve = r));
  return { promise, resolve };
}
const failed: AnalysisFinal = {
  persisted: false,
  qnaId: null,
  status: "ERR",
  result: null,
  serverErrorId: "test-transport",
  message: "테스트 전송 실패",
};
beforeEach(() => {
  vi.resetAllMocks();
  transport.listQuestions.mockResolvedValue({
    items: [],
    nextCursor: null,
    hasMore: false,
  });
});
describe("shared analysis request ownership", () => {
  it("locks repeated history clicks before the detail lookup resolves", async () => {
    const lookup = deferred<AnalysisDetail>(),
      request = deferred<AnalysisFinal>();
    transport.getQuestion.mockReturnValue(lookup.promise);
    transport.sendQuestion.mockReturnValue(request.promise);
    const { result } = renderHook(() => useAnalysisAssistant(), { wrapper });
    let first!: Promise<void>;
    act(() => {
      first = result.current.askAgain("1");
      void result.current.askAgain("1");
    });
    expect(transport.getQuestion).toHaveBeenCalledTimes(1);
    const detail = {
      question: "이번 달 매출",
      result: { context: { screenId: "UI-08", baseMonth: "2026-02" } },
    } as AnalysisDetail;
    await act(async () => {
      lookup.resolve(detail);
      await lookup.promise;
    });
    expect(transport.sendQuestion).toHaveBeenCalledTimes(1);
    expect(transport.sendQuestion.mock.calls[0].slice(0, 2)).toEqual([
      "이번 달 매출",
      detail.result.context,
    ]);
    await act(async () => {
      request.resolve(failed);
      await first;
    });
    expect(result.current.pending).toBe(false);
  });
  it("captures screen targets/period once and rejects duplicate send while in flight", async () => {
    const request = deferred<AnalysisFinal>();
    transport.sendQuestion.mockReturnValue(request.promise);
    const { result } = renderHook(() => useAnalysisAssistant(), { wrapper });
    let running!: Promise<void>;
    act(() => {
      window.dispatchEvent(
        new CustomEvent("sicost:screen-period-changed", {
          detail: {
            screenId: "UI-04",
            value: { type: "YEAR_MONTH", value: "2026-10" },
          },
        }),
      );
      window.dispatchEvent(
        new CustomEvent("sicost:analysis-context", {
          detail: { screenId: "UI-04", projectId: "P-0001" },
        }),
      );
      running = result.current.send("이 프로젝트 원가");
      void result.current.send("같은 요청");
      window.dispatchEvent(
        new CustomEvent("sicost:analysis-context", {
          detail: { screenId: "UI-04", projectId: "P-0002" },
        }),
      );
    });
    expect(transport.sendQuestion).toHaveBeenCalledTimes(1);
    expect(transport.sendQuestion.mock.calls[0][1]).toEqual({
      screenId: "UI-04",
      baseMonth: "2026-10",
      projectId: "P-0001",
    });
    await act(async () => {
      request.resolve(failed);
      await running;
    });
    expect(result.current.error).toContain("test-transport");
  });
  it("validates before transport and never automatically retries an ambiguous disconnect", async () => {
    transport.sendQuestion.mockRejectedValue(new Error("연결 단절"));
    const { result } = renderHook(() => useAnalysisAssistant(), { wrapper });
    await act(async () => {
      await result.current.send("   ");
      await result.current.send("가".repeat(1001));
    });
    expect(transport.sendQuestion).not.toHaveBeenCalled();
    await act(async () => {
      await result.current.send("이번 달 매출");
    });
    expect(transport.sendQuestion).toHaveBeenCalledTimes(1);
    expect(result.current.error).toContain("자동 재전송하지 않습니다");
    expect(result.current.pending).toBe(false);
  });
});
