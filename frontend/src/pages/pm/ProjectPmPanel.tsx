import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import {
  getUserProjectPm,
  userProjectsCommittedEvent,
  type UserProjectPm,
  type UserProjectPmData,
} from "../businessusersops/userProjectsApi";
import "./pm.css";

// SCR-UI-01: readonly PM region; assignment/removal stays in SCR-UI-23.
export function ProjectPmPanel({
  projectId,
  refreshKey = 0,
}: {
  projectId: string;
  refreshKey?: number;
}) {
  const [data, setData] = useState<UserProjectPmData>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setData(undefined);
    setError(undefined);
    getUserProjectPm(projectId, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setData(value);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [projectId, refreshKey, refresh]);
  useEffect(() => {
    const update = () => setRefresh((value) => value + 1);
    window.addEventListener(userProjectsCommittedEvent, update);
    return () => window.removeEventListener(userProjectsCommittedEvent, update);
  }, []);
  const columns: Column<UserProjectPm>[] = [
    { id: "name", label: "이름", render: (row) => row.name },
    { id: "userId", label: "아이디", render: (row) => row.userId },
  ];
  return (
    <section
      className="project-pm-panel"
      data-testid="project-pm-panel"
      data-screen-id="SCR-UI-01"
      aria-label="프로젝트 PM"
    >
      <h2>PM</h2>
      <p>
        프로젝트 PM은 읽기 전용입니다. 사용자 상세의 담당 프로젝트에서
        지정·교체·해제합니다.
      </p>
      <Link
        className="project-pm-manage"
        to="/UI-23"
        data-testid="project-pm-manage"
      >
        사용자에서 담당 프로젝트 관리
      </Link>
      {error != null ? (
        <ErrorBanner
          message={
            error instanceof Error ? error.message : "PM을 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRefresh((value) => value + 1)}
        />
      ) : (
        <DataTable
          rows={data?.items ?? []}
          columns={columns}
          rowKey={(row) => `${row.userId}-${row.scopeSn}`}
          loading={loading}
          empty="지정된 PM이 없습니다."
        />
      )}
    </section>
  );
}
