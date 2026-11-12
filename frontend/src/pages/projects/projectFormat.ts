import type { Tone } from "../../shared/Feedback";
export { formatDistributionAmount as projectAmount } from "../distribution/MonthlyMatrixTable";
export function projectMoneyInput(input: string): string | null {
  const value = input.replace(/[\s\p{Z},]/gu, "").replace(/원$/, "");
  if (!/^[0-9]+$/.test(value) || BigInt(value) > 999999999999999n) return null;
  return BigInt(value).toString();
}
export const projectStatusTone: Record<string, Tone> = {
  ING: "info",
  PRE: "warn",
  END: "lock",
  LOSE: "bad",
};
export const projectColumns = [
  { id: "id", label: "프로젝트ID", required: true },
  { id: "name", label: "프로젝트명", required: true },
  { id: "customerName", label: "고객사" },
  { id: "contractType", label: "계약유형" },
  { id: "contractPeriod", label: "계약 기간" },
  { id: "contractAmount", label: "계약금액" },
  { id: "personCount", label: "투입 인원" },
  { id: "partName", label: "영업 파트" },
  { id: "status", label: "상태" },
  { id: "distribution", label: "배분표" },
];
