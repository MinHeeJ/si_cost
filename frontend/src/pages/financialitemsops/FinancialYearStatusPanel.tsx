import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import {
  getFinancialYearStatus,
  type FinancialYearStatus,
} from "./financialPreviewApi";
import "./financialPreview.css";

export type FinancialYearStatusPanelProps = {
  selectedMonth: string;
  onMonthChange(month: string): void | boolean | Promise<void | boolean>;
  refreshKey?: string | number;
};
export function FinancialYearStatusPanel({
  selectedMonth,
  onMonthChange,
  refreshKey,
}: FinancialYearStatusPanelProps) {
  const [data, setData] = useState<FinancialYearStatus>();
  const [error, setError] = useState<unknown>();
  const [retry, setRetry] = useState(0);
  const [loading, setLoading] = useState(true);
  const [moving, setMoving] = useState(false);
  const busy = useRef(false);
  const year = selectedMonth.slice(0, 4);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    getFinancialYearStatus(year, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setData(value);
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(cause);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [year, retry, refreshKey]);
  async function move(month: string) {
    if (busy.current || month === selectedMonth) return;
    busy.current = true;
    setMoving(true);
    try {
      await onMonthChange(month);
    } catch (cause) {
      setError(cause);
    } finally {
      busy.current = false;
      setMoving(false);
    }
  }
  const previousYear = String(Number(year) - 1).padStart(4, "0");
  const nextYear = String(Number(year) + 1).padStart(4, "0");
  return (
    <section
      className="financial-year-status"
      aria-label="연도별 입력 현황"
      aria-busy={loading || moving}
      data-testid="financial-year-status"
      data-screen-id="SCR-UI-10-YEAR-LOAD"
    >
      <header>
        <button
          type="button"
          data-testid="financial-previous-year"
          aria-label="이전 해"
          disabled={
            !data || moving || previousYear < data.cashBaseMonth.slice(0, 4)
          }
          onClick={() => void move(`${previousYear}-12`)}
        >
          ‹
        </button>
        <h2>{year}년</h2>
        <button
          type="button"
          data-testid="financial-next-year"
          aria-label="다음 해"
          disabled={!data || moving || nextYear > data.today.slice(0, 4)}
          onClick={() =>
            void move(
              nextYear === data?.today.slice(0, 4)
                ? data.today.slice(0, 7)
                : `${nextYear}-12`,
            )
          }
        >
          ›
        </button>
        <strong>입력 현황</strong>
        <small>
          달을 누르면 그 달 항목으로 바뀝니다 (헤더 기준 연월도 함께 바뀜)
        </small>
      </header>
      {loading && <p role="status">현황을 불러오는 중입니다.</p>}
      {Boolean(error) && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "현황을 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRetry((value) => value + 1)}
        />
      )}
      {data && (
        <div
          className="financial-month-strip"
          role="group"
          aria-label="12개월 현황"
        >
          {data.months.map((month) => (
            <button
              type="button"
              key={month.month}
              data-testid={`financial-month-${month.month}`}
              aria-pressed={selectedMonth === month.month}
              disabled={!month.selectable || moving}
              onClick={() => void move(month.month)}
            >
              <span>{Number(month.month.slice(5))}월</span>
              <small
                className={
                  month.closeStatus === "CLOSE"
                    ? "financial-month-closed"
                    : "financial-month-open"
                }
              >
                {month.itemCount === null
                  ? "–"
                  : `${month.closeStatus === "CLOSE" ? "마감" : "작업중"} · ${month.itemCount}건`}
              </small>
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
