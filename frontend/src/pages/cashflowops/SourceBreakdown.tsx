import { useEffect, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { formatAmount, type AmountUnit } from "../../shared/format";
import {
  cashLabels,
  getCashFlowSources,
  type CashItem,
  type CashSource,
  type CashSourcePageSize,
  type CashSourcesPage,
} from "./cashFlowApi";
export type CashSelection = {
  month: string;
  item: CashItem;
  page: number;
  size: CashSourcePageSize;
};
export function SourceBreakdown({
  selection,
  unit,
  refresh,
  onChange,
  onClose,
  onSource,
  notice,
}: {
  selection: CashSelection;
  unit: AmountUnit;
  refresh: number;
  onChange(value: CashSelection): void;
  onClose(): void;
  onSource(source: CashSource): void;
  notice?: string;
}) {
  const [data, setData] = useState<CashSourcesPage>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void getCashFlowSources(
      selection.month,
      selection.item,
      selection.page,
      selection.size,
      abort.signal,
    )
      .then((value) => {
        if (!abort.signal.aborted) setData(value);
      })
      .catch((e) => {
        if (!abort.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!abort.signal.aborted) setLoading(false);
      });
    return () => abort.abort();
  }, [
    selection.month,
    selection.item,
    selection.page,
    selection.size,
    refresh,
    retry,
  ]);
  const columns: Column<CashSource>[] = [
    {
      id: "origin",
      label: "원천 종류 / 번호",
      render: (source) => (
        <>
          {source.sourceTypeLabel}
          <small className="cash-source-meta">{source.sourceId}</small>
        </>
      ),
    },
    {
      id: "attribution",
      label: "귀속일 / 기준월",
      render: (source) => (
        <>
          {source.attributionDate ?? "–"}
          <small className="cash-source-meta">
            {source.sourceMonth} → {source.attributionMonth}
          </small>
        </>
      ),
    },
    {
      id: "status",
      label: "구분",
      align: "center",
      render: (source) => {
        if (source.status === "UNKNOWN") return <StatusBadge tone="warn">미확인</StatusBadge>;
        if (source.status === "ACTUAL") return <StatusBadge tone="lock">실적</StatusBadge>;
        return <StatusBadge tone="info">예정</StatusBadge>;
      },
    },
    {
      id: "amount",
      label: `금액 (${unit})`,
      align: "right",
      render: (source) => (
        <>
          {formatAmount(source.amount, unit)}
          <small className="cash-source-meta">
            부가세 {formatAmount(source.vatAmount, unit)} · {source.includesVat ? "포함" : "별도"}
          </small>
        </>
      ),
    },
    {
      id: "reference",
      label: "참조 급여월 / 직원구분",
      render: (source) => (
        <>
          {source.referencePayrollMonth ?? "–"}
          <small className="cash-source-meta">
            {source.employeeType
              ? (data?.employeeTypeLabels[source.employeeType] ?? "미확인")
              : "–"}
          </small>
        </>
      ),
    },
    {
      id: "formula",
      label: "계산 근거",
      render: (source) => (
        <>
          {source.formula}
          {source.issues.map((issue, index) => (
            <p key={index} className="cash-unknown">
              {issue.sourceId} {issue.message}
            </p>
          ))}
        </>
      ),
    },
    {
      id: "link",
      label: "이동",
      align: "center",
      render: (source) =>
        source.sourceLink && (
          <button type="button" onClick={() => onSource(source)}>
            원천 보기
          </button>
        ),
    },
  ];
  return (
    <DetailDrawer
      title={`${selection.month} ${cashLabels[selection.item]} 계산근거`}
      testId="cash-source-panel"
      closeTestId="cash-source-close"
      onClose={onClose}
    >
      <div className="cash-source-breakdown">
        {notice && (
          <p role="status" className="operating-banner">
            {notice}
          </p>
        )}
        {error != null ? (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "원천을 조회하지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => setRetry((n) => n + 1)}
          />
        ) : (
          <DataTable
            rows={data?.items ?? []}
            columns={columns}
            rowKey={(source) => `${source.sourceType}:${source.sourceId}:${source.attributionDate}`}
            tableLabel="현금 원천"
            testId="cash-source"
            loading={loading}
            loadingMessage="조회 중입니다."
            empty="조회된 원천이 없습니다."
            page={data?.total ? data.page : undefined}
            size={data?.size ?? selection.size}
            total={data?.total ?? 0}
            onPageChange={(page) => onChange({ ...selection, page })}
            onSizeChange={(size) => onChange({ ...selection, size, page: 1 })}
          />
        )}
      </div>
    </DetailDrawer>
  );
}
