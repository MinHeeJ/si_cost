import { useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import type { BusinessUser } from "../businessusers/businessUsersApi";
import { notifyBusinessUsersCommitted } from "./businessUserOptionsApi";
import * as api from "./userAdministrationApi";

export function UserStateActions({
  user,
  onCommitted,
}: {
  user: BusinessUser;
  onCommitted(): Promise<void>;
}) {
  const [confirm, setConfirm] = useState<"STOP" | "RESUME">(),
    [error, setError] = useState<unknown>();
  const saving = useRef(false);
  const { commit, pending, message } = useCommitRefresh();
  async function save(action: api.UserStateAction) {
    if (saving.current) return;
    saving.current = true;
    setError(undefined);
    try {
      await commit(async () => {
        await api.changeBusinessUserState(user.userId, action, user.revision);
        setConfirm(undefined);
        notifyBusinessUsersCommitted();
      }, onCommitted);
    } catch (e) {
      setError(e);
    } finally {
      saving.current = false;
    }
  }
  return (
    <section data-testid="user-state-actions">
      <div className="businessusers-toolbar">
        {user.status !== "STOP" ? (
          <button
            data-testid="user-stop-open"
            disabled={pending}
            onClick={() => {
              setError(undefined);
              setConfirm("STOP");
            }}
          >
            사용 중지
          </button>
        ) : (
          <button
            data-testid="user-resume-open"
            disabled={pending}
            onClick={() => {
              setError(undefined);
              setConfirm("RESUME");
            }}
          >
            다시 사용
          </button>
        )}
        {user.status === "LOCK" && (
          <button
            data-testid="user-unlock-save"
            disabled={pending}
            onClick={() => void save("UNLOCK")}
          >
            잠금 해제
          </button>
        )}
        <button
          data-testid="user-password-preparation"
          disabled
          title="18번 인증 기능 적용 시 제공합니다."
        >
          비밀번호 초기화/발송 (18번 준비)
        </button>
      </div>
      {message && !confirm && <p role="status">{message}</p>}
      {error != null && !confirm && (
        <ErrorBanner
          message={
            error instanceof Error ? error.message : "처리하지 못했습니다."
          }
        />
      )}
      {(error != null || message?.includes("다시 불러오지")) && (
        <button
          data-testid="user-state-latest"
          disabled={pending}
          onClick={() => void onCommitted().catch(setError)}
        >
          최신값 다시 조회
        </button>
      )}
      <Dialog
        open={!!confirm}
        title={confirm === "STOP" ? "사용 중지 확인" : "다시 사용 확인"}
        pending={pending}
        onCancel={() => setConfirm(undefined)}
        onConfirm={() => save(confirm!)}
        testId="user-state-confirm"
        confirmTestId="user-state-confirm-save"
        cancelTestId="user-state-confirm-cancel"
      >
        <p>
          {user.name} ({user.userId}) · {user.status}
        </p>
        <p>
          기존 역할·담당 프로젝트·인증값은 보존합니다. 파트장을 자동으로
          재지정하지 않습니다.
        </p>
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error ? error.message : "처리하지 못했습니다."
            }
          />
        )}
      </Dialog>
    </section>
  );
}
