import { useEffect, useRef, useState } from "react";
import { FilterBar } from "../../shared/FilterBar";
import { DataTable, type Column } from "../../shared/DataTable";
import { Text } from "../../shared/Text";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatDecimal } from "../../shared/format";
import { ChangeHistoryTable } from "../apiops/ChangeHistoryTable";
import { SalesPartCreateDialog } from "./SalesPartCreateDialog";
import * as api from "./salesPartsApi";
import "./salesparts.css";

type Props = {
  dense?: boolean;
  pageSize?: 20 | 50 | 100;
  amountUnit?: "원" | "천원" | "백만원";
  onDirtyChange?(dirty: boolean): void;
  onCommitted?(): void | Promise<void>;
  onStandardChange?(detail: api.SalesPartDetail): void;
};
const initial: api.SalesPartQuery = {
  useYn: "Y",
  page: 1,
  size: 50,
  sort: "name",
  direction: "ASC",
};
export function SalesPartsScreen({
  dense = false,
  pageSize = 50,
  amountUnit = "천원",
  onDirtyChange,
  onCommitted,
  onStandardChange,
}: Props) {
  const [query, setQuery] = useState<api.SalesPartQuery>({
    ...initial,
    size: pageSize,
  });
  const [search, setSearch] = useState(""),
    [useYn, setUseYn] = useState("Y");
  const [data, setData] = useState<api.SalesPartsPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const [notice, setNotice] = useState(""),
    [creating, setCreating] = useState(false),
    [selected, setSelected] = useState<string>();
  const [detail, setDetail] = useState<api.SalesPartDetail>(),
    [detailLoading, setDetailLoading] = useState(false),
    [detailError, setDetailError] = useState<unknown>();
  const [refresh, setRefresh] = useState(0),
    [downloading, setDownloading] = useState(false);
  const [mobile, setMobile] = useState(
    () =>
      typeof matchMedia !== "undefined" &&
      matchMedia("(max-width: 767px)").matches,
  );
  const [mobileRows, setMobileRows] = useState<api.SalesPart[]>([]);
  const detailGeneration = useRef(0);
  useEffect(() => {
    const reload = () => {
      setRefresh((old) => old + 1);
      if (selected) void open(selected);
    };
    window.addEventListener(api.salesPartsCommittedEvent, reload);
    return () =>
      window.removeEventListener(api.salesPartsCommittedEvent, reload);
  }, [selected]);
  useEffect(() => {
    if (typeof matchMedia === "undefined") return;
    const media = matchMedia("(max-width: 767px)");
    const change = () => {
      setMobile(media.matches);
      setQuery((old) => ({ ...old, page: 1 }));
    };
    media.addEventListener("change", change);
    return () => media.removeEventListener("change", change);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    void api
      .getSalesParts(
        { ...query, size: mobile ? 20 : query.size },
        controller.signal,
      )
      .then((result) => {
        if (controller.signal.aborted) return;
        if (query.page > 1 && result.items.length === 0 && !mobile) {
          setQuery((old) => ({
            ...old,
            page: Math.max(1, Math.ceil(result.total / old.size)),
          }));
          return;
        }
        setData(result);
        setMobileRows((old) =>
          query.page === 1 ? result.items : [...old, ...result.items],
        );
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [query, refresh, mobile]);
  async function open(id: string) {
    const version = ++detailGeneration.current;
    setSelected(id);
    setDetail(undefined);
    setDetailLoading(true);
    setDetailError(undefined);
    try {
      const result = await api.getSalesPart(id);
      if (version === detailGeneration.current) setDetail(result);
    } catch (reason) {
      if (version === detailGeneration.current) setDetailError(reason);
    } finally {
      if (version === detailGeneration.current) setDetailLoading(false);
    }
  }
  useEffect(() => {
    if (window.location.pathname !== "/UI-15") return;
    const values = new URLSearchParams(window.location.search).getAll("partId");
    if (values.length === 1 && values[0].length > 0 && values[0].length <= 20)
      void open(values[0]);
  }, []);
  function closeDetail() {
    detailGeneration.current++;
    setSelected(undefined);
    setDetail(undefined);
  }
  async function committed(result: api.SalesPartSaveResult) {
    setNotice(result.message);
    setCreating(false);
    setDetail(result.part);
    if (mobile) setQuery((old) => ({ ...old, page: 1 }));
    setRefresh((old) => old + 1);
    if (result.changed) {
      api.notifySalesPartsCommitted();
      try {
        await onCommitted?.();
      } catch {
        setNotice("저장됐지만 다시 불러오지 못했습니다.");
      }
    }
  }
  function sort(key: string) {
    setQuery((old) => ({
      ...old,
      sort: key,
      direction: old.sort === key && old.direction === "ASC" ? "DESC" : "ASC",
      page: 1,
    }));
  }
  const labels = [
    "파트명",
    "파트장",
    "이익 배분율",
    `공통관리비(1인·월, ${amountUnit})`,
    "적용 시작",
    "예정된 변경",
    "소속 인력",
    "진행 프로젝트",
    "사용 여부",
  ];
  const ids = [
    "name",
    "leaderName",
    "profitRate",
    "salesExpenseAmount",
    "effectiveMonth",
    "scheduledChange",
    "personCount",
    "projectCount",
    "useYn",
  ];
  const amount = (value?: string | null) =>
    formatDecimal(api.salesPartAmount(value, amountUnit));
  const columns: Column<api.SalesPart>[] = ids.map((id, index) => ({
    id,
    label: labels[index],
    sortable: id !== "scheduledChange",
    render: (row) => {
      const current = row.currentStandard;
      switch (id) {
        case "name":
          return (
            <button
              data-testid={`sales-part-open-${row.id.toLowerCase()}`}
              className="sales-part-link"
              onClick={(event) => {
                event.stopPropagation();
                void open(row.id);
              }}
            >
              {row.name}
            </button>
          );
        case "leaderName":
          return current?.leaderName ?? "–";
        case "profitRate":
          return current ? `${formatDecimal(current.profitRate)}%` : "–";
        case "salesExpenseAmount":
          return amount(current?.salesExpenseAmount);
        case "effectiveMonth":
          return (
            current?.effectiveMonth ?? (
              <StatusBadge tone="info">적용 전</StatusBadge>
            )
          );
        case "scheduledChange":
          return row.scheduledChange ? (
            <span className="sales-part-scheduled">
              <StatusBadge tone="warn">예정</StatusBadge>{" "}
              {row.scheduledChange.effectiveMonth} ·{" "}
              {row.scheduledChange.leaderName} ·{" "}
              {formatDecimal(row.scheduledChange.profitRate)}% ·{" "}
              {amount(row.scheduledChange.salesExpenseAmount)}
            </span>
          ) : (
            "–"
          );
        case "personCount":
          return `${row.personCount}명`;
        case "projectCount":
          return `${row.projectCount}건`;
        default:
          return (
            <StatusBadge tone={row.useYn === "Y" ? "ok" : "lock"}>
              {row.useYn === "Y" ? "사용" : "중지"}
            </StatusBadge>
          );
      }
    },
    align: [
      "profitRate",
      "salesExpenseAmount",
      "personCount",
      "projectCount",
    ].includes(id)
      ? "right"
      : "left",
  }));
  function apply(reset = false) {
    setQuery((old) => ({
      ...old,
      q: reset ? undefined : search.trim() || undefined,
      useYn: reset ? "Y" : useYn === "" ? undefined : (useYn as "Y" | "N"),
      page: 1,
    }));
    if (reset) {
      setSearch("");
      setUseYn("Y");
    }
  }
  return (
    <section
      className="sales-parts-screen"
      data-screen-id="UI-15"
      data-testid="sales-parts-screen"
    >
      <div className="sales-part-toolbar">
        <h2>영업 파트</h2>
        <button
          data-testid="sales-part-add-button"
          className="sicost-primary"
          onClick={() => setCreating(true)}
        >
          파트 추가
        </button>
      </div>
      <p className="sicost-banner sicost-tone-info">
        기준은 적용 시작월부터 사용하며 지난 정산은 유지됩니다.
      </p>
      <FilterBar
        className="sales-part-filters"
        resetTestId="sales-part-reset-button"
        searchTestId="sales-part-search-button"
        onSubmit={() => apply()}
        onReset={() => apply(true)}
        extra={
          !mobile && (
            <button
              data-testid="sales-part-export-button"
              type="button"
              disabled={downloading}
              onClick={async () => {
                setDownloading(true);
                try {
                  await api.downloadSalesParts(query, amountUnit);
                } catch (reason) {
                  setError(reason);
                } finally {
                  setDownloading(false);
                }
              }}
            >
              엑셀 다운로드
            </button>
          )
        }
      >
        <Text
          label="파트명 검색"
          data-testid="sales-part-search-input"
          value={search}
          onChange={setSearch}
          maxLength={100}
        />
        <label className="sicost-field">
          사용 여부
          <select
            data-testid="sales-part-status-filter"
            value={useYn}
            onChange={(event) => setUseYn(event.target.value)}
          >
            <option value="Y">사용</option>
            <option value="N">중지</option>
            <option value="">전체</option>
          </select>
        </label>
      </FilterBar>
      {notice && (
        <p role="status" className="sicost-banner sicost-tone-info">
          {notice}
        </p>
      )}
      {error != null ? (
        <ErrorBanner
          message={
            notice
              ? "저장됐지만 다시 불러오지 못했습니다."
              : error instanceof Error
                ? error.message
                : "목록을 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRefresh((old) => old + 1)}
        />
      ) : mobile ? (
        <>
          <div className="sales-part-cards">
            {mobileRows.map((row) => (
              <article
                key={row.id}
                data-testid={`sales-part-card-${row.id.toLowerCase()}`}
              >
                <button
                  data-testid={`sales-part-card-open-${row.id.toLowerCase()}`}
                  onClick={() => void open(row.id)}
                >
                  {row.name}
                </button>
                <StatusBadge tone={row.useYn === "Y" ? "ok" : "lock"}>
                  {row.useYn === "Y" ? "사용" : "중지"}
                </StatusBadge>
                <p>
                  {row.currentStandard
                    ? `${row.currentStandard.leaderName} · ${formatDecimal(row.currentStandard.profitRate)}% · ${amount(row.currentStandard.salesExpenseAmount)}${amountUnit}`
                    : "적용 전"}
                </p>
                <p>
                  소속 인력 {row.personCount}명 · 진행 프로젝트{" "}
                  {row.projectCount}건
                </p>
              </article>
            ))}
          </div>
          {loading ? (
            <p role="status">불러오는 중입니다.</p>
          ) : !mobileRows.length ? (
            <p className="sicost-empty">조회된 자료가 없습니다.</p>
          ) : null}
          {(data?.total ?? 0) > mobileRows.length && (
            <button
              data-testid="sales-part-load-more"
              disabled={loading}
              onClick={() =>
                setQuery((old) => ({ ...old, page: old.page + 1 }))
              }
            >
              더 보기
            </button>
          )}
        </>
      ) : (
        <DataTable
          rows={data?.items ?? []}
          columns={columns}
          rowKey={(row) => row.id}
          loading={loading}
          dense={dense}
          page={query.page}
          size={query.size}
          total={data?.total ?? 0}
          onPageChange={(page) => setQuery((old) => ({ ...old, page }))}
          onSizeChange={(size) =>
            setQuery((old) => ({ ...old, size, page: 1 }))
          }
          sort={{
            id: query.sort ?? "name",
            direction: query.direction === "DESC" ? "DESC" : "ASC",
          }}
          onSortChange={sort}
          emptyAction={
            <button
              data-testid="sales-part-empty-reset"
              onClick={() => apply(true)}
            >
              조건 초기화
            </button>
          }
        />
      )}
      {creating && (
        <SalesPartCreateDialog
          onClose={() => setCreating(false)}
          onSaved={committed}
          onDirtyChange={onDirtyChange}
        />
      )}
      {selected &&
        (detail ? (
          <SalesPartDetailDialog
            key={selected}
            detail={detail}
            amountUnit={amountUnit}
            onClose={closeDetail}
            onReload={() => void open(selected)}
            onSaved={committed}
            onDirtyChange={onDirtyChange}
            onStandardChange={onStandardChange}
          />
        ) : (
          <DetailDrawer
            label="영업 파트 상세"
            title="영업 파트 상세"
            testId="sales-part-drawer"
            closeTestId="sales-part-drawer-close"
            onClose={closeDetail}
          >
            {detailLoading ? (
              <p role="status">불러오는 중입니다.</p>
            ) : (
              <ErrorBanner
                message={
                  detailError instanceof Error
                    ? detailError.message
                    : "상세를 불러오지 못했습니다."
                }
                onRetry={() => void open(selected)}
              />
            )}
          </DetailDrawer>
        ))}
    </section>
  );
}

function SalesPartDetailDialog({
  detail,
  amountUnit,
  onClose,
  onReload,
  onSaved,
  onDirtyChange,
  onStandardChange,
}: {
  detail: api.SalesPartDetail;
  amountUnit: "원" | "천원" | "백만원";
  onClose(): void;
  onReload(): void;
  onSaved(result: api.SalesPartSaveResult): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
  onStandardChange?(detail: api.SalesPartDetail): void;
}) {
  const [tab, setTab] = useState("현재 기준"),
    [name, setName] = useState(detail.part.name),
    [editing, setEditing] = useState(false);
  const [pending, setPending] = useState(false),
    [confirmStatus, setConfirmStatus] = useState(false),
    [error, setError] = useState<unknown>();
  const [leave, setLeave] = useState<() => void>(),
    [fields, setFields] = useState<Record<string, string>>({});
  const saving = useRef(false);
  const dirty = editing && name !== detail.part.name;
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  const guard = (action: () => void) =>
    dirty ? setLeave(() => action) : action();
  async function save(status: boolean) {
    if (saving.current) return;
    if (!status && (!name.trim() || [...name.trim()].length > 30)) {
      setFields({ name: "파트명은 1~30자로 입력해 주세요." });
      return;
    }
    saving.current = true;
    setPending(true);
    setError(undefined);
    setFields({});
    try {
      const result = await api.putSalesPart(
        detail.part.id,
        status
          ? {
              useYn: detail.part.useYn === "Y" ? "N" : "Y",
              expectedRevision: detail.revision,
            }
          : { name: name.trim(), expectedRevision: detail.revision },
      );
      setEditing(false);
      setName(result.part.part.name);
      setConfirmStatus(false);
      await onSaved(result);
    } catch (reason) {
      setError(reason);
      if (reason instanceof ApiError) setFields(reason.fieldMessages);
    } finally {
      saving.current = false;
      setPending(false);
    }
  }
  const standards: Column<api.SalesPartStandard>[] = [
    {
      id: "effectiveMonth",
      label: "적용 기간",
      render: (row) => `${row.effectiveMonth} ~ ${row.endMonth ?? "계속"}`,
    },
    { id: "leaderName", label: "파트장", render: (row) => row.leaderName },
    {
      id: "profitRate",
      label: "배분율",
      render: (row) => `${formatDecimal(row.profitRate)}%`,
    },
    {
      id: "salesExpenseAmount",
      label: `공통관리비(${amountUnit})`,
      render: (row) =>
        formatDecimal(api.salesPartAmount(row.salesExpenseAmount, amountUnit)),
    },
    {
      id: "status",
      label: "상태",
      render: (row) => (
        <StatusBadge
          tone={
            row.status === "CURRENT"
              ? "ok"
              : row.status === "FUTURE"
                ? "warn"
                : "lock"
          }
        >
          {row.status === "CURRENT"
            ? "적용 중"
            : row.status === "FUTURE"
              ? "예정"
              : "지난 기준"}
        </StatusBadge>
      ),
    },
  ];
  return (
    <>
      <DetailDrawer
        label="영업 파트 상세"
        title={detail.part.name}
        subtitle={
          <>
            <span>{detail.part.id}</span>
            <StatusBadge tone={detail.part.useYn === "Y" ? "ok" : "lock"}>
              {detail.part.useYn === "Y" ? "사용" : "중지"}
            </StatusBadge>
          </>
        }
        testId="sales-part-drawer"
        closeTestId="sales-part-drawer-close"
        pending={pending}
        onClose={() => guard(onClose)}
        actions={
          onStandardChange ? (
            <button
              className="sicost-primary"
              data-testid="sales-part-standard-change"
              type="button"
              disabled={pending}
              onClick={() => guard(() => onStandardChange(detail))}
            >
              기준 변경
            </button>
          ) : null
        }
      >
        <div
          data-testid="sales-part-detail-dialog"
          className="sales-part-detail"
        >
          {error != null && (
            <ErrorBanner
              message={
                error instanceof Error ? error.message : "저장하지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={() => guard(onReload)}
            />
          )}
          {editing ? (
            <div className="sales-part-toolbar">
              <Text
                label="파트명 수정"
                data-testid="sales-part-edit-name"
                value={name}
                onChange={setName}
                error={fields.name}
                disabled={pending}
              />
              <button
                data-testid="sales-part-save-name"
                type="button"
                disabled={pending}
                onClick={() => void save(false)}
              >
                이름 저장
              </button>
              <button
                data-testid="sales-part-cancel-name"
                type="button"
                onClick={() =>
                  guard(() => {
                    setEditing(false);
                    setName(detail.part.name);
                  })
                }
              >
                수정 취소
              </button>
            </div>
          ) : (
            <div className="sales-part-toolbar">
              <button
                data-testid="sales-part-rename"
                type="button"
                onClick={() => setEditing(true)}
              >
                이름 수정
              </button>
              <button
                data-testid="sales-part-status-change"
                type="button"
                onClick={() => setConfirmStatus(true)}
              >
                {detail.part.useYn === "Y" ? "중지" : "재개"}
              </button>
            </div>
          )}
          <div role="tablist" aria-label="파트 상세">
            <>
              {["현재 기준", "기준 이력", "변경 이력"].map((label) => (
                <button
                  data-testid={`sales-part-tab-${label === "현재 기준" ? "current" : label === "기준 이력" ? "standards" : "history"}`}
                  type="button"
                  role="tab"
                  aria-selected={tab === label}
                  key={label}
                  onClick={() => guard(() => setTab(label))}
                >
                  {label}
                </button>
              ))}
            </>
          </div>
          <div role="tabpanel" data-testid="sales-part-detail-panel">
            {tab === "변경 이력" ? (
              <ChangeHistoryTable
                targetType="PART"
                targetId={detail.part.id}
                refreshKey={detail.revision}
              />
            ) : (
              <>
                {tab === "현재 기준" && !detail.part.currentStandard && (
                  <p className="sicost-banner sicost-tone-info">
                    적용 전 · 현재 기준이 없습니다.
                  </p>
                )}
                <DataTable
                  rows={
                    tab === "현재 기준"
                      ? [
                          detail.part.currentStandard,
                          detail.part.scheduledChange,
                        ].filter(
                          (item): item is api.SalesPartStandard =>
                            item !== null,
                        )
                      : detail.standards
                  }
                  columns={standards}
                  rowKey={(row) => row.effectiveMonth}
                  empty="등록된 기준이 없습니다."
                />
                <p>최소 적용월: {detail.minMonth}</p>
              </>
            )}
          </div>
        </div>
      </DetailDrawer>
      <Dialog
        open={confirmStatus}
        title={detail.part.useYn === "Y" ? "영업 파트 중지" : "영업 파트 재개"}
        pending={pending}
        confirmLabel="확인 후 변경"
        onConfirm={() => save(true)}
        onCancel={() => setConfirmStatus(false)}
      >
        <p>{detail.part.name}의 사용 여부를 변경하시겠습니까?</p>
        <p>기준·정산 자료는 삭제하지 않습니다.</p>
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "상태를 변경하지 못했습니다."
            }
          />
        )}
      </Dialog>
      <Dialog
        open={Boolean(leave)}
        title="저장하지 않은 내용이 있습니다."
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onCancel={() => setLeave(undefined)}
        onConfirm={() => {
          setEditing(false);
          setName(detail.part.name);
          leave?.();
          setLeave(undefined);
        }}
      >
        변경 사항을 저장하지 않고 나갑니다.
      </Dialog>
    </>
  );
}
