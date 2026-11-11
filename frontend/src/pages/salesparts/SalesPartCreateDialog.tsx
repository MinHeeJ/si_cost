import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { Text } from "../../shared/Text";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { compareDecimal, validMonth } from "../../shared/format";
import { BusinessUserSelect } from "../businessusersops/BusinessUserSelect";
import * as api from "./salesPartsApi";

export function SalesPartCreateDialog({
  onClose,
  onSaved,
  onDirtyChange,
}: {
  onClose(): void;
  onSaved(result: api.SalesPartSaveResult): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [values, setValues] = useState<api.SalesPartCreate>({
    name: "",
    effectiveMonth: "",
    leaderUserId: "",
    profitRate: "",
    salesExpenseAmount: "",
  });
  const edited = useRef(new Set<string>()),
    generation = useRef(0),
    saving = useRef(false);
  const [defaults, setDefaults] = useState<api.SalesPartDefaults>();
  const [loading, setLoading] = useState(true),
    [pending, setPending] = useState(false),
    [dirty, setDirty] = useState(false);
  const [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({}),
    [leave, setLeave] = useState(false);
  async function loadDefaults(month?: string) {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const result = await api.getSalesPartDefaults(month);
      if (version !== generation.current) return;
      setDefaults(result);
      setValues((old) => ({
        ...old,
        effectiveMonth: month ?? result.effectiveMonth,
        profitRate: edited.current.has("profitRate")
          ? old.profitRate
          : (result.profitRate ?? ""),
        salesExpenseAmount: edited.current.has("salesExpenseAmount")
          ? old.salesExpenseAmount
          : (result.salesExpenseAmount ?? ""),
      }));
    } catch (reason) {
      if (version === generation.current) setError(reason);
    } finally {
      if (version === generation.current) setLoading(false);
    }
  }
  useEffect(() => {
    void loadDefaults();
    return () => {
      generation.current++;
    };
  }, []);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  function change(key: keyof api.SalesPartCreate, value: string) {
    edited.current.add(key);
    setDirty(true);
    setValues((old) => ({ ...old, [key]: value }));
    setFields((old) => ({ ...old, [key]: "" }));
    if (key === "effectiveMonth" && validMonth(value)) void loadDefaults(value);
  }
  async function save() {
    if (saving.current) return;
    const problems: Record<string, string> = {};
    if (!values.name.trim() || [...values.name.trim()].length > 30)
      problems.name = "파트명은 1~30자로 입력해 주세요.";
    if (!validMonth(values.effectiveMonth))
      problems.effectiveMonth = "적용 시작월을 입력해 주세요.";
    else if (defaults && values.effectiveMonth < defaults.minMonth)
      problems.effectiveMonth = `최소 적용월은 ${defaults.minMonth}입니다.`;
    if (!values.leaderUserId) problems.leaderUserId = "파트장을 선택해 주세요.";
    if (
      !/^\d+(\.\d{1,2})?$/.test(values.profitRate) ||
      compareDecimal(values.profitRate, "100") > 0
    )
      problems.profitRate = "배분율은 0~100, 소수 2자리까지 입력해 주세요.";
    const amount = values.salesExpenseAmount
      .replace(/[\s,]/g, "")
      .replace(/원$/, "");
    if (!/^\d+$/.test(amount) || compareDecimal(amount, "999999999999999") > 0)
      problems.salesExpenseAmount =
        "공통관리비는 0~999,999,999,999,999원 정수로 입력해 주세요.";
    setFields(problems);
    if (Object.keys(problems).length) return;
    saving.current = true;
    setPending(true);
    setError(undefined);
    try {
      const result = await api.createSalesPart({
        ...values,
        name: values.name.trim(),
        salesExpenseAmount: amount,
      });
      setDirty(false);
      onDirtyChange?.(false);
      await onSaved(result);
    } catch (reason) {
      setError(reason);
      if (reason instanceof ApiError) setFields(reason.fieldMessages);
    } finally {
      saving.current = false;
      setPending(false);
    }
  }
  return (
    <>
      <Dialog
        open
        title="영업 파트 추가"
        pending={pending}
        confirmLabel="등록"
        onConfirm={save}
        onCancel={() => (dirty ? setLeave(true) : onClose())}
      >
        <div className="sales-part-form" data-testid="sales-part-create-dialog">
          {error != null && (
            <ErrorBanner
              message={
                error instanceof Error ? error.message : "요청에 실패했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={() =>
                void loadDefaults(values.effectiveMonth || undefined)
              }
            />
          )}
          {loading && <p role="status">기본값을 불러오는 중입니다.</p>}
          {defaults?.defaultErrors.map((item) => (
            <p className="sicost-banner sicost-tone-warn" key={item.source}>
              {item.source}: {item.message} 수동 입력할 수 있습니다.
            </p>
          ))}
          <Text
            label="파트명"
            data-testid="sales-part-name-input"
            required
            value={values.name}
            onChange={(value) => change("name", value)}
            error={fields.name}
            disabled={pending}
          />
          <Text
            label="적용 시작월"
            data-testid="sales-part-month-input"
            type="month"
            required
            min={defaults?.minMonth}
            value={values.effectiveMonth}
            onChange={(value) => change("effectiveMonth", value)}
            error={fields.effectiveMonth}
            disabled={pending}
          />
          {defaults && <p>최소 적용월: {defaults.minMonth}</p>}
          <BusinessUserSelect
            purpose="PART_LEADER"
            value={values.leaderUserId}
            onChange={(value) => change("leaderUserId", value)}
            required
            disabled={pending}
            error={fields.leaderUserId}
          />
          <Text
            label="이익 배분율"
            data-testid="sales-part-rate-input"
            required
            inputMode="decimal"
            value={values.profitRate}
            onChange={(value) => change("profitRate", value)}
            error={fields.profitRate}
            disabled={pending}
          />
          <Text
            label="공통관리비 (자사 인력 1인·월, 원)"
            data-testid="sales-part-expense-input"
            required
            inputMode="numeric"
            value={values.salesExpenseAmount}
            onChange={(value) => change("salesExpenseAmount", value)}
            error={fields.salesExpenseAmount}
            disabled={pending}
          />
        </div>
      </Dialog>
      <Dialog
        open={leave}
        title="저장하지 않은 내용이 있습니다."
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onConfirm={onClose}
        onCancel={() => setLeave(false)}
      >
        입력한 내용은 저장되지 않습니다.
      </Dialog>
    </>
  );
}
