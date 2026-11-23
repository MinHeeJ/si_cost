import type { ReactNode } from "react";
import { FollowUpScreen, SharedScreenFrame } from "./FollowUpScreen";
export function UsersScreen({ children }: { children?: ReactNode }) {
  return children ? (
    <SharedScreenFrame
      screenId="UI-23"
      screenGuide={
        <dl>
          <div>
            <dt>권한과 담당 범위</dt>
            <dd>저장된 역할·메뉴 권한과 담당 범위를 적용합니다.</dd>
          </div>
          <div>
            <dt>사용 중지 계정 조회</dt>
            <dd>
              사용 중지된 계정은 기본 목록에서 숨깁니다. 상태를 ‘중지’로
              선택하면 중지 계정만 볼 수 있습니다. 상태가 ‘전체’일 때
              ‘사용 중지 포함’을 체크하면 다른 계정과 함께 조회됩니다.
            </dd>
          </div>
          <div>
            <dt>담당 파트</dt>
            <dd>목록에서 각 사용자에게 지정된 영업 파트를 확인할 수 있습니다.</dd>
          </div>
        </dl>
      }
    >
      {children}
    </SharedScreenFrame>
  ) : (
    <FollowUpScreen screenId="UI-23" />
  );
}
