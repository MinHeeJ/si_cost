import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { ApiError } from "../../shared/api";
import * as api from "./projectProgressApi";

export type ProgressEditorMode =
  | { kind: "initialize" }
  | { kind: "dates" }
  | { kind: "complete"; step: api.ProgressStep }
  | { kind: "memo"; memo?: api.WeekMemo };
export function ProjectProgressEditor({
  data,
  mode,
  onSaved,
  onClose,
  onDirtyChange,
}: {
  data: api.ProjectProgress;
  mode: ProgressEditorMode;
  onSaved(result: api.ProgressMutation): Promise<void>;
  onClose(reload?: boolean): void;
  onDirtyChange?(dirty: boolean): void;
}) {
  const memo = mode.kind === "memo" ? mode.memo : undefined;
  const [draft, setDraft] = useState({
    thisWeek: memo?.thisWeek ?? "",
    nextWeek: memo?.nextWeek ?? "",
    request: memo?.request ?? "",
    actualProgress:
      memo?.actualProgress == null ? "" : String(memo.actualProgress),
  });
  const [dates, setDates] = useState(
    data.steps.map((s) => ({ stepSn: s.stepSn, endDate: s.endDate })),
  );
  const [preview, setPreview] = useState<api.StepPreview>();
  const [pending, setPending] = useState(false),
    [error, setError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({});
  const [dirty, setDirty] = useState(false),
    [leaving, setLeaving] = useState(false);
  const [stale, setStale] = useState(false);
  const inFlight = useRef(false);
  const actualAllowed =
    data.status.contractType === "TK" || data.status.contractType === "IN";
  const title =
    mode.kind === "initialize"
      ? "기본 단계 준비"
      : mode.kind === "dates"
        ? "단계 계획 입력"
        : mode.kind === "complete"
          ? "단계 완료 확인"
          : memo
            ? "주간 메모 수정"
            : "이번 주 상태 적기";
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);
  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, [dirty]);
  function failed(e: unknown) {
    setError(e);
    setFields(e instanceof ApiError ? e.fieldMessages : {});
    if (e instanceof ApiError && e.body.code === "STALE_WRITE") setStale(true);
  }
  function cancel() {
    if (pending) return;
    if (dirty) setLeaving(true);
    else onClose(stale);
  }
  async function calculate() {
    if (inFlight.current) return;
    inFlight.current = true;
    setPending(true);
    setError(undefined);
    setFields({});
    try {
      setPreview(
        await api.createProjectStepDatesPreview(data.projectId, {
          year: data.year,
          steps: dates,
        }),
      );
    } catch (e) {
      failed(e);
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }
  async function save() {
    if (inFlight.current || stale) return;
    setFields({});
    setError(undefined);
    const body = { year: data.year, expectedRevision: data.progressRevision };
    let memoBody: api.MemoWrite | undefined;
    if (mode.kind === "memo") {
      const violations: Record<string, string> = {};
      for (const key of ["thisWeek", "nextWeek", "request"] as const)
        if ([...draft[key].trim()].length > 200)
          violations[key] = "200자 이내로 입력해 주세요.";
      if (
        actualAllowed &&
        draft.actualProgress !== "" &&
        !/^(0|[1-9][0-9]?|100)$/.test(draft.actualProgress)
      )
        violations.actualProgress = "실적은 0~100 정수로 입력해 주세요.";
      if (
        ![draft.thisWeek, draft.nextWeek, draft.request].some((v) =>
          v.trim(),
        ) &&
        (!actualAllowed || draft.actualProgress === "")
      )
        violations.thisWeek =
          "내용 또는 허용된 실적을 하나 이상 입력해 주세요.";
      if (Object.keys(violations).length) {
        setFields(violations);
        return;
      }
      memoBody = {
        ...body,
        thisWeek: draft.thisWeek.trim() || null,
        nextWeek: draft.nextWeek.trim() || null,
        request: draft.request.trim() || null,
      };
      if (actualAllowed)
        memoBody.actualProgress =
          draft.actualProgress === ""
            ? null
            : parseInt(draft.actualProgress, 10);
    }
    inFlight.current = true;
    setPending(true);
    try {
      let result: api.ProgressMutation;
      if (mode.kind === "initialize")
        result = await api.createProjectProgressSteps(data.projectId, body);
      else if (mode.kind === "dates")
        result = await api.saveProjectStepDates(data.projectId, {
          ...body,
          steps: dates,
        });
      else if (mode.kind === "complete")
        result = await api.saveProjectStepCompletion(
          data.projectId,
          mode.step.stepSn,
          body,
        );
      else
        result = memo
          ? await api.putProjectWeekMemo(data.projectId, memo.memoSn, memoBody!)
          : await api.createProjectWeekMemo(data.projectId, memoBody!);
      setDirty(false);
      onDirtyChange?.(false);
      await onSaved(result);
    } catch (e) {
      failed(e);
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }
  return (
    <>
      <Dialog
        open
        title={title}
        pending={pending}
        confirmDisabled={stale || data.readOnly}
        confirmLabel={
          mode.kind === "initialize"
            ? "초기화"
            : mode.kind === "dates"
              ? "전체 저장"
              : mode.kind === "complete"
                ? "완료"
                : "저장"
        }
        onConfirm={save}
        onCancel={cancel}
        wide={mode.kind === "dates"}
      >
        <section
          data-testid="progress-editor"
          data-screen-id="SCR-UI-01"
          className="project-progress-editor"
        >
          <p>
            {data.status.projectName} · {data.projectId}
          </p>
          {error != null && (
            <div role="alert" className="sicost-banner sicost-tone-bad">
              <span>
                {error instanceof Error
                  ? error.message
                  : "처리하지 못했습니다."}
                {error instanceof ApiError && (
                  <small> 문의 번호: {error.body.requestId}</small>
                )}
              </span>
              {stale && (
                <>
                  <span>
                    다른 변경이 있습니다. 입력 내용을 확인하고 취소 후 다시
                    조회해 주세요.
                  </span>
                  <button
                    type="button"
                    data-testid="progress-stale-reload"
                    disabled={pending}
                    onClick={cancel}
                  >
                    다시 조회
                  </button>
                </>
              )}
            </div>
          )}
          {mode.kind === "initialize" && (
            <>
              <p>
                없는 기본 단계 집합만 준비합니다. 기존 단계/완료일은 덮어쓰지
                않습니다.
              </p>
              <p>{data.initializationMessage}</p>
            </>
          )}
          {mode.kind === "complete" && (
            <>
              <p>
                {mode.step.stepName} · 완료일은 오늘로 기록됩니다.{" "}
                {data.status.baseDate}
              </p>
              <p>단계 완료가 TK/IN 실적 진척률을 자동 증가시키지 않습니다.</p>
            </>
          )}
          {mode.kind === "dates" && (
            <>
              <p>
                첫 시작/마지막 종료는 계약기간 고정·완료 단계 날짜 수정 불가
              </p>
              <div className="sicost-table-viewport">
                <table className="sicost-table">
                  <thead>
                    <tr>
                      {[
                        "단계",
                        "비중",
                        "계획 시작일",
                        "계획 종료일",
                        "완료일",
                        "상태",
                      ].map((label) => (
                        <th key={label}>{label}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {data.steps.map((s, index) => (
                      <tr
                        key={s.stepSn}
                        data-testid={`progress-date-row-${s.stepSn}`}
                      >
                        <td>{s.stepName}</td>
                        <td>{s.weight}%</td>
                        <td>
                          {preview?.steps[index]?.startDate ?? s.startDate}
                        </td>
                        <td>
                          <input
                            data-testid={`progress-end-date-${s.stepSn}`}
                            aria-label={`${s.stepName} 계획 종료일`}
                            type="date"
                            value={dates[index].endDate}
                            disabled={
                              pending ||
                              s.completedDate !== null ||
                              index === data.steps.length - 1
                            }
                            aria-invalid={Boolean(fields.steps)}
                            onChange={(e) => {
                              setDates((values) =>
                                values.map((v, i) =>
                                  i === index
                                    ? { ...v, endDate: e.target.value }
                                    : v,
                                ),
                              );
                              setPreview(undefined);
                              setDirty(true);
                            }}
                          />
                          {fields.steps && (
                            <small className="sicost-field-error">
                              {fields.steps}
                            </small>
                          )}
                        </td>
                        <td>{s.completedDate ?? "–"}</td>
                        <td>{s.status}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <button
                type="button"
                data-testid="progress-dates-preview"
                disabled={pending || stale}
                onClick={() => void calculate()}
              >
                미리보기
              </button>
              {preview && (
                <p role="status">
                  미리보기 · 저장되지 않음 ·{" "}
                  {preview.steps
                    .map((s) => `${s.stepName} ${s.startDate} ~ ${s.endDate}`)
                    .join(" / ")}
                </p>
              )}
            </>
          )}
          {mode.kind === "memo" && (
            <>
              {memo && (
                <p>
                  작성 {memo.writtenDate} {memo.registeredBy.userName} ·{" "}
                  {memo.registeredAt} (변경 불가)
                </p>
              )}
              {(
                [
                  ["thisWeek", "이번 주 한 일"],
                  ["nextWeek", "다음 주 할 일"],
                  ["request", "요청 사항"],
                ] as const
              ).map(([key, label]) => (
                <label className="sicost-field" key={key}>
                  {label}
                  <textarea
                    data-testid={`progress-memo-${key.replace(/[A-Z]/g, (v) => `-${v.toLowerCase()}`)}`}
                    aria-label={label}
                    aria-invalid={Boolean(fields[key])}
                    value={draft[key]}
                    disabled={pending}
                    onChange={(e) => {
                      setDraft({ ...draft, [key]: e.target.value });
                      setDirty(true);
                    }}
                  />
                  {fields[key] && (
                    <small className="sicost-field-error">{fields[key]}</small>
                  )}
                </label>
              ))}
              {actualAllowed && (
                <label className="sicost-field">
                  실적 진척률
                  <input
                    data-testid="progress-memo-actual"
                    aria-label="실적 진척률"
                    inputMode="numeric"
                    value={draft.actualProgress}
                    aria-invalid={Boolean(fields.actualProgress)}
                    disabled={pending}
                    onChange={(e) => {
                      setDraft({ ...draft, actualProgress: e.target.value });
                      setDirty(true);
                    }}
                  />
                  {fields.actualProgress && (
                    <small className="sicost-field-error">
                      {fields.actualProgress}
                    </small>
                  )}
                </label>
              )}
              <p>
                현재 실적 {data.status.actualProgress}% · 작성일과 작성자는
                서버에서 기록합니다.
              </p>
            </>
          )}
        </section>
      </Dialog>
      <Dialog
        open={leaving}
        title="작성 중인 내용을 나가시겠습니까?"
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onConfirm={() => {
          setDirty(false);
          onDirtyChange?.(false);
          onClose(stale);
        }}
        onCancel={() => setLeaving(false)}
      >
        <p>저장하지 않은 변경은 반영되지 않습니다.</p>
      </Dialog>
    </>
  );
}
