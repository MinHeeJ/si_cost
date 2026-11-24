import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { recommendationEntryUrl } from "../workforcerecommendationsops/recommendationBrowseApi";
import {
  errorOutsideFields,
  useFieldErrorFocus,
} from "../../shared/FieldErrors";
import { sanitizeNumber } from "../../shared/NumericInput";
import { Dialog } from "../../shared/Dialog";
import { ApiError } from "../../shared/api";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import {
  useFieldPermission,
  usePermission,
} from "../../shared/permission/PermissionBoundary";
import { getDeveloper } from "../developers/peopleClient";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import {
  AssignmentError,
  AssignmentWarnings,
  ReasonFields,
} from "./AssignmentDialogs";
import * as api from "./assignmentActionsApi";
/** 이 창에서 칸 아래에 문구를 보여 주는 칸(SFR-017-01). 여기 없는 칸의 오류는 위쪽 띠에 남긴다. */
const createFieldsWithMessage = new Set([
  "projectId",
  "developerId",
  "jobCode",
  "inputType",
  "startDate",
  "endDate",
  "contractMm",
  "billingUnitPrice",
]);

function AssignmentRecommendationButton({
  form,
  pending,
  onRequest,
}: {
  form: api.Create;
  pending: boolean;
  onRequest(proceed: () => void): void;
}) {
  const navigate = useNavigate();
  const missing =
    !form.projectId || !form.jobCode
      ? "프로젝트와 직무를 먼저 선택해 주세요."
      : !form.startDate
        ? "투입 시작일을 먼저 입력해 주세요."
        : "";
  return (
    <div className="assignment-create-wide">
      <button
        type="button"
        data-testid="assignment-ai-recommend"
        disabled={pending || Boolean(missing)}
        aria-describedby={missing ? "assignment-ai-reason" : undefined}
        onClick={() =>
          onRequest(() =>
            navigate(
              recommendationEntryUrl({
                targetType: "PROJECT",
                targetId: form.projectId,
                jobCode: form.jobCode,
                startMonth: form.startDate.slice(0, 7),
              }),
            ),
          )
        }
      >
        AI 추천 받기
      </button>
      {missing && (
        <p id="assignment-ai-reason" data-testid="assignment-ai-reason">
          {missing}
        </p>
      )}
    </div>
  );
}

