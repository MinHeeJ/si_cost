import { useEffect, useState } from "react";
import { request } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { ChangeHistoryPanel } from "./ChangeHistoryPanel";
import { SharedScreenFrame } from "./FollowUpScreen";
import { useSharedScreen } from "./SharedScreenContext";
export function CommonCodesScreen() {
  const { codes, mappings, standards, pageSize, dense, savePageSize } =
    useSharedScreen("UI-27");
  const [active, setActive] = useState("codes"),
    [groups, setGroups] = useState<{ groupId: string; name: string }[]>([]),
    [error, setError] = useState("");
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
    return <SharedScreenFrame screenId="UI-27">{codes}</SharedScreenFrame>;
  return (
    <SharedScreenFrame screenId="UI-27">
      <nav className="sicost-code-tabs" aria-label="공통코드 탭" role="tablist">
        {[
          ["codes", "코드"],
          ["mappings", "엑셀 표기 대응"],
          ["standards", "운영 기준값"],
          ["history", "변경 이력"],
        ].map(([id, label]) => (
          <button
            key={id}
            role="tab"
            id={`common-${id}-tab`}
            aria-selected={active === id}
            aria-controls={`common-${id}-panel`}
            onClick={() => setActive(id)}
          >
            {label}
          </button>
        ))}
      </nav>
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
