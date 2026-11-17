import { useEffect, useState } from "react";
import { DataTable } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import * as api from "./userAdministrationApi";

export function UserActivityPanel({ id }: { id: string }) {
  const [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(50);
  const [data, setData] = useState<api.UserActivities>(),
    [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(),
    [refresh, setRefresh] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    api
      .getBusinessUserAccessLogs(id, page, size, controller.signal)
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
  }, [id, page, size, refresh]);
  return (
    <section
      role="tabpanel"
      aria-label="접근 기록"
      data-testid="user-activity-panel"
    >
      {error ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "접근 기록을 불러오지 못했습니다."
          }
          onRetry={() => setRefresh((n) => n + 1)}
        />
      ) : (
        <DataTable
          rows={data?.items ?? []}
          rowKey={(row) => row.logId}
          loading={loading}
          page={page}
          size={size}
          total={data?.total ?? 0}
          onPageChange={setPage}
          onSizeChange={(value) => {
            setSize(value);
            setPage(1);
          }}
          empty="접근 기록이 없습니다."
          columns={[
            { id: "actedAt", label: "일시", render: (row) => row.actedAt },
            {
              id: "actionType",
              label: "작업",
              render: (row) => row.actionType,
            },
            {
              id: "screenId",
              label: "화면",
              render: (row) => row.screenId || "–",
            },
            {
              id: "targetDescription",
              label: "대상",
              render: (row) => row.targetDescription || "–",
            },
          ]}
        />
      )}
    </section>
  );
}

export function UserScopesPanel({ id }: { id: string }) {
  const [data, setData] = useState<api.UserScopes>(),
    [error, setError] = useState<unknown>(),
    [refresh, setRefresh] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setData(undefined);
    setError(undefined);
    api
      .getBusinessUserScopes(id, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setData(value);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e);
      });
    return () => controller.abort();
  }, [id, refresh]);
  return (
    <section data-testid="user-current-scopes">
      <h3>현재 담당파트</h3>
      {error ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "담당파트를 불러오지 못했습니다."
          }
          onRetry={() => setRefresh((n) => n + 1)}
        />
      ) : !data ? (
        <p role="status">불러오는 중입니다.</p>
      ) : (
        <p>{data.assignedParts.map((part) => part.name).join(", ") || "–"}</p>
      )}
      <p>
        일반 파트장은 기준정보 › 거래처·영업 파트에서 변경합니다. 본사
        연결만으로 영업 파트장 자격이 부여되지 않습니다.
      </p>
    </section>
  );
}
