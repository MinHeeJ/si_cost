import { useEffect, useId, useState } from "react";
import { ErrorBanner } from "../../shared/Feedback";
import * as api from "./clientActionsApi";
import "./clientActions.css";

// SCR-UI-15: reusable customer/vendor selector. Existing inactive values are display-only, never new choices.
export function ClientSelect({
  purpose,
  value,
  onChange,
  selectedLabel,
  disabled = false,
  onRegister,
}: {
  purpose: api.ClientPurpose;
  value: string;
  onChange(id: string): void;
  selectedLabel?: string;
  disabled?: boolean;
  onRegister?(): void;
}) {
  const [open, setOpen] = useState(false),
    [q, setQ] = useState(""),
    [search, setSearch] = useState("");
  const [version, setVersion] = useState(0),
    [items, setItems] = useState<api.ClientOption[]>([]);
  const [loading, setLoading] = useState(false),
    [error, setError] = useState<unknown>();
  const id = useId(),
    label = purpose === "CUSTOMER" ? "고객사" : "협력사";
  useEffect(() => {
    const refresh = () => setVersion((v) => v + 1);
    window.addEventListener(api.clientsCommittedEvent, refresh);
    return () => window.removeEventListener(api.clientsCommittedEvent, refresh);
  }, []);
  useEffect(() => {
    if (!open || disabled) return;
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setItems([]);
    void api
      .getClientOptions(purpose, search, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setItems(data.items);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [purpose, search, version, open, disabled]);
  const selected = items.find((item) => item.id === value);
  return (
    <section
      className="client-select"
      data-testid={`${purpose.toLowerCase()}-client-field`}
    >
      <button
        type="button"
        disabled={disabled}
        aria-controls={id}
        aria-expanded={open && !disabled}
        data-testid={`${purpose.toLowerCase()}-client-open`}
        onClick={() => setOpen((previous) => !previous)}
      >
        {label}: {(selected?.name ?? selectedLabel ?? value) || "선택해 주세요"}
      </button>
      {open && !disabled && (
        <div
          id={id}
          className="client-select-panel"
          data-testid={`${purpose.toLowerCase()}-client-panel`}
        >
          <form
            data-testid={`${purpose.toLowerCase()}-client-search-form`}
            onSubmit={(event) => {
              event.preventDefault();
              setSearch(q.trim());
              setVersion((v) => v + 1);
            }}
          >
            <label>
              상호 검색
              <input
                data-testid={`${purpose.toLowerCase()}-client-search`}
                value={q}
                maxLength={100}
                onChange={(event) => setQ(event.target.value)}
              />
            </label>
            <button
              type="submit"
              data-testid={`${purpose.toLowerCase()}-client-query`}
            >
              조회
            </button>
          </form>
          {loading ? (
            <p role="status">불러오는 중입니다.</p>
          ) : error ? (
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "거래처를 불러오지 못했습니다."
              }
              onRetry={() => setVersion((v) => v + 1)}
            />
          ) : items.length === 0 ? (
            <div className="sicost-empty sicost-empty-state">
              <p>선택할 수 있는 {label}가 없습니다.</p>
              {onRegister && (
                <button
                  type="button"
                  onClick={onRegister}
                  data-testid={`${purpose.toLowerCase()}-client-register`}
                >
                  거래처 등록
                </button>
              )}
            </div>
          ) : (
            <label>
              {label}
              <select
                data-testid={`${purpose.toLowerCase()}-client-select`}
                value={value}
                onChange={(event) => {
                  if (
                    !event.target.value ||
                    items.some((item) => item.id === event.target.value)
                  )
                    onChange(event.target.value);
                }}
              >
                <option value="">선택해 주세요</option>
                {!selected && value && (
                  <option value={value} disabled>
                    {selectedLabel ?? value}
                  </option>
                )}
                {items.map((item) => (
                  <option
                    data-testid={`${purpose.toLowerCase()}-client-option-${item.id.toLowerCase().replace(/_/g, "-")}`}
                    key={item.id}
                    value={item.id}
                  >
                    {item.name} ({item.id})
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>
      )}
    </section>
  );
}
