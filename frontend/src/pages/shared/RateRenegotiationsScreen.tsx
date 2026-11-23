import { SharedScreenFrame } from "./FollowUpScreen";
import { RateRenegotiationsScreen as RateRenegotiationsBody } from "../apiops/RateRenegotiationsScreen";
export function RateRenegotiationsScreen() {
  return (
    <SharedScreenFrame
      screenId="UI-32"
      screenGuide={
        <section>
          <h3>단가 인상 시뮬레이션</h3>
          <p>
            인상률을 입력하고 재계산하면, 현재 계약과 같은 투입량으로 12개월
            일한다고 가정했을 때 이익이 얼마나 늘어나는지 볼 수 있습니다.
            입력한 인상률은 결과를 미리 보는 데 사용되며 실제 계약 단가는 그대로입니다.
          </p>
        </section>
      }
    >
      <RateRenegotiationsBody />
    </SharedScreenFrame>
  );
}
