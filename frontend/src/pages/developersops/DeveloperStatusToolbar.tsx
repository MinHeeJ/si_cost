import { useEffect, useRef, useState } from "react";
import { ConfirmDialog, Dialog } from "../../shared/Dialog";
import { DataTable } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import type { DeveloperSelectionState } from "./useDeveloperSelection";
import * as api from "./peopleClient";
import "./developerActions.css";

export type DeveloperStatusRow = api.DeveloperSelectionItem & {
  name: string;
  useYn: "Y" | "N";
};
// SCR-UI-02: selection belongs to the retained list, not to the current page.
export function DeveloperStatusToolbar({
  selection,
  rows,
  total,
  onCommitted,
  onRefresh,
}: {
  selection: DeveloperSelectionState;
  rows: DeveloperStatusRow[];
  total: number;
  onCommitted(): void | Promise<void>;
  onRefresh(): void | Promise<void>;
}) {
  return (
    <section
      className="developer-bulk-toolbar"
      data-testid="developer-status-toolbar"
      aria-label="개발자 선택 작업"
    >
      <button
        data-testid="developer-select-page"
        type="button"
        disabled={selection.loading}
        onClick={() => selection.selectPage(rows, true)}
      >
        페이지 전체 선택
      </button>
      <button
        data-testid="developer-select-all"
        type="button"
        disabled={selection.loading}
        onClick={() => void selection.selectAll()}
      >
        조회된 {total}건 모두 선택
      </button>
      <button
        data-testid="developer-clear-selection"
        type="button"
        onClick={selection.clear}
      >
        선택 해제
      </button>
      <span role="status">선택 {selection.count}건</span>
      {selection.loading && (
        <span role="status">선택 대상을 불러오는 중입니다.</span>
      )}
      {!!selection.error && (
        <ErrorBanner
          message={
            selection.error instanceof Error
              ? selection.error.message
              : "전체 선택을 불러오지 못했습니다."
          }
        />
      )}
      <DeveloperStatusAction
        targets={selection.targets}
        context={selection.context}
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
export function DeveloperSingleStatus({
  row,
  onCommitted,
  onRefresh,
}: {
  row: DeveloperStatusRow;
  onCommitted(): void | Promise<void>;
  onRefresh(): void | Promise<void>;
}) {
  return (
    <DeveloperStatusAction
      key={row.id}
      targets={[{ id: row.id, revision: row.revision }]}
      context={`${row.id}:${row.revision}`}
      onlyTarget={row.useYn === "Y" ? "N" : "Y"}
      onCommitted={onCommitted}
      onRefresh={onRefresh}
    />
  );
}
function DeveloperStatusAction({
  targets,
  context,
  onlyTarget,
  disabled = false,
  onCommitted,
  onRefresh,
}: {
  targets: api.DeveloperSelectionItem[];
  context: string;
  onlyTarget?: "Y" | "N";
  disabled?: boolean;
  onCommitted(): void | Promise<void>;
  onRefresh(): void | Promise<void>;
}) {
  const [confirm, setConfirm] = useState<{
    target: "Y" | "N";
    items: api.DeveloperSelectionItem[];
    context: string;
  }>();
  const [result, setResult] = useState<api.DeveloperBulkResult>(),
    [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(),
    [refreshRequired, setRefreshRequired] = useState(false);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const saving = useRef(false);
  useEffect(() => {
    setConfirm(undefined);
  }, [context]);
  async function save() {
    if (
      !confirm ||
      confirm.context !== context ||
      saving.current ||
      refreshRequired
    )
      return;
    saving.current = true;
    setPending(true);
    setError(undefined);
    setResult(undefined);
    try {
      const data = await api.saveDeveloperBulkStatus(
        confirm.target,
        confirm.items,
      );
      const all = [...data.applied, ...data.noChange, ...data.rejected];
      const ids = new Set(all.map((item) => item.id));
      if (
        data.appliedCount !== data.applied.length ||
        data.noChangeCount !== data.noChange.length ||
        data.rejectedCount !== data.rejected.length ||
        all.length !== confirm.items.length ||
        ids.size !== confirm.items.length ||
        confirm.items.some((item) => !ids.has(item.id))
      )
        throw new Error(
          "처리 결과를 확인할 수 없습니다. 다시 조회한 뒤 수동으로 요청해 주세요.",
        );
      setResult(data);
      setConfirm(undefined);
      setRefreshRequired(true);
      try {
        await onCommitted();
        setRefreshRequired(false);
      } catch {
        setRefreshFailed(true);
        setError(
          new Error("저장됐지만 다시 불러오지 못했습니다. 재조회만 해 주세요."),
        );
      }
    } catch (reason) {
      setError(reason);
      setConfirm(undefined);
      setRefreshRequired(true);
    } finally {
      saving.current = false;
      setPending(false);
    }
  }
  async function refresh() {
    if (saving.current) return;
    saving.current = true;
    setPending(true);
    try {
      await onRefresh();
      setError(undefined);
      setRefreshRequired(false);
      setRefreshFailed(false);
    } catch (reason) {
      setError(reason);
    } finally {
      saving.current = false;
      setPending(false);
    }
  }
  const label = (target: "Y" | "N") =>
    target === "N" ? "사용 중지" : "다시 사용";
  return (
    <div
      className="developer-status-actions"
      data-testid="developer-status-actions"
    >
      {(["N", "Y"] as const)
        .filter((target) => !onlyTarget || target === onlyTarget)
        .map((target) => (
          <button
            key={target}
            data-testid={`developer-status-${target.toLowerCase()}`}
            type="button"
            disabled={disabled || pending || refreshRequired || !targets.length}
            onClick={() => {
              setError(undefined);
              setConfirm({
                target,
                items: targets.map((item) => ({ ...item })),
                context,
              });
            }}
          >
            {label(target)}
          </button>
        ))}
      {!!error && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "통신이 끊겼습니다. 다시 조회한 뒤 수동으로 요청해 주세요."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
        />
      )}
      {refreshRequired && (
        <button
          data-testid="developer-status-refresh"
          type="button"
          disabled={pending}
          onClick={() => void refresh()}
        >
          {refreshFailed ? "재조회" : "다시 조회"}
        </button>
      )}
      <ConfirmDialog
        open={!!confirm}
        title={`개발자 ${confirm ? label(confirm.target) : "상태 변경"}`}
        pending={pending}
        onConfirm={save}
        onCancel={() => setConfirm(undefined)}
      >
        <p>
          {confirm?.items.length}명의 개발자를{" "}
          {confirm ? label(confirm.target) : "변경"}하시겠습니까?
        </p>
        <p className="developer-secondary">
          현재·예정 투입이 있는 개발자는 사용을 중지할 수 없습니다. 퇴사한
          개발자는 다시 사용할 수 없습니다.
        </p>
      </ConfirmDialog>
      <BulkResultDialog result={result} onClose={() => setResult(undefined)} />
    </div>
  );
}
export function BulkResultDialog({
  result,
  onClose,
}: {
  result?: api.DeveloperBulkResult;
  onClose(): void;
}) {
  const rows = result
    ? [
        ...result.applied.map((item) => ({
          ...item,
          kind: "applied",
          message: "",
          details: undefined,
        })),
        ...result.noChange.map((item) => ({
          ...item,
          kind: "noChange",
          message: "",
          details: undefined,
        })),
        ...result.rejected.map((item) => ({ ...item, kind: "rejected" })),
      ]
    : [];
  return (
    <Dialog
      open={!!result}
      title="개발자 처리 결과"
      onConfirm={onClose}
      onCancel={onClose}
      confirmLabel="닫기"
      wide
    >
      {result && (
        <section data-testid="developer-bulk-results">
          <p>
            적용 {result.appliedCount}건 · 변경 없음 {result.noChangeCount}건 ·
            거부 {result.rejectedCount}건
          </p>
          <p>요청 {rows.length}건</p>
          <DataTable
            rows={rows}
            rowKey={(row) => row.id}
            columns={[
              { id: "id", label: "개발자 ID", render: (row) => row.id },
              {
                id: "result",
                label: "결과",
                render: (row) => (
                  <StatusBadge
                    tone={
                      row.kind === "applied"
                        ? "ok"
                        : row.kind === "rejected"
                          ? "bad"
                          : "lock"
                    }
                  >
                    {
                      (
                        {
                          applied: "적용",
                          noChange: "변경 없음",
                          rejected: "거부",
                        } as Record<string, string>
                      )[row.kind]
                    }
                  </StatusBadge>
                ),
              },
              {
                id: "reason",
                label: "사유",
                render: (row) => (
                  <span>
                    {row.message || "–"}
                    {row.details?.referenceCount !== undefined && (
                      <small className="developer-secondary">
                        차단 {row.details.referenceCount}건
                      </small>
                    )}
                    {row.details?.projects?.map((project, index) => (
                      <small
                        data-testid={`developer-bulk-project-${row.id.toLowerCase()}-${project.id.toLowerCase()}-${index}`}
                        key={`${project.id}-${index}`}
                        className="developer-secondary"
                      >
                        {project.name} ({project.id}) · {project.startDate} ~{" "}
                        {project.endDate}
                      </small>
                    ))}
                  </span>
                ),
              },
            ]}
          />
        </section>
      )}
    </Dialog>
  );
}
