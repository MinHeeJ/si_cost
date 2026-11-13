import { request } from "../../shared/api";
import type {
  CorporateTaxContext,
  CorporateTaxAllocation,
} from "../corporatetaxesops/corporateTaxApi";
import type { FinancialContributions } from "../apiops/financialReadApi";

export type Values = Record<string, string | null>;
export type StoredRow = Record<string, unknown>;
export type CopySource = {
  month: string;
  itemSn: string;
  mode: "REPEAT" | "PREVIOUS";
};
export type DraftRow = {
  key: string;
  id?: string;
  values: Values;
  copySource?: CopySource;
  automatic?: boolean;
  errors?: { field: string; message: string }[];
};
export type Collection = "items" | "adjustments" | "transfers" | "loans";
export type FinancialEditor = CorporateTaxContext & {
  revision: string;
  uploadRevision: string;
  closeStatus: "WORK" | "CLOSE";
  items: StoredRow[];
  automaticDrafts: StoredRow[];
  adjustments: StoredRow[];
  transfers: StoredRow[];
  loans: StoredRow[];
  cards: { cardId: string; cardName: string; last4: string; useYn: string }[];
  cardPayments: { cardId: string; amount: string }[];
  cardMonth: {
    cardSumAmount: string;
    cashAmount: string;
    editYn: string;
    difference: string;
    exists: boolean;
  };
  adjustmentTargetMonths: string[];
  firstEditableMonth: string | null;
  allocation: CorporateTaxAllocation;
  contributions: FinancialContributions;
};
export type FinancialCommit = {
  changed: boolean;
  revision: string;
  message: string;
  editor: FinancialEditor;
  createdCards: { clientKey: string; cardId: string }[];
};
export type YearStatus = {
  year: string;
  months: {
    month: string;
    closeStatus: "WORK" | "CLOSE";
    itemCount: number | null;
    selectable: boolean;
    editable: boolean;
  }[];
};
export type Preview = {
  addedDrafts: StoredRow[];
  allocation: CorporateTaxAllocation;
  contributions: FinancialContributions;
  message: string;
};
export const getFinancialItems = (month?: string, signal?: AbortSignal) =>
  request<FinancialEditor>(
    `/api/financial-items${month ? `?${new URLSearchParams({ month })}` : ""}`,
    { signal },
  );
export const saveFinancialItems = (body: Record<string, unknown>) =>
  request<FinancialCommit>("/api/financial-items", {
    method: "PUT",
    body: JSON.stringify(body),
  });
export const getFinancialYearStatus = (year: string, signal?: AbortSignal) =>
  request<YearStatus>(
    `/api/financial-items/year-status?${new URLSearchParams({ year })}`,
    { signal },
  );
export const createFinancialPreview = (
  body: Record<string, unknown>,
  action: "CALCULATE" | "LOAD_PREVIOUS",
) =>
  request<Preview>("/api/financial-items/preview", {
    method: "POST",
    body: JSON.stringify({ ...body, action }),
  });
export const financialIds: Record<Collection, string> = {
  items: "itemSn",
  adjustments: "adjustmentId",
  transfers: "transferId",
  loans: "loanId",
};
export const financialFields: Record<Collection, string[]> = {
  items: [
    "finClass",
    "sgaItemCode",
    "itemName",
    "amount",
    "vatAmount",
    "ioDate",
    "repeat",
    "remark",
  ],
  adjustments: ["targetMonth", "itemCode", "amount", "reason"],
  transfers: ["date", "classCode", "itemName", "receiver", "amount", "remark"],
  loans: ["date", "direction", "amount", "counterparty", "remark"],
};
export const sameValues = (a: Values, b: Values) =>
  JSON.stringify(a) === JSON.stringify(b);
export function draftRows(
  rows: StoredRow[],
  collection: Collection,
): DraftRow[] {
  return rows.map((row) => ({
    key: String(row[financialIds[collection]]),
    id: String(row[financialIds[collection]]),
    values: Object.fromEntries(
      financialFields[collection].map((field) => [
        field,
        typeof row[field] === "string" ? row[field] : null,
      ]),
    ),
  }));
}
export function copiedRow(
  row: StoredRow,
  key: string,
  mode: CopySource["mode"],
): DraftRow {
  return {
    key,
    values: Object.fromEntries(
      financialFields.items.map((field) => [
        field,
        typeof row[field] === "string" ? row[field] : null,
      ]),
    ),
    copySource: {
      month: String(row.sourceMonth),
      itemSn: String(row.sourceItemSn),
      mode,
    },
    automatic: mode === "REPEAT",
    errors: row.errors as DraftRow["errors"],
  };
}
