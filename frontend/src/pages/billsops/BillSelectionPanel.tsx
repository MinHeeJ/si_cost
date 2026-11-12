import { useEffect, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { BillingError } from "./BillSummaryCards";
import * as api from "./billOperationsApi";

export function BillSelectionPanel({
  filters,
  onPrepared,
}: {
  filters: api.SelectionFilters;
  onPrepared?(selection: api.BillSelection): void;
}) {
  const [data, setData] = useState<api.BillSelection>();
  const [error, setError] = useState<unknown>();
  const [pending, setPending] = useState(false);
  const [excluded, setExcluded] = useState<string[]>([]);
  const [retry, setRetry] = useState(0);
  const key = JSON.stringify(filters);
  useEffect(() => {
    setExcluded([]);
    setData(undefined);
  }, [key]);
  const exclusionKey = excluded.join(",");
  useEffect(() => {
    let active = true;
    setPending(true);
    setError(undefined);
    const filter: api.SelectionFilters = JSON.parse(key);
    void api
      .getBillSelection({
        ...filter,
        excludeIds: [...(filter.excludeIds ?? []), ...excluded],
      })
      .then((value) => {
        if (active) setData(value);
      })
      .catch((e) => {
        if (active) {
          setError(e);
          setData(undefined);
        }
      })
      .finally(() => {
        if (active) setPending(false);
      });
    return () => {
      active = false;
    };
    // Query identity is the serialized contract filter, not the caller's object identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, exclusionKey, retry]);
  const columns: Column<api.BillSelection["items"][number]>[] = [
    { id: "billId", label: "청구번호", render: (row) => row.billId },
    {
      id: "unpaidAmount",
      label: "미수금 (원)",
      align: "right",
      render: (row) => row.unpaidAmount,
    },
    {
      id: "exclude",
      label: "선택",
      render: (row) => (
        <button
          type="button"
          data-testid={`bill-exclude-${row.billId.toLowerCase()}`}
          disabled={pending}
          onClick={() => setExcluded((old) => [...old, row.billId])}
        >
          개별 제외
        </button>
      ),
    },
  ];
  return (
    <section
      className="bill-selection-panel"
      data-screen-id="SCR-UI-13"
      data-testid="bill-selection-panel"
    >
      <h3>여러 청구에 한 번에 입금</h3>
      <BillingError error={error} onRetry={() => setRetry((n) => n + 1)} />
      <DataTable
        rows={data?.items ?? []}
        columns={columns}
        rowKey={(row) => row.billId}
        loading={pending}
        empty="입금할 대상이 없습니다."
      />
      {data && (
        <>
          <p>
            대상 {data.total}건 · 제외 {data.excludedCount}건
          </p>
          {data.excluded.map((row) => (
            <p
              key={row.billId}
              data-testid={`bill-excluded-${row.billId.toLowerCase()}`}
            >
              {row.billId} · {row.reason}
            </p>
          ))}
          {!data.canSave && (
            <p className="sicost-banner sicost-tone-warn">
              대상이 없어 저장할 수 없습니다.
            </p>
          )}
        </>
      )}
      <button
        type="button"
        data-testid="bill-selection-prepare"
        disabled={pending || !data?.canSave || Boolean(error)}
        onClick={() => {
          if (data) onPrepared?.(data);
        }}
      >
        입금 입력
      </button>
      <p>
        선택 ID·revision을 고정했습니다. 저장 시 서버에서 다시 검사하며 한 건의
        오류도 전체 취소됩니다.
      </p>
    </section>
  );
}
