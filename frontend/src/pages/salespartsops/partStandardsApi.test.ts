import { afterEach, expect, it, vi } from "vitest";
import {
  createSalesPartStandard,
  getSalesPartDefaults,
  getSalesPartOptions,
} from "./partStandardsApi";
import { ApiError } from "../../shared/api";
afterEach(() => {
  vi.unstubAllGlobals();
});
function response(data: unknown, status = 200) {
  return new Response(
    JSON.stringify(status < 400 ? { success: true, meta: {}, data } : data),
    { status, headers: { "Content-Type": "application/json" } },
  );
}
it("기본값은 월생략 또는 단일월을 상대경로로 조회한다", async () => {
  const fetcher = vi
    .fn()
    .mockImplementation(async () => response({ items: [] }));
  vi.stubGlobal("fetch", fetcher);
  await getSalesPartDefaults();
  await getSalesPartDefaults("2026-09");
  await getSalesPartOptions();
  expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
    "/api/sales-parts/defaults",
    "/api/sales-parts/defaults?effectiveMonth=2026-09",
    "/api/sales-parts/options",
  ]);
});
it("선택ID와 십진문자열을 보내고 commit 응답 후에만 변경 이벤트를 낸다", async () => {
  const body = {
    effectiveMonth: "2026-09",
    leaderUserId: "selected-leader",
    profitRate: "40.25",
    salesExpenseAmount: "999999999999999",
    expectedRevision: "revision",
  };
  const fetcher = vi
    .fn()
    .mockResolvedValue(
      response({ changed: true, message: "저장했습니다.", part: {} }, 201),
    );
  vi.stubGlobal("fetch", fetcher);
  const listener = vi.fn();
  window.addEventListener("sicost:sales-parts-committed", listener);
  try {
    await createSalesPartStandard("PART-selected", body);
    expect(fetcher).toHaveBeenCalledWith(
      "/api/sales-parts/PART-selected/standards",
      expect.objectContaining({ method: "POST", body: JSON.stringify(body) }),
    );
    expect(listener).toHaveBeenCalledTimes(1);
  } finally {
    window.removeEventListener("sicost:sales-parts-committed", listener);
  }
});
it("정산 보호 오류는 root envelope로 보존하고 성공 이벤트를 내지 않는다", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      response(
        {
          success: false,
          meta: {},
          code: "PART_SETTLEMENT_CONFIRMED",
          message: "정산된 월입니다.",
          field: "effectiveMonth",
          requestId: "part-request",
        },
        409,
      ),
    ),
  );
  const listener = vi.fn();
  window.addEventListener("sicost:sales-parts-committed", listener);
  try {
    await expect(
      createSalesPartStandard("PART-selected", {
        effectiveMonth: "2026-09",
        leaderUserId: "leader",
        profitRate: "45",
        salesExpenseAmount: "1500000",
        expectedRevision: "revision",
      }),
    ).rejects.toBeInstanceOf(ApiError);
    expect(listener).not.toHaveBeenCalled();
  } finally {
    window.removeEventListener("sicost:sales-parts-committed", listener);
  }
});
