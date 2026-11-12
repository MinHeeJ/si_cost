import { useEffect, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import * as api from "./assignmentActionsApi";

export const changeLabels = {
  EXT: "연장",
  EARLY: "조기철수",
  UPRC: "단가",
  RATE: "월MM",
  EXTP: "연장가능성",
  KIND: "투입구분",
};
export function AssignmentError({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  return error ? (
    <ErrorBanner
      message={
        error instanceof Error ? error.message : "요청을 처리하지 못했습니다."
      }
      requestId={error instanceof ApiError ? error.body.requestId : undefined}
      onRetry={retry}
    />
  ) : null;
}
export function AssignmentWarnings({
  warnings,
  protectedMonths,
}: {
  warnings: api.Warning[];
  protectedMonths: string[];
}) {
  return (
    <div data-testid="assignment-warnings">
      {warnings.map((w, i) => (
        <p
          data-testid={`assignment-warning-${w.code.toLowerCase().replace(/_/g, "-")}-${i}`}
          key={`${w.code}-${i}`}
          className="sicost-banner sicost-tone-warn"
        >
          {w.message} {w.months.join(", ")}
        </p>
      ))}
      {protectedMonths.length > 0 && (
        <p className="sicost-banner sicost-tone-lock">
          보호월 {protectedMonths.join(", ")} · 마감한 달 값은 유지하고 미마감
          달부터 반영합니다. 과거 차액은 전월 조정으로 입력하세요.
        </p>
      )}
    </div>
  );
}
export function ReasonFields({
  reason,
  memo,
  onChange,
  disabled,
  error,
}: {
  reason: string;
  memo: string;
  onChange(field: "reason" | "memo", value: string): void;
  disabled?: boolean;
  error?: Record<string, string>;
}) {
  const [codes, setCodes] = useState<SharedCodeLookup[]>([]),
    [failure, setFailure] = useState<unknown>();
  useEffect(() => {
    let live = true;
    void getSharedCodes("ASGN_CHG_RSN")
      .then((v) => {
        if (live) setCodes(v.items);
      })
      .catch((e) => {
        if (live) setFailure(e);
      });
    return () => {
      live = false;
    };
  }, []);
  return (
    <>
      <AssignmentError error={failure} />
      <label className="sicost-field">
        사유
        <select
          data-testid="assignment-reason"
          disabled={disabled}
          value={reason}
          onChange={(e) => onChange("reason", e.target.value)}
          aria-invalid={Boolean(error?.reasonCode)}
        >
          <option value="">선택해 주세요</option>
          {codes.map((c) => (
            <option
              data-testid={`assignment-reason-option-${c.code.toLowerCase().replace(/_/g, "-")}`}
              key={c.code}
              value={c.code}
            >
              {c.name}
            </option>
          ))}
        </select>
        {error?.reasonCode && <span role="alert">{error.reasonCode}</span>}
      </label>
      <label className="sicost-field">
        메모
        <textarea
          data-testid="assignment-memo"
          disabled={disabled}
          value={memo}
          onChange={(e) => onChange("memo", e.target.value)}
          aria-invalid={Boolean(error?.memo)}
        />
        {error?.memo && <span role="alert">{error.memo}</span>}
      </label>
    </>
  );
}
export function AssignmentChangeDialog({
  assignment,
  year,
  today,
  onClose,
  onChanged,
  onDirtyChange,
}: {
  assignment: api.Assignment;
  year: number;
  today: string;
  onClose(): void;
  onChanged(): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [type, setType] = useState<keyof typeof changeLabels>("EXT"),
    [date, setDate] = useState(today),
    [month, setMonth] = useState(today.slice(0, 7)),
    [value, setValue] = useState(""),
    [reason, setReason] = useState(""),
    [memo, setMemo] = useState("");
  const [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({}),
    [preview, setPreview] = useState<api.Preview>(),
    [stale, setStale] = useState(false),
    [leaving, setLeaving] = useState(false);
  const { pending, message, commit } = useCommitRefresh();
  useEffect(() => {
    onDirtyChange?.(true);
    return () => onDirtyChange?.(false);
  }, [onDirtyChange]);
  const body = (): api.Change => {
    const common = {
      year,
      expectedRevision: assignment.revision,
      ...(reason ? { reasonCode: reason } : {}),
      ...(memo ? { memo } : {}),
    };
    switch (type) {
      case "EXT":
        return { ...common, type, newEndDate: date };
      case "EARLY":
        return { ...common, type, withdrawalDate: date };
      case "UPRC":
        return { ...common, type, applyMonth: month, newUnitPrice: value };
      case "RATE":
        return { ...common, type, applyMonth: month, newContractMm: value };
      case "EXTP":
        return {
          ...common,
          type,
          applyDate: date,
          extensionPossibility: value || null,
        };
      case "KIND":
        return {
          ...common,
          type,
          applyDate: date,
          inputType: value as "NEW" | "ADD",
        };
    }
  };
  async function submit() {
    setError(undefined);
    setFields({});
    try {
      if (!preview) {
        setPreview(await api.preview({ id: assignment.id, change: body() }));
        return;
      }
      await commit(async () => {
        await api.changeAssignment(assignment.id, body());
        onClose();
      }, onChanged);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        if (e.body.code === "STALE_WRITE") setStale(true);
      }
    }
  }
  const field = (
    label: string,
    name: string,
    kind: string,
    current: string,
    set: (v: string) => void,
  ) => (
    <label className="sicost-field">
      {label}
      <input
        data-testid={`assignment-change-${name}`}
        type={kind}
        value={current}
        disabled={pending || Boolean(preview)}
        onChange={(e) => set(e.target.value)}
        aria-invalid={Boolean(fields[name])}
      />
      {fields[name] && <span role="alert">{fields[name]}</span>}
    </label>
  );
  return (
    <>
      <Dialog
        open
        title={preview ? "보호월 영향 확인" : "변경 기록"}
        pending={pending}
        onCancel={() => setLeaving(true)}
        onConfirm={submit}
        confirmLabel={preview ? "확인 후 저장" : "저장"}
        confirmDisabled={stale}
      >
        <div data-testid="assignment-change-dialog">
          <p>
            {assignment.id} · YEAR {year} · {assignment.startDate} ~{" "}
            {assignment.endDate}
          </p>
          <AssignmentError error={error} />
          {message && <p role="status">{message}</p>}
          {stale && (
            <p role="alert">
              다시 조회한 뒤 변경 기록을 열어 주세요. 자동으로 재저장하지
              않습니다.
            </p>
          )}
          <label className="sicost-field">
            변경 종류
            <select
              data-testid="assignment-change-type"
              disabled={pending || Boolean(preview)}
              value={type}
              onChange={(e) => {
                setType(e.target.value as keyof typeof changeLabels);
                setValue("");
              }}
            >
              {Object.entries(changeLabels).map(([key, label]) => (
                <option
                  data-testid={`assignment-change-type-option-${key.toLowerCase()}`}
                  key={key}
                  value={key}
                >
                  {label}
                </option>
              ))}
            </select>
          </label>
          {type === "EXT" && <p>연장 시작일은 기존 종료일 다음 날입니다.</p>}
          {type === "RATE" || type === "UPRC"
            ? field("적용월", "applyMonth", "month", month, setMonth)
            : field(
                type === "EXT"
                  ? "새 종료일"
                  : type === "EARLY"
                    ? "철수일"
                    : "바꾼날",
                type === "EXT"
                  ? "newEndDate"
                  : type === "EARLY"
                    ? "withdrawalDate"
                    : "applyDate",
                "date",
                date,
                setDate,
              )}
          {(type === "RATE" || type === "UPRC") &&
            field(
              type === "RATE" ? "새 계약MM" : "새 단가 (원)",
              type === "RATE" ? "newContractMm" : "newUnitPrice",
              "text",
              value,
              setValue,
            )}
          {(type === "EXTP" || type === "KIND") && (
            <label className="sicost-field">
              {type === "EXTP" ? "연장가능성" : "투입구분"}
              <select
                data-testid="assignment-change-value"
                value={value}
                disabled={pending || Boolean(preview)}
                onChange={(e) => setValue(e.target.value)}
              >
                {type === "EXTP" ? (
                  <>
                    <option value="">미정</option>
                    <option value="H">높음</option>
                    <option value="M">보통</option>
                    <option value="L">낮음</option>
                  </>
                ) : (
                  <>
                    <option value="">선택해 주세요</option>
                    <option value="NEW">신규</option>
                    <option value="ADD">추가</option>
                  </>
                )}
              </select>
            </label>
          )}
          <ReasonFields
            reason={reason}
            memo={memo}
            disabled={pending || Boolean(preview)}
            error={fields}
            onChange={(k, v) => (k === "reason" ? setReason(v) : setMemo(v))}
          />
          {preview && (
            <>
              <AssignmentWarnings
                warnings={preview.warnings}
                protectedMonths={preview.protectedMonths}
              />
              <p>영향월 {preview.affectedMonths.join(", ")}</p>
              <button
                type="button"
                data-testid="assignment-change-edit"
                onClick={() => setPreview(undefined)}
              >
                입력 수정
              </button>
            </>
          )}
        </div>
      </Dialog>
      <Dialog
        open={leaving}
        title="작성 중인 내용을 나가시겠습니까?"
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
      >
        <p>자동 저장하지 않습니다. 입력은 확인 전까지 유지됩니다.</p>
      </Dialog>
    </>
  );
}
export function AssignmentBulkDialog({
  selection,
  year,
  onClose,
  onChanged,
}: {
  selection: api.Selection;
  year: number;
  onClose(): void;
  onChanged(): Promise<void>;
}) {
  const [type, setType] = useState<"EXTP" | "CNCL">("EXTP"),
    [value, setValue] = useState(""),
    [reason, setReason] = useState(""),
    [memo, setMemo] = useState(""),
    [items, setItems] = useState(selection.items),
    [result, setResult] = useState<api.BulkResult>(),
    [error, setError] = useState<unknown>();
  const { pending, message, commit } = useCommitRefresh();
  async function submit() {
    if (result) {
      onClose();
      return;
    }
    setError(undefined);
    try {
      await commit(
        async () =>
          setResult(
            await api.saveBulk({
              year,
              type,
              items,
              ...(type === "EXTP"
                ? { extensionPossibility: value || null }
                : {}),
              ...(reason ? { reasonCode: reason } : {}),
              ...(memo ? { memo } : {}),
            }),
          ),
        onChanged,
      );
    } catch (e) {
      setError(e);
    }
  }
  return (
    <Dialog
      open
      title={result ? "일괄 결과" : "일괄 처리 확인"}
      pending={pending}
      confirmLabel={result ? "확인" : "확인 후 처리"}
      confirmDisabled={!result && !items.length}
      onConfirm={submit}
      onCancel={onClose}
    >
      <div data-testid="assignment-bulk-dialog">
        <AssignmentError error={error} />
        {message && <p role="status">{message}</p>}
        {result ? (
          <>
            <p>
              요청 {result.requested} = applied {result.applied} + noChange{" "}
              {result.noChange} + rejected {result.rejected}
            </p>
            {result.items.map((r) => (
              <p key={r.id} data-testid={`bulk-result-${r.id.toLowerCase()}`}>
                {r.id} · {r.outcome} · {r.error?.message}{" "}
                {r.protectedMonths.join(", ")}
              </p>
            ))}
          </>
        ) : (
          <>
            <p>
              대상 {items.length}건 · 보호 영향월{" "}
              {selection.protectedMonths.join(", ") || "없음"}
            </p>
            {items.map((i) => (
              <label
                data-testid={`bulk-candidate-${i.id.toLowerCase()}`}
                key={i.id}
              >
                <input
                  data-testid={`bulk-exclude-${i.id.toLowerCase()}`}
                  type="checkbox"
                  checked
                  onChange={() =>
                    setItems((old) => old.filter((v) => v.id !== i.id))
                  }
                />
                {i.id}
              </label>
            ))}
            <label className="sicost-field">
              처리 종류
              <select
                data-testid="bulk-type"
                value={type}
                onChange={(e) => setType(e.target.value as typeof type)}
              >
                <option value="EXTP">연장 가능성 변경</option>
                <option value="CNCL">배정 취소</option>
              </select>
            </label>
            {type === "EXTP" && (
              <label className="sicost-field">
                연장 가능성
                <select
                  data-testid="bulk-possibility"
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                >
                  <option value="">미정</option>
                  <option value="H">높음</option>
                  <option value="M">보통</option>
                  <option value="L">낮음</option>
                </select>
              </label>
            )}
            <ReasonFields
              reason={reason}
              memo={memo}
              onChange={(k, v) => (k === "reason" ? setReason(v) : setMemo(v))}
            />
            <p className="sicost-banner sicost-tone-warn">
              취소 시 보호월과 원천은 보존하며 복원·삭제하지 않습니다. 지난
              투입기간 또는 보호월에 영향이 있으면 사유가 필요합니다. 같은 값은
              noChange입니다.
            </p>
          </>
        )}
      </div>
    </Dialog>
  );
}
