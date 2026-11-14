import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { FilterBar } from "../../shared/FilterBar";
import { ApiError } from "../../shared/api";
import { formatDateTime, type AmountUnit } from "../../shared/format";
import { DeveloperDrawer } from "../developers/DeveloperDrawer";
import { usePreferences } from "../me/usePreferences";
import type { ColumnView, ScreenColumnPreferences } from "../me/preferencesApi";
import { ProfitabilityValue } from "../profitability/ProfitabilityValue";
import { getProjects } from "../projects/projectsApi";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import * as api from "./rateRenegotiationsApi";
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
const help: Record<string, string> = {
  renewalDate:
    "투입 배정 종료일 − 서버 오늘. D-0 포함, 0~30일 빨강·31~92일 주황",
  contractMm:
    "표는 기준월 계약MM k. 월 이익률·목표 단가는 월MM m, 연 증가는 계약MM k 사용",
  monthlyLaborCost:
    "동일 DEV/PROJ/ASGN의 LABOR_ALLOC_AMT만 사용. 회사보험 포함 노동비, 팀비·경비 제외",
  marginRate:
    "(기준월 매출 R − 노동비 C) ÷ R × 100. 필터는 반올림 전 엄격 미만",
  targetUnitPrice: "C ÷ 월MM m ÷ (1 − 목표율 T/100), 만 원 단위 올림",
  requiredIncreaseRate: "max(0, (올림된 목표 단가 V − 현재 단가 U) ÷ U × 100)",
  annualGain:
    "DOWN(U × 가정율 r/100 × 계약MM k × 12). 같은 계약MM으로 1년 지속 투입 가정",
};
const emptyView = (): ColumnView => ({
  order: renegotiationColumns.map((c) => c.id),
  hidden: [],
});
export function RateRenegotiationsScreen({
  onPerson,
}: { onPerson?(id: string): void } = {}) {
  const preferences = usePreferences();
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
  const [columnDraft, setColumnDraft] = useState<ColumnView>();
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
  const openPerson = onPerson ?? setPerson;
  const employeeLabel = (code: string) =>
    employees.find((e) => e.code === code)?.name ?? code;
  const amount = (value: string | null | undefined) => (
    <ProfitabilityValue value={value} unit={unit} />
  );
  const columns: Column<api.RenegotiationRow>[] = view.order
    .filter((id) => !view.hidden.includes(id))
    .map((id) => {
      const definition = renegotiationColumns.find((c) => c.id === id)!;
      const label =
        id === "annualGain"
          ? `${data?.appliedIncreaseRate ?? applied.increaseRate}% 올리면 연 이익`
          : id === "targetUnitPrice"
            ? `목표 단가 (${data?.targetMarginRate ?? "–"}%)`
            : definition.label;
      return {
        id,
        sortable: id !== "employeeType",
        align: ["name", "employeeType", "projectName", "renewalDate"].includes(
          id,
        )
          ? "left"
          : "right",
        label: (
          <span title={help[id]}>
            {label}
            {help[id] && " ⓘ"}
          </span>
        ),
        render: (row) => {
          const marker = row.assignmentId
            .toLowerCase()
            .replace(/[^a-z0-9-]/g, "-");
          if (id === "name")
            return (
              <button
                type="button"
                className="sicost-link"
                data-testid={`renegotiation-person-${marker}`}
                onClick={() => openPerson(row.developerId)}
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
                  data-testid={`renegotiation-project-${marker}`}
                  to={`/UI-01?${new URLSearchParams({ projectId: row.projectId, tab: "basic" })}`}
                >
                  {row.projectName}
                </Link>
                <Link
                  className="renegotiation-sub"
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
              <span
                title={`계약MM k=${row.contractMm} / 기준월 월MM m=${row.monthlyMm}`}
              >
                <ProfitabilityValue value={row.contractMm} kind="mm" />
              </span>
            );
          const value = row[id as keyof api.RenegotiationRow] as string | null;
          return (
            <span title={row.issues.map((i) => i.message).join(" · ")}>
              <ProfitabilityValue
                value={value}
                kind={
                  id === "marginRate" || id === "requiredIncreaseRate"
                    ? "rate"
                    : "amount"
                }
                unit={unit}
              />
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
      <div className="renegotiation-toolbar">
        <span>
          기준월 {data?.baseMonth ?? "–"} · 계산시각{" "}
          {formatDateTime(data?.calculatedAt)}
        </span>
        <button
          className="sicost-button renegotiation-download"
          data-testid="renegotiation-download"
          type="button"
          disabled={!data || loading || exporting}
          onClick={async () => {
            if (exporting) return;
            setExporting(true);
            setExportError("");
            try {
              await api.downloadRenegotiations(
                applied,
                view.order.filter((id) => !view.hidden.includes(id)),
                unit,
              );
            } catch (e) {
              if (alive.current)
                setExportError(
                  e instanceof Error ? e.message : "다운로드에 실패했습니다.",
                );
            } finally {
              if (alive.current) setExporting(false);
            }
          }}
        >
          {exporting ? "엑셀 생성 중…" : "엑셀 다운로드"}
        </button>
      </div>
      <div className="renegotiation-summary">
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
          <small>전체 대상 매출 가중 평균</small>
        </article>
        <article>
          <h2>가정 연 증가</h2>
          <strong>{amount(data?.summary.annualGain)}</strong>
          <small>
            가정 {data?.appliedIncreaseRate ?? "–"}% 적용 · {unit}
          </small>
        </article>
        <article>
          <h2>목표 연 증가</h2>
          <strong>{amount(data?.summary.targetAnnualGain)}</strong>
          <small>
            목표 이익률 {data?.targetMarginRate ?? "–"}% · {unit}
          </small>
        </article>
      </div>
      <form
        className="renegotiation-assumption"
        onSubmit={(e) => {
          e.preventDefault();
          submit({ ...applied, page: 1 });
        }}
      >
        <span>인상률 가정 · 같은 계약MM으로 1년 지속 투입 시 효과</span>
        <label>
          인상률 %{" "}
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
        </label>
        <button
          data-testid="renegotiation-recalculate"
          type="submit"
          disabled={loading}
        >
          재계산
        </button>
        <small>
          적용값 {data?.appliedIncreaseRate ?? "–"}% · DB에 저장하지 않습니다.
        </small>
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
          <select
            data-testid="renegotiation-margin"
            value={draft.marginBelow}
            onChange={(e) =>
              setDraft({ ...draft, marginBelow: e.target.value })
            }
          >
            <option value="ALL">전체</option>
            {["10", "15", "20"].map((value) => (
              <option key={value} value={value}>
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
              setDraft({ ...draft, employeeType: e.target.value || undefined })
            }
          >
            <option value="">전체</option>
            {employees.map((e) => (
              <option key={e.code} value={e.code}>
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
              <option key={p.id} value={p.id}>
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
      {loading && (
        <p role="status">
          {data ? "재계산 중… 직전 결과입니다." : "불러오는 중…"}
        </p>
      )}
      {data?.issues.map((i, index) => (
        <div
          className="sicost-banner sicost-tone-warn"
          role="note"
          key={`${i.code}-${index}`}
        >
          {i.message}
        </div>
      ))}
      {!!data?.unevaluatedCount && (
        <details data-testid="renegotiation-unevaluated">
          <summary>
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
              · {i.projectId} · {i.assignmentId} · {i.field} · {i.source}
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
          onClick={() => setDense(!dense)}
        >
          좁게 보기
        </button>
        <label>
          단위
          <select
            data-testid="renegotiation-unit"
            value={unit}
            onChange={(e) => setUnit(e.target.value as AmountUnit)}
          >
            {["원", "천원", "백만원"].map((u) => (
              <option key={u}>{u}</option>
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
        testId="renegotiation-table"
        dense={dense}
        empty="조건에 맞는 재협상 대상이 없습니다"
        loading={loading && !data}
        page={data?.pagination.page ?? 1}
        size={data?.pagination.size ?? 50}
        total={data?.pagination.total ?? 0}
        onPageChange={(page) =>
          submit({ ...applied, page }, applied.increaseRate)
        }
        onSizeChange={(size) =>
          submit({ ...applied, page: 1, size }, applied.increaseRate)
        }
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
      <p>
        협상 반영은 UI-12 해당 배정의 변경 기록 → 단가 변경을 이용하세요. 이
        화면에서는 단가 적용·협상 저장·상태 변경을 하지 않습니다.
      </p>
      <details
        className="renegotiation-guide"
        data-testid="renegotiation-guide"
      >
        <summary>계산 방법</summary>
        <p>
          기준월 {data?.baseMonth ?? "–"} · 목표율은 기준월 유효
          TGT_MRGN_RT입니다. 월 계산은 월MM m, 연 증가는 계약MM k를 사용합니다.
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
            {Object.entries(help)
              .filter(([id]) => id !== "contractMm")
              .map(([id, text]) => (
                <tr key={id}>
                  <td>
                    {renegotiationColumns.find((c) => c.id === id)?.label}
                  </td>
                  <td>{text}</td>
                  <td>
                    {id === "targetUnitPrice"
                      ? "만 원 단위 올림"
                      : "기준월 원천"}
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
        <p>
          목표 연 증가 = DOWN(max(0,V−U) × k × 12). 미래 연장·급여·부가세를
          추정하지 않습니다.
        </p>
        <p>
          계산 예시: U 6,000,000원 · m/k 1.00 · C 5,400,000원 · T 20% → 현재
          10.0%, 목표 6,750,000원, 필요 12.5%, 목표 연 9,000,000원, 가정 5% 연
          3,600,000원.
        </p>
      </details>
      {person && (
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
            {columnDraft.order.map((id, index) => {
              const definition = renegotiationColumns.find((c) => c.id === id)!;
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
