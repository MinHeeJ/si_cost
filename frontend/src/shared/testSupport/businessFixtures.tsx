import { createElement, type ReactNode } from "react";
import {
  render as renderOriginal,
  type RenderOptions,
} from "@testing-library/react";
import { afterAll, vi } from "vitest";
import { PermissionContext } from "../permission/PermissionBoundary";
import { resetCsrf } from "../api";
import type { Capabilities } from "../../pages/authentication/authenticationApi";
import * as authentication from "../../pages/authentication/authenticationApi";
export * from "@testing-library/react";
export { renderBusiness as render };

// Explicit privileged fixture for pre-authentication-feature BUSINESS unit tests.
// It is not an authentication test, browser bridge, or production fallback.
// Authentication/permission tests must use their own restrictive fixtures instead.
const screens = [
  "UI-01",
  "UI-02",
  "UI-03",
  "UI-04",
  "UI-05",
  "UI-07",
  "UI-08",
  "UI-09",
  "UI-10",
  "UI-11",
  "UI-12",
  "UI-13",
  "UI-14",
  "UI-15",
  "UI-16",
  "UI-17",
  "UI-18",
  "UI-19",
  "UI-21",
  "UI-23",
  "UI-24",
  "UI-25",
  "UI-26",
  "UI-27",
  "UI-28",
  "UI-29",
  "UI-30",
  "UI-31",
  "UI-32",
  "UI-33",
];
export const businessCapabilities: Capabilities = {
  screens: Object.fromEntries(
    screens.map((screen) => [
      screen,
      ["INQ", "REG", "UPD", "DEL", "UPLD", "DNLD", "CNFM"],
    ]),
  ),
  scope: { all: true, partIds: [], projectIds: [] },
  sensitive: true,
  fields: {
    "UI-02": ["id", "managementNo", "name", "companyId", "companyName", "employeeType", "grade", "job", "status", "useYn", "currentProjects", "withdrawalDate", "monthlyMm", "revision", "gradeCode", "jobCode", "subJobCode", "positionCode", "directClass", "partId", "hireDate", "homeAreaCode", "homeAddress", "skills", "priceHistory", "editPrice", "monthlyRate", "monthlyPay"],
    "UI-12": ["id", "projectId", "projectName", "developerId", "developerName", "managementNo", "jobCode", "jobName", "startDate", "endDate", "contractMm", "inputType", "contractDeveloperId", "extensionPossibility", "extensionCount", "earlyWithdrawal", "assignmentStatus", "yearMm", "revision", "warnings", "protectedMonths", "month", "monthlyMm", "inputDays", "fixed", "billingUnitPrice", "appliedUnitPrice", "defaultUnitPrice", "salesAmount"],
    "UI-33": ["id", "name", "employeeType", "companyName", "job", "grade", "skills", "homeAddress", "availableDate", "contactStatus", "lastContactDate", "source", "revision", "companyId", "phone", "email", "consent", "jobCode", "subJobCode", "gradeCode", "homeAreaCode", "sourceCode", "memo", "consentUserId", "consentDate", "consentUserName", "addressPath", "desiredRate"],
    "UI-32": ["monthlyLaborCost", "employeeType"],
  },
  features: {
    managePartners: true,
    automationLogs: true,
    analysisPanel: true,
    userAccessLogs: true,
    roleStop: true,
  },
  pcOnlyScreens: [],
};
export function renderBusiness(ui: ReactNode, options?: RenderOptions) {
  installBusinessSession();
  const Wrapper = options?.wrapper;
  return renderOriginal(ui, {
    ...options,
    wrapper: ({ children }) =>
      createElement(
        PermissionContext.Provider,
        { value: businessCapabilities },
        Wrapper ? createElement(Wrapper, null, children) : children,
      ),
  });
}
/** Keep business-response mock queues/call assertions independent of the CSRF handshake. */
export function stubBusinessFetch(fetcher: unknown) {
  if (typeof fetcher !== "function")
    throw new Error("A business transport mock is required.");
  resetCsrf();
  vi.stubGlobal(
    "fetch",
    new Proxy(fetcher as typeof fetch, {
      apply(target, receiver, args) {
        if (String(args[0]) === "/api/auth/csrf")
          return Promise.resolve(
            new Response(
              JSON.stringify({
                success: true,
                meta: {},
                data: {
                  headerName: "X-CSRF-TOKEN",
                  token: "synthetic-business-unit-csrf",
                },
              }),
              { status: 200, headers: { "Content-Type": "application/json" } },
            ),
          );
        return Reflect.apply(target, receiver, args);
      },
    }),
  );
}
/** Router business tests bootstrap a session fixture, not a fabricated business API response. */
export function installBusinessSession() {
  vi.spyOn(authentication, "session").mockImplementation(async () => ({
    nextStep: "AUTHENTICATED",
    serverTime: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 1800000).toISOString(),
    passwordChangeRecommended: false,
  }));
  vi.spyOn(authentication, "capabilities").mockResolvedValue(
    businessCapabilities,
  );
}
afterAll(() => authentication.authenticationChannel?.close());
