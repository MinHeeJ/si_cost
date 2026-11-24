import { SharedScreenFrame } from "./FollowUpScreen";
import { WorkforceOperationsScreen as OperationsBody } from "../apiops/WorkforceOperationsScreen";

const workforceOperationsGuide = (
  <dl>
    <div>
      <dt>달력에서 월별 투입 확인</dt>
      <dd>
        선택한 연도의 인력별 투입·가배정·여유 현황을 조회합니다. 직무,
        등급, 고용 형태로 좁힐 수 있고, 월 칸을 선택하면 프로젝트별 MM을
        확인할 수 있습니다.
      </dd>
    </div>
    <div>
      <dt>수요·공급 전망</dt>
      <dd>
        기준일이 속한 달의 다음 달부터 6개월간 전체 인력의 수요와 공급을
        봅니다. 달력에서 선택한 검색 조건은 달력에만 적용됩니다.
      </dd>
    </div>
  </dl>
);

export function WorkforceOperationsScreen() {
  return (
    <SharedScreenFrame screenId="UI-28" screenGuide={workforceOperationsGuide}>
      <OperationsBody />
    </SharedScreenFrame>
  );
}
