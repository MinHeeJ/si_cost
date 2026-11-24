import { useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { PermissionSettingsPanel } from "../currentuserops/PermissionSettingsPanel";
import { SharedScreenFrame } from "./FollowUpScreen";
import { PermissionsScreen as RolePermissionsScreen } from "../roles/PermissionsScreen";

const permissionsGuide = (
  <dl>
    <div>
      <dt>역할과 적용 범위</dt>
      <dd>
        역할을 선택한 뒤 데이터 범위와 민감 항목 보기 여부를 설정합니다.
        급여·지급단가·개인별 원가·거래처 지급계좌는 허용된 업무와 담당
        범위에서만 표시됩니다.
      </dd>
    </div>
    <div>
      <dt>메뉴별 권한</dt>
      <dd>
        메뉴마다 일곱 동작을 따로 설정합니다. 메뉴에 접근하려면 조회 권한도
        필요합니다. 확정은 월마감, 영업파트 정산확정·취소, 견적승인·반려에
        적용되며, 마감해제에는 별도 관리자 자격이 필요합니다.
      </dd>
    </div>
    <div>
      <dt>저장과 이력</dt>
      <dd>
        저장을 누르면 해당 역할의 변경 사항이 적용됩니다. 변경 이력에서는
        저장된 값과 처리자를 확인할 수 있습니다.
      </dd>
    </div>
  </dl>
);

export function PermissionsScreen({
  onDirtyChange,
  settingsVersion,
}: {
  onDirtyChange?(dirty: boolean): void;
  settingsVersion?: number;
}) {
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [historyActionTarget, setHistoryActionTarget] = useState<HTMLDivElement | null>(null);
  const closeSettings = () => setSettingsOpen(false);
  return (
    <SharedScreenFrame
      screenId="UI-24"
      periodBar={null}
      screenGuide={permissionsGuide}
      headerActions={
        <div className="sicost-title-actions">
          <div ref={setHistoryActionTarget} />
          <button
            type="button"
            aria-haspopup="dialog"
            onClick={() => setSettingsOpen(true)}
          >
            내 저장 권한
          </button>
        </div>
      }
    >
      <RolePermissionsScreen
        onDirtyChange={onDirtyChange}
        historyActionTarget={historyActionTarget}
      />
      <Dialog
        open={settingsOpen}
        wide
        hideCancel
        title="내 저장 권한"
        confirmLabel="닫기"
        onConfirm={closeSettings}
        onCancel={closeSettings}
      >
        <PermissionSettingsPanel key={settingsVersion} embedded />
      </Dialog>
    </SharedScreenFrame>
  );
}
