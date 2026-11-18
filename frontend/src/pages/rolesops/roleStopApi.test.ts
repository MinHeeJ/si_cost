import { stubBusinessFetch } from "../../shared/testSupport/businessFixtures";
import { afterEach, expect, it, vi } from "vitest";
import { saveRoleStop, roleSettingsCommittedEvent } from "./roleStopApi";
import { businessUsersCommittedEvent } from "../businessusersops/businessUserOptionsApi";
afterEach(() => {
  vi.unstubAllGlobals();
});
it("POST uses selected encoded ID, revision only, and invalidates only after success", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValue(
      new Response(
        JSON.stringify({ success: true, meta: {}, data: { changed: true } }),
        { status: 200 },
      ),
    );
  stubBusinessFetch(fetcher);
  const roles = vi.fn(),
    users = vi.fn();
  window.addEventListener(roleSettingsCommittedEvent, roles);
  window.addEventListener(businessUsersCommittedEvent, users);
  try {
    await saveRoleStop("R-selected", "current-revision");
    expect(fetcher).toHaveBeenCalledWith(
      "/api/roles/R-selected/stop",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ expectedRevision: "current-revision" }),
      }),
    );
    expect(roles).toHaveBeenCalledTimes(1);
    expect(users).toHaveBeenCalledTimes(1);
    fetcher.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "ROLE_IN_USE",
          message: "연결 사용자",
          requestId: "request",
        }),
        { status: 409 },
      ),
    );
    await expect(
      saveRoleStop("R-selected", "current-revision"),
    ).rejects.toThrow("연결 사용자");
    expect(roles).toHaveBeenCalledTimes(1);
    expect(users).toHaveBeenCalledTimes(1);
  } finally {
    window.removeEventListener(roleSettingsCommittedEvent, roles);
    window.removeEventListener(businessUsersCommittedEvent, users);
  }
});
it("no-op causes no invalidation", async () => {
  stubBusinessFetch(
    vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ success: true, meta: {}, data: { changed: false } }),
          { status: 200 },
        ),
      ),
  );
  const listener = vi.fn();
  window.addEventListener(roleSettingsCommittedEvent, listener);
  try {
    await saveRoleStop("R-selected", "latest");
    expect(listener).not.toHaveBeenCalled();
  } finally {
    window.removeEventListener(roleSettingsCommittedEvent, listener);
  }
});
