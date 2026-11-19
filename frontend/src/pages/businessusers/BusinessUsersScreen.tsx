import { useEffect, useRef, useState } from "react";
import { FilterBar } from "../../shared/FilterBar";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import { BusinessUserForm } from "./BusinessUserForm";
import { usePermission } from "../../shared/permission/PermissionBoundary";
import { BusinessUserDetail } from "./BusinessUserDetail";
import { UserBulkStatusToolbar } from "../businessusersops/UserBulkStatusToolbar";
import * as api from "./businessUsersApi";
import "./businessusers.css";

export type BusinessUsersScreenProps = {
  registration?: { key: string; role: api.BusinessRole; onClose(): void };
  dense?: boolean;
  columnView?: { order: string[]; hidden: string[] };
  pageSize?: 20 | 50 | 100;
  onColumnsChange?(): void;
  onDensityChange?(): void;
  onPageSizeChange?(size: 20 | 50 | 100): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
  onCommitted?(): void | Promise<void>;
};
// SCR-UI-23: list, registration and readonly detail/history remain inside /UI-23.
export function BusinessUsersScreen({
  registration,
  dense = false,
  columnView,
  pageSize = 50,
  onColumnsChange,
  onDensityChange,
  onPageSizeChange,
  onDirtyChange,
  onCommitted,
}: BusinessUsersScreenProps) {
  const canRegister = usePermission("UI-23", "REG");
  const [draft, setDraft] = useState({
    q: "",
    role: "",
    status: "",
    includeStopped: false,
  });
  const [filters, setFilters] = useState<api.BusinessUserFilters>({
    page: 1,
    size: pageSize,
    sort: "name",
    direction: "ASC",
  });
  const [roleOptions, setRoleOptions] = useState<
    { roleId: string; name: string }[]
  >([]);
  const [roleError, setRoleError] = useState<unknown>();
  async function loadRoles() {
    setRoleError(undefined);
    try {
      setRoleOptions(await api.getManagedUserRoleOptions());
    } catch (e) {
      setRoleError(e);
    }
  }
  useEffect(() => {
    void loadRoles();
    const refresh = () => {
      void loadRoles();
    };
    window.addEventListener("sicost:configuration-committed", refresh);
    return () =>
      window.removeEventListener("sicost:configuration-committed", refresh);
  }, []);
  const [data, setData] = useState<api.BusinessUsersPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const [notice, setNotice] = useState(""),
    [detail, setDetail] = useState<string>(),
    [form, setForm] = useState<{
      user?: api.BusinessUser;
      registration?: BusinessUsersScreenProps["registration"];
    }>();
  const [refresh, setRefresh] = useState(0);
  const [selected, setSelected] = useState<Record<string, api.BusinessUser>>(
    {},
  );
  useEffect(() => setSelected({}), [filters]);
  const requestSequence = useRef(0);
  const registrationKey = useRef<string>();
  useEffect(() => {
    if (
      canRegister &&
      registration &&
      registration.key !== registrationKey.current
    ) {
      registrationKey.current = registration.key;
      setForm({ registration });
    }
  }, [registration?.key, canRegister]);
  async function load() {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setError(undefined);
    try {
      const result = await api.getBusinessUsers(filters);
      if (sequence === requestSequence.current) setData(result);
    } catch (e) {
      if (sequence === requestSequence.current) setError(e);
      throw e;
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }
  useEffect(() => {
    void load().catch(() => {});
    return () => {
      requestSequence.current++;
    };
  }, [filters, refresh]);
  useEffect(() => {
    setFilters((previous) =>
      previous.size === pageSize
        ? previous
        : { ...previous, page: 1, size: pageSize },
    );
  }, [pageSize]);
  function apply() {
    setFilters((previous) => ({
      ...previous,
      q: draft.q.trim() || undefined,
      role: draft.role || undefined,
      includeStopped: draft.includeStopped || undefined,
      status: (draft.status || undefined) as
        | api.BusinessUser["status"]
        | undefined,
      page: 1,
    }));
  }
  function reset() {
    setDraft({ q: "", role: "", status: "", includeStopped: false });
    setFilters({ page: 1, size: filters.size, sort: "name", direction: "ASC" });
  }
  function sort(id: string) {
    setFilters((previous) => ({
      ...previous,
      page: 1,
      sort: id,
      direction:
        previous.sort === id && previous.direction === "ASC" ? "DESC" : "ASC",
    }));
  }
  const labels: Record<string, string> = {
    userId: "아이디",
    name: "이름",
    email: "이메일",
    roles: "역할",
    status: "상태",
    lastLoginAt: "마지막 접속",
    assignedParts: "담당파트",
  };
  const columns: Column<api.BusinessUser>[] = [
    "userId",
    "name",
    "email",
    "roles",
    "assignedParts",
    "status",
    "lastLoginAt",
  ].map((id) => ({
    id,
    label: labels[id],
    sortable: id !== "roles" && id !== "assignedParts",
    render: (row) =>
      id === "userId" ? (
        <button
          className="businessusers-link"
          data-testid={`business-user-open-${row.userId}`}
          onClick={(event) => {
            event.stopPropagation();
            setDetail(row.userId);
          }}
        >
          {row.userId}
        </button>
      ) : id === "roles" ? (
        <>
          {row.roles.map((role) => (
            <span
              data-testid={`business-users-role-${row.userId}-${role.toLowerCase().replace(/_/g, "-")}`}
              key={role}
              className="sicost-badge sicost-tone-info"
            >
              {role}
            </span>
          ))}
        </>
      ) : id === "status" ? (
        <span
          className={`sicost-badge sicost-tone-${row.status === "USE" ? "ok" : row.status === "LOCK" ? "warn" : "lock"}`}
        >
          {row.status === "USE"
            ? "사용"
            : row.status === "LOCK"
              ? "잠김"
              : "중지"}
        </span>
      ) : id === "assignedParts" ? (
        row.assignedParts?.map((part) => part.name).join(", ") || "–"
      ) : id === "lastLoginAt" ? (
        (row.lastLoginAt ?? "–")
      ) : id === "email" ? (
        row.email
      ) : (
        row.name
      ),
  }));
  const visible = columnView
    ? [
        ...columnView.order,
        ...(!columnView.order.includes("assignedParts")
          ? ["assignedParts"]
          : []),
      ].flatMap((id) => {
        const column = columns.find((c) => c.id === id);
        return column &&
          (!columnView.hidden.includes(id) || id === "userId" || id === "name")
          ? [column]
          : [];
      })
    : columns;
  return (
    <section
      className="businessusers-screen"
      data-screen-id="UI-23"
      data-testid="business-users-screen"
    >
      <p className="businessusers-mobile">이 화면은 PC에서 사용해 주세요.</p>
      <div className="businessusers-desktop">
        <p className="sicost-banner sicost-tone-info">
          저장된 역할·메뉴 권한과 담당 범위를 적용합니다.
        </p>
        <div className="businessusers-toolbar">
          <h2>사용자</h2>
          {canRegister && (
            <button
              className="sicost-primary"
              data-testid="business-user-register"
              onClick={() => {
                setNotice("");
                setForm({});
              }}
            >
              등록
            </button>
          )}
        </div>
        {roleError != null && (
          <ErrorBanner
            message={
              roleError instanceof Error
                ? roleError.message
                : "역할을 불러오지 못했습니다."
            }
            onRetry={() => void loadRoles()}
          />
        )}
        <FilterBar
          testId="business-users-filter-form"
          resetTestId="business-users-reset"
          searchTestId="business-users-query"
          onSubmit={apply}
          onReset={reset}
        >
          <label className="sicost-field">
            역할
            <select
              data-testid="business-users-role-filter"
              value={draft.role}
              onChange={(e) => setDraft({ ...draft, role: e.target.value })}
            >
              <option value="">전체</option>
              {roleOptions.map((option) => (
                <option
                  data-testid={`business-users-role-option-${option.roleId.toLowerCase().replace(/_/g, "-")}`}
                  key={option.roleId}
                  value={option.roleId}
                >
                  {option.name}
                </option>
              ))}
            </select>
          </label>
          <label className="sicost-field">
            상태
            <select
              data-testid="business-users-status-filter"
              value={draft.status}
              onChange={(e) => setDraft({ ...draft, status: e.target.value })}
            >
              <option value="">전체</option>
              <option value="USE">사용</option>
              <option value="LOCK">잠김</option>
              <option value="STOP">중지</option>
            </select>
          </label>
          <label className="sicost-field">
            <input
              data-testid="business-users-include-stopped"
              type="checkbox"
              checked={draft.includeStopped}
              onChange={(e) =>
                setDraft({ ...draft, includeStopped: e.target.checked })
              }
            />
            사용 중지 포함
          </label>
          <label className="sicost-field businessusers-search">
            이름
            <input
              data-testid="business-users-search"
              placeholder="이름 또는 아이디"
              maxLength={100}
              value={draft.q}
              onChange={(e) => setDraft({ ...draft, q: e.target.value })}
            />
          </label>
        </FilterBar>
        <div className="businessusers-toolbar">
          {!Object.keys(selected).length && (
            <span>총 {data?.total ?? 0}건</span>
          )}
          <UserBulkStatusToolbar
            onClear={() => setSelected({})}
            onSelectAll={async () => {
              try {
                const all: Record<string, api.BusinessUser> = {};
                for (let page = 1; ; page++) {
                  const result = await api.getBusinessUsers({
                    ...filters,
                    page,
                    size: 100,
                  });
                  result.items.forEach((row) => {
                    all[row.userId] = row;
                  });
                  if (page * 100 >= result.total || !result.items.length) break;
                }
                setSelected(all);
              } catch (e) {
                setError(e);
              }
            }}
            selected={Object.values(selected)}
            onCommitted={async () => {
              await load();
              setSelected({});
              await onCommitted?.();
            }}
          />

          <div>
            {onColumnsChange && (
              <button
                data-testid="business-users-columns"
                onClick={onColumnsChange}
              >
                열 설정
              </button>
            )}
            {onDensityChange && (
              <button
                data-testid="business-users-density"
                onClick={onDensityChange}
              >
                {dense ? "넓게 보기" : "좁게 보기"}
              </button>
            )}
          </div>
        </div>
        {notice && (
          <p role="status" className="sicost-banner sicost-tone-info">
            {notice}
            {notice.includes("불러오지") && (
              <button
                data-testid="business-users-reload"
                onClick={() => {
                  setNotice("");
                  setRefresh((n) => n + 1);
                }}
              >
                다시 조회
              </button>
            )}
          </p>
        )}
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "사용자를 불러오지 못했습니다."
            }
            onRetry={() => setRefresh((n) => n + 1)}
          />
        )}
        {!error && (
          <DataTable
            rows={data?.items ?? []}
            columns={[
              {
                id: "selection",
                label: "선택",
                render: (row) => (
                  <input
                    type="checkbox"
                    aria-label={`${row.userId} 선택`}
                    data-testid={`business-user-select-${row.userId}`}
                    checked={!!selected[row.userId]}
                    onClick={(event) => event.stopPropagation()}
                    onChange={(event) =>
                      setSelected((previous) => {
                        const next = { ...previous };
                        if (event.target.checked) next[row.userId] = row;
                        else delete next[row.userId];
                        return next;
                      })
                    }
                  />
                ),
              },
              ...visible,
            ]}
            rowKey={(row) => row.userId}
            onRowAction={(row) => setDetail(row.userId)}
            dense={dense}
            loading={loading}
            page={filters.page ?? 1}
            size={filters.size ?? 50}
            total={data?.total ?? 0}
            onPageChange={(page) => setFilters({ ...filters, page })}
            onSizeChange={async (size) => {
              try {
                await onPageSizeChange?.(size);
                setFilters({ ...filters, size, page: 1 });
              } catch (e) {
                setError(e);
              }
            }}
            empty="조회된 사용자가 없습니다."
            sort={{
              id: filters.sort ?? "name",
              direction: filters.direction === "DESC" ? "DESC" : "ASC",
            }}
            onSortChange={sort}
            emptyAction={
              <button data-testid="business-users-empty-reset" onClick={reset}>
                조건 초기화
              </button>
            }
          />
        )}
      </div>
      {detail && !form && (
        <BusinessUserDetail
          id={detail}
          onDirtyChange={onDirtyChange}
          onClose={() => setDetail(undefined)}
          onEdit={(user) => setForm({ user })}
          onCommitted={async () => {
            await load();
            setSelected({});
            await onCommitted?.();
          }}
        />
      )}
      {form && (
        <BusinessUserForm
          user={form.user}
          initialRole={form.registration?.role}
          onDirtyChange={onDirtyChange}
          onClose={() => {
            setForm(undefined);
            form.registration?.onClose();
          }}
          onSaved={async (result) => {
            setForm(undefined);
            setDetail(result.user.userId);
            setNotice(
              form.user
                ? result.message
                : "등록했습니다. 로그인용 비밀번호 발급은 인증 기능 적용 시 제공합니다.",
            );
            api.notifyBusinessUsersCommitted();
            try {
              await Promise.all([load(), Promise.resolve(onCommitted?.())]);
            } catch {
              setError(undefined);
              setNotice("저장됐지만 다시 불러오지 못했습니다.");
            }
            form.registration?.onClose();
          }}
        />
      )}
    </section>
  );
}
