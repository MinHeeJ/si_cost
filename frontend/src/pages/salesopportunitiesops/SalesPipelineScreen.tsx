import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link, useLocation } from "react-router-dom";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { FilterBar } from "../../shared/FilterBar";
import { DetailDrawer } from "../../shared/DetailDrawer";
import {
  formatAmount,
  formatFixedDecimal,
  formatShortDateTime,
} from "../../shared/format";
import { SalesForecastPanel } from "../forecast/SalesForecastScreen";
import { SALES_COMMITTED } from "../assignmentcandidatesops/assignmentCandidatesApi";
import { SalesOpportunityActions, stageNames } from "./SalesOpportunityActions";
import { SalesQuotePanel, SalesCommandError } from "./SalesQuotePanel";
import * as api from "./salesCommandsApi";
import { SalesOpportunityForm } from "../salesopportunities/SalesOpportunityForm";
import { getPeriodContext } from "../shared/sharedApi";
import "./salesCommands.css";

export function SalesPipelineScreen() {
  return (
    <SharedScreenFrame screenId="UI-29">
      <SalesPipelinePanel />
    </SharedScreenFrame>
  );
}
export function SalesPipelinePanel() {
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
    [data, setData] = useState<api.OpportunityPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
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
        includeStopped,
        page,
        tab === "CLOSED" ? ordering : undefined,
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
  }, [tab, result, includeStopped, page, generation, ordering]);
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
        includeStopped,
        page,
        tab === "CLOSED" ? ordering : undefined,
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
    setTab(next);
    setPage(1);
  }
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
          />
        </OpportunityModal>
      )}
      <div className="sales-command-toolbar">
        <button
          className="sicost-primary"
          data-testid="pipeline-create-button"
          onClick={() => setEditing("new")}
        >
          기회 등록
        </button>
      </div>
      <div
        className="sales-command-tabs"
        role="tablist"
        aria-label="영업 파이프라인 보기"
      >
        {(
          [
            ["ING", "진행 중"],
            ["CLOSED", "종료 · 수주·실주"],
            ["FORECAST", "매출·인력 예측"],
          ] as const
        ).map(([value, label]) => (
          <button
            data-testid={`pipeline-${value.toLowerCase()}-tab`}
            key={value}
            role="tab"
            aria-selected={tab === value}
            onClick={() => switchTab(value)}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === "FORECAST" ? (
        <SalesForecastPanel />
      ) : (
        <>
          <FilterBar
            onSubmit={() => setGeneration((v) => v + 1)}
            onReset={() => {
              setResult("WIN");
              setIncludeStopped(false);
              setPage(1);
              setGeneration((v) => v + 1);
            }}
          >
            <button
              type="button"
              data-testid="pipeline-retry-button"
              disabled={loading}
              onClick={() => setGeneration((v) => v + 1)}
            >
              재조회
            </button>
            {tab === "CLOSED" && (
              <>
                <label>
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
              </>
            )}
          </FilterBar>
          {loading && <p role="status">영업 기회를 불러오는 중입니다.</p>}
          {error != null && <SalesCommandError error={error} />}
          {data && (
            <>
              <dl className="sales-command-summary sicost-kpi-strip">
                <div>
                  <dt>진행 기회</dt>
                  <dd>{data.summary.opportunityCount}건</dd>
                  <p>14일 초과 미갱신 {data.summary.staleCount}건</p>
                </div>
                <div>
                  <dt>예상 금액 합계</dt>
                  <dd>{formatAmount(data.summary.amount, "천원")} 천원</dd>
                </div>
                <div>
                  <dt>확률 반영 금액</dt>
                  <dd>
                    {formatAmount(data.summary.weightedAmount, "천원")} 천원
                  </dd>
                </div>
                <div>
                  <dt>확률 반영 필요 인력</dt>
                  <dd>
                    {formatFixedDecimal(data.summary.weightedPeople, 1)}명
                  </dd>
                </div>
              </dl>
              {tab === "ING" ? (
                <div className="sales-command-kanban">
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
                      <h2>{stageNames[stage]}</h2>
                      {data.stageTotals
                        .filter((s) => s.stage === stage)
                        .map((s) => (
                          <p key={s.stage}>
                            {s.count}건 · {formatAmount(s.amount, "천원")} 천원
                          </p>
                        ))}
                      {!loading &&
                        data.items.every((o) => o.stage !== stage) && (
                          <div className="sales-command-empty">
                            <p>기회 없음</p>
                            <button
                              type="button"
                              onClick={() => {
                                setPage(1);
                                setGeneration((v) => v + 1);
                              }}
                            >
                              조건 초기화
                            </button>
                          </div>
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
                            <button
                              data-testid={`opportunity-open-${o.id.toLowerCase()}`}
                              onClick={() => {
                                setSelectedId(o.id);
                                setDetailTab("기본 정보");
                              }}
                            >
                              {o.name}
                            </button>
                            <p>
                              {o.customerId} · {o.startMonth}
                            </p>
                            <strong>
                              {formatAmount(o.amount, "천원")} 천원
                            </strong>
                            <p>
                              확률 {o.probability}% ·{" "}
                              {o.manualProbability ? "수동" : "기본"}
                            </p>
                            <p>{o.needs.map((n) => n.jobCode).join(" · ")}</p>
                            <span
                              className={`sales-command-badge sales-command-${o.approvalStatus.toLowerCase()}`}
                            >
                              {o.approvalStatus}
                            </span>
                            <p
                              className={
                                today && o.nextDue && o.nextDue < today
                                  ? "sales-opportunity-overdue"
                                  : ""
                              }
                            >
                              {o.nextTask ?? "다음 할 일 없음"} {o.nextDue}
                            </p>
                            {isStale(o, today) && (
                              <span className="sales-command-badge sales-command-wait">
                                14일 초과 미갱신
                              </span>
                            )}
                            <label>
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
                            <Link
                              data-testid={`opportunity-recommend-${o.id.toLowerCase()}`}
                              to="/UI-30"
                              state={{
                                opportunityId: o.id,
                                jobCode: o.needs[0]?.jobCode,
                                startMonth: o.startMonth,
                              }}
                            >
                              인력 추천
                            </Link>
                          </article>
                        ))}
                    </section>
                  ))}
                </div>
              ) : (
                <div className="sicost-table-viewport">
                  <table className="sicost-table">
                    <thead>
                      <tr>
                        <th>기회명</th>
                        <th>고객</th>
                        <th>결과</th>
                        {(
                          [
                            ["amount", "예상액 (천원)"],
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
                              data-testid={`closed-open-${o.id.toLowerCase()}`}
                              onClick={() => setSelectedId(o.id)}
                            >
                              {o.name}
                            </button>
                          </td>
                          <td>{o.customerId}</td>
                          <td>{o.status}</td>
                          <td>{formatAmount(o.amount, "천원")}</td>
                          <td>{o.resultDate ?? "–"}</td>
                          <td>
                            {o.projectId ? (
                              <Link
                                data-testid={`closed-project-${o.id.toLowerCase()}`}
                                to="/UI-01"
                                state={{ projectId: o.projectId }}
                              >
                                {o.projectId}
                              </Link>
                            ) : (
                              `${o.lossReasonCode ?? "–"} ${o.lossMemo ?? ""}`
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {data.items.length === 0 && (
                    <div className="sales-command-empty">
                      <p>종료 기회 없음</p>
                      <button
                        type="button"
                        onClick={() => {
                          setResult("WIN");
                          setIncludeStopped(false);
                          setPage(1);
                          setGeneration((v) => v + 1);
                        }}
                      >
                        조건 초기화
                      </button>
                    </div>
                  )}
                </div>
              )}
              <div className="sales-command-toolbar">
                <button
                  data-testid="pipeline-previous-button"
                  disabled={page <= 1 || loading}
                  onClick={() => setPage((v) => v - 1)}
                >
                  이전
                </button>
                <span>
                  {page}페이지 · 전체 {data.total}건
                </span>
                <button
                  data-testid="pipeline-next-button"
                  disabled={page * data.size >= data.total || loading}
                  onClick={() => setPage((v) => v + 1)}
                >
                  다음
                </button>
              </div>
            </>
          )}
        </>
      )}
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
              <div
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
              </div>
              <div role="tabpanel" data-testid="opportunity-detail-panel">
                {detailTab === "기본 정보" && (
                  <>
                    <p>
                      고객 {detail.customerId} · 영업파트 {detail.partId} ·{" "}
                      {detail.contractType ?? "–"}
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
                {detailTab === "필요 인력" &&
                  detail.needs.map((n, i) => (
                    <article
                      data-testid={`detail-need-${n.needSn ?? i}`}
                      key={n.needSn ?? i}
                    >
                      <p>
                        {n.jobCode} / {n.gradeCode ?? "등급 미지정"} · {n.count}
                        명 · {n.startMonth}부터 {n.months}개월 · 월{" "}
                        {n.monthlyMm} MM
                      </p>
                      <p>예상단가 {n.expectedRate ?? "직무 기본단가"}</p>
                    </article>
                  ))}
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
                        {formatShortDateTime(h.registeredAt)} · {h.action} ·{" "}
                        {h.profitRate}% · {h.reason} · {h.registeredBy}
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
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    ref.current?.focus();
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
        className="sicost-dialog sales-opportunity-dialog"
        onKeyDown={(event) => {
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
