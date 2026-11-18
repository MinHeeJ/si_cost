import {
  formatAmount,
  formatFixedDecimal,
  formatRate,
  type AmountUnit,
} from "../../shared/format";
import type { ProfitabilitySummaryData } from "./individualProfitabilityApi";

export function ProfitabilitySummary({
  summary: s,
  unit,
}: {
  summary: ProfitabilitySummaryData;
  unit: AmountUnit;
}) {
  return (
    <section
      className="profitability-summary"
      data-testid="profitability-summary"
      aria-label="개인 원가 수익 요약"
    >
      <article data-testid="profitability-summary-headcount">
        <h2>회사 총인원</h2>
        <strong>{s.companyHeadcount}명</strong>
        <p>휴직 {s.companyLeaveCount}명 포함</p>
        <small>
          월평균 가동 {formatFixedDecimal(s.averageUtilizedHeadcount, 1)}명 ·
          가득 {formatFixedDecimal(s.averageEarnedHeadcount, 1)}명
        </small>
      </article>
      <article data-testid="profitability-summary-revenue">
        <h2>평균 개인 단가</h2>
        <strong>{formatAmount(s.averageRevenue, unit)}</strong>
        <p>
          유효 단가 {s.validRevenuePersonCount}명 · 미확인{" "}
          {s.unknownRevenueCount}명
        </p>
      </article>
      <article data-testid="profitability-summary-cost">
        <h2>평균 원가</h2>
        <strong>{formatAmount(s.averageCost, unit)}</strong>
        <p>미확인 {s.unknownCostCount}명</p>
      </article>
      <article data-testid="profitability-summary-profit">
        <h2>평균 이익</h2>
        <strong
          className={
            s.averageProfit?.startsWith("-") ? "profitability-negative" : ""
          }
        >
          {formatAmount(s.averageProfit, unit)}
        </strong>
        <p>확인분 적자 {s.confirmedLossCount}명</p>
      </article>
      <article data-testid="profitability-summary-utilization">
        <h2>회사 가동률 / 가득률</h2>
        <strong>
          {formatRate(s.companyUtilization)} / {formatRate(s.companyEarnedRate)}
        </strong>
        <p>월평균 재직 {formatFixedDecimal(s.averageHeadcount, 1)}명</p>
        <small>회사 지표는 검색조건과 무관합니다.</small>
      </article>
    </section>
  );
}
