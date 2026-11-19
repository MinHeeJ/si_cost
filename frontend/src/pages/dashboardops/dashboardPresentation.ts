// Presentation only: never apply these rules to registered project/opportunity names.
export const closeCheckLabels: Record<string, string> = {
  PAY: "급여 업로드",
  MM_OVER: "MM 초과",
  BILL: "세금계산서 번호 미입력",
  FI: "재무 항목",
  SETL: "영업 파트 정산",
  BILL_MISS: "청구 누락 점검",
};
export const contractTypeLabels: Record<string, string> = {
  TK: "턴키",
  MO: "월단위",
  DP: "파견",
  IN: "내부",
};
const sourceNames =
  /\b(?:Receivables|CorporateTax|CashFlow|Workforce(?:Forecast)?|Project(?:Cost|Status)?|MonthClose|OperatingProfit|SalesPipeline|BusinessPlan|OperatingStandards|Withdrawal)\b/;
const formulaLabels: Record<string, string> = {
  AMT: "금액",
  VAT_AMT: "부가세",
  IO_DT: "입출금일",
  FIN_YM: "재무 기준월",
  CASH_AMT: "현금 합계",
  PAY_AMT: "납부액",
  REAL_PAY_AMT: "실지급액",
  VAT_RT: "부가세율",
  DOWN: "절사",
  FT: "정규직",
  CT: "계약직",
  FL: "프리랜서",
  OS: "외주",
  IN: "입금",
  OUT: "출금",
};
/** System explanations may include cash-source formulas; translate known fields,
 * but do not leak a new diagnostic identifier when its meaning is unknown. */
export function dashboardMessage(
  value: string,
  fallback = "자료 상태를 확인해 주세요.",
): string {
  const message = value.replace(/\s*문의 번호:\s*\S+/g, "").trim();
  if (
    sourceNames.test(message) ||
    /\/api\/|\b[A-Za-z]+(?:Service|Controller|Repository|Mapper)\b|\b(?:request[_-]?id|SQLSTATE|[0-9a-f]{8}-[0-9a-f-]{27,})\b/i.test(
      message,
    )
  )
    return fallback;
  const translated = message.replace(
    /\b[A-Z][A-Z_]+\b/g,
    (token) => formulaLabels[token] ?? token,
  );
  if (
    (translated.match(/\b[A-Z][A-Z0-9_]+\b/g) ?? []).some(
      (token) => !["MM", "UI", "PDF"].includes(token),
    )
  )
    return fallback;
  return translated;
}
