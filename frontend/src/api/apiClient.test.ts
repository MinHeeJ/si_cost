import { afterEach, describe, expect, it, vi } from "vitest";
import * as api from "./apiClient";

const cases: {
  name: string;
  path: string;
  method: string;
  call(): Promise<unknown>;
  body?: unknown;
  status?: number;
}[] = [
  {
    name: "getHealth",
    path: "/api/health",
    method: "GET",
    call: () => api.getHealth(),
  },
  {
    name: "getCurrentUser",
    path: "/api/current-user",
    method: "GET",
    call: () => api.getCurrentUser(),
  },
  {
    name: "getMenus",
    path: "/api/menus",
    method: "GET",
    call: () => api.getMenus(),
  },
  {
    name: "getPeriodContext",
    path: "/api/period-context",
    method: "GET",
    call: () => api.getPeriodContext(),
  },
  {
    name: "getMonthClose",
    path: "/api/month-close?from=2026-01&to=2026-02",
    method: "GET",
    call: () => api.getMonthClose("2026-01", "2026-02"),
  },
  {
    name: "getPreferences",
    path: "/api/me/preferences",
    method: "GET",
    call: () => api.getPreferences(),
  },
  {
    name: "putPreference",
    path: "/api/me/preferences/PAGE_SIZE/*",
    method: "PUT",
    call: () => api.putPreference("PAGE_SIZE", "*", 20, null),
    body: { value: 20, expectedRevision: null },
  },
  {
    name: "deletePreference",
    path: "/api/me/preferences/COLS/UI-27?expectedRevision=rev",
    method: "DELETE",
    call: () => api.deletePreference("COLS", "UI-27", "rev"),
    status: 204,
  },
  {
    name: "getCodeGroups",
    path: "/api/code-groups",
    method: "GET",
    call: () => api.getCodeGroups(),
  },
  {
    name: "createCodeGroup",
    path: "/api/code-groups",
    method: "POST",
    call: () => api.createCodeGroup({ groupId: "EDUCATION", name: "교육" }),
    body: { groupId: "EDUCATION", name: "교육" },
    status: 201,
  },
  {
    name: "getGroupCodes",
    path: "/api/code-groups/EDUCATION/codes?page=1&size=20",
    method: "GET",
    call: () => api.getGroupCodes("EDUCATION", 1, 20),
  },
  {
    name: "saveGroupCodes",
    path: "/api/code-groups/EDUCATION/codes",
    method: "PUT",
    call: () =>
      api.saveGroupCodes("EDUCATION", {
        expectedRevision: "rev",
        changes: [{ code: "A", isNew: false, name: "이름" }],
      }),
    body: {
      expectedRevision: "rev",
      changes: [{ code: "A", isNew: false, name: "이름" }],
    },
  },
  {
    name: "deleteGroupCode",
    path: "/api/code-groups/EDUCATION/codes/A?expectedRevision=rev",
    method: "DELETE",
    call: () => api.deleteGroupCode("EDUCATION", "A", "rev"),
    status: 204,
  },
  {
    name: "getCodes",
    path: "/api/codes/EDUCATION?includeInactive=false",
    method: "GET",
    call: () => api.getCodes("EDUCATION"),
  },
  {
    name: "getCodeMappings",
    path: "/api/code-mappings?page=1&size=20&sourceText=%ED%95%99%EA%B5%90&groupId=EDUCATION",
    method: "GET",
    call: () =>
      api.getCodeMappings({
        page: 1,
        size: 20,
        sourceText: "학교",
        groupId: "EDUCATION",
      }),
  },
  {
    name: "createCodeMapping",
    path: "/api/code-mappings",
    method: "POST",
    call: () =>
      api.createCodeMapping({
        sourceText: "학교",
        groupId: "EDUCATION",
        code: "A",
      }),
    body: { sourceText: "학교", groupId: "EDUCATION", code: "A" },
    status: 201,
  },
  {
    name: "deleteCodeMappings",
    path: "/api/code-mappings/delete",
    method: "POST",
    call: () =>
      api.deleteCodeMappings({
        items: [{ mapId: 7, expectedRevision: "rev" }],
      }),
    body: { items: [{ mapId: 7, expectedRevision: "rev" }] },
    status: 204,
  },
  {
    name: "getOperatingStandards",
    path: "/api/operating-standards?month=2026-02",
    method: "GET",
    call: () => api.getOperatingStandards("2026-02"),
  },
  {
    name: "saveOperatingStandards",
    path: "/api/operating-standards",
    method: "PUT",
    call: () =>
      api.saveOperatingStandards({
        effectiveMonth: "2026-02",
        changes: [{ key: "DUN_D1", value: "1.2300", expectedRevision: "rev" }],
      }),
    body: {
      effectiveMonth: "2026-02",
      changes: [{ key: "DUN_D1", value: "1.2300", expectedRevision: "rev" }],
    },
  },
  {
    name: "getOperatingStandardHistory",
    path: "/api/operating-standards/DUN_D1/history",
    method: "GET",
    call: () => api.getOperatingStandardHistory("DUN_D1"),
  },
  {
    name: "getParameter",
    path: "/api/params/DUN_D1?month=2026-02",
    method: "GET",
    call: () => api.getParameter("DUN_D1", "2026-02"),
  },
  {
    name: "getCommonCodeHistory",
    path: "/api/common-code-history?page=1&size=20&targetType=CODE&groupId=EDUCATION",
    method: "GET",
    call: () =>
      api.getCommonCodeHistory({
        page: 1,
        size: 20,
        targetType: "CODE",
        groupId: "EDUCATION",
      }),
  },
];
afterEach(() => vi.unstubAllGlobals());
describe("승인 API 등록", () => {
  it.each(cases)(
    "$name은 기존 전송 계층과 정확한 경로/메서드/본문을 사용한다",
    async (test) => {
      const fetcher = vi.fn().mockResolvedValue(
        new Response(
          test.status
            ? null
            : JSON.stringify({
                success: true,
                meta: {},
                data: { value: "1.2300" },
              }),
          { status: test.status ?? 200 },
        ),
      );
      vi.stubGlobal("fetch", fetcher);
      expect(await test.call()).toEqual(
        test.status ? undefined : { value: "1.2300" },
      );
      expect(fetcher).toHaveBeenCalledTimes(1);
      const [path, options] = fetcher.mock.calls[0] as [string, RequestInit];
      expect(path).toBe(test.path);
      expect(options.method ?? "GET").toBe(test.method);
      expect(
        options.body ? JSON.parse(String(options.body)) : undefined,
      ).toEqual(test.body);
      expect(new Headers(options.headers).get("Accept")).toBe(
        "application/json",
      );
    },
  );
  it("선택한 식별자는 경로 세그먼트로 인코딩한다", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
        ),
      );
    vi.stubGlobal("fetch", fetcher);
    await api.getCodes("A/B");
    expect(fetcher.mock.calls[0][0]).toBe(
      "/api/codes/A%2FB?includeInactive=false",
    );
  });
  it.each([400, 404, 409, 500, 503])(
    "%i 평면 오류는 필드·문의번호를 보존하고 자동 재시도하지 않는다",
    async (status) => {
      const body = {
        success: false,
        meta: {},
        code: "TEST_ERROR",
        message: "요청 실패",
        field: "value",
        requestId: "request-test",
        details: { violations: [{ field: "name", message: "이름 확인" }] },
      };
      const fetcher = vi
        .fn()
        .mockResolvedValue(new Response(JSON.stringify(body), { status }));
      vi.stubGlobal("fetch", fetcher);
      await expect(api.getParameter("DUN_D1")).rejects.toMatchObject({
        status,
        body,
        fieldMessages: { value: "요청 실패", name: "이름 확인" },
      });
      expect(fetcher).toHaveBeenCalledTimes(1);
    },
  );
});
