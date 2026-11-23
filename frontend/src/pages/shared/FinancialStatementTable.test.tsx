import { render, screen, within } from "@testing-library/react";
import { expect, it } from "vitest";
import { FinancialStatementTable } from "./FinancialStatementTable";
it("preserves exact negative, zero and missing statement values", () => {
  render(
    <FinancialStatementTable
      months={["01", "02", "03"].map((month) => ({
        month: `2026-${month}`,
        sourceKind: "CALCULATED",
        completeness: { issues: [] },
      }))}
      rows={[
        {
          metric: "oprtPrftAmt",
          label: "영업이익",
          depth: 0,
          isRate: false,
          monthlyValues: ["-1001", "0", null],
          cumulativeValue: "-1001",
          previousYearCumulativeValue: "0",
          comparison: null,
        },
      ]}
      month="2026-03"
      unit="천원"
      showPreviousYear
      provisional
      name="손익표"
      testPrefix="test"
    />,
  );
  const cells = within(
    screen.getByTestId("test-row-oprt-prft-amt"),
  ).getAllByRole("cell");
  expect(cells[0]).toHaveTextContent("−1.001");
  expect(cells[0]).toHaveAttribute("data-financial-negative", "true");
  expect(cells[1]).toHaveTextContent("0");
  expect(cells[1]).toHaveAttribute("data-financial-negative", "false");
  expect(cells[2]).toHaveTextContent("–");
  expect(cells[2]).not.toHaveAttribute("data-financial-negative");
  expect(cells[3]).toHaveAttribute("data-financial-negative", "true");
});

it.each(["operating", "net"])(
  "emphasizes only named profit totals in %s, not every unindented row",
  (prefix) => {
    const metrics = [
      "salesAmt",
      "salesCostAmt",
      "allocCostAmt",
      "idleCostAmt",
      "drctExpsAmt",
      "grossPrftAmt",
      "sgaTotAmt",
      "sgaInputAmt",
      "indrctLaborAmt",
      "rndAmt",
      "prevAdjAmt",
      "oprtPrftAmt",
      "operatingProfitRate",
    ];
    if (prefix === "net")
      metrics.push(
        "noprIncmAmt",
        "noprExpsAmt",
        "corpTaxAmt",
        "netPrftAmt",
        "netProfitRate",
      );
    const { container } = render(
      <FinancialStatementTable
        months={[
          {
            month: "2026-01",
            sourceKind: "MIGRATED",
            completeness: { issues: [] },
          },
        ]}
        rows={metrics.map((metric, index) => ({
          metric,
          label: metric,
          depth: [2, 3, 4, 7, 8, 9].includes(index) ? 1 : 0,
          isRate: metric.endsWith("Rate"),
          monthlyValues: ["1"],
          cumulativeValue: "1",
          previousYearCumulativeValue: null,
          comparison: null,
        }))}
        month="2026-01"
        unit="원"
        showPreviousYear={false}
        provisional={false}
        name="손익표"
        testPrefix={prefix}
      />,
    );
    const totals = [...container.querySelectorAll("tbody .operating-total")];
    expect(totals.map((row) => row.getAttribute("data-testid"))).toEqual([
      `${prefix}-row-sales-amt`,
      `${prefix}-row-gross-prft-amt`,
      `${prefix}-row-oprt-prft-amt`,
      ...(prefix === "net" ? ["net-row-net-prft-amt"] : []),
    ]);
    expect(container.querySelectorAll(".operating-detail")).toHaveLength(6);
    for (const metric of [
      "sales-cost-amt",
      "sga-tot-amt",
      "prev-adj-amt",
      "operating-profit-rate",
    ])
      expect(screen.getByTestId(`${prefix}-row-${metric}`)).toHaveClass(
        "operating-standard",
      );
  },
);
