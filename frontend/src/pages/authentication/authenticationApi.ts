import { ApiError, request, resetCsrf } from "../../shared/api";
export type AuthenticationStep =
  | "LOGIN"
  | "MFA"
  | "CHANGE_PASSWORD"
  | "AUTHENTICATED";
export type AuthenticationSession = {
  nextStep: AuthenticationStep;
  serverTime: string;
  expiresAt?: string;
  maskedEmail?: string;
  stageExpiresAt?: string;
  passwordChangedAt?: string | null;
  passwordChangeRecommended?: boolean;
};
export type Capabilities = {
  screens: Record<string, string[]>;
  scope: { all: boolean; partIds: string[]; projectIds: string[] };
  sensitive: boolean;
  fields?: Record<string, string[]>;
  features?: Record<string, boolean>;
  pcOnlyScreens?: string[];
};
export const session = async () => {
  try {
    return await request<AuthenticationSession>("/api/auth/session");
  } catch (error) {
    // A failed recovery query is terminal, not another recovery event.
    if (error instanceof ApiError && error.status === 401)
      clearAuthentication();
    throw error;
  }
};
export const capabilities = () =>
  request<Capabilities>("/api/current-user/capabilities");
export const authenticate = (path: string, body?: unknown) =>
  request<AuthenticationSession>(`/api/auth/${path}`, {
    method: "POST",
    body: JSON.stringify(body ?? {}),
  });
export const authenticationChannel =
  typeof BroadcastChannel === "undefined"
    ? null
    : new BroadcastChannel("sicost:session");
export function clearAuthentication(broadcast = true) {
  resetCsrf();
  window.dispatchEvent(new Event("sicost:authentication-cleared"));
  if (broadcast) authenticationChannel?.postMessage({ type: "clear" });
}
