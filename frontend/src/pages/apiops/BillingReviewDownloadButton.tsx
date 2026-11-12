import { useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { saveMasterDataDownload } from "../exportsops/masterDataExportApi";
import { downloadBillingReview, type BillExportFilters } from "./billingReviewExportApi";
import type { ReviewAmountUnit } from "./billingReviewApi";
export function BillingReviewDownloadButton({
  view,
  month,
  amountUnit,
  disabled,
  bills,
}: {
  view: "BILLS" | "BILLING_CHECKS" | "RECEIVABLES";
  month?: string;
  amountUnit: ReviewAmountUnit;
  disabled?: boolean;
  bills?: { filters: BillExportFilters; sort?: string; direction?: string };
}) {
  const [pending, setPending] = useState(false),
    [error, setError] = useState<string>();
  const active = useRef(false);
  async function download() {
    if (
      active.current ||
      disabled ||
      window.matchMedia?.("(max-width: 767px)").matches
    )
      return;
    active.current = true;
    setPending(true);
    setError(undefined);
    try {
      saveMasterDataDownload(
        await downloadBillingReview({ view, month, amountUnit, bills }),
      );
    } catch (e) {
      setError(
        e instanceof ApiError
          ? `${e.message} (문의 번호: ${e.body.requestId})`
          : e instanceof Error
            ? e.message
            : "다운로드에 실패했습니다.",
      );
    } finally {
      active.current = false;
      setPending(false);
    }
  }
  return (
    <>
      <div className="billing-review-download">
        <button
          type="button"
          data-testid={`billing-review-download-${view.toLowerCase().replace(/_/g, "-")}`}
          disabled={disabled || pending}
          onClick={() => void download()}
        >
          {pending ? "파일 생성 중…" : "엑셀 다운로드"}
        </button>
        {error && <ErrorBanner message={error} />}
      </div>
      <p className="billing-review-mobile-notice">
        다운로드는 PC에서 이용해 주세요.
      </p>
    </>
  );
}
