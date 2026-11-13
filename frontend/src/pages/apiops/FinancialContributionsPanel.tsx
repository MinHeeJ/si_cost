import { useEffect, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatDecimal } from "../../shared/format";
import {
  getFinancialContributions,
  type FinancialContributions,
} from "./financialReadApi";
import "./financialRead.css";

type Props = {
  month: string;
  refreshKey?: string | number;
  unit?: "원" | "천원" | "백만원";
};
type ContributionRow = {
  id: string;
  label: string;
  amount: string | null;
  vat?: string;
};
function displayed(value: string | null, unit: Props["unit"]) {
  if (value == null) return "–";
  const places = unit === "백만원" ? 6 : unit === "천원" ? 3 : 0;
  if (!places) return formatDecimal(value);
  const sign = value.startsWith("-") ? "-" : "";
  const digits = value.replace(/^-/, "").padStart(places + 1, "0");
  return formatDecimal(
    `${sign}${digits.slice(0, -places)}.${digits.slice(-places)}`,
  );
}
export function FinancialContributionsPanel({
  month,
  refreshKey,
  unit = "천원",
}: Props) {
  const [data, setData] = useState<FinancialContributions>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void getFinancialContributions(month, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) setError(failure);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [month, refreshKey, retry]);
  const profit: ContributionRow[] = data
    ? [
        {
          id: "sga",
          label: "판관비",
          amount: data.sgaAmount,
          vat: data.sgaVatAmount,
        },
        {
          id: "income",
          label: "영업외수익",
          amount: data.nonOperatingIncome,
          vat: data.nonOperatingIncomeVat,
        },
        {
          id: "expense",
          label: "영업외비용",
          amount: data.nonOperatingExpense,
          vat: data.nonOperatingExpenseVat,
        },
        { id: "adjustment", label: "전월 조정", amount: data.adjustmentProfit },
        {
          id: "allocation",
          label: "법인세(월할)",
          amount: data.corporateTaxAllocation,
        },
      ]
    : [];
  const cash: ContributionRow[] = data
    ? [
        {
          id: "financial",
          label: "재무 항목 제경비",
          amount: data.cashFinancialExpense,
        },
        { id: "misc", label: "잡수익", amount: data.cashMiscIncome },
        { id: "card", label: "법인카드 대금", amount: data.cashCardExpense },
        {
          id: "transfer",
          label: "계좌이체(제경비)",
          amount: data.cashTransferExpense,
        },
        {
          id: "labor",
          label: "특별비(미지급 인건비)",
          amount: data.cashUnpaidLabor,
        },
        {
          id: "transfer-total",
          label: "계좌이체 전체합계",
          amount: data.transferTotal,
        },
        { id: "loan-in", label: "대여금 받음", amount: data.cashLoanIn },
        { id: "loan-out", label: "대여금 갚음", amount: data.cashLoanOut },
        { id: "loan-net", label: "대여금 순액", amount: data.cashLoanNet },
        { id: "tax", label: "법인세 납부", amount: data.cashTaxPayment },
      ]
    : [];
  const columns: Column<ContributionRow>[] = [
    { id: "label", label: "구분", render: (row) => row.label },
    {
      id: "amount",
      label: `금액 (${unit})`,
      align: "right",
      render: (row) => (
        <span
          className={
            row.amount?.startsWith("-") ? "financial-read-negative" : undefined
          }
        >
          {displayed(row.amount, unit)}
        </span>
      ),
    },
  ];
  return (
    <section
      className="financial-read-panel"
      data-testid="financial-contributions-panel"
      data-screen-id="SCR-UI-10-CONTRIBUTIONS"
    >
      <div className="sicost-list-toolbar">
        <h2>저장 원천별 손익·현금 기여값</h2>
        <span>
          {month} · 단위: {unit}
        </span>
        {data && (
          <StatusBadge tone={data.verified ? "neutral" : "warn"}>
            {data.verified ? "계산" : "미확인"}
          </StatusBadge>
        )}
      </div>
      <p className="financial-read-help">
        저장된 원천만 조회합니다. 저장 전 초안은 포함하지 않습니다. 회사 전체
        손익·자금수지 합계가 아닙니다.
      </p>
      {error != null ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "기여값을 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRetry((value) => value + 1)}
        />
      ) : (
        <>
          {data?.warnings.map((warning, index) => (
            <p
              key={warning}
              data-testid={`financial-contribution-warning-${index}`}
              className="sicost-banner sicost-tone-warn"
              role="status"
            >
              {warning}
            </p>
          ))}
          <h3>손익 반영</h3>
          <DataTable
            columns={[
              ...columns,
              {
                id: "vat",
                label: `부가세 (${unit})`,
                align: "right",
                render: (row) => displayed(row.vat ?? null, unit),
              },
            ]}
            rows={profit}
            rowKey={(row) => row.id}
            loading={loading}
          />
          <h3>현금 반영</h3>
          <DataTable
            columns={columns}
            rows={cash}
            rowKey={(row) => row.id}
            loading={loading}
          />
        </>
      )}
    </section>
  );
}
