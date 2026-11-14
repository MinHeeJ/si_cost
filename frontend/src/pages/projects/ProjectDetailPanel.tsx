import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { DetailDrawer } from "../../shared/DetailDrawer";

import { DataTable } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { ProjectBudgetDialog } from "../budget/ProjectBudgetDialog";
import { ProjectPlansPanel } from "../plans/ProjectPlansPanel";
import { ProjectExpensesPanel } from "../expenses/ProjectExpensesPanel";
import { projectAmount, projectStatusTone } from "./projectFormat";
import * as api from "./projectsApi";
export type ProjectDetailContext = {
  detail: api.ProjectDetail;
  year: number;
  currentYear: number;
  readOnly: boolean;
  currentDate: string;
  amountUnit: "원" | "천원" | "백만원";
  onChanged(): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
};
export type ProjectDetailRegistration = {
  activeTab?: string;
  onTabChange?(tab: string): void;
  partAction?(context: ProjectDetailContext): ReactNode;
  statusActions?(context: ProjectDetailContext): ReactNode;
  costSummary?(context: ProjectDetailContext): ReactNode;
  progress?(context: ProjectDetailContext): ReactNode;
  issues?(context: ProjectDetailContext): ReactNode;
  issuesTitle?(context: ProjectDetailContext): ReactNode;
};
type Props = {
  active?: boolean;
  id: string;
  year: number;
  currentYear: number;
  currentDate: string;
  customerName?: string | null;
  amountUnit?: "원" | "천원" | "백만원";
  labels(group: string, code: string): string;
  onClose(): void;
  onEdit(detail: api.ProjectDetail): void;
  onDistribution(detail: api.ProjectDetail): void;
  onChanged(): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
  registration?: ProjectDetailRegistration;
};
export function ProjectDetailPanel({
  active = true,
  id,
  year,
  currentYear,
  currentDate,
  customerName,
  amountUnit = "천원",
  labels,
  onClose,
  onEdit,
  onDistribution,
  onChanged,
  onDirtyChange,
  registration,
}: Props) {
  const [detail, setDetail] = useState<api.ProjectDetail>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true);
  const [localTab, setLocalTab] = useState("basic");
  const requestedTab = registration?.activeTab ?? localTab;
  const tab = [
    "basic",
    "plans",
    "assignments",
    "history",
    ...(registration?.progress ? ["progress"] : []),
    ...(registration?.issues ? ["issues"] : []),
  ].includes(requestedTab)
    ? requestedTab
    : "basic";
  const setTab = (value: string) => {
    setLocalTab(value);
    registration?.onTabChange?.(value);
  };
  const [cancelled, setCancelled] = useState(false),
    [budget, setBudget] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const dirtyRef = useRef(false);
  const childDirty = useCallback(
    (value: boolean) => {
      dirtyRef.current = value;
      setDirty(value);
      onDirtyChange?.(value);
    },
    [onDirtyChange],
  );
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);
  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setDetail(await api.getProject(id, cancelled));
    } catch (e) {
      setError(e);
      throw e;
    } finally {
      setLoading(false);
    }
  }, [id, cancelled]);
  useEffect(() => {
    let live = true;
    setLoading(true);
    setError(undefined);
    void api
      .getProject(id, cancelled)
      .then((v) => {
        if (live) setDetail(v);
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
  }, [id, cancelled]);
  async function refresh() {
    await Promise.all([load(), onChanged()]);
    api.notifyProjectsCommitted(id);
  }
  function close() {
    if (dirtyRef.current) setLeaving(true);
    else onClose();
  }
  const p = detail?.project,
    locked = !detail || detail.readOnly || year < currentYear;
  const context: ProjectDetailContext | undefined = detail && {
    detail,
    year,
    currentYear,
    currentDate,
    amountUnit,
    readOnly: locked,
    onChanged: refresh,
    onDirtyChange: childDirty,
  };
  const money = (value: string | null) =>
    `${projectAmount(value, amountUnit)} ${amountUnit}`;
  const currentParts =
    detail?.parts.filter(
      (part) => part.effectiveMonth <= currentDate.slice(0, 7),
    ) ?? [];
  const part = currentParts[currentParts.length - 1] ?? detail?.parts[0];
  const todayPeople = detail
    ? new Set(
        detail.assignments
          .filter(
            (a) =>
              a.status === "ACT" &&
              a.startDate <= currentDate &&
              a.endDate >= currentDate,
          )
          .map((a) => a.developerId),
      ).size
    : undefined;
  return (
    <>
      <DetailDrawer
        hidden={!active}
        title={p?.name ?? "프로젝트 상세"}
        subtitle={
          p
            ? `${p.id} · ${customerName ?? "–"} · ${labels("CTRT_TYPE", p.contractType)}`
            : undefined
        }
        testId="project-detail"
        onClose={close}
        actions={
          p && (
            <>
              <button
                type="button"
                data-testid="project-detail-edit"
                disabled={locked || dirty || loading}
                onClick={() => detail && onEdit(detail)}
              >
                수정
              </button>
              {p.contractType === "TK" && (
                <button
                  type="button"
                  data-testid="project-detail-distribution"
                  disabled={dirty}
                  onClick={() => detail && onDistribution(detail)}
                >
                  배분표
                </button>
              )}
              {context &&
                !locked &&
                detail?.effectiveStatus === "PRE" &&
                registration?.statusActions?.(context)}
            </>
          )
        }
      >
        {loading && <p role="status">상세를 불러오는 중입니다.</p>}
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "상세를 조회하지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => void load().catch(() => {})}
          />
        )}
        {detail && p && (
          <section
            data-testid="project-detail-content"
            data-screen-id="SCR-UI-01"
          >
            {locked && (
              <p className="sicost-banner sicost-tone-lock">
                실주 또는 지난 연도는 조회만 할 수 있습니다.
              </p>
            )}
            <nav
              role="tablist"
              aria-label="프로젝트 상세 영역"
              className="project-tabs"
            >
              {[
                ["basic", "기본 정보"],
                ["plans", "인원 계획"],
                ["assignments", "투입 인력"],
                ["history", "계약 변경 이력"],
                ...(registration?.progress ? [["progress", "진행 현황"]] : []),
                ...(registration?.issues ? [["issues", "이슈"]] : []),
              ].map(([key, label]) => (
                <button
                  type="button"
                  role="tab"
                  key={key}
                  data-testid={`project-tab-${key}`}
                  aria-selected={tab === key}
                  disabled={dirty && tab !== key}
                  onClick={() => setTab(key)}
                >
                  {key === "issues" && context && registration?.issuesTitle ? registration.issuesTitle(context) : label}
                </button>
              ))}
            </nav>
            <div role="tabpanel" data-testid={`project-panel-${tab}`}>
              {tab === "progress" &&
                context &&
                registration?.progress?.(context)}
              {tab === "issues" && context && registration?.issues?.(context)}
              {tab === "basic" && (
                <>
                  <dl className="project-info">
                    <dt>계약유형</dt>
                    <dd>{labels("CTRT_TYPE", p.contractType)}</dd>
                    <dt>계약 기간</dt>
                    <dd>
                      {p.startDate} ~ {p.endDate}
                    </dd>
                    {p.contractType !== "IN" && (
                      <>
                        <dt>근무지</dt>
                        <dd>{p.workplace ?? "–"}</dd>
                      </>
                    )}
                    <dt>계약금액</dt>
                    <dd>
                      {p.contractType === "IN" ? "–" : money(p.contractAmount)}
                    </dd>
                    <dt>팀비(월)</dt>
                    <dd>{money(p.teamExpenseAmount)}</dd>
                    <dt>프로젝트경비(월)</dt>
                    <dd>{money(p.projectExpenseAmount)}</dd>
                    {p.contractType !== "IN" && (
                      <>
                        <dt>예산</dt>
                        <dd>
                          {money(p.budgetAmount)} ·{" "}
                          {p.budgetManual === "Y" ? "수기" : "자동"}{" "}
                          <button
                            type="button"
                            data-testid="project-budget-open"
                            disabled={
                              locked || detail.effectiveStatus === "END"
                            }
                            onClick={() => setBudget(true)}
                          >
                            변경
                          </button>
                        </dd>
                        <dt>영업 파트</dt>
                        <dd>
                          {part?.partName ?? "미지정"}{" "}
                          {context && registration?.partAction?.(context)}
                        </dd>
                      </>
                    )}
                    <dt>PM</dt>
                    <dd>
                      {detail.pm.length
                        ? detail.pm.map((pm) => (
                            <span
                              key={`${pm.userId}-${pm.scopeSn}`}
                              data-testid={`project-pm-${pm.userId.toLowerCase()}-${pm.scopeSn}`}
                            >
                              {pm.name} ({pm.userId}){" "}
                            </span>
                          ))
                        : "미지정"}{" "}
                      <a data-testid="project-users-link" href="/UI-23">
                        사용자에서 담당 프로젝트 관리
                      </a>
                    </dd>
                    <dt>상태</dt>
                    <dd>
                      <StatusBadge
                        tone={projectStatusTone[detail.effectiveStatus]}
                      >
                        {labels("PROJ_STAT", detail.effectiveStatus)}
                      </StatusBadge>
                    </dd>
                    <dt>특징</dt>
                    <dd>{p.feature ?? "–"}</dd>
                  </dl>
                  {detail.effectiveStatus === "PRE" &&
                    todayPeople !== undefined &&
                    todayPeople > 0 && (
                      <p className="sicost-banner sicost-tone-warn">
                        계약 전에 {todayPeople}명이 먼저 투입
                      </p>
                    )}
                  {detail.effectiveStatus === "LOSE" && (
                    <p className="sicost-banner sicost-tone-bad">
                      실주 {p.lossDate} · 사유:{" "}
                      {p.lossReason ? labels("LOSE_RSN", p.lossReason) : "–"}
                      <br />
                      {p.lossMemo}
                    </p>
                  )}
                  {p.contractType !== "IN" && (
                    <>
                      <h2>영업 파트 시간선</h2>
                      <ol className="project-timeline">
                        {detail.parts.map((row) => (
                          <li
                            key={row.effectiveMonth}
                            data-testid={`project-part-${row.effectiveMonth}`}
                          >
                            {row.effectiveMonth} · {row.partName ?? "미지정"}{" "}
                            {row.scheduled && (
                              <StatusBadge tone="info">(예정)</StatusBadge>
                            )}{" "}
                            {row.reason && (
                              <span
                                tabIndex={0}
                                title={`사유: ${row.reason} · ${row.actor}`}
                                aria-label={`사유: ${row.reason} · ${row.actor}`}
                              >
                                ⓘ
                              </span>
                            )}
                          </li>
                        ))}
                      </ol>
                      <ProjectExpensesPanel
                        projectId={id}
                        year={year}
                        contractType={p.contractType}
                        effectiveStatus={detail.effectiveStatus}
                        amountUnit={amountUnit}
                        onChanged={refresh}
                        onDirtyChange={childDirty}
                      />
                      <p>직접 경비 합계 {money(detail.directExpenseTotal)}</p>
                    </>
                  )}
                  {context && registration?.costSummary?.(context)}
                  {!registration?.progress && !registration?.issues && (
                    <p className="project-help">
                      진행현황·이슈는 후속 기능에서 제공합니다.
                    </p>
                  )}
                </>
              )}
              {tab === "plans" && (
                <ProjectPlansPanel
                  projectId={id}
                  year={year}
                  currentYear={currentYear}
                  effectiveStatus={detail.effectiveStatus}
                  readOnly={locked}
                  onSaved={refresh}
                  onDirtyChange={childDirty}
                />
              )}
              {tab === "assignments" && (
                <>
                  <label>
                    <input
                      data-testid="project-cancelled-toggle"
                      type="checkbox"
                      checked={cancelled}
                      onChange={(e) => setCancelled(e.target.checked)}
                    />{" "}
                    취소 포함
                  </label>
                  <DataTable
                    rows={detail.assignments}
                    rowKey={(a) => a.assignmentId}
                    columns={[
                      {
                        id: "name",
                        label: "이름",
                        render: (a) => `${a.name} · ${a.managementNo}`,
                      },
                      { id: "job", label: "직무", render: (a) => a.jobName },
                      {
                        id: "period",
                        label: "기간",
                        render: (a) => `${a.startDate} ~ ${a.endDate}`,
                      },
                      {
                        id: "contract",
                        label: "계약 MM",
                        align: "right",
                        render: (a) => a.contractMm,
                      },
                      {
                        id: "mm",
                        label: "월 MM 합계",
                        align: "right",
                        render: (a) => a.monthlyMmTotal,
                      },
                      {
                        id: "status",
                        label: "상태",
                        render: (a) => (a.status === "ACT" ? "투입" : "취소"),
                      },
                    ]}
                  />
                  <p>실제 MM 합계 {detail.actualMmTotal}</p>
                </>
              )}
              {tab === "history" && (
                <DataTable
                  rows={detail.history}
                  rowKey={(h) => h.historySn}
                  columns={[
                    { id: "date", label: "일자", render: (h) => h.date },
                    { id: "item", label: "항목", render: (h) => h.itemName },
                    {
                      id: "before",
                      label: "이전",
                      render: (h) => (
                        <details>
                          <summary>{h.before?.slice(0, 30) ?? "–"}</summary>
                          {h.before}
                        </details>
                      ),
                    },
                    {
                      id: "after",
                      label: "이후",
                      render: (h) => (
                        <details>
                          <summary>{h.after?.slice(0, 30) ?? "–"}</summary>
                          {h.after}
                        </details>
                      ),
                    },
                    { id: "actor", label: "처리자", render: (h) => h.actor },
                  ]}
                />
              )}
            </div>
          </section>
        )}
      </DetailDrawer>
      {budget && detail && p && (
        <ProjectBudgetDialog
          projectId={id}
          projectName={p.name}
          contractType={p.contractType}
          effectiveStatus={detail.effectiveStatus}
          year={year}
          currentYear={currentYear}
          readOnly={locked}
          onClose={() => setBudget(false)}
          onSaved={refresh}
          onDirtyChange={childDirty}
        />
      )}
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
        cancelLabel="계속 작성"
        confirmLabel="나가기"
      >
        <p>저장하지 않고 상세를 닫습니다.</p>
      </Dialog>
    </>
  );
}
