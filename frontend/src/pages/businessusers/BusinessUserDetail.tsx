import { useEffect, useState } from "react";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import * as api from "./businessUsersApi";

function BusinessUserHistory({ id }: { id: string }) {
  const [data, setData] = useState<api.BusinessUserHistoryPage>(),
    [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>(),
    [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);
    api
      .getBusinessUserHistory(id, page)
      .then((value) => {
        if (active) setData(value);
      })
      .catch((e) => {
        if (active) setError(e);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, page, refresh]);
  const columns: Column<api.BusinessUserHistory>[] = [
    { id: "createdAt", label: "일시", render: (row) => row.createdAt },
    { id: "changeType", label: "유형", render: (row) => row.changeType },
    { id: "itemName", label: "항목", render: (row) => row.itemName },
    {
      id: "beforeValue",
      label: "이전값",
      render: (row) => row.beforeValue ?? "–",
    },
    {
      id: "afterValue",
      label: "이후값",
      render: (row) => row.afterValue ?? "–",
    },
    { id: "actorName", label: "처리자", render: (row) => row.actorName },
  ];
  return (
    <div role="tabpanel" data-testid="business-user-history-panel">
      {error != null ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "변경 이력을 불러오지 못했습니다."
          }
          onRetry={() => setRefresh((v) => v + 1)}
        />
      ) : (
        <DataTable
          rows={data?.items ?? []}
          columns={columns}
          rowKey={(row) => row.historyId}
          loading={loading}
          page={page}
          size={50}
          total={data?.total ?? 0}
          onPageChange={setPage}
          empty="변경 이력이 없습니다."
        />
      )}
    </div>
  );
}
export function BusinessUserDetail({
  id,
  onClose,
  onEdit,
}: {
  id: string;
  onClose(): void;
  onEdit(user: api.BusinessUser): void;
}) {
  const [user, setUser] = useState<api.BusinessUser>(),
    [error, setError] = useState<unknown>(),
    [refresh, setRefresh] = useState(0);
  const [tab, setTab] = useState<"basic" | "history">("basic");
  useEffect(() => {
    let active = true;
    setUser(undefined);
    setError(undefined);
    api
      .getBusinessUser(id)
      .then((value) => {
        if (active) setUser(value);
      })
      .catch((e) => {
        if (active) setError(e);
      });
    return () => {
      active = false;
    };
  }, [id, refresh]);
  return (
    <DetailDrawer
      label="사용자 상세"
      title={user?.name ?? "사용자 상세"}
      subtitle={user?.userId}
      testId="business-user-drawer"
      closeTestId="business-user-drawer-close"
      onClose={onClose}
      actions={
        user ? (
          <button
            type="button"
            className="sicost-primary"
            data-testid="business-user-edit"
            onClick={() => onEdit(user)}
          >
            수정
          </button>
        ) : error != null ? (
          <button
            type="button"
            data-testid="business-user-retry"
            onClick={() => setRefresh((v) => v + 1)}
          >
            다시 조회
          </button>
        ) : null
      }
    >
      <div className="businessusers-detail" data-testid="business-user-detail">
        <div
          role="tablist"
          aria-label="사용자 상세 탭"
          className="businessusers-tabs"
        >
          <button
            type="button"
            role="tab"
            aria-selected={tab === "basic"}
            data-testid="business-user-basic-tab"
            onClick={() => setTab("basic")}
          >
            기본정보
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === "history"}
            data-testid="business-user-history-tab"
            onClick={() => setTab("history")}
          >
            변경이력
          </button>
        </div>
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "사용자를 불러오지 못했습니다."
            }
            onRetry={() => setRefresh((v) => v + 1)}
          />
        )}
        {!error && !user && <p role="status">불러오는 중입니다.</p>}
        {user && tab === "basic" && (
          <dl
            role="tabpanel"
            className="businessusers-info"
            data-testid="business-user-basic-panel"
          >
            <dt>아이디</dt>
            <dd>{user.userId}</dd>
            <dt>이름</dt>
            <dd>{user.name}</dd>
            <dt>이메일</dt>
            <dd>{user.email}</dd>
            <dt>표시 역할</dt>
            <dd>
              {user.roles.map((role) => (
                <span
                  data-testid={`business-user-detail-role-${role.toLowerCase().replace(/_/g, "-")}`}
                  key={role}
                  className="sicost-badge sicost-tone-info"
                >
                  {role}
                </span>
              ))}
            </dd>
            <dt>편집 업무 역할</dt>
            <dd>{user.businessRoleIds.join(", ") || "–"}</dd>
            <dt>상태</dt>
            <dd>
              {user.status === "USE"
                ? "사용"
                : user.status === "LOCK"
                  ? "잠김"
                  : "중지"}
            </dd>
            <dt>마지막 접속</dt>
            <dd>{user.lastLoginAt ?? "–"}</dd>
          </dl>
        )}
        {user && tab === "history" && <BusinessUserHistory id={user.userId} />}
      </div>
    </DetailDrawer>
  );
}
