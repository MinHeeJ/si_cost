import { useEffect, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import * as api from "./clientActionsApi";
import "./clientActions.css";

const columns: Column<api.ClientAssignment>[] = [
  {
    id: "project",
    label: "프로젝트",
    render: (row) => (
      <span>
        {row.projectName}
        <small className="client-secondary">{row.projectId}</small>
      </span>
    ),
  },
  { id: "managementNo", label: "관리번호", render: (row) => row.managementNo },
  { id: "developerName", label: "이름", render: (row) => row.developerName },
  { id: "jobName", label: "직무", render: (row) => row.jobName },
  {
    id: "period",
    label: "투입기간",
    render: (row) => `${row.inputBeginDate} ~ ${row.inputEndDate}`,
  },
];
// SCR-UI-15: both count links open this same assignment tab, with no independent sort/filter contract.
export function ClientAssignmentsPanel({
  clientId,
  basisDate,
  active = true,
  onBasisDateChanged,
}: {
  clientId: string;
  basisDate: string;
  active?: boolean;
  onBasisDateChanged?(): void;
}) {
  return active ? (
    <Assignments
      key={clientId}
      clientId={clientId}
      basisDate={basisDate}
      onBasisDateChanged={onBasisDateChanged}
    />
  ) : null;
}
function Assignments({
  clientId,
  basisDate,
  onBasisDateChanged,
}: {
  clientId: string;
  basisDate: string;
  onBasisDateChanged?(): void;
}) {
  const [data, setData] = useState<api.ClientAssignments>(),
    [page, setPage] = useState(1);
  const [size, setSize] = useState<20 | 50 | 100>(50),
    [version, setVersion] = useState(0);
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void api
      .getClientAssignments(clientId, page, size, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setData(result);
        if (result.basisDate !== basisDate) onBasisDateChanged?.();
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [clientId, page, size, version, basisDate, onBasisDateChanged]);
  return (
    <section
      className="client-assignments-panel"
      data-testid="client-assignments-panel"
      role="tabpanel"
      aria-label="투입 내역"
    >
      <h2>투입 내역 {data ? `(${data.total})` : ""}</h2>
      <p className="client-secondary">
        기준일 {data?.basisDate ?? basisDate} · 진행/계약 전 프로젝트의 현재
        투입 내역
      </p>
      {error ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "투입 내역을 불러오지 못했습니다."
          }
          onRetry={() => setVersion((v) => v + 1)}
        />
      ) : (
        <DataTable
          rows={data?.items ?? []}
          rowKey={(row) => row.assignmentId}
          columns={columns}
          loading={loading}
          empty="현재 투입 내역이 없습니다."
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
