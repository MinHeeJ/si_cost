import { act, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { FloatingToast } from "./Feedback";

it("keeps the configured duration and restarts it for a new message or tone", () => {
  vi.useFakeTimers();
  try {
    const { rerender } = render(
      <FloatingToast message="저장했습니다." durationMs={7000} />,
    );
    expect(screen.getByRole("status")).toHaveClass("sicost-tone-ok");

    act(() => { vi.advanceTimersByTime(4000); });
    expect(screen.getByRole("status")).toBeVisible();
    rerender(
      <FloatingToast message="다시 확인해 주세요." tone="bad" durationMs={7000} />,
    );
    act(() => { vi.advanceTimersByTime(6999); });
    expect(screen.getByRole("status")).toHaveClass("sicost-tone-bad");
    expect(screen.getByRole("status")).toHaveTextContent("다시 확인해 주세요.");
    act(() => { vi.advanceTimersByTime(1); });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    rerender(
      <FloatingToast message="다시 확인해 주세요." tone="warn" durationMs={7000} />,
    );
    expect(screen.getByRole("status")).toHaveClass("sicost-tone-warn");
  } finally {
    vi.useRealTimers();
  }
});
