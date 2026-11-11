import { useEffect, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import * as api from "./peopleClient";
import "./developerActions.css";

const columns: Column<api.DeveloperAssignment>[] = [
  {
    id: "project",
    label: "프로젝트",
    render: (row) => (
      <span>
        {row.projectName}
        <small className="developer-secondary">{row.projectId}</small>
      </span>
    ),
  },
  { id: "job", label: "직무", render: (row) => row.jobName },
  {
    id: "period",
    label: "투입기간",
    render: (row) => `${row.startDate} ~ ${row.endDate}`,
  },
  {
    id: "mm",
    label: "계약 MM",
    align: "right",
    render: (row) => row.contractMm,
  },
  {
    id: "status",
    label: "상태",
    render: (row) => (
      <StatusBadge
        tone={
          row.assignmentStatus === "ACT"
            ? "info"
            : row.assignmentStatus === "CNCL"
              ? "bad"
              : "lock"
        }
      >
        {({ ACT: "투입", CNCL: "취소", END: "종료" } as Record<string, string>)[
          row.assignmentStatus
        ] ?? row.assignmentStatus}
      </StatusBadge>
    ),
  },
];
// SCR-UI-02 / SLICE-BASIC132-DEVELOPERS: mounted in the existing drawer's assignment tab.
export function DeveloperAssignmentsPanel({
  developerId,
  active = true,
}: {
  developerId: string;
  active?: boolean;
}) {
  return active ? (
    <Assignments key={developerId} developerId={developerId} />
  ) : null;
}
function Assignments({ developerId }: { developerId: string }) {
  const [data, setData] = useState<api.DeveloperAssignmentsPage>();
  const [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(50);
  const [version, setVersion] = useState(0),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void api
      .getDeveloperAssignments(developerId, page, size, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [developerId, page, size, version]);
  return (
    <section
      role="tabpanel"
      aria-label="투입 이력"
      data-testid="developer-assignments-panel"
      data-screen-id="UI-02"
      className="developer-assignments-panel"
    >
      <h2>투입 이력 {data ? `(${data.total})` : ""}</h2>
      <p className="developer-secondary">
        종료·취소된 투입을 포함합니다. 시작일 내림차순으로 표시합니다.
      </p>
      {error ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "투입 이력을 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setVersion((v) => v + 1)}
        />
      ) : (
        <DataTable
          rows={data?.items ?? []}
          rowKey={(row) => row.id}
          columns={columns}
          loading={loading}
          empty="투입 이력이 없습니다."
          page={page}
          size={size}
          total={data?.total ?? 0}
          onPageChange={setPage}
          onSizeChange={(value) => {
            setSize(value);
            setPage(1);
          }}
        />
      )}
    </section>
  );
}
