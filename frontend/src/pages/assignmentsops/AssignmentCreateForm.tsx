import { useEffect, useRef, useState } from "react";
import { FormPage } from "../../shared/FormPage";
import { Dialog } from "../../shared/Dialog";
import { ApiError } from "../../shared/api";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { getDeveloper } from "../developers/peopleClient";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import {
  AssignmentError,
  AssignmentWarnings,
  ReasonFields,
} from "./AssignmentDialogs";
import * as api from "./assignmentActionsApi";

export function AssignmentCreateForm({
  year,
  today,
  onClose,
  onSaved,
  onDirtyChange,
}: {
  year: number;
  today: string;
  onClose(): void;
  onSaved(value: api.Assignment): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [form, setForm] = useState<api.Create>({
    year,
    projectId: "",
    developerId: "",
    jobCode: "",
    startDate: today,
    endDate: today,
    contractMm: "",
    billingUnitPrice: "",
    inputType: "NEW",
    contractDeveloperId: null,
  });
  const [projects, setProjects] = useState<api.ProjectOption[]>([]),
    [developers, setDevelopers] = useState<api.DeveloperOption[]>([]),
    [jobs, setJobs] = useState<SharedCodeLookup[]>([]),
    [candidates, setCandidates] = useState<api.Candidate[]>([]),
    [projectSearch, setProjectSearch] = useState(""),
    [developerSearch, setDeveloperSearch] = useState("");
  const [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({}),
    [preview, setPreview] = useState<api.Preview>(),
    [sub, setSub] = useState<api.SubPreview>(),
    [selected, setSelected] = useState<string[]>([]),
    [reason, setReason] = useState(""),
    [memo, setMemo] = useState(""),
    [leaving, setLeaving] = useState(false),
    [stale, setStale] = useState(false);
  const { pending, message, commit } = useCommitRefresh();
  const generation = useRef(0);
  const project = projects.find((p) => p.id === form.projectId);
  const paid =
    project &&
    ["MO", "DP"].includes(project.contractType) &&
    form.inputType !== "FREE";
  useEffect(() => {
    onDirtyChange?.(true);
    return () => onDirtyChange?.(false);
  }, [onDirtyChange]);
  useEffect(() => {
    let live = true;
    void Promise.all([
      api.projectOptions(""),
      api.developerOptions(""),
      getSharedCodes("JOB"),
    ])
      .then(([p, d, j]) => {
        if (live) {
          setProjects(p.items);
          setDevelopers(d.items);
          setJobs(j.items);
        }
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, []);
  useEffect(() => {
    let live = true;
    if (form.inputType !== "SUB" || !form.projectId || !form.developerId) {
      setCandidates([]);
      return;
    }
    void api
      .candidates(form.projectId, form.developerId)
      .then((v) => {
        if (live) setCandidates(v.items);
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, [form.projectId, form.developerId, form.inputType]);
  useEffect(() => {
    const version = ++generation.current;
    if (!paid || form.inputType === "SUB" || !form.developerId) return;
    void getDeveloper(form.developerId)
      .then((d) => {
        if (version !== generation.current) return;
        const price = d.priceHistory.find(
          (p) =>
            p.effectiveMonth <= form.startDate.slice(0, 7) &&
            (!p.endMonth || p.endMonth >= form.startDate.slice(0, 7)),
        );
        setForm((old) => ({
          ...old,
          billingUnitPrice: price?.monthlyRate ?? "",
          jobCode: old.jobCode || d.jobCode,
        }));
      })
      .catch((e) => {
        if (version === generation.current) setError(e);
      });
    return () => {
      generation.current++;
    };
  }, [form.developerId, form.startDate, form.inputType, paid]);
  function update<K extends keyof api.Create>(key: K, value: api.Create[K]) {
    setPreview(undefined);
    setSub(undefined);
    setStale(false);
    setForm((old) => ({
      ...old,
      [key]: value,
      ...(["projectId", "developerId", "inputType"].includes(key)
        ? { contractDeveloperId: null, billingUnitPrice: "" }
        : {}),
    }));
  }
  function payload(): api.Create {
    const { billingUnitPrice, ...body } = form;
    return {
      ...body,
      year,
      ...(paid && billingUnitPrice ? { billingUnitPrice } : {}),
      contractDeveloperId:
        form.inputType === "SUB" ? form.contractDeveloperId : null,
    };
  }
  async function save(mode: "ONLY" | "TOGETHER", checkedSub?: api.SubPreview) {
    if (stale) return;
    setError(undefined);
    setFields({});
    let saved: api.Assignment | undefined;
    try {
      await commit(
        async () => {
          const s = checkedSub ?? sub;
          const result = s
            ? await api.substitute(
                payload(),
                mode,
                s.previewToken,
                mode === "TOGETHER"
                  ? s.candidates
                      .filter((i) => selected.includes(i.id))
                      .map((i) => ({
                        assignmentId: i.id,
                        expectedRevision: i.expectedRevision,
                        withdrawalDate: s.withdrawalDate,
                        reasonCode: reason,
                        ...(memo ? { memo } : {}),
                      }))
                  : [],
              )
            : await api.createAssignment(payload());
          saved = result.assignment;
          setPreview(undefined);
          setSub(undefined);
          onClose();
        },
        async () => {
          if (saved) await onSaved(saved);
        },
      );
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        if (e.body.code === "STALE_WRITE") setStale(true);
      }
    }
  }
  async function inspect() {
    setError(undefined);
    try {
      const p = await api.preview(payload());
      setPreview(p);
      if (form.inputType === "SUB") {
        const s = await api.substitutionPreview(payload());
        setSub(s);
        setSelected(s.candidates.map((i) => i.id));
        setMemo(
          `대체투입(${developers.find((d) => d.id === form.developerId)?.name ?? form.developerId})`,
        );
        if (!s.candidates.length && !s.protectedMonths.length)
          await save("ONLY", s);
      }
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) setFields(e.fieldMessages);
    }
  }
  const field = (
    label: string,
    key: "startDate" | "endDate" | "contractMm" | "billingUnitPrice",
    type = "text",
  ) => (
    <label className="sicost-field">
      {label}
      <input
        data-testid={`assignment-create-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
        type={type}
        value={form[key] ?? ""}
        onChange={(e) => update(key, e.target.value)}
        aria-invalid={Boolean(fields[key])}
      />
      {fields[key] && <span role="alert">{fields[key]}</span>}
    </label>
  );
  return (
    <>
      <FormPage
        title="신규 배정"
        actions={
          <button
            type="button"
            data-testid="assignment-create-close"
            disabled={pending}
            onClick={() => setLeaving(true)}
          >
            닫기
          </button>
        }
      >
        <div data-testid="assignment-create-form" className="assignment-form">
          <p>
            YEAR {year} · 신규 등록만 가능합니다. 프로젝트·인력·직무·투입일은
            저장 후 직접 수정하지 않습니다.
          </p>
          <AssignmentError error={error} />
          {message && <p role="status">{message}</p>}
          <div className="assignment-search">
            <label className="sicost-field">
              프로젝트 검색
              <input
                data-testid="assignment-project-search"
                value={projectSearch}
                onChange={(e) => setProjectSearch(e.target.value)}
              />
            </label>
            <button
              type="button"
              data-testid="assignment-project-query"
              onClick={() =>
                void api
                  .projectOptions(projectSearch)
                  .then((v) => setProjects(v.items))
                  .catch(setError)
              }
            >
              프로젝트 조회
            </button>
          </div>
          <label className="sicost-field">
            프로젝트 *
            <select
              data-testid="assignment-project"
              value={form.projectId}
              onChange={(e) => {
                const p = projects.find((v) => v.id === e.target.value);
                update("projectId", e.target.value);
                if (p)
                  setForm((old) => ({
                    ...old,
                    startDate: p.startDate,
                    endDate: p.endDate,
                  }));
              }}
            >
              <option value="">선택해 주세요</option>
              {projects.map((p) => (
                <option
                  data-testid={`assignment-project-option-${p.id.toLowerCase()}`}
                  key={p.id}
                  value={p.id}
                >
                  {p.name} · {p.contractType} · {p.status}
                </option>
              ))}
            </select>
            {fields.projectId && <span role="alert">{fields.projectId}</span>}
          </label>
          <div className="assignment-search">
            <label className="sicost-field">
              인력 검색
              <input
                data-testid="assignment-developer-search"
                value={developerSearch}
                onChange={(e) => setDeveloperSearch(e.target.value)}
              />
            </label>
            <button
              type="button"
              data-testid="assignment-developer-query"
              onClick={() =>
                void api
                  .developerOptions(developerSearch)
                  .then((v) => setDevelopers(v.items))
                  .catch(setError)
              }
            >
              인력 조회
            </button>
          </div>
          <label className="sicost-field">
            실제 투입자 *
            <select
              data-testid="assignment-developer"
              value={form.developerId}
              onChange={(e) => update("developerId", e.target.value)}
            >
              <option value="">선택해 주세요</option>
              {developers.map((d) => (
                <option
                  data-testid={`assignment-developer-option-${d.id.toLowerCase()}`}
                  key={d.id}
                  value={d.id}
                >
                  {d.name} ({d.managementNo})
                </option>
              ))}
            </select>
            {fields.developerId && (
              <span role="alert">{fields.developerId}</span>
            )}
          </label>
          <label className="sicost-field">
            직무 *
            <select
              data-testid="assignment-job"
              value={form.jobCode}
              onChange={(e) => update("jobCode", e.target.value)}
            >
              <option value="">선택해 주세요</option>
              {jobs.map((j) => (
                <option
                  data-testid={`assignment-job-option-${j.code.toLowerCase().replace(/_/g, "-")}`}
                  key={j.code}
                  value={j.code}
                >
                  {j.name}
                </option>
              ))}
            </select>
          </label>
          <label className="sicost-field">
            투입구분 *
            <select
              data-testid="assignment-kind"
              value={form.inputType}
              onChange={(e) => update("inputType", e.target.value)}
            >
              <option value="NEW">신규</option>
              <option value="ADD">추가</option>
              <option value="SUB">대체</option>
              <option value="FREE">무상</option>
            </select>
          </label>
          {field("투입일 *", "startDate", "date")}
          {field("철수일 *", "endDate", "date")}
          {field("계약 월MM (빈칸 1.00)", "contractMm")}
          {paid && (
            <>
              {field("청구 단가 (원) *", "billingUnitPrice")}
              <p>
                시작월 유효 단가가 없으면 직접 입력하세요. SUB는 계약상 인력의
                마지막 종료월 단가를 사용합니다.
              </p>
            </>
          )}
          {form.inputType === "SUB" && (
            <label className="sicost-field">
              계약상 인력 *
              <select
                data-testid="assignment-contract-developer"
                value={form.contractDeveloperId ?? ""}
                onChange={(e) => {
                  const c = candidates.find(
                    (i) => i.developerId === e.target.value,
                  );
                  setForm((old) => ({
                    ...old,
                    contractDeveloperId: c?.developerId ?? null,
                    billingUnitPrice: c?.defaultUnitPrice ?? "",
                  }));
                }}
              >
                <option value="">선택해 주세요</option>
                {candidates.map((c) => (
                  <option
                    data-testid={`assignment-contract-developer-option-${c.developerId.toLowerCase()}`}
                    key={c.developerId}
                    value={c.developerId}
                  >
                    {c.developerName} · {c.managementNo} · 최종철수{" "}
                    {c.withdrawalDate}
                  </option>
                ))}
              </select>
            </label>
          )}
          <p className="sicost-banner sicost-tone-info">
            프로젝트 매출과 사람 소속파트 정산은 별개입니다. 소속파트는 자동
            변경하지 않습니다.
          </p>
          <div className="assignment-toolbar">
            <button
              type="button"
              data-testid="assignment-create-cancel"
              disabled={pending}
              onClick={() => setLeaving(true)}
            >
              취소
            </button>
            <button
              type="button"
              data-testid="assignment-create-save"
              className="sicost-primary"
              disabled={pending}
              onClick={() => void inspect()}
            >
              저장
            </button>
          </div>
        </div>
      </FormPage>
      <Dialog
        open={Boolean(preview) && !sub}
        title="저장전 경고 확인"
        pending={pending}
        onConfirm={() => save("ONLY")}
        confirmLabel="확인 후 저장"
        onCancel={() => setPreview(undefined)}
      >
        <div data-testid="assignment-preview-dialog">
          {preview && (
            <>
              <AssignmentWarnings
                warnings={preview.warnings}
                protectedMonths={preview.protectedMonths}
              />
              <p>
                예상 월자료 {preview.months.length}건 · 저장 전 계산이며 최종
                저장에서 다시 검사합니다.
              </p>
            </>
          )}
          <AssignmentError error={error} />
        </div>
      </Dialog>
      <Dialog
        open={Boolean(sub)}
        title="계약상 인력 동시 철수 검토"
        pending={pending}
        onConfirm={() => save("TOGETHER")}
        confirmLabel="같이 처리"
        confirmDisabled={stale || !selected.length || !reason}
        onCancel={() => {
          setSub(undefined);
          setPreview(undefined);
        }}
        wide
      >
        <div data-testid="assignment-substitution-dialog">
          {sub && (
            <>
              <p>
                예정 철수일 {sub.withdrawalDate} · 실제 투입자{" "}
                {form.developerId} / 계약상 인력 {form.contractDeveloperId}
              </p>
              {sub.candidates.map((i) => (
                <label
                  data-testid={`substitution-candidate-${i.id.toLowerCase()}`}
                  key={i.id}
                >
                  <input
                    data-testid={`substitution-select-${i.id.toLowerCase()}`}
                    type="checkbox"
                    checked={selected.includes(i.id)}
                    onChange={(e) =>
                      setSelected((old) =>
                        e.target.checked
                          ? [...old, i.id]
                          : old.filter((v) => v !== i.id),
                      )
                    }
                  />
                  {i.id}
                </label>
              ))}
              <ReasonFields
                reason={reason}
                memo={memo}
                onChange={(k, v) =>
                  k === "reason" ? setReason(v) : setMemo(v)
                }
              />
              <AssignmentWarnings
                warnings={sub.warnings}
                protectedMonths={sub.protectedMonths}
              />
              {sub.turnkeyNotice && (
                <p className="sicost-banner sicost-tone-warn">
                  {sub.turnkeyNotice}
                </p>
              )}
              <p>
                대체만 저장하면 기존배정은 유지되며 겹친 기간 매출/MM이 두
                사람분 잡힐 수 있습니다.
              </p>
              <button
                type="button"
                data-testid="substitution-only"
                disabled={pending || stale}
                onClick={() => void save("ONLY")}
              >
                대체투입만 저장
              </button>
              {stale && (
                <button
                  type="button"
                  data-testid="substitution-repreview"
                  onClick={() => {
                    setStale(false);
                    void inspect();
                  }}
                >
                  다시 미리보기
                </button>
              )}
              <AssignmentError error={error} />
            </>
          )}
        </div>
      </Dialog>
      <Dialog
        open={leaving}
        title="작성 중인 내용을 나가시겠습니까?"
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
      >
        <p>자동 저장하지 않습니다. 작성한 배정은 폐기됩니다.</p>
      </Dialog>
    </>
  );
}
