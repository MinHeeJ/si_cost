import { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { ScreenPeriodBar } from "../shared/ScreenPeriodBar";
import { getSharedCodes } from "../shared/sharedApi";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { DataTable } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { FinancialUploadActions } from "../b2/FinancialUploadDialog";
import { FinancialHistoryPanel } from "../apiops/FinancialHistoryPanel";
import { TaxPaymentDialog } from "../corporatetaxesops/TaxPaymentDialog";
import { FinancialPaymentStatus } from "../corporatetaxesops/FinancialPaymentStatus";
import type { CorporateTaxPayment } from "../corporatetaxesops/corporateTaxApi";
import { FinancialRowsPanel } from "./FinancialRowsPanel";
import { FinancialAmount, type FinancialUnit } from "./FinancialAmount";
import { WonInput } from "./WonInput";
import { useFinancialEditor } from "./useFinancialEditor";
import { useFinancialNavigationGuard } from "./useFinancialNavigationGuard";
import * as api from "./financialApi";
import "./financial.css";

export type FinancialItemsScreenProps = {
  onDirtyChange?(dirty: boolean): void;
  openAdjustments?: boolean;
  openTax?: boolean;
};
export function FinancialItemsScreen(props: FinancialItemsScreenProps) {
  const location = useLocation();
  const section = (location.state as { section?: string } | null)?.section;
  const openAdjustments = section === "adjustments";
  return (
    <SharedScreenFrame screenId="UI-10" periodBar={null}>
      <FinancialItemsEditor {...props} openAdjustments={openAdjustments} openTax={section === "tax"} />
    </SharedScreenFrame>
  );
}
export function FinancialItemsEditor({
  onDirtyChange,
  openAdjustments,
  openTax,
}: FinancialItemsScreenProps) {
  const [month, setMonth] = useState<string>();
  const state = useFinancialEditor(month, onDirtyChange),
    { editor, draft } = state;
  const guard = useFinancialNavigationGuard(
    state.dirty,
    state.save,
    state.discard,
  );
  const [tab, setTab] = useState<"financial" | "cash">("financial");
  const [unit, setUnit] = useState<FinancialUnit>("천원"),
    [history, setHistory] = useState(false);
  const [showStopped, setShowStopped] = useState(false),
    [payment, setPayment] = useState<CorporateTaxPayment>();
  const [codes, setCodes] = useState<{ code: string; name: string }[]>([]);
  const [yearStatus, setYearStatus] = useState<api.YearStatus>(),
    [statusError, setStatusError] = useState<unknown>(),
    [statusRetry, setStatusRetry] = useState(0);
  const next = useRef(0);
  const adjustmentOpened = useRef(false);
  useEffect(() => {
    if (
      (!openAdjustments && !openTax) ||
      !editor ||
      state.loading ||
      adjustmentOpened.current
    )
      return;
    if (
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
      .getElementById(openTax ? "financial-tax-section" : "financial-adjustments-section")
      ?.scrollIntoView?.({ block: "start" });
  }, [openAdjustments, openTax, editor, state.loading, month]);
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
  useEffect(() => {
    if (!editor) return;
    const controller = new AbortController();
    setYearStatus(undefined);
    setStatusError(undefined);
    void api
      .getFinancialYearStatus(editor.month.slice(0, 4), controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setYearStatus(result);
      })
      .catch((failure) => {
        if (!controller.signal.aborted) setStatusError(failure);
      });
    return () => controller.abort();
  }, [editor?.month, editor?.revision, statusRetry]);
  useEffect(() => {
    if (state.violations.length)
      setTab(
        state.violations[0].tab === "CARD_TRANSFER_LOAN" ? "cash" : "financial",
      );
  }, [state.error]);
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
          <p role="status">재무 항목을 불러오는 중입니다.</p>
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
  const disabled = !editor.editable || state.pending;
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
              targetMonth: editor.adjustmentTargetMonths[0] ?? null,
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
      readOnly={!editor.editable}
      pending={state.pending}
      month={editor.month}
      targets={editor.adjustmentTargetMonths}
      codes={codes}
      violations={state.violations}
      onChange={(key, field, value) => update(collection, key, field, value)}
      onDelete={(key) => remove(collection, key)}
      onAdd={() => add(collection)}
    />
  );
  const amount = (value: string | null | undefined, plus = false) => (
    <FinancialAmount value={value} unit={unit} plus={plus} />
  );
  const cardUpdate = (key: string, field: string, value: string) =>
    state.setDraft((old) =>
      old
        ? {
            ...old,
            cards: old.cards.map((row) =>
              row.key === key
                ? { ...row, values: { ...row.values, [field]: value } }
                : row,
            ),
          }
        : old,
    );
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
    draft.estimatedTax !== editor.corporateTax?.estimatedTax ||
    draft.taxSchedules.some((row) => {
      const old = editor.taxPayments.find(
        (p) => p.year === row.year && p.payType === row.payType,
      );
      return old?.payAmount !== row.payAmount || old?.dueDate !== row.dueDate;
    });
  return (
    <section className="financial-editor" data-testid="financial-editor">
      <ScreenPeriodBar
        screenId="UI-10"
        controlled={{
          month: editor.month,
          currentMonth: editor.today.slice(0, 7),
          onChange: selectMonth,
          disabled: state.pending,
        }}
      />
      <div className="financial-actions">
        <button
          type="button"
          data-testid="financial-load-previous"
          disabled={disabled}
          onClick={() => void state.preview("LOAD_PREVIOUS")}
        >
          지난달 항목 불러오기
        </button>
        <FinancialUploadActions
          month={editor.month}
          readOnly={disabled}
          beforeOpen={() => guard.ask("B.2 업로드 시작", true)}
          onApplied={async () => {
            await refresh();
          }}
        />
        <button
          type="button"
          data-testid="financial-history-toggle"
          aria-expanded={history}
          onClick={() => setHistory((value) => !value)}
        >
          변경 이력 {history ? "접기" : "펼침"}
        </button>
      </div>
      <section
        className="financial-months"
        data-testid="financial-month-status"
      >
        <div className="sicost-list-toolbar">
          <button
            type="button"
            data-testid="financial-previous-year"
            disabled={state.pending || editor.month.slice(0, 4) <= "0001"}
            onClick={() =>
              selectMonth(
                `${String(Number(editor.month.slice(0, 4)) - 1).padStart(4, "0")}-12`,
              )
            }
          >
            ‹ 이전 해
          </button>
          <h2>{editor.month.slice(0, 4)}년</h2>
          <button
            type="button"
            data-testid="financial-next-year"
            disabled={
              state.pending ||
              editor.month.slice(0, 4) >= editor.today.slice(0, 4)
            }
            onClick={() => {
              const year = String(
                Number(editor.month.slice(0, 4)) + 1,
              ).padStart(4, "0");
              selectMonth(
                year === editor.today.slice(0, 4)
                  ? editor.today.slice(0, 7)
                  : `${year}-12`,
              );
            }}
          >
            다음 해 ›
          </button>
          <strong>입력 현황</strong>
          <span>저장된 재무 항목만 집계합니다</span>
        </div>
        <div className="financial-month-strip">
          {Array.from(
            { length: 12 },
            (_, index) =>
              `${editor.month.slice(0, 4)}-${String(index + 1).padStart(2, "0")}`,
          ).map((value) => {
            const status = yearStatus?.months.find(
              (row) => row.month === value,
            );
            const future = value > editor.today.slice(0, 7);
            return (
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
                      : "조회 중"}
                </small>
              </button>
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
          <span>{editor.readOnlyReason}</span>
          {editor.closeStatus === "CLOSE" && (
            <button
              type="button"
              data-testid="financial-open-adjustment"
              disabled={!editor.firstEditableMonth}
              onClick={() => {
                setTab("financial");
                if (editor.firstEditableMonth)
                  selectMonth(editor.firstEditableMonth);
              }}
            >
              전월 조정 입력
            </button>
          )}
        </div>
      )}
      <nav className="financial-tabs" aria-label="재무 항목 탭">
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
        <label>
          단위{" "}
          <select
            data-testid="financial-display-unit"
            value={unit}
            onChange={(event) => setUnit(event.target.value as FinancialUnit)}
          >
            <option>원</option>
            <option>천원</option>
            <option>백만원</option>
          </select>
        </label>
      </nav>
      {state.error != null && (
        <ErrorBanner
          message={
            state.error instanceof Error
              ? state.error.message
              : "요청을 처리하지 못했습니다."
          }
          requestId={
            state.error instanceof ApiError
              ? state.error.body.requestId
              : undefined
          }
        />
      )}
      {state.violations.map((v, index) => (
        <p role="alert" key={index}>
          {v.tab === "CARD_TRANSFER_LOAN"
            ? "카드·이체·대여금"
            : "판관비·법인세·조정"}{" "}
          · {v.rowId} · {v.field}: {v.message}
        </p>
      ))}
      {state.refreshFailed && (
        <div className="sicost-banner sicost-tone-warn">
          저장됐지만 다시 불러오지 못했습니다. 재저장하지 말고 조회해 주세요.
          <button
            type="button"
            data-testid="financial-refresh-only"
            onClick={() => void refresh().catch(state.setError)}
          >
            다시 조회
          </button>
        </div>
      )}
      {state.message && <p role="status">{state.message}</p>}
      <div
        hidden={tab !== "financial"}
        data-testid="financial-financial-panel"
        role="region"
        aria-label="판관비·법인세·조정"
      >
        <p className="financial-help">
          손익·영업이익표·순이익표에 들어갑니다. 판관비·영업외비용은 자금수지표
          제경비에도 들어갑니다. 아래 [저장]은 두 탭을 함께 저장합니다.
        </p>
        {draft.items.some((row) => row.automatic) && (
          <p className="sicost-banner sicost-tone-info">
            ‘매달 반복’ 항목 {draft.items.filter((row) => row.automatic).length}
            개를 지난달에서 자동으로 채웠습니다. 금액이 다르면 고친 뒤 저장해
            주세요.
          </p>
        )}
        {panel("items")}
        <div className="financial-sums">
          <span>
            판관비 입력 합계 {amount(editor.contributions.sgaAmount)} / 부가세{" "}
            {amount(editor.contributions.sgaVatAmount)}
          </span>
          <span>
            영업외수익 합계 {amount(editor.contributions.nonOperatingIncome)} /
            부가세 {amount(editor.contributions.nonOperatingIncomeVat)}
          </span>
          <span>
            영업외비용 합계 {amount(editor.contributions.nonOperatingExpense)} /
            부가세 {amount(editor.contributions.nonOperatingExpenseVat)}
          </span>
          <span>
            법인세(월할) {amount(editor.contributions.corporateTaxAllocation)}{" "}
            <StatusBadge tone="neutral">계산</StatusBadge>
          </span>
          <small>저장값 또는 마지막 서버 계산값 · 표시 단위 {unit}</small>
          <button
            type="button"
            data-testid="financial-calculate"
            disabled={disabled}
            onClick={() => void state.preview("CALCULATE")}
          >
            현재 초안 계산
          </button>
        </div>
        <section
          className="financial-section"
          id="financial-tax-section"
          data-testid="financial-tax-panel"
        >
          <h2>
            법인세 {editor.month.slice(0, 4)}년 · 1년 예상액을 달마다 나눠
            순이익표에 반영
          </h2>
          <p>예상액 관리이며 세무 신고가 아닙니다.</p>
          <label>
            연간 예상 법인세 (원) *{" "}
            <WonInput
              data-testid="financial-estimated-tax"
              value={draft.estimatedTax ?? ""}
              disabled={
                disabled ||
                draft.estimatedTax === null ||
                editor.allocation.openMonthCount === 0 ||
                editor.allocation.months.some(
                  (row) => row.closeStatus === "CLOSE" && !row.verified,
                )
              }
              onValueChange={(value) =>
                state.setDraft((old) =>
                  old ? { ...old, estimatedTax: value } : old,
                )
              }
            />
          </label>
          <p>
            마감월 반영액 {amount(editor.allocation.closedTotal)} / 남은액{" "}
            {amount(editor.allocation.remainingAmount)} / 열린 운영월 수{" "}
            {editor.allocation.openMonthCount}
          </p>
          {editor.allocation.warnings.map((warning) => (
            <p key={warning} className="sicost-banner sicost-tone-warn">
              {warning}
            </p>
          ))}
          <DataTable
            rows={editor.allocation.months}
            rowKey={(row) => row.month}
            columns={[
              { id: "month", label: "월", render: (row) => row.month },
              {
                id: "status",
                label: "상태",
                render: (row) => (
                  <StatusBadge
                    tone={row.closeStatus === "CLOSE" ? "lock" : "info"}
                  >
                    {row.closeStatus === "CLOSE" ? "마감" : "작업중"}
                  </StatusBadge>
                ),
              },
              {
                id: "amount",
                label: "월할액",
                render: (row) => amount(row.amount),
              },
              {
                id: "verified",
                label: "확인상태",
                render: (row) =>
                  `${row.verified ? "확인" : "미확인"}${row.fixed === "Y" ? " · 고정" : ""}${row.migrated === "Y" ? " · 이관" : ""}`,
              },
            ]}
          />
          <DataTable
            rows={editor.taxPayments}
            rowKey={(row) => `${row.year}-${row.payType}`}
            empty="등록된 납부 일정이 없습니다."
            columns={[
              {
                id: "type",
                label: "구분",
                render: (row) =>
                  `${row.year}년 ${row.payType === "MID" ? "중간예납" : "확정 신고"}`,
              },
              {
                id: "date",
                label: "납부일",
                render: (row) =>
                  row.payYn === "Y" ? (
                    <>
                      {row.paidDate}
                      {row.paidDate !== row.dueDate && (
                        <small>예정 {row.dueDate}</small>
                      )}
                    </>
                  ) : (
                    <input
                      type="date"
                      data-testid={`financial-tax-${row.year}-${row.payType.toLowerCase()}-date`}
                      aria-label={`${row.year} ${row.payType} 예정일`}
                      disabled={disabled}
                      value={
                        draft.taxSchedules.find(
                          (p) =>
                            p.year === row.year && p.payType === row.payType,
                        )?.dueDate ?? ""
                      }
                      onChange={(event) =>
                        state.setDraft((old) =>
                          old
                            ? {
                                ...old,
                                taxSchedules: old.taxSchedules.map((p) =>
                                  p.year === row.year &&
                                  p.payType === row.payType
                                    ? { ...p, dueDate: event.target.value }
                                    : p,
                                ),
                              }
                            : old,
                        )
                      }
                    />
                  ),
              },
              {
                id: "amount",
                label: "금액",
                render: (row) =>
                  row.payYn === "Y" ? (
                    amount(row.displayAmount)
                  ) : (
                    <>
                      <WonInput
                        data-testid={`financial-tax-${row.year}-${row.payType.toLowerCase()}-amount`}
                        aria-label={`${row.year} ${row.payType} 예정액`}
                        disabled={disabled}
                        value={
                          draft.taxSchedules.find(
                            (p) =>
                              p.year === row.year && p.payType === row.payType,
                          )?.payAmount ?? ""
                        }
                        placeholder={row.automaticAmount ?? "미확인"}
                        onValueChange={(value) =>
                          state.setDraft((old) =>
                            old
                              ? {
                                  ...old,
                                  taxSchedules: old.taxSchedules.map((p) =>
                                    p.year === row.year &&
                                    p.payType === row.payType
                                      ? { ...p, payAmount: value || null }
                                      : p,
                                  ),
                                }
                              : old,
                          )
                        }
                      />
                      <small>자동 계산값 {amount(row.automaticAmount)}</small>
                      {row.manuallyEdited && (
                        <StatusBadge tone="neutral">직접 고침</StatusBadge>
                      )}
                      {row.payType === "CNFM" && (
                        <button
                          type="button"
                          data-testid={`financial-tax-${row.year}-auto`}
                          disabled={disabled}
                          onClick={() =>
                            state.setDraft((old) =>
                              old
                                ? {
                                    ...old,
                                    taxSchedules: old.taxSchedules.map((p) =>
                                      p.year === row.year &&
                                      p.payType === row.payType
                                        ? { ...p, payAmount: null }
                                        : p,
                                    ),
                                  }
                                : old,
                            )
                          }
                        >
                          자동 계산으로
                        </button>
                      )}
                    </>
                  ),
              },
              {
                id: "status",
                label: "상태",
                render: (row) => <FinancialPaymentStatus status={row.status} />,
              },
              {
                id: "confirm",
                label: "납부 확인",
                render: (row) =>
                  row.payYn === "N" ? (
                    <button
                      type="button"
                      data-testid={`financial-tax-${row.year}-${row.payType.toLowerCase()}-confirm`}
                      disabled={disabled}
                      onClick={() => void openPayment(row)}
                    >
                      납부 확인
                    </button>
                  ) : (
                    "–"
                  ),
              },
            ]}
          />
        </section>
        {panel("adjustments")}
        <p>
          조정 합계 (적용월 이익){" "}
          {amount(editor.contributions.adjustmentProfit, true)} · 현금 반영 없음
        </p>
      </div>
      <div
        hidden={tab !== "cash"}
        data-testid="financial-cash-panel"
        role="region"
        aria-label="카드·이체·대여금"
      >
        <p className="financial-help">
          자금수지에만 반영됩니다. 손익에는 넣지 않습니다.
        </p>
        <section
          className="financial-section"
          data-testid="financial-cards-panel"
        >
          <div className="sicost-list-toolbar">
            <h2>법인카드 대금</h2>
            <label>
              <input
                type="checkbox"
                data-testid="financial-show-stopped"
                checked={showStopped}
                onChange={(event) => setShowStopped(event.target.checked)}
              />{" "}
              삭제된 카드 보기
            </label>
          </div>
          <DataTable
            rows={draft.cards.filter(
              (row) =>
                !row.id ||
                showStopped ||
                row.values.useYn === "Y" ||
                (/^[0-9]+$/.test(row.values.amount ?? "") &&
                  BigInt(row.values.amount!) > 0n),
            )}
            rowKey={(row) => row.key}
            empty="등록된 카드가 없습니다."
            columns={[
              {
                id: "name",
                label: "카드 이름 *",
                render: (row) =>
                  row.id ? (
                    row.values.cardName
                  ) : (
                    <input
                      data-testid={`financial-card-${row.key}-name`}
                      aria-label="새 카드 이름"
                      value={row.values.cardName ?? ""}
                      maxLength={100}
                      disabled={disabled}
                      onChange={(event) =>
                        cardUpdate(row.key, "cardName", event.target.value)
                      }
                    />
                  ),
              },
              {
                id: "last4",
                label: "끝 4자리 *",
                render: (row) =>
                  row.id ? (
                    row.values.last4
                  ) : (
                    <input
                      data-testid={`financial-card-${row.key}-last-four`}
                      aria-label="새 카드 끝 4자리"
                      value={row.values.last4 ?? ""}
                      maxLength={4}
                      disabled={disabled}
                      onChange={(event) =>
                        cardUpdate(row.key, "last4", event.target.value)
                      }
                    />
                  ),
              },
              {
                id: "amount",
                label: "해당 월 결제액",
                render: (row) => (
                  <WonInput
                    data-testid={`financial-card-${row.key}-amount`}
                    aria-label={`${row.key} 결제액`}
                    value={row.values.amount ?? ""}
                    disabled={disabled}
                    onValueChange={(value) =>
                      cardUpdate(row.key, "amount", value)
                    }
                  />
                ),
              },
              {
                id: "state",
                label: "상태",
                render: (row) => (
                  <StatusBadge tone={row.values.useYn === "Y" ? "ok" : "lock"}>
                    {row.values.useYn === "Y" ? "사용" : "삭제"}
                  </StatusBadge>
                ),
              },
              {
                id: "action",
                label: "삭제/되살리기",
                render: (row) => (
                  <button
                    type="button"
                    data-testid={`financial-card-${row.key}-state`}
                    disabled={disabled}
                    onClick={() => {
                      if (!row.id)
                        state.setDraft((old) =>
                          old
                            ? {
                                ...old,
                                cards: old.cards.filter(
                                  (card) => card.key !== row.key,
                                ),
                              }
                            : old,
                        );
                      else
                        cardUpdate(
                          row.key,
                          "useYn",
                          row.values.useYn === "Y" ? "N" : "Y",
                        );
                    }}
                  >
                    {row.values.useYn === "Y" ? "삭제" : "되살리기"}
                  </button>
                ),
              },
            ]}
          />
          <button
            type="button"
            data-testid="financial-card-add"
            disabled={disabled}
            onClick={() =>
              state.setDraft((old) =>
                old
                  ? {
                      ...old,
                      cards: [
                        ...old.cards,
                        {
                          key: `new-card-${++next.current}`,
                          values: {
                            cardName: "",
                            last4: "",
                            useYn: "Y",
                            amount: "0",
                          },
                        },
                      ],
                    }
                  : old,
              )
            }
          >
            카드 추가
          </button>
          <div className="financial-sums">
            <span>카드별 합계 {amount(editor.cardMonth.cardSumAmount)}</span>
            <label>
              자금수지 반영합계 (원){" "}
              <WonInput
                data-testid="financial-card-cash-override"
                disabled={disabled}
                value={
                  draft.cashOverride?.mode === "MANUAL"
                    ? (draft.cashOverride.amount ?? "")
                    : editor.cardMonth.cashAmount
                }
                onValueChange={(value) =>
                  state.setDraft((old) =>
                    old
                      ? {
                          ...old,
                          cashOverride: { mode: "MANUAL", amount: value },
                        }
                      : old,
                  )
                }
              />
            </label>
            <StatusBadge
              tone={
                draft.cashOverride?.mode === "MANUAL" ||
                (!draft.cashOverride && editor.cardMonth.editYn === "Y")
                  ? "warn"
                  : "info"
              }
            >
              {draft.cashOverride?.mode === "MANUAL" ||
              (!draft.cashOverride && editor.cardMonth.editYn === "Y")
                ? "직접 고침"
                : "자동"}
            </StatusBadge>
            <span>차액 {amount(editor.cardMonth.difference)}</span>
            <button
              type="button"
              data-testid="financial-card-auto"
              disabled={disabled}
              onClick={() =>
                state.setDraft((old) =>
                  old ? { ...old, cashOverride: { mode: "AUTO" } } : old,
                )
              }
            >
              자동 합계로
            </button>
          </div>
        </section>
        {panel("transfers")}
        <p>
          계좌이체(제경비) {amount(editor.contributions.cashTransferExpense)} /
          특별비(미지급 인건비) {amount(editor.contributions.cashUnpaidLabor)} /
          전체 {amount(editor.contributions.transferTotal)}
        </p>
        {panel("loans")}
        <p>
          받음 합계 {amount(editor.contributions.cashLoanIn)} / 갚음 합계{" "}
          {amount(editor.contributions.cashLoanOut)} / 순액{" "}
          {amount(editor.contributions.cashLoanNet)}
        </p>
      </div>
      {history && (
        <FinancialHistoryPanel
          month={editor.month}
          refreshKey={editor.revision}
        />
      )}
      <footer className="financial-footer">
        <small>금액 입력은 원 정수 · 두 탭을 함께 저장합니다.</small>
        <button
          type="button"
          data-testid="financial-cancel"
          disabled={state.pending || !state.dirty}
          onClick={state.discard}
        >
          취소
        </button>
        <button
          type="button"
          className="sicost-primary"
          data-testid="financial-save"
          disabled={disabled || !state.dirty}
          onClick={() => void state.save()}
        >
          저장
        </button>
      </footer>
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
          editable={editor.editable}
          onClose={() => setPayment(undefined)}
          onSaved={async () => {
            await refresh();
          }}
        />
      )}
    </section>
  );
}
