import { useState, type ReactNode } from "react";
import { ApiError } from "../../shared/api";
import { formatAmount, formatFixedDecimal } from "../../shared/format";
import * as api from "./salesCommandsApi";
import { useCodeNames } from "../../shared/codeNames";

export const judgementNames: Record<api.Quote["judgement"], string> = {
  LOSS: "손해",
  APPROVAL_REQUIRED: "승인 필요",
  BELOW_TARGET: "목표 미달",
  MET: "기준 충족",
  UNKNOWN: "원가·기준 미확인",
  INCOMPLETE: "미완료",
};
export function SalesQuotePanel({
  needs,
  initial,
  approval,
}: {
  approval?: ReactNode;
  needs: api.Need[];
  initial?: api.Quote;
}) {
  const name = useCodeNames("JOB", "GRADE");
  const [quote, setQuote] = useState(initial),
    [pending, setPending] = useState(false),
    [error, setError] = useState<unknown>();
  async function preview() {
    setPending(true);
    setError(undefined);
    try {
      setQuote(await api.createSalesOpportunityQuotePreview(needs));
    } catch (e) {
      setError(e);
    } finally {
      setPending(false);
    }
  }
  const money = (v: string | null | undefined) => formatAmount(v, "원");
  return (
    <section
      className="sales-command-quote"
      data-testid="sales-quote-panel"
      aria-busy={pending}
    >
      <div className="sales-command-toolbar">
        <h2>견적 이익 점검</h2>
        <button
          type="button"
          data-testid="quote-preview-button"
          onClick={() => void preview()}
          disabled={pending}
        >
          견적 다시 점검
        </button>
      </div>
      {pending && <p role="status">견적을 점검하고 있습니다.</p>}
      {error != null && <SalesCommandError error={error} />}
      {quote && (
        <>
          <p
            className={`sales-command-badge sales-command-${quote.judgement.toLowerCase()}`}
          >
            {judgementNames[quote.judgement]}
          </p>
          <dl className="sales-command-summary">
            <div>
              <dt>견적매출</dt>
              <dd>{money(quote.revenue)} 원</dd>
            </div>
            <div>
              <dt>견적원가</dt>
              <dd>{money(quote.cost)} 원</dd>
            </div>
            <div>
              <dt>이익률</dt>
              <dd>{formatFixedDecimal(quote.storedProfitRate, 1)}%</dd>
            </div>
            <div>
              <dt>기준월</dt>
              <dd>{quote.standardMonth}</dd>
            </div>
          </dl>
          {approval}
          {quote.issues.map((issue, i) => (
            <p
              key={`${issue.needSn}:${issue.field}:${i}`}
              data-testid={`quote-issue-${i}`}
              className="sales-command-warning"
            >
              {issue.needSn == null ? "" : `${issue.needSn}번째 줄 · `}
              {issue.reason}
            </p>
          ))}
          <div className="sicost-table-viewport">
            <table className="sicost-table">
              <thead>
                <tr>
                  {[
                    "직무 / 등급",
                    "Q (MM)",
                    "유효 단가",
                    "1인 월 원가 / 최소 단가",
                    "목표 단가",
                    "행 매출",
                    "행 원가",
                    "원가 기준월 / 집단 / 인원 / 보완",
                  ].map((t) => (
                    <th key={t}>{t}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {quote.rows.map((r, i) => (
                  <tr
                    key={r.needSn ?? `draft-${i}`}
                    data-testid={`quote-row-${r.needSn ?? i}`}
                  >
                    <td>
                      {name("JOB", r.jobCode)} / {name("GRADE", r.gradeCode)}
                      {!r.complete && " · 미완료"}
                    </td>
                    <td>{formatFixedDecimal(r.quantityMm, 2)}</td>
                    <td>{money(r.effectiveRate)}</td>
                    <td>{money(r.unitMonthlyCost)}</td>
                    <td>{money(r.targetRate)}</td>
                    <td>{money(r.revenue)}</td>
                    <td>{money(r.cost)}</td>
                    <td>
                      {r.costBasisMonth ?? "–"} /{" "}
                      {r.cohort === "JOB_GRADE"
                        ? "주직무+등급"
                        : r.cohort === "GRADE"
                          ? "등급 전체"
                          : "미확인"}{" "}
                      / {r.cohortCount}명 / {r.fallbackUsed ? "보완" : "없음"}
                      {r.unknownReason && ` · ${r.unknownReason}`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p>
            최소금액 {money(quote.minimumAmount)} 원 · 목표금액{" "}
            {money(quote.targetAmount)} 원
          </p>
          {quote.targetUnavailableReason && (
            <p className="sales-command-warning">
              {quote.targetUnavailableReason}
            </p>
          )}
          <details>
            <summary data-testid="quote-formula-toggle">
              계산 근거 · 원시 경계식
            </summary>
            <p>{quote.formula}</p>
            <p>
              승인율 {quote.approvalThreshold ?? "–"}% · 목표율{" "}
              {quote.targetMargin ?? "–"}% · 재승인 차{" "}
              {quote.reapprovalGap ?? "–"}%p · 원시율{" "}
              {quote.rawProfitRate ?? "–"}%
            </p>
          </details>
        </>
      )}
      {!quote && !pending && error == null && (
        <p>
          필요 인력을 입력한 뒤 견적을 점검해 주세요. 업무 데이터는 저장하지
          않습니다.
        </p>
      )}
    </section>
  );
}
export function SalesCommandError({ error }: { error: unknown }) {
  return (
    <div role="alert" className="sales-command-error">
      {error instanceof Error
        ? error.message
        : "처리하지 못했습니다. 다시 조회해 주세요."}
      {error instanceof ApiError && (
        <>
          <p>요청 ID {error.body.requestId}</p>
          {error.body.code === "QUOTE_CHANGED" && (
            <p>견적을 갱신해 주세요. 수정 후 먼저 저장해야 합니다.</p>
          )}
        </>
      )}
    </div>
  );
}
