import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  getProjectPlans,
  saveProjectPlans,
  getPlanJobOptions,
  planMmInput,
  planMmUnits,
  planMmDisplay,
} from "./projectPlansApi";
beforeEach(() => vi.stubGlobal("fetch", vi.fn()));
const data = {
  items: [],
  planMmTotal: "0.00",
  actualMmTotal: "0.00",
  differenceMmTotal: "0.00",
  revision: "opaque",
};
it("選択されたIDをencodeし相対GETで未保存nullとrevisionをそのまま返す", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data })),
  );
  expect(await getProjectPlans("selected/project")).toEqual(data);
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
    "/api/projects/selected%2Fproject/plans",
  );
});
it("承認されたPUT本文だけを送り十進文字列と年とrevisionを保持する", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({
        success: true,
        meta: {},
        data: { value: data, revision: "opaque", changed: false },
      }),
    ),
  );
  const body = {
    year: 2026,
    expectedRevision: "opaque",
    items: [{ jobCode: "DEV", planMm: "9999.99" }],
  };
  await saveProjectPlans("selected-id", body);
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe("/api/projects/selected-id/plans");
  expect(options?.method).toBe("PUT");
  expect(JSON.parse(options?.body as string)).toEqual(body);
});
it("서버 오류의 field와requestId를 보존하고 재시도하지 않는다", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "INVALID_INPUT",
        field: "items",
        message: "중복 직무",
        requestId: "plan-request",
      }),
      { status: 400 },
    ),
  );
  await expect(
    saveProjectPlans("selected-id", {
      year: 2026,
      expectedRevision: "opaque",
      items: [],
    }),
  ).rejects.toBeInstanceOf(ApiError);
  expect(fetch).toHaveBeenCalledOnce();
});
it("직무 옵션은 실제 활성 코드와 전체 정렬을 사용한다", async () => {
  vi.mocked(fetch).mockImplementation(
    async (path) =>
      new Response(
        JSON.stringify({
          success: true,
          meta: {},
          data: {
            items: String(path).includes("includeInactive")
              ? [
                  { code: "OLD", name: "중지" },
                  { code: "DEV", name: "개발" },
                ]
              : [{ code: "DEV", name: "개발" }],
          },
        }),
      ),
  );
  expect(await getPlanJobOptions()).toEqual({
    items: [{ code: "DEV", name: "개발" }],
    order: ["OLD", "DEV"],
  });
});
it.each(["", "0", "9999.99", "1.2", " 1.20 "])(
  "유효 MM %s은 직접값을 반올림하지 않는다",
  (value) => {
    expect(planMmInput(value)).toBe(value.trim() || "0");
  },
);
it.each(["-1", "+0", "1e2", "1.001", "10000", ".2", "NaN"])(
  "잘못된 MM %s 거부",
  (value) => expect(planMmInput(value)).toBeNull(),
);
it("bigint MM 합계는 큰 값에도 정밀도를 잃지 않고 음수와0 표시를 구별한다", () => {
  expect(
    planMmDisplay(planMmUnits("999999999999999.99") + planMmUnits("0.01")),
  ).toBe("1000000000000000.00");
  expect(planMmDisplay(planMmUnits("3.25") - planMmUnits("5.00"))).toBe(
    "−1.75",
  );
  expect(planMmDisplay(planMmUnits("-0.00"))).toBe("0.00");
});
