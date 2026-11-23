import { useId, useState } from "react";
import { formatAmount, type AmountUnit } from "../../shared/format";
import type { ProfitabilityContribution } from "./individualProfitabilityApi";

export function ProfitabilityContributionTable({
  cells,
  unit,
}: {
  cells: ProfitabilityContribution[];
  unit: AmountUnit;
}) {
  const [open, setOpen] = useState(false);
  const labelId = useId();
  const jobs = [...new Map(cells.map((c) => [c.jobCode, c.jobName])).entries()];
  const grades = [
    ...new Map(cells.map((c) => [c.gradeCode, c.gradeName])).entries(),
  ];
  return (
    <section
      className="profitability-contribution"
      data-testid="profitability-contribution"
    >
      <div className="profitability-contribution-heading">
        <button
          type="button"
          data-testid="profitability-contribution-toggle"
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
        >
          {open ? "▾" : "▸"} 직무·등급별 인당 공헌이익
        </button>
        {open && <p id={labelId}>인당 평균 영업이익 / 인원</p>}
      </div>
      {open && (
        <div
          className="sicost-table-viewport"
          data-testid="profitability-contribution-panel"
        >
          <table className="sicost-table" aria-labelledby={labelId}>
            <thead>
              <tr>
                <th>주직무</th>
                {grades.map(([code, name]) => (
                  <th key={JSON.stringify(code)}>{name}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {jobs.map(([job, name], index) => (
                <tr
                  key={JSON.stringify(job)}
                  data-testid={`profitability-contribution-row-${index}`}
                >
                  <th>{name}</th>
                  {grades.map(([grade]) => {
                    const cell = cells.find(
                      (c) => c.jobCode === job && c.gradeCode === grade,
                    );
                    return (
                      <td
                        key={JSON.stringify(grade)}
                        className={
                          cell?.averageProfit?.startsWith("-")
                            ? "profitability-negative"
                            : undefined
                        }
                      >
                        {formatAmount(cell?.averageProfit, unit)} (
                        {cell?.personCount ?? 0}명)
                        {cell && cell.unknownCount > 0
                          ? ` (미확인 ${cell.unknownCount}명)`
                          : ""}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
