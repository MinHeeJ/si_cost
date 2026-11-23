import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ErrorBanner, Toast } from "../../shared/Feedback";
import { usePreferences } from "../me/usePreferences";
import type { ScreenColumnPreferences } from "../me/preferencesApi";
import type { ProfitabilityQuery } from "./individualProfitabilityApi";
import {
  getProfitabilityExport,
  saveProfitabilityDownload,
} from "./profitabilityExportApi";

/** Composed into the existing renderExport slot by the final wiring group. */
export function ProfitabilityExportButton({
  query,
}: {
  query: ProfitabilityQuery;
}) {
  const preferences = usePreferences();
  const [pending, setPending] = useState(false),
    [errorMessage, setErrorMessage] = useState(""),
    [successCount, setSuccessCount] = useState(0);
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => {
    if (!successCount) return;
    const timer = window.setTimeout(() => setSuccessCount(0), 3500);
    return () => window.clearTimeout(timer);
  }, [successCount]);
  const download = async () => {
    if (request.current) return;
    const controller = new AbortController();
    request.current = controller;
    setPending(true);
    setErrorMessage("");
    const columns = (
      preferences.get("COLS", "UI-03")?.value as
        | ScreenColumnPreferences
        | undefined
    )?.individualProfitability;
    const unit = preferences.get("AMT_UNIT")?.value;
    try {
      const result = await getProfitabilityExport(
        query,
        {
          ...columns,
          unit: unit === "원" || unit === "백만원" ? unit : "천원",
        },
        controller.signal,
      );
      if (!controller.signal.aborted) {
        saveProfitabilityDownload(result);
        setSuccessCount((count) => count + 1);
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        setSuccessCount(0);
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "엑셀 다운로드에 실패했습니다.",
        );
      }
    } finally {
      request.current = null;
      if (!controller.signal.aborted) setPending(false);
    }
  };
  return (
    <ScreenTitleActions>
      <div
        data-testid="profitability-export"
        className="profitability-toolbar profitability-export"
      >
        <button
          type="button"
          className="sicost-button"
          data-testid="profitability-export-button"
          disabled={pending || preferences.loading || preferences.pending}
          onClick={() => void download()}
        >
          {pending ? "엑셀 생성 중…" : "엑셀 다운로드"}
        </button>
        {errorMessage && <ErrorBanner message={errorMessage} />}
      </div>
      {successCount > 0 &&
        createPortal(
          <div className="sicost-toast-stack">
            <Toast key={successCount} message="전체 결과 엑셀을 내려받았습니다." />
          </div>,
          document.body,
        )}
    </ScreenTitleActions>
  );
}
