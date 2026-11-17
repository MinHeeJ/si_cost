import { createContext, useContext, type ReactNode } from "react";
import type { Capabilities } from "../../pages/authentication/authenticationApi";
export const PermissionContext = createContext<Capabilities | null>(null);
export const ScreenPermissionContext = createContext<string | undefined>(
  undefined,
);
export function usePermission(screen: string, action = "INQ") {
  return (
    useContext(PermissionContext)?.screens[screen]?.includes(action) ?? false
  );
}
export function useFieldPermission(screen: string, field: string) {
  return (
    useContext(PermissionContext)?.fields?.[screen]?.includes(field) ?? false
  );
}
export function useSensitivePermission() {
  return useContext(PermissionContext)?.sensitive ?? false;
}
export function useFeaturePermission(feature: string) {
  return useContext(PermissionContext)?.features?.[feature] ?? false;
}
export function PermissionBoundary({
  screen,
  action = "INQ",
  children,
}: {
  screen: string;
  action?: string;
  children: ReactNode;
}) {
  return usePermission(screen, action) ? <>{children}</> : null;
}
