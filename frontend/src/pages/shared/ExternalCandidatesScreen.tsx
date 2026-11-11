import { SharedScreenFrame } from "./FollowUpScreen";
import { CandidatesScreen } from "../candidates/CandidatesScreen";
export function ExternalCandidatesScreen() {
  return (
    <SharedScreenFrame screenId="UI-33">
      <CandidatesScreen />
    </SharedScreenFrame>
  );
}
