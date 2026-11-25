import { useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, FeedbackNotice, FloatingToast } from "../../shared/Feedback";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { saveRoleStop, type RoleStopTarget } from "./roleStopApi";

const defaultRoles = new Set(["ADMIN", "EXEC", "FIN", "SALES_LDR", "PM"]);

// SCR-UI-24: embed beside the selected role's actions in PermissionsScreen.
export function RoleStopAction({
  role,
  dirty = false,
  pending = false,
  onRefresh,
}: {
  role: RoleStopTarget;
  dirty?: boolean;
  pending?: boolean;
  onRefresh(): Promise<unknown>;
}) {
  const [target, setTarget] = useState<RoleStopTarget>();
  const [error, setError] = useState<unknown>();
  const [committedId, setCommittedId] = useState<string>();
  const committed = committedId === role.roleId;
  const [refreshing, setRefreshing] = useState(false);
  const retrying = useRef(false);
  const command = useCommitRefresh();
  const protectedRole = role.systemRole || defaultRoles.has(role.roleId);
  const busy = pending || command.pending || refreshing;
  async function confirm() {
    if (!target || busy || committed) return;
    setError(undefined);
    try {
      await command.commit(async () => {
        await saveRoleStop(target.roleId, target.revision);
        setCommittedId(target.roleId);
        setTarget(undefined);
      }, onRefresh);
    } catch (reason) {
      setError(reason);
    }
  }
  async function refresh() {
    if (retrying.current || busy) return;
    retrying.current = true;
    setRefreshing(true);
    setError(undefined);
    try {
      await onRefresh();
      command.clearOutcome();
    } catch (reason) {
      setError(reason);
    } finally {
      retrying.current = false;
      setRefreshing(false);
    }
  }
  return (
    <section
      data-testid="role-stop-action"
      data-screen-id="SCR-UI-24"
      aria-label="추가 역할 사용 중지"
    >
      {!protectedRole && role.use && !committed && (
        <button
          type="button"
          data-testid="R-STOP-OPEN"
          disabled={busy || dirty}
          onClick={() => {
            setError(undefined);
            setTarget({ ...role });
          }}
        >
          사용 중지
        </button>
      )}
      {dirty && !protectedRole && (
        <FeedbackNotice tone="warn" title="저장되지 않은 권한 변경사항">
          변경 내용을 저장하거나 취소한 뒤 사용 중지해 주세요.
        </FeedbackNotice>
      )}
      {committed && command.outcome?.kind === "saved" && (
        <FloatingToast message={command.outcome.message} />
      )}
      {committed && command.outcome?.kind === "refresh-failed" && !error && (
        <FeedbackNotice
          tone="warn"
          title="저장 완료 · 화면 갱신 실패"
          action={
            <button
              type="button"
              data-testid="R-REFRESH"
              disabled={busy}
              onClick={() => void refresh()}
            >
              다시 조회
            </button>
          }
        >
          {command.outcome.message}
        </FeedbackNotice>
      )}
      {!target && error != null && (
        <ErrorBanner
          message={
            error instanceof Error ? error.message : "다시 조회하지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => void refresh()}
          pending={busy}
          retryTestId="role-stop-read-retry"
        />
      )}
      <Dialog
        open={Boolean(target)}
        title="추가 역할 사용 중지"
        pending={busy}
        onCancel={() => {
          setTarget(undefined);
          setError(undefined);
        }}
        onConfirm={confirm}
        confirmLabel="중지"
        testId="role-stop-dialog"
        confirmTestId="R-STOP-SAVE"
        cancelTestId="R-STOP-CANCEL"
      >
        <p>
          대상 {target?.name} ({target?.roleId})
        </p>
        <p>
          시스템 역할은 중지할 수 없고 연결 사용자가 있으면 먼저 역할을 빼야
          합니다.
        </p>
        <p>
          역할의 권한·번호·기록은 보존됩니다. 중지 후에는 역할 탭과 선택
          목록에서 제외되며 재개하거나 삭제할 수 없습니다.
        </p>
        <p className="sicost-banner sicost-tone-info">
          중지된 역할은 다음 요청부터 권한 합집합에서 제외됩니다.
        </p>
        {error != null && (
          <>
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "사용 중지하지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
            />
            {error instanceof ApiError &&
              Object.entries(error.fieldMessages).map(([field, message]) => (
                <p key={field} className="sicost-field-error">
                  {field}: {message}
                </p>
              ))}
            {error instanceof ApiError && error.body.code === "STALE_WRITE" && (
              <button
                type="button"
                data-testid="R-LATEST"
                disabled={busy}
                onClick={() => {
                  setTarget(undefined);
                  void refresh();
                }}
              >
                최신값 다시 조회
              </button>
            )}
          </>
        )}
      </Dialog>
    </section>
  );
}
