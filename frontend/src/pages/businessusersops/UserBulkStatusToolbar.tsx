import { useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { DataTable } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import type { BusinessUser } from "../businessusers/businessUsersApi";
import { notifyBusinessUsersCommitted } from "./businessUserOptionsApi";
import * as api from "./userAdministrationApi";

export function UserStatusResultsDialog({
  result,
  onClose,
}: {
  result?: api.UserStateResults;
  onClose(): void;
}) {
  const valid =
    !result ||
    (result.applied + result.unchanged + result.rejected ===
      result.items.length &&
      new Set(result.items.map((row) => row.userId)).size ===
        result.items.length);
  return (
    <Dialog
      open={!!result}
      title="일괄 상태 변경 결과"
      onCancel={onClose}
      onConfirm={onClose}
      confirmLabel="닫기"
      wide
      testId="user-status-results"
    >
      {!valid ? (
        <p role="alert">
          서버 결과의 건수가 일치하지 않습니다. 최신값을 다시 조회해 주세요.
        </p>
      ) : (
        result && (
          <>
            <p>
              적용 {result.applied} / 이미 동일 {result.unchanged} / 거부{" "}
              {result.rejected}
            </p>
            <DataTable
              rows={result.items}
              rowKey={(row) => row.userId}
              columns={[
                { id: "userId", label: "아이디", render: (row) => row.userId },
                {
                  id: "outcome",
                  label: "결과",
                  render: (row) => (
                    <StatusBadge
                      tone={
                        row.outcome === "APPLIED"
                          ? "ok"
                          : row.outcome === "REJECTED"
                            ? "bad"
                            : "lock"
                      }
                    >
                      {row.outcome}
                    </StatusBadge>
                  ),
                },
                { id: "code", label: "코드", render: (row) => row.code },
                { id: "reason", label: "이유", render: (row) => row.reason },
              ]}
            />
          </>
        )
      )}
    </Dialog>
  );
}

export function UserBulkStatusToolbar({
  selected,
  onClear,
  onSelectAll,
  onCommitted,
}: {
  selected: BusinessUser[];
  onClear?(): void;
  onSelectAll?(): Promise<void>;
  onCommitted(): Promise<void>;
}) {
  const [action, setAction] = useState<"STOP" | "RESUME">(),
    [result, setResult] = useState<api.UserStateResults>(),
    [error, setError] = useState<unknown>();
  const { commit, pending, message } = useCommitRefresh();
  async function save() {
    if (!action) return;
    setError(undefined);
    try {
      await commit(async () => {
        const response = await api.saveBusinessUserStatusChanges(
          action,
          selected.map((user) => ({
            userId: user.userId,
            expectedRevision: user.revision,
          })),
        );
        setResult(response);
        setAction(undefined);
        notifyBusinessUsersCommitted();
      }, onCommitted);
    } catch (e) {
      setError(e);
    }
  }
  return (
    <section data-testid="user-bulk-status-toolbar">
      {selected.length > 0 && <div className="businessusers-toolbar">
        <span>선택 {selected.length}건</span>
        {onSelectAll && <button type="button" disabled={pending} onClick={()=>void onSelectAll().catch(setError)}>조회전체 선택</button>}
        <div>
          <button
            data-testid="user-bulk-stop"
            disabled={pending || !selected.length || selected.length > 100}
            onClick={() => {
              setError(undefined);
              setAction("STOP");
            }}
          >
            일괄 사용 중지
          </button>
          <button
            data-testid="user-bulk-resume"
            disabled={pending || !selected.length || selected.length > 100}
            onClick={() => {
              setError(undefined);
              setAction("RESUME");
            }}
          >
            일괄 다시 사용
          </button>
          {onClear && <button type="button" disabled={pending} onClick={onClear}>선택 해제</button>}
        </div>
      </div>}
      {message && !action && <p role="status">{message}</p>}
      {message?.includes("다시 불러오지") && (
        <button
          data-testid="user-bulk-reload"
          onClick={() => void onCommitted().catch(setError)}
        >
          다시 조회
        </button>
      )}
      {error != null && !action && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "결과를 확인할 수 없습니다. 다시 조회해 주세요."
          }
        />
      )}
      <Dialog
        open={!!action}
        title={
          action === "STOP" ? "선택 사용자 사용 중지" : "선택 사용자 다시 사용"
        }
        pending={pending}
        onCancel={() => setAction(undefined)}
        onConfirm={save}
        testId="user-bulk-confirm"
        confirmTestId="user-bulk-confirm-save"
        cancelTestId="user-bulk-confirm-cancel"
      >
        <p>
          선택 {selected.length}명:{" "}
          {selected
            .map((user) => user.userId)
            .sort()
            .join(", ")}
        </p>
        <p>
          현재 선택행만 항목별 처리하며 한 항목 거부가 다른 성공을 되돌리지
          않습니다.
        </p>
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "처리 결과를 확인할 수 없습니다. 재조회해 주세요."
            }
          />
        )}
      </Dialog>
      <UserStatusResultsDialog
        result={result}
        onClose={() => setResult(undefined)}
      />
    </section>
  );
}
