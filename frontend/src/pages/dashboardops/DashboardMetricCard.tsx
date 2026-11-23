import {
  formatAmount,
  formatFixedDecimal,
  formatRate,
  type AmountUnit,
} from "../../shared/format";
import type { DashboardMetric } from "./dashboardApi";
import type { ReactNode } from "react";

export function DashboardMetricCard({
  card,
  month,
  amountUnit,
  onOpen,
  children,
}: {
  card: DashboardMetric;
  month: string;
  amountUnit: AmountUnit;
  onOpen(): void;
  children: ReactNode;
}) {
  const money = (value: string | null) => formatAmount(value, amountUnit);
  const label =
    card.metricCode === "SALES"
      ? `${month.slice(5)}월 매출`
      : card.metricCode === "OPERATING_PROFIT"
        ? `${month.slice(5)}월 영업이익`
        : card.metricCode === "YTD_OPERATING_PROFIT"
          ? "누계 영업이익"
          : card.metricCode === "UTILIZATION"
            ? "가동률"
            : "미수금";
  const value = card.unit === "PERCENT" ? formatRate(card.value) : money(card.value);
  const isLoss =
    (card.metricCode === "OPERATING_PROFIT" || card.metricCode === "YTD_OPERATING_PROFIT") &&
    value.startsWith("−");

  return (
    <button
      className={`dashboard-metric dashboard-metric-${card.metricCode.toLowerCase().replaceAll("_", "-")}`}
      data-testid={`dashboard-metric-${card.metricCode.toLowerCase().replaceAll("_", "-")}`}
      onClick={onOpen}
    >
      <span>{label}</span>
      <strong className={isLoss ? "dashboard-danger" : undefined}>
        <span>{value}</span>
        {card.unit !== "PERCENT" && (
          <>{" "}<span className="dashboard-unit">{amountUnit}</span></>
        )}
      </strong>
      <div className="dashboard-metric-details">
        {(card.metricCode === "SALES" ||
          card.metricCode === "OPERATING_PROFIT") && (
          <small>
            전월 대비 {formatRate(card.changeRate)} · 차액{" "}
            <span className="dashboard-metric-amount">
              {money(card.difference)} {amountUnit}
            </span>
          </small>
        )}
        {card.metricCode === "OPERATING_PROFIT" && (
          <small>이익률 {formatRate(card.profitRate)}</small>
        )}
        {(card.metricCode === "SALES" ||
          card.metricCode === "YTD_OPERATING_PROFIT") && (
          <small>
            {card.metricCode === "YTD_OPERATING_PROFIT"
              ? `1~${month.slice(5)}월 · `
              : ""}
            목표 달성 {formatRate(card.achievementRate)}
          </small>
        )}
        {card.metricCode === "UTILIZATION" && (
          <small>
            목표 {formatRate(card.targetValue)} 대비{" "}
            {formatFixedDecimal(card.targetGapPercentagePoints, 1)}%p · 대기{" "}
            {card.idlePersonCount ?? "–"}명
          </small>
        )}
        {card.metricCode === "RECEIVABLE" && (
          <small className="dashboard-danger">
            연체 {card.overdueCount ?? "–"}건{" "}
            <span className="dashboard-metric-amount">
              {money(card.overdueAmount)} {amountUnit}
            </span>
          </small>
        )}
      </div>
      {children}
    </button>
  );
}
