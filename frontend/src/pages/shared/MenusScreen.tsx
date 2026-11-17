import { SharedScreenFrame } from "./FollowUpScreen";
import { MenusScreen as MenuManagementScreen } from "../menumanagement/MenusScreen";
export function MenusScreen({ onDirtyChange }: { onDirtyChange?(dirty: boolean): void }) {
  return <SharedScreenFrame screenId="UI-25" periodBar={null}>
    <MenuManagementScreen onDirtyChange={onDirtyChange} />
  </SharedScreenFrame>;
}
