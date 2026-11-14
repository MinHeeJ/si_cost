import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { FormPage } from "../../shared/FormPage";
import { Text } from "../../shared/Text";
import { CodeSelect } from "../../shared/CodeSelect";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { getSharedCodes } from "../shared/sharedApi";
import {
  getClientOptions,
  type ClientOption,
} from "../clientsops/clientActionsApi";
import { getClient } from "../clients/clientsApi";
import {
  getSalesPartOptions,
  type PartOption,
} from "../salespartsops/partStandardsApi";
import { projectMoneyInput } from "./projectFormat";
import * as api from "./projectsApi";

type Props = {
  year: number;
  modal?: boolean;
  visible?: boolean;
  detail?: api.ProjectDetail;
  currentPartName?: string;
  onClose(): void;
  onSaved(result: api.SaveProjectResult): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
};
export function ProjectForm({
  year,
  modal = false,
  visible = true,
  detail,
  currentPartName,
  onClose,
  onSaved,
  onDirtyChange,
}: Props) {
  const p = detail?.project;
  const [form, setForm] = useState({
    name: p?.name ?? "",
    customerId: p?.customerId ?? "",
    contractType: p?.contractType ?? ("" as string),
    initialStatus: "ING",
    partId: "",
    startDate: p?.startDate ?? "",
    endDate: p?.endDate ?? "",
    workplace: p?.workplace ?? "",
    feature: p?.feature ?? "",
    estimateAmount: p?.estimateAmount ?? "",
    contractAmount: p?.contractAmount ?? "",
    budgetAmount: p?.budgetAmount ?? "",
    budgetMode: p?.budgetManual === "Y" ? "MANUAL" : "AUTO",
    teamExpenseAmount: p?.teamExpenseAmount ?? "",
    projectExpenseAmount: p?.projectExpenseAmount ?? "",
  });
  const initial = useRef(form),
    node = useRef<HTMLFormElement>(null),
    busy = useRef(false);
  const [codes, setCodes] = useState<
    Record<string, { code: string; name: string }[]>
  >({});
  const [customers, setCustomers] = useState<ClientOption[]>([]),
    [parts, setParts] = useState<PartOption[]>([]);
  const [loading, setLoading] = useState(true),
    [lookupError, setLookupError] = useState<unknown>();
  const [preview, setPreview] = useState<api.ProjectDefaults>(),
    [previewError, setPreviewError] = useState<unknown>();
  const [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false),
    [leaving, setLeaving] = useState(false),
    [committed, setCommitted] = useState<api.SaveProjectResult>();
  const [refreshFailed, setRefreshFailed] = useState(false);
  const internal = form.contractType === "IN",
    dirty =
      !committed && JSON.stringify(form) !== JSON.stringify(initial.current);
  async function load() {
    setLoading(true);
    setLookupError(undefined);
    try {
      const [types, states, clients, teams] = await Promise.all([
        getSharedCodes("CTRT_TYPE", Boolean(p)),
        getSharedCodes("PROJ_STAT"),
        getClientOptions("CUSTOMER"),
        getSalesPartOptions(),
      ]);
      setCodes({
        CTRT_TYPE: types.items,
        PROJ_STAT: states.items.filter(
          (c) => c.code === "ING" || c.code === "PRE",
        ),
      });
      if (p?.customerId && !clients.items.some((c) => c.id === p.customerId)) {
        const existing = await getClient(p.customerId);
        clients.items = [
          {
            id: existing.id,
            name: `${existing.name} (중지)`,
            type: existing.type,
          },
          ...clients.items,
        ];
      }
      setCustomers(clients.items);
      setParts(teams.items);
    } catch (e) {
      setLookupError(e);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
  }, []);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const guard = (e: BeforeUnloadEvent) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, [dirty]);
  useEffect(() => {
    if (Object.keys(fields).length)
      node.current
        ?.querySelector<HTMLElement>('[aria-invalid="true"]')
        ?.focus();
  }, [fields]);
  useEffect(() => {
    let live = true;
    const amount = projectMoneyInput(
      form.contractAmount || (internal ? "0" : ""),
    );
    setPreview(undefined);
    setPreviewError(undefined);
    if (
      amount !== null &&
      ["TK", "MO", "DP", "IN"].includes(form.contractType)
    ) {
      void api
        .getProjectDefaults(amount, form.contractType as api.ContractType)
        .then((v) => {
          if (live) setPreview(v);
        })
        .catch((e) => {
          if (live) setPreviewError(e);
        });
    }
    return () => {
      live = false;
    };
  }, [form.contractAmount, form.contractType, internal]);
  function change(key: keyof typeof form, value: string) {
    setForm((f) => ({
      ...f,
      [key]: value,
      ...(key === "contractType" && value === "IN"
        ? {
            customerId: "",
            partId: "",
            workplace: "",
            budgetAmount: "",
            budgetMode: "AUTO",
          }
        : {}),
    }));
    setFields((f) => ({ ...f, [key]: "" }));
  }
  const close = () => {
    if (!pending) {
      if (dirty) setLeaving(true);
      else onClose();
    }
  };
  useEffect(() => {
    if (!modal || !visible || leaving) return;
    const previous=document.activeElement as HTMLElement | null;
    node.current?.querySelector<HTMLInputElement>("input")?.focus();
    const escape=(event: KeyboardEvent) => { if(event.key === "Escape") { event.preventDefault(); close(); } };
    document.addEventListener("keydown", escape);
    return () => { document.removeEventListener("keydown", escape); previous?.focus?.(); };
  }, [modal, visible, leaving, dirty, pending]);
  async function refresh(result: api.SaveProjectResult) {
    try {
      await onSaved(result);
      setRefreshFailed(false);
    } catch {
      setRefreshFailed(true);
    }
  }
  async function save() {
    if (busy.current || committed) return;
    const errors: Record<string, string> = {};
    if (!form.name.trim() || [...form.name.trim()].length > 100)
      errors.name = "프로젝트명은 1~100자로 입력해 주세요.";
    if (!form.contractType) errors.contractType = "계약유형을 선택해 주세요.";
    if (!form.startDate) errors.startDate = "계약 시작일을 입력해 주세요.";
    if (!form.endDate || form.endDate < form.startDate)
      errors.endDate = "계약 종료일은 시작일 이후여야 합니다.";
    if (!internal && !form.customerId)
      errors.customerId = "활성 고객사를 선택해 주세요.";
    if (!p && !internal && !form.partId)
      errors.partId = "영업 파트를 선택해 주세요.";
    const money: Record<string, string | null> = {};
    for (const key of [
      "estimateAmount",
      "contractAmount",
      "budgetAmount",
      "teamExpenseAmount",
      "projectExpenseAmount",
    ] as const) {
      if (key === "budgetAmount" && (internal || form.budgetMode !== "MANUAL"))
        continue;
      money[key] = form[key].trim() ? projectMoneyInput(form[key]) : null;
      if (
        (form[key].trim() && money[key] === null) ||
        (key === "contractAmount" && !internal && money[key] === null) ||
        (key === "budgetAmount" && money[key] === null)
      )
        errors[key] = "0~999,999,999,999,999원의 정수로 입력해 주세요.";
    }
    setFields(errors);
    if (Object.keys(errors).length) return;
    busy.current = true;
    setPending(true);
    setError(undefined);
    try {
      const body: api.ProjectFields = {
        year,
        name: form.name.trim(),
        startDate: form.startDate,
        endDate: form.endDate,
        customerId: internal ? null : form.customerId,
        workplace: internal ? null : form.workplace.trim() || null,
        feature: form.feature.trim() || null,
        estimateAmount: money.estimateAmount,
        contractAmount: money.contractAmount,
        teamExpenseAmount: money.teamExpenseAmount,
        projectExpenseAmount: money.projectExpenseAmount,
        ...(!internal
          ? {
              budgetMode: form.budgetMode as "AUTO" | "MANUAL",
              ...(form.budgetMode === "MANUAL"
                ? { budgetAmount: money.budgetAmount }
                : {}),
            }
          : {}),
      };
      const result =
        p && detail
          ? await api.putProject(p.id, {
              ...body,
              expectedRevision: detail.revision,
            })
          : await api.createProject({
              ...body,
              contractType: form.contractType as api.ContractType,
              initialStatus: form.initialStatus as "ING" | "PRE",
              partId: internal ? null : form.partId,
            });
      setCommitted(result);
      onDirtyChange?.(false);
      api.notifyProjectsCommitted(result.project.id);
      await refresh(result);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) setFields(e.fieldMessages);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  const input = (
    key: keyof typeof form,
    label: string,
    options: { required?: boolean; type?: string; maxLength?: number } = {},
  ) => (
    <Text
      key={key}
      data-testid={`project-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
      label={label}
      value={form[key]}
      onChange={(v) => change(key, v)}
      error={fields[key]}
      disabled={pending || Boolean(committed)}
      {...options}
    />
  );
  const select = (
    key: keyof typeof form,
    label: string,
    options: { code: string; name: string }[],
  ) => (
    <CodeSelect
      label={label}
      value={form[key]}
      options={options}
      onChange={(v) => change(key, v)}
      disabled={pending || loading || Boolean(committed)}
      error={fields[key]}
    />
  );
  const content = (
    <FormPage
      title={p ? "프로젝트 수정" : "프로젝트 등록"}
      actions={
        <button
          type="button"
          data-testid="project-form-back"
          disabled={pending}
          onClick={close}
        >
          목록
        </button>
      }
    >
      <section data-testid="project-form" data-screen-id="SCR-UI-01">
        {loading && <p role="status">선택 항목을 불러오는 중입니다.</p>}
        {lookupError != null && (
          <ErrorBanner
            message="선택 항목을 불러오지 못했습니다."
            onRetry={() => void load()}
          />
        )}
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error ? error.message : "저장하지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
          />
        )}
        {error instanceof ApiError && error.body.details && (
          <p role="alert">
            충돌 대상과 월: {JSON.stringify(error.body.details)}
          </p>
        )}
        {error instanceof ApiError && error.body.code === "STALE_WRITE" && (
          <p>
            다시 조회한 뒤 수정해 주세요. 입력을 유지하며 자동 재저장하지
            않습니다.
          </p>
        )}
        <form
          ref={node}
          noValidate
          data-testid="project-edit-form"
          onSubmit={(e) => {
            e.preventDefault();
            void save();
          }}
        >
          <fieldset
            className="project-form-grid"
            disabled={pending || Boolean(committed)}
          >
            <legend>기본 정보</legend>
            <div className="project-wide">
              {input("name", "프로젝트명", { required: true, maxLength: 100 })}
            </div>
            {!internal &&
              select(
                "customerId",
                "고객사",
                customers.map((c) => ({ code: c.id, name: c.name })),
              )}
            {!p ? (
              select("contractType", "계약유형", codes.CTRT_TYPE ?? [])
            ) : (
              <div>
                <strong>
                  계약유형{" "}
                  {codes.CTRT_TYPE?.find((c) => c.code === p.contractType)
                    ?.name ?? p.contractType}
                </strong>
                <p>
                  계약유형은 등록 후 바꿀 수 없습니다. 원가·매출 계산 방식이
                  달라지기 때문입니다.
                </p>
              </div>
            )}
            {input("startDate", "계약 시작일", {
              required: true,
              type: "date",
            })}
            {input("endDate", "계약 종료일", { required: true, type: "date" })}
            {!p ? (
              select("initialStatus", "초기 상태", codes.PROJ_STAT ?? [])
            ) : (
              <div>
                상태 {detail?.effectiveStatus}
                <p>
                  상태는 여기서 바꿀 수 없습니다. 수주예정이면 프로젝트 상세의
                  [⋯]에서 진행 또는 실주로 바꿉니다.
                </p>
              </div>
            )}
            {!internal &&
              (!p ? (
                select(
                  "partId",
                  "영업 파트",
                  parts.map((t) => ({ code: t.id, name: t.name })),
                )
              ) : (
                <div>
                  영업 파트 {currentPartName ?? "미지정"}
                  <p>
                    영업 파트는 여기서 바꿀 수 없습니다. 프로젝트 상세의 영업
                    파트 [변경]에서 적용 시작 월과 함께 바꿉니다.
                  </p>
                </div>
              ))}
            {!internal && (
              <div className="project-wide">
                {input("workplace", "근무지", { maxLength: 200 })}
              </div>
            )}
          </fieldset>
          <fieldset
            className="project-form-grid"
            disabled={pending || Boolean(committed)}
          >
            <legend>금액 (원)</legend>
            {input("estimateAmount", "견적 금액")}
            {input("contractAmount", "계약금액", { required: !internal })}
            {!internal && (
              <>
                <CodeSelect
                  label="예산 입력 방식"
                  value={form.budgetMode}
                  options={
                    p?.budgetManual === "Y"
                      ? [{ code: "MANUAL", name: "수기" }]
                      : [
                          { code: "AUTO", name: "자동" },
                          { code: "MANUAL", name: "수기" },
                        ]
                  }
                  onChange={(v) => change("budgetMode", v)}
                  error={fields.budgetMode}
                  disabled={pending || Boolean(committed)}
                />
                {form.budgetMode === "MANUAL" ? (
                  input("budgetAmount", "예산(목표 원가)", { required: true })
                ) : (
                  <p>
                    자동 예산{" "}
                    {p && form.contractAmount === initial.current.contractAmount
                      ? (p.budgetAmount ?? "–")
                      : (preview?.budgetAmount ?? "계산 불가")}
                  </p>
                )}
                {fields.budgetAmount && form.budgetMode !== "MANUAL" && (
                  <p role="alert">
                    {fields.budgetAmount} 유효한 기준이 없으면 수기로 입력해
                    주세요.
                  </p>
                )}
              </>
            )}
            {input("teamExpenseAmount", "팀비(월)")}
            {input("projectExpenseAmount", "프로젝트경비(월)")}
            {preview && (
              <div className="project-wide sicost-banner sicost-tone-info">
                서버 미리보기 · {preview.basisMonth} · 목표율{" "}
                {preview.targetMarginRate ?? "없음"} · 자동 예산{" "}
                {preview.budgetAmount ?? "없음"} · 팀비 기본{" "}
                {preview.teamExpenseAmount ?? "없음"} · 경비 기본{" "}
                {preview.projectExpenseAmount ?? "없음"}
                <p>
                  {preview.warnings.join(" ")} 빈 팀비·경비는 저장 시점의
                  기본값을 사용합니다. 명시한 0은 유지합니다.
                </p>
              </div>
            )}
            {previewError != null && (
              <ErrorBanner message="서버 미리보기를 조회하지 못했습니다. 수기로 입력할 수 있습니다." />
            )}
          </fieldset>
          <fieldset
            className="project-form-grid"
            disabled={pending || Boolean(committed)}
          >
            <legend>특징</legend>
            <div className="project-wide">
              {input("feature", "특징", { maxLength: 500 })}
            </div>
          </fieldset>
          {committed && (
            <p role="status">
              {committed.changed ? "저장했습니다." : "바뀐 내용이 없습니다."}{" "}
              {committed.project.id} · 최종 예산{" "}
              {committed.project.budgetAmount ?? "–"} ·{" "}
              {committed.warnings.join(" ")}
            </p>
          )}
          {refreshFailed && (
            <ErrorBanner
              message="저장은 완료됐지만 조회하지 못했습니다. 다시 조회해 주세요."
              onRetry={() => committed && void refresh(committed)}
            />
          )}
          <footer className="project-toolbar">
            <button
              type="button"
              data-testid="project-form-cancel"
              disabled={pending}
              onClick={close}
            >
              취소
            </button>
            <button
              type="submit"
              className="sicost-primary"
              data-testid="project-form-save"
              disabled={
                pending || loading || Boolean(lookupError) || Boolean(committed)
              }
            >
              {pending ? "저장 중…" : "저장"}
            </button>
          </footer>
        </form>
      </section>
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
        cancelLabel="계속 작성"
        confirmLabel="나가기"
      >
        <p>나가면 입력한 내용을 폐기합니다.</p>
      </Dialog>
    </FormPage>
  );
  return modal ? createPortal(
    <div className="sicost-scrim" style={visible ? undefined : {display: "none"}}>
      <div className="sicost-dialog sicost-dialog-wide" role="dialog" aria-modal="true" aria-label={p ? "프로젝트 수정" : "프로젝트 등록"}>
        <div className="sicost-dialog-body">{content}</div>
      </div>
    </div>, document.body,
  ) : content;
}
