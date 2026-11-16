import { useState, type ReactNode } from "react";
import type { Column } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import { formatDateTime, type AmountUnit } from "../../shared/format";
import { usePreferences } from "../me/usePreferences";
import type { SharedCodeLookup } from "../shared/sharedApi";
import { ProfitabilityFilters } from "./ProfitabilityFilters";
import { ProfitabilitySummary } from "./ProfitabilitySummary";
import { ProfitabilityContributionTable } from "./ProfitabilityContributionTable";
import { ProfitabilityTable } from "./ProfitabilityTable";
import { ProfitabilityValue } from "./ProfitabilityValue";
import { ProfitabilityColumnSettings } from "./ProfitabilityColumnSettings";
import { profitabilityColumns } from "./profitabilityColumns";
import { useIndividualProfitability } from "./useIndividualProfitability";
import { useProfitabilityDetails } from "./useProfitabilityDetails";
import { ProfitabilityMonthlyLedger } from "./ProfitabilityMonthlyLedger";
import { ProfitabilityDetails } from "./ProfitabilityDetails";
import { ProfitabilityBadges } from "./ProfitabilityBadges";
import type {
  IndividualProfitabilityData,
  ProfitabilityMetrics,
  ProfitabilityPerson,
  ProfitabilityQuery,
  ProfitabilitySort,
} from "./individualProfitabilityApi";
import "./profitability.css";

