import { useEffect, useState, type ReactNode } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { projectAmount } from "../projects/projectFormat";
import { formatDecimal } from "../../shared/format";
import type { Completeness } from "./projectAnalysisApi";
export type AnalysisUnit = "원" | "천원" | "백만원";
export function analysisDisplay(
  value: unknown,
  kind: "amount" | "rate" | "text" = "text",
  unit: AnalysisUnit = "원",
) {
  if (value == null) return "–";
  if (kind === "amount") return projectAmount(String(value), unit);
  if (kind === "rate") return `${String(value)}%`;
  return typeof value === "string" && /^-?\d+(\.\d+)?$/.test(value)
    ? formatDecimal(value)
    : String(value);
}
export function AnalysisSummary({
  fields,
  summary,
  unit,
}: {
  fields: readonly (readonly [string, string, boolean?])[];
  summary: Record<string, unknown>;
  unit?: AnalysisUnit;
}) {
  return (
    <div className="project-analysis-summary">
      {fields.map(([key, label, money]) => (
        <section
          key={key}
          data-testid={`analysis-summary-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
        >
          <span>{label}</span>
          <strong>
            {analysisDisplay(summary[key], money ? "amount" : "text", unit)}
          </strong>
        </section>
      ))}
    </div>
  );
}
export function AnalysisCompleteness({ value }: { value: Completeness }) {
  return (
    <div className="project-analysis-basis">
      <span>
        집계 {value.rangeStartMonth} ~ {value.rangeEndMonth}
      </span>
      {value.estimated && <StatusBadge tone="info">예정 원가 포함</StatusBadge>}
      {value.missingBeforeMonth && (
        <span>운영 전 원가 누락 · {value.missingBeforeMonth}부터 조회</span>
      )}
      {value.unknownProjectCount > 0 && (
        <StatusBadge tone="warn">
          미확인 {value.unknownProjectCount}건
        </StatusBadge>
      )}
      {value.issues.length > 0 && (
        <details>
          <summary>미확인·추정 근거 {value.issues.length}건</summary>
          <ul>
            {value.issues.map((issue, index) => (
              <li
                data-testid={`analysis-reason-${index}`}
                key={`${issue.code}-${index}`}
              >
                {issue.month} {issue.personId} {issue.message} ({issue.metric})
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
export function AnalysisProgress({
  value,
  label,
}: {
  value: unknown;
  label: string;
}) {
  return (
    <div className="project-analysis-progress">
      <span>
        {label} {analysisDisplay(value, "rate")}
      </span>
      {value != null && (
        <div className="project-analysis-track">
          <i style={{ width: `max(0%, min(100%, ${String(value)}%))` }} />
        </div>
      )}
    </div>
  );
}
export function AnalysisFailure({
  error,
  retry,
}: {
  error: unknown;
  retry(): void;
}) {
  if (!error) return null;
  return (
    <>
      <ErrorBanner
        message={
          error instanceof Error ? error.message : "조회하지 못했습니다."
        }
        requestId={error instanceof ApiError ? error.body.requestId : undefined}
        onRetry={retry}
      />
      {error instanceof ApiError &&
        Object.entries(error.fieldMessages).map(([key, message]) => (
          <p role="alert" key={key}>
            {key}: {message}
          </p>
        ))}
    </>
  );
}
export function useAnalysisLoad<T>(
  load: (signal: AbortSignal) => Promise<T>,
  dependency: string,
) {
  const [data, setData] = useState<T>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true),
    [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let live = true;
    setLoading(true);
    setError(undefined);
    void load(controller.signal)
      .then((value) => {
        if (live) setData(value);
      })
      .catch((e) => {
        if (live && !controller.signal.aborted) setError(e);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
      controller.abort();
    };
    // The serialized query is the dependency; the closure deliberately captures that exact query.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dependency, reload]);
  useEffect(() => {
    const refresh = () => setReload((value) => value + 1);
    window.addEventListener("sicost:projects-committed", refresh);
    return () =>
      window.removeEventListener("sicost:projects-committed", refresh);
  }, []);
  return { data, error, loading, retry: () => setReload((value) => value + 1) };
}
export type AnalysisSlots = {
  renderExport?(
    query: import("./projectAnalysisApi").AnalysisQuery,
    unit?: AnalysisUnit,
  ): ReactNode;
  renderColumns?(): ReactNode;
  columnView?: import("../me/preferencesApi").ColumnView;
  dense?: boolean;
};
