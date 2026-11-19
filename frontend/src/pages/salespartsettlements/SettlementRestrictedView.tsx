import { FieldHelp } from "../../shared/FieldHelp";
import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { KpiStrip } from "../../shared/Surface";
import { useEffect, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import { formatAmount, formatMm } from "../../shared/format";
import { usePermission } from "../../shared/permission/PermissionBoundary";
import {
  downloadSettlement,
  getSalesPartSettlementTrend,
} from "../salespartsettlementsops/settlementCommandsApi";
import type {
  SettlementResult,
  SettlementRow,
  SettlementTabsResult,
  SettlementQuery,
} from "./salesPartSettlementsApi";

type Month = {
  month: string;
  status: string;
  personCount: number;
  surplusPersonCount?: number;
  deficitPersonCount: number;
  mm: string | null;
};
export function SettlementRestrictedView({
  tabs,
  data,
  selected,
  query,
  loading,
  error,
  onSelect,
  onQuery,
  onRetry,
}: {
  tabs?: SettlementTabsResult;
  data?: SettlementResult;
  selected?: string;
  query: SettlementQuery;
  loading: boolean;
  error: unknown;
  onSelect(id: string): void;
  onQuery(value: SettlementQuery): void;
  onRetry(): void;
}) {
  const [trend, setTrend] = useState(false),
    [months, setMonths] = useState<Month[]>(),
    [failure, setFailure] = useState<unknown>(),
    [pending, setPending] = useState(false);
  const download = usePermission("UI-19", "DNLD");
  useEffect(() => {
    setMonths(undefined);
    setFailure(undefined);
    if (!trend || !data) return;
    const controller = new AbortController();
    void getSalesPartSettlementTrend(
      data.partId,
      { month: data.month, page: 1, size: 100 },
      controller.signal,
    )
      .then((result) => {
        if (!controller.signal.aborted)
          setMonths(result.monthlyTotals as unknown as Month[]);
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setFailure(reason);
      });
    return () => controller.abort();
  }, [trend, data?.partId, data?.month]);
  const columns: Column<SettlementRow>[] = [
    { id: "name", label: "이름", sortable: true, render: (row) => row.name },
    {
      id: "projectNames",
      label: "프로젝트",
      render: (row) => row.projectNames.join(" · "),
    },
    {
      id: "mm",
      label: "MM",
      sortable: true,
      render: (row) => formatMm(row.mm),
    },
    {
      id: "unitPrice",
      label: "단가",
      sortable: true,
      render: (row) => formatAmount(row.unitPrice, "원"),
    },
    {
      id: "profitStatus",
      label: "흑자/적자",
      render: (row) =>
        row.profitStatus === "SURPLUS"
          ? "흑자"
          : row.profitStatus === "DEFICIT"
            ? "적자"
            : "분류 미정",
    },
  ];
  const message = error ?? failure;
  return (
    <section
      data-testid="sales-part-settlement-screen"
      data-screen-id="SCR-UI-19"
    >
      <h1>영업 파트 정산</h1>
      <SegmentedTabList as="div" role="tablist" aria-label="영업 파트">
        {tabs?.parts.map((part) => (
          <button
            key={part.partId}
            type="button"
            role="tab"
            aria-selected={selected === part.partId}
            onClick={() => onSelect(part.partId)}
          >
            {part.partName}
          </button>
        ))}
      </SegmentedTabList>
      {message != null && (
        <ErrorBanner
          message={
            message instanceof Error
              ? message.message
              : "정산 조회에 실패했습니다."
          }
          onRetry={onRetry}
        />
      )}
      {loading && <p role="status">정산 자료를 불러오는 중입니다.</p>}
      {tabs && !tabs.parts.length && (
        <p>정산 대상 영업 파트가 없습니다. 본사는 정산 대상이 아닙니다</p>
      )}
      {data && (
        <>
          <p>
            {data.month} ·{" "}
            {data.status === "WORK"
              ? "작업중"
              : data.status === "AUTO"
                ? "마감자동"
                : "확정"}
          </p>
          <KpiStrip
            className="settlement-summary"
            data-testid="settlement-summary"
          >
            <article>
              <h2>파트장</h2>
              <strong>{data.standard.leaderName ?? "–"}</strong>
            </article>
            <article>
              <h2>인원</h2>
              <strong>{data.summary.personCount}명</strong>
            </article>
            <article>
              <h2>흑자 인원</h2>
              <strong>
                {data.summary.surplusPersonCount === undefined
                  ? "분류 미정"
                  : `${data.summary.surplusPersonCount}명`}
              </strong>
            </article>
            <article>
              <h2>적자 인원</h2>
              <strong>{data.summary.deficitPersonCount}명</strong>
            </article>
          </KpiStrip>
          <p>
            수정 권한이 없어 보기만 할 수 있습니다(권한 › 메뉴×동작 표의 수정).
          </p>
          <button type="button" onClick={() => setTrend(false)}>
            이번 달
          </button>
          <button type="button" onClick={() => setTrend(true)}>
            기간 추이
          </button>
          <button
            type="button"
            disabled={!download || pending}
            onClick={async () => {
              if (!download || pending) return;
              setPending(true);
              setFailure(undefined);
              try {
                await downloadSettlement(
                  data.partId,
                  { ...query, month: data.month },
                  trend ? "TREND" : "MONTH",
                  "원",
                );
              } catch (reason) {
                setFailure(reason);
              } finally {
                setPending(false);
              }
            }}
          >
            엑셀 다운로드
          </button>
          {trend ? (
            <div role="tabpanel" data-testid="settlement-trend-panel">
              {!months ? (
                <p role="status">기간 추이를 불러오는 중입니다.</p>
              ) : (
                <table className="sicost-table">
                  <thead>
                    <tr>
                      <th>월</th>
                      <th>상태<FieldHelp label="상태" kind="B" /></th>
                      <th>인원</th>
                      <th>흑자 인원</th>
                      <th>적자 인원</th>
                      <th>MM</th>
                    </tr>
                  </thead>
                  <tbody>
                    {months.map((month) => (
                      <tr key={month.month}>
                        <td>{month.month}</td>
                        <td>{month.status}</td>
                        <td>{month.personCount}</td>
                        <td>{month.surplusPersonCount ?? "분류 미정"}</td>
                        <td>{month.deficitPersonCount}</td>
                        <td>{formatMm(month.mm)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          ) : (
            <DataTable
              testId="settlement-table"
              rows={data.items}
              rowKey={(row) => String(data.items.indexOf(row))}
              columns={columns}
              page={data.pagination.page}
              size={query.size}
              total={data.pagination.total}
              loading={loading}
              onPageChange={(page) => onQuery({ ...query, page })}
              onSizeChange={(size) => onQuery({ ...query, size, page: 1 })}
              sort={{ id: query.sort, direction: query.direction }}
              onSortChange={(id) =>
                onQuery({
                  ...query,
                  page: 1,
                  sort: id as SettlementQuery["sort"],
                  direction:
                    query.sort === id && query.direction === "ASC"
                      ? "DESC"
                      : "ASC",
                })
              }
            />
          )}
        </>
      )}
    </section>
  );
}
