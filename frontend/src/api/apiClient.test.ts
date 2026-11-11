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
    name: "getAreaSidos",
    path: "/api/areas/sidos",
    method: "GET",
    call: () => api.getAreaSidos(),
  },
  {
    name: "getAreaChildren",
    path: "/api/areas/children?parentCode=1100000000&level=SGG",
    method: "GET",
    call: () => api.getAreaChildren("1100000000", "SGG"),
  },
  {
    name: "getAreaPath",
    path: "/api/areas/1100000000/path",
    method: "GET",
    call: () => api.getAreaPath("1100000000"),
  },
  {
    name: "getDevelopers",
    path: "/api/developers?page=1&size=20",
    method: "GET",
    call: () => api.getDevelopers({ page: 1, size: 20 }),
  },
  {
    name: "getDeveloper",
    path: "/api/developers/DEV-000901",
    method: "GET",
    call: () => api.getDeveloper("DEV-000901"),
  },
  {
    name: "getDeveloperSelection",
    path: "/api/developers/selection?includeStopped=true",
    method: "GET",
    call: () => api.getDeveloperSelection({ includeStopped: true }),
  },
  {
    name: "getDeveloperAssignments",
    path: "/api/developers/DEV-000901/assignments?page=2&size=20",
    method: "GET",
    call: () => api.getDeveloperAssignments("DEV-000901", 2, 20),
  },
  {
    name: "getDeveloperStates",
    path: "/api/developers/DEV-000901/states",
    method: "GET",
    call: () => api.getDeveloperStates("DEV-000901"),
  },
  {
    name: "createDeveloperState",
    path: "/api/developers/DEV-000901/states",
    method: "POST",
    call: () =>
      api.createDeveloperState("DEV-000901", {
        state: "WORK",
        startDate: "2026-11-11",
        reason: null,
        expectedRevision: "rev",
      }),
    body: {
      state: "WORK",
      startDate: "2026-11-11",
      reason: null,
      expectedRevision: "rev",
    },
  },
  {
    name: "putDeveloperStateEnd",
    path: "/api/developers/DEV-000901/states/9/end",
    method: "PATCH",
    call: () =>
      api.putDeveloperStateEnd("DEV-000901", 9, {
        endDate: null,
        expectedRevision: "rev",
      }),
    body: { endDate: null, expectedRevision: "rev" },
  },
  {
    name: "saveDeveloperStateCancel",
    path: "/api/developers/DEV-000901/states/9/cancel",
    method: "POST",
    call: () =>
      api.saveDeveloperStateCancel("DEV-000901", 9, {
        reason: "취소",
        expectedRevision: "rev",
      }),
    body: { reason: "취소", expectedRevision: "rev" },
  },
  {
    name: "saveDeveloperBulkStatus",
    path: "/api/developers/bulk-status",
    method: "POST",
    call: () =>
      api.saveDeveloperBulkStatus("N", [{ id: "DEV-000901", revision: "rev" }]),
    body: { targetUseYn: "N", items: [{ id: "DEV-000901", revision: "rev" }] },
  },
  {
    name: "getCandidates",
    path: "/api/candidates?page=2&size=20",
    method: "GET",
    call: () => api.getCandidates({ page: 2, size: 20 }),
  },
  {
    name: "getCandidate",
    path: "/api/candidates/CAND-900001",
    method: "GET",
    call: () => api.getCandidate("CAND-900001"),
  },
  {
    name: "getCandidateSummary",
    path: "/api/candidates/summary",
    method: "GET",
    call: () => api.getCandidateSummary(),
  },
  {
    name: "getCandidateContacts",
    path: "/api/candidates/CAND-900001/contacts?page=2&size=20",
    method: "GET",
    call: () => api.getCandidateContacts("CAND-900001", 2, 20),
  },
  {
    name: "createCandidateContact",
    path: "/api/candidates/CAND-900001/contacts",
    method: "POST",
    call: () =>
      api.createCandidateContact("CAND-900001", {
        contactStatus: "DONE",
        memo: "연락",
        expectedRevision: "rev",
      }),
    body: { contactStatus: "DONE", memo: "연락", expectedRevision: "rev" },
  },
  {
    name: "getPeopleHistory",
    path: "/api/people-history?targetType=DEVELOPER&targetId=DEV-000901&page=1&size=50",
    method: "GET",
    call: () => api.getPeopleHistory("DEVELOPER", "DEV-000901"),
  },
  {
    name: "getBusinessUsers",
    path: "/api/business-users",
    method: "GET",
    call: () => api.getBusinessUsers(),
  },
  {
    name: "getBusinessUser",
    path: "/api/business-users/selected",
    method: "GET",
    call: () => api.getBusinessUser("selected"),
  },
  {
    name: "createBusinessUser",
    path: "/api/business-users",
    method: "POST",
    call: () =>
      api.createBusinessUser({
        userId: "newuser",
        name: "담당자",
        email: "user@example.org",
        businessRoleIds: ["PM"],
      }),
    body: {
      userId: "newuser",
      name: "담당자",
      email: "user@example.org",
      businessRoleIds: ["PM"],
    },
  },
  {
    name: "putBusinessUser",
    path: "/api/business-users/selected",
    method: "PUT",
    call: () =>
      api.putBusinessUser("selected", {
        name: "담당자",
        email: "user@example.org",
        businessRoleIds: [],
        expectedRevision: "rev",
      }),
    body: {
      name: "담당자",
      email: "user@example.org",
      businessRoleIds: [],
      expectedRevision: "rev",
    },
  },
  {
    name: "getBusinessUserOptions",
    path: "/api/business-users/options?purpose=PART_LEADER",
    method: "GET",
    call: () => api.getBusinessUserOptions("PART_LEADER"),
  },
  {
    name: "getClients",
    path: "/api/clients?page=1&size=50",
    method: "GET",
    call: () => api.getClients({ page: 1, size: 50 }),
  },
  {
    name: "getClient",
    path: "/api/clients/C-123",
    method: "GET",
    call: () => api.getClient("C-123"),
  },
  {
    name: "createClient",
    path: "/api/clients",
    method: "POST",
    call: () =>
      api.createClient({
        type: "CUST",
        name: "거래처",
        businessNo: "1234567890",
        taxEmail: "client@example.org",
      }),
    body: {
      type: "CUST",
      name: "거래처",
      businessNo: "1234567890",
      taxEmail: "client@example.org",
    },
  },
  {
    name: "putClient",
    path: "/api/clients/C-123",
    method: "PUT",
    call: () =>
      api.putClient("C-123", {
        type: "BOTH",
        name: "거래처",
        taxEmail: "client@example.org",
        accountAction: "KEEP",
        expectedRevision: "rev",
      }),
    body: {
      type: "BOTH",
      name: "거래처",
      taxEmail: "client@example.org",
      accountAction: "KEEP",
      expectedRevision: "rev",
    },
  },
  {
    name: "getClientSelection",
    path: "/api/clients/selection?useYn=Y",
    method: "GET",
    call: () => api.getClientSelection({ useYn: "Y" }),
  },
  {
    name: "getClientOptions",
    path: "/api/clients/options?purpose=CUSTOMER",
    method: "GET",
    call: () => api.getClientOptions("CUSTOMER"),
  },
  {
    name: "getClientAccount",
    path: "/api/clients/C-123/account",
    method: "GET",
    call: () => api.getClientAccount("C-123"),
  },
  {
    name: "getClientAssignments",
    path: "/api/clients/C-123/assignments?page=1&size=50",
    method: "GET",
    call: () => api.getClientAssignments("C-123"),
  },
  {
    name: "saveClientBulkStatus",
    path: "/api/clients/bulk-status",
    method: "POST",
    call: () =>
      api.saveClientBulkStatus("N", [{ id: "C-123", expectedRevision: "rev" }]),
    body: {
      targetUseYn: "N",
      items: [{ id: "C-123", expectedRevision: "rev" }],
    },
  },
  {
    name: "getSalesParts",
    path: "/api/sales-parts?page=1&size=50&sort=name&direction=ASC",
    method: "GET",
    call: () =>
      api.getSalesParts({ page: 1, size: 50, sort: "name", direction: "ASC" }),
  },
  {
    name: "getSalesPart",
    path: "/api/sales-parts/PART-123",
    method: "GET",
    call: () => api.getSalesPart("PART-123"),
  },
  {
    name: "createSalesPart",
    path: "/api/sales-parts",
    method: "POST",
    call: () =>
      api.createSalesPart({
        name: "파트",
        effectiveMonth: "2026-11",
        leaderUserId: "selected",
        profitRate: "40.25",
        salesExpenseAmount: "999999999999999",
      }),
    body: {
      name: "파트",
      effectiveMonth: "2026-11",
      leaderUserId: "selected",
      profitRate: "40.25",
      salesExpenseAmount: "999999999999999",
    },
  },
  {
    name: "putSalesPart",
    path: "/api/sales-parts/PART-123",
    method: "PATCH",
    call: () =>
      api.putSalesPart("PART-123", { name: "파트", expectedRevision: "rev" }),
    body: { name: "파트", expectedRevision: "rev" },
  },
  {
    name: "getSalesPartDefaults",
    path: "/api/sales-parts/defaults?effectiveMonth=2026-11",
    method: "GET",
    call: () => api.getSalesPartDefaults("2026-11"),
  },
  {
    name: "getSalesPartOptions",
    path: "/api/sales-parts/options",
    method: "GET",
    call: () => api.getSalesPartOptions(),
  },
  {
    name: "createSalesPartStandard",
    path: "/api/sales-parts/PART-123/standards",
    method: "POST",
    call: () =>
      api.createSalesPartStandard("PART-123", {
        effectiveMonth: "2026-11",
        leaderUserId: "selected",
        profitRate: "40.25",
        salesExpenseAmount: "999999999999999",
        expectedRevision: "rev",
      }),
    body: {
      effectiveMonth: "2026-11",
      leaderUserId: "selected",
      profitRate: "40.25",
      salesExpenseAmount: "999999999999999",
      expectedRevision: "rev",
    },
  },
  {
    name: "getMasterHistory",
    path: "/api/master-history?targetType=USER&targetId=selected&page=1&size=50",
    method: "GET",
    call: () => api.getMasterHistory("USER", "selected"),
  },
  {
    name: "getUploadIssues",
    path: "/api/uploads/123/issues?page=1&size=50&severity=WARN",
    method: "GET",
    call: () => api.getUploadIssues(123, "WARN", 1, 50),
  },
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
  it.each(["validate", "apply"] as const)(
    "B8 %s 등록은 feature 전송 계층을 그대로 사용한다",
    async (operation) => {
      const fetcher = vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: true,
            meta: {},
            data: { uploadId: 123 },
          }),
        ),
      );
      vi.stubGlobal("fetch", fetcher);
      const file = new File(["workbook"], "developers.xlsx");
      if (operation === "validate") await api.createB8Validation(file);
      else await api.saveB8Apply(file, 123);
      const [path, options] = fetcher.mock.calls[0] as [string, RequestInit];
      expect(path).toBe(`/api/excel/B8/${operation}`);
      expect(options.method).toBe("POST");
      expect(new Headers(options.headers).has("Content-Type")).toBe(false);
      expect((options.body as FormData).get("file")).toBe(file);
      expect((options.body as FormData).get("uploadId")).toBe(
        operation === "apply" ? "123" : null,
      );
    },
  );
  it.each(["validate", "apply"] as const)(
    "B7 %s는 multipart 경계를 브라우저에 맡긴다",
    async (operation) => {
      const fetcher = vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: true,
            meta: {},
            data: { uploadId: 123 },
          }),
          { headers: { "Content-Type": "application/json" } },
        ),
      );
      vi.stubGlobal("fetch", fetcher);
      const file = new File(["workbook"], "clients.xlsx");
      if (operation === "validate") await api.createB7Validation(file);
      else await api.saveB7Apply(file, 123);
      const [path, options] = fetcher.mock.calls[0] as [string, RequestInit];
      expect(path).toBe(`/api/excel/B7/${operation}`);
      expect(options.method).toBe("POST");
      expect(new Headers(options.headers).has("Content-Type")).toBe(false);
      expect(options.body).toBeInstanceOf(FormData);
      expect((options.body as FormData).get("file")).toBe(file);
      expect((options.body as FormData).get("uploadId")).toBe(
        operation === "apply" ? "123" : null,
      );
    },
  );
  it.each([
    {
      path: "/api/excel/B8/template",
      call: () => api.getB8Template(),
      filename: "B8_개발자양식_20261111_120000.xlsx",
    },
    {
      path: "/api/excel/B7/template",
      call: () => api.getB7Template(),
      filename: "template.xlsx",
    },
    {
      path: "/api/uploads/123/issues.xlsx?severity=ERR",
      call: () => api.getUploadIssuesXlsx(123, "ERR"),
      filename: "issues.xlsx",
    },
    {
      path: "/api/exports/master-data",
      call: () =>
        api.createMasterDataExport({
          view: "CLIENTS",
          filters: {},
          columns: ["name"],
        }),
      filename: "거래처_20261111_120000.xlsx",
    },
  ])("$path는 xlsx를 JSON으로 파싱하지 않고 반환한다", async (test) => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response("workbook", {
        headers: {
          "Content-Type":
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(test.filename)}`,
        },
      }),
    );
    vi.stubGlobal("fetch", fetcher);
    const result = await test.call();
    expect(result.filename).toBe(test.filename);
    expect(result.blob.size).toBeGreaterThan(0);
    expect(fetcher.mock.calls[0][0]).toBe(test.path);
  });
  it.each([
    () => api.getB7Template(),
    () => api.getUploadIssuesXlsx(123, ""),
    () => api.createB7Validation(new File(["workbook"], "clients.xlsx")),
    () => api.saveB7Apply(new File(["workbook"], "clients.xlsx"), 123),
    () =>
      api.createMasterDataExport({
        view: "CLIENTS",
        filters: {},
        columns: ["name"],
      }),
  ])(
    "파일 클라이언트는 JSON 오류를 유지하고 자동 재시도하지 않는다",
    async (call) => {
      const body = {
        success: false,
        meta: {},
        code: "REQUEST_FAILED",
        message: "요청 실패",
        requestId: "request-test",
      };
      const fetcher = vi.fn().mockResolvedValue(
        new Response(JSON.stringify(body), {
          status: 409,
          headers: { "Content-Type": "application/json" },
        }),
      );
      vi.stubGlobal("fetch", fetcher);
      await expect(call()).rejects.toMatchObject({ status: 409, body });
      expect(fetcher).toHaveBeenCalledTimes(1);
    },
  );
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
