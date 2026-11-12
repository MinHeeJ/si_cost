import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge, type Tone } from "../../shared/Feedback";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import type { BusinessUser } from "../businessusers/businessUsersApi";
import { notifyBusinessUsersCommitted } from "./businessUserOptionsApi";
import { UserProjectDialog, type UserProjectChoice } from "./UserProjectDialog";
import * as api from "./userProjectsApi";
import "./userProjects.css";

const labels: Record<
  api.UserProjectListItem["status"],
  { label: string; tone: Tone }
> = {
  ING: { label: "진행", tone: "ok" },
  PRE: { label: "수주예정", tone: "info" },
  END: { label: "종료", tone: "lock" },
  LOSE: { label: "실주", tone: "bad" },
};
export function UserProjectsPanel({
  user,
  onDirtyChange,
}: {
  user: BusinessUser;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [rows, setRows] = useState<UserProjectChoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [adding, setAdding] = useState(false);
  const sequence = useRef(0);
  const { commit, pending, message } = useCommitRefresh();
  const load = useCallback(
    async (signal?: AbortSignal) => {
      const request = ++sequence.current;
      setLoading(true);
      setError(undefined);
      try {
        const data = await api.getBusinessUserProjects(user.userId, signal);
        const next = await Promise.all(
          data.items.map(async (project) => ({
            project,
            pm: await api.getUserProjectPm(project.id, signal),
          })),
        );
        if (!signal?.aborted && request === sequence.current) setRows(next);
      } catch (e) {
        if (!signal?.aborted && request === sequence.current) setError(e);
        throw e;
      } finally {
        if (!signal?.aborted && request === sequence.current) setLoading(false);
      }
    },
    [user.userId],
  );
  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal).catch(() => {});
    return () => controller.abort();
  }, [load]);
  useEffect(() => {
    const refresh = () => {
      void load().catch(() => {});
    };
    window.addEventListener(api.userProjectsCommittedEvent, refresh);
    return () =>
      window.removeEventListener(api.userProjectsCommittedEvent, refresh);
  }, [load]);
  async function save(
    action: () => Promise<api.UserProjectPmResult>,
    close = false,
  ) {
    try {
      await commit(
        async () => {
          await action();
          if (close) setAdding(false);
          onDirtyChange?.(false);
          notifyBusinessUsersCommitted();
          window.dispatchEvent(new CustomEvent(api.userProjectsCommittedEvent));
        },
        () => load(),
      );
    } catch (e) {
      setError(e);
      if (e instanceof ApiError)
        throw new Error(
          `${e.message}${e.body.field ? ` (${e.body.field})` : ""} · 문의 번호: ${e.body.requestId}`,
        );
      throw e;
    }
  }
  const columns: Column<UserProjectChoice>[] = [
    {
      id: "id",
      label: "프로젝트ID",
      render: ({ project }) => (
        <span data-testid={`user-project-row-${project.id}`}>{project.id}</span>
      ),
    },
    { id: "name", label: "이름", render: ({ project }) => project.name },
    {
      id: "status",
      label: "유효 상태",
      align: "center",
      render: ({ project }) => (
        <StatusBadge tone={labels[project.status].tone}>
          {labels[project.status].label}
        </StatusBadge>
      ),
    },
    {
      id: "pm",
      label: "기존 PM 전체",
      render: ({ pm }) =>
        pm.items.map((item) => `${item.name} (${item.userId})`).join(", ") ||
        "–",
    },
    {
      id: "detach",
      label: "작업",
      render: (row) => (
        <button
          type="button"
          data-testid={`user-project-detach-${row.project.id}`}
          disabled={pending || loading}
          onClick={() => {
            void save(() =>
              api.detachUserProject(
                row.project.id,
                user.userId,
                row.pm.revision,
              ),
            ).catch(() => {});
          }}
        >
          해제
        </button>
      ),
    },
  ];
  return (
    <section
      className="user-projects-panel"
      data-testid="user-projects-panel"
      aria-label="담당 프로젝트"
    >
      <div className="businessusers-toolbar">
        <h3>담당 프로젝트</h3>
        <button
          type="button"
          data-testid="user-project-add"
          disabled={
            pending ||
            adding ||
            user.status !== "USE" ||
            !user.businessRoleIds.includes("PM")
          }
          onClick={() => setAdding(true)}
        >
          담당 프로젝트 추가
        </button>
      </div>
      <p className="user-projects-help">
        한 사용자는 여러 프로젝트를 담당할 수 있으며 프로젝트 PM은 0~1명입니다.
        기존 여러 PM은 모두 표시하며 명시적 교체 때만 정리합니다.
      </p>
      {(user.status !== "USE" || !user.businessRoleIds.includes("PM")) && (
        <p className="sicost-banner sicost-tone-warn">
          사용 중인 PM 자격 사용자만 지정할 수 있습니다. 기존 연결 해제는
          가능합니다.
        </p>
      )}
      {message && (
        <p role="status" className="sicost-banner sicost-tone-info">
          {message}
        </p>
      )}
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "담당 프로젝트를 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          pending={pending}
          onRetry={() => {
            void load().catch(() => {});
          }}
        />
      )}
      {error == null && (
        <DataTable
          rows={rows}
          columns={columns}
          rowKey={({ project }) => project.id}
          loading={loading}
          empty="담당 프로젝트가 없습니다."
        />
      )}
      {adding && (
        <UserProjectDialog
          user={user}
          pending={pending}
          onClose={() => setAdding(false)}
          onDirtyChange={onDirtyChange}
          onAssign={(choice, confirmed) =>
            save(
              () =>
                api.assignUserProject(
                  choice.project.id,
                  user.userId,
                  choice.pm.revision,
                  confirmed,
                ),
              true,
            )
          }
        />
      )}
    </section>
  );
}