/** Later detail/export groups compose into these typed seams, sharing the applied query. */
export type IndividualProfitabilityScreenProps = {
  onPerson?(personId: string): void;
  renderLedger?(
    person: ProfitabilityPerson,
    query: ProfitabilityQuery,
  ): ReactNode;
  renderExport?(
    query: ProfitabilityQuery,
    data: IndividualProfitabilityData,
  ): ReactNode;
  renderCalculationGuide?(data: IndividualProfitabilityData): ReactNode;
};
const sortable = new Set<string>([
  "name",
  "companyName",
  "employeeType",
  "hireDate",
  "utilization",
  "mm",
  "revenue",
  "totalCost",
  "profit",
  "profitRate",
]);
export function IndividualProfitabilityScreen({
  onPerson,
  renderLedger,
  renderExport,
  renderCalculationGuide,
}: IndividualProfitabilityScreenProps = {}) {
  const preferences = usePreferences();
  const pageSize = preferences.get("PAGE_SIZE")?.value;
  const initialSize = pageSize === 20 || pageSize === 100 ? pageSize : 50;
  const unitValue = preferences.get("AMT_UNIT")?.value;
  const unit: AmountUnit =
    unitValue === "원" || unitValue === "백만원" ? unitValue : "천원";
  const state = useIndividualProfitability(initialSize);
  const { query, data, loading, mobile } = state;
  const details = useProfitabilityDetails(query, mobile);
  const { expandedKeys, setExpandedKeys } = details;
  const openPerson = onPerson ?? details.openPerson;
  const ledger =
    renderLedger ??
    ((person: ProfitabilityPerson, applied: ProfitabilityQuery) => (
      <ProfitabilityMonthlyLedger
        personId={person.personId}
        query={applied}
        unit={unit}
        jobLabels={Object.fromEntries((data?.contributions ?? []).map((cell) => [cell.jobCode, cell.jobName]))}
      />
    ));
  const [settingsError, setSettingsError] = useState("");
  const [employeeTypes, setEmployeeTypes] = useState<SharedCodeLookup[]>([]);
  const value = (metrics: ProfitabilityMetrics, id: string) => (
    <ProfitabilityValue
      value={metrics[id as keyof ProfitabilityMetrics]}
      unit={unit}
      kind={id === "mm" ? "mm" : id === "profitRate" ? "rate" : "amount"}
    />
  );

  const name = (person: ProfitabilityPerson) => (
    <>
      <button
        type="button"
        className="sicost-link"
        data-testid={`profitability-person-${person.personId.toLowerCase().replace(/[^a-z0-9-]/g, "-")}`}
        onClick={(e) => {
          e.stopPropagation();
          openPerson(person.personId);
        }}
      >
        {person.name ?? person.personId} ({person.managementNo ?? "–"})
      </button>
      <ProfitabilityBadges person={person} />
    </>
  );
  const columns: Column<ProfitabilityPerson>[] = profitabilityColumns.map(
    (def) => ({
      id: def.id,
      label:
        def.id === "pay" && query.view === "CUMULATIVE"
          ? "급여(누계)"
          : def.label,
      sortable: sortable.has(def.id),
      align: [
        "name",
        "companyName",
        "position",
        "employeeType",
        "hireDate",
      ].includes(def.id)
        ? "left"
        : "right",
      render: (person) => {
        if (def.id === "name") return name(person);
        if (def.id === "utilization")
          return (
            <span
              title={`${person.utilizedMonths} 투입달 ÷ ${person.employmentMonths} 직접 재직달. ${person.monthBasis.map((m) => `${m.month} ${m.explanation}`).join("; ")}`}
            >
              <ProfitabilityValue value={person.utilization} kind="rate" />
            </span>
          );
        if (def.id === "hireDate")
          return <ProfitabilityValue value={person.hireDate} kind="date" />;
        if (def.id === "employeeType")
          return (
            employeeTypes.find((e) => e.code === person.employeeType)?.name ??
            person.employeeType
          );
        if (["companyName", "position"].includes(def.id))
          return person[def.id as "companyName" | "position"] ?? "–";
        if (def.id === "revenue" && person.turnkeyOnlyExcluded)
          return (
            <span title="TK전용: 턴키 단가만 제외했습니다. 비용과 이익은 유지되며 평균 단가 분모에서만 제외합니다.">
              –
            </span>
          );
        return value(person.metrics, def.id);
      },
    }),
  );
  const apply = (next: ProfitabilityQuery) => {
    setExpandedKeys([]);
    state.apply(next);
  };
  return (
    <div
      data-testid="profitability-overview"
      className="profitability-overview"
    >
      {data && (<>
          <p
            className="profitability-context"
            data-testid="profitability-context"
          >
            {data.context.period.year}년 {data.context.period.fromMonth ?? "–"}{" "}
            ~ {data.context.period.toMonth ?? "–"}·{" "}
            {data.context.view === "CUMULATIVE" ? "연 누계" : "월 평균"} · 턴키
            단가 {data.context.includeTurnkey ? "포함" : "제외"} · 단가: 개인
            배분 참고액 · 계산{" "}
            {formatDateTime(data.context.period.calculatedAt)}
          </p>
          <ProfitabilitySummary summary={data.summary} unit={unit} />
          {(data.unknownCount > 0 || data.estimatedCount > 0) && (
            <p role="note">
              자료 미확인 {data.unknownCount}명 · 급여 미업로드·예정{" "}
              {data.estimatedCount}명. 미확인 금액은 0으로 계산하지 않습니다.
            </p>
          )}
          <ProfitabilityContributionTable
            cells={data.contributions}
            unit={unit}
          />
      </>)}
      <ProfitabilityFilters
        query={query}
        onApply={apply}
        onReset={state.reset}
        loading={loading}
        fieldMessages={state.fieldMessages}
        onEmployeeTypes={setEmployeeTypes}
      />
      <div className="profitability-toolbar">
        <div className="profitability-view" role="group" aria-label="금액 보기">
          {([['CUMULATIVE', '연 누계'], ['MONTHLY_AVERAGE', '월 평균']] as const).map(([view, label]) => (
            <button key={view} type="button" disabled={loading}
              className={query.view === view ? "sicost-primary" : ""}
              aria-pressed={query.view === view}
              onClick={() => apply({ ...query, view })}>
              {label}
            </button>
          ))}
        </div>
        <label>
          단위
          <select
            data-testid="profitability-unit-select"
            value={unit}
            disabled={preferences.loading || preferences.pending}
            onChange={(e) => {
              setSettingsError("");
              void preferences
                .put("AMT_UNIT", "*", e.target.value)
                .catch((e: unknown) =>
                  setSettingsError(
                    e instanceof Error ? e.message : "단위 저장 실패",
                  ),
                );
            }}
          >
            <option>원</option>
            <option>천원</option>
            <option>백만원</option>
          </select>
        </label>
        {(mobile || !data) && <ProfitabilityColumnSettings />}
        {!mobile && data && renderExport?.(query, data)}
      </div>
      {settingsError && <ErrorBanner message={settingsError} />}
      {state.errorMessage && (
        <ErrorBanner message={state.errorMessage} onRetry={state.refresh} />
      )}
      {loading && <p role="status">개인 원가·수익을 불러오는 중입니다.</p>}
      {data && (
        <>
          {!mobile && (
            <div
              className="profitability-toolbar"
              onClick={(event) => event.stopPropagation()}
            >
              <button
                type="button"
                data-testid="profitability-expand-page"
                disabled={loading || !data.items.length}
                onClick={() =>
                  setExpandedKeys(data.items.map((person) => person.personId))
                }
              >
                현재 페이지 모두 펼치기
              </button>
              <button
                type="button"
                data-testid="profitability-collapse-all"
                disabled={!expandedKeys.length}
                onClick={() => setExpandedKeys([])}
              >
                모두 접기
              </button>
            </div>
          )}
          {mobile ? (
            <div
              data-testid="profitability-mobile-cards"
              className="profitability-mobile-cards"
            >
              {state.cards.map((person, index) => (
                <article
                  data-testid={`profitability-card-${index}`}
                  key={person.personId}
                >
                  <h2>{name(person)}</h2>
                  <p>
                    개인 단가 {value(person.metrics, "revenue")} · 원가{" "}
                    {value(person.metrics, "totalCost")}
                  </p>
                  <p>
                    이익 {value(person.metrics, "profit")} · 가동률{" "}
                    <ProfitabilityValue
                      value={person.utilization}
                      kind="rate"
                    />
                  </p>
                  <button
                    type="button"
                    data-testid={`profitability-card-detail-${index}`}
                    aria-expanded={expandedKeys.includes(person.personId)}
                    onClick={(event) => {
                      event.stopPropagation();
                      setExpandedKeys((keys) =>
                        keys.includes(person.personId) ? [] : [person.personId],
                      );
                    }}
                  >
                    월별 상세
                  </button>
                  {expandedKeys.includes(person.personId) && (
                    <div data-testid={`profitability-card-panel-${index}`}>
                      {ledger(person, query)}
                    </div>
                  )}
                </article>
              ))}
              {!state.cards.length && (
                <p>
                  {data.context.period.noData
                    ? "개인 원가 자료 없음"
                    : "조건에 맞는 사람이 없습니다."}
                  <button type="button" data-testid="profitability-mobile-empty-reset-button" onClick={state.reset}>
                    조건 초기화
                  </button>
                </p>
              )}
              {state.cards.length < data.total && (
                <button
                  type="button"
                  data-testid="profitability-more-button"
                  disabled={loading}
                  onClick={() =>
                    state.setQuery((q) => ({ ...q, page: q.page + 1 }))
                  }
                >
                  더보기 20건
                </button>
              )}
            </div>
          ) : (
            <ProfitabilityTable
              rows={data.items}
              rowKey={(p) => p.personId}
              columns={columns}
              page={data.page}
              size={data.size}
              total={data.total}
              stickyColumns={[{ id: "name", width: 220 }]}
              onRowAction={(person) => openPerson(person.personId)}
              onPageChange={(page) => state.setQuery((q) => ({ ...q, page }))}
              onSizeChange={(size) => {
                apply({ ...query, size });
                setSettingsError("");
                void preferences
                  .put("PAGE_SIZE", "*", size)
                  .catch((e: unknown) =>
                    setSettingsError(
                      e instanceof Error ? e.message : "건수 저장 실패",
                    ),
                  );
              }}
              sort={
                query.sort
                  ? { id: query.sort, direction: query.direction ?? "ASC" }
                  : undefined
              }
              onSortChange={(id) =>
                apply({
                  ...query,
                  sort: id as ProfitabilitySort,
                  direction:
                    query.sort === id && query.direction !== "DESC"
                      ? "DESC"
                      : "ASC",
                })
              }
              empty={
                data.context.period.noData
                  ? "개인 원가 자료 없음"
                  : data.total > 0
                    ? "해당 페이지에 자료가 없습니다."
                    : "조건에 맞는 사람이 없습니다."
              }
              emptyAction={
                <button
                  type="button"
                  data-testid="profitability-empty-reset-button"
                  onClick={state.reset}
                >
                  조건 초기화
                </button>
              }
              expandableRow={{
                expandedKeys,
                onExpandedChange: setExpandedKeys,
                label: (p) => p.name ?? p.personId,
                render: (p) => ledger(p, query),
              }}
              footer={{
                label: "필터 전체 합계",
                render: (column) =>
                  column.id === "name"
                    ? "전체 합계"
                    : [
                          "companyName",
                          "position",
                          "employeeType",
                          "hireDate",
                          "utilization",
                        ].includes(column.id)
                      ? "–"
                      : value(data.totals, column.id),
              }}
            />
          )}
          {renderCalculationGuide?.(data)}
          <p role="note">
            월평균 N은 사람별 근무월입니다. 급여구성과 개인 원가는 원 단위
            절사차이가 날 수 있습니다. 가동률은 투입달 ÷ 운영 이후 직접
            재직달이며 보기와 무관합니다.
          </p>
        </>
      )}
      {!onPerson && details.personId && (
        <ProfitabilityDetails
          personId={details.personId}
          onClose={details.closePerson}
        />
      )}
    </div>
  );
}
