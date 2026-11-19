import { createContext, useContext, type ReactNode } from "react";

export const ScreenTitleActionTarget = createContext(false);

/** Position controls in the title band without reparenting their DOM, state or accessible form scope. */
export function ScreenTitleActions({
  children,
  active = true,
}: {
  children: ReactNode;
  active?: boolean;
}) {
  const inFrame = useContext(ScreenTitleActionTarget);
  return (
    <div
      className={`sicost-title-action-group${inFrame ? " sicost-title-actions-positioned" : ""}`}
      hidden={!active}
    >
      {children}
    </div>
  );
}
