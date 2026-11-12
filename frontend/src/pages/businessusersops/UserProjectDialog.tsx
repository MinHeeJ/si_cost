import { useEffect, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { getPeriodContext, type PeriodContext } from "../shared/sharedApi";
import { getBusinessUserOptions } from "./businessUserOptionsApi";
import type { BusinessUser } from "../businessusers/businessUsersApi";
import * as api from "./userProjectsApi";

export type UserProjectChoice = {
  project: api.UserProjectListItem;
  pm: api.UserProjectPmData;
};
export function UserProjectDialog({
  user,
  pending,
  onClose,
  onAssign,
  onDirtyChange,
}: {
  user: BusinessUser;
  pending: boolean;
  onClose(): void;
  onAssign(choice: UserProjectChoice, confirmed: boolean): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [context, setContext] = useState<PeriodContext>();
  const [eligible, setEligible] = useState(false);
  const [year, setYear] = useState<number>();
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<"ING" | "PRE">("ING");
  const [filters, setFilters] = useState<api.UserProjectSearch>();
  const [data, setData] = useState<{
    items: UserProjectChoice[];
    total: number;
  }>();
  const [selected, setSelected] = useState<UserProjectChoice>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [refresh, setRefresh] = useState(0);
  const [replacing, setReplacing] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const dirty = Boolean(q || selected);
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);
    Promise.all([getPeriodContext(), getBusinessUserOptions("PM")])
      .then(([period, options]) => {
        if (!active) return;
        const currentYear = Number(period.currentDate.slice(0, 4));
        setContext(period);
        setYear(currentYear);
        setEligible(
          user.status === "USE" &&
            options.items.some((item) => item.userId === user.userId),
        );
        setFilters({ year: currentYear, status: "ING", page: 1 });
      })
      .catch((e) => {
        if (active) {
          setError(e);
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [user.userId, refresh]);
  useEffect(() => {
    if (!filters) return;
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setSelected(undefined);
    api
      .searchUserProjects(filters, controller.signal)
      .then(async (page) => {
        const items = await Promise.all(
          page.items
            .filter(
              (project) => project.status === "ING" || project.status === "PRE",
            )
            .map(async (project) => ({
              project,
              pm: await api.getUserProjectPm(project.id, controller.signal),
            })),
        );
        if (!controller.signal.aborted) setData({ items, total: page.total });
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [filters]);
  function close() {
    if (dirty) setLeaving(true);
    else onClose();
  }
  async function submit() {
    if (!selected || !eligible || pending || loading || error) return;
    if (selected.pm.items.some((pm) => pm.userId !== user.userId)) {
      setReplacing(true);
      return;
    }
    await onAssign(selected, false);
  }
  const columns: Column<UserProjectChoice>[] = [
    { id: "id", label: "프로젝트ID", render: ({ project }) => project.id },
    { id: "name", label: "이름", render: ({ project }) => project.name },
    {
      id: "status",
      label: "유효 상태",
      render: ({ project }) => (
        <StatusBadge tone={project.status === "ING" ? "ok" : "info"}>
          {project.status === "ING" ? "진행" : "수주예정"}
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
      id: "select",
      label: "선택",
      render: (choice) => (
        <button
          type="button"
          data-testid={`user-project-select-${choice.project.id}`}
          disabled={pending || !eligible}
          aria-pressed={selected?.project.id === choice.project.id}
          onClick={() => setSelected(choice)}
        >
          선택
        </button>
      ),
    },
  ];
  return (
    <>
      <Dialog
        open={!replacing && !leaving}
        wide
        title="담당 프로젝트 추가"
        pending={pending}
        onCancel={close}
        onConfirm={submit}
        confirmLabel="지정"
        confirmDisabled={!selected || !eligible || loading || Boolean(error)}
      >
        <div
          className="user-project-dialog"
          data-testid="user-project-add-dialog"
        >
          <p>
            대상 사용자 {user.name} ({user.userId}) ·{" "}
            {user.status === "USE" ? "사용" : "사용 불가"} · PM 자격{" "}
            {eligible ? "있음" : "확인 필요"}
          </p>
          {!loading && !eligible && (
            <p className="sicost-banner sicost-tone-warn">
              사용 중인 PM 자격 사용자만 지정할 수 있습니다. 기존 연결 해제는
              가능합니다.
            </p>
          )}
          <div className="user-project-search">
            <label className="sicost-field">
              조회 연도
              <select
                data-testid="user-project-year"
                value={year ?? ""}
                disabled={pending || !context}
                onChange={(e) => setYear(Number(e.target.value))}
              >
                {context &&
                  Array.from(
                    {
                      length:
                        Number(context.currentDate.slice(0, 4)) +
                        2 -
                        Number(context.cashBaseMonth.slice(0, 4)),
                    },
                    (_, i) => Number(context.cashBaseMonth.slice(0, 4)) + i,
                  ).map((value) => (
                    <option
                      data-testid={`user-project-year-option-${value}`}
                      key={value}
                      value={value}
                    >
                      {value}년
                    </option>
                  ))}
              </select>
            </label>
            <label className="sicost-field">
              상태
              <select
                data-testid="user-project-status"
                value={status}
                disabled={pending}
                onChange={(e) => setStatus(e.target.value as "ING" | "PRE")}
              >
                <option value="ING">진행</option>
                <option value="PRE">수주예정</option>
              </select>
            </label>
            <label className="sicost-field">
              프로젝트 검색
              <input
                data-testid="user-project-search"
                value={q}
                maxLength={100}
                disabled={pending}
                onChange={(e) => setQ(e.target.value)}
              />
            </label>
            <button
              type="button"
              data-testid="user-project-query"
              disabled={pending || loading || year === undefined}
              onClick={() =>
                year !== undefined && setFilters({ year, status, q, page: 1 })
              }
            >
              조회
            </button>
          </div>
          {error != null ? (
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "프로젝트를 불러오지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={() => {
                if (context && filters) setFilters({ ...filters });
                else setRefresh((v) => v + 1);
              }}
            />
          ) : (
            <DataTable
              rows={data?.items ?? []}
              columns={columns}
              rowKey={({ project }) => project.id}
              loading={loading}
              empty="선택할 수 있는 프로젝트가 없습니다."
              page={filters?.page ?? 1}
              size={20}
              total={data?.total ?? 0}
              onPageChange={(page) =>
                filters && setFilters({ ...filters, page })
              }
            />
          )}
          {selected && (
            <p>
              선택 {selected.project.id} · {selected.project.name}
            </p>
          )}
        </div>
      </Dialog>
      <Dialog
        open={replacing}
        title="PM 교체 확인"
        pending={pending}
        onCancel={() => setReplacing(false)}
        onConfirm={async () => {
          if (selected) await onAssign(selected, true);
        }}
        confirmLabel="교체 확인"
      >
        <div data-testid="user-project-replace-dialog">
          <p>
            프로젝트 {selected?.project.id} · {selected?.project.name}
          </p>
          <p>
            기존 PM 전체{" "}
            {selected?.pm.items
              .map((pm) => `${pm.name} (${pm.userId})`)
              .join(", ")}{" "}
            → 새 PM {user.name} ({user.userId})
          </p>
          <p>
            이 프로젝트 PROJ 연결만 교체합니다. 다른 프로젝트/PART/역할은
            보존합니다.
          </p>
        </div>
      </Dialog>
      <Dialog
        open={leaving}
        title="작성 취소"
        pending={pending}
        onCancel={() => setLeaving(false)}
        cancelLabel="계속 작성"
        confirmLabel="나가기"
        onConfirm={onClose}
      >
        <p data-testid="user-project-leave-dialog">
          저장하지 않은 내용이 있습니다.
        </p>
      </Dialog>
    </>
  );
}
