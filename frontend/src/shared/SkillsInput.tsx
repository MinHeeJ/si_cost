import { FieldHelp } from "./FieldHelp";
import { useEffect, useId, useRef, useState } from "react";
import { request } from "./api";

export function SkillsInput({
  value,
  onChange,
  disabled,
  error,
  required = false,
  label = "보유 스킬",
}: {
  value: string[];
  onChange(value: string[]): void;
  disabled?: boolean;
  error?: string;
  required?: boolean;
  label?: string;
}) {
  const id = useId();
  const [text, setText] = useState(value.join(", "));
  const lastValue = useRef(value.join(", "));
  const [suggestions, setSuggestions] = useState<
    { code: string; name: string }[]
  >([]);
  const [loaded, setLoaded] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [retry, setRetry] = useState(0);
  const [focused, setFocused] = useState(false);
  const [activeOption, setActiveOption] = useState(-1);
  useEffect(() => {
    const next = value.join(", ");
    if (next !== lastValue.current) {
      lastValue.current = next;
      setText(next);
    }
  }, [value]);
  useEffect(() => {
    const controller = new AbortController();
    setLoaded(false);
    setLoadError(false);
    void request<{ items: { code: string; name: string }[] }>(
      "/api/codes/SKILL",
      { signal: controller.signal },
    )
      .then((r) => {
        if (!controller.signal.aborted) {
          setSuggestions(r.items);
          setLoaded(true);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoadError(true);
      });
    return () => controller.abort();
  }, [retry]);
  const normalize = (raw: string) => [
    ...new Set(
      raw
        .split(",")
        .map((s) => {
          const trimmed = s.trim();
          return (
            suggestions.find(
              (option) => option.name.toLowerCase() === trimmed.toLowerCase(),
            )?.name ?? trimmed
          );
        })
        .filter(Boolean),
    ),
  ];
  const current = normalize(text);
  // 칸에 머무는 동안 아직 치고 있는 마지막 조각은 미등록 안내에서 뺀다(칸을 벗어나거나 쉼표를 치면 안내).
  const typing = focused && text.slice(text.lastIndexOf(",") + 1).trim() !== "";
  const settled = typing
    ? normalize(text.slice(0, text.lastIndexOf(",") + 1))
    : current;
  const fragment = text
    .slice(text.lastIndexOf(",") + 1)
    .trim()
    .toLowerCase();
  const matches =
    focused && fragment
      ? suggestions.filter((s) => s.name.toLowerCase().includes(fragment))
      : [];
  function update(raw: string) {
    setText(raw);
    setActiveOption(-1);
    const next = normalize(raw);
    lastValue.current = next.join(", ");
    onChange(next);
  }
  function choose(name: string) {
    const comma = text.lastIndexOf(",");
    update(comma < 0 ? name : `${text.slice(0, comma + 1)} ${name}`);
    setFocused(false);
  }
  return (
    <div className="sicost-field">
      <label htmlFor={id}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
        <FieldHelp label={label} />
      </label>
      <input
        id={id}
        role="combobox"
        aria-label={label}
        aria-autocomplete="list"
        aria-controls={`${id}-options`}
        aria-expanded={matches.length > 0}
        aria-activedescendant={
          activeOption >= 0 ? `${id}-option-${activeOption}` : undefined
        }
        data-testid="people-skills-input"
        value={text}
        disabled={disabled}
        aria-invalid={Boolean(error)}
        onFocus={() => setFocused(true)}
        onBlur={() => {
          setFocused(false);
          const normalized = current.join(", ");
          if (lastValue.current !== normalized) update(normalized);
          else setText(normalized);
        }}
        onChange={(e) => {
          setFocused(true);
          update(e.target.value);
        }}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            setFocused(false);
            setActiveOption(-1);
          }
          if (
            matches.length &&
            (e.key === "ArrowDown" || e.key === "ArrowUp")
          ) {
            e.preventDefault();
            setActiveOption((n) =>
              e.key === "ArrowDown"
                ? (n + 1) % matches.length
                : (n <= 0 ? matches.length : n) - 1,
            );
          }
          if (e.key === "Enter" && matches.length && activeOption >= 0) {
            e.preventDefault();
            choose(matches[activeOption].name);
          }
        }}
        placeholder="쉼표로 구분하여 입력"
      />
      {matches.length > 0 && (
        <ul
          id={`${id}-options`}
          role="listbox"
          aria-label={`${label} 제안`}
          data-testid="people-skills-suggestions"
        >
          {matches.map((s, index) => (
            <li key={s.code} role="presentation">
              <button
                type="button"
                role="option"
                id={`${id}-option-${index}`}
                aria-selected={activeOption === index}
                data-testid={`people-skill-option-${s.code.toLowerCase().replace(/_/g, "-")}`}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => choose(s.name)}
              >
                {s.name}
              </button>
            </li>
          ))}
        </ul>
      )}
      <small>
        입력순으로 저장합니다. 미등록 스킬도 입력할 수 있습니다. (최대 999개·각
        50자)
      </small>
      {loaded &&
        settled
          .filter((s) => !suggestions.some((option) => option.name === s))
          .map((s) => (
            <small key={s} className="sicost-tone-warn">
              ‘{s}’은(는) 등록된 스킬이 아니라 AI 추천 점수에 쓰이지 않습니다.
            </small>
          ))}
      {loadError && (
        <small>
          등록된 스킬을 불러오지 못했습니다. 입력한 스킬은 저장할 수 있습니다.{" "}
          <button
            type="button"
            data-testid="people-skills-retry"
            disabled={disabled}
            onClick={() => setRetry((n) => n + 1)}
          >
            다시 조회
          </button>
        </small>
      )}
      {error && (
        <span role="alert" className="sicost-field-error">
          {error}
        </span>
      )}
    </div>
  );
}
