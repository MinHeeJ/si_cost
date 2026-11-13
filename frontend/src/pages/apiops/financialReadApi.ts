import { request } from "../../shared/api";

export type FinancialHistoryScope = "MONTH" | "TAX" | "CARDS";
export type FinancialHistoryEntry = {
  historyId: string;
  targetTable: string;
  targetKey: string;
  changeType: "NEW" | "UPD" | "DEL" | "STOP" | "RESUME";
  field: string;
  beforeValue: string | null;
  afterValue: string | null;
  reason: string | null;
  userId: string;
  occurredAt: string;
};
export type FinancialHistoryPage = {
  items: FinancialHistoryEntry[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
};
export type FinancialContributions = {
  month: string;
  sgaAmount: string;
  sgaVatAmount: string;
  nonOperatingIncome: string;
  nonOperatingIncomeVat: string;
  nonOperatingExpense: string;
  nonOperatingExpenseVat: string;
  adjustmentProfit: string;
  cashFinancialExpense: string;
  cashMiscIncome: string;
  cashCardExpense: string;
  cashTransferExpense: string;
  cashUnpaidLabor: string;
  transferTotal: string;
  cashLoanIn: string;
  cashLoanOut: string;
  cashLoanNet: string;
  corporateTaxAllocation: string | null;
  cashTaxPayment: string | null;
  verified: boolean;
  warnings: string[];
};
export function getFinancialContributions(month: string, signal?: AbortSignal) {
  return request<FinancialContributions>(
    `/api/financial-contributions?${new URLSearchParams({ month })}`,
    { signal },
  );
}
export function getFinancialHistory(
  month: string,
  scope: FinancialHistoryScope,
  page = 1,
  size: 20 | 50 | 100 = 50,
  signal?: AbortSignal,
) {
  return request<FinancialHistoryPage>(
    `/api/financial-history?${new URLSearchParams({ month, scope, page: String(page), size: String(size) })}`,
    { signal },
  );
}
