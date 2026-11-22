import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { ChartHost } from "../../shared/charts/ChartHost";
import { BalanceTrendChart } from "./BalanceTrendChart";
vi.mock("../../shared/charts/ChartHost", () => ({
  ChartHost: vi.fn(({ children }: { children: ReactNode }) => (
    <div>{children}</div>
  )),
}));
it("reserves right-hand room for the full minimum cash guide without changing the adapter", () => {
  render(
    <BalanceTrendChart
      minimum="1000000000"
      points={[
        {
          month: "2026-11",
          balance: "950000000",
          status: "PLANNED",
          shortage: true,
        },
      ]}
    />,
  );
  expect(vi.mocked(ChartHost).mock.lastCall?.[0].option.grid).toMatchObject({
    right: 130,
    containLabel: true,
  });
  expect(
    screen.getByText("최소 운영자금", { exact: true }),
  ).toBeInTheDocument();
});
it("keeps complete large negative axis labels inside the chart", () => {
  const { container } = render(
    <BalanceTrendChart
      minimum="1000000000"
      points={[
        {
          month: "2026-01",
          balance: "-999999999999999",
          status: "PLANNED",
          shortage: true,
        },
      ]}
    />,
  );
  for (const text of container.querySelectorAll('text[text-anchor="end"]')) {
    if (text.textContent === "최소 운영자금") continue;
    expect(Number(text.getAttribute("x"))).toBeGreaterThan(
      (text.textContent?.length ?? 0) * 7,
    );
  }
});
it("breaks lines at unknown balances and connects last actual to planned with a dashed line", () => {
  const { container } = render(
    <BalanceTrendChart
      minimum="1000"
      points={[
        {
          month: "2026-01",
          balance: "1000",
          status: "ACTUAL",
          shortage: false,
        },
        { month: "2026-02", balance: "0", status: "PLANNED", shortage: true },
        { month: "2026-03", balance: null, status: "PLANNED", shortage: false },
        { month: "2026-04", balance: "-1", status: "PLANNED", shortage: true },
      ]}
    />,
  );
  expect(screen.getByRole("img")).toBeInTheDocument();
  expect(container.querySelectorAll(".cash-line")).toHaveLength(1);
  expect(container.querySelector(".cash-line")).toHaveClass("planned");
  expect(container.querySelectorAll("circle")).toHaveLength(3);
});
