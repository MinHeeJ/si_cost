import { ScreenLoading } from "../../shared/ScreenLoading";
import { useState } from "react";
import { ApiError } from "../../shared/api";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import {
  developerOptions,
  type DeveloperOption,
} from "../assignmentsops/assignmentActionsApi";
import {
  createTentativeAssignment,
  type TentativeSelection,
  type TentativeResult,
} from "./tentativeSelectionApi";

export function TentativeAssignmentSelector({
  selection,
  selectedCount,
  needCount,
  readOnly = false,
  onSaved,
}: {
  selection: Omit<TentativeSelection, "developerId">;
  selectedCount: number;
  needCount: number;
  readOnly?: boolean;
  onSaved(result: TentativeResult): Promise<void>;
}) {
  const [search, setSearch] = useState("");
  const [rows, setRows] = useState<DeveloperOption[]>([]);
  const [loading, setLoading] = useState(false),
    [searched, setSearched] = useState(false);
  const [error, setError] = useState<unknown>();
  const [warnings, setWarnings] = useState<string[]>([]);
  const { commit, pending, message } = useCommitRefresh(
    "저장됐지만 다시 불러오지 못했습니다",
  );
  const disabled = readOnly || pending || selectedCount >= needCount;
  async function find() {
    if (loading || readOnly) return;
    setLoading(true);
    setError(undefined);
    try {
      setRows((await developerOptions(search)).items);
      setSearched(true);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }
  async function select(developerId: string) {
    if (disabled) return;
    setError(undefined);
    let saved: TentativeResult | undefined;
    try {
      await commit(
        async () => {
          saved = await createTentativeAssignment({
            ...selection,
            developerId,
          });
          setWarnings(saved.warnings);
        },
        async () => {
          if (saved) await onSaved(saved);
        },
      );
    } catch (e) {
      setError(e);
    }
  }
  return (
    <section
      data-testid="tentative-assignment-selector"
      data-screen-id="SCR-UI-29"
      className="assignment-form"
    >
      <h3>
        개발자 가배정 ({selectedCount}/{needCount})
      </h3>
      <p>
        활성 직접 인력만 선택합니다. 다른 행에서 선택한 사람은 마지막 선택행으로
        이동합니다.
      </p>
      {readOnly && <p>조회 전용입니다.</p>}
      <label>
        개발자 검색
        <input
          data-testid="tentative-developer-search"
          value={search}
          disabled={readOnly || pending}
          onChange={(e) => setSearch(e.target.value)}
        />
      </label>
      <button
        data-testid="tentative-search-button"
        disabled={readOnly || loading || pending}
        onClick={() => void find()}
      >
        개발자 조회
      </button>
      {loading && <ScreenLoading message="개발자를 조회하는 중입니다." />}
      {error != null && (
        <div role="alert">
          {error instanceof Error ? error.message : "저장하지 못했습니다."}
          {error instanceof ApiError && (
            <span>
              {" "}
              ·{" "}
              {Object.entries(error.fieldMessages)
                .map(([field, value]) => `${field}: ${value}`)
                .join(" / ")}{" "}
              · 요청 ID {error.body.requestId}
            </span>
          )}
        </div>
      )}
      {message && <p role="status">{message}</p>}
      {warnings.map((w, i) => (
        <p data-testid={`tentative-warning-${i}`} key={i} role="note">
          {w}
        </p>
      ))}
      {searched && !rows.length && <p>선택 가능한 개발자가 없습니다.</p>}
      {rows.map((row) => (
        <div
          key={row.id}
          data-testid={`tentative-developer-${row.id.toLowerCase()}`}
        >
          <span>
            {row.name} · {row.managementNo}
          </span>
          <button
            data-testid={`tentative-select-${row.id.toLowerCase()}`}
            disabled={disabled}
            onClick={() => void select(row.id)}
          >
            선택
          </button>
        </div>
      ))}
    </section>
  );
}
