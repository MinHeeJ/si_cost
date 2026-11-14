import { useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { AssignmentError } from "../assignmentsops/AssignmentDialogs";
import {
  releaseTentativeAssignment,
  type SalesTentative,
} from "./tentativeAssignmentsApi";

// SCR-UI-29: opportunity detail's NEED tab. The parent supplies the complete GET snapshot/revision.
export function TentativeAssignmentsPanel({
  items,
  revision,
  opportunityStatus,
  developerNames = {},
  loading = false,
  error,
  onRefresh,
}: {
  items: SalesTentative[];
  revision: string;
  opportunityStatus: "ING" | "WIN" | "LOSE" | "STOP";
  developerNames?: Record<string, string>;
  loading?: boolean;
  error?: unknown;
  onRefresh(): Promise<void>;
}) {
  const [selected, setSelected] = useState<SalesTentative>();
  const [reason, setReason] = useState("");
  const [failure, setFailure] = useState<unknown>();
  const [stale, setStale] = useState(false);
  const [committed, setCommitted] = useState(false);
  const { commit, pending, message } = useCommitRefresh(
    "저장됐지만 다시 불러오지 못했습니다",
  );
  const reasonError =
    [...reason].length > 300
      ? "해제 사유는 300자 이내로 입력해 주세요."
      : failure instanceof ApiError
        ? failure.fieldMessages.reason
        : undefined;
  const refresh = async () => {
    try {
      await onRefresh();
      setStale(false);
      setFailure(undefined);
      setCommitted(false);
    } catch (e) {
      setFailure(e);
    }
  };
  return (
    <section
      data-testid="tentative-assignments-panel"
      data-screen-id="SCR-UI-29"
      aria-busy={loading}
    >
      <h3>가배정</h3>
      <AssignmentError
        error={error}
        retry={() => {
          void refresh();
        }}
      />
      {message && <p role="status">{message}</p>}
      {opportunityStatus !== "ING" && (
        <p className="sicost-banner sicost-tone-lock">
          종료된 기회의 가배정은 읽기 전용입니다. 수주 CNVT는 투입 배정에서만
          확정·빼기할 수 있습니다.
        </p>
      )}
      {loading ? (
        <p role="status">가배정을 불러오는 중입니다.</p>
      ) : (
        !error && (
          <>
            {items.length === 0 ? (
              <p className="sicost-empty">가배정한 사람이 없습니다</p>
            ) : (
              <div className="sicost-table-viewport">
                <table className="sicost-table">
                  <thead>
                    <tr>
                      {[
                        "필요행",
                        "개발자",
                        "시작월",
                        "출처",
                        "상태",
                        "처리시각",
                        "행동",
                      ].map((label) => (
                        <th key={label}>{label}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((row) => (
                      <tr key={row.id} data-testid={`tentative-row-${row.id}`}>
                        <td>{row.needSn}</td>
                        <td>
                          {developerNames[row.developerId] ?? row.developerId}
                        </td>
                        <td>{row.startMonth}</td>
                        <td>{row.sourceScreenId}</td>
                        <td>
                          <span
                            className={`sicost-badge ${row.status === "ACT" ? "sicost-tone-info" : row.status === "DONE" ? "sicost-tone-ok" : "sicost-tone-lock"}`}
                          >
                            {row.status}
                          </span>
                        </td>
                        <td>{row.processedAt ?? "–"}</td>
                        <td>
                          {opportunityStatus === "ING" &&
                            row.status === "ACT" && (
                              <button
                                type="button"
                                data-testid={`tentative-release-${row.id}`}
                                disabled={pending || stale || committed}
                                onClick={() => {
                                  setSelected(row);
                                  setReason("");
                                  setFailure(undefined);
                                }}
                              >
                                해제
                              </button>
                            )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )
      )}
      {(stale || committed || (!selected && Boolean(failure))) && (
        <div className="sicost-banner sicost-tone-warn">
          <AssignmentError error={failure} />
          <button
            type="button"
            data-testid="tentative-refresh"
            disabled={pending}
            onClick={() => {
              void refresh();
            }}
          >
            다시 조회
          </button>
        </div>
      )}
      <Dialog
        open={Boolean(selected)}
        title="가배정 해제"
        confirmLabel="해제"
        pending={pending}
        confirmDisabled={Boolean(reasonError) || stale}
        onCancel={() => setSelected(undefined)}
        onConfirm={async () => {
          if (!selected || reasonError || stale) return;
          try {
            await commit(
              async () => {
                await releaseTentativeAssignment(selected.id, revision, reason);
                setSelected(undefined);
                setCommitted(true);
              },
              async () => {
                await onRefresh();
                setCommitted(false);
              },
            );
          } catch (e) {
            setFailure(e);
            if (e instanceof ApiError && e.body.code === "STALE_WRITE")
              setStale(true);
          }
        }}
      >
        <div data-testid="tentative-release-dialog">
          <p>
            {selected
              ? (developerNames[selected.developerId] ?? selected.developerId)
              : ""}
            의 가배정을 해제하시겠습니까? 실제 투입 배정은 변경하지 않습니다.
          </p>
          <label>
            해제 사유 (선택)
            <textarea
              data-testid="tentative-release-reason"
              value={reason}
              disabled={pending}
              aria-invalid={Boolean(reasonError)}
              onChange={(e) => {
                setReason(e.target.value);
                setFailure(undefined);
              }}
            />
          </label>
          {reasonError && (
            <p role="alert" className="sicost-field-error">
              {reasonError}
            </p>
          )}
          <AssignmentError error={failure} />
          {stale && (
            <p className="sicost-banner sicost-tone-warn">
              다른 변경이 있습니다. 취소 후 다시 조회해 주세요.
            </p>
          )}
        </div>
      </Dialog>
    </section>
  );
}
