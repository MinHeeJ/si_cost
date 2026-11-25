import { HelpTooltip } from "../../shared/HelpTooltip";
import { FieldHelp } from "../../shared/FieldHelp";
import { helpContent } from "../../shared/helpContent";
import { PeriodStatus } from "../../shared/PeriodStatus";
import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { KpiStrip } from "../../shared/Surface";
import { useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { FilterBar } from "../../shared/FilterBar";
import { ApiError } from "../../shared/api";
import {
  formatDateTime,
  formatDecimal,
  formatRate,
  type AmountUnit,
} from "../../shared/format";
import { DeveloperDrawer } from "../developers/DeveloperDrawer";
import { useSharedScreen } from "../shared/SharedScreenContext";
import { usePreferences } from "../me/usePreferences";
import type { ColumnView, ScreenColumnPreferences } from "../me/preferencesApi";
import { ProfitabilityValue } from "../profitability/ProfitabilityValue";
import { getProjects } from "../projects/projectsApi";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import * as api from "./rateRenegotiationsApi";
import {
  useFieldPermission,
  usePermission,
} from "../../shared/permission/PermissionBoundary";
import "./rateRenegotiations.css";

export const renegotiationColumns = [
  { id: "name", label: "이름", required: true },
  { id: "employeeType", label: "구분" },
  { id: "projectName", label: "프로젝트", required: true },
  { id: "renewalDate", label: "갱신일" },
  { id: "contractMm", label: "MM" },
  { id: "unitPrice", label: "현재 단가" },
  { id: "monthlyLaborCost", label: "월 원가(기준월)" },
  { id: "marginRate", label: "이익률" },
  { id: "targetUnitPrice", label: "목표 단가" },
  { id: "requiredIncreaseRate", label: "필요 인상률" },
  { id: "annualGain", label: "올리면 연 이익" },
];
const columnHelp = new Map<string, string>(
  helpContent
    .filter((entry) => entry.screen === "UI-32" && entry.kind === "B")
    .map(({ target, text }) => [target, text]),
);
const simulationHelp = helpContent.find(
  (entry) => entry.screen === "UI-32" && entry.target === "단가 인상 시뮬레이션",
)?.text;
const guideColumns = renegotiationColumns.filter(({ label }) =>
  columnHelp.has(label),
);
const guideNotes: Record<string, string> = {
  renewalDate: "빨강 30일 안, 주황 3개월 안",
  monthlyLaborCost: "개인 원가·수익표와 같음",
  targetUnitPrice: "만 원 단위 올림",
  annualGain: "1년 계속 투입 가정",
};
const emptyView = (): ColumnView => ({
  order: renegotiationColumns.map((c) => c.id),
  hidden: [],
});
export function RateRenegotiationsScreen({
  onPerson,
}: { onPerson?(id: string): void } = {}) {
  const canCost = useFieldPermission("UI-32", "monthlyLaborCost");
  const canDownload = usePermission("UI-32", "DNLD"),
    canPerson = usePermission("UI-02", "INQ");
  const location = useLocation();
  const preferences = usePreferences();
  const shared = useSharedScreen("UI-32");
  const [query, setQuery] = useState(api.defaultRenegotiationQuery);
  const [draft, setDraft] = useState(api.defaultRenegotiationQuery);
  const [applied, setApplied] = useState(api.defaultRenegotiationQuery);
  const [rate, setRate] = useState("5");
  const [data, setData] = useState<api.RenegotiationsResult>();
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const [revision, setRevision] = useState(0),
    [optionsRevision, setOptionsRevision] = useState(0);
  const [optionError, setOptionError] = useState("");
  const [employees, setEmployees] = useState<SharedCodeLookup[]>([]);
  const [projects, setProjects] = useState<{ id: string; name: string }[]>([]);
  const [person, setPerson] = useState<string>();
  const [dense, setDense] = useState(false),
    [unit, setUnit] = useState<AmountUnit>("천원");
  const storedUnit = preferences.get("AMT_UNIT")?.value;
  useEffect(() => {
    if (storedUnit === "원" || storedUnit === "천원" || storedUnit === "백만원")
      setUnit(storedUnit);
  }, [storedUnit]);
  useEffect(() => {
    setDense(shared.dense ?? false);
  }, [shared.dense]);
  useEffect(() => {
    if (shared.pageSize)
      setQuery((q) =>
        q.size === shared.pageSize
          ? q
          : { ...q, size: shared.pageSize!, page: 1 },
      );
  }, [shared.pageSize]);
  const [columnDraft, setColumnDraft] = useState<ColumnView>();
  useEffect(() => {
    setPerson(undefined);
    setColumnDraft(undefined);
  }, [location.pathname]);
  const [exporting, setExporting] = useState(false),
    [exportError, setExportError] = useState("");
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    void api
      .getRateRenegotiations(query, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setData(result);
          setRate(result.appliedIncreaseRate);
          setApplied({ ...query, increaseRate: result.appliedIncreaseRate });
        }
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [query, revision]);
  useEffect(() => {
    let live = true;
    setOptionError("");
    void (async () => {
      const types = await getSharedCodes("EMP_TYPE", true);
      const found: { id: string; name: string }[] = [];
      let page = 1,
        total = 0;
      do {
        const result = await getProjects({
          page,
          size: 100,
          sort: "name",
          direction: "ASC",
        });
        found.push(...result.items);
        total = result.total;
        page++;
      } while (found.length < total);
      if (live) {
        setEmployees(types.items);
        setProjects(found);
      }
    })().catch((e) => {
      if (live)
        setOptionError(
          e instanceof Error ? e.message : "필터 선택값을 불러오지 못했습니다.",
        );
    });
    return () => {
      live = false;
    };
  }, [optionsRevision]);
  const columnsPreference = preferences.get("COLS", "UI-32");
  const stored = (columnsPreference?.value as ScreenColumnPreferences | null)
    ?.rateRenegotiations;
  const view =
    stored &&
    stored.order.length === 11 &&
    new Set(stored.order).size === 11 &&
    stored.order.every((id) => renegotiationColumns.some((c) => c.id === id)) &&
    !stored.hidden.some((id) => ["name", "projectName"].includes(id))
      ? stored
      : emptyView();
  const submit = (next: api.RenegotiationQuery, assumption = rate) => {
    if (loading) return;
    try {
      const increaseRate = api.normalizeIncrease(assumption);
      setError(undefined);
      setRate(increaseRate);
      setQuery({ ...next, increaseRate });
    } catch (e) {
      setError(e);
    }
  };
  const reset = () => {
    const next = {
      ...api.defaultRenegotiationQuery(),
      increaseRate: applied.increaseRate,
    };
    setDraft(next);
    setRate(applied.increaseRate);
    submit(next, applied.increaseRate);
  };
  const openPerson = (id: string) => {
    if (canPerson) (onPerson ?? setPerson)(id);
  };
  const employeeLabel = (code: string) =>
    employees.find((e) => e.code === code)?.name ?? code;
  const amount = (value: string | null | undefined) => (
    <ProfitabilityValue value={value} unit={unit} />
  );
  const annualGainLabel =
    `${data?.appliedIncreaseRate ?? applied.increaseRate}% 올리면 연 이익`;
  const targetPercent =
    data?.targetMarginRate == null
      ? undefined
      : `${formatDecimal(data.targetMarginRate)}%`;
  const helpFor = (label: string) =>
    columnHelp
      .get(label)
      ?.replace("{현재 목표 안내}", targetPercent ? `(지금 ${targetPercent})` : "")
      .replace("{목표 이익률}", targetPercent ?? "목표 이익률");
  const columns: Column<api.RenegotiationRow>[] = view.order
    .filter(
      (id) =>
        !view.hidden.includes(id) && (canCost || id !== "monthlyLaborCost"),
    )
    .map((id) => {
      const definition = renegotiationColumns.find((c) => c.id === id)!;
      const label =
        id === "annualGain"
          ? annualGainLabel
          : id === "targetUnitPrice"
            ? `목표 단가 (${formatRate(data?.targetMarginRate)})`
            : definition.label;
      const explanation = helpFor(definition.label);
      return {
        id,
        sortable: id !== "employeeType",
        align: ["name", "employeeType", "projectName", "renewalDate"].includes(
          id,
        )
          ? "left"
          : "right",
        label,
        help: explanation && (
          <HelpTooltip label={label} iconOnly>
            <span className="renegotiation-copy">{explanation}</span>
          </HelpTooltip>
        ),
        render: (row) => {
          const marker = row.assignmentId
            .toLowerCase()
            .replace(/[^a-z0-9-]/g, "-");
          if (id === "name")
            return (
              <button
                disabled={!canPerson}
                type="button"
                className="sicost-link renegotiation-person-link"
                data-testid={`renegotiation-person-${marker}`}
                onClick={(event) => {
                  event.stopPropagation();
                  openPerson(row.developerId);
                }}
              >
                {row.name}
                <small className="renegotiation-sub">{row.managementNo}</small>
                {row.planned && <StatusBadge tone="info">예정</StatusBadge>}
              </button>
            );
          if (id === "projectName")
            return (
              <>
                <Link
                  onClick={(event) => event.stopPropagation()}
                  data-testid={`renegotiation-project-${marker}`}
                  to={`/UI-01?${new URLSearchParams({ projectId: row.projectId, tab: "basic" })}`}
                >
                  {row.projectName}
                </Link>
                <Link
                  className="renegotiation-sub"
                  onClick={(event) => event.stopPropagation()}
                  data-testid={`renegotiation-assignment-${marker}`}
                  to={`/UI-12?${new URLSearchParams({ assignmentId: row.assignmentId, tab: "history" })}`}
                >
                  변경 기록
                </Link>
              </>
            );
          if (id === "employeeType")
            return (
              <StatusBadge tone="neutral">
                {employeeLabel(row.employeeType)}
              </StatusBadge>
            );
          if (id === "renewalDate")
            return (
              <>
                <ProfitabilityValue value={row.renewalDate} kind="date" />
                <StatusBadge
                  tone={
                    row.daysUntilRenewal <= 30
                      ? "bad"
                      : row.daysUntilRenewal <= 92
                        ? "warn"
                        : "neutral"
                  }
                >
                  D-{row.daysUntilRenewal}
                </StatusBadge>
              </>
            );
          if (id === "contractMm")
            return (
              <HelpTooltip label="계약 투입량" trigger={<ProfitabilityValue value={row.contractMm} kind="mm" />}>
                계약 투입량 {row.contractMm} / 이번 달 실제 투입량 {row.monthlyMm}
              </HelpTooltip>
            );
          const value = row[id as keyof api.RenegotiationRow] as string | null;
          return (
            <span>
              <ProfitabilityValue
                value={value}
                kind={
                  id === "marginRate" || id === "requiredIncreaseRate"
                    ? "rate"
                    : "amount"
                }
                unit={unit}
              />
              {Boolean(row.issues?.length) && <HelpTooltip label={`${label} 확인사항`} iconOnly>{row.issues?.map(i => i.message).join(" · ")}</HelpTooltip>}
            </span>
          );
        },
      };
    });
  const errorMessage =
    error instanceof Error
      ? error.message
      : error
        ? "조회하지 못했습니다."
        : "";
  return (
    <div
      className="renegotiation-screen"
      data-screen-id="SCR-UI-32"
      data-testid="renegotiation-screen"
    >
      {data && (
        <PeriodStatus className="sicost-period-summary">
          <span className="sicost-period-summary-item">
            <span className="sicost-period-summary-label">기준월</span>
            <b>{data.baseMonth}</b>
          </span>
          <span className="sicost-period-summary-item">
            <span className="sicost-period-summary-label">계산 시각</span>
            <b>{formatDateTime(data.calculatedAt)}</b>
          </span>
        </PeriodStatus>
      )}
      {canDownload && (
        <ScreenTitleActions>
          <button
            className="sicost-button sicost-download-button renegotiation-download"
            data-testid="renegotiation-download"
            type="button"
            disabled={!canDownload || !data || loading || exporting}
            onClick={async () => {
              if (exporting) return;
              setExporting(true);
              setExportError("");
              try {
                await api.downloadRenegotiations(
                  applied,
                  view.order.filter(
                    (id) =>
                      !view.hidden.includes(id) &&
                      (canCost ||
                        id !== "monthlyLaborCost"),
                  ),
                  unit,
                );
              } catch (e) {
                if (alive.current)
                  setExportError(
                    e instanceof Error
                      ? e.message
                      : "다운로드에 실패했습니다.",
                  );
              } finally {
                if (alive.current) setExporting(false);
              }
            }}
          >
            {exporting ? "엑셀 생성 중…" : "엑셀 다운로드"}
          </button>
        </ScreenTitleActions>
      )}
      <KpiStrip className="renegotiation-summary">
        <article>
          <h2>대상 인원</h2>
          <strong>{data?.summary.personCount ?? "–"}명</strong>
          <small>배정 {data?.summary.assignmentCount ?? "–"}건</small>
        </article>
        <article>
          <h2>대상 평균 이익률</h2>
          <strong>
            <ProfitabilityValue
              value={data?.summary.weightedMarginRate}
              kind="rate"
            />
          </strong>
          <small>받는 돈이 큰 사람일수록 크게 반영한 평균</small>
        </article>
        <article>
          <h2>{annualGainLabel}</h2>
          <strong>{amount(data?.summary.annualGain)}</strong>
          <small>
            인상률 {data?.appliedIncreaseRate ?? "–"}% · {unit}
          </small>
        </article>
        <article>
          <h2>목표 단가까지 올리면 연 이익</h2>
          <strong>{amount(data?.summary.targetAnnualGain)}</strong>
          <small>
            목표 이익률 {formatRate(data?.targetMarginRate)} · {unit}
          </small>
        </article>
      </KpiStrip>
      <form
        data-testid="renegotiation-assumption-form"
        className="renegotiation-assumption"
        aria-labelledby="renegotiation-assumption-title"
        onSubmit={(e) => {
          e.preventDefault();
          submit({ ...applied, page: 1 });
        }}
      >
        <div className="renegotiation-assumption-heading">
          <HelpTooltip label="단가 인상 시뮬레이션" iconOnly>
            <span className="renegotiation-copy">{simulationHelp}</span>
          </HelpTooltip>
          <h2 id="renegotiation-assumption-title">단가 인상 시뮬레이션</h2>
        </div>
        <label>
          인상률{" "}
          <span className="renegotiation-rate-input">
            <input
              data-testid="renegotiation-rate"
              inputMode="decimal"
              value={rate}
              disabled={loading}
              aria-invalid={!!errorMessage}
              onChange={(e) => setRate(e.target.value)}
              onBlur={() => {
                if (rate !== applied.increaseRate)
                  submit({ ...applied, page: 1 });
              }}
            />
            <span>%</span>
          </span>
        </label>
        <button
          data-testid="renegotiation-recalculate"
          type="submit"
          disabled={loading}
        >
          재계산
        </button>
        {errorMessage && (
          <small role="alert">
            {errorMessage}
            {error instanceof ApiError
              ? ` · 문의 번호: ${error.body.requestId}`
              : ""}
          </small>
        )}
      </form>
      {errorMessage && (
        <ErrorBanner
          message="직전 정상 결과를 유지합니다."
          pending={loading}
          onRetry={() => setRevision((r) => r + 1)}
        />
      )}
      {exportError && <ErrorBanner message={exportError} />}
      <FilterBar
        className="renegotiation-filters"
        disabled={loading}
        testId="renegotiation-filters"
        onSubmit={() => submit({ ...draft, page: 1 })}
        onReset={reset}
        searchTestId="renegotiation-search"
        resetTestId="renegotiation-reset"
      >
        <label>
          갱신 시점
          <select
            data-testid="renegotiation-window"
            value={draft.renewalWindow}
            onChange={(e) =>
              setDraft({ ...draft, renewalWindow: e.target.value })
            }
          >
            <option value="ALL">전체</option>
            <option value="92">3개월 안</option>
            <option value="183">6개월 안</option>
            <option value="366">1년 안</option>
          </select>
        </label>
        <label>
          이익률
          <FieldHelp label="이익률" /><select
            data-testid="renegotiation-margin"
            value={draft.marginBelow}
            onChange={(e) =>
              setDraft({ ...draft, marginBelow: e.target.value })
            }
          >
            <option value="ALL">전체</option>
            {["10", "15", "20"].map((value) => (
              <option
                data-testid={`renegotiation-margin-option-${value}`}
                key={value}
                value={value}
              >
                {value}% 미만
              </option>
            ))}
          </select>
        </label>
        <label>
          직원 구분
          <select
            data-testid="renegotiation-employee"
            value={draft.employeeType ?? ""}
            onChange={(e) =>
              setDraft({
                ...draft,
                employeeType: e.target.value || undefined,
              })
            }
          >
            <option value="">전체</option>
            {employees.map((e) => (
              <option
                data-testid={`renegotiation-employee-option-${Array.from(e.code, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                key={e.code}
                value={e.code}
              >
                {e.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          프로젝트
          <select
            data-testid="renegotiation-project-filter"
            value={draft.projectId ?? ""}
            onChange={(e) =>
              setDraft({ ...draft, projectId: e.target.value || undefined })
            }
          >
            <option value="">전체</option>
            {projects.map((p) => (
              <option
                data-testid={`renegotiation-project-option-${Array.from(String(p.id), (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                key={p.id}
                value={p.id}
              >
                {p.name}
              </option>
            ))}
          </select>
        </label>
      </FilterBar>
      {optionError && (
        <ErrorBanner
          message={optionError}
          onRetry={() => setOptionsRevision((r) => r + 1)}
        />
      )}
      {(data?.issues ?? []).map((i, index) => (
        <div
          data-testid={`renegotiation-banner-${index}`}
          className="sicost-banner sicost-tone-warn"
          role="note"
          key={`${i.code}-${index}`}
        >
          {i.message}
        </div>
      ))}
      {canCost && !!data?.unevaluatedCount && (
        <details data-testid="renegotiation-unevaluated">
          <summary data-testid="renegotiation-unevaluated-summary">
            평가불가 {data.unevaluatedCount}건 · 원가·매출 미확인 근거 펼침
          </summary>
          {data.unevaluatedAssignments.map((i, index) => (
            <article
              data-testid={`renegotiation-issue-${index}`}
              key={`${i.assignmentId}-${index}`}
            >
              <button
                className="sicost-link"
                data-testid={`renegotiation-issue-person-${index}`}
                onClick={() => i.developerId && openPerson(i.developerId)}
              >
                {i.developerId}
              </button>{" "}
              · {i.projectId} · {i.assignmentId}
              <p>{i.message}</p>
            </article>
          ))}
        </details>
      )}
      <div className="renegotiation-toolbar">
        <span>총 {data?.pagination.total ?? 0}건</span>
        <button
          data-testid="renegotiation-columns"
          type="button"
          onClick={() =>
            setColumnDraft({ order: [...view.order], hidden: [...view.hidden] })
          }
        >
          열 설정
        </button>
        <button
          data-testid="renegotiation-density"
          type="button"
          aria-pressed={dense}
          onClick={() => {
            void preferences
              .put("DENSE", "*", dense ? "넓게" : "좁게")
              .then(() => setDense(!dense))
              .catch(setError);
          }}
        >
          좁게 보기
        </button>
        <label>
          단위
          <select
            data-testid="renegotiation-unit"
            value={unit}
            onChange={(e) => {
              const value = e.target.value as AmountUnit;
              void preferences
                .put("AMT_UNIT", "*", value)
                .then(() => setUnit(value))
                .catch(setError);
            }}
          >
            {["원", "천원", "백만원"].map((u) => (
              <option
                data-testid={`renegotiation-unit-option-${Array.from(u, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                key={u}
              >
                {u}
              </option>
            ))}
          </select>
        </label>
      </div>
      {columnsPreference?.error && (
        <ErrorBanner message={columnsPreference.error.message} />
      )}
      <DataTable
        rows={data?.items ?? []}
        columns={columns}
        rowKey={(r) => r.assignmentId}
        onRowAction={(r) => openPerson(r.developerId)}
        testId="renegotiation-table"
        dense={dense}
        empty="조건에 맞는 재협상 대상이 없습니다"
        loading={loading}
        loadingMessage="조회 중입니다."
        page={data?.pagination.page ?? 1}
        size={data?.pagination.size ?? 50}
        total={data?.pagination.total ?? 0}
        onPageChange={(page) =>
          submit({ ...applied, page }, applied.increaseRate)
        }
        onSizeChange={(size) => {
          void (shared.savePageSize?.(size) ?? Promise.resolve())
            .then(() =>
              submit({ ...applied, page: 1, size }, applied.increaseRate),
            )
            .catch(setError);
        }}
        sort={{ id: applied.sort, direction: applied.direction }}
        onSortChange={(sort) =>
          submit(
            {
              ...applied,
              sort,
              page: 1,
              direction:
                applied.sort === sort && applied.direction === "ASC"
                  ? "DESC"
                  : "ASC",
            },
            applied.increaseRate,
          )
        }
        emptyAction={
          <button
            data-testid="renegotiation-empty-reset"
            type="button"
            onClick={reset}
          >
            조건 초기화
          </button>
        }
        footer={{
          label: "전체 합계",
          render: (column) =>
            column.id === "annualGain"
              ? amount(data?.summary.annualGain)
              : column.id === "name"
                ? "전체 합계"
                : null,
        }}
      />
      <details
        className="renegotiation-guide"
        data-testid="renegotiation-guide"
      >
        <summary data-testid="renegotiation-guide-summary">계산 방법</summary>
        <p>
          기준월 {data?.baseMonth ?? "–"} 기준입니다. 목표 이익률
          {targetPercent ? ` ${targetPercent}는` : "은"} 기준정보 › 운영 기준값에서 정한 값입니다.
        </p>
        <table>
          <thead>
            <tr>
              <th>항목</th>
              <th>계산식</th>
              <th>참고</th>
            </tr>
          </thead>
          <tbody>
            {guideColumns.map(({ id, label }) => (
                <tr
                  data-testid={`renegotiation-guide-row-${Array.from(id, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                  key={id}
                >
                  <td>
                    {id === "annualGain" ? annualGainLabel : label}
                  </td>
                  <td className="renegotiation-copy">{helpFor(label)}</td>
                  <td>{guideNotes[id]}</td>
                </tr>
              ))}
          </tbody>
        </table>
        <p>
          목표 단가까지 올릴 때 연 이익 = (목표 단가 - 현재 단가) × 계약
          투입량 × 12개월(원 단위 버림, 이미 목표 이상이면 0).
          앞으로의 연장, 급여 변동, 부가세는 미리 넣지 않습니다.
        </p>
        <p>
          예) [예시] 이저마진: 현재 단가 7,000,000원, 투입량 1.00, 월 원가 7,150,000원
        </p>
        <ul>
          <li>이익률 (7,000,000 - 7,150,000) ÷ 7,000,000 = -2.1%</li>
          <li>목표 단가 7,150,000 ÷ 1.00 ÷ 0.8 = 8,937,500 → 만 원 올림 8,940,000원</li>
          <li>필요 인상률 (8,940,000 - 7,000,000) ÷ 7,000,000 = 27.7%</li>
          <li>5% 올리면 연 이익 7,000,000 × 5% × 1.00 × 12 = 4,200,000원</li>
          <li>목표 단가까지 올리면 연 이익 (8,940,000 - 7,000,000) × 1.00 × 12 = 23,280,000원</li>
        </ul>
      </details>
      {person && canPerson && (
        <DeveloperDrawer id={person} onClose={() => setPerson(undefined)} />
      )}
      {columnDraft && (
        <Dialog
          open
          title="단가 재협상 표 열 설정"
          confirmLabel="설정 저장"
          pending={preferences.pending}
          onCancel={() => setColumnDraft(undefined)}
          onConfirm={async () => {
            const value = {
              rateRenegotiations: columnDraft,
            } as ScreenColumnPreferences;
            await preferences.put("COLS", "UI-32", value);
            setColumnDraft(undefined);
          }}
        >
          <div data-testid="renegotiation-columns-panel">
            {columnDraft.order
              .filter((id) => canCost || id !== "monthlyLaborCost")
              .map((id) => {
                const index = columnDraft.order.indexOf(id);
                const definition = renegotiationColumns.find(
                  (c) => c.id === id,
                )!;
                const move = (delta: number) => {
                  const order = [...columnDraft.order];
                  [order[index], order[index + delta]] = [
                    order[index + delta],
                    order[index],
                  ];
                  setColumnDraft({ ...columnDraft, order });
                };
                return (
                  <div
                    className="renegotiation-column"
                    key={id}
                    data-testid={`renegotiation-column-${index}`}
                  >
                    <span>
                      {definition.label}
                      {definition.required ? " (필수)" : ""}
                    </span>
                    <button
                      data-testid={`renegotiation-column-up-${index}`}
                      aria-label={`${definition.label} 위로`}
                      disabled={index === 0 || preferences.pending}
                      onClick={() => move(-1)}
                    >
                      ▲
                    </button>
                    <button
                      data-testid={`renegotiation-column-down-${index}`}
                      aria-label={`${definition.label} 아래로`}
                      disabled={index === 10 || preferences.pending}
                      onClick={() => move(1)}
                    >
                      ▼
                    </button>
                    <label>
                      <input
                        data-testid={`renegotiation-column-visible-${index}`}
                        type="checkbox"
                        disabled={definition.required || preferences.pending}
                        checked={!columnDraft.hidden.includes(id)}
                        onChange={() =>
                          setColumnDraft({
                            ...columnDraft,
                            hidden: columnDraft.hidden.includes(id)
                              ? columnDraft.hidden.filter((c) => c !== id)
                              : [...columnDraft.hidden, id],
                          })
                        }
                      />
                      보이기
                    </label>
                  </div>
                );
              })}
          </div>
        </Dialog>
      )}
    </div>
  );
}
