import { SharedScreenFrame } from "./FollowUpScreen";
import { StaffingPlanComparisonScreen as ComparisonBody } from "../staffingplancomparisonops/StaffingPlanComparisonScreen";

const staffingComparisonGuide = (
  <dl>
    <div>
      <dt>연도별 계획과 투입 조회</dt>
      <dd>
        선택한 연도의 1~12월을 기준으로 프로젝트·직무별 계획 MM과
        실제·예정 MM을 비교합니다. 프로젝트와 차이(부족·초과·맞음)로
        조회 결과를 좁힐 수 있습니다.
      </dd>
    </div>
    <div>
      <dt>계획 MM 계산</dt>
      <dd>
        프로젝트 상세의 [인원 계획]에서 입력한 직무별 전체 계획 MM에
        선택 연도와 겹치는 계약 개월 수를 곱하고 전체 계약 개월 수로 나눕니다.
      </dd>
    </div>
    <div>
      <dt>프로젝트 상세</dt>
      <dd>
        프로젝트명을 누르면 해당 프로젝트의 [인원 계획] 탭이 열립니다.
        상세 화면은 전체 계약기간의 계획을 기준으로 비교합니다.
      </dd>
    </div>
  </dl>
);

export function StaffingPlanComparisonScreen() {
  return (
    <SharedScreenFrame screenId="UI-11" screenGuide={staffingComparisonGuide}>
      <ComparisonBody />
    </SharedScreenFrame>
  );
}
