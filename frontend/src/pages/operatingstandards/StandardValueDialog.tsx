import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { Text } from "../../shared/Text";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  getOperatingStandardHistory,
  type OperatingStandard,
  type StandardHistory,
  type StandardsSave,
} from "./operatingStandardsApi";

// SCR-UI-27-M7: readonly metadata; one month; changed fields only; explicit month/before/after confirmation; preserve failed input.
export function StandardValueDialog({
  rows,
  minimumMonth,
  defaultMonth,
  onClose,
  onSave,
  onDirtyChange,
}: {
  rows: OperatingStandard[];
  minimumMonth: string;
  defaultMonth: string;
  onClose(): void;
  onSave(body: StandardsSave): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [month, setMonth] = useState(defaultMonth),
    [values, setValues] = useState<Record<string, string>>({});
  const [touched, setTouched] = useState<Set<string>>(new Set());
  const [histories, setHistories] = useState<Record<string, StandardHistory>>(
      {},
    ),
    [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true),
    [pending, setPending] = useState(false),
    [confirm, setConfirm] = useState(false);
  const [fields, setFields] = useState<Record<string, string>>({});
  const submitting = useRef(false);
  const at = (key: string, selectedMonth: string) =>
    histories[key]?.items.find((row) => row.effectiveMonth <= selectedMonth)
      ?.value ?? null;
  async function load() {
    setLoading(true);
    setError(undefined);
    try {
      const pairs = await Promise.all(
        rows.map(
          async (row) =>
            [row.key, await getOperatingStandardHistory(row.key)] as const,
        ),
      );
      const result = Object.fromEntries(pairs);
      setHistories(result);
      setValues(
        Object.fromEntries(
          rows.map((row) => [
            row.key,
            result[row.key].items.find(
              (version) => version.effectiveMonth <= defaultMonth,
            )?.value ?? "",
          ]),
        ),
      );
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
  }, []);
  useEffect(() => {
    onDirtyChange?.(touched.size > 0);
  }, [touched]);
  const changes = rows
    .filter(
      (row) => touched.has(row.key) && values[row.key] !== at(row.key, month),
    )
    .map((row) => ({
      key: row.key,
      value: values[row.key],
      expectedRevision: histories[row.key]?.keyRevision ?? row.keyRevision,
    }));
  async function submit() {
    if (
      loading ||
      submitting.current ||
      rows.some((row) => !histories[row.key])
    )
      return;
    if (
      !/^(?!0000)[0-9]{4}-(0[1-9]|1[0-2])$/.test(month) ||
      month < minimumMonth
    ) {
      setFields({
        effectiveMonth: `적용 시작월은 ${minimumMonth} 이후여야 합니다.`,
      });
      return;
    }
    if (changes.some((change) => !change.value.trim())) {
      setFields(
        Object.fromEntries(
          changes
            .filter((c) => !c.value.trim())
            .map((c) => [c.key, "값은 필수입니다."]),
        ),
      );
      return;
    }
    if (!confirm && changes.length) {
      setConfirm(true);
      return;
    }
    submitting.current = true;
    setPending(true);
    setError(undefined);
    setFields({});
    try {
      await onSave({ effectiveMonth: month, changes });
      onDirtyChange?.(false);
      onClose();
    } catch (e) {
      setError(e);
      setConfirm(false);
      if (e instanceof ApiError) {
        const errors = e.fieldMessages,
          next: Record<string, string> = { ...errors };
        changes.forEach((change, i) => {
          next[change.key] =
            errors[`changes[${i}].value`] ?? errors[`changes.${i}.value`] ?? "";
        });
        setFields(next);
      }
    } finally {
      submitting.current = false;
      setPending(false);
    }
  }
  return (
    <Dialog
      open
      wide
      title={confirm ? "운영 기준 값 변경 확인" : "운영 기준 값 변경"}
      pending={pending}
      confirmLabel={confirm ? "확인 후 저장" : "변경 확인"}
      onCancel={onClose}
      onConfirm={submit}
    >
      <div data-screen-id="SCR-UI-27-M7" className="operatingstandards-editor">
        <Text
          label="적용 시작월"
          type="month"
          required
          min={minimumMonth}
          value={month}
          readOnly={confirm || pending}
          error={fields.effectiveMonth}
          onChange={(value) => {
            setMonth(value);
            setValues((previous) =>
              Object.fromEntries(
                rows.map((row) => [
                  row.key,
                  touched.has(row.key)
                    ? previous[row.key]
                    : (at(row.key, value) ?? ""),
                ]),
              ),
            );
            setConfirm(false);
          }}
        />
        <p>
          최소 적용월 {minimumMonth} · 같은 월이 있으면 덮어씁니다. 다른
          적용월은 보존합니다.
        </p>
        {loading ? (
          <p role="status">불러오는 중입니다.</p>
        ) : (
          <div className="operatingstandards-group">
            {rows.map((row) => (
              <div key={row.key}>
                <p>
                  <strong>{row.name}</strong>
                </p>
                <p className="operatingstandards-meta">
                  {row.key} · {row.unitNm} · {row.description}
                </p>
                <Text
                  label={`${row.key} 값`}
                  required
                  value={values[row.key] ?? ""}
                  readOnly={confirm || pending}
                  error={fields[row.key]}
                  onChange={(value) => {
                    setTouched((previous) => new Set([...previous, row.key]));
                    setValues((previous) => ({
                      ...previous,
                      [row.key]: value,
                    }));
                    setConfirm(false);
                  }}
                />
              </div>
            ))}
          </div>
        )}
        {confirm && (
          <div className="sicost-banner sicost-tone-warn">
            <div>
              <strong>덮어쓸 월 / 변경 적용월: {month}</strong>
              {changes.map((change) => (
                <p key={change.key}>
                  {change.key}: {at(change.key, month) ?? "기준값 없음"} →{" "}
                  {change.value}
                  {histories[change.key]?.items.some(
                    (v) => v.effectiveMonth === month,
                  )
                    ? " (기존 월 덮어쓰기)"
                    : " (새 적용월)"}
                </p>
              ))}
            </div>
          </div>
        )}
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error ? error.message : "저장하지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={
              loading || !Object.keys(histories).length
                ? () => void load()
                : undefined
            }
          />
        )}
      </div>
    </Dialog>
  );
}
