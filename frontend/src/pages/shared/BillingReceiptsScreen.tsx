import { SharedScreenFrame } from "./FollowUpScreen";
import { BillsScreen, type BillsScreenProps } from "../bills/BillsScreen";
export function BillingReceiptsScreen(props: BillsScreenProps = {}) {
  return (
    <SharedScreenFrame screenId="UI-13">
      <BillsScreen {...props} />
    </SharedScreenFrame>
  );
}
