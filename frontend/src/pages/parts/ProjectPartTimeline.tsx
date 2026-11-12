import { DataTable, type Column } from "../../shared/DataTable";
import { StatusBadge } from "../../shared/Feedback";
import type { ProjectPart } from "./projectPartsApi";

export function projectPartEndMonth(next?: string) {
  if (!next) return null;
  const [year, month] = next.split("-").map(Number);
  return month === 1
    ? `${year - 1}-12`
    : `${year}-${String(month - 1).padStart(2, "0")}`;
}
export function ProjectPartTimeline({
  items,
  currentMonth,
}: {
  items: ProjectPart[];
  currentMonth?: string;
}) {
  const current = currentMonth
    ? items.filter((row) => row.effectiveMonth <= currentMonth).at(-1)
        ?.effectiveMonth
    : undefined;
  const columns: Column<ProjectPart>[] = [
    { id: "start", label: "적용 시작월", render: (row) => row.effectiveMonth },
    {
      id: "end",
      label: "적용 종료월",
      render: (row) =>
        projectPartEndMonth(items[items.indexOf(row) + 1]?.effectiveMonth) ??
        "종료 없음",
    },
    {
      id: "part",
      label: "영업 파트",
      render: (row) => (
        <span data-testid={`project-part-row-${row.effectiveMonth}`}>
          {row.partName ?? row.partId ?? "미지정"}
        </span>
      ),
    },
    {
      id: "state",
      label: "구분",
      align: "center",
      render: (row) => (
        <StatusBadge
          tone={
            row.scheduled
              ? "warn"
              : row.effectiveMonth === current
                ? "info"
                : "lock"
          }
        >
          {row.scheduled
            ? "예정"
            : row.effectiveMonth === current
              ? "현재"
              : currentMonth
                ? "과거"
                : "이력"}
        </StatusBadge>
      ),
    },
    { id: "reason", label: "변경 사유", render: (row) => row.reason ?? "–" },
    { id: "actor", label: "처리자", render: (row) => row.actor },
  ];
  return (
    <DataTable
      rows={items}
      columns={columns}
      rowKey={(row) => row.effectiveMonth}
      empty="등록된 영업 파트 이력이 없습니다."
    />
  );
}
