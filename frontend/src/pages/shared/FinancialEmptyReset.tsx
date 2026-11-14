export function FinancialEmptyReset({ screenId }: { screenId: string }) {
  return <button type="button" onClick={() => window.dispatchEvent(new CustomEvent("sicost:company-profit-reset-period", { detail: { screenId } }))}>조건 초기화</button>;
}
