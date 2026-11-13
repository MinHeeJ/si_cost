import { useEffect, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatAmount, type AmountUnit } from "../../shared/format";
import { DeveloperDrawer } from "../developers/DeveloperDrawer";
import { CostMetrics, CostSourceIssues } from "./ProjectCostSummaryPanel";
import { ProjectCostSimulationPanel } from "./ProjectCostSimulationPanel";
import * as api from "./projectCostApi";
import "./projectCost.css";
const tabs = [
  ["months", "월별 원가"],
  ["assignments", "투입 인력"],
  ["causes", "원가 초과 원인"],
  ["simulation", "시뮬레이션"],
] as const;
export type ProjectCostDetailPanelProps = {
  projectId: string;
  month?: string;
  amountUnit?: AmountUnit;
  onClose(): void;
  initialTab?: string;
  onDirtyChange?(dirty: boolean): void;
};
export function ProjectCostDetailPanel({
  projectId,
  month,
  amountUnit = "천원",
  onClose,
  initialTab = "months",
  onDirtyChange,
}: ProjectCostDetailPanelProps) {
  const [search, setSearch] = useSearchParams();
  const location = useLocation();
  const requested = search.get("costTab") ?? initialTab;
  const tab = tabs.some(([key]) => key === requested) ? requested : "months";
  const [detail, setDetail] = useState<api.Detail>();
  const [months, setMonths] = useState<api.Months>();
  const [assignments, setAssignments] = useState<api.Assignments>();
  const [causes, setCauses] = useState<api.Causes>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [dirty, setDirty] = useState(false);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  const [leaving, setLeaving] = useState(false);
  const [person, setPerson] = useState<string>();
  useEffect(() => {
    let live = true;
    setLoading(true);
    setError(undefined);
    setDetail(undefined);
    setMonths(undefined);
    setAssignments(undefined);
    setCauses(undefined);
    async function load() {
      const value = await api.getProjectCostAnalysis(projectId, month);
      if (!live) return;
      setDetail(value);
      if (tab === "months") {
        const data = await api.getProjectCostMonths(projectId, value.month);
        if (live) setMonths(data);
      }
      if (tab === "assignments") {
        const data = await api.getProjectCostAssignments(
          projectId,
          value.month,
        );
        if (live) setAssignments(data);
      }
      if (tab === "causes") {
        const data = await api.getProjectCostCauses(projectId, value.month);
        if (live) setCauses(data);
      }
    }
    void load()
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [projectId, month, tab, attempt]);
  const selectTab = (key: string) => {
    const next = new URLSearchParams(search);
    next.set("costTab", key);
    setSearch(next, { replace: true, state: location.state });
  };
  const money = (value: string | null) => formatAmount(value, amountUnit);
  const monthlyColumns: Column<api.MonthlyRow>[] = [
    { id: "month", label: "월", render: (r) => r.month },
    { id: "mm", label: "MM", render: (r) => r.actualMm },
    ...(
      [
        ["laborAllocated", "노동비"],
        ["teamExpense", "팀비"],
        ["projectExpense", "경비"],
        ["additionalDirectExpense", "추가경비"],
        ["totalCost", "합계"],
      ] as const
    ).map(([id, label]) => ({
      id,
      label,
      align: "right" as const,
      render: (r: api.MonthlyRow) => money(r[id]),
    })),
    {
      id: "revenue",
      label:
        detail?.row.contractType === "TK" ? "배분 기준 매출(참고)" : "매출참고",
      align: "right",
      render: (r) =>
        money(
          detail?.row.contractType === "TK"
            ? r.turnkeyReferenceRevenue
            : r.revenue,
        ),
    },
    {
      id: "source",
      label: "근거",
      render: (r) => (
        <>
          {r.fixed && <StatusBadge tone="lock">고정</StatusBadge>}
          {r.completeness.estimated && (
            <StatusBadge tone="info">예정</StatusBadge>
          )}
          <CostSourceIssues issues={r.completeness.issues} />
        </>
      ),
    },
  ];
  const assignmentColumns: Column<api.AssignmentRow>[] = [
    { id: "month", label: "월", render: (r) => r.month },
    {
      id: "person",
      label: "실제 투입자",
      render: (r) => (
        <button
          data-testid={`cost-person-${r.month}-${r.assignmentId.toLowerCase()}`}
          onClick={() => setPerson(r.personId)}
        >
          {r.personName} · {r.personId}
        </button>
      ),
    },
    { id: "assignment", label: "배정", render: (r) => r.assignmentId },
    { id: "job", label: "직무", render: (r) => r.jobName },
    {
      id: "period",
      label: "기간",
      render: (r) => `${r.startDate} ~ ${r.endDate}`,
    },
    { id: "mm", label: "MM", render: (r) => r.assignmentMm },
    { id: "freeMm", label: "무상 MM", render: (r) => r.freeMm },
    ...(
      [
        ["laborAllocated", "노동비"],
        ["directExpense", "경비"],
        ["personalPrice", "개인 단가(참고)"],
      ] as const
    ).map(([id, label]) => ({
      id,
      label,
      align: "right" as const,
      render: (r: api.AssignmentRow) => money(r[id]),
    })),
    {
      id: "type",
      label: "유상·무상",
      render: (r) => (
        <>
          {r.inputType === "FREE" ? "무상" : "유상"}
          {r.planned && <StatusBadge tone="info">예정</StatusBadge>}
          <CostSourceIssues issues={r.completeness.issues} />
        </>
      ),
    },
  ];
  return (
    <>
      <DetailDrawer
        title={detail?.row.projectName ?? "프로젝트 원가 상세"}
        subtitle={`${projectId} · ${detail?.month ?? month ?? "현재월"} · 금액 단위 ${amountUnit}`}
        onClose={() => (dirty ? setLeaving(true) : onClose())}
        testId="project-cost-detail"
      >
        <section
          data-testid="cost-detail-content"
          data-screen-id="SCR-UI-04-B138-CAUSE-NAV"
        >
          {detail && (
            <>
              <CostMetrics detail={detail} unit={amountUnit} />
              <Link
                data-testid="cost-project-information"
                to={`/UI-01?${new URLSearchParams({ projectId, tab: "basic", month: detail.month })}`}
                state={{ returnTo: location.pathname + location.search }}
              >
                프로젝트 정보
              </Link>
            </>
          )}
          <nav role="tablist" aria-label="원가 상세 영역" className="cost-tabs">
            {tabs.map(([key, label]) => (
              <button
                type="button"
                key={key}
                data-testid={`cost-tab-${key}`}
                role="tab"
                aria-selected={tab === key}
                disabled={dirty && tab !== key}
                onClick={() => selectTab(key)}
              >
                {label}
              </button>
            ))}
          </nav>
          {loading && <p role="status">원가 상세를 불러오는 중입니다.</p>}
          {error != null && (
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "원가 상세를 조회하지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={() => setAttempt((n) => n + 1)}
            />
          )}
          <section role="tabpanel" data-testid={`cost-panel-${tab}`}>
            {tab === "months" && months && (
              <DataTable
                testId="cost-months"
                rows={months.items}
                columns={monthlyColumns}
                rowKey={(r) => r.month}
              />
            )}
            {tab === "assignments" && assignments && (
              <DataTable
                testId="cost-assignments"
                rows={assignments.items}
                columns={assignmentColumns}
                rowKey={(r) => `${r.month}-${r.personId}-${r.assignmentId}`}
              />
            )}
            {tab === "causes" && causes && (
              <>
                <p
                  className={`sicost-banner sicost-tone-${causes.highlight ? "bad" : "neutral"}`}
                >
                  {causes.message}
                </p>
                <p>
                  진척 대비 초과 {money(causes.progressExcess)} · 나머지{" "}
                  {money(causes.remainder)} {amountUnit}
                </p>
                <DataTable
                  testId="cost-causes"
                  rows={causes.items}
                  rowKey={(r) => r.kind}
                  columns={[
                    {
                      id: "cause",
                      label: "원인",
                      render: (r) =>
                        ({
                          EXCESS_MM: "투입이 계획보다 많음",
                          OVER_UNIT_COST: "원가가 단가 기준을 넘음",
                          FREE_SUPPORT: "무상지원",
                        })[r.kind],
                    },
                    {
                      id: "amount",
                      label: `금액 (${amountUnit})`,
                      align: "right",
                      render: (r) => money(r.amount),
                    },
                    {
                      id: "formula",
                      label: "계산식",
                      render: (r) => (
                        <>
                          {r.formula}
                          <CostSourceIssues issues={r.issues} />
                        </>
                      ),
                    },
                    {
                      id: "related",
                      label: "관련 내역",
                      render: (r) =>
                        r.kind === "FREE_SUPPORT" ? (
                          <button
                            data-testid="cost-free-assignments"
                            onClick={() => selectTab("assignments")}
                          >
                            투입 인력
                          </button>
                        ) : (
                          <>
                            <Link
                              data-testid={`cost-cause-${r.kind.toLowerCase().replaceAll("_", "-")}`}
                              to={`/${r.targetScreenId}?${new URLSearchParams({ projectId, ...(r.targetScreenId === "UI-01" ? { tab: "plans" } : {}) })}`}
                              state={{
                                returnTo: location.pathname + location.search,
                              }}
                            >
                              {r.targetScreenId === "UI-01"
                                ? "인원 계획"
                                : "원가 검토"}
                            </Link>
                            {r.kind === "EXCESS_MM" && (
                              <Link
                                data-testid="cost-demand-link"
                                to={`/UI-11?${new URLSearchParams({ projectId })}`}
                              >
                                인력 수요
                              </Link>
                            )}
                            {r.targetScreenId === "UI-32" && (
                              <small> 후속 화면 준비 안내에서 확인</small>
                            )}
                          </>
                        ),
                    },
                  ]}
                />
                <CostSourceIssues issues={causes.issues} />
              </>
            )}
            {tab === "simulation" && detail && (
              <ProjectCostSimulationPanel
                key={`${projectId}-${detail.month}-${attempt}`}
                detail={detail}
                onDirtyChange={setDirty}
                onReload={() => {
                  setDirty(false);
                  setAttempt((n) => n + 1);
                }}
              />
            )}
          </section>
          {detail?.forecast && (
            <details>
              <summary>남은 예상 원가 근거</summary>
              <p>
                {detail.forecast.personCount}명 · {detail.forecast.remainingMm}{" "}
                MM · 알려진 금액 {money(detail.forecast.knownRemainingCost)} ·
                단가 없음 {detail.forecast.missingUnitCostPersonCount}명
              </p>
              <ul>
                {detail.forecast.people.map((p) => (
                  <li
                    key={p.personId}
                    data-testid={`cost-forecast-${p.personId.toLowerCase()}`}
                  >
                    {p.personName} · {p.remainingMm} MM · {p.unitCostSource} ·{" "}
                    {p.sourceMonth} · {money(p.contribution)}
                  </li>
                ))}
              </ul>
              <CostSourceIssues issues={detail.forecast.issues} />
            </details>
          )}
        </section>
      </DetailDrawer>
      <Dialog
        open={leaving}
        title="계산하지 않은 입력이 있습니다."
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
        cancelLabel="계속 작성"
        confirmLabel="나가기"
      >
        <p>시뮬레이션 입력을 버리고 닫습니다. 업무 원천은 변경되지 않습니다.</p>
      </Dialog>
      {person && (
        <DeveloperDrawer id={person} onClose={() => setPerson(undefined)} />
      )}
    </>
  );
}
