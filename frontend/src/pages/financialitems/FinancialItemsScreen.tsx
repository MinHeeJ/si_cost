import { ScreenLoading } from "../../shared/ScreenLoading";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { PeriodSelector } from "../shared/PeriodSelector";
import { getSharedCodes } from "../shared/sharedApi";
import { ErrorBanner, FeedbackNotice, StatusBadge, FloatingToast } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { usePermission } from "../../shared/permission/PermissionBoundary";

import { Dialog } from "../../shared/Dialog";
import { FinancialUploadDialog } from "../b2/FinancialUploadDialog";
import { FinancialHistoryPanel } from "../apiops/FinancialHistoryPanel";
import { TaxPaymentDialog } from "../corporatetaxesops/TaxPaymentDialog";
import { FinancialTaxPanel, taxExplanation } from "./FinancialTaxPanel";
import { FinancialCardsPanel } from "./FinancialCardsPanel";
import type { CorporateTaxPayment } from "../corporatetaxesops/corporateTaxApi";
import { FinancialRowsPanel } from "./FinancialRowsPanel";
import { FinancialAmount, type FinancialUnit } from "./FinancialAmount";

import { useFinancialEditor } from "./useFinancialEditor";
import { useFinancialNavigationGuard } from "./useFinancialNavigationGuard";
import * as api from "./financialApi";
import "./financial.css";

const financialItemsGuide = (
  <>
    <section>
      <h3>조회월과 입력 현황</h3>
      <p>
        입력 현황에서 연도를 고른 뒤 월을 누르면 그 달의 항목이 열립니다. 입력
        현황의 건수는 저장된 재무 항목을 기준으로 표시합니다.
      </p>
    </section>
    <section>
      <h3>금액 단위와 저장</h3>
      <p>
        입력 금액은 원 단위 정수로 입력합니다. 탭 옆 ‘단위’에서 조회 금액의 표시
        단위를 바꿀 수 있습니다. [저장]은 두 탭의 변경사항을 함께 반영합니다.
      </p>
    </section>
  </>
);

