import { formatAmount } from "../../shared/format";
import type { DashboardTrend } from "./dashboardApi";
export function DashboardTrendChart({ rows }: { rows: DashboardTrend[] }) {
  const series = [
    { key: "salesAmount", label: "매출", style: "sales" },
    { key: "salesCostAmount", label: "매출원가", style: "cost" },
    { key: "salesTargetAmount", label: "ALL 매출목표", style: "target" },
  ] as const;
  const values = rows.flatMap((row) =>
    series.flatMap((s) => (row[s.key] == null ? [] : [BigInt(row[s.key]!)])),
  );
  const low = values.reduce((a, b) => (a < b ? a : b), 0n),
    high = values.reduce((a, b) => (a > b ? a : b), 0n),
    span = high - low || 1n;
  // Number conversion is limited to bounded SVG pixel coordinates, never business amounts.
  const y = (value: string) =>
    170 - Number(((BigInt(value) - low) * 14000n) / span) / 100;
  const x = (index: number) => 80 + index * 39;
  return (
    <div className="dashboard-chart">
      <small>
        단위: 백만원 · 실적 실선 / ALL 매출목표 점선 · 미확인은 연결하지 않음
      </small>
      <svg
        viewBox="0 0 560 215"
        role="img"
        aria-label="매출·매출원가 실선과 ALL 매출목표 점선"
      >
        {[low, high].map((value, index) => (
          <g key={index}>
            <text x="72" y={y(String(value))} textAnchor="end">
              {formatAmount(String(value), "백만원")}
            </text>
            <line
              x1="80"
              x2="530"
              y1={y(String(value))}
              y2={y(String(value))}
              className="dashboard-chart-grid"
            />
          </g>
        ))}
        {series.map((s) => (
          <g key={s.key} className={`dashboard-chart-${s.style}`}>
            {rows.map((row, index) => (
              <g key={row.month}>
                {index > 0 &&
                  row[s.key] !== null &&
                  rows[index - 1][s.key] !== null && (
                    <line
                      x1={x(index - 1)}
                      x2={x(index)}
                      y1={y(rows[index - 1][s.key]!)}
                      y2={y(row[s.key]!)}
                    />
                  )}
                {row[s.key] !== null && (
                  <circle cx={x(index)} cy={y(row[s.key]!)} r="3">
                    <title>
                      {row.month} {s.label} {formatAmount(row[s.key], "백만원")}{" "}
                      백만원
                    </title>
                  </circle>
                )}
              </g>
            ))}
          </g>
        ))}
        {rows.map((row, index) => (
          <text key={row.month} x={x(index)} y="199" textAnchor="middle">
            {row.month.slice(5)}월
          </text>
        ))}
      </svg>
      <div className="dashboard-chart-legend">
        {series.map((s) => (
          <span key={s.key} className={`dashboard-chart-${s.style}`}>
            {s.style === "target" ? "┄" : "━"} {s.label}
          </span>
        ))}
      </div>
    </div>
  );
}
