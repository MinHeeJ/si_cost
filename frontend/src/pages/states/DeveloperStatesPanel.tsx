import { useCallback, useEffect, useRef, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { COMMITTED_REFRESH_FAILED } from "../../shared/useCommitRefresh";
import {
  DeveloperStateDialog,
  type DeveloperStateAction,
} from "./DeveloperStateDialog";
import * as api from "./developerStatesApi";
import "./developerStates.css";

// SCR-UI-02 · SLICE-BASIC132-DEVELOPERS: mounted in the existing /UI-02 detail drawer's 근무상태 tab.
export function DeveloperStatesPanel({
  developerId,
  currentDate,
  onChanged,
  onDirtyChange,
}: {
  developerId: string;
  currentDate: string;
  onChanged?(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [data, setData] = useState<api.DeveloperStates>();
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const [action, setAction] = useState<DeveloperStateAction>();
  const [saved, setSaved] = useState(false),
    [notice, setNotice] = useState("");
  const [warnings, setWarnings] = useState<api.StateWarning[]>([]);
  const generation = useRef(0);
  const load = useCallback(async () => {
    const token = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const result = await api.getDeveloperStates(developerId);
      if (token === generation.current) setData(result);
    } catch (e) {
      if (token === generation.current) setError(e);
      throw e;
    } finally {
      if (token === generation.current) setLoading(false);
    }
  }, [developerId]);
  useEffect(() => {
    setData(undefined);
    setAction(undefined);
    setSaved(false);
    setNotice("");
    setWarnings([]);
    void load().catch(() => undefined);
    return () => {
      generation.current++;
    };
  }, [load]);
  async function refresh() {
    await load();
    await onChanged?.();
    setSaved(false);
  }
  async function committed(nextWarnings: api.StateWarning[], changed: boolean) {
    setAction(undefined);
    setWarnings(nextWarnings);
    setSaved(true);
    setNotice(changed ? "저장했습니다." : "바뀐 내용이 없습니다.");
    try {
      await refresh();
    } catch {
      setNotice(COMMITTED_REFRESH_FAILED);
    }
  }
  async function retry() {
    try {
      if (saved) {
        await refresh();
        setNotice("저장한 내용을 다시 조회했습니다.");
      } else await load();
    } catch {
      if (saved) setNotice(COMMITTED_REFRESH_FAILED);
    }
  }
  const columns: Column<api.DeveloperState>[] = [
    {
      id: "state",
      label: "상태",
      render: (row) => {
        const activeLeave =
          row.state === "LEAVE" &&
          row.startDate <= currentDate &&
          (!row.endDate || row.endDate >= currentDate);
        const scheduled =
          row.state === "RETIRE" && row.startDate >= currentDate;
        return (
          <span data-testid={`developer-state-row-${row.sn}`}>
            <StatusBadge
              tone={
                scheduled
                  ? "warn"
                  : activeLeave
                    ? "lock"
                    : row.state === "WORK"
                      ? "ok"
                      : "neutral"
              }
            >
              {scheduled
                ? "퇴사 예정"
                : row.state === "LEAVE"
                  ? "휴직"
                  : row.state === "WORK"
                    ? "재직"
                    : "퇴사"}
            </StatusBadge>
            {activeLeave && (
              <strong className="developer-state-active"> 진행 중</strong>
            )}
          </span>
        );
      },
    },
    { id: "startDate", label: "시작", render: (row) => row.startDate },
    {
      id: "endDate",
      label: "종료",
      render: (row) =>
        row.state === "LEAVE" ? (row.endDate ?? "복직일 미정") : "—",
    },
    { id: "reason", label: "사유", render: (row) => row.reason ?? "—" },
    { id: "actor", label: "등록자", render: (row) => row.actorName },
    {
      id: "actions",
      label: "허용 작업",
      render: (row) => (
        <div className="developer-state-actions">
          {row.allowedActions.includes("EDIT_END") && (
            <button
              data-testid={`developer-state-edit-${row.sn}`}
              type="button"
              disabled={saved || loading}
              onClick={() => setAction({ kind: "end", row })}
            >
              종료일 수정
            </button>
          )}
          {row.allowedActions.includes("CANCEL") &&
            (row.state === "WORK" || row.state === "RETIRE") && (
              <button
                data-testid={`developer-state-cancel-${row.sn}`}
                type="button"
                disabled={saved || loading}
                onClick={() => setAction({ kind: "cancel", row })}
              >
                {row.state === "WORK" ? "재직 취소" : "퇴사 취소"}
              </button>
            )}
        </div>
      ),
    },
  ];
  return (
    <section
      className="developer-states"
      data-screen-id="UI-02"
      data-testid="developer-states-panel"
      aria-label="근무 상태 이력"
    >
      <header>
        <h2>근무 상태 이력</h2>
        <button
          data-testid="developer-state-create"
          className="sicost-primary"
          type="button"
          disabled={!data || loading || saved || Boolean(error)}
          onClick={() => setAction({ kind: "create" })}
        >
          상태 등록
        </button>
      </header>
      {notice && (
        <div
          role="status"
          className={`sicost-banner sicost-tone-${saved ? "warn" : "ok"}`}
        >
          {notice}
          {saved && (
            <button
              data-testid="developer-state-refresh"
              type="button"
              disabled={loading}
              onClick={() => void retry()}
            >
              다시 조회
            </button>
          )}
        </div>
      )}
      {warnings.map((warning, index) => (
        <div
          key={`${warning.field}-${index}`}
          className="sicost-banner sicost-tone-warn"
          role="status"
        >
          {warning.message}
        </div>
      ))}
      {Boolean(error) && !saved && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "이력을 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => void retry()}
        />
      )}
      <DataTable
        rows={data?.items ?? []}
        columns={columns}
        rowKey={(row) => row.sn}
        loading={loading}
        empty="등록된 근무 상태 이력이 없습니다."
      />
      {!loading && data && (
        <p className="developer-state-hire">
          {data.hireDate ?? "입사일 미등록"} · 입사·재직{" "}
          <small>표시 전용</small>
        </p>
      )}
      {action && data && (
        <DeveloperStateDialog
          key={`${developerId}-${action.kind}-${action.kind === "create" ? "new" : action.row.sn}`}
          developerId={developerId}
          revision={data.revision}
          hireDate={data.hireDate}
          action={action}
          onClose={() => setAction(undefined)}
          onCommitted={committed}
          onDirtyChange={onDirtyChange}
        />
      )}
    </section>
  );
}
