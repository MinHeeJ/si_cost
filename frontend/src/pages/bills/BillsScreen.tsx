import { ScreenLoading } from "../../shared/ScreenLoading";
import { usePermission } from "../../shared/permission/PermissionBoundary";
import { FieldHelp } from "../../shared/FieldHelp";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { KpiStrip } from "../../shared/Surface";
import { FilterBar } from "../../shared/FilterBar";
import { BillingReviewDownloadButton } from "../apiops/BillingReviewDownloadButton";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type Key,
  type ReactNode,
} from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { FeedbackNotice, FloatingToast } from "../../shared/Feedback";
import {
  getPeriodContext,
  getSharedPreferences,
  type PeriodContext,
  type PeriodValue,
} from "../shared/sharedApi";
import { BillProjectPicker } from "./BillProjectPicker";
import { BillFormDialog, nextDue } from "./BillFormDialog";
import { BillingChecksPanel } from "../apiops/BillingChecksPanel";
import { ReceivablesPanel } from "../apiops/ReceivablesPanel";
import type { BillingCheckRemedy } from "../apiops/billingReviewApi";
import { ReceiptBatchAction } from "../receiptsops/ReceiptBatchAction";
import {
  BillDetailDrawer,
  BillError,
  BillStatus,
  billMoney,
  billStatusNames,
  type AmountUnit,
  type BillDetailProps,
} from "./BillDetailDrawer";
import * as api from "./billsApi";
import "./bills.css";

