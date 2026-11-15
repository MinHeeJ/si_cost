import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { BalanceTrendChart } from "./BalanceTrendChart";
it("keeps complete large negative axis labels inside the chart", () => {
  const { container } = render(<BalanceTrendChart minimum="1000000000" points={[{ month: "2026-01", balance: "-999999999999999", status: "PLANNED", shortage: true }]} />);
  for (const text of container.querySelectorAll('text[text-anchor="end"]')) {
    if (text.textContent === "최소 운영자금") continue;
    expect(Number(text.getAttribute("x"))).toBeGreaterThan((text.textContent?.length ?? 0) * 7);
  }
});
it("breaks lines at unknown balances and connects last actual to planned with a dashed line", () => {
  const { container } = render(<BalanceTrendChart minimum="1000" points={[
    {month:"2026-01",balance:"1000",status:"ACTUAL",shortage:false},
    {month:"2026-02",balance:"0",status:"PLANNED",shortage:true},
    {month:"2026-03",balance:null,status:"PLANNED",shortage:false},
    {month:"2026-04",balance:"-1",status:"PLANNED",shortage:true},
  ]} />);
  expect(screen.getByRole("img")).toBeInTheDocument();
  expect(container.querySelectorAll('.cash-line')).toHaveLength(1);
  expect(container.querySelector('.cash-line')).toHaveClass('planned');
  expect(container.querySelectorAll('circle')).toHaveLength(3);
});
