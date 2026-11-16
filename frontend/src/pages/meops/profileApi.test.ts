import { afterEach, expect, it, vi } from "vitest";
import { getMyProfile, type MyProfile } from "./profileApi";

afterEach(() => vi.unstubAllGlobals());
it("내정보는상대경로GET한번만사용하고주체나개발자입력및본문을보내지않는다", async () => {
  const data: MyProfile = {
    userId: "current-user",
    userName: "현재 사용자",
    activeRoles: [],
    parts: [],
    basisMonth: "2026-08",
    editGuidance: "관리자에게 문의해 주세요.",
  };
  const fetcher = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data }), {
      status: 200,
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  expect(await getMyProfile()).toEqual(data);
  expect(fetcher).toHaveBeenCalledTimes(1);
  const [path, options] = fetcher.mock.calls[0];
  expect(path).toBe("/api/me/profile");
  expect(options.method ?? "GET").toBe("GET");
  expect(options.body).toBeUndefined();
  expect(options.headers.get("Accept")).toBe("application/json");
});
