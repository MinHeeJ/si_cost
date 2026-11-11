import { useEffect, useRef, useState } from "react";
import { Dialog, ConfirmDialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import * as api from "./developerStatesApi";

export type DeveloperStateAction =
  | { kind: "create" }
  | { kind: "end" | "cancel"; row: api.DeveloperState };
export function DeveloperStateDialog({
  developerId,
  revision,
  hireDate,
  action,
  onClose,
  onCommitted,
  onDirtyChange,
}: {
  developerId: string;
  revision: string;
  hireDate: string | null;
  action: DeveloperStateAction;
  onClose(): void;
  onCommitted(
    warnings: api.StateWarning[],
    changed: boolean,
  ): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [state, setState] = useState<api.DeveloperState["state"]>("LEAVE");
  const [startDate, setStartDate] = useState("");
  const initialEnd = action.kind === "end" ? (action.row.endDate ?? "") : "";
  const [endDate, setEndDate] = useState(initialEnd),
    [reason, setReason] = useState("");
  const [pending, setPending] = useState(false),
    [discard, setDiscard] = useState(false);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>();
  const saving = useRef(false);
  const dirty =
    state !== "LEAVE" || Boolean(startDate || reason) || endDate !== initialEnd;
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const title =
    action.kind === "create"
      ? "근무 상태 등록"
      : action.kind === "end"
        ? "휴직 종료일 수정"
        : action.row.state === "WORK"
          ? "재직 취소 확인"
          : "퇴사 취소 확인";
  function close() {
    if (!saving.current) {
      if (dirty) setDiscard(true);
      else onClose();
    }
  }
  async function save() {
    if (saving.current) return;
    const next: Record<string, string> = {};
    if (action.kind === "create") {
      if (!startDate || !/^(?!0000)\d{4}-\d{2}-\d{2}$/.test(startDate))
        next.startDate = "시작일을 YYYY-MM-DD로 입력해 주세요.";
      else if (hireDate && startDate < hireDate)
        next.startDate = "입사일 이후의 날짜를 입력해 주세요.";
      if (state === "LEAVE" && endDate && endDate < startDate)
        next.endDate = "종료일은 시작일 이후여야 합니다.";
    }
    if (action.kind === "end" && endDate && endDate < action.row.startDate)
      next.endDate = "종료일은 시작일 이후여야 합니다.";
    if (
      Array.from(reason.trim()).length > 300 ||
      (action.kind === "cancel" &&
        action.row.state === "RETIRE" &&
        !reason.trim())
    )
      next.reason = "퇴사 취소 사유는 1~300자로 입력해 주세요.";
    setFields(next);
    setError(undefined);
    if (Object.keys(next).length) return;
    saving.current = true;
    setPending(true);
    try {
      let warnings: api.StateWarning[] = [],
        changed = true;
      if (action.kind === "create") {
        const body: api.CreateDeveloperState = {
          state,
          startDate,
          reason: reason.trim() || null,
          expectedRevision: revision,
        };
        if (state === "LEAVE") body.endDate = endDate || null;
        warnings = (await api.createDeveloperState(developerId, body)).warnings;
      } else if (action.kind === "end") {
        changed = (
          await api.putDeveloperStateEnd(developerId, action.row.sn, {
            endDate: endDate || null,
            expectedRevision: revision,
          })
        ).changed;
      } else {
        changed = (
          await api.saveDeveloperStateCancel(developerId, action.row.sn, {
            reason: reason.trim() || null,
            expectedRevision: revision,
          })
        ).changed;
      }
      // The parent owns refresh failures. Once committed this form must not issue the write again.
      await onCommitted(warnings, changed);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) setFields(e.fieldMessages);
    } finally {
      saving.current = false;
      setPending(false);
    }
  }
  const requestError = error instanceof ApiError ? error : undefined;
  const details = requestError?.body.details as
    | {
        blockingMonths?: string[];
        projects?: { name: string; startDate: string; endDate: string }[];
        referenceCount?: number;
      }
    | undefined;
  const fieldError = (name: string) =>
    fields[name] && <span className="sicost-field-error">{fields[name]}</span>;
  return (
    <>
      <Dialog
        open={!discard}
        title={title}
        pending={pending}
        onConfirm={save}
        onCancel={close}
        confirmLabel={action.kind === "cancel" ? title : "저장"}
      >
        <div
          className="developer-state-form"
          data-testid="developer-state-dialog"
        >
          {action.kind !== "create" && (
            <p>
              {developerId} / {action.row.sn} ·{" "}
              {action.row.state === "LEAVE"
                ? "휴직"
                : action.row.state === "WORK"
                  ? "재직"
                  : "퇴사"}{" "}
              · {action.row.startDate}
            </p>
          )}
          {action.kind === "create" && (
            <>
              <label className="sicost-field">
                상태 *
                <select
                  data-testid="developer-state-type"
                  disabled={pending}
                  value={state}
                  onChange={(e) =>
                    setState(e.target.value as api.DeveloperState["state"])
                  }
                >
                  <option value="LEAVE">휴직</option>
                  <option value="WORK">재직</option>
                  <option value="RETIRE">퇴사</option>
                </select>
              </label>
              <label className="sicost-field">
                {state === "WORK"
                  ? "복귀일"
                  : state === "RETIRE"
                    ? "마지막 근무일"
                    : "시작일"}{" "}
                *
                <input
                  data-testid="developer-state-start"
                  name="startDate"
                  type="date"
                  disabled={pending}
                  value={startDate}
                  min={hireDate ?? undefined}
                  aria-invalid={Boolean(fields.startDate)}
                  onChange={(e) => setStartDate(e.target.value)}
                />
                {fieldError("startDate")}
              </label>
            </>
          )}
          {(action.kind === "end" ||
            (action.kind === "create" && state === "LEAVE")) && (
            <label className="sicost-field">
              종료일 (미정은 비워 두세요)
              <input
                data-testid="developer-state-end"
                name="endDate"
                type="date"
                disabled={pending}
                value={endDate}
                aria-invalid={Boolean(fields.endDate)}
                onChange={(e) => setEndDate(e.target.value)}
              />
              {fieldError("endDate")}
            </label>
          )}
          {action.kind !== "end" && (
            <label className="sicost-field">
              사유
              {action.kind === "cancel" && action.row.state === "RETIRE"
                ? " *"
                : " (선택)"}
              <textarea
                data-testid="developer-state-reason"
                name="reason"
                aria-label={
                  action.kind === "cancel" && action.row.state === "RETIRE"
                    ? "사유 *"
                    : "사유 (선택)"
                }
                disabled={pending}
                value={reason}
                aria-invalid={Boolean(fields.reason)}
                onChange={(e) => setReason(e.target.value)}
              />
              {fieldError("reason")}
            </label>
          )}
          {action.kind === "create" && state === "WORK" && (
            <p className="developer-state-guidance">
              휴직 중인 사람만 재직으로 등록할 수 있습니다. 복귀일은 휴직 시작일
              초과, 원래 종료일 다음 날까지입니다. 이후 날짜는 종료일 수정을
              쓰세요.
            </p>
          )}
          {action.kind === "create" && state === "RETIRE" && (
            <p className="developer-state-guidance">
              퇴사일 이후의 ACT 투입은 먼저 조기 철수해 주세요. 휴직은 퇴사
              전날로 닫힙니다.
            </p>
          )}
          {action.kind === "cancel" && (
            <p className="developer-state-guidance">
              휴직 종료일과 연결을 복원하고 전체 이력을 검사합니다.
              {action.row.state === "RETIRE" &&
                " 퇴사일 비우기·다시 사용·근무 상태 재산출을 함께 처리합니다."}
            </p>
          )}
          {Boolean(error) && (
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "저장하지 못했습니다. 입력한 내용은 유지됩니다."
              }
              requestId={requestError?.body.requestId}
            />
          )}
          {details?.blockingMonths && (
            <p>차단월: {details.blockingMonths.join(", ")}</p>
          )}
          {details?.projects?.map((p) => (
            <p key={`${p.name}-${p.startDate}`}>
              {p.name} · {p.startDate} ~ {p.endDate}
            </p>
          ))}
        </div>
      </Dialog>
      <ConfirmDialog
        open={discard}
        title="작성 중인 내용이 있습니다"
        onConfirm={onClose}
        onCancel={() => setDiscard(false)}
        confirmLabel="나가기"
        cancelLabel="계속 작성"
      >
        <p>나가기를 선택한 경우에만 입력을 버립니다.</p>
      </ConfirmDialog>
    </>
  );
}