export function AssignmentCreateForm({
  year,
  today,
  onClose,
  onSaved,
  onDirtyChange,
  initialValues,
  submitAssignment,
  onCandidateSaved,
}: {
  year: number;
  today: string;
  onClose(): void;
  onSaved(value: api.Assignment): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
  initialValues?: api.Create;
  submitAssignment?(value: api.Create): Promise<{ assignmentId: string }>;
  onCandidateSaved?(id: string): Promise<void>;
}) {
  const canRate = useFieldPermission("UI-12", "billingUnitPrice"),
    canReadDeveloper = usePermission("UI-02");
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
    ...initialValues,
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
  const { pending, message, commit } = useCommitRefresh(
    submitAssignment ? "저장됐지만 다시 불러오지 못했습니다" : undefined,
  );
  const generation = useRef(0);
  const leaveToRecommendation = useRef<(() => void) | undefined>();
  const [missingMonthlyRate, setMissingMonthlyRate] = useState(false);
  const formRoot = useRef<HTMLDivElement>(null);
  useFieldErrorFocus(formRoot, fields);
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
    setMissingMonthlyRate(false);
    if (!canRate || !canReadDeveloper || !paid || !form.developerId) return;
    void getDeveloper(form.developerId)
      .then((d) => {
        if (version !== generation.current) return;
        const price = d.priceHistory.find(
          (p) =>
            p.effectiveMonth <= form.startDate.slice(0, 7) &&
            (!p.endMonth || p.endMonth >= form.startDate.slice(0, 7)),
        );
        setMissingMonthlyRate(!price?.monthlyRate);
        if (form.inputType === "SUB") return;
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
  }, [
    form.developerId,
    form.startDate,
    form.inputType,
    paid,
    canRate,
    canReadDeveloper,
  ]);
  function update<K extends keyof api.Create>(key: K, value: api.Create[K]) {
    setPreview(undefined);
    setSub(undefined);
    setStale(false);
    setForm((old) => ({
      ...old,
      [key]: value,
      ...(["projectId", "developerId", "inputType"].includes(key)
        ? {
            contractDeveloperId: null,
            billingUnitPrice: "",
          }
        : {}),
      ...(["projectId", "developerId"].includes(key)
        ? { recommendationRef: null }
        : {}),
    }));
  }
  function payload(): api.Create {
    const { billingUnitPrice, ...body } = form;
    return {
      ...body,
      year,
      ...(canRate && paid && billingUnitPrice
        ? { billingUnitPrice: billingUnitPrice.replace(/,/g, "") }
        : {}),
      contractDeveloperId:
        form.inputType === "SUB" ? form.contractDeveloperId : null,
    };
  }
  async function save(mode: "ONLY" | "TOGETHER", checkedSub?: api.SubPreview) {
    if (stale) return;
    setError(undefined);
    setFields({});
    let saved: api.Assignment | undefined;
    let savedId: string | undefined;
    try {
      await commit(
        async () => {
          if (submitAssignment) {
            savedId = (await submitAssignment(payload())).assignmentId;
            setPreview(undefined);
            onClose();
            return;
          }
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
          if (savedId) await onCandidateSaved?.(savedId);
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
      <FieldHelp label={label} />
      <input
        data-testid={`assignment-create-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
        type={type}
        value={form[key] ?? ""}
        onChange={(e) =>
          update(
            key,
            type === "date"
              ? e.target.value
              : sanitizeNumber(
                  e.target.value,
                  key === "billingUnitPrice" ? "amount" : "decimal",
                ),
          )
        }
        aria-invalid={Boolean(fields[key])}
      />
      {fields[key] && <span role="alert">{fields[key]}</span>}
      {key === "billingUnitPrice" && missingMonthlyRate && (
        <span data-testid="assignment-missing-monthly-rate">
          ⚠ 개발자 풀에 월 단가가 없는 사람
        </span>
      )}
    </label>
  );
  return (
    <>
      <Dialog
        open
        wide
        title={submitAssignment ? "신규 배정 (수주 후보)" : "신규 배정"}
        pending={pending}
        confirmLabel="저장"
        onConfirm={inspect}
        onCancel={() => setLeaving(true)}
      >
        <div
          data-testid="assignment-create-form"
          ref={formRoot}
          className="assignment-create-grid"
        >
          <p className="assignment-create-wide">
            {year}년 · 신규 등록만 가능합니다. 프로젝트·인력·직무·투입일은 저장
            후 직접 수정하지 않습니다.
          </p>
          <div className="assignment-create-wide">
            <AssignmentError
              error={errorOutsideFields(error, (key) =>
                createFieldsWithMessage.has(key),
              )}
            />
          </div>
          {message && (
            <p role="status" className="assignment-create-wide">
              {message}
            </p>
          )}
          <div className="assignment-search">
            <label className="sicost-field">
              프로젝트 검색
              <input
                data-testid="assignment-project-search"
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    document
                      .querySelector<HTMLButtonElement>(
                        '[data-testid="assignment-project-query"]',
                      )
                      ?.click();
                  }
                }}
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
              aria-invalid={Boolean(fields.projectId)}
              disabled={Boolean(submitAssignment)}
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
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    document
                      .querySelector<HTMLButtonElement>(
                        '[data-testid="assignment-developer-query"]',
                      )
                      ?.click();
                  }
                }}
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
              aria-invalid={Boolean(fields.developerId)}
              disabled={Boolean(submitAssignment)}
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
                  {d.name} ({d.managementNo}) ·{" "}
                  {d.status === "LEAVE"
                    ? "휴직"
                    : d.withdrawalDate
                      ? `투입 중 ~${d.withdrawalDate}`
                      : "대기"}
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
              aria-invalid={Boolean(fields.jobCode)}
              disabled={Boolean(submitAssignment)}
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
            {fields.jobCode && <span role="alert">{fields.jobCode}</span>}
          </label>
          <label className="sicost-field">
            투입구분 *
            <select
              data-testid="assignment-kind"
              aria-invalid={Boolean(fields.inputType)}
              disabled={Boolean(submitAssignment)}
              value={form.inputType}
              onChange={(e) => update("inputType", e.target.value)}
            >
              <option value="NEW">신규</option>
              <option value="ADD">추가</option>
              <option value="SUB">대체</option>
              <option value="FREE">무상</option>
            </select>
            {fields.inputType && <span role="alert">{fields.inputType}</span>}
          </label>
          {field("투입일 *", "startDate", "date")}
          {field("철수일 *", "endDate", "date")}
          {field("계약 월MM (빈칸 1.00)", "contractMm")}
          {!submitAssignment && (
            <AssignmentRecommendationButton
              form={form}
              pending={pending}
              onRequest={(proceed) => {
                leaveToRecommendation.current = proceed;
                setLeaving(true);
              }}
            />
          )}
          {paid && canRate && (
            <>
              {field("청구 단가 (원) *", "billingUnitPrice")}
              <p className="assignment-create-wide">
                시작월 유효 단가가 없으면 직접 입력하세요. SUB는 계약상 인력의
                마지막 종료월 단가를 사용합니다.
              </p>
            </>
          )}
          {form.inputType === "SUB" && (
            <label className="sicost-field assignment-create-wide">
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
          <p className="sicost-banner sicost-tone-info assignment-create-wide">
            프로젝트 매출과 사람 소속파트 정산은 별개입니다. 소속파트는 자동
            변경하지 않습니다.
          </p>
        </div>
      </Dialog>
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
        onCancel={() => {
          leaveToRecommendation.current = undefined;
          setLeaving(false);
        }}
        onConfirm={() => {
          const proceed = leaveToRecommendation.current;
          leaveToRecommendation.current = undefined;
          setLeaving(false);
          onClose();
          proceed?.();
        }}
      >
        <p>자동 저장하지 않습니다. 작성한 배정은 폐기됩니다.</p>
      </Dialog>
    </>
  );
}
