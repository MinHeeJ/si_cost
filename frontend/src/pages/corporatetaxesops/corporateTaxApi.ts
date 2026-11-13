import { request } from "../../shared/api";

export type CorporateTaxAudit = {
  regUserId: string;
  regDttm: string;
  modUserId: string | null;
  modDttm: string | null;
};
export type CorporateTaxAnnual = {
  year: string;
  estimatedTax: string;
  audit: CorporateTaxAudit;
};
export type CorporateTaxPayment = {
  year: string;
  payType: "MID" | "CNFM";
  dueDate: string;
  payAmount: string | null;
  paidDate: string | null;
  payYn: "Y" | "N";
  displayAmount: string | null;
  automaticAmount: string | null;
  manuallyEdited: boolean;
  status: "납부" | "확인 필요" | "예정";
  confirmationRevision: string;
  audit: CorporateTaxAudit;
};
export type CorporateTaxAllocationMonth = {
  month: string;
  closeStatus: "WORK" | "CLOSE";
  amount: string | null;
  verified: boolean;
  fixed: string | null;
  migrated: string | null;
};
export type CorporateTaxAllocation = {
  year: string;
  estimatedTax: string | null;
  closedTotal: string;
  remainingAmount: string | null;
  openMonthCount: number;
  months: CorporateTaxAllocationMonth[];
  verified: boolean;
  warnings: string[];
};
export type CorporateTaxDefaults = {
  changed: boolean;
  corporateTax: CorporateTaxAnnual;
  taxPayments: CorporateTaxPayment[];
  revision: string;
};
export type CorporateTaxConfirmation = {
  changed: true;
  payment: CorporateTaxPayment;
  revision: string;
};
export type CorporateTaxContext = {
  month: string;
  today: string;
  cashBaseMonth: string;
  editable: boolean;
  readOnlyReason: string | null;
  corporateTax: CorporateTaxAnnual | null;
  taxPayments: CorporateTaxPayment[];
};
export const CORPORATE_TAX_COMMITTED = "sicost:corporate-tax-committed";
export const getCorporateTaxAllocation = (year: string) =>
  request<CorporateTaxAllocation>(
    `/api/corporate-taxes/${encodeURIComponent(year)}/allocation`,
  );
export const createCorporateTaxDefaults = (month: string) =>
  request<CorporateTaxDefaults>("/api/corporate-taxes/initialize", {
    method: "POST",
    body: JSON.stringify({ month }),
  });
export const saveCorporateTaxPaymentConfirmation = (
  year: string,
  payType: CorporateTaxPayment["payType"],
  input: {
    month: string;
    expectedRevision: string;
    paidDate: string;
    payAmount: string;
  },
) =>
  request<CorporateTaxConfirmation>(
    `/api/corporate-taxes/${encodeURIComponent(year)}/payments/${encodeURIComponent(payType)}/confirmation`,
    { method: "POST", body: JSON.stringify(input) },
  );
export const getCorporateTaxContext = (month: string) =>
  request<CorporateTaxContext>(
    `/api/financial-items?${new URLSearchParams({ month })}`,
  );
