import { SharedScreenFrame } from "./FollowUpScreen";
import { PermissionsScreen as RolePermissionsScreen } from "../roles/PermissionsScreen";
export function PermissionsScreen({
  onDirtyChange,
}: {
  onDirtyChange?(dirty: boolean): void;
}) {
  return (
    <SharedScreenFrame screenId="UI-24" periodBar={null}>
      <RolePermissionsScreen onDirtyChange={onDirtyChange} />
    </SharedScreenFrame>
  );
}
