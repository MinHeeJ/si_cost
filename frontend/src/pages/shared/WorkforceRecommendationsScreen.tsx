import { SharedScreenFrame } from "./FollowUpScreen";
import { WorkforceRecommendationsScreen as Recommendations } from "../workforcerecommendations/WorkforceRecommendationsScreen";
export function WorkforceRecommendationsScreen() {
  return (
    <SharedScreenFrame screenId="UI-30" periodBar={null}>
      <Recommendations />
    </SharedScreenFrame>
  );
}
