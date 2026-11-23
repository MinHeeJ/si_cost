import { useState, type Dispatch, type SetStateAction } from "react";
import { DataTable } from "../../shared/DataTable";
import { StatusBadge } from "../../shared/Feedback";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { FinancialPaymentStatus } from "../corporatetaxesops/FinancialPaymentStatus";
import type {
  CorporateTaxAllocation,
  CorporateTaxPayment,
} from "../corporatetaxesops/corporateTaxApi";
import type { FinancialEditor } from "./financialApi";
import type { FinancialDraft } from "./useFinancialEditor";
import { FinancialAmount, type FinancialUnit } from "./FinancialAmount";
import { WonInput } from "./WonInput";

export function taxExplanation(
  allocation: CorporateTaxAllocation,
  month: string,
) {
  const missing = allocation.months.filter(
    (row) => row.closeStatus === "CLOSE" && !row.verified,
  );
  if (missing.length)
    return `마감된 달(${missing.map((row) => `${Number(row.month.slice(5))}월`).join("·")})의 법인세 자료가 없어 계산할 수 없습니다. 그래서 연간 예상액도 지금은 고칠 수 없습니다.`;
  if (
    allocation.months.find((row) => row.month === month)?.closeStatus ===
    "CLOSE"
  )
    return "마감 확정";
  if (allocation.estimatedTax === null)
    return "연간 예상 법인세 자료가 없어 계산할 수 없습니다.";
  const closed = allocation.months.filter(
    (row) => row.closeStatus === "CLOSE",
  ).length;
  const won = (value: string) => BigInt(value).toLocaleString("ko-KR");
  return closed
    ? `(연간 예상액 ${won(allocation.estimatedTax)}원 − 마감된 ${closed}개월 반영액 ${won(allocation.closedTotal)}원) ÷ 남은 ${allocation.openMonthCount}개월`
    : `연간 예상액 ÷ 남은 ${allocation.openMonthCount}개월`;
}

