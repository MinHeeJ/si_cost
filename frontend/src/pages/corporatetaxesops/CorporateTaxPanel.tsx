import { useEffect, useRef, useState } from "react";
import { DataTable } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { formatDecimal } from "../../shared/format";
import { CorporateTaxAllocationScreen } from "./CorporateTaxAllocationScreen";
import { FinancialPaymentStatus } from "./FinancialPaymentStatus";
import { TaxPaymentDialog } from "./TaxPaymentDialog";
import * as api from "./corporateTaxApi";
import "./corporateTax.css";

export type CorporateTaxPanelProps = {
  editor: api.CorporateTaxContext;
  dirty?: boolean;
  onRefresh(): Promise<api.CorporateTaxContext>;
  onSaveDraft?(): Promise<void>;
  onDiscardDraft?(): void;
};
export function CorporateTaxPanel({
  editor,
  dirty = false,
  onRefresh,
  onSaveDraft,
  onDiscardDraft,
}: CorporateTaxPanelProps) {
  const [context, setContext] = useState(editor);
  const [preparing, setPreparing] = useState(false);
  const [error, setError] = useState<unknown>();
  const [initialized, setInitialized] = useState(false);
  const [prepareAttempt, setPrepareAttempt] = useState(0);
  const [payment, setPayment] = useState<api.CorporateTaxPayment>();
  const [requested, setRequested] = useState<api.CorporateTaxPayment>();
  const [pending, setPending] = useState(false);
  const refreshRef = useRef(onRefresh);
  refreshRef.current = onRefresh;
  useEffect(() => {
    setContext(editor);
  }, [editor]);
  useEffect(() => {
    let live = true;
    setError(undefined);
    setInitialized(false);
    setPayment(undefined);
    setRequested(undefined);
    if (!editor.editable) {
      setPreparing(false);
      return;
    }
    setPreparing(true);
    void api
      .createCorporateTaxDefaults(editor.month)
      .then(async () => {
        if (live) setInitialized(true);
        const fresh = await refreshRef.current();
        if (live) setContext(fresh);
      })
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setPreparing(false);
      });
    return () => {
      live = false;
    };
  }, [editor.month, editor.editable, prepareAttempt]);
  async function refresh() {
    const fresh = await refreshRef.current();
    setContext(fresh);
    return fresh;
  }
  async function open(row: api.CorporateTaxPayment) {
    setError(undefined);
    setPending(true);
    try {
      const fresh = await refresh();
      const latest = fresh.taxPayments.find(
        (p) => p.year === row.year && p.payType === row.payType,
      );
      if (!fresh.editable || !latest || latest.payYn === "Y")
        throw new Error("선택월 또는 최신 납부 상태를 확인해 주세요.");
      setPayment(latest);
      setRequested(undefined);
    } catch (e) {
      setError(e);
    } finally {
      setPending(false);
    }
  }
  async function resolve(save: boolean) {
    if (!requested) return;
    setPending(true);
    setError(undefined);
    try {
      if (save) {
        if (!onSaveDraft) throw new Error("먼저 편집 내용을 저장해 주세요.");
        await onSaveDraft();
      } else {
        onDiscardDraft?.();
      }
      await open(requested);
    } catch (e) {
      setError(e);
    } finally {
      setPending(false);
    }
  }
  return (
    <section
      className="corporate-tax-section"
      aria-label="법인세"
      data-testid="corporate-tax-panel"
    >
      <h2>
        법인세 {context.month.slice(0, 4)}년 · 1년 예상액을 달마다 나눠
        순이익표에 반영
      </h2>
      {!context.editable && (
        <p className="sicost-banner sicost-tone-lock">
          {context.readOnlyReason ?? "선택월은 읽기 전용입니다."}
        </p>
      )}
      {preparing && <p role="status">법인세 기본행을 준비하는 중입니다.</p>}
      {Boolean(error) && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "법인세를 불러오지 못했습니다."
          }
        />
      )}
      {Boolean(error) && initialized && (
        <button
          type="button"
          data-testid="tax-initialized-refresh"
          onClick={() =>
            void refresh()
              .then(() => setError(undefined))
              .catch(setError)
          }
        >
          기본행은 준비됐습니다 · 다시 조회
        </button>
      )}
      {Boolean(error) && !initialized && context.editable && (
        <button
          type="button"
          data-testid="tax-prepare-retry"
          disabled={preparing}
          onClick={() => setPrepareAttempt((n) => n + 1)}
        >
          기본행 준비 다시 시도
        </button>
      )}
      <p>
        연간 예상 법인세:{" "}
        {context.corporateTax === null
          ? "미확인"
          : `${formatDecimal(context.corporateTax.estimatedTax)} 원`}
      </p>
      <CorporateTaxAllocationScreen year={context.month.slice(0, 4)} />
      <DataTable
        rows={context.taxPayments}
        rowKey={(row) => `${row.year}:${row.payType}`}
        empty="등록된 납부 일정이 없습니다."
        columns={[
          {
            id: "date",
            label: "납부일",
            render: (row) => (
              <>
                <span>{row.payYn === "Y" ? row.paidDate : row.dueDate}</span>
                {row.payYn === "Y" && row.paidDate !== row.dueDate && (
                  <small> 예정 {row.dueDate}</small>
                )}
              </>
            ),
          },
          {
            id: "type",
            label: "구분",
            render: (row) =>
              `${row.year}년 ${row.payType === "MID" ? "중간예납" : "확정 신고"}`,
          },
          {
            id: "amount",
            label: "금액",
            align: "right",
            render: (row) => (
              <>
                {row.displayAmount === null
                  ? "미확인"
                  : formatDecimal(row.displayAmount)}{" "}
                {row.manuallyEdited && (
                  <span className="corporate-tax-badge corporate-tax-neutral">
                    직접 고침
                  </span>
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
            id: "confirmation",
            label: "납부 확인",
            render: (row) =>
              row.payYn === "N" ? (
                <button
                  type="button"
                  data-testid={`tax-confirm-${row.year}-${row.payType.toLowerCase()}`}
                  disabled={!context.editable || preparing || pending}
                  onClick={() => {
                    if (dirty) setRequested(row);
                    else void open(row);
                  }}
                >
                  납부 확인
                </button>
              ) : (
                "–"
              ),
          },
        ]}
      />
      {requested && (
        <Dialog
          open
          title="저장 전 변경을 먼저 처리해 주세요"
          onCancel={() => setRequested(undefined)}
          onConfirm={() => resolve(true)}
          pending={pending}
          confirmLabel="먼저 저장"
          confirmDisabled={!onSaveDraft}
        >
          <div data-testid="tax-resolve-draft">
            <p>
              {requested.year}년 납부 확인 · 변경 버리기는 초안만 폐기하며
              저장된 자료는 지우지 않습니다.
            </p>
            {Boolean(error) && (
              <p role="alert">
                {error instanceof Error ? error.message : "저장 실패"}
              </p>
            )}
            <button
              type="button"
              data-testid="tax-discard-draft"
              disabled={pending || !onDiscardDraft}
              onClick={() => void resolve(false)}
            >
              변경 버리기
            </button>
          </div>
        </Dialog>
      )}
      {payment && (
        <TaxPaymentDialog
          payment={payment}
          month={context.month}
          today={context.today}
          cashBaseMonth={context.cashBaseMonth}
          editable={context.editable}
          onClose={() => setPayment(undefined)}
          onSaved={async () => {
            await refresh();
          }}
        />
      )}
    </section>
  );
}
