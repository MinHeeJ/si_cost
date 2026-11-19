import { formatAmount, type AmountUnit } from "../../shared/format";
import type { PlanMonth } from "./businessPlanPerformanceApi";
import { ChartHost } from "../../shared/charts/ChartHost";
import { seriesChartOptions } from "../../shared/charts/chartAdapters";

export function BusinessPlanRevenueChart({
  months,
  unit,
}: {
  months: PlanMonth[];
  unit: AmountUnit;
}) {
  // Floating point is confined to SVG coordinates. Labels use exact server amount strings.
  const point = (amount: string | null) =>
    amount == null ? null : Number(amount) / 1_000_000;
  const amounts = months
    .flatMap((month) => [
      point(month.salesActualAmt),
      point(month.salesTargetAmt),
    ])
    .filter((n): n is number => n != null);
  const minimum = Math.min(0, ...amounts),
    maximum = Math.max(1, ...amounts),
    span = maximum - minimum;
  const y = (value: number) => 310 - ((value - minimum) / span) * 260;
  const baseline = y(0),
    x = (index: number) => 80 + index * 70;
  const segments: string[] = [];
  let segment = "";
  months.forEach((month, index) => {
    const target = point(month.salesTargetAmt);
    if (target == null) {
      if (segment) segments.push(segment);
      segment = "";
    } else segment += `${segment ? " L" : "M"}${x(index)} ${y(target)}`;
  });
  if (segment) segments.push(segment);
  return (
    <section className="business-plan-chart" data-testid="business-plan-chart">
      <h2>월별 매출 실적과 목표</h2>
      <small className="sicost-chart-unit">축: 백만원 · 상세: {unit}</small>
      {amounts.length === 0 ? (
        <div className="business-plan-empty">
          표시할 매출 목표·실적 자료가 없습니다.
        </div>
      ) : (
        <div className="business-plan-scroll">
          <ChartHost
            option={seriesChartOptions({
              categories: months.map((month) => month.month),
              series: [
                {
                  name: "실적",
                  values: months.map((month) => month.salesActualAmt),
                  type: "bar",
                },
                {
                  name: "목표",
                  values: months.map((month) => month.salesTargetAmt),
                  dashed: true,
                  dashPattern: [5, 4],
                },
              ],
              formatValue: (value) => `${formatAmount(value, unit)} ${unit}`,
              axisUnit: "백만원",
            })}
          >
            <svg
              viewBox="0 0 920 360"
              role="img"
              aria-label="1~12월 매출 실적 막대와 목표 기준선, 백만원"
            >
              {[0, 1, 2, 3, 4].map((tick) => {
                const value = minimum + (span * tick) / 4;
                return (
                  <g key={tick} data-testid={`business-plan-chart-tick-${tick}`}>
                    <line
                      x1="60"
                      x2="890"
                      y1={y(value)}
                      y2={y(value)}
                      className="business-plan-grid"
                    />
                    <text x="52" y={y(value) + 4} textAnchor="end">
                      {value.toFixed(1)}
                    </text>
                  </g>
                );
              })}
              <line
                x1="60"
                x2="890"
                y1={baseline}
                y2={baseline}
                className="business-plan-zero"
              />
              {months.map((month, index) => {
                const value = point(month.salesActualAmt);
                return (
                  <g
                    key={month.month}
                    data-testid={`business-plan-chart-month-${month.month}`}
                  >
                    {value != null && (
                      <rect
                        data-testid={`business-plan-actual-bar-${month.month}`}
                        x={x(index) - 16}
                        y={Math.min(y(value), baseline)}
                        width="32"
                        height={Math.max(1, Math.abs(y(value) - baseline))}
                        className="business-plan-actual-bar"
                      >
                        <title>
                          {month.month} 실적{" "}
                          {formatAmount(month.salesActualAmt, unit)} {unit}
                        </title>
                      </rect>
                    )}
                    <text x={x(index)} y="344" textAnchor="middle">
                      {index + 1}월
                    </text>
                  </g>
                );
              })}
              {segments.map((path, index) => (
                <path
                  key={index}
                  data-testid={`business-plan-target-segment-${index}`}
                  d={path}
                  className="business-plan-target-line"
                />
              ))}
              {months.map(
                (month, index) =>
                  month.salesTargetAmt != null && (
                    <circle
                      key={month.month}
                      data-testid={`business-plan-target-point-${month.month}`}
                      cx={x(index)}
                      cy={y(point(month.salesTargetAmt)!)}
                      r="4"
                      className="business-plan-target-point"
                    >
                      <title>
                        {month.month} 목표{" "}
                        {formatAmount(month.salesTargetAmt, unit)} {unit}
                      </title>
                    </circle>
                  ),
              )}
            </svg>
          </ChartHost>
        </div>
      )}
    </section>
  );
}
