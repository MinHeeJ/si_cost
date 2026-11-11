import { afterEach, expect, it, vi } from "vitest";
import {
  getSalesParts,
  getSalesPart,
  createSalesPart,
  putSalesPart,
  getSalesPartDefaults,
  salesPartAmount,
  downloadSalesParts,
} from "./salesPartsApi";
import { formatDecimal } from "../../shared/format";
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
const envelope = () =>
  new Response(JSON.stringify({ success: true, meta: {}, data: {} }));
it("전체 조회는 상태를 생략하고 선택 ID만 상세와 PATCH 경로에 쓴다", async () => {
  const fetcher = vi.fn().mockImplementation(async () => envelope());
  vi.stubGlobal("fetch", fetcher);
  await getSalesParts({ page: 1, size: 50, sort: "name", direction: "ASC" });
  expect(fetcher.mock.calls[0][0]).not.toContain("useYn");
  await getSalesPart("선택/파트");
  expect(fetcher.mock.calls[1][0]).toBe(
    "/api/sales-parts/%EC%84%A0%ED%83%9D%2F%ED%8C%8C%ED%8A%B8",
  );
  const body = { name: "파트", expectedRevision: "revision" };
  await putSalesPart("PART-321", body);
  expect(fetcher.mock.calls[2][1]).toEqual(
    expect.objectContaining({ method: "PATCH", body: JSON.stringify(body) }),
  );
});
it("등록은 다섯 입력을 십진 문자열로 보내고 선택월 기본값을 조회한다", async () => {
  const fetcher = vi.fn().mockImplementation(async () => envelope());
  vi.stubGlobal("fetch", fetcher);
  const body = {
    name: "파트",
    effectiveMonth: "2026-06",
    leaderUserId: "selected",
    profitRate: "0",
    salesExpenseAmount: "999999999999999",
  };
  await createSalesPart(body);
  expect(fetcher.mock.calls[0][1]).toEqual(
    expect.objectContaining({ method: "POST", body: JSON.stringify(body) }),
  );
  await getSalesPartDefaults("2026-07");
  expect(fetcher.mock.calls[1][0]).toBe(
    "/api/sales-parts/defaults?effectiveMonth=2026-07",
  );
});
it("금액은 Number 변환 없이 원·천원·백만원까지 정확히 표시한다", () => {
  expect(formatDecimal(salesPartAmount("999999999999999", "원"))).toBe(
    "999,999,999,999,999",
  );
  expect(formatDecimal(salesPartAmount("999999999999999", "천원"))).toBe(
    "999,999,999,999.999",
  );
  expect(formatDecimal(salesPartAmount("999999999999999", "백만원"))).toBe(
    "999,999,999.999999",
  );
});
it("다운로드 JSON 오류는 파일 대신 같은 ApiError로 처리한다", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "기록 실패",
          requestId: "request",
        }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      ),
    ),
  );
  await expect(
    downloadSalesParts(
      { page: 1, size: 20, sort: "name", direction: "ASC" },
      "천원",
    ),
  ).rejects.toMatchObject({ body: { code: "INTERNAL_ERROR" } });
});
it("파트 다운로드도 공통 내보내기 계약의 서버 파일명과 고정 열을 사용한다", async () => {
  const filename = "영업파트_20260615_090000.xlsx";
  const fetcher = vi.fn().mockResolvedValue(
    new Response(new Uint8Array([80, 75, 3, 4]), {
      headers: {
        "Content-Type":
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
      },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const createObjectURL = vi.fn().mockReturnValue("blob:parts-export");
  const revokeObjectURL = vi.fn();
  vi.stubGlobal("URL", { createObjectURL, revokeObjectURL });
  let downloaded = "";
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    downloaded = this.download;
  });
  await downloadSalesParts(
    {
      page: 3,
      size: 20,
      q: " 검색 ",
      useYn: "Y",
      sort: "name",
      direction: "DESC",
    },
    "백만원",
  );
  expect(downloaded).toBe(filename);
  expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({
    view: "PARTS",
    filters: { q: "검색", useYn: "Y" },
    sort: "name",
    direction: "DESC",
    amountUnit: "백만원",
    columns: [
      "name",
      "leaderName",
      "profitRate",
      "salesExpenseAmount",
      "effectiveMonth",
      "scheduledChange",
      "personCount",
      "projectCount",
      "useYn",
    ],
  });
  expect(revokeObjectURL).toHaveBeenCalledWith("blob:parts-export");
  expect(document.querySelector("a[download]")).toBeNull();
});
