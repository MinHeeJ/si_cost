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
  pageIds: string[];
  mobile?: boolean;
};
export function ReceiptBatchAction(props: Props) {
  const context = JSON.stringify(props.filters);
  return <ReceiptBatchActionContent key={context} {...props} />;
}
function ReceiptBatchActionContent({
  filters,
  pageIds,
  today,
  minimumDate,
  projectNames,
  onRefresh,
  mobile,
}: Props) {
  const [selection, setSelection] = useState<ReceiptBatchSelection>(),
    [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false),
    [all, setAll] = useState(false);
  const controller = useRef<AbortController>();
  useEffect(() => () => controller.current?.abort(), []);
  async function prepare(whole: boolean) {
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;
    setLoading(true);
    setError(undefined);
    setSelection(undefined);
    setAll(whole);
    try {
      const data = await loadReceiptBatchSelection(
        filters,
        whole ? undefined : pageIds,
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
        data-testid="receipt-batch-page"
        disabled={readOnly || loading || pageIds.length === 0}
        onClick={() => void prepare(false)}
      >
        페이지 선택 · 여러 청구에 한 번에 입금
      </button>
      <button
        type="button"
        data-testid="receipt-batch-all"
        disabled={readOnly || loading}
        onClick={() => void prepare(true)}
      >
        조회전체 선택
      </button>
      {loading && <span role="status">입금 대상을 불러오는 중입니다.</span>}
      {error && (
        <ErrorBanner message={error} onRetry={() => void prepare(all)} />
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
          onReload={() => prepare(all)}
          onRefresh={onRefresh}
        />
      )}
    </section>
  );
}
