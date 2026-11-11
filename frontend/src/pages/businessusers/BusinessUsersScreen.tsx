import { useEffect, useRef, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import { BusinessUserForm } from "./BusinessUserForm";
import { BusinessUserDetail } from "./BusinessUserDetail";
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
  const [draft, setDraft] = useState({ q: "", businessRole: "", status: "" });
  const [filters, setFilters] = useState<api.BusinessUserFilters>({
    page: 1,
    size: pageSize,
    sort: "name",
    direction: "ASC",
  });
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
  const requestSequence = useRef(0);
  const registrationKey = useRef<string>();
  useEffect(() => {
    if (registration && registration.key !== registrationKey.current) {
      registrationKey.current = registration.key;
      setForm({ registration });
    }
  }, [registration?.key]);
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
      businessRole: (draft.businessRole || undefined) as
        | api.BusinessRole
        | undefined,
      status: (draft.status || undefined) as
        | api.BusinessUser["status"]
        | undefined,
      page: 1,
    }));
  }
  function reset() {
    setDraft({ q: "", businessRole: "", status: "" });
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
  };
  const columns: Column<api.BusinessUser>[] = [
    "userId",
    "name",
    "email",
    "roles",
    "status",
    "lastLoginAt",
  ].map((id) => ({
    id,
    label:
      id === "roles" ? (
        labels[id]
      ) : (
        <button
          type="button"
          className="businessusers-sort"
          data-testid={`business-users-sort-${id.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
          onClick={() => sort(id)}
          aria-label={`${labels[id]} 정렬`}
        >
          {labels[id]}
          {filters.sort === id
            ? filters.direction === "ASC"
              ? " ↑"
              : " ↓"
            : ""}
        </button>
      ),
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
      ) : id === "lastLoginAt" ? (
        (row.lastLoginAt ?? "–")
      ) : id === "email" ? (
        row.email
      ) : (
        row.name
      ),
  }));
  const visible = columnView
    ? columnView.order.flatMap((id) => {
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
        <div className="businessusers-toolbar">
          <h2>사용자</h2>
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
        </div>
        <form
          data-testid="business-users-filter-form"
          className="businessusers-filter"
          onSubmit={(event) => {
            event.preventDefault();
            apply();
          }}
        >
          <label className="sicost-field">
            역할
            <select
              data-testid="business-users-role-filter"
              value={draft.businessRole}
              onChange={(e) =>
                setDraft({ ...draft, businessRole: e.target.value })
              }
            >
              <option value="">전체</option>
              <option value="PM">PM</option>
              <option value="SALES_LDR">SALES_LDR</option>
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
          <button
            type="button"
            data-testid="business-users-reset"
            onClick={reset}
          >
            초기화
          </button>
          <button
            type="submit"
            className="sicost-primary"
            data-testid="business-users-query"
          >
            조회
          </button>
        </form>
        <div className="businessusers-toolbar">
          <span>총 {data?.total ?? 0}건</span>
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
            columns={visible}
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
          />
        )}
        {!loading && !error && !data?.items.length && (
          <button data-testid="business-users-empty-reset" onClick={reset}>
            조건 초기화
          </button>
        )}
      </div>
      {detail && !form && (
        <BusinessUserDetail
          id={detail}
          onClose={() => setDetail(undefined)}
          onEdit={(user) => setForm({ user })}
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
            setNotice(result.message);
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
