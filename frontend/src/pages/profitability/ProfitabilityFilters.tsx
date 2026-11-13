import { useEffect, useState } from "react";
import { FilterBar } from "../../shared/FilterBar";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import {
  getClientOptions,
  type ClientOption,
} from "../clientsops/clientActionsApi";
import {
  getSalesPartOptions,
  type PartOption,
} from "../salespartsops/partStandardsApi";
import type { ProfitabilityQuery } from "./individualProfitabilityApi";

export function ProfitabilityFilters({
  query,
  onApply,
  onReset,
  loading,
  fieldMessages,
  onEmployeeTypes,
}: {
  query: ProfitabilityQuery;
  onApply(query: ProfitabilityQuery): void;
  onReset(): void;
  loading: boolean;
  fieldMessages: Record<string, string>;
  onEmployeeTypes?(items: SharedCodeLookup[]): void;
}) {
  const [draft, setDraft] = useState(query),
    [expanded, setExpanded] = useState(false);
  const [jobs, setJobs] = useState<SharedCodeLookup[]>([]),
    [employees, setEmployees] = useState<SharedCodeLookup[]>([]);
  const [companies, setCompanies] = useState<ClientOption[]>([]),
    [parts, setParts] = useState<PartOption[]>([]);
  const [error, setError] = useState(""),
    [revision, setRevision] = useState(0);
  useEffect(() => setDraft(query), [query]);
  useEffect(() => {
    let live = true;
    setError("");
    void Promise.all([
      getSharedCodes("JOB", true),
      getSharedCodes("EMP_TYPE", true),
      getClientOptions("VENDOR"),
      getSalesPartOptions(),
    ])
      .then(([j, e, c, p]) => {
        if (live) {
          setJobs(j.items);
          setEmployees(e.items);
          onEmployeeTypes?.(e.items);
          setCompanies(c.items);
          setParts(p.items);
        }
      })
      .catch((e: unknown) => {
        if (live)
          setError(
            e instanceof Error
              ? e.message
              : "필터 선택값을 불러오지 못했습니다.",
          );
      });
    return () => {
      live = false;
    };
  }, [revision]);
  const set = (field: keyof ProfitabilityQuery, value: string) =>
    setDraft((d) => ({ ...d, [field]: value || undefined }));
  return (
    <FilterBar
      testId="profitability-filters"
      className="profitability-filters"
      disabled={loading}
      onSubmit={() => { if (!loading) onApply(draft); }}
      onReset={onReset}
      searchTestId="profitability-search-button"
      resetTestId="profitability-reset-button"
    >
      <label className="sicost-field">
        이름
        <input
          data-testid="profitability-name-filter"
          aria-label="이름 검색"
          value={draft.name ?? ""}
          maxLength={100}
          onChange={(e) => set("name", e.target.value)}
        />
        {fieldMessages.name && <small role="alert">{fieldMessages.name}</small>}
      </label>
      <label className="sicost-field">
        직원구분
        <select
          data-testid="profitability-employee-filter"
          value={draft.employeeType ?? ""}
          onChange={(e) => set("employeeType", e.target.value)}
        >
          <option value="">전체</option>
          {employees.map((e) => (
            <option key={e.code} value={e.code}>
              {e.name}
            </option>
          ))}
        </select>
      </label>
      <label className="sicost-field">
        소속
        <select
          data-testid="profitability-company-filter"
          value={draft.companyId ?? ""}
          onChange={(e) => set("companyId", e.target.value)}
        >
          <option value="">전체</option>
          <option value="SELF">자사</option>
          {companies.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      <label className="sicost-field">
        사람 파트
        <select
          data-testid="profitability-part-filter"
          value={draft.partId ?? ""}
          onChange={(e) => set("partId", e.target.value)}
        >
          <option value="">전체</option>
          <option value="UNASSIGNED">미지정</option>
          {parts.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
              {p.hqYn === "Y" ? " (본사)" : ""}
            </option>
          ))}
        </select>
      </label>
      <label className="sicost-field">
        주직무
        <select
          data-testid="profitability-job-filter"
          value={draft.jobCode ?? ""}
          onChange={(e) => set("jobCode", e.target.value)}
        >
          <option value="">전체</option>
          {jobs.map((j) => (
            <option key={j.code} value={j.code}>
              {j.name}
            </option>
          ))}
        </select>
      </label>
      <button
        type="button"
        data-testid="profitability-detail-filters-button"
        aria-expanded={expanded}
        onClick={() => setExpanded((v) => !v)}
      >
        상세조건
      </button>
      {expanded && (
        <fieldset data-testid="profitability-detail-filters-panel">
          <legend>상세조건</legend>
          <label className="sicost-field">
            대상
            <select
              data-testid="profitability-show-filter"
              value={draft.show}
              onChange={(e) =>
                setDraft((d) => ({
                  ...d,
                  show: e.target.value as ProfitabilityQuery["show"],
                }))
              }
            >
              <option value="ALL">전체</option>
              <option value="LOSS">적자</option>
              <option value="CONCURRENT">동시투입</option>
            </select>
          </label>
          <label className="sicost-field">
            <input
              type="checkbox"
              data-testid="profitability-turnkey-filter"
              checked={draft.includeTurnkey}
              onChange={(e) =>
                setDraft((d) => ({ ...d, includeTurnkey: e.target.checked }))
              }
            />
            턴키 단가 포함
          </label>
        </fieldset>
      )}
      {error && (
        <div role="alert">
          {error}
          <button
            type="button"
            data-testid="profitability-filter-retry-button"
            onClick={() => setRevision((n) => n + 1)}
          >
            선택값 다시 조회
          </button>
        </div>
      )}
      {Object.entries(fieldMessages)
        .filter(([key]) => key !== "name")
        .map(([key, message]) => (
          <small key={key} role="alert">
            {key}: {message}
          </small>
        ))}
    </FilterBar>
  );
}
