import type { CorporateTaxPayment } from "./corporateTaxApi";

export function FinancialPaymentStatus({
  status,
}: {
  status: CorporateTaxPayment["status"];
}) {
  const tone =
    status === "납부" ? "ok" : status === "확인 필요" ? "warn" : "info";
  return (
    <span className={`corporate-tax-badge corporate-tax-${tone}`}>
      {status}
    </span>
  );
}
