import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { request } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { ChangeHistoryPanel } from "./ChangeHistoryPanel";
import { SharedScreenFrame } from "./FollowUpScreen";
import { useSharedScreen } from "./SharedScreenContext";
const commonCodesGuide = (
  <dl>
    <div>
      <dt>코드 등록·수정·사용 중지</dt>
      <dd>
        왼쪽에서 코드 그룹을 고른 뒤 오른쪽 목록에서 코드를 관리합니다.
        코드 이름을 저장하면 선택 목록과 과거 자료에도 새 이름으로 표시됩니다.
        한 번도 사용하지 않은 코드는 삭제할 수 있고, 사용 이력이 있는 코드는
        사용을 끌 수 있습니다. 삭제 기록은 변경 이력에 남습니다.
        그룹 이름과 그룹의 사용 여부는 변경할 수 없습니다.
      </dd>
    </div>
    <div>
      <dt>시스템 코드</dt>
      <dd>
        자물쇠가 표시된 시스템 코드는 계산 규칙에 쓰입니다. 표시 이름만
        바꿀 수 있으며 코드 추가·삭제·사용 중지는 제한됩니다.
      </dd>
    </div>
    <div>
      <dt>엑셀 표기 대응</dt>
      <dd>
        엑셀에서 쓰는 표기를 공통코드에 연결합니다. 표기를 바꾸려면 기존
        연결을 삭제한 뒤 다시 등록하세요. 이미 등록된 업무 자료는 유지됩니다.
      </dd>
    </div>
    <div>
      <dt>운영 기준값</dt>
      <dd>
        이름·키·단위·설명은 조회용입니다. 값을 변경하면 선택한 적용 시작월부터
        반영되며 다른 적용월의 기록은 유지됩니다. 자금수지 시작 월과 기초
        잔액은 ‘자금수지 시작 기준’에서 별도로 관리합니다. 기존 시작월이
        마감된 경우에는 이 두 값을 변경할 수 없습니다.
      </dd>
    </div>
    <div>
      <dt>변경 이력</dt>
      <dd>
        ‘변경 이력’ 탭에서 대상이나 그룹을 골라 변경 내역을 조회할 수 있습니다.
        운영 기준값의 시점별 값은 해당 기준값의 ‘이력’에서 확인할 수 있습니다.
      </dd>
    </div>
  </dl>
);

export function CommonCodesScreen() {
  const location = useLocation();
  const initialTab =
    (location.state as { section?: string } | null)?.section === "standards"
      ? "standards"
      : "codes";
  const { codes, mappings, standards, pageSize, dense, savePageSize } =
    useSharedScreen("UI-27");
  const [active, setActive] = useState(initialTab),
    [groups, setGroups] = useState<{ groupId: string; name: string }[]>([]),
    [error, setError] = useState("");
  useEffect(() => {
    if (location.pathname === "/UI-27") setActive(initialTab);
  }, [location.pathname, location.key, initialTab]);
  const loadGroups = () => {
    setError("");
    void request<{ items: { groupId: string; name: string }[] }>(
      "/api/code-groups",
    )
      .then((result) => setGroups(result.items))
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "그룹 조회 실패"),
      );
  };
  useEffect(() => {
    if (!codes) loadGroups();
  }, [codes]);
  if (codes)
    return (
      <SharedScreenFrame screenId="UI-27" screenGuide={commonCodesGuide}>
        {codes}
      </SharedScreenFrame>
    );
  return (
    <SharedScreenFrame screenId="UI-27" screenGuide={commonCodesGuide}>
      <SegmentedTabList
        as="nav"
        className="sicost-code-tabs"
        aria-label="공통코드 탭"
        role="tablist"
      >
        {[
          ["codes", "코드"],
          ["mappings", "엑셀 표기 대응"],
          ["standards", "운영 기준값"],
          ["history", "변경 이력"],
        ].map(([id, label]) => (
          <button
            key={id}
            role="tab"
            data-testid={`common-${id}-tab`}
            id={`common-${id}-tab`}
            aria-selected={active === id}
            aria-controls={`common-${id}-panel`}
            onClick={() => setActive(id)}
          >
            {label}
          </button>
        ))}
      </SegmentedTabList>
      {error && <ErrorBanner message={error} onRetry={loadGroups} />}
      {(
        [
          ["codes", codes],
          ["mappings", mappings],
          ["standards", standards],
        ] as const
      ).map(([id, panel]) => (
        <div
          key={id}
          role="tabpanel"
          data-testid={`common-${id}-panel`}
          id={`common-${id}-panel`}
          aria-labelledby={`common-${id}-tab`}
          hidden={active !== id}
        >
          {panel ?? (
            <p className="sicost-followup">
              화면 구성 요소가 아직 연결되지 않았습니다.
            </p>
          )}
        </div>
      ))}
      <div
        role="tabpanel"
        id="common-history-panel"
        data-testid="common-history-panel"
        aria-labelledby="common-history-tab"
        hidden={active !== "history"}
      >
        {active === "history" && (
          <ChangeHistoryPanel
            groupOptions={groups}
            pageSize={pageSize}
            dense={dense}
            onPageSizeChange={savePageSize}
          />
        )}
      </div>
    </SharedScreenFrame>
  );
}
