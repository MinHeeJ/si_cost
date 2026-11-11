import { useEffect, useRef, useState } from "react";
import { Text } from "../../shared/Text";
import { Decimal } from "../../shared/Decimal";
import { CodeSelect } from "../../shared/CodeSelect";
import { FormPage } from "../../shared/FormPage";
import { SkillsInput } from "../../shared/SkillsInput";
import { AddressSelector } from "../../shared/AddressSelector";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  getClientOptions,
  type ClientOption,
} from "../clientsops/clientActionsApi";
import {
  getSalesPartOptions,
  type PartOption,
} from "../salespartsops/partStandardsApi";
import * as api from "./peopleClient";

export const developerBanner =
  "보유 스킬·거주지는 이 화면에서 입력합니다. 동까지만 받으며 번지·연락처 입력은 없습니다.";
export const priceHelp =
  "단가·급여 변경은 저장월부터 적용되며 월별 급여 업로드와 별개입니다.";
type Props = {
  developer?: api.DeveloperDetail;
  onClose(): void;
  onSaved(id: string, message: string): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
};
export function DeveloperForm({
  developer,
  onClose,
  onSaved,
  onDirtyChange,
}: Props) {
  const [form, setForm] = useState(() => ({
    name: developer?.name ?? "",
    managementNo: developer?.managementNo ?? "",
    employeeType: developer?.employeeType ?? "FT",
    companyId: developer ? (developer.companyId ?? "__own__") : "",
    gradeCode: developer?.gradeCode ?? "",
    jobCode: developer?.jobCode ?? "",
    subJobCode: developer?.subJobCode ?? "",
    positionCode: developer?.positionCode ?? "MEMBER",
    directClass: developer?.directClass ?? "DRCT",
    partId: developer?.partId ?? "",
    hireDate: developer?.hireDate ?? "",
    monthlyRate: developer?.editPrice?.monthlyRate ?? "",
    monthlyPay: developer?.editPrice?.monthlyPay ?? "",
    homeAreaCode: developer?.homeAreaCode ?? null,
    skills: developer?.skills ?? [],
  }));
  const initial = useRef(form),
    container = useRef<HTMLFormElement>(null),
    busy = useRef(false);
  const dirty = JSON.stringify(form) !== JSON.stringify(initial.current);
  const [codes, setCodes] = useState<
    Record<string, { code: string; name: string }[]>
  >({});
  const [companies, setCompanies] = useState<ClientOption[]>([]),
    [parts, setParts] = useState<PartOption[]>([]);
  const [loading, setLoading] = useState(true),
    [lookupError, setLookupError] = useState<unknown>();
  const [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false),
    [leaving, setLeaving] = useState(false);
  async function load() {
    setLoading(true);
    setLookupError(undefined);
    try {
      const groups = ["EMP_TYPE", "GRADE", "JOB", "JBPS", "DRCT_CLS"];
      const [choices, vendors, teams] = await Promise.all([
        Promise.all(groups.map(api.getPeopleCodes)),
        getClientOptions("VENDOR"),
        getSalesPartOptions(),
      ]);
      setCodes(Object.fromEntries(groups.map((g, i) => [g, choices[i].items])));
      setCompanies(vendors.items);
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
    const prevent = (e: BeforeUnloadEvent) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, [dirty]);
  useEffect(() => {
    if (Object.keys(fields).length)
      container.current
        ?.querySelector<HTMLElement>('[aria-invalid="true"]')
        ?.focus();
  }, [fields]);
  function change<K extends keyof typeof form>(
    key: K,
    value: (typeof form)[K],
  ) {
    setForm((f) => ({ ...f, [key]: value }));
    setFields((f) => ({ ...f, [key]: "" }));
  }
  const close = () => {
    if (!pending) {
      if (dirty) setLeaving(true);
      else onClose();
    }
  };
  async function save() {
    if (busy.current) return;
    const validation: Record<string, string> = {};
    if (!form.name.trim() || [...form.name.trim()].length > 50)
      validation.name = "이름을 1~50자로 입력해 주세요.";
    if (
      !form.companyId ||
      (form.employeeType === "OS" && form.companyId === "__own__")
    )
      validation.companyId = "소속 협력사를 선택해 주세요.";
    if (!form.jobCode) validation.jobCode = "주 직무를 선택해 주세요.";
    if (!form.partId) validation.partId = "영업 파트를 선택해 주세요.";
    if (Object.keys(validation).length) {
      setFields(validation);
      return;
    }
    busy.current = true;
    setPending(true);
    setError(undefined);
    setFields({});
    try {
      const payload: api.DeveloperFields = {
        name: form.name,
        employeeType: form.employeeType,
        companyId: form.companyId === "__own__" ? null : form.companyId,
        jobCode: form.jobCode,
        subJobCode: form.subJobCode || null,
        gradeCode: form.gradeCode || null,
        positionCode: form.positionCode || null,
        directClass: form.directClass,
        partId: form.partId,
        hireDate: form.hireDate || null,
        homeAreaCode: form.homeAreaCode,
        skills: form.skills,
        ...(!developer || form.monthlyRate !== initial.current.monthlyRate
          ? { monthlyRate: form.monthlyRate || null }
          : {}),
        ...(!developer || form.monthlyPay !== initial.current.monthlyPay
          ? { monthlyPay: form.monthlyPay || null }
          : {}),
      };
      let id: string, message: string;
      if (developer) {
        const result = await api.putDeveloper(developer.id, {
          ...payload,
          expectedRevision: developer.revision,
        });
        id = developer.id;
        message = result.changed ? "저장했습니다." : "바뀐 내용이 없습니다.";
      } else {
        const result = await api.createDeveloper({
          ...payload,
          managementNo: form.managementNo,
        });
        id = result.id;
        message = `등록했습니다. 관리번호 ${result.managementNo} ${result.warnings.map((w) => w.message).join(" ")}`;
      }
      onDirtyChange?.(false);
      await onSaved(id, message);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) setFields(e.fieldMessages);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  function text(
    key: "name" | "managementNo" | "hireDate",
    label: string,
    maxLength: number,
    required = false,
  ) {
    return (
      <Text
        label={label}
        key={key}
        data-testid={`developer-${key.toLowerCase()}-input`}
        value={form[key]}
        onChange={(v) => change(key, v)}
        error={fields[key]}
        maxLength={maxLength}
        required={required}
        readOnly={key === "managementNo" && Boolean(developer)}
        disabled={pending}
        type={key === "hireDate" ? "date" : "text"}
      />
    );
  }
  function select(
    key:
      | "employeeType"
      | "gradeCode"
      | "jobCode"
      | "subJobCode"
      | "positionCode"
      | "directClass"
      | "companyId"
      | "partId",
    label: string,
    options: { code: string; name: string }[],
    required = false,
  ) {
    const existing = form[key];
    const all =
      existing && !options.some((o) => o.code === existing)
        ? [{ code: existing, name: `${existing} (기존값)` }, ...options]
        : options;
    return (
      <CodeSelect
        key={key}
        label={label}
        value={form[key]}
        options={all}
        onChange={(v) => change(key, v)}
        required={required}
        disabled={pending || loading}
        error={fields[key]}
      />
    );
  }
  return (
    <FormPage
      title={developer ? "개발자 수정" : "개발자 등록"}
      actions={
        <button
          type="button"
          data-testid="developer-form-back"
          onClick={close}
          disabled={pending}
        >
          목록
        </button>
      }
    >
      <p className="sicost-banner sicost-tone-info">{developerBanner}</p>
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
      <form
        ref={container}
        data-testid="developer-edit-form"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <fieldset disabled={pending} className="developer-form-fields">
          <legend>기본 정보</legend>
          {text("name", "이름", 50, true)}
          {text("managementNo", "관리번호", 20)}
          {select("employeeType", "직원구분", codes.EMP_TYPE ?? [], true)}
          {select(
            "companyId",
            "소속",
            [
              { code: "__own__", name: "자사" },
              ...companies.map((c) => ({ code: c.id, name: c.name })),
            ],
            true,
          )}
          {select("gradeCode", "등급", codes.GRADE ?? [])}
          {select("jobCode", "주 직무", codes.JOB ?? [], true)}
          {select("subJobCode", "보조 직무", codes.JOB ?? [])}
          <SkillsInput
            value={form.skills}
            onChange={(v) => change("skills", v)}
            disabled={pending}
            error={fields.skills}
          />
          <AddressSelector
            value={form.homeAreaCode}
            onChange={(v) => change("homeAreaCode", v)}
            maxLevel="EMD"
            disabled={pending}
            error={fields.homeAreaCode}
          />
          {select(
            "partId",
            "영업 파트",
            parts.map((p) => ({
              code: p.id,
              name:
                p.name +
                (p.firstEffectiveMonth
                  ? ` · 시작 ${p.firstEffectiveMonth}`
                  : ""),
            })),
            true,
          )}
          {text("hireDate", "입사일", 10)}
          {select("positionCode", "직위", codes.JBPS ?? [])}
          {select("directClass", "직접/간접", codes.DRCT_CLS ?? [], true)}
        </fieldset>
        <fieldset disabled={pending} className="developer-form-fields">
          <legend>금액</legend>
          <Decimal
            label="월 단가 (원/월)"
            data-testid="developer-rate-input"
            value={form.monthlyRate}
            onChange={(v) => change("monthlyRate", v)}
            error={fields.monthlyRate}
          />
          <Decimal
            label="월 급여·지급액 (원/월)"
            data-testid="developer-pay-input"
            value={form.monthlyPay}
            onChange={(v) => change("monthlyPay", v)}
            error={fields.monthlyPay}
          />
          {developer?.editPrice && (
            <p>
              편집 가격 시작월: {developer.editPrice.effectiveMonth} (현재 또는
              가장 가까운 예정값)
            </p>
          )}
          <p>{priceHelp}</p>
        </fieldset>
        <footer className="developer-toolbar">
          <button
            type="button"
            data-testid="developer-form-cancel"
            onClick={close}
            disabled={pending}
          >
            취소
          </button>
          <button
            className="sicost-primary"
            type="submit"
            data-testid="developer-form-save"
            disabled={pending || loading || Boolean(lookupError)}
          >
            {pending ? "저장 중…" : "저장"}
          </button>
        </footer>
      </form>
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
        cancelLabel="계속 작성"
        confirmLabel="나가기"
      >
        <p>
          계속 작성하면 입력한 내용이 유지됩니다. 나가기를 선택하면 폐기합니다.
        </p>
      </Dialog>
    </FormPage>
  );
}
