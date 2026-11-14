import { SharedScreenFrame } from "./FollowUpScreen";
import { RateRenegotiationsScreen as RateRenegotiationsBody } from "../apiops/RateRenegotiationsScreen";
export function RateRenegotiationsScreen() {
  return (
    <SharedScreenFrame screenId="UI-32">
      <RateRenegotiationsBody />
    </SharedScreenFrame>
  );
}
