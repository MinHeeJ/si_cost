import { formatAmount, type AmountUnit } from "../../shared/format";
import type { StructureBucket, WaterfallStep } from "./profitStructureApi";
const colors = ["var(--primary)", "var(--warn)", "var(--ok)", "var(--info)"];
export function bucketLabel(bucket: StructureBucket, quarterly: boolean) {
  return quarterly
    ? `${bucket.key} · ${bucket.throughMonth.slice(5)}월까지/${bucket.monthCount}개월`
    : bucket.key;
}
// Number is limited to SVG coordinates. Every tooltip uses the original decimal string.
export function StructureTrend({
  title,
  buckets,
  metrics,
  labels,
  quarterly,
  stacked = false,
  unit,
}: {
  title: string;
  buckets: StructureBucket[];
  metrics: string[];
  labels: string[];
  quarterly: boolean;
  stacked?: boolean;
  unit: AmountUnit;
}) {
  const raw = (b: StructureBucket, metric: string) =>
    metric === "referenceRevenue"
      ? b.referenceRevenue.visible
        ? b.referenceRevenue.amount
        : null
      : b.amounts[metric];
  const values = buckets
    .flatMap((b) => metrics.map((m) => raw(b, m)))
    .filter((v): v is string => v != null)
    .map(Number);
  const maximum =
      Math.max(1, ...values.map((v) => Math.abs(v))) *
      (stacked ? metrics.length : 1),
    y = (value: number) => 140 - (value / maximum) * 100;
  const x = (index: number) => 70 + index * (650 / Math.max(1, buckets.length));
  return (
    <section className="profit-structure-card">
      <h2>{title}</h2>
      <p className="profit-structure-muted">축: 백만원 · 툴팁: {unit}</p>
      <div className="profit-structure-chart-scroll">
        <svg viewBox="0 0 800 285" role="img" aria-label={title}>
          <line
            x1="55"
            x2="785"
            y1="140"
            y2="140"
            className="profit-structure-axis"
          />
          {[maximum, 0, -maximum].map((value) => (
            <text key={value} x="5" y={y(value)}>
              {(value / 1000000).toLocaleString("ko-KR", {
                maximumSignificantDigits: 4,
              })}
            </text>
          ))}
          {metrics.map((metric, series) => {
            let segment = "";
            const segments: string[] = [];
            buckets.forEach((bucket, index) => {
              const v = raw(bucket, metric);
              if (v == null) {
                if (segment) segments.push(segment);
                segment = "";
              } else
                segment += `${segment ? " L" : "M"}${x(index)},${y(Number(v))}`;
            });
            if (segment) segments.push(segment);
            return (
              <g key={metric} style={{ color: colors[series % colors.length] }}>
                {!quarterly &&
                  !stacked &&
                  segments.map((path, index) => (
                    <path
                      key={index}
                      d={path}
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeDasharray={
                        metric === "referenceRevenue" ? "6 5" : undefined
                      }
                    />
                  ))}
                {buckets.map((bucket, index) => {
                  const v = raw(bucket, metric);
                  if (v == null) return null;
                  const value = Number(v);
                  let base = 0;
                  if (stacked)
                    for (let k = 0; k < series; k++) {
                      const previous = raw(bucket, metrics[k]);
                      if (previous == null) return null;
                      const n = Number(previous);
                      if (n < 0 === value < 0) base += n;
                    }
                  const bar = quarterly || stacked;
                  return (
                    <g
                      key={bucket.key}
                      data-testid={`structure-chart-${title.includes("하위") ? "costs" : title.includes("총이익") ? "profits" : "revenue"}-${metric.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}-${index}`}
                    >
                      <title>
                        {bucketLabel(bucket, quarterly)} · {labels[series]}:{" "}
                        {formatAmount(v, unit)} {unit}
                      </title>
                      {bar ? (
                        <rect
                          x={x(index) + (stacked ? 0 : series * 9)}
                          y={Math.min(y(base), y(base + value))}
                          width={stacked ? 22 : 8}
                          height={Math.abs(y(base + value) - y(base))}
                          fill="currentColor"
                        />
                      ) : (
                        <circle
                          cx={x(index)}
                          cy={y(value)}
                          r="3"
                          fill="currentColor"
                        />
                      )}
                    </g>
                  );
                })}
              </g>
            );
          })}
          {buckets.map((bucket, index) => (
            <text
              key={bucket.key}
              x={x(index)}
              y="265"
              fontSize="10"
              textAnchor="middle"
            >
              {bucket.key}
            </text>
          ))}
        </svg>
      </div>
      <div className="profit-structure-legend">
        {labels.map((label, i) => (
          <span key={label} style={{ color: colors[i] }}>
            {metrics[i] === "referenceRevenue" ? "┄" : "●"} {label}
          </span>
        ))}
      </div>
    </section>
  );
}
export function StructureWaterfall({
  steps,
  unit,
}: {
  steps: WaterfallStep[];
  unit: AmountUnit;
}) {
  let total: bigint | null = 0n;
  const positions = steps.map((step) => {
    const amount = step.amount == null ? null : BigInt(step.amount);
    const from = step.kind === "SUBTOTAL" ? 0n : step.connected ? total : null;
    const to =
      step.kind === "SUBTOTAL"
        ? amount
        : from == null || amount == null
          ? null
          : from + amount;
    total = to;
    return { step, from, to };
  });
  const maximum = Math.max(
    1,
    ...positions
      .flatMap((p) => [p.from, p.to])
      .filter((v): v is bigint => v != null)
      .map((v) => Math.abs(Number(v))),
  );
  const y = (n: bigint) => 145 - (Number(n) / maximum) * 105;
  return (
    <section className="profit-structure-card profit-structure-wide">
      <h2>수익 구조 폭포</h2>
      <p className="profit-structure-muted">
        백만원 · 소계는 재가산하지 않습니다
      </p>
      <div className="profit-structure-chart-scroll">
        <svg viewBox="0 0 900 290" role="img" aria-label="수익 구조 폭포">
          <line
            x1="10"
            x2="885"
            y1="145"
            y2="145"
            className="profit-structure-axis"
          />
          {positions.map(({ step, from, to }, index) => (
            <g
              key={step.metric}
              data-testid={`structure-waterfall-${step.metric.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
            >
              <title>
                {step.label}: {formatAmount(step.amount, unit)} {unit}
              </title>
              {from != null && to != null ? (
                <>
                  <rect
                    x={index * 97 + 15}
                    y={Math.min(y(from), y(to))}
                    width="63"
                    height={Math.max(1, Math.abs(y(to) - y(from)))}
                    fill={
                      step.kind === "SUBTOTAL"
                        ? "var(--primary)"
                        : BigInt(step.amount!) < 0n
                          ? "var(--bad)"
                          : "var(--ok)"
                    }
                  />
                  <text
                    x={index * 97 + 46}
                    y={Math.min(y(from), y(to)) - 6}
                    textAnchor="middle"
                  >
                    {formatAmount(step.amount, "백만원")}
                  </text>
                </>
              ) : (
                <text x={index * 97 + 46} y="120" textAnchor="middle">
                  미확인
                </text>
              )}
              <text
                x={index * 97 + 46}
                y="275"
                textAnchor="middle"
                fontSize="11"
              >
                {step.label}
              </text>
            </g>
          ))}
        </svg>
      </div>
    </section>
  );
}
