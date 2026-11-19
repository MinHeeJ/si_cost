import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
const engine = vi.hoisted(() => ({
  setOption: vi.fn(),
  resize: vi.fn(),
  dispose: vi.fn(),
}));
vi.mock("echarts/core", () => ({ use: vi.fn(), init: vi.fn(() => engine) }));
import { ChartHost } from "./ChartHost";
let notifyResize: ResizeObserverCallback;
const disconnect = vi.fn();
beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal(
    "ResizeObserver",
    class {
      constructor(callback: ResizeObserverCallback) {
        notifyResize = callback;
      }
      observe() {}
      disconnect = disconnect;
    },
  );
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
});
afterEach(() => vi.unstubAllGlobals());
it("SVG host는 정확한 옵션·reduced-motion을 적용하고 재노출 크기 변경과 dispose를 처리한다", () => {
  const view = render(
    <ChartHost
      label="매출"
      testId="trend"
      option={{ series: [{ type: "line", data: [0, null, -1] }] }}
      fallback={<p>숫자표</p>}
    />,
  );
  expect(engine.setOption).toHaveBeenCalledWith(
    expect.objectContaining({ animation: false, aria: { enabled: true } }),
    { notMerge: true },
  );
  const host = screen.getByRole("img", { name: "매출" });
  Object.defineProperty(host, "clientWidth", { value: 640 });
  act(() => notifyResize([], {} as ResizeObserver));
  expect(engine.resize).toHaveBeenCalledTimes(1);
  view.unmount();
  expect(disconnect).toHaveBeenCalledTimes(1);
  expect(engine.dispose).toHaveBeenCalledTimes(1);
});
it("렌더러가 없는 환경은 기존 숫자표/SVG를 보여 준다", () => {
  vi.stubGlobal("ResizeObserver", undefined);
  render(
    <ChartHost
      label="원가"
      testId="costs"
      option={{}}
      fallback={<p>원천 숫자표</p>}
    />,
  );
  expect(screen.getByText("원천 숫자표")).toBeVisible();
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
});
