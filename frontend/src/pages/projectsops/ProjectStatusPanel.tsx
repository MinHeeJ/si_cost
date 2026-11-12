import "./projectStatus.css";
import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import {
  getPeriodContext,
  getSharedCodes,
  type PeriodContext,
  type SharedCodeLookup,
} from "../shared/sharedApi";
import * as api from "./projectStatusApi";

export type ProjectStatusPanelProps = {
  projectId: string;
  name: string;
  effectiveStatus: api.ProjectStatus;
  revision: string;
  year: number;
  onChanged(result: api.ProjectStatusResult): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
};
export function ProjectStatusPanel(props: ProjectStatusPanelProps) {
  return (
    <StatusContent
      key={`${props.projectId}:${props.year}:${props.revision}`}
      {...props}
    />
  );
}
function StatusContent({
  projectId,
  name,
  effectiveStatus,
  revision,
  year,
  onChanged,
  onDirtyChange,
}: ProjectStatusPanelProps) {
  const [period, setPeriod] = useState<PeriodContext>();
  const [mode, setMode] = useState<"progress" | "loss">();
  const [preview, setPreview] = useState<api.ProjectLossPreview>();
  const [codes, setCodes] = useState<SharedCodeLookup[]>([]);
  const [reason, setReason] = useState("");
  const [memo, setMemo] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({});
  const [stale, setStale] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [saved, setSaved] = useState<api.ProjectStatusResult>();
  const generation = useRef(0);
  const { pending, message, commit } = useCommitRefresh();
  const dirty = mode === "loss" && Boolean(reason || memo);
  useEffect(() => {
    let alive = true;
    void getPeriodContext()
      .then((p) => {
        if (alive) setPeriod(p);
      })
      .catch((e) => {
        if (alive) setError(e);
      });
    return () => {
      alive = false;
    };
  }, []);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(
    () => () => {
      generation.current++;
    },
    [],
  );
  const readonly =
    effectiveStatus !== "PRE" ||
    !period ||
    year < Number(period.currentDate.slice(0, 4));
  const errorMessage =
    error instanceof Error ? error.message : "요청을 처리하지 못했습니다.";
  async function loadPreview() {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    setPreview(undefined);
    try {
      const [value, reasons] = await Promise.all([
        api.getProjectLossPreview(projectId),
        getSharedCodes("LOSE_RSN"),
      ]);
      if (version !== generation.current) return;
      setPreview(value);
      setCodes(reasons.items);
      setStale(false);
    } catch (e) {
      if (version === generation.current) setError(e);
    } finally {
      if (version === generation.current) setLoading(false);
    }
  }
  function close() {
    if (pending) return;
    if (dirty) setLeaving(true);
    else {
      generation.current++;
      setMode(undefined);
    }
  }
  async function save() {
    if (
      readonly ||
      pending ||
      stale ||
      (mode === "loss" && (!preview || loading))
    )
      return;
    const validation: Record<string, string> = {};
    if (mode === "loss" && !codes.some((c) => c.code === reason))
      validation.lossReason = "활성 실주 사유를 선택해 주세요.";
    if ([...memo.trim()].length > 500)
      validation.lossMemo = "메모는 500자 이하로 입력해 주세요.";
    setFields(validation);
    if (Object.keys(validation).length) return;
    setError(undefined);
    try {
      let result: api.ProjectStatusResult;
      await commit(
        async () => {
          result =
            mode === "progress"
              ? await api.saveProjectProgress(projectId, {
                  year,
                  expectedRevision: revision,
                })
              : await api.saveProjectLoss(projectId, {
                  year,
                  expectedRevision: preview!.revision,
                  previewToken: preview!.previewToken,
                  lossReason: reason,
                  lossMemo: memo.trim() || null,
                });
          setSaved(result);
          setMode(undefined);
          setReason("");
          setMemo("");
          onDirtyChange?.(false);
        },
        async () => {
          await onChanged(result!);
          setSaved(undefined);
        },
      );
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        if (
          ["STALE_WRITE", "CLOSED_MONTH", "INVALID_PROJECT_STATUS"].includes(
            e.body.code,
          )
        ) {
          setStale(true);
          setPreview(undefined);
        }
      }
    }
  }
  const columns: Column<api.LossAssignment>[] = [
    { id: "managementNo", label: "관리번호", render: (a) => a.managementNo },
    { id: "name", label: "이름", render: (a) => a.name },
    { id: "job", label: "직무", render: (a) => a.jobName },
    {
      id: "period",
      label: "투입 기간",
      render: (a) => `${a.startDate} ~ ${a.endDate}`,
    },
  ];
  return (
    <section
      data-testid="project-status-panel"
      data-screen-id="SCR-UI-01"
      aria-label="프로젝트 상태 전환"
    >
      {effectiveStatus === "PRE" && (
        <div className="project-status-actions">
          <button
            type="button"
            className="sicost-primary"
            data-testid="project-progress-button"
            disabled={readonly || pending || Boolean(saved)}
            onClick={() => {
              setError(undefined);
              setStale(false);
              setMode("progress");
            }}
          >
            진행 처리
          </button>
          <button
            type="button"
            className="project-loss-trigger"
            data-testid="project-loss-button"
            disabled={readonly || pending || Boolean(saved)}
            onClick={() => {
              setMode("loss");
              void loadPreview();
            }}
          >
            실주 처리
          </button>
        </div>
      )}
      {effectiveStatus === "LOSE" && (
        <p className="sicost-banner sicost-tone-lock">
          실주 프로젝트는 읽기 전용입니다. 실주 취소·복원은 제공하지 않습니다.
        </p>
      )}
      {period && year < Number(period.currentDate.slice(0, 4)) && (
        <p className="sicost-banner sicost-tone-lock">
          지난 연도는 조회만 할 수 있습니다.
        </p>
      )}
      {message && <p role="status">{message}</p>}
      {saved && (
        <button
          type="button"
          data-testid="project-status-refresh"
          disabled={pending}
          onClick={() => {
            void Promise.resolve(onChanged(saved))
              .then(() => setSaved(undefined))
              .catch(setError);
          }}
        >
          다시 조회
        </button>
      )}
      {Boolean(error) && (
        <ErrorBanner
          message={errorMessage}
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
        />
      )}
      <Dialog
        open={mode === "progress"}
        title="프로젝트 진행 처리"
        pending={pending}
        onCancel={close}
        onConfirm={save}
        confirmDisabled={readonly || stale}
        confirmLabel="진행 확인"
      >
        <div data-testid="project-progress-dialog">
          <p>
            {projectId} / {name}
          </p>
          <p>수주예정(PRE) → 진행(ING)</p>
          <p>금액/예산/원계약금액/배정/영업기회는 그대로입니다.</p>
        </div>
      </Dialog>
      <Dialog
        open={mode === "loss"}
        title="실주 처리"
        wide
        pending={pending}
        onCancel={close}
        onConfirm={save}
        confirmDisabled={readonly || stale || loading || !preview}
        confirmLabel="실주 확정"
      >
        <div className="project-loss-form" data-testid="project-loss-dialog">
          <p className="project-loss-target">
            <strong>{projectId}</strong> / {name}
          </p>
          <label className="sicost-field">
            <span className="project-loss-label">
              실주 사유 <em>*</em>
            </span>
            <select
              data-testid="project-loss-reason"
              value={reason}
              aria-invalid={Boolean(fields.lossReason)}
              onChange={(e) => {
                setReason(e.target.value);
                setFields((f) => ({ ...f, lossReason: "" }));
              }}
            >
              <option value="">선택</option>
              {codes.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          {fields.lossReason && (
            <p className="sicost-field-error">{fields.lossReason}</p>
          )}
          <label className="sicost-field">
            <span className="project-loss-label">메모</span>
            <textarea
              data-testid="project-loss-memo"
              rows={3}
              placeholder="선택 입력 · 실주 사유를 보충할 내용을 적어 주세요."
              value={memo}
              aria-invalid={Boolean(fields.lossMemo)}
              onChange={(e) => setMemo(e.target.value)}
            />
          </label>
          {fields.lossMemo && (
            <p className="sicost-field-error">{fields.lossMemo}</p>
          )}
          <div className="project-loss-preview-bar">
            <span className="project-loss-hint">
              {preview
                ? `기준일 ${preview.basisDate} (서버 오늘)`
                : "확정 전에 영향받는 투입 인원을 확인하세요."}
            </span>
            <button
              type="button"
              data-testid="project-loss-preview"
              disabled={loading || pending}
              onClick={() => void loadPreview()}
            >
              영향 미리보기
            </button>
          </div>
          {loading && <p role="status">불러오는 중입니다.</p>}
          {preview && (
            <>
              <h3>
                <StatusBadge tone="warn">현재 투입</StatusBadge>{" "}
                {preview.currentPersonCount}명 /{" "}
                {preview.currentAssignmentCount}건
              </h3>
              <DataTable
                rows={preview.currentAssignments}
                columns={columns}
                rowKey={(a) => a.assignmentId}
                empty="현재 투입 배정이 없습니다."
              />
              <h3>
                <StatusBadge tone="info">미래 투입</StatusBadge>{" "}
                {preview.futurePersonCount}명 / {preview.futureAssignmentCount}
                건
              </h3>
              <DataTable
                rows={preview.futureAssignments}
                columns={columns}
                rowKey={(a) => a.assignmentId}
                empty="미래 투입 배정이 없습니다."
              />
            </>
          )}
          <p className="sicost-banner sicost-tone-warn">
            현재 배정은 조기철수, 미래 배정은 취소됩니다. 미마감 월/MM을
            정리하고 실주 후 읽기 전용이 됩니다.
          </p>
          {stale && (
            <p role="alert">
              상태 또는 배정이 변경되었습니다. 다시 조회하고 영향을 확인해
              주세요. 자동으로 재저장하지 않습니다.
            </p>
          )}
        </div>
      </Dialog>
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        onCancel={() => setLeaving(false)}
        onConfirm={() => {
          generation.current++;
          setLeaving(false);
          setMode(undefined);
          setMemo("");
          setReason("");
        }}
        cancelLabel="계속 작성"
        confirmLabel="나가기"
      >
        <p>입력한 실주 사유와 메모를 버리고 나가시겠습니까?</p>
      </Dialog>
    </section>
  );
}
