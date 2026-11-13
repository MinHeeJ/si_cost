import { afterEach, describe, expect, it, vi } from "vitest";
import { defaultProfitabilityQuery } from "./individualProfitabilityApi";
import { getProfitabilityExport } from "./profitabilityExportApi";
import { ApiError } from "../../shared/api";

const type =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
const filename = "개인원가수익_2026_20260131_090000.xlsx";
afterEach(() => vi.unstubAllGlobals());
function response(overrides: Partial<Response> = {}) {
  return {
    ok: true,
    status: 200,
    headers: new Headers({
      "Content-Type": type,
      "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
    }),
    blob: async () => new Blob(["xlsx test bytes"], { type }),
    ...overrides,
  };
}
describe("개인 원가 전체 엑셀 요청", () => {
  it("현재 조건/보기/정렬/열순서/숨김/단위를 보내고 페이지와 펼침 상태를 보내지 않는다", async () => {
    const fetcher = vi.fn().mockResolvedValue(response());
    vi.stubGlobal("fetch", fetcher);
    const result = await getProfitabilityExport(
      {
        ...defaultProfitabilityQuery(),
        year: 2026,
        page: 9,
        size: 20,
        name: "  이름  ",
        show: "LOSS",
        includeTurnkey: false,
        sort: "profit",
        direction: "DESC",
      },
      { order: ["profit", "name"], hidden: [], unit: "원" },
    );
    const [path, init] = fetcher.mock.calls[0];
    expect(path.startsWith("/api/individual-profitability/export?")).toBe(true);
    const params = new URLSearchParams(path.split("?")[1]);
    expect(params.has("page")).toBe(false);
    expect(params.has("size")).toBe(false);
    expect(params.get("name")).toBe("이름");
    expect(params.get("includeTurnkey")).toBe("false");
    expect(params.get("show")).toBe("LOSS");
    expect(params.get("sort")).toBe("profit");
    expect(params.get("direction")).toBe("DESC");
    expect(params.get("order")).toBe("profit,name");
    expect(params.get("hidden")).toBe("");
    expect(params.get("unit")).toBe("원");
    expect(init.method).toBe("GET");
    expect(init.cache).toBe("no-store");
    expect(init.redirect).toBe("error");
    expect(result.filename).toBe(filename);
  });
  it("DNLD 실패 JSON을 파일로 저장하지 않고 API 오류로 돌려준다", async () => {
    const error = {
      success: false,
      code: "INTERNAL_ERROR",
      message: "접근기록을 생성하지 못했습니다.",
      meta: {},
      requestId: "owned-request",
    };
    const blob = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        response({
          ok: false,
          status: 500,
          headers: new Headers({ "Content-Type": "application/json" }),
          json: async () => error,
          blob,
        }),
      ),
    );
    await expect(
      getProfitabilityExport(defaultProfitabilityQuery()),
    ).rejects.toBeInstanceOf(ApiError);
    expect(blob).not.toHaveBeenCalled();
  });
  it.each(["text/html", "application/json", "text/plain"])(
    "%s 비파일 응답을 거부한다",
    async (contentType) => {
      vi.stubGlobal(
        "fetch",
        vi.fn().mockResolvedValue(
          response({
            headers: new Headers({ "Content-Type": contentType }),
            json: async () => ({}),
          }),
        ),
      );
      await expect(
        getProfitabilityExport(defaultProfitabilityQuery()),
      ).rejects.toThrow();
    },
  );
  it.each([
    "../bad.xlsx",
    "",
    "개인원가수익.xlsx",
    "개발자_20260131_090000.xlsx",
  ])("잘못된 파일명 %s를 거부한다", async (name) => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        response({
          headers: new Headers({
            "Content-Type": type,
            "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(name)}`,
          }),
        }),
      ),
    );
    await expect(
      getProfitabilityExport(defaultProfitabilityQuery()),
    ).rejects.toThrow("파일명");
  });
  it("빈 XLSX 파일을 거부한다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(response({ blob: async () => new Blob([]) })),
    );
    await expect(
      getProfitabilityExport(defaultProfitabilityQuery()),
    ).rejects.toThrow("비어");
  });
});
