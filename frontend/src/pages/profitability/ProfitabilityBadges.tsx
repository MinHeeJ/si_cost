import { StatusBadge } from "../../shared/Feedback";
import { compareDecimal } from "../../shared/format";
import type { ProfitabilityPerson } from "./individualProfitabilityApi";

/** Flags and original MM come from the server; this component only chooses visible labels. */
export function ProfitabilityBadges({
  person,
}: {
  person: ProfitabilityPerson;
}) {
  const over = person.monthBasis.filter(
    (month) => compareDecimal(month.assignmentMm, "1.00") > 0,
  );
  return (
    <>
      {over.length > 0 && (
        <span
          title={over
            .map((month) => `${month.month}: ${month.assignmentMm}MM`)
            .join(", ")}
        >
          <StatusBadge tone="warn">1.0 초과</StatusBadge>
        </span>
      )}
      {person.completeness.estimated && (
        <span title="급여 미업로드·예정. 원천을 저장하지 않은 계산값입니다.">
          <StatusBadge tone="info">급여 미업로드·예정</StatusBadge>
        </span>
      )}
      {(!person.completeness.costComplete ||
        !person.completeness.revenueComplete) && (
        <span
          title={person.completeness.issues
            .map(
              (issue) =>
                `${issue.month} ${issue.message} (${issue.source}, ${issue.relatedScreen})`,
            )
            .join("\n")}
        >
          <StatusBadge tone="warn">미확인</StatusBadge>
        </span>
      )}
    </>
  );
}
