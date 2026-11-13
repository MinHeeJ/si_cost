import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { usePreferences } from "../me/usePreferences";
import type { ColumnView } from "../me/preferencesApi";
import type { AnalysisQuery } from "../apiops/projectAnalysisApi";
import type { AmountUnit } from "../../shared/format";
import { ProfitabilityExportButton } from "../profitability/ProfitabilityExportButton";
import type { ProfitabilityQuery } from "../profitability/individualProfitabilityApi";
import * as api from "./projectStatusApi";
import "./projectStatus.css";

// SCR-UI-03-EXPORT: preserve the existing UI-03 annual export implementation.
export function IndividualCostDownload({
  query,
}: {
  query: ProfitabilityQuery;
}) {
  return (
    <div
      data-screen-id="SCR-UI-03-EXPORT"
      data-testid="individual-cost-download"
    >
      <ProfitabilityExportButton query={query} />
    </div>
  );
}
export function ProjectAnalysisDownload({
  view,
  query,
  unit,
  columns,
}: {
  view: "statusBoard" | "projectCost";
  query: AnalysisQuery;
  unit?: AmountUnit;
  columns?: ColumnView;
}) {
  const preferences = usePreferences();
  const [pending, setPending] = useState(false),
    [success, setSuccess] = useState(false),
    [error, setError] = useState<unknown>();
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), []);
  const screenId = view === "statusBoard" ? "UI-31" : "UI-04";
  async function download() {
    if (active.current) return;
    const controller = new AbortController();
    active.current = controller;
    setPending(true);
    setError(undefined);
    setSuccess(false);
    const value = preferences.get("COLS", screenId)?.value;
    const stored =
      value && typeof value === "object" && !Array.isArray(value)
        ? (value as Partial<Record<string, ColumnView>>)[view]
        : undefined;
    const storedUnit = preferences.get("AMT_UNIT")?.value;
    try {
      const file = await api.getProjectAnalysisDownload(
        view,
        query,
        {
          ...(columns ?? stored),
          unit:
            unit ??
            (storedUnit === "원" || storedUnit === "백만원"
              ? storedUnit
              : "천원"),
        },
        controller.signal,
      );
      if (!controller.signal.aborted) {
        api.saveProjectAnalysisDownload(file);
        setSuccess(true);
      }
    } catch (e) {
      if (!controller.signal.aborted) setError(e);
    } finally {
      if (active.current === controller) active.current = null;
      if (!controller.signal.aborted) setPending(false);
    }
  }
  function cancel() {
    active.current?.abort();
    active.current = null;
    setPending(false);
  }
  const failure = error ?? preferences.error;
  return (
    <div
      className="project-status-download"
      data-testid={`project-download-${view.toLowerCase()}`}
      data-screen-id={
        view === "statusBoard"
          ? "SCR-UI-31-B138-EXPORT"
          : "SCR-UI-04-B138-EXPORT"
      }
    >
      <button
        type="button"
        data-testid="project-download-button"
        disabled={
          pending ||
          preferences.loading ||
          preferences.pending ||
          Boolean(preferences.error)
        }
        onClick={() => void download()}
      >
        {pending ? "엑셀 생성 중…" : "엑셀 다운로드"}
      </button>
      {pending && (
        <button
          type="button"
          data-testid="project-download-cancel"
          onClick={cancel}
        >
          취소
        </button>
      )}
      {success && <span role="status">전체 결과 엑셀을 내려받았습니다.</span>}
      {Boolean(failure) && (
        <ErrorBanner
          message={
            failure instanceof Error
              ? failure.message
              : "엑셀 다운로드에 실패했습니다."
          }
          requestId={
            failure instanceof ApiError ? failure.body.requestId : undefined
          }
        />
      )}
    </div>
  );
}
