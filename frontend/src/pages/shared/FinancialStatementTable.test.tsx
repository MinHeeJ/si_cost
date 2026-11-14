import { render, screen, within } from "@testing-library/react";
import { expect, it } from "vitest";
import { FinancialStatementTable } from "./FinancialStatementTable";
it("preserves exact negative, zero and missing statement values", () => {
  render(<FinancialStatementTable months={["01", "02", "03"].map(month => ({ month: `2026-${month}`, sourceKind: "CALCULATED", completeness: { issues: [] } }))} rows={[{ metric: "oprtPrftAmt", label: "영업이익", depth: 0, isRate: false, monthlyValues: ["-1001", "0", null], cumulativeValue: "-1001", previousYearCumulativeValue: "0", comparison: null }]} month="2026-03" unit="천원" showPreviousYear provisional name="손익표" testPrefix="test" />);
  const cells = within(screen.getByTestId("test-row-oprt-prft-amt")).getAllByRole("cell");
  expect(cells[0]).toHaveTextContent("−1.001");
  expect(cells[0]).toHaveAttribute("data-financial-negative", "true");
  expect(cells[1]).toHaveTextContent("0");
  expect(cells[1]).toHaveAttribute("data-financial-negative", "false");
  expect(cells[2]).toHaveTextContent("–");
  expect(cells[2]).not.toHaveAttribute("data-financial-negative");
  expect(cells[3]).toHaveAttribute("data-financial-negative", "true");
});
