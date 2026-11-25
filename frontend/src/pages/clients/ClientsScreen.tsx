import { ScreenLoading } from "../../shared/ScreenLoading";
import { TableControlIcon } from "../../shared/Icon";
import { usePermission, useSensitivePermission } from "../../shared/permission/PermissionBoundary";
import { FieldHelp } from "../../shared/FieldHelp";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { FilterBar } from "../../shared/FilterBar";
import { SegmentedTabs } from "../../shared/SegmentedTabs";
import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { useClientWriteScope } from "./clientPermissions";
import { ClientForm } from "./ClientForm";
import { ClientDrawer } from "./ClientDrawer";
import { Dialog } from "../../shared/Dialog";
import { ClientStatusToolbar } from "../clientsops/ClientStatusToolbar";
import { useClientSelection } from "../clientsops/useClientSelection";
import * as api from "./clientsApi";
import type {
  Client,
  ClientCode,
  ClientFilters,
  ClientsPage,
  ClientSaveResult,
} from "./clientsApi";
import "./clients.css";

export type ClientsScreenProps = {
  active?: boolean;
  panelDirty?: boolean;
  dense?: boolean;
  columnView?: { order: string[]; hidden: string[] };
  pageSize?: 20 | 50 | 100;
  onColumnsChange?(): void;
  onDensityChange?(): void;
  onDirtyChange?(dirty: boolean): void;
  onTemplate?(): void;
  onUpload?(): void;
  onExport?(filters: ClientFilters): void;
  renderParts?: ReactNode;
};
export function ClientsScreen({
  active = true,
  panelDirty = false,
  dense = false,
  columnView,
  pageSize = 50,
  onColumnsChange,
  onDensityChange,
  onDirtyChange,
  onTemplate,
  onUpload,
  onExport,
  renderParts,
}: ClientsScreenProps) {
  const { managePartners, manageCustomers } = useClientWriteScope();
  const registrationPermission = usePermission("UI-15", "REG");
  const updatePermission = usePermission("UI-15", "UPD");
  const uploadPermission = usePermission("UI-15", "UPLD");
  const canDownload = usePermission("UI-15", "DNLD");
  const sensitive = useSensitivePermission();
  const canRegister = registrationPermission && manageCustomers;
  const [mobile, setMobile] = useState(
    () =>
      typeof window.matchMedia === "function" &&
      window.matchMedia("(max-width: 767px)").matches,
  );
  const [filters, setFilters] = useState<ClientFilters>({
    useYn: "Y",
    page: 1,
    size: mobile ? 20 : pageSize,
    sort: "name",
    direction: "ASC",
  });
  const canBulkUpdate =
    updatePermission &&
    (managePartners || (manageCustomers && filters.type === "CUST"));
  const [draft, setDraft] = useState({ type: "", useYn: "Y", q: "" });
  const [data, setData] = useState<ClientsPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const [types, setTypes] = useState<ClientCode[]>([]);
  const [creating, setCreating] = useState(false),
    [selected, setSelected] = useState<{
      id: string;
      tab: "basic" | "assignments";
    }>();
  const [notice, setNotice] = useState(""),
    [createdId, setCreatedId] = useState<string>(),
    [highlight, setHighlight] = useState<string>();
  const [workspaceTab, setWorkspaceTab] = useState("clients"),
    [refresh, setRefresh] = useState(0);
  const generation = useRef(0),
    dataRef = useRef<ClientsPage>();
  const selection = useClientSelection(filters, filters.size);
  const [nextTab, setNextTab] = useState<string>(),
    [panelVersion, setPanelVersion] = useState(0);
  const clientDirty = useRef(false);
  const reportDirty = useCallback(
    (value: boolean) => {
      clientDirty.current = value;
      onDirtyChange?.(value);
    },
    [onDirtyChange],
  );
  const changeTab = (tab: string) => {
    if (tab === workspaceTab) return;
    if (clientDirty.current || panelDirty) setNextTab(tab);
    else setWorkspaceTab(tab);
  };
  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const media = window.matchMedia("(max-width: 767px)");
    const change = () => {
      setMobile(media.matches);
      setFilters((previous) => ({
        ...previous,
        page: 1,
        size: media.matches ? 20 : pageSize,
      }));
    };
    media.addEventListener?.("change", change);
    return () => media.removeEventListener?.("change", change);
  }, [pageSize]);
  useEffect(() => {
    if (!mobile)
      setFilters((previous) => ({ ...previous, page: 1, size: pageSize }));
  }, [pageSize]);
  const load = useCallback(
    async (signal?: AbortSignal) => {
      const version = ++generation.current;
      setLoading(true);
      setError(undefined);
      try {
        const result = await api.getClients(filters, signal);
        if (!signal?.aborted && version === generation.current) {
          setData((previous) =>
            mobile && filters.page > 1
              ? {
                  ...result,
                  items: [...(previous?.items ?? []), ...result.items],
                }
              : result,
          );
          dataRef.current = result;
        }
        return result;
      } catch (e) {
        if (!signal?.aborted && version === generation.current) setError(e);
        throw e;
      } finally {
        if (!signal?.aborted && version === generation.current)
          setLoading(false);
      }
    },
    [filters, mobile],
  );
  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal).catch(() => {});
    return () => controller.abort();
  }, [load, refresh]);
  useEffect(() => {
    let active = true;
    void api
      .getClientCodes("CLNT_TYPE")
      .then((result) => {
        if (active) setTypes(result.items);
      })
      .catch(setError);
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    const recheck = () => {
      const date = new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Seoul",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(new Date());
      if (dataRef.current && date !== dataRef.current.basisDate)
        setRefresh((value) => value + 1);
    };
    const timer = window.setInterval(recheck, 30000);
    window.addEventListener("focus", recheck);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", recheck);
    };
  }, []);
  useEffect(() => {
    const reload = () => setRefresh((value) => value + 1);
    window.addEventListener(api.CLIENTS_COMMITTED, reload);
    return () => window.removeEventListener(api.CLIENTS_COMMITTED, reload);
  }, []);
  useEffect(() => {
    if (!highlight) return;
    const timer = window.setTimeout(() => setHighlight(undefined), 2500);
    return () => window.clearTimeout(timer);
  }, [highlight]);
  const basisChanged = useCallback((date: string) => {
    if (dataRef.current && date !== dataRef.current.basisDate)
      setRefresh((value) => value + 1);
  }, []);
  function apply(reset = false) {
    if (reset) setDraft({ type: "", useYn: "Y", q: "" });
    setFilters((previous) => ({
      ...previous,
      page: 1,
      type: reset
        ? undefined
        : ((draft.type || undefined) as ClientFilters["type"]),
      useYn: reset
        ? "Y"
        : ((draft.useYn || undefined) as ClientFilters["useYn"]),
      q: reset ? undefined : draft.q.trim() || undefined,
    }));
  }
  async function saved(result?: ClientSaveResult) {
    setCreating(false);
    if (result) {
      setNotice(
        [
          result.message,
          ...result.warnings.map((warning) => warning.message),
        ].join(" "),
      );
      setCreatedId(result.id);
    }
    try {
      const page = await load();
      if (result && page.items.some((row) => row.id === result.id))
        setHighlight(result.id);
    } catch {
      setNotice("저장됐지만 다시 불러오지 못했습니다.");
    }
    if (result?.changed) api.notifyClientsCommitted(result.id);
  }
  const open = (row: Client, tab: "basic" | "assignments" = "basic") =>
    setSelected({ id: row.id, tab });
  const typeName = (type: string) =>
    types.find((item) => item.code === type)?.name ?? type;
  const columns: Column<Client>[] = [
    {
      id: "type",
      label: "구분",
      render: (row) => (
        <StatusBadge tone={row.type === "CUST" ? "info" : "neutral"}>
          {typeName(row.type)}
        </StatusBadge>
      ),
    },
    {
      id: "name",
      label: "상호",
      render: (row) => (
        <button
          type="button"
          className={
            highlight === row.id
              ? "clients-row-highlight clients-link"
              : "clients-link"
          }
          data-testid={`client-${row.id}-open`}
          onClick={(event) => {
            event.stopPropagation();
            open(row);
          }}
        >
          {row.name}
        </button>
      ),
    },
    ...(
      [
        ["businessNo", "사업자번호"],
        ["ceo", "대표자"],
        ["industry", "업종"],
        ["contactName", "담당자"],
        ["phone", "연락처"],
        ["taxEmail", "세금계산서 이메일"],
        ["account", "지급 계좌"],
      ] as const
    ).map(
      ([id, label]): Column<Client> => ({
        id,
        label,
        render: (row) => row[id] ?? "–",
      }),
    ),
    {
      id: "projectCount",
      label: "진행 프로젝트",
      align: "right",
      render: (row) => (
        <button
          type="button"
          className="clients-link"
          data-testid={`client-${row.id}-projects`}
          onClick={(event) => {
            event.stopPropagation();
            open(row, "assignments");
          }}
        >
          {row.projectCount}
        </button>
      ),
    },
    {
      id: "personCount",
      label: "투입 인력",
      align: "right",
      render: (row) => (
        <button
          type="button"
          className="clients-link"
          data-testid={`client-${row.id}-people`}
          onClick={(event) => {
            event.stopPropagation();
            open(row, "assignments");
          }}
        >
          {row.personCount}
        </button>
      ),
    },
    {
      id: "useYn",
      label: "사용 여부",
      render: (row) => (
        <StatusBadge tone={row.useYn === "Y" ? "ok" : "lock"}>
          {row.useYn === "Y" ? "사용" : "중지"}
        </StatusBadge>
      ),
    },
  ];
  const sortableColumns = columns
    .filter((column) => sensitive || column.id !== "account")
    .map((column) =>
      column.id === "account" ? column : { ...column, sortable: true },
    );
  const visible = columnView
    ? sortableColumns
        .filter(
          (column) =>
            column.id === "name" || !columnView.hidden.includes(column.id),
        )
        .sort(
          (a, b) =>
            columnView.order.indexOf(a.id) - columnView.order.indexOf(b.id),
        )
    : [...sortableColumns];
  return (
    <section
      className="clients-screen"
      data-screen-id="UI-15"
      data-testid="clients-screen"
    >
      <div hidden={creating}>
        <SegmentedTabs
          label="기준정보 탭"
          testId="clients-workspace-tabs"
          value={workspaceTab}
          onChange={(key) => changeTab(key as "clients" | "parts")}
          tabs={[
            {
              key: "clients",
              label: "거래처",
              testId: "clients-workspace-tab",
            },
            ...(renderParts
              ? [
                  {
                    key: "parts",
                    label: "영업 파트",
                    testId: "parts-workspace-tab",
                  },
                ]
              : []),
          ]}
        />
        <div
          hidden={workspaceTab !== "clients"}
          role="tabpanel"
          data-testid="clients-workspace-panel"
        >
          <ScreenTitleActions active={workspaceTab === "clients" && !creating}>
            <div className="clients-toolbar">
              <h2 className="sicost-sr-only">거래처</h2>
              <div className="clients-actions">
                {!mobile && canDownload && onTemplate && (
                  <button
                    type="button"
                    data-testid="clients-template"
                    onClick={onTemplate}
                  >
                    양식 내려받기
                  </button>
                )}
                {!mobile && uploadPermission && managePartners && onUpload && (
                  <button
                    type="button"
                    data-testid="clients-upload"
                    onClick={onUpload}
                  >
                    엑셀 업로드
                  </button>
                )}
                {canRegister && (
                  <button
                    type="button"
                    className="sicost-primary"
                    data-testid="clients-register"
                    onClick={() => setCreating(true)}
                  >
                    등록
                  </button>
                )}
              </div>
            </div>
          </ScreenTitleActions>
          <FilterBar
            testId="clients-filter-form"
            className="clients-filter"
            resetTestId="clients-reset"
            searchTestId="clients-search-submit"
            onSubmit={() => apply()}
            onReset={() => apply(true)}
          >
            <label className="sicost-field">
              구분
              <FieldHelp label="구분" /><select
                data-testid="clients-filter-type"
                value={draft.type}
                onChange={(event) =>
                  setDraft((previous) => ({
                    ...previous,
                    type: event.target.value,
                  }))
                }
              >
                <option value="">전체</option>
                {types.map((type) => (
                  <option
                    data-testid={`clients-filter-type-option-${type.code.toLowerCase().replace(/_/g, "-")}`}
                    key={type.code}
                    value={type.code}
                  >
                    {type.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="sicost-field">
              사용 여부
              <select
                data-testid="clients-filter-use"
                value={draft.useYn}
                onChange={(event) =>
                  setDraft((previous) => ({
                    ...previous,
                    useYn: event.target.value,
                  }))
                }
              >
                <option value="">전체</option>
                <option value="Y">사용</option>
                <option value="N">중지</option>
              </select>
            </label>
            <label className="sicost-field clients-search">
              상호
              <input
                data-testid="clients-search"
                value={draft.q}
                maxLength={100}
                placeholder="상호 또는 사업자번호"
                onChange={(event) =>
                  setDraft((previous) => ({
                    ...previous,
                    q: event.target.value,
                  }))
                }
              />
            </label>
          </FilterBar>
          {notice && (
            <p role="status" className="sicost-banner sicost-tone-info">
              {notice}
              {createdId && (
                <button
                  type="button"
                  data-testid="client-created-view"
                  onClick={() => setSelected({ id: createdId, tab: "basic" })}
                >
                  등록 거래처 보기
                </button>
              )}
              {notice.includes("다시 불러오지") && (
                <button
                  type="button"
                  data-testid="clients-reload-saved"
                  onClick={() => void load().catch(() => {})}
                >
                  다시 조회
                </button>
              )}
            </p>
          )}
          <div className="clients-toolbar">
            {!mobile && canBulkUpdate && selection.count > 0 ? (
              <ClientStatusToolbar
                selection={selection}
                rows={data?.items ?? []}
                total={data?.total ?? 0}
                onCommitted={async () => {
                  await load();
                }}
                onRefresh={async () => {
                  await load();
                }}
              />
            ) : (
              <span>
                총 {data?.total ?? 0}건 · 기준일 {data?.basisDate ?? "–"}
              </span>
            )}
            <div className="clients-actions">
              {onColumnsChange && (
                <button
                  type="button"
                  data-testid="clients-columns"
                  onClick={onColumnsChange}
                >
                  <TableControlIcon name="columns" />
                  열 설정
                </button>
              )}
              {onDensityChange && (
                <button
                  type="button"
                  data-testid="clients-density"
                  onClick={onDensityChange}
                >
                  <TableControlIcon name="rows" />
                  목록 밀도
                </button>
              )}
              {!mobile && canDownload && onExport && (
                <button
                  className="sicost-download-button"
                  type="button"
                  data-testid="clients-export"
                  onClick={() => onExport(filters)}
                >
                  엑셀 다운로드
                </button>
              )}
            </div>
          </div>
          {error != null ? (
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "목록을 불러오지 못했습니다."
              }
              onRetry={() => void load().catch(() => {})}
            />
          ) : mobile ? (
            <div className="clients-cards">
              {loading && <ScreenLoading message="불러오는 중입니다." />}
              {data?.items.map((row) => (
                <article key={row.id} data-testid={`client-card-${row.id}`}>
                  <button
                    type="button"
                    data-testid={`client-card-open-${row.id}`}
                    onClick={() => open(row)}
                  >
                    {row.name}
                  </button>
                  <p>
                    {typeName(row.type)} · {row.businessNo}
                  </p>
                  <p>
                    진행 프로젝트 {row.projectCount} · 투입 인력{" "}
                    {row.personCount}
                  </p>
                </article>
              ))}
              {!loading && !data?.items.length && (
                <p>조회된 거래처가 없습니다.</p>
              )}
              {(data?.items.length ?? 0) < (data?.total ?? 0) && (
                <button
                  type="button"
                  data-testid="clients-load-more"
                  disabled={loading}
                  onClick={() =>
                    setFilters((previous) => ({
                      ...previous,
                      page: previous.page + 1,
                    }))
                  }
                >
                  더 보기
                </button>
              )}
            </div>
          ) : (
            <DataTable
              rows={data?.items ?? []}
              columns={visible}
              rowKey={(row) => row.id}
              onRowAction={(row) => open(row)}
              loading={loading}
              dense={dense}
              empty="조회된 거래처가 없습니다."
              sort={{
                id: filters.sort ?? "name",
                direction: filters.direction === "DESC" ? "DESC" : "ASC",
              }}
              onSortChange={(id) =>
                setFilters((previous) => ({
                  ...previous,
                  page: 1,
                  sort: id as ClientFilters["sort"],
                  direction:
                    previous.sort === id && previous.direction === "ASC"
                      ? "DESC"
                      : "ASC",
                }))
              }
              emptyAction={
                <button
                  type="button"
                  data-testid="clients-empty-reset"
                  onClick={() => apply(true)}
                >
                  조건 초기화
                </button>
              }
              selectedKeys={[...selection.items.keys()]}
              onSelectionChange={canBulkUpdate ? (keys) => {
                const after = new Set(keys.map(String));
                (data?.items ?? []).forEach((row) => {
                  if (after.has(row.id) !== selection.items.has(row.id))
                    selection.toggle(row, after.has(row.id));
                });
              } : undefined}
              page={filters.page}
              size={filters.size}
              total={data?.total ?? 0}
              onPageChange={(page) =>
                setFilters((previous) => ({ ...previous, page }))
              }
              onSizeChange={(size) =>
                setFilters((previous) => ({ ...previous, page: 1, size }))
              }
            />
          )}
        </div>
        {renderParts && (
          <div
            key={panelVersion}
            hidden={workspaceTab !== "parts"}
            role="tabpanel"
            data-testid="parts-workspace-panel"
          >
            {renderParts}
          </div>
        )}
      </div>
      {canRegister && creating && (
        <ClientForm
          onClose={() => {
            setCreating(false);
            reportDirty(false);
          }}
          onSaved={saved}
          onDirtyChange={reportDirty}
        />
      )}
      {selected && (
        <ClientDrawer
          active={active && workspaceTab === "clients"}
          id={selected.id}
          initialTab={selected.tab}
          onClose={() => {
            setSelected(undefined);
            reportDirty(false);
          }}
          onSaved={saved}
          onBasisChanged={basisChanged}
          onDirtyChange={reportDirty}
        />
      )}
      <Dialog
        open={nextTab !== undefined}
        title="저장하지 않은 내용이 있습니다."
        cancelLabel="계속 작성"
        confirmLabel="나가기"
        onCancel={() => setNextTab(undefined)}
        onConfirm={() => {
          setSelected(undefined);
          setCreating(false);
          reportDirty(false);
          setPanelVersion((value) => value + 1);
          setWorkspaceTab(nextTab!);
          setNextTab(undefined);
        }}
      >
        변경 사항을 저장하지 않고 이동합니다.
      </Dialog>
    </section>
  );
}
