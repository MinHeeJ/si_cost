import { useEffect, useState } from "react";
import { ApiError } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { StatusBadge, ErrorBanner } from "../../shared/Feedback";
import { formatDecimal } from "../../shared/format";
import { formatDistributionAmount } from "../distribution/MonthlyMatrixTable";
import {
  getBillingChecks,
  type BillingCheckRow,
  type BillingCheckRemedy,
  type BillingChecksPage,
  type ReviewAmountUnit,
  type ReviewPageSize,
} from "./billingReviewApi";
import { BillingReviewDownloadButton } from "./BillingReviewDownloadButton";
import "./billingReview.css";

type Props = {
  year: number;
  currentYear: number;
  amountUnit?: ReviewAmountUnit;
  refreshKey?: number | string;
  onRemedy(remedy: BillingCheckRemedy): void;
  onBillDetail(billId: string): void;
  onCount?(count: number): void;
};
export function BillingChecksPanel(props: Props) {
  if (props.year !== props.currentYear)
    return (
      <section
        data-testid="billing-checks-unavailable"
        className="sicost-banner sicost-tone-lock"
      >
        청구 누락 점검은 올해만 제공합니다.
      </section>
    );
  return <BillingChecksContent key={props.year} {...props} />;
}
function BillingChecksContent({
  amountUnit = "천원",
  refreshKey,
  onRemedy,
  onBillDetail,
  onCount,
}: Props) {
  const [month, setMonth] = useState<string>(),
    [page, setPage] = useState(1),
    [size, setSize] = useState<ReviewPageSize>(50);
  const [data, setData] = useState<BillingChecksPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<string>(),
    [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    getBillingChecks({ month, page, size }, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setData(value);
      })
      .catch((e) => {
        if (!controller.signal.aborted)
          setError(
            e instanceof ApiError
              ? `${e.message} (문의 번호: ${e.body.requestId})`
              : "점검 결과를 불러오지 못했습니다.",
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [month, page, size, reload, refreshKey]);
  useEffect(() => {
    if (data) onCount?.(data.summary.tabCount);
  }, [data, onCount]);
  const money = (value: string) => formatDistributionAmount(value, amountUnit);
  const columns: Column<BillingCheckRow>[] = [
    {
      id: "verdict",
      label: "판정",
      align: "center",
      render: (r) => (
        <StatusBadge
          tone={
            r.verdict === "맞음"
              ? "ok"
              : r.verdict === "적게 청구"
                ? "warn"
                : "bad"
          }
        >
          {r.verdict}
        </StatusBadge>
      ),
    },
    { id: "projectName", label: "프로젝트", render: (r) => r.projectName },
    {
      id: "contractType",
      label: "계약유형",
      render: (r) => (r.contractType === "MO" ? "도급" : "파견"),
    },
    {
      id: "personCount",
      label: "투입 인원",
      align: "right",
      render: (r) => r.personCount,
    },
    { id: "mm", label: "투입 MM", align: "right", render: (r) => r.mm },
    {
      id: "estimatedAmount",
      label: `투입 기준 금액 (${amountUnit})`,
      align: "right",
      render: (r) => money(r.estimatedAmount),
    },
    {
      id: "billedAmount",
      label: `청구 공급가 (${amountUnit})`,
      align: "right",
      render: (r) => money(r.billedAmount),
    },
    {
      id: "differenceAmount",
      label: `차이 (${amountUnit})`,
      align: "right",
      render: (r) => (
        <span
          className={r.verdict === "맞음" ? "" : "billing-review-difference"}
        >
          {money(r.differenceAmount)}
        </span>
      ),
    },
    {
      id: "explanation",
      label: "설명",
      render: (r) => (
        <span className="billing-review-explanation">{r.explanation}</span>
      ),
    },
    {
      id: "action",
      label: "작업",
      render: (r) => (
        <div
          data-testid={`billing-check-actions-${r.projectId.toLowerCase()}`}
          className="billing-review-actions"
        >
          {(r.remedy.mode === "CREATE" || r.remedy.mode === "UPDATE") && (
            <button
              data-testid={`billing-check-remedy-${r.projectId.toLowerCase()}`}
              type="button"
              onClick={() => onRemedy(r.remedy)}
            >
              보완
            </button>
          )}
          {r.remedy.reason && (
            <span className="sicost-banner sicost-tone-lock">
              {r.remedy.reason}
            </span>
          )}
          {r.remedy.relatedBillIds.map((id) => (
            <button
              key={id}
              data-testid={`billing-check-detail-${id.toLowerCase()}`}
              type="button"
              onClick={() => onBillDetail(id)}
            >
              관련 청구 상세 {id}
            </button>
          ))}
        </div>
      ),
    },
  ];
  return (
    <section
      role="tabpanel"
      aria-label="청구 누락 점검"
      data-testid="billing-checks-panel"
      className="billing-review-panel"
    >
      <div className="billing-review-toolbar">
        <h2>청구 누락 점검{data ? ` (${data.summary.tabCount})` : ""}</h2>
        <button
          data-testid="billing-checks-reload"
          type="button"
          disabled={loading}
          onClick={() => setReload((r) => r + 1)}
        >
          재조회
        </button>
        {data && (
          <BillingReviewDownloadButton
            view="BILLING_CHECKS"
            month={data.month}
            amountUnit={amountUnit}
            disabled={loading || !!error || !data.availableMonths.length}
          />
        )}
      </div>
      {error && (
        <ErrorBanner message={error} onRetry={() => setReload((r) => r + 1)} />
      )}
      <div className="billing-review-months" aria-label="점검월">
        {data?.availableMonths.map((value) => (
          <button
            key={value}
            data-testid={`billing-check-month-${value}`}
            type="button"
            disabled={loading}
            aria-pressed={value === data.month}
            onClick={() => {
              setMonth(value);
              setPage(1);
            }}
          >
            {value}
          </button>
        ))}
        {data?.includesPlanned && (
          <StatusBadge tone="info">예정 포함</StatusBadge>
        )}
      </div>
      {!loading && !error && data && (
        <>
          {data.warnings.map((warning, i) => (
            <p
              key={warning}
              data-testid={`billing-check-warning-${i}`}
              className="sicost-banner sicost-tone-info"
            >
              {warning}
            </p>
          ))}
          <div className="billing-review-cards billing-review-check-cards">
            {[
              ["점검 프로젝트", data.summary.projectCount],
              ["맞음", data.summary.matchedCount],
              ["청구 없음", data.summary.missingCount],
              ["금액 차이", data.summary.differenceCount],
              [
                "빠진 것으로 보이는 공급가",
                `${money(data.summary.missingSupplyAmount)} ${amountUnit}`,
              ],
            ].map(([label, value], index) => (
              <article key={label} data-testid={`billing-check-card-${index}`}>
                <span>{label}</span>
                <strong>{value}</strong>
              </article>
            ))}
          </div>
          <p className="sicost-banner sicost-tone-info">
            계산방법: E=유상 배정별 적용단가×월 MM 절사합, B=미취소 청구
            공급가합, D=E−B, T=max(최소 허용오차, E×허용율/100). 허용오차는
            반올림하지 않습니다. 취소 청구·무상 제외, 보호 스냅샷 보존, 예정
            포함.
          </p>
          <p className="billing-review-caption">
            점검월 {data.month} · 단위 {amountUnit} · MM 소수 2자리 · 허용오차
            원 단위{" "}
            {data.items.length === 1
              ? formatDecimal(data.items[0].tolerance)
              : "행별 계산근거 참조"}
          </p>
        </>
      )}
      <DataTable
        rows={error ? [] : (data?.items ?? [])}
        columns={columns}
        rowKey={(r) => r.projectId}
        loading={loading}
        empty="점검할 프로젝트가 없습니다."
        page={page}
        size={size}
        total={data?.total ?? 0}
        onPageChange={setPage}
        onSizeChange={(value) => {
          setSize(value);
          setPage(1);
        }}
      />
    </section>
  );
}