type Props = {
  editor: FinancialEditor;
  draft: FinancialDraft;
  allocation: CorporateTaxAllocation;
  automaticAmounts?: Record<string, string | null>;
  disabled: boolean;
  unit: FinancialUnit;
  setDraft: Dispatch<SetStateAction<FinancialDraft | undefined>>;
  violations: { collection: string; rowId: string; field: string }[];
  onPayment(row: CorporateTaxPayment): void;
};
export function FinancialTaxPanel({
  editor,
  draft,
  allocation,
  automaticAmounts,
  disabled,
  unit,
  setDraft,
  violations,
  onPayment,
}: Props) {
  const invalid = (row: CorporateTaxPayment, field: string) =>
    violations.some(
      (v) =>
        v.collection === "tax" &&
        v.rowId === `${row.year}-${row.payType.toLowerCase()}` &&
        v.field === field,
    );
  const [expanded, setExpanded] = useState(false);
  const explanation = taxExplanation(allocation, editor.month);
  const missing = allocation.months.some(
    (row) => row.closeStatus === "CLOSE" && !row.verified,
  );
  const current = allocation.months.find((row) => row.month === editor.month);
  const amount = (value: string | null | undefined) => (
    <FinancialAmount value={value} unit={unit} />
  );
  const schedule = (row: CorporateTaxPayment) =>
    draft.taxSchedules.find(
      (p) => p.year === row.year && p.payType === row.payType,
    );
  const update = (
    row: CorporateTaxPayment,
    changes: Partial<FinancialDraft["taxSchedules"][number]>,
  ) =>
    setDraft((old) =>
      old
        ? {
            ...old,
            taxSchedules: old.taxSchedules.map((p) =>
              p.year === row.year && p.payType === row.payType
                ? { ...p, ...changes }
                : p,
            ),
          }
        : old,
    );
  const automatic = (row: CorporateTaxPayment) =>
    automaticAmounts &&
    Object.prototype.hasOwnProperty.call(
      automaticAmounts,
      `${row.year}:${row.payType}`,
    )
      ? automaticAmounts[`${row.year}:${row.payType}`]
      : row.automaticAmount;
  return (
    <section
      className="financial-section sicost-section-card"
      id="financial-tax-section"
      data-testid="financial-tax-panel"
    >
      <header className="financial-section-heading sicost-section-heading">
        <div>
          <h2>법인세</h2>
          <HelpTooltip label="법인세 월별 배분" iconOnly>
            연간 예상 법인세는 순이익표에 월별로 나눠 반영하는 관리용 금액입니다.
            예상액을 수정하면 마감된 달의 금액을 유지하고 차액을 남은 달에 나눕니다.
          </HelpTooltip>
          <small>{editor.month.slice(0, 4)}년</small>
        </div>
      </header>
      <div className="financial-tax-overview">
        <div className="financial-tax-field">
          <label>
            연간 예상 법인세 (원)
            <WonInput
              data-testid="financial-estimated-tax"
              aria-label="연간 예상 법인세(원)"
              aria-invalid={
                violations.some((v) => v.collection === "corporateTax") || undefined
              }
              value={draft.estimatedTax ?? ""}
              disabled={
                disabled ||
                draft.estimatedTax === null ||
                missing ||
                allocation.openMonthCount === 0
              }
              onValueChange={(value) =>
                setDraft((old) => (old ? { ...old, estimatedTax: value } : old))
              }
            />
          </label>
          {missing && <p className="financial-section-note" data-testid="financial-tax-lock-reason">{explanation}</p>}
          {draft.estimatedTax !== editor.corporateTax?.estimatedTax &&
            draft.estimatedTax !== null && (
              <p className="financial-section-note" data-testid="financial-tax-unsaved">
                저장 전입니다. [저장]을 눌러야 반영됩니다.
              </p>
            )}
        </div>
        <div className="financial-tax-field" data-testid="financial-tax-current-panel">
          <span>선택월 법인세 ({unit})</span>
          <div className="financial-tax-value">
            <output className="financial-calculated" data-testid="financial-tax-current">
              {current?.verified && current.amount !== null ? amount(current.amount) : "–"}
            </output>
            {current?.closeStatus === "CLOSE" && <StatusBadge tone="lock">마감 확정</StatusBadge>}
          </div>
          {!missing && current?.closeStatus !== "CLOSE" && (
            <p className="financial-section-note" data-testid="financial-tax-explanation">{explanation}</p>
          )}
          {allocation.remainingAmount?.startsWith("-") && (
            <p
              className="financial-negative"
              data-testid="financial-tax-negative-warning"
            >
              연간 예상액이 마감된 달에 이미 반영한 금액보다 작아서, 남은 달
              법인세가 음수(되돌림)로 잡힙니다. 금액이 맞는지 확인하세요.
            </p>
          )}
        </div>
      </div>
      <div className="financial-subsection">
        <header className="financial-subsection-heading">
          <h3>법인세 납부 일정</h3>
          <HelpTooltip label="법인세 납부 일정" iconOnly>
            자금수지표에는 실제 납부월의 금액이 반영됩니다. 중간예납은
            세무사 안내 금액을 입력합니다. 확정 신고의 기본값은 연간 예상액에서
            중간예납을 뺀 금액입니다. 납부일이 지났는데 납부 확인을 하지 않았다면
            ‘확인 필요’로 표시됩니다.
          </HelpTooltip>
        </header>
        <DataTable
          tableLabel="법인세 납부 일정"
          testId="financial-tax-payments-table"
          rows={editor.taxPayments}
          rowKey={(row) => `${row.year}-${row.payType.toLowerCase()}`}
          empty="등록된 납부 일정이 없습니다."
          columns={[
            {
              id: "date",
              label: "납부일",
              render: (row) =>
                row.payYn === "Y" ? (
                  <>
                    {row.dueDate}
                    {row.paidDate !== row.dueDate && (
                      <small>{row.paidDate} 납부</small>
                    )}
                  </>
                ) : (
                  <input
                    type="date"
                    data-testid={`financial-tax-${row.year}-${row.payType.toLowerCase()}-date`}
                    aria-invalid={invalid(row, "date") || undefined}
                    aria-label={`${row.year} ${row.payType} 예정일`}
                    disabled={disabled}
                    value={schedule(row)?.dueDate ?? ""}
                    onChange={(event) =>
                      update(row, { dueDate: event.target.value })
                    }
                  />
                ),
            },
            {
              id: "type",
              label: "구분",
              render: (row) =>
                `${row.year}년 ${row.payType === "MID" ? "중간예납" : "확정 신고"}`,
            },
            {
              id: "amount",
              label: "금액",
              align: "right",
              render: (row) => {
                if (row.payYn === "Y") return <FinancialAmount value={row.displayAmount} unit={unit} showUnit />;
                const manual = schedule(row)?.payAmount != null;
                return (
                  <>
                    <WonInput
                      data-testid={`financial-tax-${row.year}-${row.payType.toLowerCase()}-amount`}
                      aria-invalid={invalid(row, "amount") || undefined}
                      aria-label={`${row.year} ${row.payType} 예정액`}
                      disabled={disabled}
                      value={
                        schedule(row)?.payAmount ??
                        (row.payType === "CNFM" ? (automatic(row) ?? "") : "")
                      }
                      placeholder={
                        row.payType === "MID" ? "세무사 안내 금액" : "미확인"
                      }
                      onValueChange={(value) =>
                        update(row, { payAmount: value || null })
                      }
                    />
                    {row.payType === "CNFM" && (
                      <>
                        <small>
                          {manual ? (
                            <>고침 · 자동 <FinancialAmount value={automatic(row)} unit={unit} showUnit /></>
                          ) : (
                            "자동 계산"
                          )}
                        </small>
                        {manual && (
                          <button
                            type="button"
                            data-testid={`financial-tax-${row.year}-auto`}
                            disabled={disabled}
                            onClick={() => update(row, { payAmount: null })}
                          >
                            자동 계산으로
                          </button>
                        )}
                      </>
                    )}
                  </>
                );
              },
            },
            {
              id: "status",
              label: "상태",
              align: "center",
              render: (row) => <FinancialPaymentStatus status={row.status} />,
            },
            {
              id: "confirm",
              label: "납부 확인",
              align: "center",
              render: (row) =>
                row.payYn === "N" ? (
                  <button
                    type="button"
                    data-testid={`financial-tax-${row.year}-${row.payType.toLowerCase()}-confirm`}
                    className={
                      row.status === "확인 필요" ? "sicost-primary" : undefined
                    }
                    disabled={disabled}
                    onClick={() => onPayment(row)}
                  >
                    납부 확인
                  </button>
                ) : (
                  "–"
                ),
            },
          ]}
        />
      </div>
      <div className="financial-subsection">
        <div className="financial-tax-allocation-heading">
          <h3>월별 배분 내역</h3>
          <button
            type="button"
            data-testid="financial-tax-allocation-toggle"
            aria-expanded={expanded}
            onClick={() => setExpanded((value) => !value)}
          >
            {expanded ? "접기" : "월별 배분 펼치기"}
          </button>
        </div>
        {expanded && (
          <div data-testid="financial-tax-allocation-panel">
            <DataTable
              tableLabel="월별 법인세 배분 내역"
              testId="financial-tax-allocation-table"
              rows={allocation.months}
              rowKey={(row) => row.month}
              columns={[
                { id: "month", label: "월", render: (row) => row.month },
                {
                  id: "status",
                  label: "상태",
                  render: (row) => (
                    <StatusBadge
                      tone={row.closeStatus === "CLOSE" ? "lock" : "info"}
                    >
                      {row.closeStatus === "CLOSE" ? "마감" : "작업중"}
                    </StatusBadge>
                  ),
                },
                {
                  id: "amount",
                  label: `월할액 (${unit})`,
                  align: "right",
                  render: (row) => amount(row.amount),
                },
                {
                  id: "verified",
                  label: "확인상태",
                  render: (row) =>
                    `${row.verified ? "확인" : "미확인"}${row.fixed === "Y" ? " · 고정" : ""}${row.migrated === "Y" ? " · 이관" : ""}`,
                },
              ]}
            />
          </div>
        )}
      </div>
    </section>
  );
}
