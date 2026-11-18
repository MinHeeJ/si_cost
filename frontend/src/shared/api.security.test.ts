import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authenticatedFetch, request, resetCsrf, ApiError } from "./api";
const envelope = (data: unknown) =>
  new Response(JSON.stringify({ success: true, meta: {}, data }), {
    headers: { "Content-Type": "application/json" },
  });
const denied = (status: number, code: string) =>
  new Response(
    JSON.stringify({
      success: false,
      meta: {},
      code,
      message: "인증 확인",
      requestId: "test-request",
    }),
    { status, headers: { "Content-Type": "application/json" } },
  );
describe("authenticated transport", () => {
  beforeEach(() => resetCsrf());
  afterEach(() => vi.unstubAllGlobals());
  it("uses the server header name, one in-memory CSRF preparation and same-origin cookies", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        envelope({ headerName: "X-SERVER-CSRF", token: "csrf-only" }),
      )
      .mockResolvedValueOnce(envelope({ changed: true }));
    vi.stubGlobal("fetch", fetch);
    await request("/api/business-users", {
      method: "POST",
      body: JSON.stringify({ name: "합성" }),
    });
    expect(fetch).toHaveBeenCalledTimes(2);
    const options = fetch.mock.calls[1][1] as RequestInit;
    expect(options.credentials).toBe("same-origin");
    expect(new Headers(options.headers).get("X-SERVER-CSRF")).toBe("csrf-only");
  });
  it("leaves multipart boundary to the browser and never retries a denied upload", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        envelope({ headerName: "X-CSRF-TOKEN", token: "csrf-only" }),
      )
      .mockResolvedValueOnce(denied(403, "FORBIDDEN"));
    vi.stubGlobal("fetch", fetch);
    const body = new FormData();
    body.append("file", new Blob(["synthetic"]), "test.csv");
    await expect(
      authenticatedFetch("/api/excel/B7/apply", {
        method: "POST",
        body,
        headers: { "Content-Type": "multipart/form-data" },
      }),
    ).rejects.toBeInstanceOf(ApiError);
    const options = fetch.mock.calls[1][1] as RequestInit;
    expect(new Headers(options.headers).has("Content-Type")).toBe(false);
    expect(options.body).toBe(body);
    expect(fetch).toHaveBeenCalledTimes(2);
  });
  it("rejects a 401 download before a caller can save its JSON as an xlsx", async () => {
    const fetch = vi.fn().mockResolvedValue(denied(401, "SESSION_EXPIRED"));
    vi.stubGlobal("fetch", fetch);
    const listener = vi.fn();
    window.addEventListener("sicost:authentication-required", listener);
    try {
      await expect(
        authenticatedFetch("/api/project-costs/export"),
      ).rejects.toMatchObject({ status: 401 });
      expect(listener).toHaveBeenCalledTimes(1);
      expect(fetch).toHaveBeenCalledTimes(1);
    } finally {
      window.removeEventListener("sicost:authentication-required", listener);
    }
  });
  it("does not resurrect an in-flight token from a cleared authentication generation", async () => {
    let resolveOld!: (response: Response) => void;
    const old = new Promise<Response>((resolve) => {
      resolveOld = resolve;
    });
    const fetch = vi.fn().mockResolvedValue(envelope({ changed: true }));
    fetch
      .mockResolvedValueOnce(old)
      .mockResolvedValueOnce(
        envelope({ headerName: "X-CSRF-TOKEN", token: "current-generation" }),
      );
    vi.stubGlobal("fetch", fetch);
    const first = authenticatedFetch("/api/business-users", { method: "POST" });
    resetCsrf();
    await authenticatedFetch("/api/business-users", { method: "POST" });
    resolveOld(
      envelope({ headerName: "X-CSRF-TOKEN", token: "old-generation" }),
    );
    await first;
    const writes = fetch.mock.calls.filter(
      ([path]) => path === "/api/business-users",
    );
    expect(writes).toHaveLength(2);
    for (const [, options] of writes)
      expect(new Headers(options.headers).get("X-CSRF-TOKEN")).toBe(
        "current-generation",
      );
  });
  it("marks automatic notification and task polls without marking a direct user read", async () => {
    const fetch = vi.fn().mockImplementation(async () => envelope({}));
    vi.stubGlobal("fetch", fetch);
    const notifications = await import(
      "../pages/notificationsops/notificationsApi"
    );
    const automation = await import(
      "../pages/automationrunsops/automationRunsApi"
    );
    await notifications.getNotificationUnreadCount(true);
    await notifications.getNotificationUnreadCount();
    await automation.getAutomationRuns({ page: 1, size: 20 }, undefined, true);
    expect(
      new Headers(fetch.mock.calls[0][1].headers).get("X-Background-Request"),
    ).toBe("true");
    expect(
      new Headers(fetch.mock.calls[1][1].headers).has("X-Background-Request"),
    ).toBe(false);
    expect(
      new Headers(fetch.mock.calls[2][1].headers).get("X-Background-Request"),
    ).toBe("true");
  });
  it("rejects external and protocol-relative paths before sending any credential", async () => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    await expect(
      authenticatedFetch("https://example.test/api/test"),
    ).rejects.toThrow();
    await expect(
      authenticatedFetch("//example.test/api/test"),
    ).rejects.toThrow();
    expect(fetch).not.toHaveBeenCalled();
  });
  it("does not discard a pending MFA form for recoverable verify 401 responses", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        envelope({ headerName: "X-CSRF-TOKEN", token: "csrf-only" }),
      )
      .mockResolvedValueOnce(denied(401, "MFA_INVALID"));
    vi.stubGlobal("fetch", fetch);
    const listener = vi.fn();
    window.addEventListener("sicost:authentication-required", listener);
    try {
      await expect(
        authenticatedFetch("/api/auth/mfa/verify", {
          method: "POST",
          body: JSON.stringify({ code: "123456" }),
        }),
      ).rejects.toMatchObject({ status: 401, body: { code: "MFA_INVALID" } });
      expect(listener).not.toHaveBeenCalled();
      expect(fetch).toHaveBeenCalledTimes(2);
    } finally {
      window.removeEventListener("sicost:authentication-required", listener);
    }
  });
});
