import { useEffect, useId, useState } from "react";
import { request } from "./api";
import { HelpTooltip } from "./HelpTooltip";

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
      <div className="sicost-field-label">
        <label htmlFor={id}>보유 스킬</label>
        <HelpTooltip label="보유 스킬">
          공통코드 「스킬」(UI-27)에 있는 이름을 쉼표(,)로 구분해 여러 개
          적습니다. AI 인력 추천의 스킬 점수에 씁니다. 입력순으로 저장하며
          미등록 스킬도 입력할 수 있습니다(최대 999개·각 50자).
        </HelpTooltip>
      </div>
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

      {error && (
        <span role="alert" className="sicost-field-error">
          {error}
        </span>
      )}
    </div>
  );
}
