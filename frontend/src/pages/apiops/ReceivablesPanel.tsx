import { useEffect, useState } from "react";
import { ApiError } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { StatusBadge, ErrorBanner } from "../../shared/Feedback";
import { formatDistributionAmount } from "../distribution/MonthlyMatrixTable";
import {
  getReceivables,
  type Receivable,
  type ReceivablesPage,
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
  onBillDetail(billId: string): void;
  onCount?(count: number): void;
};
export function ReceivablesPanel(props: Props) {
  if (props.year !== props.currentYear)
    return (
      <section
        data-testid="receivables-unavailable"
        className="sicost-banner sicost-tone-lock"
      >
        미수 알림은 올해만 제공합니다.
      </section>
    );
  return <ReceivablesContent key={props.year} {...props} />;
}
function ReceivablesContent({
  amountUnit = "천원",
  refreshKey,
  onBillDetail,
  onCount,
}: Props) {
  const [page, setPage] = useState(1),
    [size, setSize] = useState<ReviewPageSize>(50),
    [reload, setReload] = useState(0);
  const [data, setData] = useState<ReceivablesPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<string>();
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    getReceivables({ page, size }, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setData(value);
      })
      .catch((e) => {
        if (!controller.signal.aborted)
          setError(
            e instanceof ApiError
              ? `${e.message} (문의 번호: ${e.body.requestId})`
              : "미수 알림을 불러오지 못했습니다.",
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [page, size, reload, refreshKey]);
  useEffect(() => {
    if (data) onCount?.(data.tabCount);
  }, [data, onCount]);
  const columns: Column<Receivable>[] = [
    { id: "projectName", label: "프로젝트", render: (r) => r.projectName },
    {
      id: "billId",
      label: "청구번호",
      render: (r) => (
        <button
          type="button"
          data-testid={`receivable-detail-${r.billId.toLowerCase()}`}
          onClick={() => onBillDetail(r.billId)}
        >
          {r.billId}
        </button>
      ),
    },
    {
      id: "unpaidAmount",
      label: `미수금 (${amountUnit})`,
      align: "right",
      render: (r) => formatDistributionAmount(r.unpaidAmount, amountUnit),
    },
    { id: "dueDate", label: "입금 예정일", render: (r) => r.dueDate },
    {
      id: "elapsedDays",
      label: "지난 일수",
      align: "right",
      render: (r) => `${r.dunning.elapsedDays}일`,
    },
    {
      id: "step",
      label: "지금 단계",
      align: "center",
      render: (r) => (
        <StatusBadge
          tone={
            r.dunning.step === 0
              ? "neutral"
              : r.dunning.step === 1
                ? "warn"
                : "bad"
          }
        >
          {r.dunning.step
            ? `${r.dunning.step}단계 · ${r.dunning.elapsedDays}일`
            : "알림 전"}
        </StatusBadge>
      ),
    },
    {
      id: "sentHistory",
      label: "보낸 알림",
      render: (r) => (
        <div className="billing-review-history">
          {r.sentHistory.length
            ? r.sentHistory.map((h) => (
                <p
                  key={h.sn}
                  data-testid={`receivable-history-${r.billId.toLowerCase()}-${h.sn}`}
                >
                  {h.sendDate} · #{h.sn} · {h.step}단계 · 당시 수신자:{" "}
                  {h.receiverDescription} · {h.sendMethodCode}
                  {h.resend ? " (다시)" : ""}
                </p>
              ))
            : "발송 이력 없음"}
          <details
            data-testid={`receivable-recipients-${r.billId.toLowerCase()}`}
          >
            <summary
              data-testid={`receivable-recipients-open-${r.billId.toLowerCase()}`}
            >
              현재 수신대상
            </summary>
            {r.recipients.map((group) => (
              <div
                key={group.step}
                data-testid={`receivable-recipient-group-${r.billId.toLowerCase()}-${group.step}`}
              >
                <strong>{group.step}단계</strong>{" "}
                {group.recipients.map((recipient) => (
                  <span key={recipient.userId}>
                    {recipient.name} ({recipient.roles.join("·")}){" "}
                  </span>
                ))}
                {group.unassignedReasons.map((reason) => (
                  <p key={reason} className="billing-review-unassigned">
                    {reason}
                  </p>
                ))}
              </div>
            ))}
          </details>
        </div>
      ),
    },
    {
      id: "nextNotification",
      label: "다음 알림",
      render: (r) =>
        r.dunning.finalStep
          ? "최종 단계"
          : `${r.dunning.nextStep}단계 · ${r.dunning.nextDate}`,
    },
  ];
  return (
    <section
      role="tabpanel"
      aria-label="미수 알림"
      data-testid="receivables-panel"
      className="billing-review-panel"
    >
      <div className="billing-review-toolbar">
        <h2>미수 알림{data ? ` (${data.tabCount})` : ""}</h2>
        <button
          type="button"
          data-testid="receivables-reload"
          disabled={loading}
          onClick={() => setReload((r) => r + 1)}
        >
          재조회
        </button>
        {data && (
          <BillingReviewDownloadButton
            view="RECEIVABLES"
            amountUnit={amountUnit}
            disabled={loading || !!error}
          />
        )}
      </div>
      {error && (
        <ErrorBanner message={error} onRetry={() => setReload((r) => r + 1)} />
      )}
      {!loading && !error && data && (
        <>
          <p className="billing-review-caption">
            기준일 {data.asOfDate} · 이월 포함 전체 연체 · 단위 {amountUnit}
          </p>
          <div className="billing-review-cards">
            {data.cards.map((card) => (
              <article
                key={card.step}
                data-testid={`receivable-step-card-${card.step}`}
              >
                <h3>
                  {card.step}단계 예정일 + {card.thresholdDays}일
                </h3>
                <span>{card.recipientRoles.join(" · ")}</span>
                <StatusBadge tone={card.step === 1 ? "warn" : "bad"}>
                  {card.count}건
                </StatusBadge>
              </article>
            ))}
          </div>
          <p className="sicost-banner sicost-tone-info">
            발송 기능 연결 예정 · 현재 수신대상과 과거 실제 수신자는 다릅니다.
            기준일 이전의 모든 연체를 포함하며 알림 전 단계도 숨기지 않습니다.
          </p>
          <h2>입금 예정일이 지난 청구 {data.total}건</h2>
        </>
      )}
      <DataTable
        rows={error ? [] : (data?.items ?? [])}
        columns={columns}
        rowKey={(r) => r.billId}
        loading={loading}
        empty="입금 예정일이 지난 청구가 없습니다."
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
