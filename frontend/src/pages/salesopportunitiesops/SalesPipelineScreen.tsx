import { recommendationEntryUrl } from "../workforcerecommendationsops/recommendationBrowseApi";
import { useOverlayLayer } from "../../shared/useOverlayLayer";
import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link, useLocation } from "react-router-dom";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { Pagination } from "../../shared/Pagination";
import { KpiStrip } from "../../shared/Surface";
import { StatusBadge } from "../../shared/Feedback";
import { Icon } from "../../shared/Icon";
import { DetailDrawer } from "../../shared/DetailDrawer";
import {
  formatAmount,
  formatFixedDecimal,
  formatShortDateTime,
  formatSummaryAmount,
  formatRate,
} from "../../shared/format";
import { SalesForecastPanel } from "../forecast/SalesForecastScreen";
import { SALES_COMMITTED } from "../assignmentcandidatesops/assignmentCandidatesApi";
import { SalesOpportunityActions, stageNames } from "./SalesOpportunityActions";
import { SalesQuotePanel, SalesCommandError } from "./SalesQuotePanel";
import * as api from "./salesCommandsApi";
import { SalesOpportunityForm } from "../salesopportunities/SalesOpportunityForm";
import { TentativeAssignmentsPanel } from "../tentativeassignmentsops/TentativeAssignmentsPanel";
import { getPeriodContext } from "../shared/sharedApi";
import { useCodeNames } from "../../shared/codeNames";
import { BusinessName } from "../../shared/BusinessName";
import "../apiops/projectAnalysis.css";
import "./salesCommands.css";

const pipelineTabs = [
  ["ING", "진행 중"],
  ["CLOSED", "종료 · 수주·실주"],
  ["FORECAST", "매출·인력 예측"],
] as const;

const pipelineGuide = (
  <dl>
    <div>
      <dt>진행 중인 영업 기회</dt>
      <dd>
        기회를 등록하고 단계별로 관리합니다. 카드를 끌어 놓거나 [단계 선택]에서
        단계를 바꿀 수 있습니다. 기회명을 누르면 필요 인력과 견적을 확인합니다.
      </dd>
    </div>
    <div>
      <dt>종료된 기회 조회</dt>
      <dd>
        수주·실주 결과를 선택하면 목록이 바로 바뀝니다. [사용 중지 포함]을
        켜면 사용 중지된 기회도 조회할 수 있습니다.
      </dd>
    </div>
    <div>
      <dt>매출·인력 예측</dt>
      <dd>
        다음 달부터 6개월간의 예상 매출, 필요·가용 인력과 채용 검토 근거를
        확인할 수 있습니다.
      </dd>
    </div>
  </dl>
);

