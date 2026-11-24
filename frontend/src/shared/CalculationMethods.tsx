import type { ReactNode } from "react";
import "./calculationMethods.css";

export function CalculationMethods({
  rows,
  testId,
  rowTestIdPrefix,
  children,
}: {
  rows: readonly { metric: string; formula: string; reference: ReactNode }[];
  testId?: string;
  rowTestIdPrefix?: string;
  children?: ReactNode;
}) {
  return (
    <details className="sicost-calculation-methods" data-testid={testId}>
      <summary>계산 방법</summary>
      <div className="sicost-calculation-table-scroll">
        <table aria-label="계산 방법" className="sicost-calculation-table">
          <thead>
            <tr>
              <th scope="col">항목</th>
              <th scope="col">계산</th>
              <th scope="col">참고</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(({ metric, formula, reference }, index) => (
              <tr
                key={metric}
                data-testid={
                  rowTestIdPrefix ? `${rowTestIdPrefix}-${index}` : undefined
                }
              >
                <th scope="row">{metric}</th>
                <td>{formula}</td>
                <td>{reference}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {children}
    </details>
  );
}
