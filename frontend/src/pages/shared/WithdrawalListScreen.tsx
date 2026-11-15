import { SharedScreenFrame } from "./FollowUpScreen";
import { WithdrawalListScreen as WithdrawalBody } from "../apiops/WithdrawalListScreen";
export function WithdrawalListScreen() {
  return (
    <SharedScreenFrame screenId="UI-05">
      <WithdrawalBody />
    </SharedScreenFrame>
  );
}
