import { SharedScreenFrame } from "./FollowUpScreen";
import { WithdrawalListScreen as WithdrawalBody } from "../apiops/WithdrawalListScreen";

const withdrawalGuide = (
  <dl>
    <div>
      <dt>철수 예정·완료 내역 조회</dt>
      <dd>
        선택한 연도에 철수하는 배정을 기간, 연장 가능성, 직무로 좁혀 볼 수
        있습니다. 지난 연도는 조회만 할 수 있습니다.
      </dd>
    </div>
    <div>
      <dt>연장 가능성과 다음 배정</dt>
      <dd>
        연장 가능성 변경은 인력 운영 보드와 인력 수요 예측에 반영됩니다.
        다음 배정 열의 추천 버튼에서 해당 인력에게 맞는 자리를 확인할 수
        있습니다.
      </dd>
    </div>
  </dl>
);

export function WithdrawalListScreen() {
  return (
    <SharedScreenFrame screenId="UI-05" screenGuide={withdrawalGuide}>
      <WithdrawalBody />
    </SharedScreenFrame>
  );
}
