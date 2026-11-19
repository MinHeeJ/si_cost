import { formatFixedDecimal } from "../../shared/format";
import { ChartHost } from "../../shared/charts/ChartHost";
import { seriesOptions } from "../../shared/charts/chartAdapters";
import type { DashboardSupply } from "./dashboardApi";

export function DashboardSupplyChart({ rows }: { rows: DashboardSupply[] }) {
  const option = seriesOptions(
    rows.map((row) => row.month),
    [
      {
        name: "가용",
        kind: "bar",
        values: rows.map((row) => row.availablePersons),
      },
      {
        name: "필요",
        kind: "bar",
        values: rows.map((row) => row.requiredPersons),
        color: "#f59e0b",
      },
    ],
    "원",
    1,
  );
  option.yAxis = { name: "명" };
  // Counts and differences are server results, not sums reconstructed from assignments.
  option.tooltip = {
    formatter: (input: unknown) => {
      const entry = (Array.isArray(input) ? input[0] : input) as
        | { dataIndex?: number }
        | undefined;
      const row =
        entry?.dataIndex === undefined ? undefined : rows[entry.dataIndex];
      return row
        ? `${row.month}\n가용 ${formatFixedDecimal(row.availablePersons, 1)}명\n필요 ${formatFixedDecimal(row.requiredPersons, 1)}명\n차이 ${formatFixedDecimal(row.differencePersons, 1)}명`
        : "";
    },
  };
  return (
    <ChartHost
      testId="dashboard-supply-chart"
      label="앞으로 6개월 가용·필요 인력, 단위 명"
      option={option}
      fallback={
        <p role="note">인력 수급은 아래 숫자표에서 확인할 수 있습니다.</p>
      }
    />
  );
}
