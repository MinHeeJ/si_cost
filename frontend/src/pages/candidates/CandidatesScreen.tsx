import { ScreenLoading } from "../../shared/ScreenLoading";
import { TableControlIcon } from "../../shared/Icon";
import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { useCallback, useEffect, useRef, useState } from "react";
import { FilterBar } from "../../shared/FilterBar";
import { DataTable, type Column } from "../../shared/DataTable";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { ErrorBanner, FeedbackNotice, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatAmount } from "../../shared/format";
import {
  useFieldPermission,
  usePermission,
} from "../../shared/permission/PermissionBoundary";
import { CandidateSummaryPanel } from "../candidatesops/CandidateSummaryPanel";
import { CandidateShortagesPanel } from "../candidatesops/CandidateShortagesPanel";
import { CandidatePositionsPanel } from "../candidatesops/CandidatePositionsPanel";
import {
  getShortageCandidates,
  shortageCandidateFilters,
  type CandidateShortageContext,
} from "../candidatesops/candidateMatchingApi";
import {
  CandidateContactsPanel,
  CandidateContactStatusBadge,
} from "../contacts/CandidateContactsPanel";
import { ChangeHistoryTable } from "../apiops/ChangeHistoryTable";
import { getPeopleHistory } from "../apiops/peopleClient";
import { getCandidateContacts } from "../contacts/peopleClient";
import { CandidateForm } from "./CandidateForm";
import * as api from "./peopleClient";
import "./candidates.css";

export const candidateScreenGuide = (
  <dl>
    <div>
      <dt>후보 요약과 투입 가능일</dt>
      <dd>
        요약은 전체 후보 기준이며 목록의 조회 조건과는 별도로 집계합니다.
        요약의 ‘다음 달 시작까지 가능’은 다음 달 1일까지 투입 가능한
        진행 중 후보를 셉니다. 목록에서 ‘다음 달까지’를 선택하면 다음 달
        말일까지 투입 가능한 후보를 조회합니다.
      </dd>
    </div>
    <div>
      <dt>부족 직무별 후보 조회</dt>
      <dd>
        [후보 보기]를 누르면 해당 직무에서 첫 부족월 말일까지 투입 가능한
        후보를 조회합니다. 보류와 계약 완료 상태는 제외합니다.
      </dd>
    </div>
    <div>
      <dt>후보 등록과 상세 확인</dt>
      <dd>
        [후보 등록]에서 외부 인력 정보를 입력할 수 있습니다. 목록에서 이름을
        누르면 기본 정보, 연락 기록과 변경 이력을 확인할 수 있습니다.
      </dd>
    </div>
    <div>
      <dt>맞는 자리</dt>
      <dd>
        후보 상세에서 해당 후보가 투입될 수 있는 영업 기회를 최대 4곳까지
        확인할 수 있습니다. 개발자 등록과 배정·가배정은 별도 업무 화면에서
        진행합니다.
      </dd>
    </div>
  </dl>
);

