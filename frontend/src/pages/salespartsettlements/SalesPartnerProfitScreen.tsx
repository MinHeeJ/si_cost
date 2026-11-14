import { useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  formatAmount,
  formatDateTime,
  formatMm,
  formatRate,
  type AmountUnit,
} from "../../shared/format";
import { DeveloperDrawer } from "../developers/DeveloperDrawer";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { ScreenPeriodBar } from "../shared/ScreenPeriodBar";
import { useSharedScreen } from "../shared/SharedScreenContext";
import * as api from "./salesPartSettlementsApi";
import "./settlements.css";

export type SalesPartnerProfitScreenProps = {
  onPerson?(developerId: string): void;
  onPartStandard?(partId: string): void;
  renderActions?(data: api.SettlementResult, refresh: () => void): ReactNode;
  renderViews?(
    data: api.SettlementResult,
    query: api.SettlementQuery,
  ): ReactNode;
  renderCard?(data: api.SettlementResult, refresh: () => void): ReactNode;
  guardNavigation?(proceed: () => void): void;
  showMonthTable?: boolean;
};
const defaultQuery: api.SettlementQuery = {
  page: 1,
  size: 50,
  sort: "name",
  direction: "ASC",
};
const employeeLabels: Record<string, string> = {
  FT: "정규직",
  CT: "계약직",
  FL: "프리랜서",
  OS: "외주",
};
export function SalesPartnerProfitScreen({
  onPerson,
  onPartStandard,
  renderActions,
  renderViews,
  renderCard,
  guardNavigation,
  showMonthTable = true,
}: SalesPartnerProfitScreenProps = {}) {
  const navigate = useNavigate();
  const shared = useSharedScreen("UI-19");
  const [query, setQuery] = useState<api.SettlementQuery>({
    ...defaultQuery,
    size: shared.pageSize ?? 50,
  });
  const [tabs, setTabs] = useState<api.SettlementTabsResult>();
  const [selected, setSelected] = useState<string>();
  const [data, setData] = useState<api.SettlementResult>();
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const [reload, setReload] = useState(0),
    [notice, setNotice] = useState("");
  const [unit, setUnit] = useState<AmountUnit>("천원"),
    [person, setPerson] = useState<string>();
  const selectedRef = useRef(selected);
  selectedRef.current = selected;
  const refresh = () => setReload((n) => n + 1);
  useEffect(() => {
    const changed = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          value: { type: string; value?: string };
        }>
      ).detail;
      if (
        detail?.screenId === "UI-19" &&
        detail.value.type === "YEAR_MONTH" &&
        detail.value.value
      )
        setQuery((q) =>
          q.month === detail.value.value
            ? q
            : { ...q, month: detail.value.value, page: 1 },
        );
    };
    window.addEventListener("sicost:screen-period-changed", changed);
    return () =>
      window.removeEventListener("sicost:screen-period-changed", changed);
  }, []);
  useEffect(() => {
    const events = [
      "sicost:sales-parts-committed",
      "sicost:assignments-committed",
      "sicost:projects-committed",
      "sicost:developers-committed",
      "sicost:payroll-committed",
      "sicost:settlements-committed",
    ];
    const committed = () => setReload((n) => n + 1);
    events.forEach((name) => window.addEventListener(name, committed));
    return () =>
      events.forEach((name) => window.removeEventListener(name, committed));
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    setTabs(undefined);
    void api
      .getSalesPartSettlements(query.month, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        const ordered = [...result.parts].sort(
          (a, b) =>
            a.partName.localeCompare(b.partName, "ko") ||
            a.partId.localeCompare(b.partId),
        );
        const previous = selectedRef.current;
        const retained = ordered.find(
          (p) => p.partId === previous && p.eligible,
        );
        const next =
          retained ??
          ordered.find((p) => p.eligible) ??
          ordered.find((p) => p.historyOnly);
        setTabs({ ...result, parts: ordered });
        setSelected(next?.partId);
        setNotice(
          previous && previous !== next?.partId
            ? "선택한 파트가 이번 달 정산 대상이 아니어서 첫 유효 파트로 변경했습니다."
            : "",
        );
        if (!next) setLoading(false);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setError(reason);
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [query.month, reload]);
  useEffect(() => {
    if (!tabs || !selected) return;
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void api
      .getSalesPartSettlement(
        selected,
        { ...query, month: tabs.month },
        controller.signal,
      )
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [tabs, selected, query.page, query.size, query.sort, query.direction]);
  const openPerson = (id: string) => (onPerson ? onPerson(id) : setPerson(id));
  const apply = (next: Partial<api.SettlementQuery>) =>
    setQuery((q) => ({ ...q, ...next, page: 1 }));
  const guarded = (proceed: () => void) =>
    guardNavigation ? guardNavigation(proceed) : proceed();
  const amount = (value: string | null | undefined) => (
    <span
      className={value?.startsWith("-") ? "settlement-negative" : undefined}
    >
      {formatAmount(value, unit)}
    </span>
  );
  const moneyColumns = [
    ["unitPrice", "단가"],
    ["payAmount", "급여"],
    ["salesExpenseAmount", "공통관리비"],
    ["retirementAmount", "퇴직금"],
    ["costAmount", "비용 계 ⓘ"],
    ["profitAmount", "이익 ⓘ"],
    ["profitShareAmount", "이익금"],
  ] as const;
  const columns: Column<api.SettlementRow>[] = [
    {
      id: "name",
      label: "이름",
      sortable: true,
      render: (row) => (
        <button
          type="button"
          className="settlement-person"
          data-testid={`settlement-person-${row.developerId.toLowerCase().replace(/[^a-z0-9-]/g, "-")}`}
          onClick={(event) => {
            event.stopPropagation();
            openPerson(row.developerId);
          }}
        >
          {row.name}
          <small>{row.managementNo}</small>
        </button>
      ),
    },
    {
      id: "employeeType",
      label: "구분",
      sortable: true,
      render: (row) => employeeLabels[row.employeeType] ?? row.employeeType,
    },
    {
      id: "projects",
      label: "프로젝트",
      render: (row) => (
        <span title={row.legacyProjectText ?? row.projectNames.join("\n")}>
          {row.legacyProjectText ??
            `${row.projectNames[0] ?? "–"}${row.projectNames.length > 1 ? ` 외 ${row.projectNames.length - 1}건` : ""}`}
        </span>
      ),
    },
    {
      id: "mm",
      label: "MM",
      align: "right",
      sortable: true,
      render: (row) => formatMm(row.mm),
    },
    ...moneyColumns.map(
      ([id, label]): Column<api.SettlementRow> => ({
        id,
        label,
        align: "right",
        sortable: [
          "unitPrice",
          "payAmount",
          "costAmount",
          "profitAmount",
          "profitShareAmount",
        ].includes(id),
        render: (row) => (
          <span
            className={
              id === "payAmount" && row.basis.firstMonthMissingPay
                ? "settlement-negative"
                : undefined
            }
            title={
              id === "costAmount"
                ? "파트 비용 = 급여 + 공통관리비 + FT 퇴직금(급여÷12, 원 미만 절사)"
                : id === "profitAmount"
                  ? "이익 = 단가 − 파트 비용"
                  : row.basis.warnings.map((w) => w.message).join("\n")
            }
          >
            {amount(row[id])}
          </span>
        ),
      }),
    ),
    {
      id: "remark",
      label: "비고",
      render: (row) => (
        <div
          className="settlement-remark"
          onClick={(event) => event.stopPropagation()}
        >
          <span>{row.remark ?? "–"}</span>
          {row.basis.firstMonthMissingPay && (
            <p className="settlement-negative">
              ⚠ 월 급여 없음(시작 달 급여 0)
            </p>
          )}
          <details>
            <summary>계산 근거</summary>
            <p>급여 원천: {row.basis.paySource}</p>
            <p>
              최초 투입 {row.basis.firstInputDate ?? "–"} · 시작일수{" "}
              {row.basis.payableDays ?? "–"}/{row.basis.calendarDays ?? "–"} ·
              기본급 {amount(row.basis.basePayAmount)}
            </p>
            {row.basis.tkSources.map((source) => (
              <p key={source}>TK {source}</p>
            ))}
            {row.issues.map((issue, i) => (
              <p key={i}>{issue.message}</p>
            ))}
          </details>
        </div>
      ),
    },
  ];
  const selectedTab = tabs?.parts.find((p) => p.partId === selected);
  return (
    <SharedScreenFrame
      screenId="UI-19"
      periodBar={<ScreenPeriodBar screenId="UI-19" />}
    >
      <div
        className="settlement-screen"
        data-testid="sales-part-settlement-screen"
        data-screen-id="SCR-UI-19"
      >
        {notice && (
          <p role="status" className="settlement-banner info">
            {notice}
          </p>
        )}
        {tabs && tabs.eligibleCount === 0 && (
          <p className="settlement-empty">
            정산 대상 영업 파트가 없습니다. 본사는 정산 대상이 아닙니다
          </p>
        )}
        {tabs && (
          <div
            role="tablist"
            aria-label="영업 파트"
            className="settlement-tabs"
          >
            {tabs.parts.map((part) => (
              <button
                type="button"
                role="tab"
                key={part.partId}
                data-testid={`settlement-tab-${part.partId.toLowerCase().replace(/[^a-z0-9-]/g, "-")}`}
                aria-selected={part.partId === selected}
                onClick={() => {
                  const proceed = () => {
                    setSelected(part.partId);
                    apply({});
                  };
                  if (guardNavigation) guardNavigation(proceed);
                  else proceed();
                }}
              >
                {part.partName}
                {part.historyOnly ? " (확정 이력)" : ""}
              </button>
            ))}
          </div>
        )}
        {error != null && (
          <ErrorBanner
            message={`${error instanceof Error ? error.message : "정산 조회에 실패했습니다."}${error instanceof ApiError ? ` (${error.body.requestId})` : ""}`}
            onRetry={refresh}
          />
        )}
        {loading && <p role="status">정산 자료를 불러오는 중입니다.</p>}
        {tabs?.issues.map((issue, i) => (
          <p className="settlement-banner warn" role="alert" key={i}>
            {issue.message}
          </p>
        ))}
        {data && (
          <>
            <div
              className="settlement-summary"
              data-testid="settlement-summary"
            >
              <article>
                <h2>파트장</h2>
                <strong>{data.standard.leaderName ?? "–"}</strong>
                <p>
                  이익 배분율 {formatRate(data.standard.distributionRate)} ·
                  공통관리비{" "}
                  {formatAmount(data.standard.salesExpenseAmount, "원")}원
                </p>
                <button
                  type="button"
                  data-testid="settlement-standard-button"
                  onClick={() =>
                    onPartStandard
                      ? onPartStandard(data.partId)
                      : navigate(
                          `/UI-15?partId=${encodeURIComponent(data.partId)}`,
                        )
                  }
                >
                  파트 기준 확인
                </button>
              </article>
              <article>
                <h2>인원</h2>
                <strong>{data.summary.personCount}명</strong>
                <p>이번 달 투입 인력</p>
              </article>
              <article>
                <h2>이익 합계</h2>
                <strong>{amount(data.summary.profitAmount)}</strong>
                <p>단가 − (급여 + 공통관리비 + 퇴직금)</p>
              </article>
              <article>
                <h2>파트 이익금</h2>
                <strong>{amount(data.summary.partProfitAmount)}</strong>
                <p>
                  인력별 이익금 {amount(data.summary.personProfitShareAmount)} −
                  법인카드 {amount(data.cardAmount)}
                </p>
              </article>
            </div>
            <div
              className={`settlement-banner ${data.status === "WORK" ? "info" : "lock"}`}
            >
              <StatusBadge tone={data.status === "WORK" ? "info" : "lock"}>
                {data.status === "WORK"
                  ? "작업중"
                  : data.status === "AUTO"
                    ? "마감자동"
                    : "수동 확정"}
              </StatusBadge>
              {data.status !== "WORK" && (
                <span>
                  {data.confirmedByName ?? data.confirmedBy ?? "–"} ·{" "}
                  {formatDateTime(data.confirmedAt)}
                </span>
              )}
              {data.readOnlyReasons.map((reason) => (
                <span key={reason}>{reason}</span>
              ))}
            </div>
            {renderCard ? (
              renderCard(data, refresh)
            ) : (
              <div className="settlement-card">
                <span>법인카드 사용액</span>
                <strong>{formatAmount(data.cardAmount, "원")} 원</strong>
                <p>
                  파트 이익금에서 뺍니다(엑셀 ‘원가표’의 ‘법카 사용 별도’). 회사
                  카드와 별도입니다.
                </p>
              </div>
            )}
            {renderActions?.(data, refresh)}
            {data.issues
              .filter((issue) => !issue.developerId)
              .map((issue, i) => (
                <p
                  role="alert"
                  className={`settlement-banner ${issue.blocking ? "warn" : "info"}`}
                  key={i}
                >
                  {issue.message}
                </p>
              ))}
            <div className="settlement-toolbar" hidden={!showMonthTable}>
              <h2>이번 달</h2>
              <span>
                {data.month} · {selectedTab?.partName} ·{" "}
                {data.summary.personCount}명
              </span>
              <label>
                단위{" "}
                <select
                  data-testid="settlement-unit-select"
                  value={unit}
                  onChange={(event) =>
                    setUnit(event.target.value as AmountUnit)
                  }
                >
                  <option>원</option>
                  <option>천원</option>
                  <option>백만원</option>
                </select>
              </label>
            </div>
            <div
              data-testid="settlement-month-panel"
              role="tabpanel"
              hidden={!showMonthTable}
            >
              <DataTable
                testId="settlement-table"
                rows={data.items}
                rowKey={(row) => row.developerId}
                columns={columns}
                loading={loading}
                page={data.pagination.page}
                size={query.size}
                total={data.pagination.total}
                onPageChange={(page) =>
                  guarded(() => setQuery((q) => ({ ...q, page })))
                }
                onSizeChange={(size) =>
                  guarded(() => {
                    apply({ size });
                    void shared.savePageSize?.(size).catch(setError);
                  })
                }
                sort={{ id: query.sort, direction: query.direction }}
                onSortChange={(id) =>
                  guarded(() =>
                    apply({
                      sort: id as api.SettlementQuery["sort"],
                      direction:
                        query.sort === id && query.direction === "ASC"
                          ? "DESC"
                          : "ASC",
                    }),
                  )
                }
                onRowAction={(row) => openPerson(row.developerId)}
                empty={
                  data.issues.some((i) => i.code === "CLOSED_SNAPSHOT_MISSING")
                    ? "마감 정산 자료 미확인"
                    : "이번 달 투입 인력이 없습니다."
                }
                footer={{
                  label: "전체 합계",
                  render: (column) =>
                    column.id === "name"
                      ? "전체 합계"
                      : column.id === "mm"
                        ? formatMm(data.totals.mm)
                        : column.id === "profitShareAmount"
                          ? amount(data.totals.personProfitShareAmount)
                          : moneyColumns.some(([id]) => id === column.id)
                            ? amount(
                                data.totals[
                                  column.id as keyof api.SettlementTotals
                                ] as string | null,
                              )
                            : "–",
                }}
              />
            </div>
            {renderViews?.(data, query)}
            <details className="settlement-guide">
              <summary>계산 방법 펼침</summary>
              <p>
                MM: 비내부 실제 월 투입 합계. MO/DP 단가: 귀속월 매출 합계.
                FREE/PRE/LOSE는 단가 0입니다.
              </p>
              <p>
                최초 시작월만 기본급 × 포함일수 ÷ 달력일수(원 미만 절사). 그
                밖에는 업로드 급여, 외주는 공급액 전액이며 미업로드는 유효
                기본급 예정입니다.
              </p>
              <p>
                공통관리비: 외주 0, 나머지 월 한 번 전액. 퇴직금: 정규직만 급여
                ÷ 12(원 미만 절사).
              </p>
              <p>
                파트 비용 = 급여 + 공통관리비 + 퇴직금. 이익 = 단가 − 비용.
                인력별 이익금 = 이익 × 배분율 ÷ 100(음수도 0쪽 절사). 파트
                이익금 = 전체 인력 이익금 합 − 카드.
              </p>
              <p>
                TK는 PLAN &gt; CTRT &gt; EST의 실제 인력 행을 사용합니다. 사람
                행이 없을 때만 노동비 × 계약금액 ÷ 저장 예산이며 예산이 0
                이하이면 미확인입니다.
              </p>
              <p>
                미확인은 –이며 0과 다릅니다. 확정 사본의 저장되지 않은 당시
                근거는 현재 자료로 복원하지 않습니다.
              </p>
            </details>
          </>
        )}
        {person && (
          <DeveloperDrawer id={person} onClose={() => setPerson(undefined)} />
        )}
      </div>
    </SharedScreenFrame>
  );
}
