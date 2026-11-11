import { useEffect, useRef, useState } from "react";
import { Text } from "../../shared/Text";
import { ErrorBanner } from "../../shared/Feedback";
import { BusinessUserSelect } from "../businessusersops/BusinessUserSelect";
import { getSalesPartDefaults, type PartDefaults } from "./partStandardsApi";
import "./partStandards.css";

export type SalesPartInitialValues = {
  effectiveMonth: string;
  leaderUserId: string;
  profitRate: string;
  salesExpenseAmount: string;
};
export function SalesPartDefaultsFields({
  onChange,
  disabled = false,
}: {
  onChange(value: SalesPartInitialValues): void;
  disabled?: boolean;
}) {
  const [defaults, setDefaults] = useState<PartDefaults>();
  const [value, setValue] = useState<SalesPartInitialValues>({
    effectiveMonth: "",
    leaderUserId: "",
    profitRate: "",
    salesExpenseAmount: "",
  });
  const [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true);
  const latest = useRef(0),
    callback = useRef(onChange);
  callback.current = onChange;
  const manual = useRef(new Set<string>());
  async function load(month?: string) {
    const generation = ++latest.current;
    setLoading(true);
    setError(undefined);
    try {
      const result = await getSalesPartDefaults(month);
      if (generation !== latest.current) return;
      setDefaults(result);
      setValue((previous) => {
        const next = {
          ...previous,
          effectiveMonth: result.effectiveMonth,
          profitRate: manual.current.has("profitRate")
            ? previous.profitRate
            : (result.profitRate ?? ""),
          salesExpenseAmount: manual.current.has("salesExpenseAmount")
            ? previous.salesExpenseAmount
            : (result.salesExpenseAmount ?? ""),
        };
        return next;
      });
    } catch (e) {
      if (generation === latest.current) setError(e);
    } finally {
      if (generation === latest.current) setLoading(false);
    }
  }
  useEffect(() => {
    void load();
    return () => {
      latest.current++;
    };
  }, []);
  useEffect(() => {
    callback.current(value);
  }, [value]);
  function change(field: keyof SalesPartInitialValues, text: string) {
    manual.current.add(field);
    setValue((previous) => ({ ...previous, [field]: text }));
    if (
      field === "effectiveMonth" &&
      /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(text)
    )
      void load(text);
  }
  return (
    <section
      data-testid="sales-part-default-fields"
      className="part-standard-editor"
    >
      {loading && <p role="status">기본값을 불러오는 중입니다.</p>}
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "기본값 조회에 실패했습니다."
          }
          onRetry={() => void load(value.effectiveMonth || undefined)}
        />
      )}
      <div className="part-standard-fields">
        <Text
          label="적용 시작월"
          type="month"
          value={value.effectiveMonth}
          min={defaults?.minMonth}
          required
          readOnly={disabled}
          onChange={(text) => change("effectiveMonth", text)}
        />
        <BusinessUserSelect
          purpose="PART_LEADER"
          value={value.leaderUserId}
          required
          disabled={disabled}
          onChange={(text) => change("leaderUserId", text)}
        />
        <Text
          label="이익 배분율 (%)"
          value={value.profitRate}
          required
          readOnly={disabled}
          onChange={(text) => change("profitRate", text)}
        />
        <Text
          label="공통관리비 (자사 인력 1인·월, 원)"
          value={value.salesExpenseAmount}
          required
          readOnly={disabled}
          onChange={(text) => change("salesExpenseAmount", text)}
        />
      </div>
      {defaults && <p>최소 적용월 {defaults.minMonth}</p>}
      {defaults?.defaultErrors.map((row) => (
        <p
          key={row.field}
          role="alert"
          className="sicost-banner sicost-tone-warn"
        >
          {row.source}: {row.message}
        </p>
      ))}
    </section>
  );
}
