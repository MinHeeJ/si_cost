import { afterEach, expect, it, vi } from "vitest";
import {
  getOperatingStandards,
  getOperatingStandardHistory,
  saveOperatingStandards,
  nextStandardMonth,
  notifyOperatingStandardsCommitted,
  OPERATING_STANDARDS_COMMITTED,
} from "./operatingStandardsApi";
afterEach(() => vi.unstubAllGlobals());
it("현재월 조회는 인자·페이지없이 전달하고 선택월만 인코딩한다", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValue(
      new Response(
        JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
      ),
    );
  vi.stubGlobal("fetch", fetcher);
  await getOperatingStandards();
  expect(fetcher.mock.calls[0][0]).toBe("/api/operating-standards");
  fetcher.mockResolvedValue(
    new Response(
      JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
    ),
  );
  await getOperatingStandards("2026-06");
  expect(fetcher.mock.calls[1][0]).toBe(
    "/api/operating-standards?month=2026-06",
  );
});
it("선택키를 경로에 인코딩하고 동일 PUT으로 CASH별도 요청한다", async () => {
  const fetcher = vi
    .fn()
    .mockImplementation(
      async () =>
        new Response(
          JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
        ),
    );
  vi.stubGlobal("fetch", fetcher);
  await getOperatingStandardHistory("선택/키");
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/operating-standards/%EC%84%A0%ED%83%9D%2F%ED%82%A4/history",
  );
  const body = {
    changes: [
      { key: "CASH_BASE_AMT", value: "1234.5678", expectedRevision: "token" },
    ],
  };
  await saveOperatingStandards(body);
  expect(fetcher.mock.calls[1][1]).toEqual(
    expect.objectContaining({ method: "PUT", body: JSON.stringify(body) }),
  );
  expect(fetcher.mock.calls[1][1].body).not.toContain("effectiveMonth");
});
it("월경계는 12월 다음 해 1월이고 저장 브라우저에 즉시 무효화 이벤트를 보낸다", () => {
  expect(nextStandardMonth("2026-12")).toBe("2027-01");
  const listener = vi.fn();
  window.addEventListener(OPERATING_STANDARDS_COMMITTED, listener);
  notifyOperatingStandardsCommitted(true);
  expect(listener).toHaveBeenCalledWith(
    expect.objectContaining({
      detail: expect.objectContaining({
        cash: true,
        resources: ["operating-standards", "parameters", "period-context"],
      }),
    }),
  );
  window.removeEventListener(OPERATING_STANDARDS_COMMITTED, listener);
});
