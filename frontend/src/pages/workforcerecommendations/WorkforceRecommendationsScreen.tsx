import { HelpTooltip } from "../../shared/HelpTooltip";
import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { StatusBadge } from "../../shared/Feedback";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import { AssignmentCreateForm } from "../assignmentsops/AssignmentCreateForm";
import { DeveloperDrawer } from "../developers/DeveloperDrawer";
import { AssignmentError } from "../assignmentsops/AssignmentDialogs";
import * as api from "./workforceRecommendationsApi";
import "./recommendations.css";

const labels: Record<string, string> = {
  JOB: "직무",
  SKILL: "스킬",
  CUST: "고객",
  GRADE: "등급",
  PERF: "성과",
  COST: "비용",
  AVAIL: "가용일",
  LOC: "근무지",
  GAP: "공백",
  PROB: "확률",
};
export function RecommendationCards({
  data,
  disabled = false,
  onAction,
  onPerson,
}: {
  data: api.Data;
  disabled?: boolean;
  onAction?: (
    kind: "exclude" | "tentative" | "assign",
    candidate: api.Candidate,
  ) => void;
  onPerson?: (id: string) => void;
}) {
  return (
    <div className="recommendation-cards">
      {data.candidates
        .filter((c) => !onAction || c.selection.actionCode !== "EXCL")
        .map((c) => (
          <article
            className="recommendation-card"
            data-testid={`recommendation-card-${c.rank}`}
            key={c.rank}
          >
            <header>
              <div>
                <span className="recommendation-rank">{c.rank}</span>
                {onPerson ? (
                  <button
                    type="button"
                    className="sicost-link"
                    data-testid={`recommendation-person-${c.rank}`}
                    onClick={() => onPerson(c.developer.detailId)}
                  >
                    {c.developer.name}
                  </button>
                ) : (
                  <strong>{c.developer.name}</strong>
                )}{" "}
                <span>{c.developer.managementNumber}</span>
                <p>
                  {c.developer.jobCode} ·{" "}
                  {c.developer.gradeCode ?? "등급 미확인"} ·{" "}
                  {c.developer.employeeTypeCode}
                </p>
              </div>
              <strong className="recommendation-total">
                {c.totalScore}
                <small>점</small>
              </strong>
            </header>
            {c.otherTentativeAssignments.map((t) => (
              <span
                className="sicost-badge sicost-tone-warn"
                key={t.tentativeAssignmentId}
              >
                다른 기회 가배정: {t.opportunityName} · 이동 시 이전 가배정 해제
              </span>
            ))}
            <div className="recommendation-scores">
              {c.scoreItems.map((s) => (
                <div
                  className="recommendation-score"
                  key={s.itemCode}
                  title={s.basis}
                >
                  <span>{labels[s.itemCode] ?? s.itemCode}</span>
                  <progress
                    aria-label={`${c.rank}위 ${labels[s.itemCode] ?? s.itemCode}`}
                    value={s.weight === "0" ? 0 : s.score}
                    max={s.weight === "0" ? "1" : s.weight}
                  />
                  <span>
                    {s.score}/{s.weight}
                  </span>
                  <small>{s.basis}</small>
                </div>
              ))}
            </div>
            <p className="recommendation-reason">{c.reason}</p>
            {c.selection.actionCode && (
              <StatusBadge
                tone={c.selection.actionCode === "EXCL" ? "lock" : "ok"}
              >
                {c.selection.actionCode} · {c.selection.actedBy} ·{" "}
                {c.selection.actedAt}
              </StatusBadge>
            )}
            {onAction && (
              <footer>
                <button
                  type="button"
                  className="primary"
                  data-testid={`recommendation-select-${c.rank}`}
                  disabled={disabled || Boolean(c.selection.actionCode)}
                  onClick={() =>
                    onAction(
                      data.appliedCondition?.targetType === "PROJECT"
                        ? "assign"
                        : "tentative",
                      c,
                    )
                  }
                >
                  {data.appliedCondition?.targetType === "PROJECT"
                    ? "배정"
                    : "가배정"}
                </button>
                <button
                  type="button"
                  data-testid={`recommendation-exclude-${c.rank}`}
                  disabled={disabled || Boolean(c.selection.actionCode)}
                  onClick={() => onAction("exclude", c)}
                >
                  제외
                </button>
              </footer>
            )}
          </article>
        ))}
    </div>
  );
}

