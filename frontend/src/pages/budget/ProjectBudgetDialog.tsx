import { useCallback, useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { Text } from "../../shared/Text";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  useCommitRefresh,
  COMMITTED_REFRESH_FAILED,
} from "../../shared/useCommitRefresh";
import { formatDecimal } from "../../shared/format";
import * as api from "./projectBudgetApi";

export type ProjectBudgetDialogProps = {
  projectId: string;
  projectName: string;
  contractType: string;
  effectiveStatus: string;
  year: number;
  currentYear: number;
  readOnly?: boolean;
  onClose(): void;
  onSaved(result: api.SaveProjectBudgetResult): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
};
export function ProjectBudgetDialog({
  projectId,
  projectName,
  contractType,
  effectiveStatus,
  year,
  currentYear,
  readOnly = false,
  onClose,
  onSaved,
  onDirtyChange,
}: ProjectBudgetDialogProps) {
  const [data, setData] = useState<api.ProjectBudgetData>();
  const [amount, setAmount] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [fieldError, setFieldError] = useState("");
  const [leaving, setLeaving] = useState(false);
  const [touched, setTouched] = useState(false);
  const [saved, setSaved] = useState<api.SaveProjectBudgetResult>();
  const [refreshFailed, setRefreshFailed] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const generation = useRef(0);
  const { pending, commit } = useCommitRefresh();
  const locked =
    readOnly ||
    year < currentYear ||
    effectiveStatus === "LOSE" ||
    effectiveStatus === "END" ||
    contractType === "IN";
  const dirty = !saved && touched;
  const load = useCallback(async () => {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const value = await api.getProjectBudget(projectId);
      if (generation.current !== version) return;
      setData(value);
      setAmount(value.budgetAmount ?? "");
      setTouched(false);
      setFieldError("");
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
    if (!data || loading || pending) return;
    const normalized = amount.replace(/[\s\p{Z},]/gu, "").replace(/원$/, "");
    if (!/^[0-9]+$/.test(normalized) || BigInt(normalized) > 999999999999999n) {
      setFieldError("예산은 0~999,999,999,999,999원의 정수로 입력해 주세요.");
      return;
    }
    setError(undefined);
    setFieldError("");
    try {
      let result: api.SaveProjectBudgetResult | undefined;
      await commit(
        async () => {
          result = await api.saveProjectBudget(projectId, {
            year,
            expectedRevision: data.revision,
            budgetAmount: normalized,
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
      if (e instanceof ApiError)
        setFieldError(e.fieldMessages.budgetAmount ?? "");
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
  const final = saved?.value ?? data;
  const basisChanged =
    saved &&
    data &&
    (saved.value.basisMonth !== data.basisMonth ||
      saved.value.targetMarginRate !== data.targetMarginRate ||
      saved.value.autoPreviewAmount !== data.autoPreviewAmount);
  return (
    <>
      <Dialog
        open
        title="예산(목표 원가) 변경"
        pending={pending || retrying}
        onCancel={close}
        onConfirm={submit}
        confirmLabel={saved || locked ? "닫기" : "저장"}
        confirmDisabled={!saved && !locked && (loading || !data)}
      >
        <section data-testid="project-budget-dialog" data-screen-id="UI-01">
          <p>
            대상 {projectId} · {projectName}
          </p>
          {loading && <p role="status">예산을 불러오는 중입니다.</p>}
          {final && (
            <>
              <p>
                현재 예산{" "}
                {final.budgetAmount === null
                  ? "–"
                  : `${formatDecimal(final.budgetAmount)}원`}{" "}
                <StatusBadge
                  tone={final.budgetManual === "Y" ? "warn" : "info"}
                >
                  {final.budgetManual === "Y" ? "수기(Y)" : "자동(N)"}
                </StatusBadge>
              </p>
              <p className="sicost-banner sicost-tone-info">
                서버 자동 미리보기:{" "}
                {final.autoPreviewAmount == null
                  ? "유효한 목표율이 없어 계산할 수 없습니다. 수기로 입력해 주세요."
                  : `${formatDecimal(final.autoPreviewAmount)}원 · 목표율 ${final.targetMarginRate}%`}{" "}
                {final.basisMonth && `· 기준월 ${final.basisMonth}`}
              </p>
            </>
          )}
          {locked ? (
            <p className="sicost-banner sicost-tone-lock">
              {contractType === "IN"
                ? "내부 프로젝트는 예산을 입력하지 않습니다."
                : "선택 연도 또는 프로젝트 상태에 따라 조회만 할 수 있습니다."}
            </p>
          ) : (
            !saved && (
              <>
                <Text
                  label="새 예산 (원) *"
                  data-testid="project-budget-amount"
                  value={amount}
                  aria-required="true"
                  inputMode="numeric"
                  disabled={pending || loading || !data}
                  error={fieldError}
                  onChange={(value) => {
                    setAmount(value);
                    setTouched(true);
                    setFieldError("");
                  }}
                />
                <p>
                  직접 입력은 자동 예산과 같아도 수기(Y)로 저장됩니다. 0원을
                  입력할 수 있으며 자동으로 되돌릴 수 없습니다.
                </p>
              </>
            )
          )}
          {saved && (
            <p role="status" className="sicost-banner sicost-tone-ok">
              {saved.changed ? "예산을 저장했습니다." : "바뀐 내용이 없습니다."}{" "}
              최종 예산{" "}
              {saved.value.budgetAmount === null
                ? "–"
                : formatDecimal(saved.value.budgetAmount)}
              원 · {saved.value.budgetManual === "Y" ? "수기(Y)" : "자동(N)"}
            </p>
          )}
          {basisChanged && (
            <p role="status" className="sicost-banner sicost-tone-warn">
              창을 연 뒤 자동 예산 기준이 변경되었습니다. 위의 최종 저장값과
              기준을 확인해 주세요.
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
                  : "예산을 처리하지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={!data ? () => void load() : undefined}
            />
          )}
          {error instanceof ApiError && error.body.code === "STALE_WRITE" && (
            <p>
              다시 조회한 뒤 저장해 주세요. 입력한 값은 유지되며 자동으로
              재저장하지 않습니다.
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
        <p>나가기를 선택하면 입력한 예산을 폐기합니다.</p>
      </Dialog>
    </>
  );
}
