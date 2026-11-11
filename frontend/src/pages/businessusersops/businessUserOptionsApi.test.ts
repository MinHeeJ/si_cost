import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { getBusinessUserOptions } from "./businessUserOptionsApi";

afterEach(() => vi.unstubAllGlobals());
it.each(["PM", "PART_LEADER"] as const)(
  "%s uses relative public nonpaged API and the shared envelope",
  async (purpose) => {
    const data = {
      items: [],
      roleOptions: [{ roleId: "PM", name: "DB 표시명" }],
    };
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ success: true, meta: {}, data })),
      );
    vi.stubGlobal("fetch", fetcher);
    const controller = new AbortController();
    expect(await getBusinessUserOptions(purpose, controller.signal)).toEqual(
      data,
    );
    expect(fetcher.mock.calls[0][0]).toBe(
      `/api/business-users/options?purpose=${purpose}`,
    );
    expect(fetcher.mock.calls[0][1]).toMatchObject({
      cache: "no-store",
      signal: controller.signal,
      redirect: "error",
    });
    expect(fetcher.mock.calls[0][1].headers.has("Authorization")).toBe(false);
  },
);
it("preserves root JSON errors for field feedback without retry", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "VALIDATION_ERROR",
        message: "목적을 확인해 주세요.",
        field: "purpose",
        requestId: "trace",
      }),
      { status: 400 },
    ),
  );
  vi.stubGlobal("fetch", fetcher);
  await expect(getBusinessUserOptions("PM")).rejects.toBeInstanceOf(ApiError);
  expect(fetcher).toHaveBeenCalledTimes(1);
});
