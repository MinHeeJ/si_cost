import { useEffect, useId, useRef, useState } from "react";
import { ApiError } from "./api";
import { ErrorBanner, StatusBadge } from "./Feedback";
import {
  getAreaChildren,
  getAreaPath,
  getAreaSidos,
  type PeopleArea,
} from "./areaClient";
import "./address.css";

export type AddressSelectorProps = {
  value: string | null;
  onChange(homeAreaCode: string | null): void;
  maxLevel: "SGG" | "EMD";
  disabled?: boolean;
  readOnly?: boolean;
  error?: string;
  testId?: string;
};

// SCR-UI-02 / SCR-UI-33: shared form region, not an address management screen.
export function AddressSelector({
  value,
  onChange,
  maxLevel,
  disabled = false,
  readOnly = false,
  error,
  testId,
}: AddressSelectorProps) {
  const id = useId();
  const prefix = testId ?? `address-${id.replace(/[^a-z0-9-]/g, "")}`;
  const first = useRef<HTMLSelectElement>(null);
  const [path, setPath] = useState<PeopleArea[]>([]);
  const [sidos, setSidos] = useState<PeopleArea[]>([]);
  const [sggs, setSggs] = useState<PeopleArea[]>([]);
  const [emds, setEmds] = useState<PeopleArea[]>([]);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<unknown>(null);
  const [retry, setRetry] = useState(0);
  const [directEmd, setDirectEmd] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    async function load() {
      setLoading(true);
      setFailure(null);
      try {
        const [roots, restored] = await Promise.all([
          getAreaSidos(controller.signal),
          value
            ? getAreaPath(value, controller.signal)
            : Promise.resolve({ items: [], homeAddress: null }),
        ]);
        const sido = restored.items.find((a) => a.level === "SIDO");
        const sgg = restored.items.find((a) => a.level === "SGG");
        const districts = sido
          ? await getAreaChildren(sido.code, "SGG", controller.signal)
          : { items: [] };
        const direct = Boolean(sido && !sgg && districts.items.length === 0);
        const parent = sgg?.code ?? (direct ? sido?.code : undefined);
        const towns =
          maxLevel === "EMD" && parent
            ? await getAreaChildren(parent, "EMD", controller.signal)
            : { items: [] };
        if (!active) return;
        setSidos(roots.items);
        setPath(restored.items);
        setSggs(districts.items);
        setEmds(towns.items);
        setDirectEmd(direct);
      } catch (cause) {
        if (active) setFailure(cause);
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
      controller.abort();
    };
  }, [value, maxLevel, retry]);

  useEffect(() => {
    if (error) first.current?.focus();
  }, [error]);
  const selected = (level: PeopleArea["level"]) =>
    path.find((a) => a.level === level);
  const locked = disabled || readOnly || loading || Boolean(failure);
  const stopped = path.some((a) => a.useYn === "N");
  function choose(level: PeopleArea["level"], code: string) {
    const rank = { SIDO: 0, SGG: 1, EMD: 2 };
    const ancestors = path.filter((a) => rank[a.level] < rank[level]);
    const options = level === "SIDO" ? sidos : level === "SGG" ? sggs : emds;
    const item = options.find((a) => a.code === code);
    const next = item ? [...ancestors, item] : ancestors;
    setPath(next);
    if (level === "SIDO") {
      setSggs([]);
      setEmds([]);
      setDirectEmd(false);
    }
    if (level === "SGG") setEmds([]);
    onChange(next[next.length - 1]?.code ?? null);
  }
  function field(
    level: PeopleArea["level"],
    label: string,
    options: PeopleArea[],
    unavailable: boolean,
  ) {
    const existing = selected(level);
    const entries =
      existing && !options.some((a) => a.code === existing.code)
        ? [...options, existing]
        : options;
    const inactiveParent = path.some(
      (a) =>
        a.useYn === "N" &&
        (level === "EMD"
          ? a.level !== "EMD"
          : level === "SGG" && a.level === "SIDO"),
    );
    return (
      <div className="sicost-field">
        <label htmlFor={`${id}-${level}`}>{label}</label>
        <select
          id={`${id}-${level}`}
          ref={level === "SIDO" ? first : undefined}
          name={level === "SIDO" ? "homeAreaCode" : undefined}
          data-testid={`${prefix}-${level.toLowerCase()}`}
          value={existing?.code ?? ""}
          disabled={locked || unavailable}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined}
          onChange={(event) => choose(level, event.target.value)}
        >
          <option value="">선택</option>
          {entries.map((area) => (
            <option
              key={area.code}
              value={area.code}
              disabled={area.useYn === "N" || inactiveParent}
            >
              {area.name}
              {area.useYn === "N" ? " (사용 중지)" : ""}
            </option>
          ))}
        </select>
      </div>
    );
  }
  return (
    <section
      className="sicost-address"
      data-testid={prefix}
      aria-label="거주지"
      aria-busy={loading}
    >
      <div className="sicost-address-fields">
        {field("SIDO", "거주지(시·도)", sidos, false)}
        {!directEmd &&
          field("SGG", "거주지(시·군·구)", sggs, !selected("SIDO"))}
        {maxLevel === "EMD" &&
          field("EMD", "거주지(동)", emds, !selected("SGG") && !directEmd)}
        {!readOnly && (
          <button
            type="button"
            data-testid={`${prefix}-clear`}
            disabled={disabled || loading || !value}
            onClick={() => {
              setPath([]);
              setSggs([]);
              setEmds([]);
              setDirectEmd(false);
              onChange(null);
            }}
          >
            비우기
          </button>
        )}
      </div>
      {loading && (
        <p role="status" className="sicost-address-hint">
          행정구역을 불러오는 중…
        </p>
      )}
      {!loading && !failure && sidos.length === 0 && (
        <p className="sicost-empty">선택할 행정구역이 없습니다.</p>
      )}
      {!loading &&
        !failure &&
        selected("SIDO") &&
        sggs.length === 0 &&
        emds.length === 0 && (
          <p className="sicost-address-hint">
            선택할 하위 행정구역이 없습니다.
          </p>
        )}
      {stopped && (
        <p className="sicost-address-hint">
          <StatusBadge tone="lock">사용 중지</StatusBadge> 기존 주소는 변경하지
          않고 유지할 수 있습니다. 새 선택은 사용할 수 없습니다.
        </p>
      )}
      {failure != null && (
        <ErrorBanner
          message={
            failure instanceof Error
              ? failure.message
              : "행정구역을 조회하지 못했습니다."
          }
          requestId={
            failure instanceof ApiError ? failure.body.requestId : undefined
          }
          onRetry={() => setRetry((n) => n + 1)}
        />
      )}
      {error && (
        <p id={`${id}-error`} role="alert" className="sicost-field-error">
          {error}
        </p>
      )}
    </section>
  );
}