export function WorkforceRecommendationsScreen() {
  const [targets, setTargets] = useState<api.Targets>();
  const [target, setTarget] = useState<api.Target>();
  const [draft, setDraft] = useState<api.Condition>();
  const [data, setData] = useState<api.Data>();
  const [jobs, setJobs] = useState<SharedCodeLookup[]>([]),
    [grades, setGrades] = useState<SharedCodeLookup[]>([]),
    [reasons, setReasons] = useState<SharedCodeLookup[]>([]);
  const [loading, setLoading] = useState(true),
    [running, setRunning] = useState(false),
    [error, setError] = useState<unknown>(),
    [notice, setNotice] = useState("");
  const [excluding, setExcluding] = useState<api.Candidate>(),
    [reason, setReason] = useState(""),
    [actionError, setActionError] = useState<unknown>(),
    [warnings, setWarnings] = useState<{
      candidate: api.Candidate;
      items: api.Warning[];
    }>();
  const [assigning, setAssigning] = useState<api.Candidate>(),
    [person, setPerson] = useState<string>(),
    [history, setHistory] = useState(false);
  const [refreshOnly, setRefreshOnly] = useState(false);
  const [targetRetry, setTargetRetry] = useState(0);
  const generation = useRef(0),
    inFlight = useRef(false);
  const { pending, commit, message } = useCommitRefresh(
    "저장됐지만 다시 불러오지 못했습니다",
  );
  const fields = error instanceof ApiError ? error.fieldMessages : {};
  function valid(c: api.Condition, base: string) {
    if (
      !c.jobCode ||
      !c.startMonth ||
      !/^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(c.startMonth) ||
      c.startMonth < base ||
      !c.requestedGradeCode ||
      (c.targetType === "OPPORTUNITY" && !c.needSn)
    )
      throw new Error("직무·시작월·등급·가배정 자리를 확인해 주세요.");
  }
  async function run(c: api.Condition, base: string, switched = false) {
    const token = ++generation.current;
    try {
      valid(c, base);
    } catch (e) {
      setError(e);
      return;
    }
    inFlight.current = true;
    setRunning(true);
    setError(undefined);
    setRefreshOnly(false);
    try {
      const result = await api.createRecommendation(c);
      if (token !== generation.current) return;
      setData(result);
      setNotice(switched ? "대상을 바꿔 다시 추천했습니다" : "");
    } catch (e) {
      if (token === generation.current) setError(e);
    } finally {
      if (token === generation.current) {
        setRunning(false);
        inFlight.current = false;
      }
    }
  }
  useEffect(() => {
    let live = true;
    setLoading(true);
    void Promise.all([
      api.getTargets(),
      getSharedCodes("JOB"),
      getSharedCodes("GRADE"),
      getSharedCodes("RCMD_EXCL"),
    ])
      .then(([t, j, g, r]) => {
        if (!live) return;
        setTargets(t);
        setJobs(j.items);
        setGrades(g.items);
        setReasons(r.items);
        setLoading(false);
        const first =
          t.items.find((v) => v.targetId === t.firstTargetId) ?? t.items[0];
        if (first) {
          const c: api.Condition = {
            targetType: first.targetType,
            targetId: first.targetId,
            jobCode: first.defaults.jobCode ?? "",
            startMonth: first.defaults.startMonth ?? t.currentMonth,
            requestedGradeCode: first.defaults.requestedGradeCode,
            needSn: first.defaults.needSn,
          };
          setTarget(first);
          setDraft(c);
          void run(c, t.baseMonth);
        }
      })
      .catch((e) => {
        if (live) {
          setError(e);
          setLoading(false);
        }
      });
    return () => {
      live = false;
      generation.current++;
      inFlight.current = false;
    };
  }, [targetRetry]);
  async function reload() {
    if (!data?.rcmdId) return;
    const result = await api.getRecommendation(data.rcmdId);
    setData(result);
    setRefreshOnly(false);
    setError(undefined);
  }
  async function afterAction() {
    setRefreshOnly(true);
    await reload();
  }
  async function tentative(candidate: api.Candidate, confirmed: string[]) {
    if (!data?.rcmdId || !draft?.needSn) return;
    setActionError(undefined);
    try {
      await commit(async () => {
        const result = await api.selectTentative(
          data.rcmdId!,
          candidate,
          draft.needSn!,
          confirmed,
        );
        setWarnings(undefined);
        setNotice(result.message);
        api.announceRecommendationCommit();
      }, afterAction);
    } catch (e) {
      const details =
        e instanceof ApiError
          ? (e.body.details as
              | { workforceRecommendationWarnings?: api.Warning[] }
              | undefined)
          : undefined;
      if (details?.workforceRecommendationWarnings?.length)
        setWarnings({
          candidate,
          items: details.workforceRecommendationWarnings,
        });
      else setActionError(e);
    }
  }
  const staleTarget =
    !data?.appliedCondition ||
    data.appliedCondition.targetType !== target?.targetType ||
    data.appliedCondition.targetId !== target?.targetId;
  const disabled = running || pending || staleTarget || refreshOnly;
  return (
    <section
      data-screen-id="SCR-UI-30 UI-30"
      data-testid="workforce-recommendations-screen"
      className="workforce-recommendations"
      aria-busy={loading || running}
    >
      <header className="recommendation-heading">
        <button
          type="button"
          data-testid="recommendation-history"
          onClick={() => setHistory(true)}
        >
          추천 기록
        </button>
      </header>
      <section
        className="recommendation-card"
        data-testid="recommendation-conditions"
      >
        <h2>어떤 자리에 추천할까요?</h2>
        {loading ? (
          <p role="status">추천 대상을 불러오는 중입니다.</p>
        ) : !targets ? (
          <button
            type="button"
            data-testid="recommendation-target-retry"
            onClick={() => setTargetRetry((v) => v + 1)}
          >
            대상 재조회
          </button>
        ) : !targets.items.length ? (
          <p className="recommendation-empty">추천할 진행 대상이 없습니다.</p>
        ) : (
          <div className="recommendation-filters">
            <label className="sicost-field">
              프로젝트·영업 기회
              <select
                data-testid="recommendation-target"
                disabled={running || pending}
                value={target ? `${target.targetType}:${target.targetId}` : ""}
                onChange={(e) => {
                  const t = targets.items.find(
                    (v) => `${v.targetType}:${v.targetId}` === e.target.value,
                  );
                  if (!t || !draft) return;
                  const c = {
                    ...draft,
                    targetType: t.targetType,
                    targetId: t.targetId,
                    needSn:
                      t.targetType === "OPPORTUNITY" ? t.defaults.needSn : null,
                  };
                  setTarget(t);
                  setDraft(c);
                  setAssigning(undefined);
                  setExcluding(undefined);
                  setWarnings(undefined);
                  void run(c, targets.baseMonth, true);
                }}
              >
                {targets.items.map((t) => (
                  <option
                    key={`${t.targetType}:${t.targetId}`}
                    value={`${t.targetType}:${t.targetId}`}
                  >
                    [{t.targetType === "PROJECT" ? "프로젝트" : "기회"}]{" "}
                    {t.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="sicost-field">
              직무
              <FieldHelp label="직무" /><select
                data-testid="recommendation-job"
                disabled={running || pending}
                value={draft?.jobCode ?? ""}
                onChange={(e) =>
                  setDraft((c) => c && { ...c, jobCode: e.target.value })
                }
              >
                <option value="">선택해 주세요</option>
                {jobs.map((j) => (
                  <option key={j.code} value={j.code}>
                    {j.name}
                  </option>
                ))}
              </select>
              {fields.jobCode && <small role="alert">{fields.jobCode}</small>}
            </label>
            {target?.targetType === "OPPORTUNITY" && (
              <label className="sicost-field">
                가배정 자리
                <FieldHelp label="가배정 자리" /><select
                  data-testid="recommendation-need"
                  disabled={running || pending}
                  value={draft?.needSn ?? ""}
                  onChange={(e) =>
                    setDraft((c) => c && { ...c, needSn: e.target.value })
                  }
                >
                  <option value="">선택해 주세요</option>
                  {target.needs.map((n) => (
                    <option key={n.needSn} value={n.needSn}>
                      {n.needSn}.{" "}
                      {jobs.find((j) => j.code === n.jobCode)?.name ??
                        n.jobCode}{" "}
                      {n.needCount}명 · {n.startMonth}
                    </option>
                  ))}
                </select>
                {fields.needSn && <small role="alert">{fields.needSn}</small>}
              </label>
            )}
            <label className="sicost-field">
              시작월
              <input
                type="month"
                data-testid="recommendation-month"
                min={targets.baseMonth}
                disabled={running || pending}
                value={draft?.startMonth ?? ""}
                onChange={(e) =>
                  setDraft((c) => c && { ...c, startMonth: e.target.value })
                }
              />
              {fields.startMonth && (
                <small role="alert">{fields.startMonth}</small>
              )}
            </label>
            <label className="sicost-field">
              등급
              <select
                data-testid="recommendation-grade"
                disabled={running || pending}
                value={draft?.requestedGradeCode ?? ""}
                onChange={(e) =>
                  setDraft(
                    (c) => c && { ...c, requestedGradeCode: e.target.value },
                  )
                }
              >
                {grades.map((g) => (
                  <option key={g.code} value={g.code}>
                    {g.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}
        <button
          type="button"
          className="primary"
          data-testid="recommendation-run"
          disabled={!draft || !target || running || pending || refreshOnly}
          onClick={() => {
            if (!inFlight.current && draft && targets)
              void run(draft, targets.baseMonth);
          }}
        >
          추천 받기
        </button><HelpTooltip label="추천 대상" iconOnly>휴직·퇴사 예정자, 그 기간 MM이 이미 1.0인 사람은 뺍니다. 나이·성별·학력은 쓰지 않습니다.</HelpTooltip>
      </section>
      <AssignmentError error={error} />
      <AssignmentError error={actionError} />
      {refreshOnly && (
        <button
          type="button"
          data-testid="recommendation-reload"
          disabled={pending}
          onClick={() => void reload().catch(setError)}
        >
          재조회
        </button>
      )}
      {(notice || message) && (
        <p role="status">
          {refreshOnly && message ? message : notice || message}
        </p>
      )}
      {running && <p role="status">추천 중입니다.</p>}
      {data && (
        <>
          <h2 data-testid="recommendation-applied">
            {data.appliedCondition
              ? `${targets?.items.find((t) => t.targetId === data.appliedCondition?.targetId)?.name ?? data.appliedCondition.targetId} · ${jobs.find((j) => j.code === data.appliedCondition?.jobCode)?.name ?? data.appliedCondition.jobCode} · ${data.appliedCondition.startMonth}부터 · ${data.appliedCondition.requestedGradeCode}`
              : "저장된 추천"}
          </h2>
          <p className="sicost-banner sicost-tone-info">
            추천은 참고용입니다. 배정은 담당자가 정합니다
          </p>
          {!data.candidates.filter((c) => c.selection.actionCode !== "EXCL")
            .length && (
            <div
              className="recommendation-empty"
              data-testid="recommendation-empty"
            >
              <h2>맞는 인력이 없습니다.</h2>
              <p>{data.message}</p>
            </div>
          )}
          <RecommendationCards
            data={data}
            disabled={disabled}
            onPerson={setPerson}
            onAction={(kind, c) => {
              setActionError(undefined);
              if (kind === "exclude") {
                setExcluding(c);
                setReason("");
              } else if (kind === "assign") setAssigning(c);
              else void tentative(c, []);
            }}
          />
          <section
            className="recommendation-card"
            data-testid="recommendation-exclusions"
          >
            <h2>후보 제외 진단</h2>
            <p>
              전체 후보 {data.exclusions.totalCandidateCount}명 · 고유 제외{" "}
              {data.exclusions.excludedUniqueCount}명
            </p>
            <p>
              한 사람에게 여러 사유가 있을 수 있어 사유별 건수 합과 고유 인원은
              다릅니다.
            </p>
            {data.exclusions.byReason.map((r) => (
              <span className="sicost-badge sicost-tone-neutral" key={r.reason}>
                {r.reason} {r.count}명
              </span>
            ))}
            <details>
              <summary>개인 진단</summary>
              {data.exclusions.diagnostics.map((d) => (
                <p
                  data-testid={`recommendation-diagnostic-${d.developerId.toLowerCase()}`}
                  key={d.developerId}
                >
                  {d.developerId}: {d.reasons.join(" · ")}
                </p>
              ))}
            </details>
          </section>
        </>
      )}
      <Dialog
        open={Boolean(excluding)}
        title={`${excluding?.developer.name ?? "후보"}님을 추천에서 뺄까요?`}
        pending={pending}
        confirmLabel="확정"
        confirmDisabled={!reason}
        onCancel={() => {
          setExcluding(undefined);
          setActionError(undefined);
        }}
        onConfirm={async () => {
          if (!data?.rcmdId || !excluding) return;
          try {
            await commit(async () => {
              await api.excludeCandidate(data.rcmdId!, excluding, reason);
              setExcluding(undefined);
              api.announceRecommendationCommit();
            }, afterAction);
          } catch (e) {
            setActionError(e);
          }
        }}
      >
        <div
          data-testid="recommendation-exclude-dialog"
          data-screen-id="SCR-UI-30-M1"
        >
          <AssignmentError error={actionError} />
          <label className="sicost-field">
            제외 사유
            <select
              data-testid="recommendation-exclude-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            >
              <option value="">사유를 선택해 주세요</option>
              {reasons.map((r) => (
                <option value={r.code} key={r.code}>
                  {r.name}
                </option>
              ))}
            </select>
          </label>
          <p>같은 대상·적용 직무에서 다시 추천해도 제외됩니다.</p>
        </div>
      </Dialog>
      <Dialog
        open={Boolean(warnings)}
        title="가배정 확인"
        confirmLabel="확인 후 가배정"
        pending={pending}
        onCancel={() => setWarnings(undefined)}
        onConfirm={() =>
          warnings
            ? tentative(
                warnings.candidate,
                warnings.items.map((w) => w.code),
              )
            : undefined
        }
      >
        <div
          data-testid="recommendation-warning-dialog"
          data-screen-id="SCR-UI-30-M2"
        >
          {warnings?.items.map((w) => <p key={w.code}>{w.message}</p>)}
          <AssignmentError error={actionError} />
        </div>
      </Dialog>
      {assigning &&
        data?.rcmdId &&
        data.appliedCondition &&
        targets &&
        target && (
          <AssignmentCreateForm
            year={Number(targets.serverDate.slice(0, 4))}
            today={targets.serverDate}
            initialValues={{
              year: Number(targets.serverDate.slice(0, 4)),
              projectId: data.appliedCondition.targetId,
              developerId: assigning.developer.developerId,
              jobCode: data.appliedCondition.jobCode,
              startDate: `${data.appliedCondition.startMonth}-01`,
              endDate:
                target.contractEndDate ??
                `${data.appliedCondition.startMonth}-01`,
              inputType: "NEW",
            }}
            onClose={() => setAssigning(undefined)}
            onSaved={afterAction}
            submitAssignment={(value) =>
              api.saveRecommendedAssignment(
                data.rcmdId!,
                assigning,
                data.appliedCondition!,
                value,
              )
            }
            onCandidateSaved={async () => {
              api.announceRecommendationCommit();
              try {
                await afterAction();
              } catch (e) {
                setError(e);
                setNotice("저장됐지만 다시 불러오지 못했습니다");
              }
            }}
          />
        )}
      {person && (
        <DeveloperDrawer id={person} onClose={() => setPerson(undefined)} />
      )}
      {history && <RecommendationHistory onClose={() => setHistory(false)} />}
    </section>
  );
}

function RecommendationHistory({ onClose }: { onClose(): void }) {
  const [targetId, setTargetId] = useState(""),
    [job, setJob] = useState(""),
    [page, setPage] = useState(1),
    [size, setSize] = useState(50);
  const [filter, setFilter] = useState({ targetId: "", job: "" });
  const [rows, setRows] = useState<api.HistoryData>(),
    [detail, setDetail] = useState<api.Data>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true);
  const token = useRef(0);
  async function load() {
    setLoading(true);
    try {
      setRows(await api.getHistory({ ...filter, page, size }));
      setError(undefined);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
  }, [filter, page, size]);
  return (
    <DetailDrawer
      title="추천 기록"
      testId="recommendation-history-panel"
      onClose={onClose}
    >
      <section data-testid="recommendation-history-content">
        <div className="recommendation-filters">
          <label className="sicost-field">
            대상 ID
            <input
              data-testid="recommendation-history-target"
              value={targetId}
              onChange={(e) => setTargetId(e.target.value)}
            />
          </label>
          <label className="sicost-field">
            직무
            <FieldHelp label="직무" /><input
              data-testid="recommendation-history-job"
              value={job}
              onChange={(e) => setJob(e.target.value)}
            />
          </label>
          <button
            type="button"
            data-testid="recommendation-history-query"
            onClick={() => {
              setPage(1);
              setFilter({ targetId, job });
            }}
          >
            조회
          </button>
        </div>
        <AssignmentError error={error} retry={() => void load()} />
        {loading ? (
          <p role="status">기록 조회 중</p>
        ) : (
          rows && (
            <>
              <p>전체 {rows.totalCount}회</p>
              {!rows.items.length && <p>추천 기록이 없습니다.</p>}
              {rows.items.map((r) => (
                <button
                  type="button"
                  data-testid={`recommendation-round-${r.rcmdId}`}
                  key={r.rcmdId}
                  onClick={() => {
                    const version = ++token.current;
                    void api
                      .getRecommendation(r.rcmdId)
                      .then((d) => {
                        if (token.current === version) setDetail(d);
                      })
                      .catch(setError);
                  }}
                >
                  회차 {r.rcmdId} · {r.recommendationType} · {r.executedAt} ·{" "}
                  {r.executedBy} · {r.targetId} · {r.jobCode}
                </button>
              ))}
              <nav aria-label="추천 기록 페이지">
                <button
                  type="button"
                  data-testid="recommendation-history-previous"
                  disabled={page === 1}
                  onClick={() => setPage((p) => p - 1)}
                >
                  이전
                </button>
                {page}
                <button
                  type="button"
                  data-testid="recommendation-history-next"
                  disabled={page * size >= rows.totalCount}
                  onClick={() => setPage((p) => p + 1)}
                >
                  다음
                </button>
                <select
                  aria-label="기록 페이지 크기"
                  data-testid="recommendation-history-size"
                  value={size}
                  onChange={(e) => {
                    setPage(1);
                    setSize(Number(e.target.value));
                  }}
                >
                  {[20, 50, 100].map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </nav>
            </>
          )
        )}
        {detail && (
          <>
            <h2>
              회차 {detail.rcmdId} · {detail.executedAt}
            </h2>
            <p>
              {detail.appliedCondition?.targetId} ·{" "}
              {detail.appliedCondition?.jobCode} ·{" "}
              {detail.appliedCondition?.startMonth} ·{" "}
              {detail.appliedCondition?.requestedGradeCode}
            </p>
            <RecommendationCards data={detail} />
            {detail.candidates.map((c) => (
              <p key={c.rank}>
                당시 {c.rank}위 · 최종 선택 {c.selection.actionCode ?? "미선택"}{" "}
                · {c.selection.exclusionReasonCode} · {c.selection.actedBy} ·{" "}
                {c.selection.actedAt} · 연결{" "}
                {c.selection.assignmentId ?? c.selection.tentativeAssignmentId}{" "}
                · 자리 {c.selection.finalNeedSn}
              </p>
            ))}
          </>
        )}
      </section>
    </DetailDrawer>
  );
}
