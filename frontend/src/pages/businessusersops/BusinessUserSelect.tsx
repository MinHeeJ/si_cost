import { useEffect, useId, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { CodeSelect } from "../../shared/CodeSelect";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import {
  businessUsersCommittedEvent,
  getBusinessUserOptions,
  type BusinessUserOption,
  type BusinessUserPurpose,
} from "./businessUserOptionsApi";
import "./businessUserSelect.css";

// SCR-UI-23 / UI-15: embedded qualification field, not a separate route.
export type BusinessUserSelectProps = {
  purpose: BusinessUserPurpose;
  value: string;
  onChange(userId: string): void;
  label?: string;
  selectedLabel?: string;
  required?: boolean;
  disabled?: boolean;
  error?: string;
  refreshKey?: number;
  onRegister?(): void;
};
export function BusinessUserSelect({
  purpose,
  value,
  onChange,
  label = purpose === "PM" ? "PM" : "파트장",
  selectedLabel,
  required,
  disabled = false,
  error: fieldError,
  refreshKey,
  onRegister,
}: BusinessUserSelectProps) {
  const navigate = useNavigate(),
    location = useLocation(),
    panelId = useId();
  const [open, setOpen] = useState(false),
    [version, setVersion] = useState(0);
  const [items, setItems] = useState<BusinessUserOption[]>([]);
  const [loading, setLoading] = useState(false),
    [error, setError] = useState<unknown>();
  useEffect(() => {
    const refresh = () => setVersion((old) => old + 1);
    window.addEventListener(businessUsersCommittedEvent, refresh);
    window.addEventListener("focus", refresh);
    return () => {
      window.removeEventListener(businessUsersCommittedEvent, refresh);
      window.removeEventListener("focus", refresh);
    };
  }, []);
  useEffect(() => {
    if (!open || disabled) return;
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setItems([]);
    void getBusinessUserOptions(purpose, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setItems(result.items);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [open, purpose, version, refreshKey, disabled]);
  const current = items.find((item) => item.userId === value);
  const display = current?.label ?? selectedLabel ?? value;
  function register() {
    setOpen(false);
    if (onRegister) onRegister();
    else
      navigate("/UI-23", {
        state: {
          businessUserRegistration: true,
          qualificationPurpose: purpose,
          returnTo: location.pathname,
        },
      });
  }
  return (
    <section
      className="business-user-select"
      data-testid={`${purpose.toLowerCase().replace("_", "-")}-user-field`}
    >
      <button
        data-testid={`${purpose.toLowerCase().replace("_", "-")}-user-open`}
        type="button"
        aria-expanded={open && !disabled}
        aria-controls={panelId}
        disabled={disabled}
        onClick={() => setOpen((previous) => !previous)}
      >
        {label}: {display || "선택해 주세요"}
      </button>
      {fieldError && (
        <p role="alert" className="sicost-field-error">
          {fieldError}
        </p>
      )}
      {open && !disabled && (
        <div id={panelId} className="business-user-select-panel">
          {loading ? (
            <p role="status">불러오는 중입니다.</p>
          ) : error != null ? (
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "사용자를 불러오지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={() => setVersion((old) => old + 1)}
            />
          ) : items.length === 0 ? (
            <div className="sicost-empty sicost-empty-state">
              <p>선택할 수 있는 {label} 사용자가 없습니다.</p>
              <button
                data-testid={`${purpose.toLowerCase().replace("_", "-")}-user-register`}
                type="button"
                onClick={register}
              >
                사용자 등록
              </button>
            </div>
          ) : (
            <CodeSelect
              label={label}
              value={value}
              required={required}
              options={[
                ...(!current && value
                  ? [{ code: value, name: display, disabled: true }]
                  : []),
                ...items.map((item) => ({
                  code: item.userId,
                  name: item.label,
                })),
              ]}
              onChange={(id) => {
                if (id === "" || items.some((item) => item.userId === id))
                  onChange(id);
              }}
            />
          )}
        </div>
      )}
    </section>
  );
}
