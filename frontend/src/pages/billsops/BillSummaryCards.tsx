import { useCallback, useEffect, useState } from "react";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatDecimal } from "../../shared/format";
import * as api from "./billOperationsApi";
import "./billOperations.css";

export function billAmount(
  amount: string,
  unit: "원" | "천원" | "백만원" = "천원",
) {
  const digits = unit === "원" ? 0 : unit === "천원" ? 3 : 6;
  const value = amount.padStart(digits + 1, "0");
  return formatDecimal(
    digits ? `${value.slice(0, -digits)}.${value.slice(-digits)}` : value,
  );
}
export function BillingError({
  error,
  onRetry,
}: {
  error: unknown;
  onRetry?: () => void;
}) {
  if (!error) return null;
  return (
    <ErrorBanner
      message={
        error instanceof Error ? error.message : "요청을 처리하지 못했습니다."
      }
      requestId={error instanceof ApiError ? error.body.requestId : undefined}
      onRetry={onRetry}
    />
  );
}
export function BillSummaryCards({
  year,
  includeCarryover,
  refreshKey,
  amountUnit = "천원",
}: {
  year: number;
  includeCarryover: boolean;
  refreshKey?: number;
  amountUnit?: "원" | "천원" | "백만원";
}) {
  const [data, setData] = useState<api.BillsSummary>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);
    void api
      .getBillsSummary(year, includeCarryover)
      .then((value) => {
        if (active) setData(value);
      })
      .catch((e) => {
        if (active) setError(e);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [year, includeCarryover, refreshKey, retry]);
  return (
    <section
      data-screen-id="SCR-UI-13"
      data-testid="bill-summary-cards"
      aria-busy={loading}
    >
      <BillingError
        error={error}
        onRetry={() => setRetry((value) => value + 1)}
      />
      {loading ? (
        <p role="status">청구 요약을 불러오는 중입니다.</p>
      ) : (
        !error &&
        data && (
          <div className="bill-summary-grid">
            {(
              [
                ["청구 총액", data.billed],
                ["이번달 청구", data.currentMonth],
                ["미수금", data.unpaid],
              ] as const
            ).map(([label, card], index) => (
              <article
                className="bill-summary-card"
                key={label}
                data-testid={`bill-summary-${index}`}
              >
                <span>{label}</span>
                <strong>
                  {billAmount(card.amount, amountUnit)} {amountUnit}
                </strong>
                <span>{card.count}건</span>
                <small>{card.scope}</small>
              </article>
            ))}
            <article
              className="bill-summary-card"
              data-testid="bill-summary-late"
            >
              <span>연체</span>
              <strong>{data.lateCount}건</strong>
              <small>{data.lateScope}</small>
            </article>
          </div>
        )
      )}
    </section>
  );
}
export function BillEstimatePanel({
  projectId,
  month,
  onEstimate,
}: {
  projectId: string;
  month: string;
  onEstimate?(estimate: api.BillEstimate): void;
}) {
  const [data, setData] = useState<api.BillEstimate>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(false);
  const [retry, setRetry] = useState(0);
  const load = useCallback(
    () => api.getBillEstimate(projectId, month),
    [projectId, month],
  );
  useEffect(() => {
    if (!projectId || !month) return;
    let active = true;
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void load()
      .then((value) => {
        if (active) {
          setData(value);
          onEstimate?.(value);
        }
      })
      .catch((e) => {
        if (active) setError(e);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [load, retry, onEstimate, projectId, month]);
  return (
    <section
      data-screen-id="SCR-UI-13"
      data-testid="bill-estimate-panel"
      aria-busy={loading}
    >
      <BillingError error={error} onRetry={() => setRetry((n) => n + 1)} />
      {loading && <p role="status">추정 근거를 불러오는 중입니다.</p>}
      {data && (
        <>
          <p>
            공급가 추정{" "}
            {data.supplyAmount === null
              ? "직접 입력"
              : `${formatDecimal(data.supplyAmount)} 원`}
          </p>
          {data.warnings.map((warning, i) => (
            <p className="sicost-banner sicost-tone-warn" key={i}>
              {warning}
            </p>
          ))}
          {data.sources.length === 0 ? (
            <p className="sicost-empty">해당월 유상 투입 자료가 없습니다.</p>
          ) : (
            <ul>
              {data.sources.map((row) => (
                <li
                  data-testid={`bill-source-${row.assignmentId.toLowerCase()}`}
                  key={row.assignmentId}
                >
                  {row.developerName} · {row.managementNo} · {row.mm} MM ·{" "}
                  {formatDecimal(row.appliedUnitPrice)} 원 ·{" "}
                  {formatDecimal(row.supplyAmount)} 원{" "}
                  {row.protected && "보호월"}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