export type CandidatesScreenProps = {
  entryJob?: { key: string; jobCode: string };
  active?: boolean;
  dense?: boolean;
  pageSize?: 20 | 50 | 100;
  amountUnit?: "원" | "천원" | "백만원";
  onDirtyChange?(dirty: boolean): void;
  columnView?: { order: string[]; hidden: string[] };
  onColumnsChange?(): void;
  onDensityChange?(): void;
};
export const candidateColumnOrder = [
  "name",
  "employeeType",
  "job",
  "grade",
  "skills",
  "homeAddress",
  "desiredRate",
  "availableDate",
  "contactStatus",
  "lastContactDate",
  "source",
];
const initialFilters = {
  jobCode: "",
  gradeCode: "",
  employeeType: "",
  contactStatus: "",
  availability: "",
};
export function CandidatesScreen({
  entryJob,
  active = true,
  dense = false,
  pageSize = 50,

  onDirtyChange,
  columnView,
  onColumnsChange,
  onDensityChange,
}: CandidatesScreenProps) {
  const canRate = useFieldPermission("UI-33", "desiredRate");
  const canRegister = usePermission("UI-33", "REG"),
    canUpdate = usePermission("UI-33", "UPD"),
    canDownload = usePermission("UI-33", "DNLD");
  const [mobile, setMobile] = useState(
    () => window.matchMedia?.("(max-width: 767px)")?.matches ?? false,
  );
  const [draft, setDraft] = useState(initialFilters),
    [filters, setFilters] = useState<api.CandidateFilters>({
      page: 1,
      size: mobile ? 20 : pageSize,
    });
  const consumedEntry = useRef<string>();
  useEffect(() => {
    if (!active || !entryJob || consumedEntry.current === entryJob.key) return;
    consumedEntry.current = entryJob.key;
    setShortageContext(undefined);
    setDraft({ ...initialFilters, jobCode: entryJob.jobCode });
    setFilters((previous) => ({
      page: 1,
      size: previous.size,
      jobCode: entryJob.jobCode,
    }));
  }, [active, entryJob?.key, entryJob?.jobCode]);
  const [data, setData] = useState<api.CandidatesPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const [shortageContext, setShortageContext] =
    useState<CandidateShortageContext>();
  const [shortageNotice, setShortageNotice] = useState<string>();
  useEffect(() => {
    if (!active) setShortageNotice(undefined);
  }, [active]);
  const [codes, setCodes] = useState<Record<string, api.CandidateCode[]>>({}),
    [today, setToday] = useState("");
  const [version, setVersion] = useState(0),
    [lookupRetry, setLookupRetry] = useState(0),
    [lookupError, setLookupError] = useState<unknown>();
  const [form, setForm] = useState<{
      candidate?: api.CandidateDetail;
      open: boolean;
      key: number;
    }>(),
    [detail, setDetail] = useState<api.CandidateDetail>();
  const [selected, setSelected] = useState<string>(),
    [detailError, setDetailError] = useState<unknown>(),
    [detailLoading, setDetailLoading] = useState(false),
    [tab, setTab] = useState("기본 정보");
  const [notice, setNotice] = useState(""),
    [committed, setCommitted] = useState<string>(),
    [highlight, setHighlight] = useState<string>();
  const [pinnedRow, setPinnedRow] = useState<api.CandidateDetail>();
  const [actionError, setActionError] = useState<unknown>(),
    [downloading, setDownloading] = useState(false);
  const [detailCounts, setDetailCounts] = useState<{
    contacts: number;
    history: number;
  }>();
  const [countsError, setCountsError] = useState(false);
  const [countsRetry, setCountsRetry] = useState(0);
  useEffect(() => {
    setDetailCounts(undefined);
    setCountsError(false);
    if (!detail) return;
    const controller = new AbortController();
    void Promise.all([
      getCandidateContacts(detail.id, 1, 20, controller.signal),
      getPeopleHistory("CANDIDATE", detail.id, 1, 20, controller.signal),
    ])
      .then(([contacts, history]) => {
        if (!controller.signal.aborted)
          setDetailCounts({ contacts: contacts.total, history: history.total });
      })
      .catch(() => {
        if (!controller.signal.aborted) setCountsError(true);
      });
    return () => controller.abort();
  }, [detail?.id, detail?.revision, version, countsRetry]);
  useEffect(() => {
    const refresh = () => setVersion((v) => v + 1);
    const events = [
      "sicost:sales-committed",
      "sicost:assignments-committed",
      "sicost:developers-committed",
      "sicost:operating-standards-committed",
      "sicost:codegroups-committed",
    ];
    events.forEach((name) => window.addEventListener(name, refresh));
    return () =>
      events.forEach((name) => window.removeEventListener(name, refresh));
  }, []);
  const sequence = useRef(0),
    scroll = useRef(0),
    nextForm = useRef(0),
    openSequence = useRef(0);
  useEffect(() => {
    const media = window.matchMedia?.("(max-width: 767px)");
    const update = () => {
      setMobile(media?.matches ?? false);
      setFilters((f) => ({
        ...f,
        page: 1,
        size: media?.matches ? 20 : pageSize,
      }));
    };
    media?.addEventListener("change", update);
    return () => media?.removeEventListener("change", update);
  }, [pageSize]);
  useEffect(() => {
    let current = true;
    setLookupError(undefined);
    void Promise.all([
      Promise.all(
        ["EMP_TYPE", "JOB", "GRADE", "CNTC_STAT"].map(
          async (group) =>
            [group, (await api.getCandidateCodes(group)).items] as const,
        ),
      ),
      api.getCandidateDate(),
    ])
      .then(([entries, date]) => {
        if (current) {
          setCodes(Object.fromEntries(entries));
          setToday(date.currentDate);
        }
      })
      .catch((e) => {
        if (current) setLookupError(e);
      });
    return () => {
      current = false;
    };
  }, [lookupRetry]);
  const load = useCallback(
    async (signal?: AbortSignal) => {
      const token = ++sequence.current;
      setPinnedRow(undefined);
      setLoading(true);
      setError(undefined);
      try {
        const page = shortageContext
          ? await getShortageCandidates(filters, shortageContext, signal)
          : await api.getCandidates(filters, signal);
        if (sequence.current === token && !signal?.aborted)
          setData((previous) =>
            mobile && filters.page > 1
              ? {
                  ...page,
                  items: [
                    ...(previous?.items ?? []).filter(
                      (r) => !page.items.some((n) => n.id === r.id),
                    ),
                    ...page.items,
                  ],
                }
              : page,
          );
      } catch (e) {
        if (sequence.current === token && !signal?.aborted) setError(e);
        throw e;
      } finally {
        if (sequence.current === token && !signal?.aborted) setLoading(false);
      }
    },
    [filters, mobile, shortageContext],
  );
  useEffect(() => {
    const controller = new AbortController();
    if (active) void load(controller.signal).catch(() => {});
    return () => controller.abort();
  }, [load, active, version]);
  useEffect(() => {
    if (!highlight) return;
    const timer = window.setTimeout(() => setHighlight(undefined), 6000);
    return () => window.clearTimeout(timer);
  }, [highlight]);
  function label(group: string, value: string) {
    return codes[group]?.find((c) => c.code === value)?.name ?? value;
  }
  async function open(id: string) {
    const token = ++openSequence.current;
    scroll.current = window.scrollY;
    setSelected(id);
    setDetail(undefined);
    setTab("기본 정보");
    setDetailLoading(true);
    setDetailError(undefined);
    try {
      const row = await api.getCandidate(id);
      if (token === openSequence.current) setDetail(row);
    } catch (e) {
      if (token === openSequence.current) setDetailError(e);
    } finally {
      if (token === openSequence.current) setDetailLoading(false);
    }
  }
  async function refreshDetail() {
    if (!selected) return;
    const row = await api.getCandidate(selected);
    setDetail(row);
    setVersion((v) => v + 1);
  }
  async function saved(id: string, message: string, created: boolean) {
    setForm(undefined);
    if (!id) return;
    setNotice(message);
    setCommitted(id);
    setSelected(undefined);
    setDetail(undefined);
    if (created) {
      setShortageContext(undefined);
      setDraft(initialFilters);
      setFilters({ page: 1, size: mobile ? 20 : filters.size });
    }
    try {
      const row = await api.getCandidate(id);
      setSelected(id);
      setDetail(row);
      setTab("기본 정보");
      if (created) {
        const page = await api.getCandidates({
          page: 1,
          size: mobile ? 20 : filters.size,
        });
        setData({
          ...page,
          items: [row, ...page.items.filter((r) => r.id !== id)],
        });
        setHighlight(id);
        setPinnedRow(row);
      } else await load();
      if (!created) setVersion((v) => v + 1);
      setCommitted(undefined);
    } catch {
      setNotice(
        `${message} 저장은 완료됐습니다. 다시 저장하지 말고 다시 조회해 주세요.`,
      );
    }
  }
  async function retryCommitted() {
    if (!committed) return;
    try {
      const row = await api.getCandidate(committed);
      await load();
      setDetail(row);
      setSelected(committed);
      setCommitted(undefined);
      setVersion((v) => v + 1);
    } catch {
      setNotice("저장은 완료됐습니다. 조회를 다시 시도해 주세요.");
    }
  }
  function apply(reset = false) {
    const values = reset ? initialFilters : draft;
    if (
      reset ||
      values.jobCode !== shortageContext?.jobCode ||
      values.availability
    ) {
      setShortageContext(undefined);
      setShortageNotice(undefined);
    }
    if (reset) setDraft(initialFilters);
    setHighlight(undefined);
    setFilters({
      ...Object.fromEntries(Object.entries(values).filter(([, v]) => v)),
      page: 1,
      size: filters.size,
    });
  }
  const money = (value: string | undefined) => {
    if (value === undefined) return "";
    return formatAmount(value, "원");
  };
  const days = (row: api.CandidateItem) =>
    today && row.lastContactDate
      ? Math.floor(
          (Date.parse(`${today}T00:00:00Z`) -
            Date.parse(`${row.lastContactDate}T00:00:00Z`)) /
            86400000,
        )
      : null;
  const columns: Column<api.CandidateItem>[] = [
    {
      id: "name",
      label: "이름",
      render: (r) => (
        <button
          className={
            highlight === r.id
              ? "candidate-name candidate-new"
              : "candidate-name"
          }
          data-testid={`candidate-name-${r.id.toLowerCase()}`}
          onClick={(e) => {
            e.stopPropagation();
            void open(r.id);
          }}
        >
          {r.name}
        </button>
      ),
    },
    {
      id: "employeeType",
      label: "구분",
      render: (r) => (
        <>
          <StatusBadge tone="neutral">
            {label("EMP_TYPE", r.employeeType)}
          </StatusBadge>
          {r.employeeType === "OS" && (
            <small className="candidate-sub">{r.companyName}</small>
          )}
        </>
      ),
    },
    { id: "job", label: "직무", render: (r) => r.job },
    { id: "grade", label: "등급", render: (r) => r.grade },
    {
      id: "skills",
      label: "기술",
      render: (r) => (
        <div
          data-testid={`candidate-skills-${r.id.toLowerCase()}`}
          className="candidate-skills"
        >
          {r.skills.map((s) => (
            <span
              data-testid={`candidate-skill-${r.id.toLowerCase()}-${r.skills.indexOf(s)}`}
              className="candidate-chip"
              key={s}
            >
              {s}
            </span>
          ))}
        </div>
      ),
    },
    { id: "homeAddress", label: "거주지", render: (r) => r.homeAddress ?? "–" },
    {
      id: "desiredRate",
      label: "희망 단가",
      align: "right",
      render: (r) => money(r.desiredRate),
    },
    {
      id: "availableDate",
      label: "투입 가능일",
      render: (r) => r.availableDate,
    },
    {
      id: "contactStatus",
      label: "연락 상태",
      render: (r) => <CandidateContactStatusBadge status={r.contactStatus} />,
    },
    {
      id: "lastContactDate",
      label: "최근 연락",
      render: (r) => (
        <>
          <span>{r.lastContactDate ?? "–"}</span>
          {!["HOLD", "CTRT"].includes(r.contactStatus) &&
            (days(r) ?? 0) > 30 && (
              <small className="candidate-overdue">{days(r)}일 전</small>
            )}
        </>
      ),
    },
    { id: "source", label: "출처", render: (r) => r.source },
  ];
  const visible = (columnView?.order ?? candidateColumnOrder)
    .filter((k) => k !== "desiredRate" || canRate)
    .filter((k) => k === "name" || !columnView?.hidden.includes(k))
    .map((k) => columns.find((c) => c.id === k))
    .map((c) =>
      c && !["skills", "homeAddress"].includes(c.id)
        ? { ...c, sortable: true }
        : c,
    )
    .filter((c): c is Column<api.CandidateItem> => Boolean(c));
  const sortedRows =
    pinnedRow && data
      ? [pinnedRow, ...data.items.filter((r) => r.id !== pinnedRow.id)].slice(
          0,
          filters.size,
        )
      : (data?.items ?? []);
  return (
    <section
      hidden={!active}
      className="candidates-screen"
      data-testid="candidates-screen"
      data-screen-id="SCR-UI-33"
      data-feature-id="SLICE-BASIC132-CANDIDATES"
    >
      <ScreenTitleActions>
        <header className="candidate-toolbar">
          <button
            className="sicost-primary"
            data-testid="candidate-create"
            disabled={!canRegister || Boolean(committed)}
            onClick={() =>
              setForm((f) =>
                f && !f.candidate
                  ? { ...f, open: true }
                  : { open: true, key: ++nextForm.current },
              )
            }
          >
            후보 등록
          </button>
        </header>
      </ScreenTitleActions>
      {active && shortageNotice && (
        <FeedbackNotice
          tone="info"
          title="후보 조회 범위"
          onDismiss={() => setShortageNotice(undefined)}
        >
          {shortageNotice}
        </FeedbackNotice>
      )}
      <CandidateSummaryPanel refreshKey={version} active={active} />
      <CandidateShortagesPanel
        refreshKey={version}
        active={active}
        jobLabel={(code) => label("JOB", code)}
        onView={(row) => {
          setShortageNotice(
            `${label("JOB", row.jobCode)} 직무에서 ${row.availableUntil}까지 투입 가능한 후보를 조회합니다. 보류·계약 완료는 제외합니다. [초기화]를 누르면 조회 조건이 해제됩니다.`,
          );
          setShortageContext({
            jobCode: row.jobCode,
            availableUntil: row.availableUntil,
          });
          setDraft({ ...initialFilters, jobCode: row.jobCode });
          setHighlight(undefined);
          setFilters({ jobCode: row.jobCode, page: 1, size: filters.size });
        }}
      />
      {lookupError != null && (
        <ErrorBanner
          message="필터 코드와 서버 날짜를 불러오지 못했습니다."
          onRetry={() => setLookupRetry((v) => v + 1)}
        />
      )}
      <FilterBar
        className="candidate-filters"
        testId="candidate-filters"
        resetTestId="candidate-reset"
        searchTestId="candidate-query"
        onSubmit={() => apply()}
        onReset={() => apply(true)}
      >
        {(
          [
            ["jobCode", "직무", "JOB"],
            ["gradeCode", "등급", "GRADE"],
            ["employeeType", "구분", "EMP_TYPE"],
            ["contactStatus", "연락 상태", "CNTC_STAT"],
          ] as const
        ).map(([key, title, group]) => (
          <label
            data-testid={`candidate-filter-label-${key.toLowerCase()}`}
            className="sicost-field"
            key={key}
          >
            {title}
            <select
              data-testid={`candidate-filter-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
              value={draft[key]}
              onChange={(e) =>
                setDraft((f) => ({ ...f, [key]: e.target.value }))
              }
            >
              <option value="">전체</option>
              {(codes[group] ?? [])
                .filter(
                  (c) =>
                    key !== "employeeType" || ["FL", "OS"].includes(c.code),
                )
                .map((c) => (
                  <option
                    data-testid={`candidate-filter-option-${group.toLowerCase().replace(/_/g, "-")}-${c.code.toLowerCase().replace(/_/g, "-")}`}
                    key={c.code}
                    value={c.code}
                  >
                    {c.name}
                  </option>
                ))}
            </select>
          </label>
        ))}
        <label className="sicost-field">
          투입 가능
          <select
            data-testid="candidate-filter-availability"
            value={draft.availability}
            onChange={(e) =>
              setDraft((f) => ({ ...f, availability: e.target.value }))
            }
          >
            <option value="">전체</option>
            <option value="NEXT_MONTH">다음 달까지</option>
            <option value="THREE_MONTHS">3개월 안</option>
          </select>
        </label>
      </FilterBar>
      <div className="candidate-toolbar">
        <strong>총 {data?.total ?? 0}건</strong>
        <div className="candidate-actions">
          {onColumnsChange && (
            <button data-testid="candidate-columns" onClick={onColumnsChange}>
              <TableControlIcon name="columns" />
              열 설정
            </button>
          )}
          {onDensityChange && (
            <button data-testid="candidate-density" onClick={onDensityChange}>
              <TableControlIcon name="rows" />
              {dense ? "넓게 보기" : "좁게 보기"}
            </button>
          )}
          {!mobile && (
            <button
              className="sicost-download-button"
              data-testid="candidate-export"
              disabled={!canDownload || downloading || Boolean(committed)}
              onClick={async () => {
                setDownloading(true);
                setActionError(undefined);
                try {
                  await api.downloadCandidates(
                    shortageContext
                      ? shortageCandidateFilters(filters, shortageContext)
                      : filters,
                    visible.map((c) => c.id),
                    "원",
                  );
                } catch (e) {
                  setActionError(e);
                } finally {
                  setDownloading(false);
                }
              }}
            >
              엑셀 다운로드
            </button>
          )}
        </div>
      </div>
      {notice && (
        <p role="status" className="sicost-banner sicost-tone-info">
          {notice}
        </p>
      )}
      {committed && (
        <button
          data-testid="candidate-refresh-saved"
          onClick={() => void retryCommitted()}
        >
          저장된 후보 다시 조회
        </button>
      )}
      {actionError != null && (
        <ErrorBanner
          message={
            actionError instanceof Error
              ? actionError.message
              : "다운로드하지 못했습니다."
          }
          requestId={
            actionError instanceof ApiError
              ? actionError.body.requestId
              : undefined
          }
        />
      )}
      {error != null ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "후보 목록을 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setVersion((v) => v + 1)}
        />
      ) : mobile ? (
        <div className="candidate-cards" aria-busy={loading}>
          {loading && <ScreenLoading message="불러오는 중입니다." />}
          {!loading && !sortedRows.length && (
            <div className="sicost-empty">
              <p>조건에 맞는 결과가 없습니다.</p>
              <button
                type="button"
                data-testid="candidate-card-empty-reset"
                onClick={() => apply(true)}
              >
                조건 초기화
              </button>
            </div>
          )}
          {sortedRows.map((r) => (
            <article
              data-testid={`candidate-card-${r.id.toLowerCase()}`}
              className={highlight === r.id ? "candidate-new-row" : undefined}
              key={r.id}
            >
              {columns
                .filter((c) => c.id !== "desiredRate" || canRate)
                .map((c) => (
                  <div
                    data-testid={`candidate-card-field-${r.id.toLowerCase()}-${c.id.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
                    key={c.id}
                  >
                    {c.render(r)}
                  </div>
                ))}
            </article>
          ))}
          {data && data.items.length < data.total && (
            <button
              data-testid="candidate-more"
              disabled={loading}
              onClick={() =>
                setFilters((f) => ({ ...f, page: f.page + 1, size: 20 }))
              }
            >
              20개 더보기
            </button>
          )}
        </div>
      ) : (
        <DataTable
          rows={sortedRows}
          columns={visible}
          rowKey={(r) => r.id}
          loading={loading}
          empty="조건에 맞는 결과가 없습니다."
          sort={
            filters.sort
              ? {
                  id: filters.sort,
                  direction: filters.direction === "DESC" ? "DESC" : "ASC",
                }
              : undefined
          }
          onSortChange={(id) => {
            setHighlight(undefined);
            setFilters((f) => ({
              ...f,
              page: 1,
              sort: id,
              direction:
                f.sort === id && f.direction !== "DESC" ? "DESC" : "ASC",
            }));
          }}
          emptyAction={
            <button
              type="button"
              data-testid="candidate-empty-reset"
              onClick={() => apply(true)}
            >
              조건 초기화
            </button>
          }
          page={filters.page}
          size={filters.size}
          total={data?.total ?? 0}
          onPageChange={(page) => {
            setHighlight(undefined);
            setFilters((f) => ({ ...f, page }));
          }}
          onSizeChange={(size) => {
            setHighlight(undefined);
            setFilters((f) => ({ ...f, size, page: 1 }));
          }}
          dense={dense}
          onRowAction={(r) => void open(r.id)}
        />
      )}
      {active && selected && (
        <DetailDrawer
          title={detail?.name ?? "후보 상세"}
          subtitle={selected}
          onClose={() => {
            openSequence.current++;
            setSelected(undefined);
            window.scrollTo?.(0, scroll.current);
          }}
          actions={
            detail && (
              <button
                data-testid="candidate-edit"
                disabled={!canUpdate || Boolean(committed)}
                onClick={() =>
                  setForm((f) =>
                    f?.candidate?.id === detail.id
                      ? { ...f, open: true }
                      : {
                          candidate: detail,
                          open: true,
                          key: ++nextForm.current,
                        },
                  )
                }
              >
                수정
              </button>
            )
          }
        >
          {detailLoading ? (
            <ScreenLoading message="상세를 불러오는 중입니다." />
          ) : detailError != null ? (
            <ErrorBanner
              message="후보 상세를 불러오지 못했습니다."
              onRetry={() => void open(selected)}
            />
          ) : (
            detail && (
              <>
                <SegmentedTabList
                  as="nav"
                  className="candidate-tabs"
                  aria-label="후보 상세 탭"
                >
                  {["기본 정보", "연락 기록", "변경 이력"].map((t) => (
                    <button
                      key={t}
                      data-testid={`candidate-tab-${t === "기본 정보" ? "basic" : t === "연락 기록" ? "contacts" : "history"}`}
                      aria-pressed={t === tab}
                      onClick={() => setTab(t)}
                    >
                      {t === "기본 정보"
                        ? t
                        : `${t} (${(t === "연락 기록" ? detailCounts?.contacts : detailCounts?.history) ?? "…"})`}
                    </button>
                  ))}
                </SegmentedTabList>
                {countsError && (
                  <ErrorBanner
                    message="상세 탭 건수를 불러오지 못했습니다."
                    onRetry={() => setCountsRetry((n) => n + 1)}
                  />
                )}
                <section
                  hidden={tab !== "기본 정보"}
                  role="tabpanel"
                  aria-label="기본 정보"
                  data-testid="candidate-basic-panel"
                >
                  <dl className="candidate-detail-fields">
                    {[
                      ["구분", label("EMP_TYPE", detail.employeeType)],
                      ["업체", detail.companyName],
                      ["전화번호", detail.phone],
                      ["이메일", detail.email],
                      ["주 직무", detail.job],
                      [
                        "보조 직무",
                        detail.subJobCode
                          ? label("JOB", detail.subJobCode)
                          : null,
                      ],
                      ["등급", detail.grade],
                      ["거주지", detail.homeAddress],
                      ["희망 단가", `${money(detail.desiredRate)} 원`],
                      ["투입 가능일", detail.availableDate],
                      ["출처", detail.source],
                      ["개인정보 동의", detail.consent ? "동의" : "미동의"],
                      [
                        "동의 확인",
                        detail.consentDate
                          ? `${detail.consentDate.slice(5)} ${detail.consentUserName}(${detail.consentUserId})`
                          : null,
                      ],
                      ["자체 메모", detail.memo],
                    ]
                      .filter(([k]) => k !== "희망 단가" || canRate)
                      .map(([k, v]) => (
                        <div
                          data-testid={`candidate-detail-field-${Array.from(k!, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                          key={k}
                        >
                          <dt>{k}</dt>
                          <dd>{v ?? "–"}</dd>
                        </div>
                      ))}
                  </dl>
                  <div className="candidate-skills">
                    {detail.skills.map((s) => (
                      <span
                        data-testid={`candidate-detail-skill-${detail.skills.indexOf(s)}`}
                        className="candidate-chip"
                        key={s}
                      >
                        {s}
                      </span>
                    ))}
                  </div>
                  <CandidatePositionsPanel
                    candidate={detail}
                    active={tab === "기본 정보"}
                    refreshKey={version}
                    amountUnit="원"
                    label={(group, code) =>
                      code == null ? "–" : label(group, code)
                    }
                  />
                </section>
                <CandidateContactsPanel
                  candidate={detail}
                  active={tab === "연락 기록"}
                  onCommitted={refreshDetail}
                  onDirtyChange={onDirtyChange}
                />
                {tab === "변경 이력" && (
                  <ChangeHistoryTable
                    targetType="CANDIDATE"
                    targetId={detail.id}
                    loader={getPeopleHistory}
                    refreshKey={version}
                    showTargetTable
                  />
                )}
              </>
            )
          )}
        </DetailDrawer>
      )}
      {form && (
        <CandidateForm
          key={form.key}
          candidate={form.candidate}
          open={active && form.open}
          onClose={() => setForm((f) => (f ? { ...f, open: false } : f))}
          onSaved={(id, message, created) => void saved(id, message, created)}
          onDirtyChange={onDirtyChange}
        />
      )}
    </section>
  );
}
