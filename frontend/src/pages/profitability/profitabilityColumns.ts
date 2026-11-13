import type { PreferenceColumn } from "../me/ColumnPreferencesDialog";

// Presentation metadata, not business data. All money/reference values come from the API.
export const profitabilityColumns: PreferenceColumn[] = [
  { id: "name", label: "이름", required: true },
  { id: "companyName", label: "소속" },
  { id: "position", label: "직위" },
  { id: "employeeType", label: "직원구분" },
  { id: "hireDate", label: "입사일" },
  { id: "utilization", label: "가동률" },
  { id: "mm", label: "MM" },
  { id: "revenue", label: "개인 단가", required: true },
  { id: "pay", label: "월급여" },
  { id: "allowance", label: "수당" },
  { id: "companyInsurance", label: "회사보험" },
  { id: "retirementReserve", label: "퇴직충당금" },
  { id: "personalCost", label: "개인 원가" },
  { id: "teamExpense", label: "팀비" },
  { id: "projectExpense", label: "프로젝트경비" },
  { id: "totalCost", label: "원가합계", required: true },
  { id: "profit", label: "영업이익", required: true },
  { id: "profitRate", label: "이익률" },
  { id: "selfInsurance", label: "본인보험" },
  { id: "withholdingTax", label: "원천세" },
  { id: "incomeTax", label: "근로소득세" },
  { id: "netPay", label: "실지급액" },
];
export const profitabilityReferenceColumns = [
  "selfInsurance",
  "withholdingTax",
  "incomeTax",
  "netPay",
];
