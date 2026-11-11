import { useCallback, useEffect, useRef, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { CandidateSummaryPanel } from "../candidatesops/CandidateSummaryPanel";
import {
  CandidateContactsPanel,
  CandidateContactStatusBadge,
} from "../contacts/CandidateContactsPanel";
import { ChangeHistoryTable } from "../apiops/ChangeHistoryTable";
import { getPeopleHistory } from "../apiops/peopleClient";
import { CandidateForm } from "./CandidateForm";
import * as api from "./peopleClient";
import "./candidates.css";

export type CandidatesScreenProps = {
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
  active = true,
  dense = false,
  pageSize = 50,
  amountUnit = "원",
  onDirtyChange,
  columnView,
  onColumnsChange,
  onDensityChange,
}: CandidatesScreenProps) {
  const [mobile, setMobile] = useState(
    () => window.matchMedia?.("(max-width: 767px)").matches ?? false,
  );
  const [draft, setDraft] = useState(initialFilters),
    [filters, setFilters] = useState<api.CandidateFilters>({
      page: 1,
      size: mobile ? 20 : pageSize,
    });
  const [data, setData] = useState<api.CandidatesPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
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
  const [actionError, setActionError] = useState<unknown>(),
    [downloading, setDownloading] = useState(false);
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
      setLoading(true);
      setError(undefined);
      try {
        const page = await api.getCandidates(filters, signal);
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
    [filters, mobile],
  );
  useEffect(() => {
    const controller = new AbortController();
    if (active) void load(controller.signal).catch(() => {});
    return () => controller.abort();
  }, [load, active, version]);
  useEffect(() => {
    if (!highlight) return;
    const timer = window.setTimeout(() => setHighlight(undefined), 2000);
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
      } else await load();
      setVersion((v) => v + 1);
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
    if (reset) setDraft(initialFilters);
    setHighlight(undefined);
    setFilters({
      ...Object.fromEntries(Object.entries(values).filter(([, v]) => v)),
      page: 1,
      size: filters.size,
    });
  }
  const money = (value: string) => {
    const divisor =
      amountUnit === "백만원" ? 1000000 : amountUnit === "천원" ? 1000 : 1;
    return (Number(value) / divisor).toLocaleString("ko-KR", {
      maximumFractionDigits: 6,
    });
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
      label: `희망 단가 (${amountUnit})`,
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
    .filter((k) => k === "name" || !columnView?.hidden.includes(k))
    .map((k) => columns.find((c) => c.id === k))
    .filter((c): c is Column<api.CandidateItem> => Boolean(c));
  const highlightedRow =
    highlight && detail?.id === highlight ? detail : undefined;
  const sortedRows =
    highlight && data
      ? [
          ...(highlightedRow
            ? [highlightedRow]
            : data.items.filter((r) => r.id === highlight)),
          ...data.items.filter((r) => r.id !== highlight),
        ].slice(0, filters.size)
      : (data?.items ?? []);
  return (
    <section
      hidden={!active}
      className="candidates-screen"
      data-testid="candidates-screen"
      data-screen-id="SCR-UI-33"
      data-feature-id="SLICE-BASIC132-CANDIDATES"
    >
      <header className="candidate-toolbar">
        <button
          className="sicost-primary"
          data-testid="candidate-create"
          disabled={Boolean(committed)}
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
      <CandidateSummaryPanel refreshKey={version} active={active} />
      {lookupError != null && (
        <ErrorBanner
          message="필터 코드와 서버 날짜를 불러오지 못했습니다."
          onRetry={() => setLookupRetry((v) => v + 1)}
        />
      )}
      <form
        className="candidate-filters"
        data-testid="candidate-filters"
        onSubmit={(e) => {
          e.preventDefault();
          apply();
        }}
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
        <button
          type="button"
          data-testid="candidate-reset"
          onClick={() => apply(true)}
        >
          초기화
        </button>
        <button className="sicost-primary" data-testid="candidate-query">
          조회
        </button>
      </form>
      <div className="candidate-toolbar">
        <strong>총 {data?.total ?? 0}건</strong>
        <div className="candidate-actions">
          <label>
            정렬
            <select
              data-testid="candidate-sort"
              value={filters.sort ?? ""}
              onChange={(e) => {
                setHighlight(undefined);
                setFilters((f) => ({
                  ...f,
                  sort: e.target.value || undefined,
                  page: 1,
                }));
              }}
            >
              <option value="">기본 정렬</option>
              {columns
                .filter((c) => !["skills", "homeAddress"].includes(c.id))
                .map((c) => (
                  <option
                    data-testid={`candidate-sort-option-${c.id.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
                    key={c.id}
                    value={c.id}
                  >
                    {c.label}
                  </option>
                ))}
            </select>
          </label>
          <button
            data-testid="candidate-direction"
            onClick={() => {
              setHighlight(undefined);
              setFilters((f) => ({
                ...f,
                page: 1,
                direction: f.direction === "DESC" ? "ASC" : "DESC",
              }));
            }}
          >
            {filters.direction === "DESC" ? "내림차순" : "오름차순"}
          </button>
          {onColumnsChange && (
            <button data-testid="candidate-columns" onClick={onColumnsChange}>
              열 설정
            </button>
          )}
          {onDensityChange && (
            <button data-testid="candidate-density" onClick={onDensityChange}>
              {dense ? "넓게 보기" : "좁게 보기"}
            </button>
          )}
          {!mobile && (
            <button
              data-testid="candidate-export"
              disabled={downloading || Boolean(committed)}
              onClick={async () => {
                setDownloading(true);
                setActionError(undefined);
                try {
                  await api.downloadCandidates(
                    filters,
                    visible.map((c) => c.id),
                    amountUnit,
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
          {loading && <p role="status">불러오는 중입니다.</p>}
          {!loading && !sortedRows.length && (
            <p className="sicost-empty">조회된 후보가 없습니다.</p>
          )}
          {sortedRows.map((r) => (
            <article
              data-testid={`candidate-card-${r.id.toLowerCase()}`}
              key={r.id}
            >
              {columns.map((c) => (
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
          empty="조회된 후보가 없습니다."
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
                disabled={Boolean(committed)}
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
            <p role="status">상세를 불러오는 중입니다.</p>
          ) : detailError != null ? (
            <ErrorBanner
              message="후보 상세를 불러오지 못했습니다."
              onRetry={() => void open(selected)}
            />
          ) : (
            detail && (
              <>
                <nav className="candidate-tabs" aria-label="후보 상세 탭">
                  {["기본 정보", "연락 기록", "변경 이력"].map((t) => (
                    <button
                      key={t}
                      data-testid={`candidate-tab-${t === "기본 정보" ? "basic" : t === "연락 기록" ? "contacts" : "history"}`}
                      aria-pressed={t === tab}
                      onClick={() => setTab(t)}
                    >
                      {t}
                    </button>
                  ))}
                </nav>
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
                      [
                        "희망 단가",
                        `${money(detail.desiredRate)} ${amountUnit}`,
                      ],
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
                    ].map(([k, v]) => (
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
