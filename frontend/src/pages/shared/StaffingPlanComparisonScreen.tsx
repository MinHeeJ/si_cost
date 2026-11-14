import { SharedScreenFrame } from "./FollowUpScreen";
import { StaffingPlanComparisonScreen as ComparisonBody } from "../staffingplancomparisonops/StaffingPlanComparisonScreen";
export function StaffingPlanComparisonScreen() {
  return (
    <SharedScreenFrame screenId="UI-11">
      <ComparisonBody />
    </SharedScreenFrame>
  );
}
