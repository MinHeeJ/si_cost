import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ErrorBanner, FeedbackNotice, FloatingToast } from "./Feedback";

it("restarts the completion toast timer for a new message", () => {
  vi.useFakeTimers();
  try {
    const { rerender } = render(
      <FloatingToast message="저장 완료" durationMs={7000} />,
    );
    expect(screen.getByRole("status")).toHaveClass("sicost-tone-ok");
    act(() => { vi.advanceTimersByTime(4000); });
    rerender(<FloatingToast message="다운로드 완료" detail="파일 1개" durationMs={7000} />);
    act(() => { vi.advanceTimersByTime(6999); });
    expect(screen.getByRole("status")).toHaveTextContent("다운로드 완료");
    expect(screen.getByRole("status")).toHaveTextContent("파일 1개");
    act(() => { vi.advanceTimersByTime(1); });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  } finally {
    vi.useRealTimers();
  }
});

it("keeps warnings and errors visible and only renders requested controls", () => {
  vi.useFakeTimers();
  try {
    const dismiss = vi.fn();
    const retry = vi.fn();
    render(
      <>
        <FeedbackNotice tone="warn" title="입력값 확인" onDismiss={dismiss}>
          입금액을 확인해 주세요.
        </FeedbackNotice>
        <ErrorBanner title="청구 조회 실패" message="자료를 불러오지 못했습니다." onRetry={retry} />
      </>,
    );
    act(() => { vi.advanceTimersByTime(30000); });
    const [warning, error] = screen.getAllByRole("alert");
    expect(warning).toHaveClass("sicost-tone-warn");
    expect(warning).toHaveTextContent("입금액을 확인해 주세요.");
    expect(within(warning).getAllByRole("button")).toHaveLength(1);
    fireEvent.click(within(warning).getByRole("button", { name: "알림 닫기" }));
    expect(dismiss).toHaveBeenCalledOnce();
    expect(error).toHaveClass("sicost-tone-bad");
    fireEvent.click(within(error).getByRole("button", { name: "다시 조회" }));
    expect(retry).toHaveBeenCalledOnce();
  } finally {
    vi.useRealTimers();
  }
});
