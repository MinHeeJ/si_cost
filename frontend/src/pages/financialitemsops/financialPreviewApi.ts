import { request } from "../../shared/api";

export type FinancialMonthStatus = {
  month: string;
  closeStatus: "WORK" | "CLOSE";
  itemCount: number | null;
  selectable: boolean;
  editable: boolean;
};
export type FinancialYearStatus = {
  year: string;
  today: string;
  cashBaseMonth: string;
  months: FinancialMonthStatus[];
};
export type FinancialItemAddition = {
  finClass: "SGA" | "NOPR_IN" | "NOPR_OUT";
  sgaItemCode?: string | null;
  itemName: string;
  amount: string;
  vatAmount?: string;
  ioDate?: string | null;
  repeat?: "Y" | "N";
  remark?: string | null;
  copySource?: { month: string; itemSn: string; mode: "REPEAT" | "PREVIOUS" };
};
export type FinancialChanges<T, K extends string> = {
  add?: T[];
  update?: (T & Record<K, string>)[];
  deleteIds?: string[];
};
export type FinancialAdjustmentInput = {
  targetMonth: string;
  itemCode: "SALES" | "COST" | "SGA" | "ETC";
  amount: string;
  reason: string;
};
export type FinancialTransferInput = {
  date: string;
  classCode: "EXP" | "SPCL";
  itemName: string;
  receiver?: string | null;
  amount: string;
  remark?: string | null;
};
export type FinancialLoanInput = {
  date: string;
  direction: "IN" | "OUT";
  amount: string;
  counterparty?: string | null;
  remark?: string | null;
};
export type FinancialDraftChanges = {
  items?: FinancialChanges<FinancialItemAddition, "itemSn">;
  adjustments?: FinancialChanges<FinancialAdjustmentInput, "adjustmentId">;
  transfers?: FinancialChanges<FinancialTransferInput, "transferId">;
  loans?: FinancialChanges<FinancialLoanInput, "loanId">;
  cards?: {
    add?: {
      clientKey: string;
      cardName: string;
      last4: string;
      amount?: string;
    }[];
    states?: { cardId: string; useYn: "Y" | "N" }[];
    payments?: { cardId: string; amount: string }[];
    cashOverride?: { mode: "AUTO" } | { mode: "MANUAL"; amount: string };
  };
  estimatedTax?: string;
  taxSchedules?: {
    year: string;
    payType: "MID" | "CNFM";
    dueDate: string;
    payAmount: string | null;
  }[];
};
export type FinancialAllocation = {
  year: string;
  estimatedTax: string | null;
  closedTotal: string;
  remainingAmount: string | null;
  openMonthCount: number;
  months: {
    month: string;
    closeStatus: "WORK" | "CLOSE";
    amount: string | null;
    verified: boolean;
    fixed: string | null;
    migrated: string | null;
  }[];
  verified: boolean;
  warnings: string[];
};
export type FinancialContributions = Record<
  | "sgaAmount"
  | "sgaVatAmount"
  | "nonOperatingIncome"
  | "nonOperatingIncomeVat"
  | "nonOperatingExpense"
  | "nonOperatingExpenseVat"
  | "adjustmentProfit"
  | "cashFinancialExpense"
  | "cashMiscIncome"
  | "cashCardExpense"
  | "cashTransferExpense"
  | "cashUnpaidLabor"
  | "transferTotal"
  | "cashLoanIn"
  | "cashLoanOut"
  | "cashLoanNet",
  string
> & {
  month: string;
  corporateTaxAllocation: string | null;
  cashTaxPayment: string | null;
  verified: boolean;
  warnings: string[];
};
export type FinancialDraftItem = Required<
  Omit<FinancialItemAddition, "copySource">
> & {
  sourceMonth: string;
  sourceItemSn: string;
  autoFill: "Y" | "N";
  errors: { field: string; message: string }[];
};
export type FinancialPreviewRequest = FinancialDraftChanges & {
  month: string;
  expectedRevision: string;
  action: "CALCULATE" | "LOAD_PREVIOUS";
};
export type FinancialPreviewResult = {
  month: string;
  revision: string;
  addedDrafts: FinancialDraftItem[];
  copiedCount: number;
  message: string;
  allocation: FinancialAllocation;
  contributions: FinancialContributions;
};
export function getFinancialYearStatus(year: string, signal?: AbortSignal) {
  return request<FinancialYearStatus>(
    `/api/financial-items/year-status?${new URLSearchParams({ year })}`,
    { signal },
  );
}
export function createFinancialPreview(
  body: FinancialPreviewRequest,
  signal?: AbortSignal,
) {
  return request<FinancialPreviewResult>("/api/financial-items/preview", {
    method: "POST",
    body: JSON.stringify(body),
    signal,
  });
}
export function financialPreviousAddition(
  row: FinancialDraftItem,
): FinancialItemAddition {
  return {
    finClass: row.finClass,
    sgaItemCode: row.sgaItemCode,
    itemName: row.itemName,
    amount: row.amount,
    vatAmount: row.vatAmount,
    ioDate: row.ioDate,
    repeat: row.repeat,
    remark: row.remark,
    copySource: {
      month: row.sourceMonth,
      itemSn: row.sourceItemSn,
      mode: "PREVIOUS",
    },
  };
}
export function formatFinancialWon(
  value: string | null,
  unit: "원" | "천원" | "백만원" = "천원",
) {
  if (value === null) return "–";
  const negative = value.startsWith("-");
  const digits = negative ? value.slice(1) : value;
  const scale = unit === "원" ? 0 : unit === "천원" ? 3 : 6;
  const padded = digits.padStart(scale + 1, "0");
  const integer = (scale ? padded.slice(0, -scale) : padded).replace(
    /\B(?=(\d{3})+(?!\d))/g,
    ",",
  );
  const fraction = scale ? padded.slice(-scale).replace(/0+$/, "") : "";
  return `${negative ? "−" : ""}${integer}${fraction ? `.${fraction}` : ""}`;
}
