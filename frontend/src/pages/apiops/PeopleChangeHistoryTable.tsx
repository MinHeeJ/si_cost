import { ChangeHistoryTable } from "./ChangeHistoryTable";
import {
  getPeopleHistory,
  type PeopleHistoryEntry,
  type PeopleHistoryTarget,
} from "./peopleClient";

type Props = {
  targetType: PeopleHistoryTarget;
  targetId: string;
  refreshKey?: string | number;
  dense?: boolean;
};

function publicHistoryRow(row: PeopleHistoryEntry): PeopleHistoryEntry {
  if (!/(TELNO|PHONE|EMAIL|E-MAIL|연락처|전화|이메일)/i.test(row.itemName))
    return row;
  return {
    ...row,
    beforeValue: row.beforeValue === null ? null : "[비공개]",
    afterValue: row.afterValue === null ? null : "[비공개]",
    reason: null,
  };
}

// SCR-UI-02 / SCR-UI-33: the existing detail drawers mount this read-only tab.
export function PeopleChangeHistoryTable(props: Props) {
  return (
    <div
      data-testid="people-history-panel"
      data-screen-id={props.targetType === "DEVELOPER" ? "UI-02" : "UI-33"}
    >
      <ChangeHistoryTable
        {...props}
        loader={getPeopleHistory}
        rowAdapter={publicHistoryRow}
        showTargetTable
      />
    </div>
  );
}