export type FinancialItemsScreenProps = {
  onDirtyChange?(dirty: boolean): void;
  openAdjustments?: boolean;
  openTax?: boolean;
  initialMonth?: string;
  initialAdjustmentTargetMonth?: string;
};
export function FinancialItemsScreen(props: FinancialItemsScreenProps) {
  const location = useLocation();
  type Entry = { section?: string; month?: string; targetMonth?: string };
  const savedEntry = useRef<{ key: string; value: Entry }>();
  const routeEntry = location.state as Entry | null;
  if (
    location.pathname === "/UI-10" &&
    routeEntry?.month &&
    savedEntry.current?.key !== location.key
  )
    savedEntry.current = { key: location.key, value: routeEntry };
  const incoming =
    location.pathname === "/UI-10"
      ? (routeEntry ?? savedEntry.current?.value)
      : savedEntry.current?.value;
  const section = incoming?.section;
  const openAdjustments = section === "adjustments";
  return (
    <SharedScreenFrame
      screenId="UI-10"
      periodBar={null}
      screenGuide={financialItemsGuide}
    >
      <FinancialItemsEditor
        key={savedEntry.current?.key}
        {...props}
        initialMonth={incoming?.month ?? props.initialMonth}
        initialAdjustmentTargetMonth={
          openAdjustments
            ? incoming?.targetMonth
            : props.initialAdjustmentTargetMonth
        }
        openAdjustments={openAdjustments}
        openTax={section === "tax"}
      />
    </SharedScreenFrame>
  );
}
export function FinancialItemsEditor({
  onDirtyChange,
  openAdjustments,
  openTax,
  initialMonth,
  initialAdjustmentTargetMonth,
}: FinancialItemsScreenProps) {
  const canUpdate = usePermission("UI-10", "UPD"),
    canTaxConfirm = usePermission("UI-09", "CNFM");
  const [month, setMonth] = useState<string | undefined>(initialMonth);
  const state = useFinancialEditor(month, onDirtyChange),
    { editor, draft } = state;
  const guard = useFinancialNavigationGuard(
    state.dirty,
    state.save,
    state.discard,
  );
  const [tab, setTab] = useState<"financial" | "cash">("financial");
  const [unit, setUnit] = useState<FinancialUnit>("천원");
  const [uploadOpen, setUploadOpen] = useState(false),
    [uploadOpening, setUploadOpening] = useState(false);
  const [payment, setPayment] = useState<CorporateTaxPayment>();
  const [confirmation, setConfirmation] = useState<{
    title: string;
    message?: string;
    label: string;
    proceed(): void;
  }>();
  const [adjustmentTarget, setAdjustmentTarget] = useState(
    initialAdjustmentTargetMonth,
  );
  const [pendingAdjustment, setPendingAdjustment] = useState<string>();
  const [scrollToAdjustment, setScrollToAdjustment] = useState(false);
  const [codes, setCodes] = useState<{ code: string; name: string }[]>([]);
  const [yearStatus, setYearStatus] = useState<api.YearStatus>(),
    [statusError, setStatusError] = useState<unknown>(),
    [statusRetry, setStatusRetry] = useState(0);
  const next = useRef(0);
  const editorElement = useRef<HTMLElement>(null);
  useEffect(() => {
    const element = editorElement.current;
    if (!element || typeof ResizeObserver === "undefined") return;
    const position = () => {
      const bounds = element.getBoundingClientRect();
      if (!bounds.width) return;
      element.style.setProperty(
        "--financial-footer-left",
        `${Math.max(0, bounds.left)}px`,
      );
      element.style.setProperty(
        "--financial-footer-right",
        `${Math.max(0, window.innerWidth - bounds.right)}px`,
      );
    };
    const observer = new ResizeObserver(position);
    observer.observe(element);
    window.addEventListener("resize", position);
    position();
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", position);
    };
  }, [state.loading, editor?.month]);
  const adjustmentOpened = useRef(false);
  useEffect(() => {
    if (!pendingAdjustment || !editor?.editable || state.loading) return;
    setPendingAdjustment(undefined);
    setTab("financial");
    if (!editor.adjustmentTargetMonths.includes(pendingAdjustment)) {
      state.setError(new Error("최근 12개월 마감월만 조정할 수 있습니다"));
      return;
    }
    setAdjustmentTarget(pendingAdjustment);
    state.setDraft((old) =>
      old
        ? {
            ...old,
            adjustments: [
              ...old.adjustments,
              {
                key: `new-${++next.current}`,
                values: {
                  targetMonth: pendingAdjustment,
                  itemCode: "ETC",
                  amount: "",
                  reason: "",
                },
              },
            ],
          }
        : old,
    );
    setScrollToAdjustment(true);
  }, [pendingAdjustment, editor, state.loading]);
  useEffect(() => {
    if (!scrollToAdjustment || tab !== "financial") return;
    document
      .getElementById("financial-adjustments-section")
      ?.scrollIntoView?.({ block: "start" });
    setScrollToAdjustment(false);
  }, [scrollToAdjustment, tab]);
  useEffect(() => {
    if (
      (!openAdjustments && !openTax) ||
      !editor ||
      state.loading ||
      adjustmentOpened.current
    )
      return;
    if (
      !initialMonth &&
      !editor.editable &&
      editor.firstEditableMonth &&
      month !== editor.firstEditableMonth
    ) {
      setMonth(editor.firstEditableMonth);
      return;
    }
    adjustmentOpened.current = true;
    setTab("financial");
    document
      .getElementById(
        openTax ? "financial-tax-section" : "financial-adjustments-section",
      )
      ?.scrollIntoView?.({ block: "start" });
  }, [openAdjustments, openTax, initialMonth, editor, state.loading, month]);
  useEffect(() => {
    let live = true;
    void getSharedCodes("SGA_ITEM")
      .then((result) => {
        if (live) setCodes(result.items);
      })
      .catch((failure) => {
        if (live) state.setError(failure);
      });
    return () => {
      live = false;
    };
  }, []);
  const year = editor?.month.slice(0, 4);
  const firstYear = editor?.cashBaseMonth.slice(0, 4);
  const canLoadYear =
    year !== undefined && firstYear !== undefined && year >= firstYear;
  useEffect(() => {
    setYearStatus(undefined);
    setStatusError(undefined);
    if (!canLoadYear) return;
    const controller = new AbortController();
    void api
      .getFinancialYearStatus(year, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setYearStatus(result);
      })
      .catch((failure) => {
        if (!controller.signal.aborted) setStatusError(failure);
      });
    return () => controller.abort();
  }, [year, canLoadYear, editor?.revision, statusRetry]);
  useEffect(() => {
    if (state.violations.length)
      setTab(
        state.violations[0].tab === "CARD_TRANSFER_LOAN" ? "cash" : "financial",
      );
  }, [state.error]);
  useEffect(() => {
    const violation = state.violations[0];
    if (!violation) return;
    const key = violation.field.replace(
      /[A-Z]/g,
      (letter) => `-${letter.toLowerCase()}`,
    );
    const id =
      violation.collection === "corporateTax"
        ? "financial-estimated-tax"
        : `financial-${violation.collection === "cards" ? "card" : violation.collection}-${violation.rowId}-${key}`;
    const field = Array.from(
      document.querySelectorAll<HTMLElement>("[data-testid]"),
    ).find((element) => element.dataset.testid === id);
    field?.scrollIntoView?.({ block: "center" });
    field?.focus();
  }, [state.error, tab]);
  useEffect(() => {
    if (!editor) return;
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-10",
          value: { type: "YEAR_MONTH", value: editor.month },
          context: {
            currentMonth: editor.today.slice(0, 7),
            cashBaseMonth: editor.cashBaseMonth,
          },
        },
      }),
    );
  }, [editor?.month]);
  const selectMonth = (value: string) => {
    if (!editor || value === editor.month || value > editor.today.slice(0, 7))
      return;
    const proceed = () => {
      setMonth(value);
      setPayment(undefined);
    };
    if (
      window.dispatchEvent(
        new CustomEvent("sicost:screen-period-changing", {
          cancelable: true,
          detail: {
            screenId: "UI-10",
            value: { type: "YEAR_MONTH", value },
            proceed,
            label: "기간 변경",
          },
        }),
      )
    )
      proceed();
  };
  const refresh = async () => {
    const value = await state.refresh();
    setStatusRetry((n) => n + 1);
    return value;
  };
  const openPayment = async (row: CorporateTaxPayment) => {
    if (!(await guard.ask("법인세 납부 확인", true))) return;
    try {
      const fresh = await refresh();
      const latest = fresh.taxPayments.find(
        (p) => p.year === row.year && p.payType === row.payType,
      );
      if (latest?.payYn === "N" && fresh.editable) setPayment(latest);
    } catch (failure) {
      state.setError(failure);
    }
  };
  if (state.loading || !editor || !draft)
    return (
      <section data-testid="financial-editor">
        {state.loading ? (
          <ScreenLoading message="재무 항목을 불러오는 중입니다." />
        ) : (
          <ErrorBanner
            message={
              state.error instanceof Error
                ? state.error.message
                : "재무 항목을 불러오지 못했습니다."
            }
            onRetry={state.retry}
          />
        )}
      </section>
    );
  const disabled = !canUpdate || !editor.editable || state.pending;
  const originals = (collection: api.Collection) =>
    api.draftRows(editor[collection], collection);
  const changed = (collection: api.Collection) =>
    JSON.stringify(draft[collection]) !== JSON.stringify(originals(collection));
  const update = (
    collection: api.Collection,
    key: string,
    field: string,
    value: string | null,
  ) =>
    state.setDraft((old) =>
      old
        ? {
            ...old,
            [collection]: old[collection].map((row) =>
              row.key === key
                ? { ...row, values: { ...row.values, [field]: value } }
                : row,
            ),
          }
        : old,
    );
  const remove = (collection: api.Collection, key: string) =>
    state.setDraft((old) =>
      old
        ? {
            ...old,
            [collection]: old[collection].filter((row) => row.key !== key),
          }
        : old,
    );
  const add = (collection: api.Collection) => {
    const values: api.Values =
      collection === "items"
        ? {
            finClass: "SGA",
            sgaItemCode: null,
            itemName: "",
            amount: "",
            vatAmount: "0",
            ioDate: null,
            repeat: "N",
            remark: null,
          }
        : collection === "adjustments"
          ? {
              targetMonth:
                adjustmentTarget &&
                editor.adjustmentTargetMonths.includes(adjustmentTarget)
                  ? adjustmentTarget
                  : (editor.adjustmentTargetMonths[0] ?? null),
              itemCode: "ETC",
              amount: "",
              reason: "",
            }
          : collection === "transfers"
            ? {
                date: `${editor.month}-01`,
                classCode: "EXP",
                itemName: "",
                receiver: null,
                amount: "",
                remark: null,
              }
            : {
                date: `${editor.month}-01`,
                direction: "IN",
                amount: "",
                counterparty: null,
                remark: null,
              };
    state.setDraft((old) =>
      old
        ? {
            ...old,
            [collection]: [
              ...old[collection],
              { key: `new-${++next.current}`, values },
            ],
          }
        : old,
    );
  };
  const panel = (collection: api.Collection) => (
    <FinancialRowsPanel
      collection={collection}
      rows={draft[collection]}
      originals={originals(collection)}
      readOnly={!canUpdate || !editor.editable}
      pending={state.pending}
      month={editor.month}
      targets={editor.adjustmentTargetMonths}
      codes={codes}
      violations={state.violations}
      onChange={(key, field, value) => update(collection, key, field, value)}
      onDelete={(key) =>
        setConfirmation({
          title: "이 행을 삭제할까요?",
          label: "삭제",
          proceed: () => remove(collection, key),
        })
      }
      onAdd={() => add(collection)}
      notice={
        collection === "items" && automaticCount > 0 ? (
          <p className="sicost-banner sicost-tone-info">
            ‘매달 반복’ 항목 {automaticCount}개를 지난달({previousMonth})에서
            채웠습니다. 금액을 확인한 뒤 저장해 주세요.
          </p>
        ) : undefined
      }
      summary={
        collection === "adjustments" ? (
          <>
            <span>
              조정 합계 (적용월 이익){" "}
              {amount(editor.contributions.adjustmentProfit, true, true)}
            </span>
            <small>현금 반영 없음</small>
          </>
        ) : collection === "loans" ? (
          <>
            <span>
              받음 합계 {amount(editor.contributions.cashLoanIn, false, true)}
            </span>
            <span>
              갚음 합계 {amount(editor.contributions.cashLoanOut, false, true)}
            </span>
            <span>
              순액 {amount(editor.contributions.cashLoanNet, false, true)}
            </span>
          </>
        ) : undefined
      }
      tableRows={
        collection === "items"
          ? [
              {
                key: "tax-calculated",
                cells: {
                  finClass: "법인세",
                  itemName: (
                    <>
                      법인세(월할){" "}
                      <HelpTooltip label="법인세(월할) 안내" iconOnly>
                        {taxExplanation(allocation, editor.month)}
                      </HelpTooltip>
                    </>
                  ),
                  amount: (
                    <button
                      type="button"
                      className="financial-calculated"
                      data-testid="financial-tax-jump"
                      aria-label="법인세(월할) 금액과 납부 일정 보기"
                      onClick={() => {
                        document
                          .getElementById("financial-tax-section")
                          ?.scrollIntoView?.({ block: "start" });
                        document
                          .querySelector<HTMLInputElement>(
                            '[data-testid="financial-estimated-tax"]:not(:disabled)',
                          )
                          ?.focus({ preventScroll: true });
                      }}
                    >
                      {currentTax?.verified && currentTax.amount !== null
                        ? amount(currentTax.amount)
                        : "–"}
                    </button>
                  ),
                  vatAmount: "–",
                  ioDate: "–",
                  repeat: "–",
                  remark: "연간 예상액에서 자동 계산",
                },
              },
              ...[
                [
                  "sga",
                  "판관비 합계",
                  "SGA",
                  contributions.sgaAmount,
                  contributions.sgaVatAmount,
                ],
                [
                  "income",
                  "영업외수익 합계",
                  "NOPR_IN",
                  contributions.nonOperatingIncome,
                  contributions.nonOperatingIncomeVat,
                ],
                [
                  "expense",
                  "영업외비용 합계",
                  "NOPR_OUT",
                  contributions.nonOperatingExpense,
                  contributions.nonOperatingExpenseVat,
                ],
              ].map(([key, label, code, total, vat]) => ({
                key: `total-${key}`,
                cells: {
                  finClass: <strong>{label}</strong>,
                  itemName: `${draft.items.filter((row) => row.values.finClass === code).length}건`,
                  amount: <strong>{amount(total)}</strong>,
                  vatAmount: amount(vat),
                },
              })),
              {
                key: "total-tax",
                cells: {
                  finClass: <strong>법인세(월할)</strong>,
                  itemName: "계산값",
                  amount: (
                    <strong>
                      {currentTax?.verified && currentTax.amount !== null
                        ? amount(currentTax.amount)
                        : "–"}
                    </strong>
                  ),
                },
              },
            ]
          : collection === "transfers"
            ? [
                {
                  key: "total-expense",
                  cells: {
                    classCode: <strong>계좌이체(제경비) 합계</strong>,
                    amount: (
                      <strong>
                        {amount(contributions.cashTransferExpense)}
                      </strong>
                    ),
                  },
                },
                {
                  key: "total-special",
                  cells: {
                    classCode: <strong>특별비(미지급 인건비) 합계</strong>,
                    amount: (
                      <strong>{amount(contributions.cashUnpaidLabor)}</strong>
                    ),
                  },
                },
                {
                  key: "total-transfers",
                  cells: {
                    classCode: <strong>합계</strong>,
                    itemName: `${draft.transfers.length}건`,
                    amount: (
                      <strong>{amount(contributions.transferTotal)}</strong>
                    ),
                  },
                },
              ]
            : []
      }
    />
  );
  const amount = (
    value: string | null | undefined,
    plus = false,
    showUnit = false,
  ) => (
    <FinancialAmount
      value={value}
      unit={unit}
      plus={plus}
      showUnit={showUnit}
    />
  );
  const automaticCount = draft.items.filter((row) => row.automatic).length;
  const selectedYear = editor.month.slice(0, 4);
  const currentMonth = editor.today.slice(0, 7);
  const currentYear = currentMonth.slice(0, 4);
  const earliestListedYear = String(
    Math.max(
      1,
      Math.min(Number(editor.cashBaseMonth.slice(0, 4)), Number(currentYear)) -
        1,
    ),
  ).padStart(4, "0");
  const contributions = state.calculated?.contributions ?? editor.contributions;
  const allocation = state.calculated?.allocation ?? editor.allocation;
  const currentTax = allocation.months.find(
    (row) => row.month === editor.month,
  );
  const previousMonth = `${Number(editor.month.slice(5)) === 1 ? Number(editor.month.slice(0, 4)) - 1 : editor.month.slice(0, 4)}-${String(Number(editor.month.slice(5)) === 1 ? 12 : Number(editor.month.slice(5)) - 1).padStart(2, "0")}`;
  const cashDirty =
    changed("transfers") ||
    changed("loans") ||
    JSON.stringify(draft.cards.map((row) => row.values)) !==
      JSON.stringify(
        editor.cards.map((card) => ({
          cardName: card.cardName,
          last4: card.last4,
          useYn: card.useYn,
          amount:
            editor.cardPayments.find((p) => p.cardId === card.cardId)?.amount ??
            "0",
        })),
      ) ||
    Boolean(draft.cashOverride);
  const financialDirty =
    changed("items") ||
    changed("adjustments") ||
    draft.estimatedTax !== (editor.corporateTax?.estimatedTax ?? null) ||
    draft.taxSchedules.some((row) => {
      const old = editor.taxPayments.find(
        (p) => p.year === row.year && p.payType === row.payType,
      );
      return old?.payAmount !== row.payAmount || old?.dueDate !== row.dueDate;
    });
  return (
    <section
      ref={editorElement}
      className="financial-editor"
      data-testid="financial-editor"
    >
      <ScreenTitleActions>
        <div className="financial-actions">
          {editor.editable && (
            <>
              <button
                type="button"
                data-testid="financial-load-previous"
                disabled={disabled}
                onClick={async () => {
                  const result = await state.preview("LOAD_PREVIOUS");
                  if (result?.addedDrafts.length)
                    setConfirmation({
                      title: `${previousMonth} 항목을 불러올까요?`,
                      label: "불러오기",
                      message: `이번 달에 없는 항목 ${result.addedDrafts.length}개(${result.addedDrafts.map((row) => row.itemName).join(", ")})를 가져옵니다. 금액은 그대로, 입출금일은 이번 달 같은 날짜로 바뀝니다. 저장해야 반영됩니다.`,
                      proceed: () => state.applyPrevious(result),
                    });
                }}
              >
                지난달 항목 불러오기
              </button>
              <section
                data-testid="financial-upload-actions"
                className="b7-actions"
              >
                <button
                  type="button"
                  data-testid="financial-upload-button"
                  disabled={disabled || uploadOpening}
                  onClick={async () => {
                    if (uploadOpening) return;
                    setUploadOpening(true);
                    try {
                      if (await guard.ask("B.2 업로드 시작", true))
                        setUploadOpen(true);
                    } finally {
                      setUploadOpening(false);
                    }
                  }}
                >
                  엑셀 업로드
                </button>
                {uploadOpen && (
                  <FinancialUploadDialog
                    month={editor.month}
                    readOnly={disabled}
                    onClose={() => setUploadOpen(false)}
                    onApplied={async () => {
                      await refresh();
                    }}
                  />
                )}
              </section>
            </>
          )}
        </div>
      </ScreenTitleActions>
      <section
        className="financial-months sicost-section-card"
        aria-labelledby="financial-month-status-title"
        data-testid="financial-month-status"
      >
        <div className="financial-year-controls">
          <div className="sicost-period-controls">
            <PeriodSelector
              type="YEAR"
              label="입력 현황 연도"
              value={selectedYear}
              min="0001"
              max={currentYear}
              optionMin={earliestListedYear}
              disabled={state.pending}
              onChange={(year) =>
                selectMonth(year === currentYear ? currentMonth : `${year}-12`)
              }
            />
          </div>
        </div>
        <header className="financial-month-heading">
          <h2 id="financial-month-status-title">입력 현황</h2>
          <span>저장된 재무 항목만 집계합니다</span>
        </header>
        <div
          className="financial-month-grid"
          role="group"
          aria-label="12개월 입력 현황"
        >
          {Array.from(
            { length: 12 },
            (_, index) =>
              `${editor.month.slice(0, 4)}-${String(index + 1).padStart(2, "0")}`,
          ).map((value) => {
            const status = yearStatus?.months.find(
              (row) => row.month === value,
            );
            const future = value > editor.today.slice(0, 7);
            const chip = (
              <button
                type="button"
                key={value}
                data-testid={`financial-month-${value}`}
                aria-pressed={value === editor.month}
                disabled={
                  future || state.pending || status?.selectable === false
                }
                onClick={() => selectMonth(value)}
              >
                <span>{Number(value.slice(5))}월</span>
                <small>
                  {future
                    ? "–"
                    : status
                      ? `${status.closeStatus === "CLOSE" ? "마감" : "작업중"} · ${status.itemCount ?? "–"}건`
                      : statusError || !canLoadYear
                        ? "–"
                        : "조회 중"}
                </small>
              </button>
            );
            return future || status?.selectable === false ? (
              <HelpTooltip
                key={value}
                label={`${Number(value.slice(5))}월`}
                disabledTarget={chip}
              >
                아직 열리지 않은 달입니다. 그 달이 되면 매달 반복 항목이
                자동으로 채워집니다.
              </HelpTooltip>
            ) : (
              <span key={value}>{chip}</span>
            );
          })}
        </div>
        {Boolean(statusError) && (
          <ErrorBanner
            message="입력 현황을 불러오지 못했습니다."
            onRetry={() => setStatusRetry((n) => n + 1)}
          />
        )}
      </section>
      {!editor.editable && (
        <div
          className="sicost-banner sicost-tone-lock"
          data-testid="financial-read-only"
        >
          <span>
            {editor.closeStatus === "CLOSE"
              ? `${editor.month}는 마감되었습니다. 고칠 내용은 다음 달 전월 조정으로 입력하세요.`
              : editor.readOnlyReason}
          </span>
          {editor.closeStatus === "CLOSE" && (
            <button
              type="button"
              data-testid="financial-open-adjustment"
              disabled={!editor.firstEditableMonth}
              onClick={() => {
                setTab("financial");
                if (editor.firstEditableMonth) {
                  setPendingAdjustment(editor.month);
                  selectMonth(editor.firstEditableMonth);
                }
              }}
            >
              전월 조정 입력
            </button>
          )}
        </div>
      )}
      <div className="financial-view-toolbar">
        <SegmentedTabList as="nav" aria-label="재무 항목 탭">
          <button
            type="button"
            data-testid="financial-tab-financial"
            aria-pressed={tab === "financial"}
            onClick={() => setTab("financial")}
          >
            판관비·법인세·조정{" "}
            {financialDirty && <StatusBadge tone="warn">저장 전</StatusBadge>}
          </button>
          <button
            type="button"
            data-testid="financial-tab-cash"
            aria-pressed={tab === "cash"}
            onClick={() => setTab("cash")}
          >
            카드·이체·대여금{" "}
            {cashDirty && <StatusBadge tone="warn">저장 전</StatusBadge>}
          </button>
        </SegmentedTabList>
        <label className="financial-unit-control">
          단위{" "}
          <select
            data-testid="financial-display-unit"
            aria-label="표시 단위"
            value={unit}
            onChange={(event) => setUnit(event.target.value as FinancialUnit)}
          >
            <option>원</option>
            <option>천원</option>
            <option>백만원</option>
          </select>
        </label>
      </div>
      <div className="sicost-feedback-slot">
        {state.refreshFailed ? (
          <FeedbackNotice
            tone="warn"
            title="저장 완료 · 화면 갱신 실패"
            action={
              <button
                type="button"
                data-testid="financial-refresh-only"
                onClick={() => void refresh().catch(state.setError)}
              >
                다시 조회
              </button>
            }
          >
            저장됐지만 다시 불러오지 못했습니다. 재저장하지 말고 조회해 주세요.
          </FeedbackNotice>
        ) : state.error != null && (
          <ErrorBanner
            title={state.violations.length ? "입력 항목 확인" : "재무항목 처리 실패"}
            message={state.error instanceof Error ? state.error.message : "요청을 처리하지 못했습니다."}
            requestId={state.error instanceof ApiError ? state.error.body.requestId : undefined}
            onDismiss={() => state.setError(undefined)}
            focusOnShow
          />
        )}
        {state.violations.length > 0 && (
          <FeedbackNotice tone="warn" title="확인할 항목">
            <ul>
              {state.violations.map((v, index) => (
                <li key={index}>
                  {v.tab === "CARD_TRANSFER_LOAN" ? "카드·이체·대여금" : "판관비·법인세·조정"}
                  {" · "}{v.rowId} · {v.field}: {v.message}
                </li>
              ))}
            </ul>
          </FeedbackNotice>
        )}
        {state.previewError != null && (
          <ErrorBanner
            title="미리보기 조회 실패"
            message="서버 미리보기를 불러오지 못했습니다. 입력을 확인한 뒤 다시 시도해 주세요."
          />
        )}
      </div>
      {state.message && !state.error && !state.pending && !state.refreshFailed && (
        <FloatingToast key={state.messageSequence} message={state.message} />
      )}
      {state.previewing && (
        <p role="status" data-testid="financial-preview-pending">
          서버에서 초안을 계산하고 있습니다.
        </p>
      )}
      <div
        hidden={tab !== "financial"}
        data-testid="financial-financial-panel"
        className="financial-tab-panel"
        role="region"
        aria-label="판관비·법인세·조정"
      >
        {panel("items")}
        <FinancialTaxPanel
          editor={editor}
          draft={draft}
          allocation={allocation}
          automaticAmounts={state.calculated?.automaticTaxAmounts}
          disabled={disabled}
          unit={unit}
          setDraft={state.setDraft}
          violations={state.violations}
          onPayment={(row) => void openPayment(row)}
        />
        {panel("adjustments")}
      </div>
      <div
        hidden={tab !== "cash"}
        data-testid="financial-cash-panel"
        className="financial-tab-panel"
        role="region"
        aria-label="카드·이체·대여금"
      >
        <FinancialCardsPanel
          month={editor.month}
          draft={draft}
          cardMonth={state.calculated?.cardMonth ?? editor.cardMonth}
          unit={unit}
          disabled={disabled}
          readOnly={!canUpdate || !editor.editable}
          setDraft={state.setDraft}
          violations={state.violations}
        />
        {panel("transfers")}
        {panel("loans")}
      </div>
      <FinancialHistoryPanel
        month={editor.month}
        refreshKey={editor.revision}
      />
      {canUpdate && editor.editable && (
        <footer className="financial-footer" data-testid="financial-footer">
          <small>금액 입력은 원 정수 · 두 탭을 함께 저장합니다.</small>
          <button
            type="button"
            data-testid="financial-cancel"
            disabled={state.pending}
            onClick={() =>
              setConfirmation({
                title: "저장 안 한 내용을 모두 되돌릴까요?",
                label: "되돌리기",
                proceed: state.discard,
              })
            }
          >
            취소
          </button>
          <button
            type="button"
            className="sicost-primary"
            data-testid="financial-save"
            disabled={disabled}
            onClick={() => void state.save()}
          >
            저장
          </button>
        </footer>
      )}
      {confirmation && (
        <Dialog
          open
          title={confirmation.title}
          confirmLabel={confirmation.label}
          cancelLabel={confirmation.label === "되돌리기" ? "계속 편집" : "취소"}
          onCancel={() => setConfirmation(undefined)}
          onConfirm={() => {
            confirmation.proceed();
            setConfirmation(undefined);
          }}
        >
          <div data-testid="financial-confirmation-dialog">
            {confirmation.message}
          </div>
        </Dialog>
      )}
      {guard.request && (
        <Dialog
          open
          title={
            guard.request.immediate
              ? "저장 전 변경을 먼저 처리해 주세요"
              : "저장 안 한 내용이 있습니다"
          }
          confirmLabel={guard.request.immediate ? "먼저 저장" : "저장 후 이동"}
          cancelLabel={guard.request.immediate ? "취소" : "계속 편집"}
          onConfirm={guard.saveAndGo}
          onCancel={guard.stay}
          pending={state.pending}
        >
          <section data-testid="financial-navigation-dialog">
            <p>{guard.request.label}</p>
            {state.error != null && (
              <p role="alert">
                {state.error instanceof Error
                  ? state.error.message
                  : "저장 실패"}
              </p>
            )}
            <button
              type="button"
              data-testid="financial-navigation-discard"
              disabled={state.pending}
              onClick={guard.discardAndGo}
            >
              {guard.request.immediate ? "변경 버리기" : "버리고 이동"}
            </button>
          </section>
        </Dialog>
      )}
      {payment && (
        <TaxPaymentDialog
          payment={payment}
          month={editor.month}
          today={editor.today}
          cashBaseMonth={editor.cashBaseMonth}
          editable={canTaxConfirm && editor.editable}
          onClose={() => setPayment(undefined)}
          onSaved={async () => {
            await refresh();
            state.setMessage("납부 확인을 저장했습니다.");
          }}
        />
      )}
    </section>
  );
}
