import type { ReactNode } from "react";
import { useInRouterContext, useLocation } from "react-router-dom";
import { SharedScreenFrame } from "./FollowUpScreen";
import {
  IndividualProfitabilityScreen as ProfitabilityBody,
  type IndividualProfitabilityScreenProps,
} from "../profitability/IndividualProfitabilityScreen";
import { ProfitabilityExportButton } from "../profitability/ProfitabilityExportButton";
import { ProfitabilityCalculationGuide } from "../profitability/ProfitabilityCalculationGuide";
/** Keep this registered entry and its mounted work-tab identity during body assembly. */
type EntryProps = IndividualProfitabilityScreenProps & { children?: ReactNode };
export function IndividualProfitabilityScreen(props: EntryProps = {}) {
  const routed = useInRouterContext();
  return routed ? (
    <RoutedProfitabilityEntry {...props} />
  ) : (
    <ProfitabilityEntry {...props} />
  );
}
function RoutedProfitabilityEntry(props: EntryProps) {
  const location = useLocation();
  const incoming = location.state as {
    fromAnalysis?: boolean;
    month?: string;
    developerId?: string;
  } | null;
  const analysisEntry =
    location.pathname === "/UI-03" &&
    incoming?.fromAnalysis &&
    incoming.month &&
    /^\d{4}-(0[1-9]|1[0-2])$/.test(incoming.month)
      ? {
          key: location.key,
          year: Number(incoming.month.slice(0, 4)),
          developerId: incoming.developerId,
        }
      : undefined;
  return <ProfitabilityEntry {...props} analysisEntry={analysisEntry} />;
}
const profitabilityGuide = (
  <dl>
    <div>
      <dt>연 누계와 월 평균</dt>
      <dd>
        ‘연 누계’는 조회 기간의 금액을 합산해 보여 줍니다. ‘월 평균’은 그
        합계를 각 사람의 평균 계산에 포함되는 근무월 수로 나눈 값입니다.
      </dd>
    </div>
    <div>
      <dt>턴키 단가 포함</dt>
      <dd>
        조회 조건의 ‘상세조건’에서 턴키 프로젝트의 개인 단가를 포함할지
        선택할 수 있습니다. 체크를 끄면 턴키 단가를 빼고 이익을 다시
        계산합니다. 해당 프로젝트의 원가와 투입량은 계속 집계합니다.
      </dd>
    </div>
    <div>
      <dt>개인 단가</dt>
      <dd>
        개인 단가는 프로젝트에 투입된 사람별 수익을 비교할 때 쓰는 배분액입니다.
        고객에게 실제 청구했거나 입금받은 금액은 청구·입금 화면에서 확인하세요.
      </dd>
    </div>
  </dl>
);

function ProfitabilityEntry({ children, ...slots }: EntryProps) {
  return (
    <SharedScreenFrame screenId="UI-03" screenGuide={profitabilityGuide}>
      <div data-screen-id="UI-03" data-testid="individual-profitability-screen">
        {children ?? (
          <ProfitabilityBody
            renderExport={(query) => (
              <ProfitabilityExportButton query={query} />
            )}
            renderCalculationGuide={(data, query) => (
              <ProfitabilityCalculationGuide data={data} view={query.view} />
            )}
            {...slots}
          />
        )}
      </div>
    </SharedScreenFrame>
  );
}
