import type { ReactNode } from "react";
import { FollowUpScreen, SharedScreenFrame } from "./FollowUpScreen";
export function UsersScreen({ children }: { children?: ReactNode }) {
  return children ? (
    <SharedScreenFrame screenId="UI-23">{children}</SharedScreenFrame>
  ) : (
    <FollowUpScreen screenId="UI-23" />
  );
}
