import { SharedScreenFrame } from "./FollowUpScreen";
import {
  ProjectCostAnalysis,
  type ProjectCostAnalysisProps,
} from "../apiops/ProjectCostAnalysis";
import { ProjectCostExportButton } from "../projectcostsops/ProjectCostExportButton";
const projectCostGuide = (
  <dl>
    <div>
      <dt>조회 기간과 프로젝트 상세</dt>
      <dd>
        월 선택에서 누적 실적을 확인할 기준월을 고릅니다. ‘집계 기간’은
        현재 조회 결과에 포함된 시작월과 종료월입니다. 프로젝트 행을 누르면
        월별 원가와 경보 근거를 볼 수 있고, 시뮬레이션에서 조건을 바꿔
        예상 결과를 비교할 수 있습니다.
      </dd>
    </div>
    <div>
      <dt>계산 방법</dt>
      <dd>
        누적 원가는 프로젝트에 배분된 인건비, 팀비·프로젝트경비와
        추가 직접 경비를 더한 금액입니다. 추가 직접 경비는 한 번만
        반영합니다. 턴키 매출은 현재 계약금액에 진행률을 곱해 원 단위로
        절사합니다. 진행률은 누적 원가를 예산으로 나눈 값이며
        최대 100%를 적용합니다. 월단위·파견 매출은 귀속월별 매출을 합산합니다.
      </dd>
    </div>
    <div>
      <dt>경보와 미확인 금액</dt>
      <dd>
        턴키는 예상 최종 마진율, 월단위·파견은 선택월의 마진율을 목표와
        비교합니다. 마진율이 목표보다 경고 기준폭을 넘게 낮으면 ‘저마진’으로 표시합니다.
        ‘예산 소진율’은 누적 원가가 예산의 몇 퍼센트인지 보여 주며,
        100% 이상이면 ‘예산 초과’가 표시됩니다. 확인할 수 없는 금액은
        0원으로 합산하지 않습니다.
      </dd>
    </div>
  </dl>
);

export function ProjectCostAnalysisScreen(
  props: ProjectCostAnalysisProps = {},
) {
  return (
    <SharedScreenFrame screenId="UI-04" periodBar={null} screenGuide={projectCostGuide}>
      <ProjectCostAnalysis
        renderExport={(query, unit) => (
          <ProjectCostExportButton query={query} unit={unit} />
        )}
        {...props}
      />
    </SharedScreenFrame>
  );
}
