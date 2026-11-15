import type { Key } from "react";

export function ProfitabilityExpansionActions({
  personIds,
  expandedKeys,
  loading,
  onExpandedChange,
}: {
  personIds: string[];
  expandedKeys: Key[];
  loading: boolean;
  onExpandedChange(keys: Key[]): void;
}) {
  return (
    <div
      className="profitability-toolbar"
      data-testid="profitability-expansion-actions"
      onClick={(event) => event.stopPropagation()}
    >
      <button
        type="button"
        data-screen-id="SCR-UI-03-EXPAND-ALL"
        data-testid="profitability-expand-page"
        disabled={loading || !personIds.length}
        onClick={() => onExpandedChange(personIds)}
      >
        현재 페이지 모두 펼치기
      </button>
      <button
        type="button"
        data-screen-id="SCR-UI-03-COLLAPSE-ALL"
        data-testid="profitability-collapse-all"
        disabled={!expandedKeys.length}
        onClick={() => onExpandedChange([])}
      >
        모두 접기
      </button>
    </div>
  );
}
