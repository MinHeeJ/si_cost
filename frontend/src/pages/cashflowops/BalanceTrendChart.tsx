import { formatAmount } from "../../shared/format";
import { useMemo } from "react";
import { ChartHost } from "../../shared/charts/ChartHost";
import { seriesOption } from "../../shared/charts/chartAdapters";
import type { CashPoint } from "./cashFlowApi";
export function BalanceTrendChart({
  points,
  minimum,
}: {
  points: CashPoint[];
  minimum: string;
}) {
  const option = useMemo(
    () =>
      seriesOption(
        points.map((point) => point.month),
        [
          {
            name: "월말 잔액(실적)",
            values: points.map((point, index) =>
              point.status !== "PLANNED" ||
              points[index + 1]?.status === "ACTUAL"
                ? point.balance
                : null,
            ),
          },
          {
            name: "월말 잔액(예정)",
            dashed: true,
            values: points.map((point, index) =>
              point.status === "PLANNED" ||
              points[index + 1]?.status === "PLANNED"
                ? point.balance
                : null,
            ),
          },
        ],
        "백만원",
        minimum,
      ),
    [points, minimum],
  );
  const values = points.flatMap((p) =>
      p.balance === null ? [] : [BigInt(p.balance)],
    ),
    floor = BigInt(minimum);
  const low = [0n, floor, ...values].reduce((a, b) => (a < b ? a : b)),
    high = [floor, ...values].reduce((a, b) => (a > b ? a : b), 0n);
  const span = high - low || 1n;
  const y = (value: string) =>
    250 - Number(((BigInt(value) - low) * 22000n) / span) / 100;
  const ticks = [0, 1, 2, 3, 4].map((i) =>
    String(low + (span * BigInt(i)) / 4n),
  );
  // Reserve room for the full decimal labels, including negative balances.
  const left = Math.max(
    80,
    ...ticks.map((value) => formatAmount(value, "백만원").length * 9 + 20),
  );
  const x = (i: number) => left + (i * (900 - left)) / 11;
  return (
    <section className="cash-chart" aria-label="월말 잔액 흐름">
      <div className="operating-toolbar">
        <h2>월말 잔액 흐름</h2>
        <small>단위: 백만 원 · 부가세 포함</small>
      </div>
      <ChartHost
        testId="cash-balance-chart"
        label="실적 실선, 예정 점선, 미확인 잔액은 선을 연결하지 않습니다."
        option={option}
      >
        <svg
          viewBox="0 0 940 310"
          role="img"
          aria-label="실적 실선, 예정 점선, 미확인 잔액은 선을 연결하지 않습니다."
        >
          {ticks.map((value, i) => {
            return (
              <g key={i}>
                <line
                  x1={left}
                  x2="900"
                  y1={y(String(value))}
                  y2={y(String(value))}
                  className="cash-grid"
                />
                <text x={left - 12} y={y(String(value)) + 5} textAnchor="end">
                  {formatAmount(String(value), "백만원")}
                </text>
              </g>
            );
          })}
          <line
            x1={left}
            x2="900"
            y1={y(minimum)}
            y2={y(minimum)}
            className="cash-minimum"
          />
          <text x="900" y={y(minimum) - 8} textAnchor="end">
            최소 운영자금
          </text>
          {points.map((p, i) => (
            <g key={p.month}>
              {i > 0 &&
                p.balance !== null &&
                points[i - 1].balance !== null && (
                  <line
                    x1={x(i - 1)}
                    x2={x(i)}
                    y1={y(points[i - 1].balance!)}
                    y2={y(p.balance)}
                    className={`cash-line ${p.status === "PLANNED" ? "planned" : "actual"}`}
                  />
                )}
              {p.balance !== null && (
                <circle
                  cx={x(i)}
                  cy={y(p.balance)}
                  r="4"
                  className={p.shortage ? "cash-shortage-point" : "cash-point"}
                >
                  <title>
                    {p.month} {formatAmount(p.balance, "백만원")}
                  </title>
                </circle>
              )}
              <text x={x(i)} y="278" textAnchor="middle">
                {p.month.slice(5)}월
              </text>
            </g>
          ))}
        </svg>
        <p className="cash-chart-legend">
          <span>━ 월말 잔액(실적)</span>
          <span>┄ 월말 잔액(예정)</span>
        </p>
      </ChartHost>
    </section>
  );
}
