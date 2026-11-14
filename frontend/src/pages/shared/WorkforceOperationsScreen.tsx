import { SharedScreenFrame } from "./FollowUpScreen";
import { WorkforceOperationsScreen as OperationsBody } from "../apiops/WorkforceOperationsScreen";
export function WorkforceOperationsScreen() {
  return (
    <SharedScreenFrame screenId="UI-28">
      <OperationsBody />
    </SharedScreenFrame>
  );
}
