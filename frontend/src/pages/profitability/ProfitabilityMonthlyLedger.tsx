import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import type { AmountUnit } from "../../shared/format";
import type {
  MonthlyCostRow,
  ProfitabilityQuery,
} from "./individualProfitabilityApi";
import { ProfitabilityValue } from "./ProfitabilityValue";
import { useProfitabilityLedger } from "./useProfitabilityDetails";

/** Full physical identity, including the actual developer, not the contract developer. */
export const monthlyCostKey = (row: MonthlyCostRow) =>
  JSON.stringify([row.month, row.personId, row.projectId, row.assignmentId]);
const marker = (value: string) =>
  Array.from(value, (character) => character.codePointAt(0)!.toString(16)).join(
    "-",
  );
const ascending = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);
const monthLabel = (month: string) =>
  `${month.slice(0, 4)}-${month.slice(4, 6)}`;
function classification(row: MonthlyCostRow) {
  const labels = [];
  if (row.inputType === "FREE") labels.push("무상지원");
  if (row.costClass === "RND") labels.push("내부");
  if (row.costClass === "PRE") labels.push("선투입(PRE)");
  if (row.costClass === "IDLE")
    labels.push(
      row.projectId === "IDLE" && row.assignmentId === "IDLE"
        ? "대기 · 남는 몫"
        : "실주 대기",
    );
  if (row.costClass === "INDRCT") labels.push("간접");
  return labels.length ? labels.join(" · ") : row.costClass;
}
export function ProfitabilityMonthlyLedger({
  personId,
  query,
  unit = "천원",
}: {
  personId: string;
  query: ProfitabilityQuery;
  unit?: AmountUnit;
}) {
  const { data, loading, error, retry } = useProfitabilityLedger(
    personId,
    query,
  );
  const rows = (data?.months.flatMap((month) => month.rows) ?? [])
    .slice()
    .sort(
      (a, b) =>
        ascending(a.month, b.month) ||
        ascending(a.projectId, b.projectId) ||
        ascending(a.assignmentId, b.assignmentId),
    );
  const amount = (
    id:
      | "personalRevenue"
      | "laborAllocated"
      | "teamExpense"
      | "projectExpense"
      | "totalCost"
      | "profit",
    label: string,
  ): Column<MonthlyCostRow> => ({
    id,
    label,
    align: "right",
    render: (row) => <ProfitabilityValue value={row[id]} unit={unit} />,
  });
  const columns: Column<MonthlyCostRow>[] = [
    { id: "month", label: "월", render: (row) => monthLabel(row.month) },
    {
      id: "project",
      label: "프로젝트",
      render: (row) => row.projectName ?? row.projectId,
    },
    { id: "assignment", label: "투입 ID", render: (row) => row.assignmentId },
    {
      id: "job",
      label: "투입 직무",
      render: (row) => row.assignmentJobCode ?? "–",
    },
    {
      id: "classification",
      label: "분류",
      render: (row) => (
        <>
          {classification(row)}{" "}
          {row.fixed === "Y" && <StatusBadge tone="info">고정</StatusBadge>}
          {row.estimated && <StatusBadge tone="info">예정</StatusBadge>}
          {(!row.costComplete || !row.revenueComplete) && (
            <StatusBadge tone="warn">미확인</StatusBadge>
          )}
        </>
      ),
    },
    {
      id: "mm",
      label: "MM",
      align: "right",
      render: (row) => (
        <ProfitabilityValue value={row.assignmentMm} kind="mm" />
      ),
    },
    {
      id: "freeMm",
      label: "무상 MM",
      align: "right",
      render: (row) => <ProfitabilityValue value={row.freeMm} kind="mm" />,
    },
    amount("personalRevenue", "개인 단가(배분 참고액)"),
    amount("laborAllocated", "배분 노동비"),
    amount("teamExpense", "팀비"),
    amount("projectExpense", "프로젝트경비"),
    amount("totalCost", "원가"),
    amount("profit", "이익"),
    {
      id: "profitRate",
      label: "이익률",
      align: "right",
      render: (row) => (
        <ProfitabilityValue value={row.profitRate} kind="rate" />
      ),
    },
  ];
  return (
    <section
      className="profitability-ledger"
      data-testid={`profitability-monthly-ledger-${marker(personId)}`}
      onClick={(event) => event.stopPropagation()}
    >
      <h3>월별 원본 원장 · 월평균 전 금액</h3>
      <details
        data-testid={`profitability-ledger-help-panel-${marker(personId)}`}
      >
        <summary data-testid={`profitability-ledger-help-${marker(personId)}`}>
          금액·분류 안내
        </summary>
        <p>
          부모의 누계/평균 보기와 무관하게 월별 원본 금액입니다. 단위: {unit}.
          개인 단가는 청구·입금액이 아닌 배분 참고액입니다. 원가 = 배분 노동비 +
          팀비 + 프로젝트경비. 급여·공제 참고값은 투입마다 복제하지 않습니다.
          IDLE 대기 행은 배분 후 남는 몫이며 실주 투입과 구별합니다. 미확인 값은
          0이 아닌 –로 표시합니다.
        </p>
      </details>
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "월별 원본을 조회하지 못했습니다."
          }
          onRetry={retry}
        />
      )}
      {loading && <p role="status">월별 원본을 불러오는 중입니다.</p>}
      {data && (
        <>
          <ul aria-label="월별 인건비 출처">
            {data.months.map((month) => (
              <li
                key={JSON.stringify([month.personId, month.month])}
                data-testid={`profitability-labor-source-${marker(month.personId)}-${month.month}`}
              >
                {monthLabel(month.month)} · 인건비 출처:{" "}
                {month.laborSource.source} · 월 인건비{" "}
                <ProfitabilityValue
                  value={month.laborSource.laborCost}
                  unit={unit}
                />
                {month.laborSource.estimated && " · 급여 미업로드·예정"}
                {!month.completeness.costComplete && " · 원가 미확인"}
                {!month.completeness.revenueComplete && " · 단가 미확인"}
                <p>
                  급여/공급가{" "}
                  <ProfitabilityValue
                    value={month.laborSource.pay}
                    unit={unit}
                  />{" "}
                  · 수당{" "}
                  <ProfitabilityValue
                    value={month.laborSource.allowance}
                    unit={unit}
                  />
                  {" · 회사보험 "}
                  <ProfitabilityValue
                    value={month.laborSource.companyInsurance}
                    unit={unit}
                  />{" "}
                  · 퇴직충당금{" "}
                  <ProfitabilityValue
                    value={month.laborSource.retirementReserve}
                    unit={unit}
                  />
                  {" · 본인보험 "}
                  <ProfitabilityValue
                    value={month.laborSource.selfInsurance}
                    unit={unit}
                  />{" "}
                  · 원천세{" "}
                  <ProfitabilityValue
                    value={month.laborSource.withholdingTax}
                    unit={unit}
                  />
                  {" · 근로소득세 "}
                  <ProfitabilityValue
                    value={month.laborSource.incomeTax}
                    unit={unit}
                  />{" "}
                  · 실지급/공급가{" "}
                  <ProfitabilityValue
                    value={month.laborSource.netPay}
                    unit={unit}
                  />
                </p>
              </li>
            ))}
          </ul>
          <details
            data-testid={`profitability-ledger-calculation-panel-${marker(personId)}`}
          >
            <summary
              data-testid={`profitability-ledger-calculation-toggle-${marker(personId)}`}
            >
              이 월원장의 서버 계산 근거
            </summary>
            <ul>
              {data.calculationBasis.map((basis, index) => (
                <li
                  key={`${index}:${basis}`}
                  data-testid={`profitability-ledger-basis-${marker(personId)}-${index}`}
                >
                  {basis}
                </li>
              ))}
            </ul>
          </details>
          {data.issues.length > 0 && (
            <ul aria-label="월별 자료 확인 필요" role="note">
              {data.issues.map((issue, index) => (
                <li
                  key={`${issue.personId}:${issue.month}:${index}`}
                  data-testid={`profitability-ledger-issue-${marker(personId)}-${index}`}
                >
                  {monthLabel(issue.month)} {issue.message} · {issue.source} ·
                  관련 화면 {issue.relatedScreen}
                </li>
              ))}
            </ul>
          )}
          <DataTable
            testId={`profitability-ledger-table-${marker(personId)}`}
            rows={rows}
            rowKey={(row) => marker(monthlyCostKey(row))}
            columns={columns}
            empty="개인 원가 자료 없음 · 확인이 필요합니다."
          />
        </>
      )}
    </section>
  );
}
