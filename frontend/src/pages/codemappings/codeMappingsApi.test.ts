import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createCodeMapping,
  deleteCodeMappings,
  getCodeMappings,
  getMappingCodes,
} from "./codeMappingsApi";
afterEach(() => vi.unstubAllGlobals());
describe("표기 대응 HTTP 연결", () => {
  it("필터와 서버 페이지를 인코딩하여 전달한다", async () => {
    const data = { items: [], total: 0, page: 1, size: 50 };
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ success: true, meta: {}, data })),
      );
    vi.stubGlobal("fetch", fetcher);
    expect(
      await getCodeMappings({
        sourceText: "표기 & %",
        groupId: "GROUP",
        page: 1,
        size: 50,
      }),
    ).toEqual(data);
    const url = new URL(fetcher.mock.calls[0][0], "https://test.invalid");
    expect(url.pathname).toBe("/api/code-mappings");
    expect(url.searchParams.get("sourceText")).toBe("표기 & %");
    expect(url.searchParams.get("groupId")).toBe("GROUP");
  });
  it("201 JSON과 204 빈 응답을 처리하고 삭제 revision을 body로 보낸다", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ success: true, meta: {} }), {
          status: 201,
        }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetcher);
    const create = { sourceText: "표기", groupId: "GROUP", code: "A" };
    await createCodeMapping(create);
    const deletion = { items: [{ mapId: 42, expectedRevision: "revision" }] };
    expect(await deleteCodeMappings(deletion)).toBeUndefined();
    expect(fetcher.mock.calls[0][1].body).toBe(JSON.stringify(create));
    expect(fetcher.mock.calls[1][0]).toBe("/api/code-mappings/delete");
    expect(fetcher.mock.calls[1][1].body).toBe(JSON.stringify(deletion));
  });
  it("선택된 그룹의 active 코드만 조회한다", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
        ),
      );
    vi.stubGlobal("fetch", fetcher);
    await getMappingCodes("GROUP");
    expect(fetcher.mock.calls[0][0]).toBe(
      "/api/codes/GROUP?includeInactive=false",
    );
  });
});