export type BillsActionsContext = {
  query: api.Query;
  selected: Key[];
  amountUnit: AmountUnit;
  readonly: boolean;
  refresh(): Promise<void>;
};
export type BillsScreenProps = {
  initialBillId?: string;
  initialMonth?: string;
  initialSection?: string;
  initialMissingInvoiceNo?: boolean;
  onDirtyChange?(dirty: boolean): void;
  renderActions?(context: BillsActionsContext): ReactNode;
  renderTabs?(context: BillsActionsContext): ReactNode;
  renderDetailActions?: BillDetailProps["renderActions"];
  renderReceiptActions?: BillDetailProps["renderReceiptActions"];
};
export function BillsScreen(props: BillsScreenProps = {}) {
  const [period, setPeriod] = useState<PeriodContext>(),
    [year, setYear] = useState<number>(),
    [error, setError] = useState<unknown>(),
    [retry, setRetry] = useState(0);
  const [unit, setUnit] = useState<AmountUnit>("천원"),
    [size, setSize] = useState<20 | 50 | 100>(50),
    [dense, setDense] = useState(false);
  useEffect(() => {
    let live = true;
    void Promise.all([getPeriodContext(), getSharedPreferences()])
      .then(([c, p]) => {
        if (!live) return;
        setPeriod(c);
        const pref = p.items.find(
          (v) => v.type === "PERIOD" && v.key === "UI-13",
        )?.value as { type?: string; value?: string } | undefined;
        const current = Number(c.currentMonth.slice(0, 4)),
          min = Number(c.cashBaseMonth.slice(0, 4));
        setYear(
          (old) =>
            old ??
            (props.initialMonth
              ? Number(props.initialMonth.slice(0, 4))
              : undefined) ??
            (pref?.type === "YEAR" && /^\d{4}$/.test(pref.value ?? "")
              ? Math.max(min, Math.min(Number(pref?.value), current + 1))
              : current),
        );
        const amount = p.items.find(
          (v) => v.type === "AMT_UNIT" && v.key === "*",
        )?.value;
        if (amount === "원" || amount === "천원" || amount === "백만원")
          setUnit(amount);
        const pageSize = p.items.find(
          (v) => v.type === "PAGE_SIZE" && v.key === "*",
        )?.value;
        if (pageSize === 20 || pageSize === 50 || pageSize === 100)
          setSize(pageSize);
        setDense(
          p.items.find((v) => v.type === "DENSE" && v.key === "*")?.value ===
            "좁게",
        );
        setError(undefined);
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, [retry]);
  useEffect(() => {
    const changed = (event: Event) => {
      const d = (
        event as CustomEvent<{
          screenId: string;
          value: PeriodValue;
          context: PeriodContext;
        }>
      ).detail;
      if (d?.screenId === "UI-13" && d.value.type === "YEAR") {
        setPeriod(d.context);
        setYear(Number(d.value.value));
      }
    };
    window.addEventListener("sicost:screen-period-changed", changed);
    return () =>
      window.removeEventListener("sicost:screen-period-changed", changed);
  }, []);
  return (
    <section
      data-testid="bills-screen"
      data-screen-id="SCR-UI-13"
      className="bills-screen"
    >
      <BillError error={error} retry={() => setRetry((v) => v + 1)} />
      {period && year !== undefined ? (
        <BillsContent
          key={year}
          {...props}
          period={period}
          year={year}
          initialSize={size}
          unit={unit}
          setUnit={setUnit}
          dense={dense}
        />
      ) : (
        !error && <ScreenLoading message="청구 기간을 불러오는 중입니다." />
      )}
    </section>
  );
}
function BillsContent({
  period,
  year,
  initialSize,
  unit,
  setUnit,
  dense,
  onDirtyChange,
  renderActions,
  renderTabs,
  renderDetailActions,
  renderReceiptActions,
  initialMonth,
  initialSection,
  initialMissingInvoiceNo,
  initialBillId,
}: BillsScreenProps & {
  period: PeriodContext;
  year: number;
  initialSize: 20 | 50 | 100;
  unit: AmountUnit;
  setUnit(value: AmountUnit): void;
  dense: boolean;
}) {
  const currentYear = Number(period.currentMonth.slice(0, 4)),
    readonly = year < currentYear;
  const canRegister = usePermission("UI-13", "REG");
  const canUpdate = usePermission("UI-13", "UPD");
  const [mobile, setMobile] = useState(
    () => window.matchMedia?.("(max-width: 767px)")?.matches ?? false,
  );
  const [mobileRows, setMobileRows] = useState<api.Bill[]>([]);
  const [tab, setTab] = useState<"list" | "checks" | "receivables">(
    initialSection === "checks" || initialSection === "receivables"
      ? initialSection
      : "list",
  );
  const [checkCount, setCheckCount] = useState<number>();
  const [receivableCount, setReceivableCount] = useState<number>();
  const [query, setQuery] = useState<api.Query>({
    year,
    ...(initialMonth?.startsWith(`${year}-`) ? { month: initialMonth } : {}),
    ...(initialMissingInvoiceNo ? { missingInvoiceNo: true } : {}),
    includeCarryover: year === currentYear,
    page: 1,
    size: mobile ? 20 : initialSize,
  });
  const [data, setData] = useState<api.BillsPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>(),
    [summary, setSummary] = useState<api.Summary>(),
    [summaryError, setSummaryError] = useState<unknown>(),
    [summaryLoading, setSummaryLoading] = useState(true);
  const [selected, setSelectedKeys] = useState<Key[]>([]),
    [allMatching, setAllMatching] = useState(false),
    [detailId, setDetailId] = useState<string | undefined>(initialBillId),
    [form, setForm] = useState<{ bill?: api.Bill; initial?: api.CreateBill }>(),
    [refreshKey, setRefreshKey] = useState(0);
  useEffect(() => {
    if (initialBillId) setDetailId(initialBillId);
  }, [initialBillId]);
  const [message, setMessage] = useState(""),
    [adjustmentToast, setAdjustmentToast] = useState<string>(),
    [adjustmentRefreshFailed, setAdjustmentRefreshFailed] = useState(false),
    [actionError, setActionError] = useState<unknown>(),
    [pending, setPending] = useState(false);
  const [projectSearch, setProjectSearch] = useState("");
  const [pickedProjectId, setPickedProjectId] = useState<string>();
  const [projectOpen, setProjectOpen] = useState(false);
  const generation = useRef(0),
    summaryGeneration = useRef(0);
  useEffect(() => {
    const media = window.matchMedia?.("(max-width: 767px)");
    if (!media) return;
    const changed = () => {
      setMobile(media.matches);
      setQuery((q) => ({
        ...q,
        page: 1,
        size: media.matches ? 20 : initialSize,
      }));
      setSelected([]);
    };
    media.addEventListener("change", changed);
    return () => media.removeEventListener("change", changed);
  }, [initialSize]);
  const load = useCallback(async () => {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const page = await api.getBills(query);
      if (generation.current === version) {
        setData(page);
        setMobileRows((old) =>
          query.page === 1
            ? page.items
            : [
                ...old,
                ...page.items.filter((b) => !old.some((v) => v.id === b.id)),
              ],
        );
      }
    } catch (e) {
      if (generation.current === version) setError(e);
      throw e;
    } finally {
      if (generation.current === version) setLoading(false);
    }
  }, [query]);
  const loadSummary = useCallback(async () => {
    const version = ++summaryGeneration.current;
    setSummaryLoading(true);
    setSummaryError(undefined);
    try {
      const value = await api.getSummary(year, query.includeCarryover);
      if (summaryGeneration.current === version) setSummary(value);
    } catch (e) {
      if (summaryGeneration.current === version) setSummaryError(e);
      throw e;
    } finally {
      if (summaryGeneration.current === version) setSummaryLoading(false);
    }
  }, [year, query.includeCarryover]);
  useEffect(() => {
    void load().catch(() => {});
    return () => {
      generation.current++;
    };
  }, [load]);
  useEffect(() => {
    void loadSummary().catch(() => {});
    return () => {
      summaryGeneration.current++;
    };
  }, [loadSummary]);
  async function refresh() {
    setRefreshKey((v) => v + 1);
    await Promise.all([load(), loadSummary()]);
  }
  function conditions(patch: Partial<api.Query>) {
    setQuery((old) => ({ ...old, ...patch, page: 1 }));
    setSelected([]);
  }
  async function saved(result: api.Mutation) {
    setForm(undefined);
    setDetailId(result.bill.id);
    const adjustment = form?.initial?.closedMonthAdjustment;
    setAdjustmentRefreshFailed(false);
    setMessage(
      adjustment ? "" : result.changed
        ? `저장했습니다. ${result.warnings.join(" ")}`
        : "바뀐 내용이 없습니다.",
    );
    if (adjustment) setAdjustmentToast(result.bill.id);
    try {
      await refresh();
    } catch {
      if (adjustment) setAdjustmentRefreshFailed(true);
      else setMessage("저장됐지만 다시 불러오지 못했습니다. 재조회해 주세요.");
    }
  }
  async function copy(bill: api.Bill) {
    if (pending) return;
    setPending(true);
    setActionError(undefined);
    try {
      const draft = await api.getCopyDraft(bill.id, year);
      setForm({
        initial: {
          year,
          projectId: draft.projectId,
          billingMonth: draft.billingMonth,
          supplyAmount: draft.supplyAmount ?? "",
          vatRate: draft.vatRate,
          billingDate: draft.billingDate,
          dueDate: draft.dueDate,
          taxInvoiceNo: null,
          copyContext: {
            sourceBillId: draft.sourceBillId,
            expectedRevision: draft.expectedRevision,
          },
        },
      });
    } catch (e) {
      setActionError(e);
    } finally {
      setPending(false);
    }
  }
  async function next(bill: api.Bill) {
    if (pending) return;
    setPending(true);
    setActionError(undefined);
    try {
      const result = await api.nextMonth(bill, year);
      await saved(result);
    } catch (e) {
      setActionError(e);
    } finally {
      setPending(false);
    }
  }
  async function remedy(value: BillingCheckRemedy) {
    const adjustment = value.mode === "CLOSED_MONTH_ADJUSTMENT";
    const creating = value.mode === "CREATE" || adjustment;
    if (
      pending ||
      readonly ||
      (creating ? !canRegister : !canUpdate) ||
      !value.proposedSupplyAmount
    ) return;
    setPending(true);
    setActionError(undefined);
    try {
      if (value.mode === "UPDATE" && value.billId) {
        const detail = await api.getBill(value.billId, year);
        setForm({
          bill: { ...detail.bill, supplyAmount: value.proposedSupplyAmount },
          initial: { ...detail.bill, year, checkContext: value.checkContext },
        });
      } else if (creating) {
        const draft = await api.getEstimate(value.projectId, value.month);
        setForm({
          initial: {
            year,
            projectId: value.projectId,
            billingMonth: value.month,
            supplyAmount: value.proposedSupplyAmount,
            vatRate: draft.vatRate ?? "",
            billingDate: draft.defaultBillingDate,
            dueDate: adjustment && draft.defaultDueDate < draft.defaultBillingDate
              ? nextDue(draft.defaultBillingDate.slice(0, 7))
              : draft.defaultDueDate,
            taxInvoiceNo: null,
            checkContext: value.checkContext,
            ...(adjustment ? { closedMonthAdjustment: true } : {}),
          },
        });
      }
    } catch (e) {
      setActionError(e);
    } finally {
      setPending(false);
    }
  }
  const columns: Column<api.Bill>[] = [
    {
      id: "id",
      label: "청구번호",
      sortable: true,
      render: (b) => (
        <div data-testid={`bill-row-${b.id.toLowerCase()}`}>
          <button
            type="button"
            className="bill-link"
            data-testid={`bill-open-${b.id.toLowerCase()}`}
            onClick={() => setDetailId(b.id)}
          >
            {b.id}
          </button>
          <small>
            {b.billingMonth}{" "}
            {b.carryover && (
              <span className="bill-badge sicost-tone-info">이월</span>
            )}
          </small>
        </div>
      ),
    },
    {
      id: "projectName",
      label: "프로젝트",
      sortable: true,
      render: (b) => b.projectName,
    },
    { id: "order", label: "차수", sortable: true, render: (b) => b.order },
    {
      id: "billingDate",
      label: "청구일",
      sortable: true,
      render: (b) => b.billingDate,
    },
    ...(
      [
        "supplyAmount",
        "vatAmount",
        "totalAmount",
        "receiptAmount",
        "unpaidAmount",
      ] as const
    ).map((id, i) => ({
      id,
      label: ["공급가", "부가세", "합계", "입금액", "미수금"][i],
      sortable: true,
      align: "right" as const,
      render: (b: api.Bill) => billMoney(b[id], unit),
    })),
    {
      id: "dueDate",
      label: "입금 예정일",
      sortable: true,
      render: (b) => b.dueDate,
    },
    {
      id: "status",
      label: "상태",
      sortable: true,
      align: "center",
      render: (b) => <BillStatus bill={b} />,
    },
    {
      id: "dunning",
      label: "미수 알림",
      sortable: true,
      render: (b) =>
        b.dunning ? (
          <span
            className={`bill-badge sicost-tone-${b.dunning.step >= 2 ? "bad" : "warn"}`}
          >
            {b.dunning.step
              ? `${b.dunning.step}단계 · ${b.dunning.elapsedDays}일`
              : "알림 전"}
          </span>
        ) : b.status === "PAID" || b.cancelled ? (
          "–"
        ) : (
          "설정 확인 필요"
        ),
    },
    {
      id: "quickActions",
      label: "빠른 청구",
      render: (b) => (
        <span title={b.quickActions.reason ?? undefined}>
          {canRegister && !readonly && b.quickActions.copyAllowed ? (
            <>
              <button
                type="button"
                data-testid={`bill-copy-${b.id.toLowerCase()}`}
                disabled={pending}
                onClick={() => {
                  void copy(b);
                }}
              >
                고쳐서 청구
              </button>
              {b.quickActions.nextMonthAllowed && (
                <button
                  type="button"
                  data-testid={`bill-next-${b.id.toLowerCase()}`}
                  disabled={pending}
                  onClick={() => {
                    void next(b);
                  }}
                >
                  다음 달 청구
                </button>
              )}
            </>
          ) : (
            <span
              className="bill-quick-unavailable"
              data-testid={`bill-quick-reason-${b.id.toLowerCase()}`}
            >
              {b.quickActions.reason ? (
                <>
                  <span aria-hidden="true">–</span>
                  <small>{b.quickActions.reason}</small>
                </>
              ) : (
                "–"
              )}
            </span>
          )}
        </span>
      ),
    },
  ];
  const setSelected = useCallback((keys: Key[]) => {
    setSelectedKeys(keys);
    setAllMatching(false);
  }, []);
  const actionContext: BillsActionsContext = {
    query,
    selected,
    amountUnit: unit,
    readonly,
    refresh,
  };
  return (
    <>
      <ScreenTitleActions>
        <header className="bill-heading">
          <span>오늘 {period.currentDate}</span>
          <label htmlFor="bill-unit">단위</label>
          <select
            id="bill-unit"
            data-testid="bill-unit"
            value={unit}
            onChange={(e) => setUnit(e.target.value as AmountUnit)}
          >
            <option>원</option>
            <option>천원</option>
            <option>백만원</option>
          </select>
          {canRegister && (
            <button
              type="button"
              className="sicost-primary"
              data-testid="bill-create"
              disabled={readonly || pending}
              onClick={() => setForm({})}
            >
              청구 등록
            </button>
          )}
        </header>
      </ScreenTitleActions>
      {readonly && (
        <p className="sicost-banner sicost-tone-lock">
          지난 연도는 조회만 할 수 있습니다.
        </p>
      )}
      <BillError
        error={summaryError}
        retry={() => {
          void loadSummary().catch(() => {});
        }}
      />
      <KpiStrip
        as="section"
        data-testid="bills-summary"
        className="bill-summary"
        aria-busy={summaryLoading}
      >
        {summaryLoading && <ScreenLoading message="요약을 불러오는 중입니다." />}
        {summary && (
          <>
            {(
              [
                ["청구 총액", summary.billed],
                ["이번달 청구", summary.currentMonth],
                ["미수금", summary.unpaid],
              ] as const
            ).map(([label, card]) => (
              <article
                key={label}
                data-testid={`bill-summary-card-${({ "청구 총액": "billed", "이번달 청구": "current-month", 미수금: "unpaid" } as const)[label]}`}
              >
                <span>{label}</span>
                <strong>
                  {billMoney(card.amount, unit)} {unit}
                </strong>
                <small>
                  {card.count}건 · {card.scope}
                </small>
              </article>
            ))}
            <article>
              <span className="bill-summary-label">
                연체
                <HelpTooltip label="연체" iconOnly>
                  입금 예정일이 지났고 미수금이 남은 청구 건수입니다.{" "}
                  {summary.includeCarryover
                    ? "이전 연도에서 이월된 연체도 포함합니다."
                    : "선택 연도에 속한 청구만 집계합니다."}
                  {year === currentYear &&
                    " 미수 알림 탭은 ‘이월 미수 포함’ 설정과 관계없이 이월분까지 포함한 전체 연체를 보여 줍니다."}
                </HelpTooltip>
              </span>
              <strong>{summary.lateCount}건</strong>
              <small>{summary.lateScope}</small>
            </article>
          </>
        )}
      </KpiStrip>
      {year === currentYear &&
        (renderTabs ? (
          renderTabs(actionContext)
        ) : (
          <>
            <SegmentedTabList
              as="nav"
              role="tablist"
              aria-label="청구 업무 탭"
              className="bill-tabs"
            >
              <button
                type="button"
                role="tab"
                data-testid="billing-list-tab"
                aria-selected={tab === "list"}
                onClick={() => setTab("list")}
              >
                청구 목록
              </button>
              <button
                type="button"
                role="tab"
                data-testid="billing-checks-tab"
                aria-selected={tab === "checks"}
                onClick={() => setTab("checks")}
              >
                청구 누락 점검 ({checkCount ?? "–"})
              </button>
              <button
                type="button"
                role="tab"
                data-testid="billing-receivables-tab"
                aria-selected={tab === "receivables"}
                onClick={() => setTab("receivables")}
              >
                미수 알림 ({receivableCount ?? "–"})
              </button>
            </SegmentedTabList>
            <div hidden={tab !== "checks"}>
              <BillingChecksPanel
                initialMonth={initialMonth}
                year={year}
                currentYear={currentYear}
                amountUnit={unit}
                refreshKey={refreshKey}
                onCount={setCheckCount}
                onRemedy={(value) => {
                  void remedy(value);
                }}
                onBillDetail={setDetailId}
              />
            </div>
            <div hidden={tab !== "receivables"}>
              <ReceivablesPanel
                year={year}
                currentYear={currentYear}
                amountUnit={unit}
                refreshKey={refreshKey}
                onCount={setReceivableCount}
                onBillDetail={setDetailId}
              />
            </div>
            {adjustmentToast && (
              <FloatingToast key={adjustmentToast} message="추가 청구를 등록했습니다." />
            )}
            {adjustmentRefreshFailed && (
              <FeedbackNotice
                tone="warn"
                title="등록 완료 · 조회 실패"
                action={
                  <button type="button" onClick={() => {
                    void refresh().then(() => setAdjustmentRefreshFailed(false)).catch(() => {});
                  }}>다시 조회</button>
                }
              >
                청구 목록과 누락 점검을 다시 불러와 주세요.
              </FeedbackNotice>
            )}
            <BillError error={actionError} />
          </>
        ))}
      <section
        data-testid="bills-list-panel"
        role="tabpanel"
        hidden={!renderTabs && tab !== "list"}
      >
        <FilterBar
          className="bill-filters"
          searchTestId="bill-search"
          resetTestId="bill-reset"
          onSubmit={() => {
            setSelected([]);
            const projectId = pickedProjectId;
            const projectName = projectId ? undefined : projectSearch.trim() || undefined;
            if (query.page === 1 && projectId === query.projectId && projectName === query.projectName)
              void load().catch(() => {});
            else conditions({ projectId, projectName });
          }}
          onReset={() => {
            setProjectSearch("");
            setPickedProjectId(undefined);
            setSelected([]);
            setQuery({
              year,
              includeCarryover: year === currentYear,
              page: 1,
              size: initialSize,
            });
          }}
        >
          <div className="sicost-field">
            <label htmlFor="bill-status-filter">상태<FieldHelp label="상태" /></label>
            <select
              id="bill-status-filter"
              data-testid="bill-status-filter"
              value={query.status ?? ""}
              onChange={(e) =>
                conditions({
                  status: (e.target.value || undefined) as
                    | api.BillStatus
                    | undefined,
                })
              }
            >
              <option value="">전체</option>
              {Object.entries(billStatusNames).map(([value, label]) => (
                <option
                  key={value}
                  value={value}
                  data-testid={`bill-status-option-${value.toLowerCase().replaceAll("_", "-")}`}
                >
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div className="sicost-field">
            <label htmlFor="bill-month-filter">청구월</label>
            <input
              id="bill-month-filter"
              data-testid="bill-month-filter"
              type="month"
              value={query.month ?? ""}
              onChange={(e) => conditions({ month: e.target.value || undefined })}
            />
          </div>
          <label className="sicost-field">
            계산서 번호
            <select
              data-testid="bill-missing-invoice-filter"
              value={query.missingInvoiceNo ? "true" : ""}
              onChange={(e) =>
                conditions({
                  missingInvoiceNo: e.target.value === "true" || undefined,
                })
              }
            >
              <option value="">전체</option>
              <option value="true">번호누락만</option>
            </select>
          </label>
          <div className="sicost-field bill-project-filter">
            <label htmlFor="bill-project-filter">프로젝트명</label>
            <div className="bill-project-filter-controls">
              <input
                id="bill-project-filter"
                data-testid="bill-project-filter"
                type="search"
                maxLength={100}
                value={projectSearch}
                placeholder="프로젝트명 일부 입력"
                onChange={(event) => {
                  setProjectSearch(event.target.value);
                  setPickedProjectId(undefined);
                }}
              />
              <button
                type="button"
                data-testid="bill-project-search"
                onClick={() => setProjectOpen(true)}
              >
                프로젝트 선택
              </button>
            </div>
          </div>
          {year === currentYear && (
            <label>
              <input
                type="checkbox"
                data-testid="bill-carryover"
                checked={query.includeCarryover}
                onChange={(e) =>
                  conditions({ includeCarryover: e.target.checked })
                }
              />
              이월 미수 포함
            </label>
          )}
        </FilterBar>
        <BillError
          error={error}
          retry={() => {
            void load().catch(() => {});
          }}
        />
        <BillError error={actionError} />
        {message && <p role="status">{message}</p>}
        <div className="bill-list-actions">
          {canRegister && (selected.length > 0 || allMatching) ? (
            <span
              className="bill-selection-bar"
              data-testid="bill-selection-bar"
            >
              <strong>
                {allMatching
                  ? `조회된 전체 ${data?.total ?? 0}건 선택`
                  : `선택 ${selected.length}건`}
              </strong>
              {!allMatching && (data?.total ?? 0) > selected.length && (
                <button
                  type="button"
                  data-testid="bill-select-all"
                  onClick={() => setAllMatching(true)}
                >
                  조회전체 선택
                </button>
              )}
              {renderActions ? (
                renderActions(actionContext)
              ) : (
                <ReceiptBatchAction
                  filters={{
                    year,
                    includeCarryover: query.includeCarryover,
                    status: query.status,
                    month: query.month,
                    missingInvoiceNo: query.missingInvoiceNo,
                    projectId: query.projectId,
                    projectName: query.projectName,
                    sort: query.sort,
                    direction: query.direction,
                  }}
                  selectedIds={selected.map(String)}
                  whole={allMatching}
                  today={period.currentDate}
                  minimumDate={`${period.cashBaseMonth}-01`}
                  projectNames={Object.fromEntries(
                    (data?.items ?? []).map((b) => [b.id, b.projectName]),
                  )}
                  mobile={mobile}
                  onRefresh={refresh}
                />
              )}
              <button
                type="button"
                data-testid="bill-selection-clear"
                onClick={() => setSelected([])}
              >
                선택 해제
              </button>
            </span>
          ) : (
            <span>청구 목록 · 전체 {data?.total ?? "–"}건</span>
          )}
          <span className="bill-list-tools">
            <button
              type="button"
              data-testid="bill-refresh"
              onClick={() => {
                void refresh()
                  .then(() => setMessage(""))
                  .catch(() => {});
              }}
            >
              재조회
            </button>
            <BillingReviewDownloadButton
              view="BILLS"
              amountUnit={unit}
              bills={{
                filters: {
                  year,
                  status: query.status,
                  month: query.month,
                  missingInvoiceNo: query.missingInvoiceNo,
                  projectId: query.projectId,
                  projectName: query.projectName,
                  includeCarryover: query.includeCarryover,
                },
                sort: query.sort,
                direction: query.direction,
              }}
            />
          </span>
        </div>
        <div className="bill-desktop">
          <DataTable
            rows={data?.items ?? []}
            columns={columns}
            rowKey={(b) => b.id}
            loading={loading}
            empty="조회된 청구가 없습니다."
            emptyAction={
              <button
                type="button"
                data-testid="bill-empty-reset"
                onClick={() => {
                  setProjectSearch("");
                  setSelected([]);
                  setQuery({
                    year,
                    includeCarryover: year === currentYear,
                    page: 1,
                    size: initialSize,
                  });
                }}
              >
                조건 초기화
              </button>
            }
            page={query.page}
            size={query.size}
            total={data?.total ?? 0}
            dense={dense}
            selectedKeys={selected}
            onSelectionChange={canRegister ? setSelected : undefined}
            onPageChange={(page) => {
              setQuery((q) => ({ ...q, page }));
              setSelected([]);
            }}
            onSizeChange={(size) => conditions({ size })}
            sort={
              query.sort
                ? { id: query.sort, direction: query.direction ?? "DESC" }
                : undefined
            }
            onSortChange={(id) =>
              conditions({
                sort: id as api.Sort,
                direction:
                  query.sort === id && query.direction === "ASC"
                    ? "DESC"
                    : "ASC",
              })
            }
          />
        </div>
        <div className="bill-mobile">
          {loading && <ScreenLoading message="불러오는 중입니다." />}
          {!loading && !data?.items.length && <p>조회된 청구가 없습니다.</p>}
          {(mobile ? mobileRows : (data?.items.slice(0, 20) ?? [])).map((b) => (
            <article key={b.id} data-testid={`bill-card-${b.id.toLowerCase()}`}>
              <button
                type="button"
                data-testid={`bill-card-open-${b.id.toLowerCase()}`}
                onClick={() => setDetailId(b.id)}
              >
                {b.id} · {b.projectName}
              </button>
              <p>
                {b.billingMonth} {b.carryover ? "이월" : ""} · 예정 {b.dueDate}
              </p>
              <p>
                합계 {billMoney(b.totalAmount, unit)} · 입금{" "}
                {billMoney(b.receiptAmount, unit)} · 미수{" "}
                {billMoney(b.unpaidAmount, unit)} {unit}
              </p>
              <BillStatus bill={b} />
            </article>
          ))}
          <p>일괄 입금·다운로드는 PC에서 이용해 주세요.</p>
          {data && query.page * query.size < data.total && (
            <button
              type="button"
              data-testid="bills-mobile-more"
              disabled={loading}
              onClick={() =>
                setQuery((q) => ({ ...q, size: 20, page: q.page + 1 }))
              }
            >
              더 보기
            </button>
          )}
        </div>
        {data && (
          <p className="bill-totals">
            필터전체 미취소 금액 합계 (현재 페이지 합계 아님) · 공급가{" "}
            {billMoney(data.totals.supplyAmount, unit)} · 부가세{" "}
            {billMoney(data.totals.vatAmount, unit)} · 합계{" "}
            {billMoney(data.totals.totalAmount, unit)} · 입금{" "}
            {billMoney(data.totals.receiptAmount, unit)} · 미수{" "}
            {billMoney(data.totals.unpaidAmount, unit)} {unit}
          </p>
        )}
      </section>
      {projectOpen && (
        <BillProjectPicker
          initialSearch={projectSearch}
          onClose={() => setProjectOpen(false)}
          onSelect={(project) => {
            setPickedProjectId(project.id);
            setProjectSearch(project.name);
            conditions({ projectId: project.id, projectName: undefined });
            setProjectOpen(false);
          }}
        />
      )}
      {detailId && !form && (
        <BillDetailDrawer
          id={detailId}
          year={year}
          period={period}
          amountUnit={unit}
          refreshKey={refreshKey}
          onClose={() => setDetailId(undefined)}
          onEdit={(bill) => setForm({ bill })}
          renderActions={renderDetailActions}
          renderReceiptActions={renderReceiptActions}
          onDirtyChange={onDirtyChange}
          onChanged={refresh}
        />
      )}
      {form && (form.bill ? canUpdate : canRegister) && (
        <BillFormDialog
          year={year}
          period={period}
          bill={form.bill}
          initial={form.initial}
          onClose={() => setForm(undefined)}
          onSaved={saved}
          onDirtyChange={onDirtyChange}
        />
      )}
    </>
  );
}
