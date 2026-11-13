import { afterEach, describe, expect, it, vi } from "vitest";
import {
  defaultProfitabilityQuery,
  getIndividualProfitability,
  getIndividualProfitabilityPerson,
  profitabilityParameters,
} from "./individualProfitabilityApi";

afterEach(() => vi.unstubAllGlobals());
describe("개인 조회 상대경로 계약", () => {
  it("기본보기와 false를 손실 없이 보내고 이름을 trim하며 빈 선택값은 생략한다", async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () =>
        JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
    });
    vi.stubGlobal("fetch", fetcher);
    await getIndividualProfitability({
      ...defaultProfitabilityQuery(),
      year: 2026,
      includeTurnkey: false,
      name: "  이름  ",
      partId: "",
    });
    const path = fetcher.mock.calls[0][0] as string;
    expect(path.startsWith("/api/individual-profitability?")).toBe(true);
    const params = new URLSearchParams(path.split("?")[1]);
    expect(params.get("name")).toBe("이름");
    expect(params.get("includeTurnkey")).toBe("false");
    expect(params.has("partId")).toBe(false);
    expect(params.get("size")).toBe("50");
  });
  it("선택한 사람 ID와 동일조회문맥으로 상세를 요청하고 page/size는 제외한다", async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ success: true, meta: {}, data: {} }),
    });
    vi.stubGlobal("fetch", fetcher);
    const selected = { personId: "selected/person" };
    await getIndividualProfitabilityPerson(selected.personId, {
      ...defaultProfitabilityQuery(),
      page: 3,
      size: 20,
      view: "MONTHLY_AVERAGE",
      show: "LOSS",
    });
    const path = fetcher.mock.calls[0][0] as string;
    expect(
      path.startsWith(
        `/api/individual-profitability/${encodeURIComponent(selected.personId)}?`,
      ),
    ).toBe(true);
    const params = new URLSearchParams(path.split("?")[1]);
    expect(params.has("page")).toBe(false);
    expect(params.has("size")).toBe(false);
    expect(params.get("view")).toBe("MONTHLY_AVERAGE");
    expect(params.get("show")).toBe("LOSS");
  });
  it("query serialization preserves server sorting rather than sorting current-page rows", () => {
    const params = profitabilityParameters({
      ...defaultProfitabilityQuery(),
      sort: "profit",
      direction: "DESC",
      page: 2,
    });
    expect(params.get("sort")).toBe("profit");
    expect(params.get("direction")).toBe("DESC");
    expect(params.get("page")).toBe("2");
  });
});
