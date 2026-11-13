import { useEffect, useRef, useState } from "react";
import { ErrorBanner } from "../../shared/Feedback";
import {
  ReceiptBatchDialog,
  type ReceiptBatchDialogProps,
} from "./ReceiptBatchDialog";
import {
  loadReceiptBatchSelection,
  type ReceiptBatchFilters,
  type ReceiptBatchSelection,
} from "./receiptBatchApi";
import "./receiptBatch.css";

type Props = Pick<
  ReceiptBatchDialogProps,
  "today" | "minimumDate" | "projectNames" | "onRefresh"
> & {
  filters: ReceiptBatchFilters;
  /** 목록에서 고른 청구 번호들. `whole`이 아니면 이 행들이 입금 대상이다. */
  selectedIds: string[];
  /** 조회된 전체를 대상으로 삼는다(고른 행이 아니라 현재 조건에 맞는 모든 청구). */
  whole?: boolean;
  mobile?: boolean;
};
export function ReceiptBatchAction(props: Props) {
  const context = JSON.stringify(props.filters) + (props.whole ? ":whole" : "");
  return <ReceiptBatchActionContent key={context} {...props} />;
}
function ReceiptBatchActionContent({
  filters,
  selectedIds,
  whole = false,
  today,
  minimumDate,
  projectNames,
  onRefresh,
  mobile,
}: Props) {
  const [selection, setSelection] = useState<ReceiptBatchSelection>(),
    [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false);
  const controller = useRef<AbortController>();
  useEffect(() => () => controller.current?.abort(), []);
  async function prepare() {
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;
    setLoading(true);
    setError(undefined);
    setSelection(undefined);
    try {
      const data = await loadReceiptBatchSelection(
        filters,
        whole ? undefined : selectedIds,
        request.signal,
      );
      if (!request.signal.aborted) setSelection(data);
    } catch (reason) {
      if (!request.signal.aborted)
        setError(
          reason instanceof Error
            ? reason.message
            : "입금 대상을 불러오지 못했습니다.",
        );
    } finally {
      if (!request.signal.aborted) setLoading(false);
    }
  }
  if (mobile) return null;
  const readOnly = filters.year < Number(today.slice(0, 4));
  return (
    <section
      className="receipt-batch-action"
      data-testid="receipt-batch-action"
    >
      <button
        type="button"
        className="sicost-primary"
        data-testid="receipt-batch-open"
        disabled={readOnly || loading || (!whole && selectedIds.length === 0)}
        onClick={() => void prepare()}
      >
        일괄 입금
      </button>
      {loading && <span role="status">입금 대상을 불러오는 중입니다.</span>}
      {error && (
        <ErrorBanner message={error} onRetry={() => void prepare()} />
      )}
      {selection && (
        <ReceiptBatchDialog
          key={JSON.stringify(selection)}
          year={filters.year}
          today={today}
          minimumDate={minimumDate}
          selection={selection}
          projectNames={projectNames}
          onClose={() => setSelection(undefined)}
          onReload={() => prepare()}
          onRefresh={onRefresh}
        />
      )}
    </section>
  );
}
