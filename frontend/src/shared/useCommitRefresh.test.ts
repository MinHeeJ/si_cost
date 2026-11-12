import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { COMMITTED_REFRESH_FAILED, useCommitRefresh } from "./useCommitRefresh";

describe("useCommitRefresh", () => {
  it("사용자가 저장하면 '저장했습니다.'를 알린다", async () => {
    const { result } = renderHook(() => useCommitRefresh());
    await act(() =>
      result.current.commit(
        async () => {},
        async () => {},
      ),
    );
    expect(result.current.message).toBe("저장했습니다.");
  });
  it("자동 저장(quiet)은 성공해도 메시지를 만들지 않고 앞선 메시지도 지우지 않는다", async () => {
    const { result } = renderHook(() => useCommitRefresh());
    await act(() =>
      result.current.commit(
        async () => {},
        async () => {},
        { quiet: true },
      ),
    );
    expect(result.current.message).toBeNull();
    await act(() =>
      result.current.commit(
        async () => {},
        async () => {},
      ),
    );
    expect(result.current.message).toBe("저장했습니다.");
    await act(() =>
      result.current.commit(
        async () => {},
        async () => {},
        { quiet: true },
      ),
    );
    expect(result.current.message).toBe("저장했습니다.");
  });
  it("자동 저장(quiet)도 실패는 숨기지 않는다", async () => {
    const { result } = renderHook(() => useCommitRefresh());
    await act(async () => {
      await result.current
        .commit(
          async () => {
            throw new Error("저장하지 못했습니다.");
          },
          async () => {},
          { quiet: true },
        )
        .catch(() => {});
    });
    expect(result.current.message).toBe("저장하지 못했습니다.");
    await act(() =>
      result.current.commit(
        async () => {},
        async () => {
          throw new Error("x");
        },
        { quiet: true },
      ),
    );
    expect(result.current.message).toBe(COMMITTED_REFRESH_FAILED);
  });
});
