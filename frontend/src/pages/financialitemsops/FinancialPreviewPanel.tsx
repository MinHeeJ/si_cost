import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import {
  createFinancialPreview,
  financialPreviousAddition,
  formatFinancialWon,
  type FinancialDraftChanges,
  type FinancialDraftItem,
  type FinancialItemAddition,
  type FinancialPreviewResult,
} from "./financialPreviewApi";
import "./financialPreview.css";

export type FinancialPreviewPanelProps = {
  month: string;
  revision: string;
  changes: FinancialDraftChanges;
  editable: boolean;
  readOnlyReason?: string | null;
  tab?: "FINANCIAL_TAX_ADJUSTMENT" | "CARD_TRANSFER_LOAN";
  unit?: "원" | "천원" | "백만원";
  onAddDrafts(
    rows: FinancialItemAddition[],
    drafts: FinancialDraftItem[],
  ): void | Promise<void>;
  onCalculated?(result: FinancialPreviewResult): void;
  onValidationError?(error: ApiError): void;
};
export function FinancialPreviewPanel({
  month,
  revision,
  changes,
  editable,
  readOnlyReason,
  tab = "FINANCIAL_TAX_ADJUSTMENT",
  unit = "천원",
  onAddDrafts,
  onCalculated,
  onValidationError,
}: FinancialPreviewPanelProps) {
  const [result, setResult] = useState<FinancialPreviewResult>();
  const [error, setError] = useState<unknown>();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState("");
  const [retry, setRetry] = useState(0);
  const generation = useRef(0),
    copying = useRef(false);
  const callbacks = useRef({ onCalculated, onValidationError, onAddDrafts });
  callbacks.current = { onCalculated, onValidationError, onAddDrafts };
  const payload = JSON.stringify({
    ...changes,
    month,
    expectedRevision: revision,
  });
  useEffect(() => {
    const current = ++generation.current;
    const controller = new AbortController();
    setResult(undefined);
    setError(undefined);
    setMessage("");
    if (!editable || !revision) {
      setPending(false);
      return () => controller.abort();
    }
    setPending(true);
    createFinancialPreview(
      { ...JSON.parse(payload), action: "CALCULATE" },
      controller.signal,
    )
      .then((value) => {
        if (controller.signal.aborted || generation.current !== current) return;
        setResult(value);
        callbacks.current.onCalculated?.(value);
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted || generation.current !== current) return;
        setError(cause);
        if (cause instanceof ApiError)
          callbacks.current.onValidationError?.(cause);
      })
      .finally(() => {
        if (!controller.signal.aborted && generation.current === current)
          setPending(false);
      });
    return () => {
      controller.abort();
      generation.current++;
    };
  }, [payload, editable, revision, retry]);
  async function loadPrevious() {
    if (!editable || pending || copying.current) return;
    copying.current = true;
    const current = ++generation.current;
    setPending(true);
    setError(undefined);
    setResult(undefined);
    setMessage("");
    try {
      const value = await createFinancialPreview({
        ...JSON.parse(payload),
        action: "LOAD_PREVIOUS",
      });
      if (current !== generation.current) return;
      if (value.addedDrafts.length)
        await callbacks.current.onAddDrafts(
          value.addedDrafts.map(financialPreviousAddition),
          value.addedDrafts,
        );
      if (current !== generation.current) return;
      setMessage(value.message);
      setResult(value);
      callbacks.current.onCalculated?.(value);
    } catch (cause) {
      if (current === generation.current) {
        setError(cause);
        if (cause instanceof ApiError)
          callbacks.current.onValidationError?.(cause);
      }
    } finally {
      copying.current = false;
      if (current === generation.current) setPending(false);
    }
  }
  const contributions = result?.contributions;
  const labels =
    tab === "FINANCIAL_TAX_ADJUSTMENT"
      ? ([
          ["판관비 입력 합계", "sgaAmount"],
          ["판관비 부가세", "sgaVatAmount"],
          ["영업외수익 합계", "nonOperatingIncome"],
          ["영업외수익 부가세", "nonOperatingIncomeVat"],
          ["영업외비용 합계", "nonOperatingExpense"],
          ["영업외비용 부가세", "nonOperatingExpenseVat"],
          ["법인세(월할)", "corporateTaxAllocation"],
          ["조정 합계", "adjustmentProfit"],
        ] as const)
      : ([
          ["자금수지 반영합계", "cashCardExpense"],
          ["계좌이체(제경비) 합계", "cashTransferExpense"],
          ["특별비(미지급 인건비) 합계", "cashUnpaidLabor"],
          ["계좌이체 전체 합계", "transferTotal"],
          ["받음 합계", "cashLoanIn"],
          ["갚음 합계", "cashLoanOut"],
          ["대여금 순액", "cashLoanNet"],
        ] as const);
  return (
    <section
      className="financial-preview"
      data-testid="financial-preview"
      data-screen-id="SCR-UI-10-PREVIEW"
      aria-label="재무 초안 미리보기"
      aria-busy={pending}
    >
      <div className="financial-preview-actions">
        <button
          type="button"
          data-testid="financial-load-previous"
          disabled={!editable || pending}
          onClick={() => void loadPrevious()}
        >
          지난달 항목 불러오기
        </button>
        <StatusBadge tone="neutral">계산</StatusBadge>
        <span>미리보기는 저장 전 초안이며 두 탭을 함께 계산합니다.</span>
      </div>
      {!editable && (
        <p className="sicost-banner sicost-tone-lock">
          {readOnlyReason ||
            "읽기 전용 기간입니다. 저장된 합계를 확인해 주세요."}
        </p>
      )}
      {pending && <p role="status">초안을 계산하는 중입니다.</p>}
      {Boolean(error) && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "미리보기를 계산하지 못했습니다. 초안은 유지됩니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRetry((value) => value + 1)}
        />
      )}
      {message && (
        <p role="status" className="sicost-banner sicost-tone-info">
          {message}
        </p>
      )}
      {contributions && (
        <dl className="financial-preview-totals">
          {labels.map(([label, key]) => (
            <div key={key}>
              <dt>{label}</dt>
              <dd
                className={
                  contributions[key]?.startsWith("-")
                    ? "financial-negative"
                    : ""
                }
              >
                {formatFinancialWon(contributions[key], unit)}{" "}
                <small>{unit}</small>
              </dd>
            </div>
          ))}
        </dl>
      )}
      {result && tab === "FINANCIAL_TAX_ADJUSTMENT" && (
        <div
          className="financial-allocation"
          data-testid="financial-allocation"
        >
          <h2>
            법인세 {result.allocation.year}년 · 1년 예상액을 달마다 나눠
            순이익표에 반영
          </h2>
          <p>
            마감월 반영액{" "}
            {formatFinancialWon(result.allocation.closedTotal, unit)} / 남은액{" "}
            {formatFinancialWon(result.allocation.remainingAmount, unit)} / 열린
            운영월 수 {result.allocation.openMonthCount}
          </p>
          <div className="financial-preview-table">
            <table>
              <thead>
                <tr>
                  <th>월</th>
                  <th>상태</th>
                  <th>월할액 ({unit})</th>
                  <th>확인상태</th>
                </tr>
              </thead>
              <tbody>
                {result.allocation.months.map((row) => (
                  <tr
                    key={row.month}
                    data-testid={`financial-allocation-${row.month}`}
                  >
                    <td>{row.month}</td>
                    <td>
                      <StatusBadge
                        tone={row.closeStatus === "CLOSE" ? "lock" : "info"}
                      >
                        {row.closeStatus === "CLOSE" ? "마감" : "작업중"}
                      </StatusBadge>
                    </td>
                    <td
                      className={
                        row.amount?.startsWith("-") ? "financial-negative" : ""
                      }
                    >
                      {formatFinancialWon(row.amount, unit)}
                    </td>
                    <td>
                      {row.verified ? "확인" : "미확인"}
                      {row.fixed === "Y" ? " · 고정" : ""}
                      {row.migrated === "Y" ? " · 이관" : ""}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {result?.contributions.warnings.map((warning) => (
        <p className="sicost-banner sicost-tone-warn" key={warning}>
          {warning}
        </p>
      ))}
    </section>
  );
}
