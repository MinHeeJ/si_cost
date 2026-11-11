import type { ReactNode } from "react";
import { FollowUpScreen, SharedScreenFrame } from "./FollowUpScreen";
export function BusinessPartnersScreen({ children }: { children?: ReactNode }) {
  return children ? (
    <SharedScreenFrame screenId="UI-15">{children}</SharedScreenFrame>
  ) : (
    <FollowUpScreen screenId="UI-15" />
  );
}
