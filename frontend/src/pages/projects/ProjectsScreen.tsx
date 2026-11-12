import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { getSharedCodes } from "../shared/sharedApi";
import {
  getSalesPartOptions,
  type PartOption,
} from "../salespartsops/partStandardsApi";
import { B9UploadActions } from "../b9/B9UploadDialog";
import { ProjectDistributionPanel } from "../distribution/ProjectDistributionPanel";
import type { ProjectDistributionPanelProps } from "../distribution/ProjectDistributionPanel";
import { ProjectForm } from "./ProjectForm";
import {
  ProjectDetailPanel,
  type ProjectDetailRegistration,
} from "./ProjectDetailPanel";
import {
  projectAmount,
  projectColumns,
  projectStatusTone,
} from "./projectFormat";
import { downloadFilename } from "../shared/downloadFilename";
import * as api from "./projectsApi";
import "./projects.css";
export type ProjectsRegistration = ProjectDetailRegistration & {
  distributionUpload?(
    context: Parameters<ProjectDistributionPanelProps["onUpload"]>[0] & {
      year: number;
      onClose(): void;
      onDirtyChange?(dirty: boolean): void;
    },
  ): ReactNode;
};
export type ProjectsScreenProps = {
  year: number;
  currentYear: number;
  currentDate: string;
  active?: boolean;
  dense?: boolean;
  pageSize?: 20 | 50 | 100;
  amountUnit?: "원" | "천원" | "백만원";
  columnView?: { order: string[]; hidden: string[] };
  registration?: ProjectsRegistration;
  onDirtyChange?(dirty: boolean): void;
  onColumnsChange?(): void;
  onDensityChange?(): void;
  onPageSizeChange?(size: 20 | 50 | 100): Promise<void> | void;
  onAmountUnitChange?(unit: "원" | "천원" | "백만원"): Promise<void> | void;
};
export function ProjectsScreen({
  year,
  currentYear,
  currentDate,
  active = true,
  dense = false,
  pageSize = 50,
  amountUnit = "천원",
  columnView,
  registration,
  onDirtyChange,
  onColumnsChange,
  onDensityChange,
  onPageSizeChange,
  onAmountUnitChange,
}: ProjectsScreenProps) {
  const [mobile, setMobile] = useState(
    () => window.matchMedia?.("(max-width: 767px)").matches ?? false,
  );
  const defaults = { contractType: "", status: "", partId: "", q: "" };
  const [draft, setDraft] = useState(defaults),
    [filters, setFilters] = useState<api.ProjectFilters>({
      year,
      page: 1,
      size: mobile ? 20 : pageSize,
      sort: "startDate",
      direction: "DESC",
    });
  const [data, setData] = useState<api.ProjectsPage>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState<unknown>(),
    [notice, setNotice] = useState("");
  const [codes, setCodes] = useState<
      Record<string, { code: string; name: string }[]>
    >({}),
    [parts, setParts] = useState<PartOption[]>([]);
  const [form, setForm] = useState<{ detail?: api.ProjectDetail }>(),
    [selected, setSelected] =
      useState<Pick<api.ProjectListItem, "id" | "customerName" | "partName">>();
  const [distribution, setDistribution] = useState<{
    id: string;
    customerName: string | null;
    step?: "EST" | "CTRT" | "PLAN";
  }>();
  const [upload, setUpload] =
    useState<Parameters<ProjectDistributionPanelProps["onUpload"]>[0]>();
  const [pendingLeave, setPendingLeave] = useState<() => void>(),
    [downloading, setDownloading] = useState(false);
  const [committed, setCommitted] = useState<api.SaveProjectResult>();
  const sequence = useRef(0),
    scroll = useRef(0),
    dirty = useRef(false);
  const childDirty = useCallback(
    (value: boolean) => {
      dirty.current = value;
      onDirtyChange?.(value);
    },
    [onDirtyChange],
  );
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);
  useEffect(() => {
    const guard = (e: Event) => {
      const event = e as CustomEvent<{ screenId: string; proceed(): void }>;
      if (event.detail.screenId === "UI-01" && dirty.current) {
        e.preventDefault();
        setPendingLeave(() => event.detail.proceed);
      }
    };
    window.addEventListener("sicost:screen-period-changing", guard);
    return () =>
      window.removeEventListener("sicost:screen-period-changing", guard);
  }, []);
  useEffect(() => {
    setFilters((f) => (f.year === year ? f : { ...f, year, page: 1 }));
  }, [year]);
  useEffect(() => {
    const media = window.matchMedia?.("(max-width: 767px)");
    const update = () => {
      setMobile(media?.matches ?? false);
      const size = media?.matches ? 20 : pageSize;
      setFilters((f) => (f.size === size ? f : { ...f, size, page: 1 }));
    };
    media?.addEventListener?.("change", update);
    update();
    return () => media?.removeEventListener?.("change", update);
  }, [pageSize]);
  const load = useCallback(async () => {
    const version = ++sequence.current;
    setLoading(true);
    setError(undefined);
    try {
      const value = await api.getProjects(filters);
      if (sequence.current === version)
        setData((old) =>
          mobile && filters.page > 1
            ? { ...value, items: [...(old?.items ?? []), ...value.items] }
            : value,
        );
    } catch (e) {
      if (sequence.current === version) setError(e);
      throw e;
    } finally {
      if (sequence.current === version) setLoading(false);
    }
  }, [filters, mobile]);
  useEffect(() => {
    if (active) void load().catch(() => {});
    return () => {
      sequence.current++;
    };
  }, [active, load]);
  const loadLookups = useCallback(async () => {
    setActionError(undefined);
    try {
      const groups = ["CTRT_TYPE", "PROJ_STAT", "LOSE_RSN"];
      const [choices, teams] = await Promise.all([
        Promise.all(groups.map((g) => getSharedCodes(g, true))),
        getSalesPartOptions(),
      ]);
      setCodes(Object.fromEntries(groups.map((g, i) => [g, choices[i].items])));
      setParts(teams.items);
    } catch (e) {
      setActionError(e);
    }
  }, []);
  useEffect(() => {
    void loadLookups();
  }, [loadLookups]);
  const label = (group: string, code: string) =>
    codes[group]?.find((c) => c.code === code)?.name ?? code;
  function apply(reset = false) {
    const next = reset ? defaults : draft;
    if (reset) setDraft(defaults);
    setFilters((f) => ({
      ...f,
      page: 1,
      contractType: (next.contractType || undefined) as
        | api.ContractType
        | undefined,
      status: (next.status || undefined) as api.ProjectStatus | undefined,
      partId: next.partId || undefined,
      q: next.q.trim() || undefined,
    }));
  }
  const readOnly = year < currentYear;
  function open(row: api.ProjectListItem) {
    scroll.current = window.scrollY;
    setSelected(row);
  }
  function openDistribution(row: api.ProjectListItem) {
    scroll.current = window.scrollY;
    setSelected(undefined);
    setDistribution({
      id: row.id,
      customerName: row.customerName,
      step: row.distribution?.selectedStep ?? "EST",
    });
  }
  function back() {
    setDistribution(undefined);
    window.scrollTo?.(0, scroll.current);
  }
  async function saved(result: api.SaveProjectResult) {
    setCommitted(result);
    setNotice(
      `${result.changed ? "저장했습니다." : "바뀐 내용이 없습니다."} ${result.project.id} · ${result.warnings.join(" ")}`,
    );
    await Promise.all([load(), api.getProject(result.project.id)]);
    childDirty(false);
    setForm(undefined);
    setCommitted(undefined);
    setSelected({
      id: result.project.id,
      customerName:
        data?.items.find((row) => row.id === result.project.id)?.customerName ??
        null,
      partName:
        data?.items.find((row) => row.id === result.project.id)?.partName ??
        null,
    });
  }
  async function exportList() {
    if (downloading) return;
    setDownloading(true);
    setActionError(undefined);
    try {
      const {
        page: _page,
        size: _size,
        sort,
        direction,
        ...conditions
      } = filters;
      const response = await fetch("/api/exports/master-data", {
        method: "POST",
        redirect: "error",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          view: "PROJECTS",
          filters: conditions,
          sort,
          direction,
          columns: visible.map((c) => c.id),
          amountUnit,
        }),
      });
      if (!response.ok)
        throw new ApiError(response.status, await response.json());
      if (
        !response.headers.get("Content-Type")?.includes("spreadsheetml.sheet")
      )
        throw new Error("다운로드 형식을 확인해 주세요.");
      const url = URL.createObjectURL(await response.blob()),
        anchor = document.createElement("a");
      try {
        anchor.href = url;
        anchor.download = downloadFilename(response, "프로젝트목록.xlsx");
        anchor.click();
      } finally {
        URL.revokeObjectURL(url);
      }
    } catch (e) {
      setActionError(e);
    } finally {
      setDownloading(false);
    }
  }
  const cols: Column<api.ProjectListItem>[] = projectColumns.map((c) => {
    const sort = c.id === "contractPeriod" ? "startDate" : c.id;
    return {
      ...c,
      label:
        c.id === "distribution" ? (
          c.label
        ) : (
          <button
            type="button"
            className="project-sort"
            data-testid={`project-sort-${c.id.toLowerCase()}`}
            onClick={() =>
              setFilters((f) => ({
                ...f,
                page: 1,
                sort: sort as api.ProjectSort,
                direction:
                  f.sort === sort && f.direction === "DESC" ? "ASC" : "DESC",
              }))
            }
          >
            {c.label}
            {filters.sort === sort
              ? filters.direction === "ASC"
                ? " ↑"
                : " ↓"
              : ""}
          </button>
        ),
      align:
        c.id === "contractAmount" || c.id === "personCount"
          ? "right"
          : c.id === "status"
            ? "center"
            : "left",
      render: (row) => {
        switch (c.id) {
          case "name":
            return (
              <button
                type="button"
                className="project-link"
                data-testid={`project-open-${row.id.toLowerCase()}`}
                onClick={(e) => {
                  e.stopPropagation();
                  open(row);
                }}
              >
                {row.name}
              </button>
            );
          case "contractType":
            return (
              <StatusBadge tone="neutral">
                {label("CTRT_TYPE", row.contractType)}
              </StatusBadge>
            );
          case "contractPeriod":
            return `${row.startDate} ~ ${row.endDate}`;
          case "contractAmount":
            return row.contractType === "IN"
              ? "–"
              : projectAmount(row.contractAmount, amountUnit);
          case "partName":
            return row.contractType === "IN"
              ? "–"
              : (row.partName ?? <StatusBadge tone="warn">미지정</StatusBadge>);
          case "status":
            return (
              <StatusBadge tone={projectStatusTone[row.status]}>
                {label("PROJ_STAT", row.status)}
              </StatusBadge>
            );
          case "distribution":
            return row.contractType !== "TK" ? (
              "–"
            ) : (
              <button
                type="button"
                data-testid={`project-distribution-${row.id.toLowerCase()}`}
                className="project-link"
                onClick={(e) => {
                  e.stopPropagation();
                  if (!mobile) openDistribution(row);
                  else setNotice("이 기능은 PC에서 사용해 주세요.");
                }}
              >
                {row.distribution?.selectedStep ?? "올리기"}{" "}
                {row.distribution &&
                  row.distribution.contractDifference !== "0" && (
                    <StatusBadge tone="warn">합계 경고</StatusBadge>
                  )}{" "}
                {Boolean(row.distribution?.mismatchCount) && (
                  <StatusBadge tone="warn">
                    불일치 {row.distribution?.mismatchCount}
                  </StatusBadge>
                )}
              </button>
            );
          case "customerName":
            return row.contractType === "IN"
              ? "(자체)"
              : (row.customerName ?? "–");
          case "personCount":
            return row.personCount;
          default:
            return row.id;
        }
      },
    };
  });
  const visible = (columnView?.order ?? projectColumns.map((c) => c.id))
    .filter(
      (id) => id === "id" || id === "name" || !columnView?.hidden.includes(id),
    )
    .map((id) => cols.find((c) => c.id === id))
    .filter((c): c is Column<api.ProjectListItem> => Boolean(c));
  const feedback = (e: unknown, retry?: () => void) => (
    <ErrorBanner
      message={e instanceof Error ? e.message : "요청하지 못했습니다."}
      requestId={e instanceof ApiError ? e.body.requestId : undefined}
      onRetry={retry}
    />
  );
  return (
    <section
      className="projects-screen"
      data-testid="projects-screen"
      data-screen-id="SCR-UI-01"
      hidden={!active}
    >
      {form ? (
        <ProjectForm
          key={form.detail?.project.id ?? "new"}
          year={year}
          detail={form.detail}
          currentPartName={selected?.partName ?? undefined}
          onClose={() => setForm(undefined)}
          onSaved={saved}
          onDirtyChange={childDirty}
        />
      ) : distribution ? (
        <>
          {mobile ? (
            <p>
              이 기능은 PC에서 사용해 주세요.{" "}
              <button data-testid="project-mobile-back" onClick={back}>
                목록
              </button>
            </p>
          ) : (
            <ProjectDistributionPanel
              projectId={distribution.id}
              customerName={distribution.customerName}
              initialStep={distribution.step}
              amountUnit={amountUnit}
              readOnly={readOnly}
              onBack={back}
              onUpload={(context) => {
                if (registration?.distributionUpload) setUpload(context);
                else
                  setActionError(new Error("배분표 업로드 연결이 필요합니다."));
              }}
              onAmountUnitChange={(unit) => void onAmountUnitChange?.(unit)}
            />
          )}
          {upload &&
            registration?.distributionUpload?.({
              ...upload,
              year,
              onClose: () => setUpload(undefined),
              onDirtyChange: childDirty,
            })}
        </>
      ) : (
        <>
          <header className="project-toolbar">
            <div />
            <div className="project-actions">
              {!mobile && (
                <B9UploadActions
                  year={year}
                  readOnly={readOnly}
                  onApplied={() => load()}
                  onDirtyChange={childDirty}
                />
              )}
              <button
                type="button"
                data-testid="project-create"
                className="sicost-primary"
                disabled={readOnly}
                onClick={() => {
                  setSelected(undefined);
                  setForm({});
                }}
              >
                등록
              </button>
            </div>
          </header>
          {data && data.unassignedCount > 0 && (
            <div
              className="sicost-banner sicost-tone-warn project-unassigned"
              data-testid="project-unassigned-banner"
            >
              <span>
                영업 파트 미지정 프로젝트 {data.unassignedCount}건이 있습니다.
              </span>
              <button
                type="button"
                data-testid="project-unassigned-only"
                onClick={() => {
                  setDraft((d) => ({ ...d, partId: "UNASSIGNED" }));
                  setFilters((f) => ({ ...f, partId: "UNASSIGNED", page: 1 }));
                }}
              >
                이 건만 보기
              </button>
            </div>
          )}
          <form
            className="project-filters"
            data-testid="project-filter-form"
            onSubmit={(e) => {
              e.preventDefault();
              apply();
            }}
          >
            {(
              [
                ["contractType", "계약유형", "CTRT_TYPE"],
                ["status", "상태", "PROJ_STAT"],
              ] as const
            ).map(([key, title, group]) => (
              <label className="sicost-field" key={key}>
                {title}
                <select
                  data-testid={`project-filter-${key.toLowerCase()}`}
                  value={draft[key]}
                  onChange={(e) =>
                    setDraft((d) => ({ ...d, [key]: e.target.value }))
                  }
                >
                  <option value="">전체</option>
                  {codes[group]?.map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
            ))}
            <label className="sicost-field">
              영업 파트
              <select
                data-testid="project-filter-part"
                value={draft.partId}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, partId: e.target.value }))
                }
              >
                <option value="">전체</option>
                {parts.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
                <option value="UNASSIGNED">미지정</option>
              </select>
            </label>
            <label className="sicost-field project-search">
              프로젝트명
              <input
                data-testid="project-search"
                placeholder="프로젝트명 또는 고객사"
                value={draft.q}
                maxLength={100}
                onChange={(e) => setDraft((d) => ({ ...d, q: e.target.value }))}
              />
            </label>
            <button
              type="button"
              data-testid="project-reset"
              onClick={() => apply(true)}
            >
              초기화
            </button>
            <button
              type="submit"
              data-testid="project-query"
              className="sicost-primary"
            >
              조회
            </button>
          </form>
          {notice && (
            <p role="status" className="sicost-banner sicost-tone-info">
              {notice}
            </p>
          )}
          {committed && (
            <button
              data-testid="project-refresh-committed"
              onClick={() => void saved(committed).catch(() => {})}
            >
              저장은 완료됐습니다. 다시 조회
            </button>
          )}
          {actionError != null &&
            feedback(actionError, () => void loadLookups())}
          <div className="project-toolbar">
            <strong>총 {data?.total ?? 0}건</strong>
            <div className="project-actions">
              {onColumnsChange && (
                <button
                  type="button"
                  data-testid="project-columns"
                  onClick={onColumnsChange}
                >
                  열 설정
                </button>
              )}
              {onDensityChange && (
                <button
                  type="button"
                  data-testid="project-density"
                  onClick={onDensityChange}
                >
                  {dense ? "넓게 보기" : "좁게 보기"}
                </button>
              )}
              <label>
                단위{" "}
                <select
                  data-testid="project-amount-unit"
                  value={amountUnit}
                  onChange={(e) =>
                    void onAmountUnitChange?.(
                      e.target.value as "원" | "천원" | "백만원",
                    )
                  }
                >
                  <option>원</option>
                  <option>천원</option>
                  <option>백만원</option>
                </select>
              </label>
              {!mobile && (
                <button
                  type="button"
                  data-testid="project-export"
                  disabled={downloading}
                  onClick={() => void exportList()}
                >
                  목록 다운로드
                </button>
              )}
            </div>
          </div>
          {error != null && feedback(error, () => void load().catch(() => {}))}
          {!mobile ? (
            <DataTable
              rows={data?.items ?? []}
              columns={visible}
              rowKey={(r) => r.id}
              loading={loading}
              dense={dense}
              page={filters.page}
              size={filters.size}
              total={data?.total ?? 0}
              onRowAction={open}
              onPageChange={(page) => setFilters((f) => ({ ...f, page }))}
              onSizeChange={(size) => {
                setFilters((f) => ({ ...f, page: 1, size }));
                void onPageSizeChange?.(size);
              }}
            />
          ) : (
            <div className="project-cards">
              {loading && <p role="status">불러오는 중입니다.</p>}
              {!loading && !data?.items.length && (
                <p className="sicost-empty">조회된 자료가 없습니다.</p>
              )}
              {data?.items.map((row) => (
                <article
                  key={row.id}
                  className="project-card"
                  data-testid={`project-card-${row.id.toLowerCase()}`}
                >
                  <button
                    type="button"
                    data-testid={`project-card-open-${row.id.toLowerCase()}`}
                    onClick={() => open(row)}
                  >
                    {row.name} · {row.id}
                  </button>
                  <p>
                    {row.customerName ?? "–"} · {row.startDate} ~ {row.endDate}
                  </p>
                  <StatusBadge tone={projectStatusTone[row.status]}>
                    {label("PROJ_STAT", row.status)}
                  </StatusBadge>
                  <p>
                    {projectAmount(row.contractAmount, amountUnit)} {amountUnit}{" "}
                    · {row.partName ?? "미지정"}
                  </p>
                </article>
              ))}
              {(data?.items.length ?? 0) < (data?.total ?? 0) && (
                <button
                  data-testid="project-more"
                  disabled={loading}
                  onClick={() =>
                    setFilters((f) => ({ ...f, page: f.page + 1 }))
                  }
                >
                  20개 더보기
                </button>
              )}
            </div>
          )}
        </>
      )}
      {selected && !form && !distribution && (
        <ProjectDetailPanel
          key={selected.id}
          id={selected.id}
          year={year}
          currentYear={currentYear}
          currentDate={currentDate}
          customerName={selected.customerName}
          labels={label}
          amountUnit={amountUnit}
          registration={registration}
          onClose={() => {
            setSelected(undefined);
            window.scrollTo?.(0, scroll.current);
          }}
          onEdit={(detail) => setForm({ detail })}
          onDistribution={(detail) => {
            setDistribution({
              id: detail.project.id,
              customerName: selected.customerName,
            });
            setSelected(undefined);
          }}
          onChanged={() => load()}
          onDirtyChange={childDirty}
        />
      )}
      <Dialog
        open={Boolean(pendingLeave)}
        title="저장하지 않은 내용이 있습니다."
        onCancel={() => setPendingLeave(undefined)}
        onConfirm={() => {
          setForm(undefined);
          setSelected(undefined);
          setUpload(undefined);
          childDirty(false);
          pendingLeave?.();
          setPendingLeave(undefined);
        }}
        cancelLabel="계속 작성"
        confirmLabel="나가기"
      >
        <p>선택 연도를 바꾸면 입력한 내용을 폐기합니다.</p>
      </Dialog>
    </section>
  );
}
