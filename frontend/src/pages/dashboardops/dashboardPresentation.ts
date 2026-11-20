// Presentation only: keep API codes, financial values and navigation contracts intact.
export const dashboardCheckLabels: Record<string, string> = {
  PAY: "급여 업로드",
  MM_OVER: "MM 초과 점검",
  BILL: "세금계산서 번호 미입력",
  FI: "재무 항목",
  SETL: "영업 파트 정산",
  BILL_MISS: "청구 누락 점검",
};
export const dashboardContractLabels: Record<string, string> = {
  TK: "턴키",
  MO: "월단위",
  DP: "파견",
  IN: "내부(자체 개발)",
};

// Apply only to diagnostic/reason fields, never project, opportunity or part names.
// Those business names may legitimately contain English words and slashes.
const internalDetail =
  /\b(?:Receivables|CorporateTax|CashFlow|Workforce|Project|MonthClose|OperatingProfit|BusinessPlan|ProjectCost|ProjectStatus|WorkforceForecast|SalesPipeline|Withdrawal|OperatingStandards)\b|\/api\/\S+|\b[A-Z][A-Z0-9]*_[A-Z0-9_]+\b|\b(?:requestId|traceId)\b/;
export function dashboardMessage(
  text: string,
  fallback = "상세 화면에서 확인해 주세요.",
): string {
  if (internalDetail.test(text) || /^[A-Z][A-Z0-9_]*$/.test(text.trim()))
    return fallback;
  return text;
}
