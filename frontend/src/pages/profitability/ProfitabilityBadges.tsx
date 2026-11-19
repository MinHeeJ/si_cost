import { StatusBadge } from "../../shared/Feedback";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { compareDecimal } from "../../shared/format";
import type { ProfitabilityPerson } from "./individualProfitabilityApi";

/** Flags and original MM come from the server; only their presentation changes. */
export function ProfitabilityBadges({ person }: { person: ProfitabilityPerson }) {
  const over = person.monthBasis.filter(month => compareDecimal(month.assignmentMm, "1.00") > 0);
  return <>
    {over.length > 0 && <HelpTooltip label="1.0 초과" trigger={<StatusBadge tone="warn">1.0 초과</StatusBadge>}>
      {over.map(month => `${month.month}: ${month.assignmentMm}MM`).join(", ")}
    </HelpTooltip>}
    {person.completeness.estimated && <HelpTooltip label="급여 미업로드·예정" trigger={<StatusBadge tone="info">급여 미업로드·예정</StatusBadge>}>
      급여 미업로드·예정. 원천을 저장하지 않은 계산값입니다.
    </HelpTooltip>}
    {(!person.completeness.costComplete || !person.completeness.revenueComplete) && <HelpTooltip label="미확인" trigger={<StatusBadge tone="warn">미확인</StatusBadge>}>
      {person.completeness.issues.map(issue => <p key={`${issue.month}-${issue.source}-${issue.message}`}>{issue.month} {issue.message} ({issue.source}, {issue.relatedScreen})</p>)}
    </HelpTooltip>}
  </>;
}
