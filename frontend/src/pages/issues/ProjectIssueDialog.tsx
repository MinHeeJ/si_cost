import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  getCurrentUser,
  getSharedCodes,
  type SharedCodeLookup,
} from "../shared/sharedApi";
import * as api from "./projectIssuesApi";
import "./issues.css";

export type IssueProjectChoice = { id: string; name: string };
export type ProjectIssueDialogProps = {
  projectId?: string;
  projectName?: string;
  projects?: IssueProjectChoice[];
  issue?: api.ProjectIssue;
  year: number;
  baseDate: string;
  host?: "UI-01" | "UI-31";
  readOnly?: boolean;
  onClose(): void;
  onSaved(result: api.IssueResult): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
};
export function ProjectIssueDialog(props: ProjectIssueDialogProps) {
  const {
    issue,
    year,
    baseDate,
    host = "UI-01",
    onClose,
    onSaved,
    onDirtyChange,
  } = props;
  const initial = useRef({
    title: issue?.title ?? "",
    dueDate: issue?.dueDate ?? defaultDue(baseDate),
    importance: issue?.importance ?? "MID",
    status: issue?.status ?? "OPEN",
    action: issue?.action ?? "",
  }).current;
  const [form, setForm] = useState(initial);
  const [projectId, setProjectId] = useState(
    issue?.projectId ?? props.projectId ?? "",
  );
  const [progress, setProgress] = useState<api.IssueProgress>();
  const [codes, setCodes] = useState<{
    importance: SharedCodeLookup[];
    status: SharedCodeLookup[];
  }>();
  const [actor, setActor] = useState("");
  const [error, setError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true),
    [pending, setPending] = useState(false),
    [stale, setStale] = useState(false),
    [leaving, setLeaving] = useState(false);
  const active = useRef(false);
  const initialized = useRef(false);
  const [reload, setReload] = useState(0);
  const dirty =
    JSON.stringify(form) !== JSON.stringify(initial) ||
    (!props.projectId && Boolean(projectId));
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, [dirty]);
  useEffect(() => {
    let live = true;
    setLoading(true);
    setProgress(undefined);
    setError(undefined);
    void Promise.all([
      getSharedCodes("IMPR"),
      getSharedCodes("ISSUE_STAT"),
      getCurrentUser(),
      projectId
        ? api.getIssueProgress(projectId, year)
        : Promise.resolve(undefined),
    ])
      .then(([impr, status, user, value]) => {
        if (live) {
          if (issue && !initialized.current && value) {
            const latest = value.issues.find(
              (r) => r.issueId === issue.issueId,
            );
            if (!latest)
              throw new Error(
                "이슈를 찾을 수 없습니다. 목록을 다시 조회해 주세요.",
              );
            const fields = {
              title: latest.title,
              dueDate: latest.dueDate,
              importance: latest.importance,
              status: latest.status,
              action: latest.action ?? "",
            };
            Object.assign(initial, fields);
            setForm(fields);
            initialized.current = true;
          }
          setCodes({ importance: impr.items, status: status.items });
          setActor(user.name);
          setProgress(value);
          setStale(false);
        }
      })
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [projectId, year, reload]);
  function change(key: keyof typeof form, value: string) {
    setForm((old) => ({ ...old, [key]: value }));
    setFields((old) => ({ ...old, [key]: "" }));
  }
  function cancel() {
    if (active.current) return;
    if (dirty) setLeaving(true);
    else onClose();
  }
  async function save() {
    if (
      active.current ||
      !progress ||
      stale ||
      loading ||
      progress.readOnly ||
      props.readOnly
    )
      return;
    const invalid: Record<string, string> = {};
    if (!form.title.trim() || [...form.title.trim()].length > 100)
      invalid.title = "제목을 1~100자로 입력해 주세요.";
    if ([...form.action.trim()].length > 500)
      invalid.action = "조치 내용은 500자 이내로 입력해 주세요.";
    if (!form.dueDate) invalid.dueDate = "기한을 입력해 주세요.";
    setFields(invalid);
    if (Object.keys(invalid).length) return;
    active.current = true;
    setPending(true);
    setError(undefined);
    try {
      const body: api.IssueWrite = {
        year,
        expectedRevision: progress.progressRevision,
        title: form.title.trim(),
        dueDate: form.dueDate,
        importance: form.importance,
        status: form.status,
        action: form.action.trim() || null,
      };
      const result = issue
        ? await api.putProjectIssue(projectId, issue.issueId, body)
        : await api.createProjectIssue(projectId, body);
      await onSaved(result);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        setStale(e.status === 409);
      }
    } finally {
      active.current = false;
      setPending(false);
    }
  }
  const locked =
    pending ||
    loading ||
    Boolean(progress?.readOnly) ||
    Boolean(props.readOnly);
  const field = (key: keyof typeof form, label: string) => (
    <div className="sicost-field">
      <label htmlFor={`issue-${key}`}>{label}</label>
      <input
        id={`issue-${key}`}
        data-testid={`issue-${key}`}
        type={key === "dueDate" ? "date" : "text"}
        value={form[key]}
        aria-invalid={Boolean(fields[key])}
        disabled={locked}
        onChange={(e) => change(key, e.target.value)}
      />
      {fields[key] && <p className="sicost-field-error">{fields[key]}</p>}
    </div>
  );
  const choices = (key: "importance" | "status", label: string) => {
    const values = codes?.[key] ?? [];
    return (
      <div className="sicost-field">
        <label htmlFor={`issue-${key}`}>{label}</label>
        <select
          id={`issue-${key}`}
          data-testid={`issue-${key}`}
          value={form[key]}
          disabled={locked || !codes}
          aria-invalid={Boolean(fields[key])}
          onChange={(e) => change(key, e.target.value)}
        >
          {!values.some((c) => c.code === form[key]) && (
            <option value={form[key]}>{form[key]}</option>
          )}
          {values.map((c) => (
            <option key={c.code} value={c.code}>
              {c.name}
            </option>
          ))}
        </select>
        {fields[key] && <p className="sicost-field-error">{fields[key]}</p>}
      </div>
    );
  };
  return (
    <>
      <Dialog
        open
        title={issue ? "이슈 수정" : "이슈 등록"}
        pending={pending}
        onCancel={cancel}
        onConfirm={save}
        confirmLabel="저장"
        confirmDisabled={loading || !progress || locked || stale || !codes}
      >
        <section
          className="issue-form"
          data-testid="project-issue-dialog"
          data-screen-id={`SCR-${host}-B138-ISSUE-${issue ? "EDIT" : "NEW"}-OPEN`}
          data-submit-screen-id={`SCR-${host}-B138-ISSUE-${issue ? "EDIT" : "NEW"}-SUBMIT`}
          data-cancel-screen-id={`SCR-${host}-B138-ISSUE-${issue ? "EDIT" : "NEW"}-CANCEL`}
        >
          {issue || props.projectId ? (
            <p>
              프로젝트 {issue?.projectName ?? props.projectName ?? projectId}
            </p>
          ) : (
            <div className="sicost-field">
              <label htmlFor="issue-project">프로젝트 *</label>
              <select
                id="issue-project"
                data-testid="issue-project"
                value={projectId}
                disabled={locked}
                onChange={(e) => {
                  setProgress(undefined);
                  setProjectId(e.target.value);
                }}
              >
                <option value="">선택해 주세요</option>
                {props.projects?.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
          )}
          {loading && <p role="status">입력 기준을 불러오는 중입니다.</p>}
          {error != null && (
            <ErrorBanner
              message={
                error instanceof Error ? error.message : "처리하지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={() => setReload((v) => v + 1)}
              pending={pending}
            />
          )}
          {stale && (
            <p className="sicost-banner sicost-tone-warn">
              입력 내용은 유지됩니다. 최신 자료를 다시 조회한 뒤 직접 저장해
              주세요.
            </p>
          )}
          {progress?.readOnly && (
            <p className="sicost-banner sicost-tone-lock">
              조회 전용 · {progress.readOnlyReasons.join(" · ")}
            </p>
          )}
          {field("title", "제목 *")}
          <div className="issue-form-pair">
            {field("dueDate", "기한 *")}
            {choices("importance", "중요도 *")}
          </div>
          <p>담당자 {issue?.registeredBy.userName ?? actor} (읽기 전용)</p>
          {choices("status", "상태 *")}
          <div className="sicost-field">
            <label htmlFor="issue-action">조치 내용</label>
            <textarea
              id="issue-action"
              data-testid="issue-action"
              value={form.action}
              disabled={locked}
              aria-invalid={Boolean(fields.action)}
              onChange={(e) => change("action", e.target.value)}
            />
            {fields.action && (
              <p className="sicost-field-error">{fields.action}</p>
            )}
          </div>
          {issue && (
            <p>
              등록 {issue.registeredAt} · {issue.registeredBy.userName}
              <br />
              수정 {issue.modifiedAt ?? "–"} ·{" "}
              {issue.modifiedBy?.userName ?? "–"}
            </p>
          )}
        </section>
      </Dialog>
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
        cancelLabel="계속 작성"
        confirmLabel="나가기"
      >
        <p>저장하지 않고 이슈 창을 닫습니다.</p>
      </Dialog>
    </>
  );
}
function defaultDue(baseDate: string) {
  const date = new Date(`${baseDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + 7);
  return date.toISOString().slice(0, 10);
}