export function SalesPipelineScreen({
  onDirtyChange,
}: { onDirtyChange?(dirty: boolean): void } = {}) {
  return (
    <SharedScreenFrame screenId="UI-29" screenGuide={pipelineGuide}>
      <SalesPipelinePanel onDirtyChange={onDirtyChange} />
    </SharedScreenFrame>
  );
}
export function SalesPipelinePanel({
  onDirtyChange,
}: { onDirtyChange?(dirty: boolean): void } = {}) {
  const name = useCodeNames(
    "JOB",
    "GRADE",
    "APRV_STAT",
    "OPP_STAT",
    "LOSE_RSN",
    "CTRT_TYPE",
    "APRV_ACT",
  );
  const location = useLocation();
  const [editing, setEditing] = useState<"new" | api.Opportunity>(),
    [highlight, setHighlight] = useState<string>(),
    [today, setToday] = useState<string>();
  useEffect(() => {
    let live = true;
    void getPeriodContext()
      .then((p) => {
        if (live) setToday(p.currentDate);
      })
      .catch(() => {});
    return () => {
      live = false;
    };
  }, []);
  useEffect(() => {
    if (!highlight) return;
    const timer = window.setTimeout(() => setHighlight(undefined), 6000);
    return () => window.clearTimeout(timer);
  }, [highlight]);
  const [tab, setTab] = useState<"ING" | "CLOSED" | "FORECAST">("ING"),
    [result, setResult] = useState<"WIN" | "LOSE" | "STOP">("WIN"),
    [includeStopped, setIncludeStopped] = useState(false);
  const [ordering, setOrdering] = useState<{
    sort: "amount" | "resultDate";
    direction: "ASC" | "DESC";
  }>();
  const [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(50),
    [data, setData] = useState<api.OpportunityPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const kanbanRef = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const board = kanbanRef.current;
    if (!board) return;

    const cards = Array.from(board.querySelectorAll(".sales-command-card"));
    const parts = cards.map((card) => Array.from(card.children) as HTMLElement[]);
    const syncHeight = () => {
      const height = Math.max(
        0,
        ...parts.map((children) =>
          children.reduce(
            (total, child) => total + child.getBoundingClientRect().height,
            0,
          ),
        ),
      );
      board.style.setProperty(
        "--sales-card-content-height",
        `${Math.ceil(height)}px`,
      );
    };
    syncHeight();
    if (typeof ResizeObserver === "undefined") return;

    // Observe the natural content so matching the outer height cannot affect the measurement.
    const observer = new ResizeObserver(syncHeight);
    parts.flat().forEach((part) => observer.observe(part));
    return () => observer.disconnect();
  }, [tab, data]);
  const [selectedId, setSelectedId] = useState<string | undefined>(
    (location.state as { opportunityId?: string } | null)?.opportunityId ??
      new URLSearchParams(location.search).get("opportunityId") ??
      undefined,
  );
  useEffect(() => {
    const id =
      (location.state as { opportunityId?: string } | null)?.opportunityId ??
      new URLSearchParams(location.search).get("opportunityId");
    if (id) {
      setSelectedId(id);
      setDetailTab("기본 정보");
    }
  }, [location.key, location.search]);
  const [detail, setDetail] = useState<api.Opportunity>(),
    [detailError, setDetailError] = useState<unknown>(),
    [detailTab, setDetailTab] = useState("기본 정보"),
    [generation, setGeneration] = useState(0);
  useEffect(() => {
    if (tab === "FORECAST") {
      setLoading(false);
      return;
    }
    let live = true;
    setLoading(true);
    setError(undefined);
    void api
      .getSalesOpportunities(
        tab === "ING" ? "ING" : result,
        tab === "CLOSED" && includeStopped,
        page,
        tab === "CLOSED" ? ordering : undefined,
        tab === "CLOSED" ? size : 50,
      )
      .then((v) => {
        if (live) setData(v);
      })
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [tab, result, includeStopped, page, size, generation, ordering]);
  useEffect(() => {
    let live = true;
    setDetail((previous) =>
      previous?.id === selectedId ? previous : undefined,
    );
    setDetailError(undefined);
    if (selectedId)
      void api
        .getSalesOpportunity(selectedId)
        .then((v) => {
          if (live) setDetail(v);
        })
        .catch((e) => {
          if (live) setDetailError(e);
        });
    return () => {
      live = false;
    };
  }, [selectedId, generation]);
  useEffect(() => {
    const changed = () => setGeneration((v) => v + 1);
    const names = [
      SALES_COMMITTED,
      "sicost:staffing-committed",
      "sicost:assignments-committed",
      "sicost:payroll-committed",
      "sicost:developers-committed",
      "sicost:codegroups-committed",
      "sicost:operating-standards-committed",
    ];
    names.forEach((name) => window.addEventListener(name, changed));
    return () =>
      names.forEach((name) => window.removeEventListener(name, changed));
  }, []);
  async function refresh() {
    const [list, value] = await Promise.all([
      api.getSalesOpportunities(
        tab === "ING" ? "ING" : result,
        tab === "CLOSED" && includeStopped,
        page,
        tab === "CLOSED" ? ordering : undefined,
        tab === "CLOSED" ? size : 50,
      ),
      selectedId
        ? api.getSalesOpportunity(selectedId)
        : Promise.resolve(undefined),
    ]);
    setData(list);
    setDetail(value);
    if (value && value.status !== "ING") {
      setTab("CLOSED");
      setResult(value.status);
      if (value.status === "STOP") setIncludeStopped(true);
    }
    if (value?.status === "ING" && tab === "CLOSED") setTab("ING");
  }
  async function drop(id: string, stage: api.Stage) {
    const row = data?.items.find((o) => o.id === id);
    if (!row) return;
    try {
      await api.saveSalesOpportunityStage(row, { stage });
      await refresh();
    } catch (e) {
      setError(e);
    }
  }
  function switchTab(next: typeof tab) {
    if (next === tab) return;
    setData(undefined);
    setTab(next);
    setPage(1);
  }
  const reloadList = () => setGeneration((value) => value + 1);
  const resetClosedFilters = () => {
    setResult("WIN");
    setIncludeStopped(false);
    setPage(1);
  };
  async function saved(result: api.Result) {
    const [value, list] = await Promise.all([
      api.getSalesOpportunity(result.opportunityId),
      api.getSalesOpportunities("ING", false, 1),
    ]);
    setData(list);
    setDetail(value);
    setSelectedId(undefined);
    setTab("ING");
    setPage(1);
    setHighlight(result.opportunityId);
    setEditing(undefined);
  }
  return (
    <section
      className="sales-command-screen"
      data-testid="sales-pipeline-screen"
      data-screen-id="SCR-UI-29"
      aria-busy={loading}
    >
      {editing && (
        <OpportunityModal>
          <SalesOpportunityForm
            detail={editing === "new" ? undefined : editing}
            onClose={() => setEditing(undefined)}
            onSaved={saved}
            onDirtyChange={onDirtyChange}
          />
        </OpportunityModal>
      )}
      <SegmentedTabList
        as="div"
        className="project-analysis-tabs"
        role="tablist"
        aria-label="영업 파이프라인 보기"
      >
        {pipelineTabs.map(([value, label]) => (
          <button
            type="button"
            id={`pipeline-tab-${value.toLowerCase()}`}
            data-testid={`pipeline-${value.toLowerCase()}-tab`}
            key={value}
            role="tab"
            aria-controls="pipeline-tab-content"
            aria-selected={tab === value}
            onClick={() => switchTab(value)}
          >
            {label}
          </button>
        ))}
      </SegmentedTabList>
      <div
        className="sales-pipeline-content"
        id="pipeline-tab-content"
        role="tabpanel"
        aria-labelledby={`pipeline-tab-${tab.toLowerCase()}`}
      >
        {tab === "FORECAST" ? (
          <SalesForecastPanel />
        ) : (
          <>
            {tab === "CLOSED" ? (
              <div
                className="sicost-filterbar sales-pipeline-filters"
                role="group"
                aria-label="종료 기회 조회 조건"
                data-testid="pipeline-filters"
              >
                <label className="sicost-field">
                  결과
                  <select
                    data-testid="pipeline-result-select"
                    value={result}
                    onChange={(e) => {
                      setResult(e.target.value as typeof result);
                      setPage(1);
                    }}
                  >
                    <option value="WIN">수주</option>
                    <option value="LOSE">실주</option>
                    {includeStopped && <option value="STOP">사용 중지</option>}
                  </select>
                </label>
                <label>
                  <input
                    data-testid="pipeline-include-stopped-input"
                    type="checkbox"
                    checked={includeStopped}
                    onChange={(e) => {
                      setIncludeStopped(e.target.checked);
                      if (!e.target.checked && result === "STOP")
                        setResult("WIN");
                      setPage(1);
                    }}
                  />
                  사용 중지 포함
                </label>
                <div className="sicost-filterbar-actions">
                  {(result !== "WIN" || includeStopped) && (
                    <button
                      type="button"
                      data-testid="pipeline-empty-reset"
                      onClick={resetClosedFilters}
                    >
                      초기화
                    </button>
                  )}
                  <button
                    type="button"
                    data-testid="pipeline-retry-button"
                    disabled={loading}
                    onClick={reloadList}
                  >
                    재조회
                  </button>
                </div>
              </div>
            ) : (
              <>
                {data && (
                  <KpiStrip className="sales-command-summary" label="진행 기회 요약">
                    <div>
                      <p>진행 기회</p>
                      <strong>{data.summary.opportunityCount}건</strong>
                      <StatusBadge tone={data.summary.staleCount ? "warn" : "lock"}>
                        14일 초과 미갱신 {data.summary.staleCount}건
                      </StatusBadge>
                    </div>
                    <div>
                      <p>예상 금액 합계</p>
                      <strong>{formatSummaryAmount(data.summary.amount)}</strong>
                    </div>
                    <div>
                      <p>확률 반영 금액</p>
                      <strong>{formatSummaryAmount(data.summary.weightedAmount)}</strong>
                    </div>
                    <div>
                      <p>확률 반영 필요 인력</p>
                      <strong>{formatFixedDecimal(data.summary.weightedPeople, 1)}명</strong>
                    </div>
                  </KpiStrip>
                )}
                <div className="sales-command-toolbar sales-pipeline-actions">
                  <button
                    type="button"
                    data-testid="pipeline-retry-button"
                    disabled={loading}
                    onClick={reloadList}
                  >
                    재조회
                  </button>
                  <button
                    type="button"
                    className="sicost-primary"
                    data-testid="pipeline-create-button"
                    onClick={() => setEditing("new")}
                  >
                    기회 등록
                  </button>
                </div>
              </>
            )}
            {loading && <p role="status">영업 기회를 불러오는 중입니다.</p>}
            {error != null && <SalesCommandError error={error} />}
            {data && (
              <>
                {tab === "ING" ? (
                  <div className="sales-command-kanban" ref={kanbanRef}>
                    {(Object.keys(stageNames) as api.Stage[]).map((stage) => (
                      <section
                        key={stage}
                        className="sales-command-column"
                        data-testid={`pipeline-column-${stage.toLowerCase()}`}
                        onDragOver={(e) => e.preventDefault()}
                        onDrop={(e) => {
                          e.preventDefault();
                          void drop(e.dataTransfer.getData("text/plain"), stage);
                        }}
                      >
                        <header className="sales-command-column-heading">
                          <h2>{stageNames[stage]}</h2>
                          {data.stageTotals
                            .filter((s) => s.stage === stage)
                            .map((s) => (
                              <p key={s.stage}>
                                <span>{s.count}건</span>
                                <strong>{formatAmount(s.amount, "원")} 원</strong>
                              </p>
                            ))}
                        </header>
                        {!loading &&
                          data.items.every((o) => o.stage !== stage) && (
                            <p className="sales-command-empty">기회 없음</p>
                          )}
                        {data.items
                          .filter((o) => o.stage === stage)
                          .map((o) => (
                            <article
                              key={o.id}
                              data-testid={`opportunity-card-${o.id.toLowerCase()}`}
                              className={`sales-command-card${highlight === o.id ? " sales-opportunity-highlight" : ""}`}
                              draggable
                              onDragStart={(e) =>
                                e.dataTransfer.setData("text/plain", o.id)
                              }
                            >
                              <div className="sales-opportunity-card-body">
                                <header className="sales-opportunity-card-heading">
                                  <button
                                    type="button"
                                    className="sicost-text-link"
                                    data-testid={`opportunity-open-${o.id.toLowerCase()}`}
                                    onClick={() => {
                                      setSelectedId(o.id);
                                      setDetailTab("기본 정보");
                                    }}
                                  >
                                    {o.name}
                                  </button>
                                  <div className="sales-opportunity-status">
                                    {o.approvalStatus !== "NONE" && (
                                      <span
                                        className={`sales-command-badge sales-command-${o.approvalStatus.toLowerCase()}`}
                                      >
                                        {name("APRV_STAT", o.approvalStatus)}
                                      </span>
                                    )}
                                    {isStale(o, today) && (
                                      <StatusBadge tone="warn">
                                        14일 초과 미갱신
                                      </StatusBadge>
                                    )}
                                  </div>
                                </header>
                                <p className="sales-opportunity-meta">
                                  <span>
                                    <BusinessName kind="client" id={o.customerId} />
                                  </span>
                                  <span>시작월 {o.startMonth}</span>
                                </p>
                                <dl className="sales-opportunity-metrics">
                                  <div className="sales-opportunity-amount">
                                    <dt>예상 금액</dt>
                                    <dd>{formatAmount(o.amount, "원")} 원</dd>
                                  </div>
                                  <div>
                                    <dt>수주 확률</dt>
                                    <dd>
                                      {formatRate(o.probability)}{" "}
                                      <small>
                                        {o.manualProbability ? "수동" : "기본"}
                                      </small>
                                    </dd>
                                  </div>
                                  <div
                                    data-testid={`opportunity-tentative-count-${o.id.toLowerCase()}`}
                                  >
                                    <dt>가배정</dt>
                                    <dd>
                                      {
                                        o.tentativeAssignments.filter(
                                          (t) => t.status === "ACT",
                                        ).length
                                      }
                                      건
                                    </dd>
                                  </div>
                                </dl>
                                {o.needs.length > 0 && (
                                  <p className="sales-opportunity-roles">
                                    <span>필요 직무</span>
                                    <strong>
                                      {o.needs
                                        .map((n) => name("JOB", n.jobCode))
                                        .join(" · ")}
                                    </strong>
                                  </p>
                                )}
                                <div className="sales-opportunity-next">
                                  {o.nextTask && (
                                    <span className="sales-opportunity-next-label">
                                      다음 할 일
                                    </span>
                                  )}
                                  <p>
                                    <strong>{o.nextTask ?? "다음 할 일 없음"}</strong>
                                    {o.nextDue && (
                                      <time
                                        dateTime={o.nextDue}
                                        className={
                                          today && o.nextDue < today
                                            ? "sales-opportunity-overdue"
                                            : undefined
                                        }
                                      >
                                        {o.nextDue}
                                      </time>
                                    )}
                                  </p>
                                </div>
                                <label className="sales-opportunity-stage">
                                  단계 선택
                                  <select
                                    data-testid={`card-stage-${o.id.toLowerCase()}`}
                                    value={o.stage}
                                    onChange={(e) =>
                                      void drop(o.id, e.target.value as api.Stage)
                                    }
                                  >
                                    {Object.entries(stageNames).map(([v, name]) => (
                                      <option key={v} value={v}>
                                        {name}
                                      </option>
                                    ))}
                                  </select>
                                </label>
                              </div>
                              <Link
                                className="sales-opportunity-recommend"
                                data-testid={`opportunity-recommend-${o.id.toLowerCase()}`}
                                to={recommendationEntryUrl({
                                  targetType: "OPPORTUNITY",
                                  targetId: o.id,
                                  jobCode: o.needs[0]?.jobCode ?? "",
                                  startMonth: o.startMonth,
                                })}
                                onClick={(event) => event.stopPropagation()}
                              >
                                인력 추천으로 이동
                                <Icon name="chevron" size={15} />
                              </Link>
                            </article>
                          ))}
                      </section>
                    ))}
                  </div>
                ) : (
                  <>
                    <div className="sales-command-toolbar sales-pipeline-actions">
                      <strong>총 {data.total}건</strong>
                    </div>
                    <div className="sicost-table-viewport">
                      <table className="sicost-table sales-pipeline-closed-table" aria-label="종료 기회 목록">
                        <thead>
                          <tr>
                            <th>기회명</th>
                            <th>고객</th>
                            <th>결과</th>
                            {(
                              [
                                ["amount", "예상액 (원)"],
                                ["resultDate", "결과일"],
                              ] as const
                            ).map(([key, title]) => {
                              const selected =
                                ordering?.sort === key ||
                                (!ordering && key === "resultDate");
                              const direction = ordering?.direction ?? "DESC";
                              return (
                                <th
                                  key={key}
                                  aria-sort={
                                    selected
                                      ? direction === "ASC"
                                        ? "ascending"
                                        : "descending"
                                      : "none"
                                  }
                                >
                                  <button
                                    type="button"
                                    className="sicost-sort-header"
                                    onClick={() => {
                                      setPage(1);
                                      setOrdering({
                                        sort: key,
                                        direction:
                                          selected && direction === "ASC"
                                            ? "DESC"
                                            : "ASC",
                                      });
                                    }}
                                  >
                                    {title}{" "}
                                    {selected
                                      ? direction === "ASC"
                                        ? "↑"
                                        : "↓"
                                      : "↕"}
                                  </button>
                                </th>
                              );
                            })}
                            <th>프로젝트 / 실주 사유</th>
                          </tr>
                        </thead>
                        <tbody>
                          {data.items.map((o) => (
                            <tr
                              key={o.id}
                              data-testid={`closed-row-${o.id.toLowerCase()}`}
                            >
                              <td>
                                <button
                                  type="button"
                                  className="sicost-text-link"
                                  data-testid={`closed-open-${o.id.toLowerCase()}`}
                                  onClick={() => setSelectedId(o.id)}
                                >
                                  {o.name}
                                </button>
                              </td>
                              <td>
                                <BusinessName kind="client" id={o.customerId} />
                              </td>
                              <td>{name("OPP_STAT", o.status)}</td>
                              <td className="sicost-decimal">{formatAmount(o.amount, "원")}</td>
                              <td>{o.resultDate ?? "–"}</td>
                              <td>
                                {o.projectId ? (
                                  <Link
                                    className="sicost-text-link"
                                    data-testid={`closed-project-${o.id.toLowerCase()}`}
                                    to={`/UI-01?${new URLSearchParams({ projectId: o.projectId })}`}
                                  >
                                    <BusinessName
                                      kind="project"
                                      id={o.projectId}
                                    />{" "}
                                    ({o.projectId})
                                  </Link>
                                ) : (
                                  `${name("LOSE_RSN", o.lossReasonCode)} ${o.lossMemo ?? ""}`
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                      {data.items.length === 0 && (
                        <div className="sales-command-empty">
                          <p>조건에 맞는 결과가 없습니다.</p>
                        </div>
                      )}
                    </div>
                  </>
                )}
                <div className="sales-pipeline-pagination">
                  <Pagination
                    label={tab === "ING" ? "진행 기회 페이지" : "종료 기회 페이지"}
                    testId="pipeline"
                    page={page}
                    size={tab === "CLOSED" ? size : data.size}
                    total={data.total}
                    disabled={loading}
                    onPageChange={setPage}
                    onSizeChange={
                      tab === "CLOSED"
                        ? (value) => {
                            setSize(value);
                            setPage(1);
                          }
                        : undefined
                    }
                  />
                </div>
              </>
            )}
          </>
        )}
      </div>
      {selectedId && (
        <DetailDrawer
          title={detail?.name ?? "영업 기회 상세"}
          testId="opportunity-detail-drawer"
          onClose={() => setSelectedId(undefined)}
        >
          {detailError != null && (
            <>
              <SalesCommandError error={detailError} />
              <button
                data-testid="detail-retry-button"
                onClick={() => setGeneration((v) => v + 1)}
              >
                재조회
              </button>
            </>
          )}
          {!detail && !detailError && (
            <p role="status">상세를 불러오는 중입니다.</p>
          )}
          {detail && (
            <>
              {detail.status === "ING" && (
                <button
                  data-testid="opportunity-edit-button"
                  onClick={() => {
                    setEditing(detail);
                    setSelectedId(undefined);
                  }}
                >
                  수정
                </button>
              )}
              <SalesOpportunityActions
                key={detail.id}
                opportunity={detail}
                refresh={refresh}
              />
              <SegmentedTabList
                as="div"
                className="sales-command-tabs"
                role="tablist"
                aria-label="기회 상세 탭"
              >
                {["기본 정보", "필요 인력", "견적 점검/승인 이력"].map((t) => (
                  <button
                    role="tab"
                    data-testid={`detail-tab-${t === "기본 정보" ? "base" : t === "필요 인력" ? "needs" : "quote"}`}
                    key={t}
                    aria-selected={detailTab === t}
                    onClick={() => setDetailTab(t)}
                  >
                    {t}
                  </button>
                ))}
              </SegmentedTabList>
              <div role="tabpanel" data-testid="opportunity-detail-panel">
                {detailTab === "기본 정보" && (
                  <>
                    <p>
                      고객 <BusinessName kind="client" id={detail.customerId} />{" "}
                      · 영업파트 <BusinessName kind="part" id={detail.partId} />{" "}
                      · {name("CTRT_TYPE", detail.contractType)}
                    </p>
                    <p>
                      {detail.startMonth} · {detail.months ?? "–"}개월 ·{" "}
                      {detail.workplace ?? "–"}
                    </p>
                    <p>{detail.memo}</p>
                    {detail.approvalStatus === "REJ" && (
                      <div className="sales-command-error">
                        {
                          detail.approvalHistory
                            .filter((h) => h.action === "REJ")
                            .at(-1)?.reason
                        }
                      </div>
                    )}
                  </>
                )}
                {detailTab === "필요 인력" && (
                  <>
                    <div className="sicost-table-viewport">
                      <table
                        className="sicost-table"
                        data-testid="opportunity-needs-table"
                      >
                        <thead>
                          <tr>
                            {[
                              "직무",
                              "등급",
                              "인원",
                              "시작월",
                              "개월",
                              "월 MM",
                              "예상 단가 (원)",
                            ].map((label) => (
                              <th key={label}>{label}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {detail.needs.map((n, i) => (
                            <tr
                              data-testid={`detail-need-${n.needSn ?? i}`}
                              key={n.needSn ?? i}
                            >
                              <td>{name("JOB", n.jobCode)}</td>
                              <td>
                                {n.gradeCode
                                  ? name("GRADE", n.gradeCode)
                                  : "등급 미지정"}
                              </td>
                              <td>{n.count}</td>
                              <td>{n.startMonth}</td>
                              <td>{n.months}</td>
                              <td>{formatFixedDecimal(n.monthlyMm, 2)}</td>
                              <td>
                                {n.expectedRate == null
                                  ? "직무 기본단가"
                                  : formatAmount(n.expectedRate, "원")}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                      {detail.needs.length === 0 && (
                        <p>필요 인력이 없습니다.</p>
                      )}
                    </div>
                    <TentativeAssignmentsPanel
                      items={detail.tentativeAssignments.filter(
                        (t) => t.status === "ACT",
                      )}
                      needs={detail.needs}
                      revision={detail.revision}
                      opportunityStatus={detail.status}
                      onRefresh={refresh}
                    />
                  </>
                )}
                {detailTab === "견적 점검/승인 이력" && (
                  <>
                    <SalesQuotePanel
                      key={detail.revision}
                      needs={detail.needs}
                      initial={detail.quote}
                    />
                    <h3>승인 이력</h3>
                    {detail.approvalHistory.length === 0 && (
                      <p>승인 이력이 없습니다.</p>
                    )}
                    {detail.approvalHistory.map((h) => (
                      <article
                        data-testid={`approval-history-${h.histSn}`}
                        key={h.histSn}
                      >
                        {formatShortDateTime(h.registeredAt)} ·{" "}
                        {name("APRV_ACT", h.action)} · {h.profitRate}% ·{" "}
                        {h.reason} · {h.registeredBy}
                      </article>
                    ))}
                  </>
                )}
              </div>
            </>
          )}
        </DetailDrawer>
      )}
    </section>
  );
}
function isStale(opportunity: api.Opportunity, today?: string) {
  const row = opportunity as api.Opportunity & {
    registeredAt?: string;
    modifiedAt?: string | null;
  };
  const timestamp = row.modifiedAt ?? row.registeredAt;
  if (!today || !timestamp) return false;
  const threshold = new Date(`${today}T00:00:00Z`);
  threshold.setUTCDate(threshold.getUTCDate() - 14);
  return timestamp.slice(0, 10) < threshold.toISOString().slice(0, 10);
}

function OpportunityModal({ children }: { children: React.ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const topmost = useOverlayLayer(ref, true, 55);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    if (topmost()) ref.current?.focus();
    return () => previous?.focus();
  }, []);
  return createPortal(
    <div className="sicost-scrim sales-opportunity-scrim">
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-label="영업 기회 입력"
        tabIndex={-1}
        className="sicost-dialog sicost-dialog-page sales-opportunity-dialog"
        onKeyDown={(event) => {
          if (!topmost()) return;
          if (!ref.current?.contains(event.target as Node)) return;
          if (event.key === "Escape") {
            event.preventDefault();
            ref.current
              ?.querySelector<HTMLButtonElement>(
                '[data-testid="opportunity-form-back"]',
              )
              ?.click();
          }
          if (event.key === "Tab") {
            const nodes = [
              ...(ref.current?.querySelectorAll<HTMLElement>(
                "button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]",
              ) ?? []),
            ].filter((node) => node.getClientRects().length > 0);
            const first = nodes[0],
              last = nodes[nodes.length - 1];
            if (!first) {
              event.preventDefault();
              ref.current?.focus();
            } else if (
              event.shiftKey &&
              (document.activeElement === first ||
                document.activeElement === ref.current)
            ) {
              event.preventDefault();
              last?.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
              event.preventDefault();
              first.focus();
            }
          }
        }}
      >
        {children}
      </div>
    </div>,
    document.body,
  );
}
