import { useEffect, useRef, useState } from "react";
import { ConfirmDialog, Dialog } from "../../shared/Dialog";
import { DataTable } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import type { DeveloperSelectionState } from "./useDeveloperSelection";
import * as api from "./peopleClient";
import { getDeveloper } from "../developers/peopleClient";
import "./developerActions.css";

type StopPerson = api.DeveloperSelectionItem & {
  name: string;
  managementNo: string;
  projects: { name: string; endDate: string }[];
};

function BlockedList({ people }: { people: StopPerson[] }) {
  return (
    <>
      <p>
        아래 개발자는 투입 중인 배정이 있어 중지할 수 없습니다. 먼저 투입 배정의
        [변경 기록]에서 조기 철수로 배정을 끝내 주세요.
      </p>
      <ul>
        {people.map((person) => (
          <li
            key={person.id}
            data-testid={`developer-blocked-${person.id.toLowerCase()}`}
          >
            {person.name} ({person.managementNo}) —{" "}
            {person.projects
              .map((project) => `${project.name} ~${project.endDate}`)
              .join(", ")}
          </li>
        ))}
      </ul>
    </>
  );
}

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
    people?: StopPerson[];
    blocked?: StopPerson[];
  }>();
  const [blocked, setBlocked] = useState<StopPerson[]>([]);
  const [notice, setNotice] = useState("");
  const [result, setResult] = useState<api.DeveloperBulkResult>(),
    [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(),
    [refreshRequired, setRefreshRequired] = useState(false);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const saving = useRef(false);
  const currentSelection = useRef("");
  currentSelection.current = JSON.stringify([context, targets]);
  useEffect(() => {
    setConfirm(undefined);
  }, [context]);
  async function prepare(target: "Y" | "N") {
    setError(undefined);
    setNotice("");
    const items = targets.map((item) => ({ ...item }));
    if (target === "Y") {
      setConfirm({ target, items, context });
      return;
    }
    if (saving.current) return;
    saving.current = true;
    setPending(true);
    const selection = currentSelection.current;
    try {
      const parts = new Intl.DateTimeFormat("en-US", {
        timeZone: "Asia/Seoul",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).formatToParts(new Date());
      const part = (type: string) =>
        parts.find((value) => value.type === type)?.value;
      const today = `${part("year")}-${part("month")}-${part("day")}`;
      const people = await Promise.all(
        items.map(async (item) => {
          const [detail, first] = await Promise.all([
            getDeveloper(item.id),
            api.getDeveloperAssignments(item.id, 1, 100),
          ]);
          const assignments = [...first.items];
          for (let page = 2; (page - 1) * first.size < first.total; page++) {
            assignments.push(
              ...(await api.getDeveloperAssignments(item.id, page, 100)).items,
            );
          }
          return {
            ...item,
            name: detail.name,
            managementNo: detail.managementNo,
            projects: assignments
              .filter((a) => a.assignmentStatus === "ACT" && a.endDate >= today)
              .map((a) => ({ name: a.projectName, endDate: a.endDate })),
          };
        }),
      );
      if (selection !== currentSelection.current) return;
      const unavailable = people.filter((person) => person.projects.length);
      const available = people.filter((person) => !person.projects.length);
      if (!available.length) setBlocked(unavailable);
      else
        setConfirm({
          target,
          items: available.map(({ id, revision }) => ({ id, revision })),
          context,
          people: available,
          blocked: unavailable,
        });
    } catch (reason) {
      if (selection === currentSelection.current) setError(reason);
    } finally {
      saving.current = false;
      setPending(false);
    }
  }
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
      const inUse =
        confirm.target === "N"
          ? data.rejected.filter((item) => item.code === "DEVELOPER_IN_USE")
          : [];
      if (inUse.length) {
        setBlocked(
          inUse.map((item) => ({
            ...confirm.people!.find((person) => person.id === item.id)!,
            projects: item.details.projects ?? [],
          })),
        );
      }
      if (
        confirm.target === "Y" ||
        data.rejected.some((item) => item.code !== "DEVELOPER_IN_USE") ||
        data.noChangeCount
      )
        setResult(data);
      if (confirm.target === "N" && data.appliedCount)
        setNotice(
          `${data.appliedCount}명의 사용을 중지했습니다.${confirm.blocked?.length ? ` (${confirm.blocked.length}명은 투입 중이라 제외)` : ""}`,
        );
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
            onClick={() => void prepare(target)}
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
      {notice && <p role="status">{notice}</p>}
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
        title={
          confirm?.target === "N"
            ? `${confirm.items.length}명의 사용을 중지할까요?`
            : `개발자 ${confirm ? label(confirm.target) : "상태 변경"}`
        }
        confirmLabel={confirm?.target === "N" ? "사용 중지" : "확인"}
        confirmTestId="developer-status-confirm"
        cancelTestId="developer-status-cancel"
        pending={pending}
        onConfirm={save}
        onCancel={() => setConfirm(undefined)}
      >
        {confirm?.target === "N" ? (
          <>
            <p>
              {confirm.people?.map((person) => person.name).join(", ")} — 삭제가
              아니라 배정 후보에서 빼는 것이며 기록은 남습니다.
            </p>
            {!!confirm.blocked?.length && (
              <>
                <h3>중지할 수 없는 개발자</h3>
                <BlockedList people={confirm.blocked} />
              </>
            )}
          </>
        ) : (
          <>
            <p>
              {confirm?.items.length}명의 개발자를{" "}
              {confirm ? label(confirm.target) : "변경"}하시겠습니까?
            </p>
            <p className="developer-secondary">
              현재·예정 투입이 있는 개발자는 사용을 중지할 수 없습니다. 퇴사한
              개발자는 다시 사용할 수 없습니다.
            </p>
          </>
        )}
      </ConfirmDialog>
      <Dialog
        open={!!blocked.length}
        title="사용을 중지할 수 없습니다"
        hideCancel
        testId="developer-stop-blocked-dialog"
        confirmTestId="developer-stop-blocked-confirm"
        onConfirm={() => setBlocked([])}
        onCancel={() => setBlocked([])}
      >
        <BlockedList people={blocked} />
      </Dialog>
      <BulkResultDialog
        result={blocked.length ? undefined : result}
        onClose={() => setResult(undefined)}
      />
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
