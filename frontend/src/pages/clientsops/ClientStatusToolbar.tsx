import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { DataTable } from "../../shared/DataTable";
import type { ClientSelectionState } from "./useClientSelection";
import * as api from "./clientActionsApi";
import "./clientActions.css";

export type ClientStatusRow = api.ClientSelectionItem & {
  name: string;
  useYn: "Y" | "N";
};
// SCR-UI-15: retained list owns selection through useClientSelection; single status uses exactly the same endpoint.
export function ClientStatusToolbar({
  selection,
  rows,
  total,
  onCommitted,
  onRefresh,
}: {
  selection: ClientSelectionState;
  rows: ClientStatusRow[];
  total: number;
  onCommitted(): void | Promise<void>;
  onRefresh(): void | Promise<void>;
}) {
  return (
    <section
      className="client-bulk-toolbar"
      data-testid="client-status-toolbar"
      aria-label="거래처 선택 작업"
    >
      <button
        data-testid="client-select-page"
        type="button"
        disabled={selection.loading}
        onClick={() => selection.selectPage(rows, true)}
      >
        페이지 전체 선택
      </button>
      <button
        data-testid="client-select-all"
        type="button"
        disabled={selection.loading}
        onClick={() => void selection.selectAll()}
      >
        조회된 {total}건 모두 선택
      </button>
      <button
        data-testid="client-selection-clear"
        type="button"
        onClick={selection.clear}
      >
        선택 해제
      </button>
      <span role="status">선택 {selection.count}건</span>
      {selection.error && (
        <p role="alert" className="sicost-field-error">
          {selection.error}
        </p>
      )}
      <ClientStatusAction
        targets={selection.targets}
        selectionContext={selection.context}
        disabled={selection.loading}
        onCommitted={async () => {
          selection.clear();
          await onCommitted();
        }}
        onRefresh={async () => {
          selection.clear();
          await onRefresh();
        }}
      />
    </section>
  );
}
export function ClientSingleStatus({
  row,
  onCommitted,
  onRefresh,
}: {
  row: ClientStatusRow;
  onCommitted(): void | Promise<void>;
  onRefresh(): void | Promise<void>;
}) {
  return (
    <ClientStatusAction
      key={row.id}
      targets={[{ id: row.id, expectedRevision: row.revision }]}
      selectionContext={`${row.id}:${row.revision}`}
      onlyTarget={row.useYn === "Y" ? "N" : "Y"}
      onCommitted={onCommitted}
      onRefresh={onRefresh}
    />
  );
}
function ClientStatusAction({
  targets,
  selectionContext,
  onlyTarget,
  disabled = false,
  onCommitted,
  onRefresh,
}: {
  targets: api.ClientBulkTarget[];
  selectionContext: string;
  onlyTarget?: "Y" | "N";
  disabled?: boolean;
  onCommitted(): void | Promise<void>;
  onRefresh(): void | Promise<void>;
}) {
  const [confirm, setConfirm] = useState<{
    target: "Y" | "N";
    items: api.ClientBulkTarget[];
    context: string;
  }>();
  const [pending, setPending] = useState(false),
    [result, setResult] = useState<api.ClientBulkResult>();
  const [error, setError] = useState(""),
    [refreshRequired, setRefreshRequired] = useState(false);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const saving = useRef(false);
  useEffect(() => {
    setConfirm(undefined);
  }, [selectionContext]);
  async function save() {
    if (
      !confirm ||
      confirm.context !== selectionContext ||
      saving.current ||
      refreshRequired
    )
      return;
    saving.current = true;
    setPending(true);
    setError("");
    setResult(undefined);
    try {
      const data = await api.saveClientBulkStatus(
        confirm.target,
        confirm.items,
      );
      const ids = new Set(data.items.map((item) => item.id));
      if (
        data.applied + data.noChange + data.rejected !== confirm.items.length ||
        ids.size !== confirm.items.length ||
        data.items.length !== confirm.items.length ||
        confirm.items.some((item) => !ids.has(item.id))
      )
        throw new Error(
          "처리 결과를 확인할 수 없습니다. 다시 조회한 뒤 수동으로 요청해 주세요.",
        );
      setResult(data);
      setConfirm(undefined);
      setRefreshRequired(true);
      if (data.applied > 0) api.notifyClientsCommitted();
      try {
        await onCommitted();
        setRefreshRequired(false);
      } catch {
        setRefreshFailed(true);
        setError("저장됐지만 다시 불러오지 못했습니다");
      }
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "통신이 끊겼습니다. 다시 조회한 뒤 수동으로 요청해 주세요.",
      );
      setConfirm(undefined);
      setRefreshRequired(true);
    } finally {
      setPending(false);
      saving.current = false;
    }
  }
  async function refresh() {
    if (saving.current) return;
    saving.current = true;
    setPending(true);
    try {
      await onRefresh();
      setRefreshRequired(false);
      setRefreshFailed(false);
      setError("");
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "다시 불러오지 못했습니다.",
      );
    } finally {
      saving.current = false;
      setPending(false);
    }
  }
  const label = (target: "Y" | "N") =>
    target === "N" ? "사용 중지" : "다시 사용";
  return (
    <div className="client-status-actions" data-testid="client-status-actions">
      {(["N", "Y"] as const)
        .filter((target) => !onlyTarget || target === onlyTarget)
        .map((target) => (
          <button
            key={target}
            type="button"
            data-testid={`client-status-${target.toLowerCase()}`}
            disabled={disabled || pending || refreshRequired || !targets.length}
            onClick={() => {
              setError("");
              setConfirm({
                target,
                items: targets.map((item) => ({ ...item })),
                context: selectionContext,
              });
            }}
          >
            {label(target)}
          </button>
        ))}
      {error && (
        <p role="alert" className="sicost-field-error">
          {error}
        </p>
      )}
      {refreshRequired && (
        <button
          data-testid="client-status-refresh"
          type="button"
          disabled={pending}
          onClick={() => void refresh()}
        >
          {refreshFailed ? "재조회" : "다시 조회"}
        </button>
      )}
      <Dialog
        open={!!confirm}
        title={`거래처 ${confirm ? label(confirm.target) : "상태 변경"}`}
        pending={pending}
        onConfirm={save}
        onCancel={() => setConfirm(undefined)}
        confirmLabel="확인"
      >
        <p>
          {confirm?.items.length}건의 거래처를{" "}
          {confirm ? label(confirm.target) : "변경"}하시겠습니까?
        </p>
        <p className="client-secondary">
          연결 자료는 삭제되지 않습니다. 처리할 수 없는 항목은 사유를 확인해
          주세요.
        </p>
      </Dialog>
      <Dialog
        open={!!result}
        title="거래처 처리 결과"
        onConfirm={() => setResult(undefined)}
        onCancel={() => setResult(undefined)}
        confirmLabel="닫기"
        wide
      >
        {result && (
          <>
            <p>
              적용 {result.applied}건 · 변경 없음 {result.noChange}건 · 거부{" "}
              {result.rejected}건
            </p>
            <DataTable
              rows={result.items}
              rowKey={(row) => row.id}
              columns={[
                { id: "id", label: "거래처 ID", render: (row) => row.id },
                { id: "name", label: "상호", render: (row) => row.name },
                {
                  id: "result",
                  label: "결과",
                  render: (row) => (
                    <span
                      className={`client-result client-result-${row.result}`}
                    >
                      {
                        {
                          applied: "적용",
                          noChange: "변경 없음",
                          rejected: "거부",
                        }[row.result]
                      }
                    </span>
                  ),
                },
                {
                  id: "error",
                  label: "사유",
                  render: (row) =>
                    row.error ? (
                      <span>
                        {row.error.message}
                        {(row.error.details?.references ?? []).map(
                          (reference, index) => {
                            const value = reference as {
                              type: string;
                              count: number;
                            };
                            return (
                              <small
                                data-testid={`client-status-reference-${row.id.toLowerCase().replace(/_/g, "-")}-${index}`}
                                key={index}
                              >
                                {" "}
                                {value.type === "PROJECT"
                                  ? "프로젝트"
                                  : "개발자"}{" "}
                                {value.count}건
                              </small>
                            );
                          },
                        )}
                      </span>
                    ) : (
                      "–"
                    ),
                },
              ]}
            />
          </>
        )}
      </Dialog>
    </div>
  );
}
