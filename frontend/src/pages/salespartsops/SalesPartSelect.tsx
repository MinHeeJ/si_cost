import { useEffect, useId, useState } from "react";
import { ErrorBanner } from "../../shared/Feedback";
import {
  getSalesPartOptions,
  salesPartsCommittedEvent,
  type PartOption,
} from "./partStandardsApi";

export function SalesPartSelect({
  value,
  onChange,
  disabled = false,
  includeHq = true,
}: {
  value: string;
  onChange(id: string): void;
  disabled?: boolean;
  includeHq?: boolean;
}) {
  const panel = useId();
  const [open, setOpen] = useState(false),
    [version, setVersion] = useState(0);
  const [items, setItems] = useState<PartOption[]>([]),
    [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>();
  useEffect(() => {
    const refresh = () => setVersion((v) => v + 1);
    window.addEventListener(salesPartsCommittedEvent, refresh);
    return () => window.removeEventListener(salesPartsCommittedEvent, refresh);
  }, []);
  useEffect(() => {
    if (!open || disabled) return;
    const abort = new AbortController();
    setLoading(true);
    setError(undefined);
    void getSalesPartOptions(abort.signal)
      .then((result) => {
        if (!abort.signal.aborted) setItems(result.items);
      })
      .catch((e) => {
        if (!abort.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!abort.signal.aborted) setLoading(false);
      });
    return () => abort.abort();
  }, [open, disabled, version]);
  return (
    <section data-testid="sales-part-select">
      <button
        type="button"
        data-testid="sales-part-options-toggle"
        disabled={disabled}
        aria-expanded={open}
        aria-controls={panel}
        onClick={() => setOpen((v) => !v)}
      >
        영업 파트:{" "}
        {items.find((row) => row.id === value)?.name ??
          (value || "선택해 주세요")}
      </button>
      {open && (
        <div id={panel} data-testid="sales-part-options-panel">
          {loading ? (
            <p role="status">불러오는 중입니다.</p>
          ) : error ? (
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "파트를 불러오지 못했습니다."
              }
              onRetry={() => setVersion((v) => v + 1)}
            />
          ) : items.filter((row) => includeHq || row.hqYn !== "Y").length ===
            0 ? (
            <p className="sicost-empty">선택할 수 있는 파트가 없습니다.</p>
          ) : (
            <label className="sicost-field">
              영업 파트 선택
              <select
                data-testid="sales-part-options-input"
                value={value}
                onChange={(e) => onChange(e.target.value)}
              >
                <option value="">선택해 주세요</option>
                {includeHq && (
                  <optgroup label="본사">
                    {items
                      .filter((row) => row.hqYn === "Y")
                      .map((row) => (
                        <option key={row.id} value={row.id}>
                          {row.name} (본사)
                        </option>
                      ))}
                  </optgroup>
                )}
                <optgroup label="영업 파트">
                  {items
                    .filter((row) => row.hqYn === "N")
                    .map((row) => (
                      <option key={row.id} value={row.id}>
                        {row.name}
                        {row.firstEffectiveMonth
                          ? ` · 최초 시작 ${row.firstEffectiveMonth}`
                          : ""}
                      </option>
                    ))}
                </optgroup>
              </select>
            </label>
          )}
        </div>
      )}
    </section>
  );
}
