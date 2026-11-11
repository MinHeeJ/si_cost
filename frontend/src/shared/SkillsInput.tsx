import { useEffect, useId, useState } from "react";
import { request } from "./api";

export function SkillsInput({
  value,
  onChange,
  disabled,
  error,
}: {
  value: string[];
  onChange(value: string[]): void;
  disabled?: boolean;
  error?: string;
}) {
  const id = useId();
  const [text, setText] = useState(value.join(", "));
  const [suggestions, setSuggestions] = useState<
    { code: string; name: string }[]
  >([]);
  useEffect(() => {
    let active = true;
    void request<{ items: { code: string; name: string }[] }>(
      "/api/codes/SKILL",
    )
      .then((r) => {
        if (active) setSuggestions(r.items);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);
  return (
    <div className="sicost-field">
      <label htmlFor={id}>보유 스킬</label>
      <input
        id={id}
        data-testid="people-skills-input"
        list={`${id}-options`}
        value={text}
        disabled={disabled}
        aria-invalid={Boolean(error)}
        onChange={(e) => {
          setText(e.target.value);
          onChange([
            ...new Set(
              e.target.value
                .split(",")
                .map((s) => s.trim())
                .filter(Boolean),
            ),
          ]);
        }}
        placeholder="쉼표로 구분하여 입력"
      />
      <datalist id={`${id}-options`}>
        {suggestions.map((s) => (
          <option key={s.code} value={s.name} />
        ))}
      </datalist>
      <small>
        입력순으로 저장합니다. 미등록 스킬도 입력할 수 있습니다. (최대 999개·각
        50자)
      </small>
      {error && (
        <span role="alert" className="sicost-field-error">
          {error}
        </span>
      )}
    </div>
  );
}
