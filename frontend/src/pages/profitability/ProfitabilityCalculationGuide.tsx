import type { IndividualProfitabilityData } from "./individualProfitabilityApi";

/** Render the response's explanations, never a second financial calculator. */
export function ProfitabilityCalculationGuide({
  data,
}: {
  data: IndividualProfitabilityData;
}) {
  return (
    <section
      className="profitability-calculation-guide"
      data-testid="profitability-calculation-guide"
      aria-label="계산 방법"
    >
      <p data-testid="profitability-company-profit-note">
        내부 프로젝트·대기 인력은 매출이 없어 영업이익이 −원가로 보입니다. 회사
        전체 영업이익표에서는 내부 몫은 연구개발비(판관비), 대기 몫은 대기
        원가로 따로 잡혀 이 표의 합계와 다를 수 있습니다.
      </p>
      <details data-testid="profitability-calculation-panel">
        <summary data-testid="profitability-calculation-toggle">
          계산 방법 · 급여/분모/경비/턴키/평균 도움말
        </summary>
        <div data-testid="profitability-calculation-basis">
          {data.calculationBasis.length ? (
            <ul>
              {data.calculationBasis.map((basis, index) => (
                <li
                  key={`${index}:${basis}`}
                  data-testid={`profitability-calculation-basis-${index}`}
                >
                  {basis}
                </li>
              ))}
            </ul>
          ) : (
            <p>계산 근거 자료 없음 · 다시 조회해 주세요.</p>
          )}
        </div>
        <p>
          현재 페이지의 사람별 서버 분모입니다. 월별 원장을 펼치면 같은 조회
          문맥의 급여 출처·배분액·확인 필요 자료를 볼 수 있습니다.
        </p>
        <ul aria-label="사람별 평균·가동률 분모">
          {data.items.map((person, index) => (
            <li
              key={person.personId}
              data-testid={`profitability-calculation-person-${index}`}
            >
              {person.name ?? person.personId} ({person.managementNo ?? "–"}) ·
              평균 근무월 N=
              {person.workedMonths} · 직접 재직달 {person.employmentMonths} ·
              실제 투입달 {person.utilizedMonths}
              <ul>
                {person.monthBasis.map((month) => (
                  <li
                    key={month.month}
                    data-testid={`profitability-calculation-month-${index}-${month.month}`}
                  >
                    {month.month} · 월 MM {month.assignmentMm} ·{" "}
                    {month.explanation}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      </details>
    </section>
  );
}
