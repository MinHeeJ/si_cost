import { SharedScreenFrame } from "./FollowUpScreen";
import { MenusScreen as MenuManagementScreen } from "../menumanagement/MenusScreen";

const menusGuide = (
  <dl>
    <div>
      <dt>메뉴 이름과 표시 순서</dt>
      <dd>
        이름은 표에서 편집합니다. 순서는 이동 손잡이나 위·아래 버튼으로
        바꿀 수 있고, 하위 메뉴가 속할 대메뉴는 대메뉴 열에서 선택합니다.
      </dd>
    </div>
    <div>
      <dt>사용 여부와 PC 전용</dt>
      <dd>
        사용을 해제한 메뉴는 탐색 메뉴에서 숨겨집니다. PC 전용으로 설정한
        화면은 PC에서 열 수 있습니다. 잠긴 메뉴는 표시 상태나 대메뉴를
        변경할 수 없습니다.
      </dd>
    </div>
    <div>
      <dt>변경 적용</dt>
      <dd>
        저장을 누르면 메뉴 편집 내용이 함께 반영됩니다. 각 행의 변경
        이력에서 해당 메뉴의 기록을 확인할 수 있습니다.
      </dd>
    </div>
  </dl>
);

export function MenusScreen({ onDirtyChange }: { onDirtyChange?(dirty: boolean): void }) {
  return (
    <SharedScreenFrame screenId="UI-25" periodBar={null} screenGuide={menusGuide}>
      <MenuManagementScreen onDirtyChange={onDirtyChange} />
    </SharedScreenFrame>
  );
}
