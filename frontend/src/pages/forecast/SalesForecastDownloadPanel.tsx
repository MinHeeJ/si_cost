import { ErrorBanner, FloatingToast } from "../../shared/Feedback";
import { useEffect, useState } from "react";
import { ApiError } from "../../shared/api";
import * as api from "./salesForecastApi";

export function SalesForecastDownloadPanel({
  amountUnit,
}: {
  amountUnit: api.ForecastAmountUnit;
}) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const [complete, setComplete] = useState(false);
  const [mobile, setMobile] = useState(
    () => window.matchMedia?.("(max-width: 767px)")?.matches ?? false,
  );
  useEffect(() => {
    const media = window.matchMedia?.("(max-width: 767px)");
    const change = () => setMobile(media?.matches ?? false);
    media?.addEventListener?.("change", change);
    return () => media?.removeEventListener?.("change", change);
  }, []);
  const download = async () => {
    if (pending || window.matchMedia?.("(max-width: 767px)")?.matches) return;
    setPending(true);
    setError(undefined);
    setComplete(false);
    try {
      api.saveSalesForecastDownload(
        await api.getSalesOpportunityForecastExport(amountUnit),
      );
      setComplete(true);
    } catch (e) {
      setError(e);
    } finally {
      setPending(false);
    }
  };
  return (
    <section
      className="sales-forecast-download"
      data-testid="forecast-download-panel"
      data-screen-id="SCR-UI-32-B140-EXPORT"
    >
      {mobile ? (
        <span role="note">엑셀 다운로드는 PC에서 이용해 주세요.</span>
      ) : (
        <button
          className="sicost-download-button"
          data-testid="forecast-export-button"
          disabled={pending}
          onClick={() => void download()}
        >
          {pending ? "파일 생성 중…" : "엑셀 다운로드"}
        </button>
      )}
      {error != null && (
        <ErrorBanner
          title="엑셀 다운로드 실패"
          message={error instanceof Error ? error.message : "다운로드에 실패했습니다."}
          requestId={error instanceof ApiError ? error.body.requestId : undefined}
          onDismiss={() => setError(undefined)}
        />
      )}
      {complete && <FloatingToast message="다운로드했습니다." />}
    </section>
  );
}
