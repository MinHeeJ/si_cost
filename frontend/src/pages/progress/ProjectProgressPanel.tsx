import { useCallback, useEffect, useState } from "react";
import { ErrorBanner, StatusBadge, type Tone } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { notifyProjectsCommitted } from "../projects/projectsApi";
import {
  ProjectProgressEditor,
  type ProgressEditorMode,
} from "./ProjectProgressEditor";
import * as api from "./projectProgressApi";
import "./projectProgress.css";

export type ProjectProgressPanelProps = {
  projectId: string;
  year: number;
  onChanged?(): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
};
const tone = (status: string): Tone =>
  status === "완료" || status === "정상"
    ? "ok"
    : status === "위험" || status === "지연"
      ? "bad"
      : status === "주의"
        ? "warn"
        : status === "진행중"
          ? "info"
          : "neutral";
export function ProjectProgressPanel({
  projectId,
  year,
  onChanged,
  onDirtyChange,
}: ProjectProgressPanelProps) {
  const [data, setData] = useState<api.ProjectProgress>();
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const [mode, setMode] = useState<ProgressEditorMode>();
  const [message, setMessage] = useState("");
  const [reloadOnly, setReloadOnly] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false),
    [history, setHistory] = useState<api.ProgressHistoryPage>();
  const [historyError, setHistoryError] = useState<unknown>(),
    [historyLoading, setHistoryLoading] = useState(false);
  const [historyTable, setHistoryTable] = useState("");
  const [calculation, setCalculation] = useState(false);
  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setData(await api.getProjectProgress(projectId, year));
      setReloadOnly(false);
    } catch (e) {
      setError(e);
      throw e;
    } finally {
      setLoading(false);
    }
  }, [projectId, year]);
  useEffect(() => {
    let live = true;
    setLoading(true);
    setData(undefined);
    setError(undefined);
    setMode(undefined);
    setHistoryOpen(false);
    setMessage("");
    setReloadOnly(false);
    void api
      .getProjectProgress(projectId, year)
      .then((value) => {
        if (live) setData(value);
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
  }, [projectId, year]);
  async function loadHistory(page = 1, table = historyTable) {
    setHistoryLoading(true);
    setHistoryError(undefined);
    try {
      setHistory(
        await api.getProjectProgressHistory(projectId, {
          page,
          size: 20,
          ...(table ? { targetTable: table } : {}),
        }),
      );
    } catch (e) {
      setHistoryError(e);
    } finally {
      setHistoryLoading(false);
    }
  }
  async function saved(result: api.ProgressMutation) {
    setMode(undefined);
    setData(result.progress);
    setMessage(result.message);
    setHistory(undefined);
    notifyProjectsCommitted(projectId);
    try {
      await Promise.all([load(), onChanged?.()]);
      if (historyOpen) await loadHistory();
    } catch {
      setReloadOnly(true);
      setMessage("저장됐지만 다시 불러오지 못했습니다. 다시 조회해 주세요.");
    }
  }
  const status = data?.status;
  const locked = !data || data.readOnly || loading || reloadOnly;
  return (
    <section
      data-testid="project-progress-panel"
      data-screen-id="SCR-UI-01"
      className="project-progress"
    >
      {loading && <p role="status">진행 현황을 불러오는 중입니다.</p>}
      {message && (
        <p
          role="status"
          className={`sicost-banner sicost-tone-${reloadOnly ? "warn" : "ok"}`}
        >
          {message}
        </p>
      )}
      {error != null && (
        <ErrorBanner
          message={
            reloadOnly
              ? "저장됐지만 다시 불러오지 못했습니다."
              : error instanceof Error
                ? error.message
                : "진행 현황을 조회하지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => void load().catch(() => {})}
        />
      )}
      {data && status && (
        <>
          <header className="project-progress-actions">
            <h2>진행 현황</h2>
            <StatusBadge tone={tone(status.light)}>{status.light}</StatusBadge>
            {status.costUnknown && (
              <StatusBadge tone="neutral">원가 미확인</StatusBadge>
            )}
            <button
              type="button"
              data-testid="progress-calculation"
              onClick={() => setCalculation(!calculation)}
            >
              계산 방법
            </button>
            <button
              type="button"
              data-testid="progress-history"
              onClick={() => {
                setHistoryOpen(!historyOpen);
                if (!historyOpen) void loadHistory();
              }}
            >
              관련 변경 이력{historyOpen ? " 접기" : ""}
            </button>
          </header>
          {status.reasons.map((reason, i) => (
            <p
              data-testid={`progress-reason-${i}`}
              key={`${reason.kind}-${i}`}
              className={`sicost-banner sicost-tone-${tone(reason.light)}`}
            >
              {reason.kind} {reason.value} · 기준 {reason.threshold}
            </p>
          ))}
          {calculation && (
            <aside
              data-testid="progress-calculation-panel"
              className="sicost-banner sicost-tone-info"
            >
              <p>
                {data.calculationNotice} 기준일 {status.baseDate} · 원가 기준월{" "}
                {status.costMonth}. 일정 늦음 = P − A, 진척대비원가 = U − A, U =
                C/B × 100. 메모 경과 = 기준일 − MAX(WRT_DT), 메모 없음은
                max(등록일, 계약시작일), 미래는 0일.
              </p>
              {status.reasons.map((r, i) => (
                <p key={i}>
                  {r.kind}: {r.formula}
                </p>
              ))}
            </aside>
          )}
          <div className="project-progress-metrics">
            <div>
              <span>계획</span>
              <strong>{status.planProgress}%</strong>
              <div
                className="project-progress-meter"
                aria-label={`계획 ${status.planProgress}%`}
              >
                <span style={{ width: `${status.planProgress}%` }} />
              </div>
              <span>{status.planBasis}</span>
            </div>
            <div>
              <span>실적</span>
              <strong>{status.actualProgress}%</strong>
              <div
                className="project-progress-meter"
                aria-label={`실적 ${status.actualProgress}%`}
              >
                <span style={{ width: `${status.actualProgress}%` }} />
              </div>
            </div>
            {status.contractType === "TK" && (
              <div>
                <span>원가 사용률</span>
                <strong>
                  {status.costUsage == null ? "–" : `${status.costUsage}%`}
                </strong>
              </div>
            )}
            <div>
              <span>일정 늦음</span>
              <strong>{status.scheduleGap}%p</strong>
            </div>
            {status.contractType === "TK" && (
              <div>
                <span>진척대비원가</span>
                <strong>
                  {status.costProgressGap == null
                    ? "–"
                    : `${status.costProgressGap}%p`}
                </strong>
              </div>
            )}
          </div>
          <p>
            현재 단계{" "}
            {status.allStepsCompleted
              ? "전 단계 완료"
              : (status.currentStep?.stepName ?? "–")}{" "}
            · PM {status.pm.map((p) => p.userName).join(", ") || "미지정"}
          </p>
          <p>
            마지막 업데이트 {status.lastUpdatedDate ?? "– (메모 없음)"} ·{" "}
            {status.elapsedDays}일 경과{" "}
            {status.updateWarning && (
              <StatusBadge tone="warn">미갱신 주의</StatusBadge>
            )}
          </p>
          {status.completeness.issues.map((i, index) => (
            <p
              key={index}
              data-testid={`progress-source-issue-${index}`}
              className="sicost-banner sicost-tone-warn"
            >
              {i.message}
            </p>
          ))}
          {data.readOnly && (
            <p className="sicost-banner sicost-tone-lock">
              {data.readOnlyReasons.join(" ")}
            </p>
          )}
          {data.initializationMessage && (
            <p className="sicost-banner sicost-tone-info">
              {data.initializationMessage}
            </p>
          )}
          <header className="project-progress-actions">
            <h2>단계</h2>
            {!data.steps.length && (
              <button
                data-testid="progress-initialize"
                type="button"
                disabled={locked}
                onClick={() => setMode({ kind: "initialize" })}
              >
                기본 단계 초기화
              </button>
            )}
            <button
              data-testid="progress-dates"
              type="button"
              disabled={locked || !data.steps.length}
              onClick={() => setMode({ kind: "dates" })}
            >
              단계 계획 입력
            </button>
          </header>
          <div className="sicost-table-viewport">
            <table className="sicost-table">
              <thead>
                <tr>
                  {["단계", "비중", "예정 기간", "완료일", "상태", "작업"].map(
                    (label) => (
                      <th key={label}>{label}</th>
                    ),
                  )}
                </tr>
              </thead>
              <tbody>
                {data.steps.map((s) => (
                  <tr
                    key={s.stepSn}
                    data-testid={`progress-step-${s.stepSn}`}
                    className={
                      status.currentStep?.stepSn === s.stepSn
                        ? "project-progress-current"
                        : undefined
                    }
                  >
                    <td>{s.stepName}</td>
                    <td>{s.weight}%</td>
                    <td>
                      {s.startDate} ~ {s.endDate}
                    </td>
                    <td>{s.completedDate ?? "–"}</td>
                    <td>
                      <StatusBadge tone={tone(s.status)}>
                        {s.status}
                      </StatusBadge>
                    </td>
                    <td>
                      {s.completedDate == null && (
                        <button
                          data-testid={`progress-complete-${s.stepSn}`}
                          type="button"
                          disabled={locked || s.startDate > status.baseDate}
                          onClick={() => setMode({ kind: "complete", step: s })}
                        >
                          완료
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {!data.steps.length && (
                  <tr>
                    <td colSpan={6} className="project-progress-empty">
                      등록된 단계가 없습니다. 기간 기준으로 계획을 표시합니다.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <header className="project-progress-actions">
            <h2>주간 메모</h2>
            <button
              data-testid="progress-memo-new"
              type="button"
              disabled={locked}
              onClick={() => setMode({ kind: "memo" })}
            >
              이번 주 상태 적기
            </button>
          </header>
          {!data.memos.length && (
            <div className="project-progress-empty">
              등록된 주간 메모가 없습니다.
            </div>
          )}
          {data.memos.map((m) => (
            <article
              className="project-progress-memo"
              data-testid={`progress-memo-${m.memoSn}`}
              key={m.memoSn}
            >
              <header className="project-progress-actions">
                <strong>
                  {m.writtenDate} · {m.registeredBy.userName} 작성
                </strong>
                <button
                  data-testid={`progress-memo-edit-${m.memoSn}`}
                  type="button"
                  disabled={locked}
                  onClick={() => setMode({ kind: "memo", memo: m })}
                >
                  수정
                </button>
              </header>
              <dl>
                <dt>이번 주 한 일</dt>
                <dd>{m.thisWeek ?? "–"}</dd>
                <dt>다음 주 할 일</dt>
                <dd>{m.nextWeek ?? "–"}</dd>
                <dt>요청 사항</dt>
                <dd>{m.request ?? "–"}</dd>
                {(status.contractType === "TK" ||
                  status.contractType === "IN") && (
                  <>
                    <dt>실적 진척률</dt>
                    <dd>
                      {m.actualProgress == null ? "–" : `${m.actualProgress}%`}
                    </dd>
                  </>
                )}
              </dl>
              <small>
                등록 {m.registeredAt}
                {m.modifiedAt &&
                  ` · ${m.modifiedAt} ${m.modifiedBy?.userName ?? ""} 고침`}
              </small>
            </article>
          ))}
          {historyOpen && (
            <section data-testid="progress-history-panel">
              <h2>진행자료 관련 변경 이력</h2>
              <select
                data-testid="progress-history-table"
                aria-label="이력 대상"
                value={historyTable}
                onChange={(e) => {
                  setHistoryTable(e.target.value);
                  void loadHistory(1, e.target.value);
                }}
              >
                <option value="">전체</option>
                <option value="TB_PJ_STEP">단계</option>
                <option value="TB_PJ_WEEK_MEMO">메모</option>
                <option value="TB_PJ_ISSUE">이슈</option>
              </select>
              {historyLoading && (
                <p role="status">변경 이력을 불러오는 중입니다.</p>
              )}
              {historyError != null && (
                <ErrorBanner
                  message={
                    historyError instanceof Error
                      ? historyError.message
                      : "변경 이력을 불러오지 못했습니다."
                  }
                  onRetry={() => void loadHistory()}
                />
              )}
              {history && (
                <>
                  <div className="sicost-table-viewport">
                    <table className="sicost-table">
                      <thead>
                        <tr>
                          {[
                            "대상",
                            "키",
                            "항목",
                            "변경 전",
                            "변경 후",
                            "처리자",
                            "처리 시각",
                          ].map((t) => (
                            <th key={t}>{t}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {history.items.map((h) => (
                          <tr
                            data-testid={`progress-history-${h.historyId}`}
                            key={h.historyId}
                          >
                            <td>{h.targetTable}</td>
                            <td>{h.targetKey}</td>
                            <td>{h.itemName}</td>
                            <td>{h.before ?? "–"}</td>
                            <td>{h.after ?? "–"}</td>
                            <td>{h.registeredBy.userName}</td>
                            <td>{h.registeredAt}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {!history.items.length && (
                    <p className="project-progress-empty">
                      관련 변경 이력이 없습니다.
                    </p>
                  )}
                  <div className="project-progress-actions">
                    <span>
                      {history.total}건 · {history.page}페이지
                    </span>
                    <button
                      data-testid="progress-history-previous"
                      type="button"
                      disabled={historyLoading || history.page === 1}
                      onClick={() => void loadHistory(history.page - 1)}
                    >
                      이전
                    </button>
                    <button
                      data-testid="progress-history-next"
                      type="button"
                      disabled={
                        historyLoading ||
                        history.page * history.size >= history.total
                      }
                      onClick={() => void loadHistory(history.page + 1)}
                    >
                      다음
                    </button>
                  </div>
                </>
              )}
            </section>
          )}
          {mode && (
            <ProjectProgressEditor
              key={
                mode.kind === "memo"
                  ? `memo-${mode.memo?.memoSn ?? "new"}`
                  : mode.kind
              }
              data={data}
              mode={mode}
              onClose={(reload) => {
                setMode(undefined);
                if (reload) void load().catch(() => {});
              }}
              onSaved={saved}
              onDirtyChange={onDirtyChange}
            />
          )}
        </>
      )}
    </section>
  );
}
