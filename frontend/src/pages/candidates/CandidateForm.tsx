import { useEffect, useRef, useState } from "react";
import { Text } from "../../shared/Text";
import { Decimal } from "../../shared/Decimal";
import { CodeSelect } from "../../shared/CodeSelect";
import { AddressSelector } from "../../shared/AddressSelector";
import { SkillsInput } from "../../shared/SkillsInput";
import { Dialog, ConfirmDialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { ClientSelect } from "../clientsops/ClientSelect";
import * as api from "./peopleClient";

export function CandidateForm({
  candidate,
  open,
  onClose,
  onSaved,
  onDirtyChange,
}: {
  candidate?: api.CandidateDetail;
  open: boolean;
  onClose(): void;
  onSaved(id: string, message: string, created: boolean): void;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [form, setForm] = useState<api.CandidateFields>(() =>
    candidate
      ? {
          name: candidate.name,
          employeeType: candidate.employeeType,
          companyId: candidate.companyId,
          phone: candidate.phone,
          email: candidate.email,
          consent: candidate.consent,
          jobCode: candidate.jobCode,
          subJobCode: candidate.subJobCode,
          gradeCode: candidate.gradeCode,
          homeAreaCode: candidate.homeAreaCode,
          desiredRate: candidate.desiredRate,
          availableDate: candidate.availableDate,
          sourceCode: candidate.sourceCode,
          memo: candidate.memo,
          skills: candidate.skills,
        }
      : {
          name: "",
          employeeType: "FL",
          companyId: null,
          phone: null,
          email: null,
          consent: false,
          jobCode: "",
          subJobCode: null,
          gradeCode: "MD",
          homeAreaCode: null,
          desiredRate: "",
          availableDate: "",
          sourceCode: "SITE",
          memo: null,
          skills: [],
        },
  );
  const [codes, setCodes] = useState<Record<string, api.CandidateCode[]>>({});
  const [error, setError] = useState<unknown>(),
    [lookupError, setLookupError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({}),
    [pending, setPending] = useState(false),
    [leaving, setLeaving] = useState(false),
    [retry, setRetry] = useState(0);
  const changed = useRef(false),
    busy = useRef(false);
  useEffect(() => {
    let active = true;
    setLookupError(undefined);
    void Promise.all([
      Promise.all(
        ["EMP_TYPE", "JOB", "GRADE", "CAND_SRC"].map(
          async (group) =>
            [group, (await api.getCandidateCodes(group)).items] as const,
        ),
      ),
      candidate ? Promise.resolve(undefined) : api.getCandidateDate(),
    ])
      .then(([entries, date]) => {
        if (!active) return;
        setCodes(Object.fromEntries(entries));
        if (date) {
          const value = new Date(`${date.currentDate}T00:00:00Z`);
          value.setUTCDate(value.getUTCDate() + 30);
          setForm((f) =>
            f.availableDate
              ? f
              : { ...f, availableDate: value.toISOString().slice(0, 10) },
          );
        }
      })
      .catch((e) => {
        if (active) setLookupError(e);
      });
    return () => {
      active = false;
    };
  }, [candidate, retry]);
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);
  function change<K extends keyof api.CandidateFields>(
    key: K,
    value: api.CandidateFields[K],
  ) {
    setForm((f) => ({ ...f, [key]: value }));
    changed.current = true;
    onDirtyChange?.(true);
    setFields((f) => ({ ...f, [key]: "" }));
  }
  function options(key: string, current: string | null) {
    const list = codes[key] ?? [];
    return current && !list.some((c) => c.code === current)
      ? [...list, { code: current, name: `${current} (기존값)` }]
      : list;
  }
  function validate() {
    const messages: Record<string, string> = {};
    if (!form.name.trim() || [...form.name.trim()].length > 50)
      messages.name = "이름은 1~50자로 입력해 주세요.";
    if (form.employeeType === "OS" && !form.companyId)
      messages.companyId = "사용 중인 협력사를 선택해 주세요.";
    if (form.phone?.trim() && !/^[0-9][0-9-]{7,19}$/.test(form.phone.trim()))
      messages.phone = "전화번호는 숫자와 하이픈으로 8~20자 입력해 주세요.";
    if (
      form.email?.trim() &&
      (form.email.trim().length > 100 ||
        !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email.trim()))
    )
      messages.email = "이메일 형식을 확인해 주세요.";
    if (!form.jobCode) messages.jobCode = "주 직무를 선택해 주세요.";
    if (!form.gradeCode) messages.gradeCode = "등급을 선택해 주세요.";
    if (!form.sourceCode) messages.sourceCode = "출처를 선택해 주세요.";
    const rate = form.desiredRate.replace(/\s|,/g, "").replace(/원$/, "");
    if (
      !/^\d+$/.test(rate) ||
      /^0+$/.test(rate) ||
      rate.replace(/^0+/, "").length > 15
    )
      messages.desiredRate =
        "희망 단가는 1~999,999,999,999,999원 정수로 입력해 주세요.";
    const date = new Date(`${form.availableDate}T00:00:00Z`);
    if (
      !/^(?!0000)\d{4}-\d{2}-\d{2}$/.test(form.availableDate) ||
      Number.isNaN(date.valueOf()) ||
      date.toISOString().slice(0, 10) !== form.availableDate
    )
      messages.availableDate = "실제 가능일을 YYYY-MM-DD로 입력해 주세요.";
    if (
      !form.skills.length ||
      form.skills.length > 999 ||
      form.skills.some((s) => [...s].length > 50)
    )
      messages.skills = "보유 스킬은 1~999개, 각 50자 이내로 입력해 주세요.";
    if ((form.phone?.trim() || form.email?.trim()) && !form.consent)
      messages.consent = "연락처가 있으면 개인정보 동의를 확인해 주세요.";
    if (form.memo && [...form.memo.trim()].length > 500)
      messages.memo = "메모는 500자 이내로 입력해 주세요.";
    setFields(messages);
    return Object.keys(messages).length === 0;
  }
  async function save() {
    if (busy.current || !validate()) return;
    busy.current = true;
    setPending(true);
    setError(undefined);
    try {
      if (candidate) {
        const result = await api.putCandidate(candidate.id, {
          ...form,
          expectedRevision: candidate.revision,
        });
        changed.current = false;
        onDirtyChange?.(false);
        onSaved(
          candidate.id,
          result.changed
            ? "후보 정보를 저장했습니다."
            : "바뀐 내용이 없습니다.",
          false,
        );
      } else {
        const result = await api.createCandidate(form);
        changed.current = false;
        onDirtyChange?.(false);
        onSaved(
          result.id,
          [
            "후보를 등록했습니다.",
            ...(result.warnings ?? []).map((w) => w.message),
          ].join(" "),
          true,
        );
      }
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) setFields(e.fieldMessages);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  const text = (
    key: "name" | "phone" | "email" | "availableDate",
    label: string,
  ) => (
    <Text
      label={label}
      value={form[key] ?? ""}
      data-testid={`candidate-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
      disabled={pending}
      onChange={(value) => change(key, value)}
      error={fields[key]}
    />
  );
  return (
    <>
      <Dialog
        open={open}
        wide
        title={candidate ? "외부 인력 후보 수정" : "외부 인력 후보 등록"}
        pending={pending}
        onConfirm={save}
        onCancel={onClose}
        confirmLabel="저장"
        confirmDisabled={Boolean(lookupError)}
      >
        <div
          className="candidate-form"
          data-testid="candidate-form"
          data-screen-id="SCR-UI-33"
        >
          {lookupError != null && (
            <ErrorBanner
              message="선택 항목과 서버 날짜를 불러오지 못했습니다."
              onRetry={() => setRetry((v) => v + 1)}
            />
          )}
          {text("name", "이름 *")}
          <CodeSelect
            label="출처 *"
            value={form.sourceCode}
            options={options("CAND_SRC", form.sourceCode)}
            onChange={(v) => change("sourceCode", v)}
            error={fields.sourceCode}
            disabled={pending}
          />
          <CodeSelect
            label="구분 *"
            value={form.employeeType}
            options={(codes.EMP_TYPE ?? []).filter((c) =>
              ["FL", "OS"].includes(c.code),
            )}
            onChange={(v) => change("employeeType", v)}
            disabled={pending}
          />
          <div className="sicost-field">
            <ClientSelect
              purpose="VENDOR"
              value={form.companyId ?? ""}
              selectedLabel={candidate?.companyName ?? undefined}
              disabled={pending || form.employeeType !== "OS"}
              onChange={(v) => change("companyId", v || null)}
            />
            {fields.companyId && <p role="alert">{fields.companyId}</p>}
            {form.employeeType === "FL" && form.companyId && (
              <p className="sicost-tone-warn">
                프리랜서는 입력한 업체를 저장하지 않습니다.
              </p>
            )}
          </div>
          {text("phone", "전화번호")}
          {text("email", "이메일")}
          <CodeSelect
            label="주 직무 *"
            value={form.jobCode}
            options={options("JOB", form.jobCode)}
            onChange={(v) => change("jobCode", v)}
            error={fields.jobCode}
            disabled={pending}
          />
          <CodeSelect
            label="보조 직무"
            value={form.subJobCode ?? ""}
            options={options("JOB", form.subJobCode)}
            onChange={(v) => change("subJobCode", v || null)}
            disabled={pending}
          />
          <SkillsInput
            value={form.skills}
            onChange={(v) => change("skills", v)}
            error={fields.skills}
            disabled={pending}
          />
          <CodeSelect
            label="등급 *"
            value={form.gradeCode}
            options={options("GRADE", form.gradeCode)}
            onChange={(v) => change("gradeCode", v)}
            error={fields.gradeCode}
            disabled={pending}
          />
          <Decimal
            label="희망 단가 (원/월) *"
            value={form.desiredRate}
            data-testid="candidate-rate"
            onChange={(v) => change("desiredRate", v)}
            error={fields.desiredRate}
            disabled={pending}
          />
          {text("availableDate", "투입 가능일 *")}
          <div className="candidate-form-wide">
            <AddressSelector
              value={form.homeAreaCode}
              onChange={(v) => change("homeAreaCode", v)}
              maxLevel="SGG"
              disabled={pending}
              error={fields.homeAreaCode}
            />
          </div>
          <div className="candidate-form-wide">
            <label>
              <input
                data-testid="candidate-consent"
                type="checkbox"
                checked={form.consent}
                disabled={pending}
                aria-invalid={Boolean(fields.consent)}
                onChange={(e) => change("consent", e.target.checked)}
              />{" "}
              후보에게 개인정보(전화번호·이메일) 수집·이용 동의를 받았습니다
            </label>
            {fields.consent && (
              <p role="alert" className="sicost-field-error">
                {fields.consent}
              </p>
            )}
          </div>
          <label className="sicost-field candidate-form-wide">
            자체 메모
            <textarea
              data-testid="candidate-memo"
              value={form.memo ?? ""}
              disabled={pending}
              aria-invalid={Boolean(fields.memo)}
              onChange={(e) => change("memo", e.target.value)}
            />
            {fields.memo && <span role="alert">{fields.memo}</span>}
          </label>
          {error != null && (
            <div className="candidate-form-wide">
              <ErrorBanner
                message={
                  error instanceof Error
                    ? error.message
                    : "저장하지 못했습니다. 입력은 유지됩니다."
                }
                requestId={
                  error instanceof ApiError ? error.body.requestId : undefined
                }
              />
            </div>
          )}
          {changed.current && (
            <button
              type="button"
              data-testid="candidate-discard"
              disabled={pending}
              onClick={() => setLeaving(true)}
            >
              입력을 버리고 나가기
            </button>
          )}
        </div>
      </Dialog>
      <ConfirmDialog
        open={open && leaving}
        title="입력 내용 버리기"
        confirmLabel="버리고 나가기"
        onCancel={() => setLeaving(false)}
        onConfirm={() => {
          setLeaving(false);
          changed.current = false;
          onDirtyChange?.(false);
          onSaved("", "", false);
        }}
      >
        <p>작성한 후보 정보를 버리시겠습니까?</p>
      </ConfirmDialog>
    </>
  );
}
