import { useEffect, useRef, useState } from "react";
import { ErrorBanner } from "../../shared/Feedback";
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
    [message, setMessage] = useState("");
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  const download = async () => {
    if (request.current) return;
    const controller = new AbortController();
    request.current = controller;
    setPending(true);
    setMessage("");
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
        setMessage("전체 결과 엑셀을 내려받았습니다.");
      }
    } catch (error) {
      if (!controller.signal.aborted)
        setMessage(
          error instanceof Error
            ? error.message
            : "엑셀 다운로드에 실패했습니다.",
        );
    } finally {
      request.current = null;
      if (!controller.signal.aborted) setPending(false);
    }
  };
  return (
    <div data-testid="profitability-export" className="profitability-toolbar profitability-export">
      <button
        type="button"
        className="sicost-button"
        data-testid="profitability-export-button"
        disabled={pending || preferences.loading || preferences.pending}
        onClick={() => void download()}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4" /></svg>
        {pending ? "엑셀 생성 중…" : "엑셀 다운로드"}
      </button>
      {message &&
        (message === "전체 결과 엑셀을 내려받았습니다." ? (
          <span role="status">{message}</span>
        ) : (
          <ErrorBanner message={message} />
        ))}
    </div>
  );
}
