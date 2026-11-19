import { ChangeHistoryTable } from "./ChangeHistoryTable";
import { useCallback } from "react";
import { useCodeNames } from "../../shared/codeNames";
import { formatAmount } from "../../shared/format";
import { getAreaPath } from "../../shared/areaClient";
import {
  getPeopleHistory,
  type PeopleHistoryEntry,
  type PeopleHistoryTarget,
} from "./peopleClient";

type Props = {
  targetType: PeopleHistoryTarget;
  targetId: string;
  targetName?: string;
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
  const name = useCodeNames(
    "GRADE",
    "WORK_STAT",
    "EMP_TYPE",
    "JOB",
    "DRCT_CLS",
    "JBPS",
  );
  const loader = useCallback(
    async (...args: Parameters<typeof getPeopleHistory>) => {
      const page = await getPeopleHistory(...args);
      const areas = new Map<string, Promise<string>>();
      const resolve = (value: string | null) => {
        if (!value) return Promise.resolve(value);
        if (!areas.has(value))
          areas.set(
            value,
            getAreaPath(value)
              .then((path) => path.homeAddress ?? "–")
              .catch(() => {
                console.warn("변경 이력의 지역 이름을 찾지 못했습니다.");
                return "–";
              }),
          );
        return areas.get(value)!;
      };
      return {
        ...page,
        items: await Promise.all(
          page.items.map(async (row) => {
            if (!["homeAreaCode", "HOME_AREA_CD"].includes(row.itemName))
              return row;
            return {
              ...row,
              beforeValue: await resolve(row.beforeValue),
              afterValue: await resolve(row.afterValue),
            };
          }),
        ),
      };
    },
    [],
  );
  const adapter = useCallback(
    (entry: PeopleHistoryEntry): PeopleHistoryEntry => {
      const row = publicHistoryRow(entry);
      const fields: Record<string, [string, string?]> = {
        gradeCode: ["등급", "GRADE"],
        GRADE_CD: ["등급", "GRADE"],
        homeAreaCode: ["거주지"],
        HOME_AREA_CD: ["거주지"],
        WORK_STAT_CD: ["근무 상태", "WORK_STAT"],
        workStatus: ["근무 상태", "WORK_STAT"],
        status: ["근무 상태", "WORK_STAT"],
        MON_UPRC: ["월 단가", "amount"],
        monthlyRate: ["월 단가", "amount"],
        name: ["이름"],
        DEV_NM: ["이름"],
        employeeType: ["직원구분", "EMP_TYPE"],
        jobCode: ["주 직무", "JOB"],
        subJobCode: ["보조 직무", "JOB"],
        directClass: ["직접/간접", "DRCT_CLS"],
        positionCode: ["직위", "JBPS"],
        USE_YN: ["사용 여부"],
        useYn: ["사용 여부"],
        skills: ["보유 스킬"],
      };
      const field = fields[row.itemName];
      const display = (value: string | null) =>
        !value
          ? value
          : field?.[1] === "amount"
            ? formatAmount(value, "원")
            : field?.[1]
              ? name(field[1], value)
              : value;
      return {
        ...row,
        targetKey: props.targetName ?? "–",
        targetTable: "",
        itemName:
          field?.[0] ??
          (/^[A-Za-z_][A-Za-z0-9_]*$/.test(row.itemName)
            ? "변경 내용"
            : row.itemName),
        beforeValue: display(row.beforeValue),
        afterValue: display(row.afterValue),
      };
    },
    [name, props.targetName],
  );
  return (
    <div
      data-testid="people-history-panel"
      data-screen-id={props.targetType === "DEVELOPER" ? "UI-02" : "UI-33"}
    >
      <ChangeHistoryTable {...props} loader={loader} rowAdapter={adapter} />
    </div>
  );
}
