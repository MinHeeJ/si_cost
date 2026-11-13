import { useEffect, useState } from "react";
import { DataTable } from "../../shared/DataTable";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { formatDecimal } from "../../shared/format";
import * as api from "./corporateTaxApi";
import "./corporateTax.css";

export function CorporateTaxAllocationScreen({ year }: { year: string }) {
  const [data, setData] = useState<api.CorporateTaxAllocation>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let live = true;
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void api
      .getCorporateTaxAllocation(year)
      .then((value) => {
        if (live) setData(value);
      })
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [year, refresh]);
  useEffect(() => {
    const reload = () => setRefresh((n) => n + 1);
    window.addEventListener(api.CORPORATE_TAX_COMMITTED, reload);
    return () =>
      window.removeEventListener(api.CORPORATE_TAX_COMMITTED, reload);
  }, []);
  return (
    <section
      className="corporate-tax-section"
      data-testid="corporate-tax-allocation"
      data-screen-id="SCR-UI-10-ALLOCATION"
      aria-label="법인세 배분 조회"
    >
      <header className="corporate-tax-heading">
        <h2>법인세 {year}년 · 월별 배분</h2>
        <button
          type="button"
          data-testid="corporate-tax-allocation-reload"
          disabled={loading}
          onClick={() => setRefresh((n) => n + 1)}
        >
          다시 조회
        </button>
      </header>
      <p className="corporate-tax-description">
        1년 예상액을 달마다 나눠 순이익표에 반영 · 마감한 달은 저장된 법인세를
        유지합니다.
      </p>
      {Boolean(error) && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "법인세 배분 조회에 실패했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRefresh((n) => n + 1)}
        />
      )}
      {data && (
        <>
          <dl className="corporate-tax-summary">
            <div>
              <dt>연간 예상 법인세</dt>
              <dd>{won(data.estimatedTax)}</dd>
            </div>
            <div>
              <dt>마감 합계</dt>
              <dd>{won(data.closedTotal)}</dd>
            </div>
            <div>
              <dt>남은 금액</dt>
              <dd
                className={
                  data.remainingAmount?.startsWith("-")
                    ? "corporate-tax-negative"
                    : undefined
                }
              >
                {won(data.remainingAmount)}
              </dd>
            </div>
            <div>
              <dt>미마감 월</dt>
              <dd>{data.openMonthCount}개월</dd>
            </div>
          </dl>
          {data.warnings.map((warning) => (
            <p
              role="alert"
              key={warning}
              className={`sicost-banner ${warning === "금액이 맞는지 확인하세요" ? "sicost-tone-bad" : "sicost-tone-warn"}`}
            >
              {warning}
            </p>
          ))}
        </>
      )}
      {!error && (
        <DataTable
          rows={data?.months ?? []}
          rowKey={(row) => row.month}
          loading={loading}
          empty="배분할 운영월이 없습니다."
          columns={[
            {
              id: "month",
              label: "월",
              render: (row) => (
                <span data-testid={`corporate-tax-month-${row.month}`}>
                  {row.month}
                </span>
              ),
            },
            {
              id: "close",
              label: "상태",
              render: (row) => (
                <span
                  className={`corporate-tax-badge ${row.closeStatus === "CLOSE" ? "corporate-tax-lock" : "corporate-tax-info"}`}
                >
                  {row.closeStatus === "CLOSE" ? "마감" : "작업중"}
                </span>
              ),
            },
            {
              id: "amount",
              label: "법인세 배분액 (원)",
              align: "right",
              render: (row) => (
                <span
                  className={
                    row.amount?.startsWith("-")
                      ? "corporate-tax-negative"
                      : undefined
                  }
                >
                  {row.verified ? won(row.amount) : "미확인"}
                </span>
              ),
            },
            {
              id: "copy",
              label: "자료 구분",
              render: (row) =>
                row.closeStatus === "CLOSE" ? (
                  <>
                    {row.fixed === "Y" ? "고정 " : ""}
                    {row.migrated === "Y" ? "이관 " : ""}마감 사본
                  </>
                ) : (
                  <span className="corporate-tax-badge corporate-tax-neutral">
                    계산
                  </span>
                ),
            },
          ]}
        />
      )}
    </section>
  );
}
function won(value: string | null) {
  return value === null ? "미확인" : `${formatDecimal(value)} 원`;
}
