import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { ApiError } from "../../shared/api";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import { RecommendationHistoryPanel } from "./RecommendationHistoryPanel";
import {
  RecommendationBrowseError,
  RecommendationSavedValues,
} from "./RecommendationSavedValues";
import * as api from "./recommendationBrowseApi";
import { useCodeNames } from "../../shared/codeNames";
import "./recommendationBrowse.css";

const targetKey = (t: api.RecommendationTarget) =>
  `${t.targetType}:${t.targetId}`;
function initialCondition(
  target: api.RecommendationTarget,
): api.RecommendationCondition {
  return {
    targetType: target.targetType,
    targetId: target.targetId,
    jobCode: target.defaults.jobCode ?? "",
    startMonth: target.defaults.startMonth ?? "",
    requestedGradeCode: target.defaults.requestedGradeCode,
    needSn: target.defaults.needSn,
  };
}
export function WorkforceRecommendationsScreen({
  renderRecommendation,
}: {
  renderRecommendation?(
    value: api.RecommendationSnapshot,
    currentNeedSn: string | null,
    canAct: boolean,
    context: {
      target: api.RecommendationTarget;
      serverDate: string;
      onRefresh(): Promise<void>;
      onReset(): void;
    },
  ): ReactNode;
} = {}) {
  const [targets, setTargets] = useState<api.RecommendationTargets>();
  const name = useCodeNames("GRADE", "JOB");
  const [jobs, setJobs] = useState<SharedCodeLookup[]>([]);
  const [selected, setSelected] = useState<api.RecommendationTarget>();
  const [draft, setDraft] = useState<api.RecommendationCondition>();
  const [result, setResult] = useState<api.RecommendationSnapshot>();
  const [loading, setLoading] = useState(true),
    [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(),
    [targetError, setTargetError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({}),
    [notice, setNotice] = useState("");
  const [history, setHistory] = useState(false),
    [retry, setRetry] = useState(0);
  const selection = useRef<api.RecommendationTarget>(),
    draftRef = useRef<api.RecommendationCondition>();
  const generation = useRef(0),
    active = useRef(true),
    request = useRef<AbortController>();
  const initialized = useRef(false),
    busy = useRef(false);
  const canAct =
    !loading &&
    !pending &&
    !error &&
    !targetError &&
    Boolean(result) &&
    Boolean(selected) &&
    result?.appliedCondition?.targetId === selected?.targetId &&
    result?.appliedCondition?.targetType === selected?.targetType;
  function update(next: api.RecommendationCondition) {
    draftRef.current = next;
    setDraft(next);
  }
  function validate(
    input: api.RecommendationCondition,
    target: api.RecommendationTarget,
    base: string,
  ) {
    const invalid: Record<string, string> = {};
    if (!input.jobCode) invalid.jobCode = "직무를 선택해 주세요.";
    if (
      !/^(?!0000)[0-9]{4}-(0[1-9]|1[0-2])$/.test(input.startMonth) ||
      input.startMonth < base
    )
      invalid.startMonth = "이관 시작월 이상의 올바른 연월을 입력해 주세요.";
    if (!input.requestedGradeCode)
      invalid.requestedGradeCode = "요청등급을 선택해 주세요.";
    if (target.targetType === "OPPORTUNITY" && !input.needSn)
      invalid.needSn = "가배정 자리를 선택해 주세요.";
    if (target.targetType === "PROJECT") {
      const begin = target.contractStartDate?.slice(0, 7),
        end = target.contractEndDate?.slice(0, 7);
      if (begin && input.startMonth < begin)
        input = { ...input, startMonth: begin };
      if (end && input.startMonth > end)
        invalid.startMonth = "프로젝트 계약 종료월을 넘을 수 없습니다.";
    }
    setFields(invalid);
    return { input, invalid: Object.keys(invalid).length > 0 };
  }
  async function recommend(
    input: api.RecommendationCondition,
    target: api.RecommendationTarget,
    base: string,
    switched = false,
  ) {
    if (busy.current) return;
    const checked = validate(input, target, base);
    update(checked.input);
    if (checked.invalid) {
      if (switched) setResult(undefined);
      return;
    }
    const current = ++generation.current;
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    busy.current = true;
    setPending(true);
    setError(undefined);
    setNotice("");
    try {
      const value = await api.requestTargetRecommendation(
        checked.input,
        controller.signal,
      );
      if (active.current && current === generation.current) {
        setResult(value);
        if (switched) setNotice("대상을 바꿔 다시 추천했습니다");
      }
    } catch (e) {
      if (
        active.current &&
        current === generation.current &&
        !controller.signal.aborted
      ) {
        setError(e);
        if (e instanceof ApiError) setFields(e.fieldMessages);
      }
    } finally {
      if (active.current && current === generation.current) {
        setPending(false);
        busy.current = false;
      }
    }
  }
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      generation.current++;
      request.current?.abort();
      busy.current = false;
    };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setTargetError(undefined);
    void Promise.all([
      api.getWorkforceRecommendationTargets(controller.signal),
      getSharedCodes("JOB"),
    ])
      .then(([value, codes]) => {
        if (controller.signal.aborted) return;
        setTargets(value);
        setJobs(codes.items);
        setLoading(false);
        const previous = selection.current;
        const retained =
          previous &&
          value.items.find((t) => targetKey(t) === targetKey(previous));
        const converted =
          previous?.projectIdAfterWin &&
          value.items.find(
            (t) =>
              t.targetType === "PROJECT" &&
              t.targetId === previous.projectIdAfterWin,
          );
        const first =
          value.items.find((t) => t.targetId === value.firstTargetId) ??
          value.items[0];
        const next = retained || converted || first;
        selection.current = next;
        setSelected(next);
        if (!next) {
          update(undefinedCondition());
          setResult(undefined);
          return;
        }
        if (!retained || !draftRef.current) update(initialCondition(next));
        if (!initialized.current) {
          initialized.current = true;
          void recommend(initialCondition(next), next, value.baseMonth);
        } else if (previous && !retained) {
          generation.current++;
          request.current?.abort();
          busy.current = false;
          setPending(false);
          setResult(undefined);
          setNotice(
            "대상 상태가 변경되어 진행 대상으로 전환했습니다. 조건을 확인하고 추천 받기를 눌러 주세요.",
          );
        }
      })
      .catch((e) => {
        if (!controller.signal.aborted) {
          setTargetError(e);
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [retry]);
  function undefinedCondition(): api.RecommendationCondition {
    return {
      targetType: "OPPORTUNITY",
      targetId: "",
      jobCode: "",
      startMonth: "",
      requestedGradeCode: "SR",
      needSn: null,
    };
  }
  function choose(key: string) {
    const next = targets?.items.find((t) => targetKey(t) === key);
    if (!next || !targets) return;
    selection.current = next;
    setSelected(next);
    setResult(undefined);
    setError(undefined);
    const previous = draftRef.current;
    const condition = {
      ...initialCondition(next),
      ...(previous
        ? {
            jobCode: previous.jobCode,
            needSn:
              next.targetType === "OPPORTUNITY"
                ? (next.needs.find((n) => n.jobCode === previous.jobCode)
                    ?.needSn ??
                  next.needs[0]?.needSn ??
                  null)
                : null,
            startMonth: previous.startMonth,
            requestedGradeCode: previous.requestedGradeCode,
          }
        : {}),
    };
    update(condition);
    void recommend(condition, next, targets.baseMonth, true);
  }
  const disabled = loading || pending || Boolean(targetError) || !selected;
  return (
    <section
      className="recommendation-screen"
      data-testid="workforce-recommendations-screen"
      data-screen-id="UI-30 SCR-UI-30"
    >
      <header className="recommendation-heading">
        <h2>AI 인력 추천</h2>
        <ScreenTitleActions>
          <button
            type="button"
            data-testid="recommendation-history-open"
            onClick={() => setHistory(true)}
          >
            추천 기록
          </button>
        </ScreenTitleActions>
      </header>
      <RecommendationBrowseError
        error={targetError}
        retry={() => setRetry((n) => n + 1)}
      />
      {loading && <p role="status">진행 대상을 불러오는 중입니다.</p>}
      {!loading && !targetError && targets?.items.length === 0 && (
        <p className="recommendation-empty">진행 중인 추천 대상이 없습니다.</p>
      )}
      {selected && draft && (
        <form
          className="recommendation-card"
          onSubmit={(e) => {
            e.preventDefault();
            if (targets && !disabled)
              void recommend(draft, selected, targets.baseMonth);
          }}
        >
          <h2>어떤 자리에 추천할까요?</h2>
          <fieldset disabled={disabled} className="recommendation-filters">
            <label className="sicost-field">
              프로젝트·영업 기회 *
              <select
                data-testid="recommendation-target"
                value={targetKey(selected)}
                onChange={(e) => choose(e.target.value)}
              >
                {targets?.items.map((t) => (
                  <option key={targetKey(t)} value={targetKey(t)}>
                    [{t.targetType === "OPPORTUNITY" ? "기회" : "프로젝트"}]{" "}
                    {t.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="sicost-field">
              직무 *
              <select
                data-testid="recommendation-job"
                value={draft.jobCode}
                aria-invalid={Boolean(fields.jobCode)}
                onChange={(e) => {
                  const jobCode = e.target.value;
                  const need = selected.needs.find(
                    (n) => n.jobCode === jobCode,
                  );
                  update({
                    ...draft,
                    jobCode,
                    needSn:
                      selected.targetType === "OPPORTUNITY"
                        ? (need?.needSn ?? selected.needs[0]?.needSn ?? null)
                        : null,
                  });
                }}
              >
                <option value="">선택</option>
                {jobs.map((job) => (
                  <option key={job.code} value={job.code}>
                    {job.name}
                  </option>
                ))}
              </select>
              {fields.jobCode && (
                <span role="alert" className="sicost-field-error">
                  {fields.jobCode}
                </span>
              )}
            </label>
            {selected.targetType === "OPPORTUNITY" && (
              <label className="sicost-field">
                가배정 자리 *
                <select
                  data-testid="recommendation-need"
                  value={draft.needSn ?? ""}
                  aria-invalid={Boolean(fields.needSn)}
                  onChange={(e) =>
                    update({ ...draft, needSn: e.target.value || null })
                  }
                >
                  <option value="">선택</option>
                  {selected.needs.map((n) => (
                    <option key={n.needSn} value={n.needSn}>
                      {n.needSn}.{" "}
                      {jobs.find((j) => j.code === n.jobCode)?.name ??
                        name("JOB", n.jobCode)}{" "}
                      {n.needCount}명 · {n.startMonth}
                    </option>
                  ))}
                </select>
                {fields.needSn && (
                  <span role="alert" className="sicost-field-error">
                    {fields.needSn}
                  </span>
                )}
              </label>
            )}
            <label className="sicost-field">
              시작월 *
              <input
                type="month"
                data-testid="recommendation-start-month"
                min={targets?.baseMonth}
                max={selected.contractEndDate?.slice(0, 7)}
                value={draft.startMonth}
                aria-invalid={Boolean(fields.startMonth)}
                onChange={(e) =>
                  update({ ...draft, startMonth: e.target.value })
                }
              />
              {fields.startMonth && (
                <span role="alert" className="sicost-field-error">
                  {fields.startMonth}
                </span>
              )}
            </label>
            <label className="sicost-field">
              요청등급 *
              <select
                data-testid="recommendation-grade"
                value={draft.requestedGradeCode}
                onChange={(e) =>
                  update({ ...draft, requestedGradeCode: e.target.value })
                }
              >
                {["JR", "MD", "SR", "EX"].map((code) => (
                  <option key={code} value={code}>
                    {name("GRADE", code)}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="submit"
              data-testid="recommendation-run"
              className="sicost-primary"
            >
              {pending ? "추천 중…" : "추천 받기"}
            </button>
          </fieldset>
          <p className="sicost-banner sicost-tone-info">
            추천은 참고용입니다. 배정은 담당자가 정합니다.
          </p>
        </form>
      )}
      <RecommendationBrowseError
        error={error}
        retry={() => {
          if (draft && selected && targets)
            void recommend(draft, selected, targets.baseMonth);
        }}
      />
      {notice && <p role="status">{notice}</p>}
      {result && (
        <fieldset
          className="recommendation-results"
          disabled={!canAct}
          aria-disabled={!canAct}
          data-testid="recommendation-results"
        >
          {renderRecommendation && selected && targets ? (
            renderRecommendation(result, draft?.needSn ?? null, canAct, {
              target: selected,
              serverDate: targets.serverDate,
              onReset: () => {
                if (targets && selected)
                  void recommend(
                    initialCondition(selected),
                    selected,
                    targets.baseMonth,
                  );
              },
              onRefresh: async () => {
                if (!result.rcmdId) return;
                const current = generation.current;
                const value = await api.getRecommendationHistorySnapshot(
                  result.rcmdId,
                );
                if (active.current && current === generation.current)
                  setResult(value);
              },
            })
          ) : (
            <RecommendationSavedValues value={result} />
          )}
        </fieldset>
      )}
      {result?.exclusions && (
        <section
          className="recommendation-card"
          data-testid="recommendation-exclusions"
        >
          <h2>후보 제외 진단</h2>
          <p>
            전체 후보 {result.exclusions.totalCandidateCount}명 · 고유 제외{" "}
            {result.exclusions.excludedUniqueCount}명
          </p>
          <p>
            한 사람에게 여러 사유가 있을 수 있어 사유별 건수 합과 고유 인원은
            다를 수 있습니다.
          </p>
          <div>
            {result.exclusions.byReason.map((r) => (
              <span className="sicost-badge sicost-tone-neutral" key={r.reason}>
                {r.reason} {r.count}명
              </span>
            ))}
          </div>
          <details>
            <summary>개인 진단</summary>
            {result.exclusions.diagnostics.map((d) => (
              <p key={d.developerId}>
                {d.developerId}: {d.reasons.join(" · ")}
              </p>
            ))}
          </details>
        </section>
      )}
      <button
        type="button"
        data-testid="recommendation-target-refresh"
        disabled={loading || pending}
        onClick={() => setRetry((n) => n + 1)}
      >
        대상 새로고침
      </button>
      {history && (
        <RecommendationHistoryPanel onClose={() => setHistory(false)} />
      )}
    </section>
  );
}
