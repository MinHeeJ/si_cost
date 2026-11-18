import { act, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { ChartHost } from "./ChartHost";
import { init } from "echarts/core";

vi.mock("echarts/core", () => ({ init: vi.fn(), use: vi.fn() }));
afterEach(() => vi.unstubAllGlobals());

it("keeps the SVG fallback when hidden, resizes on reveal, applies reduced motion and disposes the chart/observer", () => {
  let resize: ResizeObserverCallback = () => {};
  const disconnect = vi.fn();
  class Observer {
    constructor(callback: ResizeObserverCallback) {
      resize = callback;
    }
    observe() {} // No layout is synthesized until the explicit reveal below.
    disconnect = disconnect;
  }
  vi.stubGlobal("ResizeObserver", Observer);
  let width = 0;
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(
    () => ({
      x: 0,
      y: 0,
      top: 0,
      left: 0,
      bottom: 300,
      right: width,
      width,
      height: 300,
      toJSON() {},
    }),
  );
  const media = {
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  };
  vi.stubGlobal("matchMedia", () => media);
  const chart = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() };
  vi.mocked(init).mockReturnValue(chart as unknown as ReturnType<typeof init>);
  const { unmount } = render(
    <ChartHost testId="host" label="실제 차트" option={{ series: [] }}>
      <svg role="img" aria-label="대체 숫자 그래프" />
    </ChartHost>,
  );
  expect(screen.getByRole("img", { name: "대체 숫자 그래프" })).toBeVisible();
  expect(init).not.toHaveBeenCalled();
  width = 640;
  act(() => resize([], {} as ResizeObserver));
  expect(init).toHaveBeenCalledWith(
    expect.any(HTMLElement),
    expect.anything(),
    { renderer: "svg" },
  );
  expect(chart.setOption).toHaveBeenCalledWith(
    expect.objectContaining({ animation: false }),
  );
  expect(screen.getByRole("img", { name: "실제 차트" })).toBeVisible();
  expect(
    screen.queryByRole("img", { name: "대체 숫자 그래프" }),
  ).not.toBeInTheDocument();
  act(() => window.dispatchEvent(new Event("resize")));
  expect(chart.resize).toHaveBeenCalledTimes(2);
  unmount();
  expect(chart.dispose).toHaveBeenCalledOnce();
  expect(disconnect).toHaveBeenCalledOnce();
});

it("falls back to the original SVG when the renderer cannot start", () => {
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({
    width: 640,
    height: 300,
  } as DOMRect);
  vi.mocked(init).mockImplementation(() => {
    throw new Error("renderer unavailable");
  });
  render(
    <ChartHost testId="host" label="차트" option={{}}>
      <svg role="img" aria-label="원래 그래프" />
    </ChartHost>,
  );
  expect(screen.getByRole("img", { name: "원래 그래프" })).toBeVisible();
});
