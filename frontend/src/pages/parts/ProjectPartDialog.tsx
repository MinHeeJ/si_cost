import { useCallback, useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { Text } from "../../shared/Text";
import { CodeSelect } from "../../shared/CodeSelect";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  useCommitRefresh,
  COMMITTED_REFRESH_FAILED,
} from "../../shared/useCommitRefresh";
import {
  getSalesPartOptions,
  type PartOption,
} from "../salespartsops/partStandardsApi";
import * as api from "./projectPartsApi";
import "./projectParts.css";

export type ProjectPartDialogProps = {
  projectId: string;
  projectName: string;
  year: number;
  currentYear: number;
  effectiveStatus: string;
  contractType: string;
  readOnly?: boolean;
  onClose(): void;
  onSaved(result: api.SaveProjectPartResult): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
};
export function ProjectPartDialog({
  projectId,
  projectName,
  year,
  currentYear,
  effectiveStatus,
  contractType,
  readOnly = false,
  onClose,
  onSaved,
  onDirtyChange,
}: ProjectPartDialogProps) {
  const [data, setData] = useState<api.ProjectPartsData>();
  const [options, setOptions] = useState<PartOption[]>([]);
  const [partId, setPartId] = useState("");
  const [month, setMonth] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({});
  const [leaving, setLeaving] = useState(false);
  const [touched, setTouched] = useState(false);
  const [saved, setSaved] = useState<api.SaveProjectPartResult>();
  const [refreshFailed, setRefreshFailed] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const generation = useRef(0);
  const { pending, commit } = useCommitRefresh();
  const locked =
    readOnly ||
    year < currentYear ||
    ["END", "LOSE"].includes(effectiveStatus) ||
    contractType === "IN";
  const dirty = touched && !saved;
  const load = useCallback(async () => {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const [value, choices] = await Promise.all([
        api.getProjectParts(projectId),
        getSalesPartOptions(),
      ]);
      if (generation.current !== version) return;
      setData(value);
      setOptions(choices.items);
      setPartId(value.retainedBasePart?.partId ?? "");
      setMonth("");
      setReason("");
      setTouched(false);
      setFields({});
    } catch (e) {
      if (generation.current === version) setError(e);
    } finally {
      if (generation.current === version) setLoading(false);
    }
  }, [projectId]);
  useEffect(() => {
    void load();
    return () => {
      generation.current++;
    };
  }, [load]);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const prevent = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, [dirty]);
  function close() {
    if (pending || retrying) return;
    if (dirty) setLeaving(true);
    else onClose();
  }
  async function submit() {
    if (saved || locked) {
      close();
      return;
    }
    if (!data || loading || pending || !data.retainedBasePart) return;
    const errors: Record<string, string> = {};
    if (!options.some((option) => option.id === partId))
      errors.partId = "사용 중인 파트 또는 본사를 선택해 주세요.";
    if (
      !/^(?!0000)[0-9]{4}-(0[1-9]|1[0-2])$/.test(month) ||
      month < data.minimumMonth ||
      month > data.maximumMonth ||
      month <= data.retainedBasePart.effectiveMonth
    )
      errors.effectiveMonth =
        "허용월 이내이며 보존 마지막 시작월보다 뒤인 월을 선택해 주세요.";
    if ([...reason].length > 500)
      errors.reason = "사유는 500자 이하로 입력해 주세요.";
    setFields(errors);
    if (Object.keys(errors).length) return;
    setError(undefined);
    try {
      let result: api.SaveProjectPartResult | undefined;
      await commit(
        async () => {
          result = await api.saveProjectPart(projectId, {
            year,
            expectedRevision: data.revision,
            partId,
            effectiveMonth: month,
            reason: reason.trim() ? reason : null,
          });
          setSaved(result);
          setTouched(false);
          onDirtyChange?.(false);
        },
        async () => {
          if (result) {
            try {
              await onSaved(result);
            } catch (e) {
              setRefreshFailed(true);
              throw e;
            }
          }
        },
      );
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) setFields(e.fieldMessages);
    }
  }
  async function retryRefresh() {
    if (!saved || retrying) return;
    setRetrying(true);
    try {
      await onSaved(saved);
      setRefreshFailed(false);
    } catch {
      setRefreshFailed(true);
    } finally {
      setRetrying(false);
    }
  }
  const value = saved?.value ?? data;
  const cancelReservation = data && partId === data.retainedBasePart?.partId;
  return (
    <>
      <Dialog
        open
        title="영업 파트 변경"
        pending={pending || retrying}
        onCancel={close}
        onConfirm={submit}
        confirmLabel={saved || locked ? "닫기" : "저장"}
        confirmDisabled={
          !saved && !locked && (loading || !data?.retainedBasePart)
        }
      >
        <section
          className="project-parts-content"
          data-testid="project-part-dialog"
          data-screen-id="SCR-UI-01"
        >
          <p>
            대상 {projectId} · {projectName}
          </p>
          {loading && <p role="status">영업 파트를 불러오는 중입니다.</p>}
          {value && (
            <>
              <p>
                보존 마지막 기준 파트{" "}
                <strong>
                  {value.retainedBasePart?.partName ??
                    value.retainedBasePart?.partId ??
                    "없음"}
                </strong>{" "}
                {value.retainedBasePart?.effectiveMonth}
              </p>
              <div className="sicost-banner sicost-tone-info">
                최초 행과 마감·이관된 시작행은 보존합니다. 기존 예약은 모두
                교체되며 과거 감사 기록은 유지됩니다.
              </div>
              <div>
                <h3>교체 대상 월/파트</h3>
                {value.replacementTargets.length ? (
                  <ul className="project-parts-targets">
                    {value.replacementTargets.map((row) => (
                      <li
                        data-testid={`project-part-target-${row.effectiveMonth}`}
                        key={row.effectiveMonth}
                      >
                        {row.effectiveMonth} ·{" "}
                        {row.partName ?? row.partId ?? "미지정"}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p>교체 대상 없음</p>
                )}
              </div>
              <p>
                허용월 {value.minimumMonth} ~ {value.maximumMonth} · 보존 마지막
                시작월 다음 달부터
              </p>
              {!value.retainedBasePart && (
                <p className="sicost-banner sicost-tone-warn">
                  등록된 영업 파트 이력이 없습니다. 최초 이력을 확인해 주세요.
                </p>
              )}
            </>
          )}
          {locked ? (
            <p className="sicost-banner sicost-tone-lock">
              선택 연도 또는 프로젝트 상태에 따라 조회만 할 수 있습니다.
            </p>
          ) : (
            !saved && (
              <>
                <div className="project-parts-fields">
                  <CodeSelect
                    label="새 파트 *"
                    value={partId}
                    options={options.map((option) => ({
                      code: option.id,
                      name:
                        option.hqYn === "Y"
                          ? `${option.name} (본사)`
                          : option.name,
                    }))}
                    error={fields.partId}
                    disabled={pending || loading || !data}
                    onChange={(next) => {
                      setPartId(next);
                      setTouched(true);
                      setFields((f) => ({ ...f, partId: "" }));
                    }}
                  />
                  <Text
                    label="적용 시작월 *"
                    data-testid="project-part-month"
                    type="month"
                    value={month}
                    error={fields.effectiveMonth}
                    disabled={pending || loading || !data}
                    onChange={(next) => {
                      setMonth(next);
                      setTouched(true);
                      setFields((f) => ({ ...f, effectiveMonth: "" }));
                    }}
                  />
                </div>
                <Text
                  label="사유 (선택, 최대 500자)"
                  data-testid="project-part-reason"
                  value={reason}
                  error={fields.reason}
                  disabled={pending || loading || !data}
                  onChange={(next) => {
                    setReason(next);
                    setTouched(true);
                    setFields((f) => ({ ...f, reason: "" }));
                  }}
                />
                <p className="sicost-banner sicost-tone-warn">
                  {cancelReservation
                    ? data.replacementTargets.length
                      ? "기준 파트와 같으므로 기존 예약을 취소하고 새 행은 추가하지 않습니다."
                      : "예약이 없고 기준 파트와 같으면 바뀐 내용 없이 저장됩니다."
                    : "교체 대상 예약을 모두 제거하고 선택한 적용 시작월의 새 파트 한 행을 저장합니다."}
                </p>
              </>
            )
          )}
          {saved && (
            <p role="status" className="sicost-banner sicost-tone-ok">
              {saved.changed
                ? "영업 파트를 저장했습니다."
                : "바뀐 내용이 없습니다."}
            </p>
          )}
          {refreshFailed && (
            <ErrorBanner
              message={COMMITTED_REFRESH_FAILED}
              pending={retrying}
              onRetry={() => void retryRefresh()}
            />
          )}
          {error != null && (
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "영업 파트를 처리하지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={!data ? () => void load() : undefined}
            />
          )}
          {error instanceof ApiError && error.body.code === "STALE_WRITE" && (
            <p>
              다시 조회한 뒤 저장해 주세요. 입력은 유지되며 자동으로 재저장하지
              않습니다.
            </p>
          )}
        </section>
      </Dialog>
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
        confirmLabel="나가기"
        cancelLabel="계속 작성"
      >
        <p>나가기를 선택하면 입력한 파트 변경을 폐기합니다.</p>
      </Dialog>
    </>
  );
}
