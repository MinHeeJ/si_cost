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
const projectCases: typeof cases = [
  {
    name: "getProjects",
    path: "/api/projects?year=2026&page=1&size=50",
    method: "GET",
    call: () => api.getProjects({ year: 2026, page: 1, size: 50 }),
  },
  {
    name: "getProject",
    path: "/api/projects/P%2Fselected?includeCancelled=false",
    method: "GET",
    call: () => api.getProject("P/selected"),
  },
  {
    name: "createProject",
    path: "/api/projects",
    method: "POST",
    call: () =>
      api.createProject({
        year: 2026,
        name: "내부 프로젝트",
        contractType: "IN",
        initialStatus: "ING",
        startDate: "2026-01-01",
        endDate: "2026-12-31",
      }),
    body: {
      year: 2026,
      name: "내부 프로젝트",
      contractType: "IN",
      initialStatus: "ING",
      startDate: "2026-01-01",
      endDate: "2026-12-31",
    },
  },
  {
    name: "putProject",
    path: "/api/projects/P%2Fselected",
    method: "PUT",
    call: () =>
      api.putProject("P/selected", {
        year: 2026,
        name: "수정 프로젝트",
        startDate: "2026-01-01",
        endDate: "2026-12-31",
        expectedRevision: "rev",
      }),
    body: {
      year: 2026,
      name: "수정 프로젝트",
      startDate: "2026-01-01",
      endDate: "2026-12-31",
      expectedRevision: "rev",
    },
  },
  {
    name: "getProjectDefaults",
    path: "/api/projects/defaults?contractAmount=999999999999999&contractType=TK",
    method: "GET",
    call: () => api.getProjectDefaults("999999999999999", "TK"),
  },
  {
    name: "getProjectOptions",
    path: "/api/projects/options?purpose=ASSIGNMENT&q=",
    method: "GET",
    call: () => api.getProjectOptions("ASSIGNMENT"),
  },
  {
    name: "getProjectBudget",
    path: "/api/projects/P%2Fselected/budget",
    method: "GET",
    call: () => api.getProjectBudget("P/selected"),
  },
  {
    name: "saveProjectBudget",
    path: "/api/projects/P%2Fselected/budget",
    method: "PUT",
    call: () =>
      api.saveProjectBudget("P/selected", {
        year: 2026,
        expectedRevision: "rev",
        budgetAmount: "0",
      }),
    body: { year: 2026, expectedRevision: "rev", budgetAmount: "0" },
  },
  {
    name: "getProjectParts",
    path: "/api/projects/P%2Fselected/parts",
    method: "GET",
    call: () => api.getProjectParts("P/selected"),
  },
  {
    name: "saveProjectPart",
    path: "/api/projects/P%2Fselected/parts",
    method: "PUT",
    call: () =>
      api.saveProjectPart("P/selected", {
        year: 2026,
        expectedRevision: "rev",
        partId: "selected-part",
        effectiveMonth: "2026-12",
        reason: null,
      }),
    body: {
      year: 2026,
      expectedRevision: "rev",
      partId: "selected-part",
      effectiveMonth: "2026-12",
      reason: null,
    },
  },
  {
    name: "getProjectPlans",
    path: "/api/projects/P%2Fselected/plans",
    method: "GET",
    call: () => api.getProjectPlans("P/selected"),
  },
  {
    name: "saveProjectPlans",
    path: "/api/projects/P%2Fselected/plans",
    method: "PUT",
    call: () =>
      api.saveProjectPlans("P/selected", {
        year: 2026,
        expectedRevision: "rev",
        items: [],
      }),
    body: { year: 2026, expectedRevision: "rev", items: [] },
  },
  {
    name: "getProjectExpenses",
    path: "/api/projects/P%2Fselected/expenses",
    method: "GET",
    call: () => api.getProjectExpenses("P/selected"),
  },
  {
    name: "createProjectExpense",
    path: "/api/projects/P%2Fselected/expenses",
    method: "POST",
    call: () =>
      api.createProjectExpense("P/selected", {
        year: 2026,
        expectedRevision: "rev",
        expenseMonth: "2026-11",
        itemCode: "selected-code",
        amount: "1",
        memo: null,
      }),
    body: {
      year: 2026,
      expectedRevision: "rev",
      expenseMonth: "2026-11",
      itemCode: "selected-code",
      amount: "1",
      memo: null,
    },
  },
  {
    name: "deleteProjectExpense",
    path: "/api/projects/P%2Fselected/expenses/2026-11/7",
    method: "DELETE",
    call: () =>
      api.deleteProjectExpense(
        "P/selected",
        { expenseMonth: "2026-11", expenseSn: 7 },
        { year: 2026, expectedRevision: "rev" },
      ),
    body: { year: 2026, expectedRevision: "rev" },
  },
  {
    name: "getBusinessUserProjects",
    path: "/api/business-users/selected%2Fuser/projects",
    method: "GET",
    call: () => api.getBusinessUserProjects("selected/user"),
  },
  {
    name: "getProjectPm",
    path: "/api/projects/P%2Fselected/pm",
    method: "GET",
    call: () => api.getProjectPm("P/selected"),
  },
  {
    name: "saveProjectPm",
    path: "/api/projects/P%2Fselected/pm",
    method: "PUT",
    call: () => api.saveProjectPm("P/selected", "selected-user", "rev", true),
    body: {
      userId: "selected-user",
      expectedRevision: "rev",
      replaceConfirmed: true,
    },
  },
  {
    name: "deleteProjectPm",
    path: "/api/projects/P%2Fselected/pm",
    method: "DELETE",
    call: () => api.deleteProjectPm("P/selected", "selected-user", "rev"),
    body: { userId: "selected-user", expectedRevision: "rev" },
  },
  {
    name: "getProjectLossPreview",
    path: "/api/projects/P%2Fselected/loss-preview",
    method: "GET",
    call: () => api.getProjectLossPreview("P/selected"),
  },
  {
    name: "saveProjectProgress",
    path: "/api/projects/P%2Fselected/progress",
    method: "PUT",
    call: () =>
      api.saveProjectProgress("P/selected", {
        year: 2026,
        expectedRevision: "rev",
      }),
    body: { year: 2026, expectedRevision: "rev" },
  },
  {
    name: "saveProjectLoss",
    path: "/api/projects/P%2Fselected/loss",
    method: "PUT",
    call: () =>
      api.saveProjectLoss("P/selected", {
        year: 2026,
        expectedRevision: "rev",
        lossReason: "selected-reason",
        lossMemo: null,
        previewToken: "preview",
      }),
    body: {
      year: 2026,
      expectedRevision: "rev",
      lossReason: "selected-reason",
      lossMemo: null,
      previewToken: "preview",
    },
  },
  {
    name: "getProjectDistribution",
    path: "/api/projects/P%2Fselected/distribution?step=PLAN",
    method: "GET",
    call: () => api.getProjectDistribution("P/selected", "PLAN"),
  },
  {
    name: "getProjectDistributionComparison",
    path: "/api/projects/P%2Fselected/distribution/comparison",
    method: "GET",
    call: () => api.getProjectDistributionComparison("P/selected"),
  },
  {
    name: "getProjectDistributionUploads",
    path: "/api/projects/P%2Fselected/distribution/uploads",
    method: "GET",
    call: () => api.getProjectDistributionUploads("P/selected"),
  },
  {
    name: "getUploadResult",
    path: "/api/uploads/123/result",
    method: "GET",
    call: () => api.getUploadResult(123),
  },
];
const assignmentInput = {
  year: 2026,
  projectId: "selected-project",
  developerId: "selected-developer",
  jobCode: "DEV",
  startDate: "2026-11-01",
  endDate: "2026-12-31",
};
const assignmentCases: typeof cases = [
  {
    name: "getAssignments",
    path: "/api/assignments?year=2026",
    method: "GET",
    call: () => api.getAssignments({ year: 2026 }),
  },
  {
    name: "getAssignment",
    path: "/api/assignments/selected%2Fassignment?year=2026",
    method: "GET",
    call: () => api.getAssignment("selected/assignment", 2026),
  },
  {
    name: "getAssignmentMonths",
    path: "/api/assignments/selected%2Fassignment/months?year=2026&page=1&size=50",
    method: "GET",
    call: () => api.getAssignmentMonths("selected/assignment", 2026),
  },
  {
    name: "getAssignmentHistory",
    path: "/api/assignments/selected%2Fassignment/history?year=2026&page=1&size=50",
    method: "GET",
    call: () =>
      api.getAssignmentHistory("selected/assignment", {
        year: 2026,
        page: 1,
        size: 50,
      }),
  },
  {
    name: "createAssignment",
    path: "/api/assignments",
    method: "POST",
    call: () => api.createAssignment(assignmentInput),
    body: assignmentInput,
  },
  {
    name: "createAssignmentPreview",
    path: "/api/assignments/preview",
    method: "POST",
    call: () => api.createAssignmentPreview(assignmentInput),
    body: assignmentInput,
  },
  {
    name: "createAssignmentChange",
    path: "/api/assignments/selected%2Fassignment/changes",
    method: "POST",
    call: () =>
      api.createAssignmentChange("selected/assignment", {
        year: 2026,
        expectedRevision: "revision",
        type: "EXT",
        newEndDate: "2027-01-01",
      }),
    body: {
      year: 2026,
      expectedRevision: "revision",
      type: "EXT",
      newEndDate: "2027-01-01",
    },
  },
  {
    name: "createAssignmentSelection",
    path: "/api/assignments/selection",
    method: "POST",
    call: () =>
      api.createAssignmentSelection({
        year: 2026,
        scope: "ALL",
        filters: { year: 2026 },
      }),
    body: { year: 2026, scope: "ALL", filters: { year: 2026 } },
  },
  {
    name: "saveAssignmentBulk",
    path: "/api/assignments/bulk",
    method: "POST",
    call: () =>
      api.saveAssignmentBulk({
        year: 2026,
        type: "CNCL",
        items: [{ id: "selected/assignment", expectedRevision: "revision" }],
      }),
    body: {
      year: 2026,
      type: "CNCL",
      items: [{ id: "selected/assignment", expectedRevision: "revision" }],
    },
  },
  {
    name: "getAssignmentSubstitutionCandidates",
    path: "/api/assignments/substitution-candidates?projectId=selected-project&developerId=selected-developer&page=1&size=50",
    method: "GET",
    call: () =>
      api.getAssignmentSubstitutionCandidates(
        "selected-project",
        "selected-developer",
      ),
  },
  {
    name: "createAssignmentSubstitutionPreview",
    path: "/api/assignments/substitution-preview",
    method: "POST",
    call: () => api.createAssignmentSubstitutionPreview(assignmentInput),
    body: { assignment: assignmentInput },
  },
  {
    name: "createAssignmentSubstitution",
    path: "/api/assignments/substitutions",
    method: "POST",
    call: () =>
      api.createAssignmentSubstitution(assignmentInput, "ONLY", "preview", []),
    body: {
      assignment: assignmentInput,
      mode: "ONLY",
      previewToken: "preview",
      withdrawals: [],
    },
  },
  {
    name: "createAssignmentHistoryCorrection",
    path: "/api/assignments/selected%2Fassignment/history/2/corrections",
    method: "POST",
    call: () =>
      api.createAssignmentHistoryCorrection("selected/assignment", 2, {
        year: 2026,
        expectedRevision: "revision",
        correctContent: "정정",
        reasonCode: "ETC",
      }),
    body: {
      year: 2026,
      expectedRevision: "revision",
      correctContent: "정정",
      reasonCode: "ETC",
    },
  },
  {
    name: "getPayroll",
    path: "/api/payroll?month=2026-11&page=1&size=50",
    method: "GET",
    call: () => api.getPayroll({ month: "2026-11", page: 1, size: 50 }),
  },
  {
    name: "getPayrollUploads",
    path: "/api/payroll/uploads?month=2026-11&page=2&size=20",
    method: "GET",
    call: () => api.getPayrollUploads("2026-11", 2, 20),
  },
  {
    name: "getPayrollResult",
    path: "/api/uploads/123/result",
    method: "GET",
    call: () => api.getPayrollResult(123),
  },
  {
    name: "getB5Result",
    path: "/api/uploads/123/result",
    method: "GET",
    call: () => api.getB5Result(123),
  },
];
const billDraft = {
  year: 2026,
  projectId: "selected-project",
  billingMonth: "2026-11",
  supplyAmount: "99999999999999",
  vatRate: "0.25",
  billingDate: "2026-11-10",
  dueDate: "2026-12-25",
  taxInvoiceNo: null,
};
const billEdit = {
  year: 2026,
  expectedRevision: "bill-revision",
  supplyAmount: "99999999999999",
  vatRate: "0.25",
  billingDate: "2026-11-10",
  dueDate: "2026-12-25",
  taxInvoiceNo: null,
};
const billCancel = {
  year: 2026,
  expectedRevision: "bill-revision",
  reason: "취소 사유",
};
const receiptDraft = {
  year: 2026,
  expectedRevision: "bill-revision",
  date: "2026-11-10",
  amount: "1000",
};
const batchDraft = {
  year: 2026,
  date: "2026-11-10",
  items: [
    {
      billId: "selected/bill",
      amount: "1000",
      expectedRevision: "bill-revision",
    },
  ],
};
const copiedDraft = {
  ...billDraft,
  copyContext: {
    sourceBillId: "selected/bill",
    expectedRevision: "bill-revision",
  },
};
const billingCases: typeof cases = [
  {
    name: "getBills",
    path: "/api/bills?year=2026&includeCarryover=true&page=1&size=20",
    method: "GET",
    call: () =>
      api.getBills({ year: 2026, includeCarryover: true, page: 1, size: 20 }),
  },
  {
    name: "getBill",
    path: "/api/bills/selected%2Fbill?year=2026",
    method: "GET",
    call: () => api.getBill("selected/bill", 2026),
  },
  {
    name: "createBill",
    path: "/api/bills",
    method: "POST",
    call: () => api.createBill(billDraft),
    body: billDraft,
  },
  {
    name: "putBill",
    path: "/api/bills/selected%2Fbill",
    method: "PUT",
    call: () => api.putBill("selected/bill", billEdit),
    body: billEdit,
  },
  {
    name: "getBillEstimate",
    path: "/api/bills/estimate?projectId=selected-project&month=2026-11",
    method: "GET",
    call: () => api.getBillEstimate("selected-project", "2026-11"),
  },
  {
    name: "getBillsSummary",
    path: "/api/bills/summary?year=2026&includeCarryover=false",
    method: "GET",
    call: () => api.getBillsSummary(2026, false),
  },
  {
    name: "getBillSelection",
    path: "/api/bills/selection?year=2026&ids=selected%2Fbill",
    method: "GET",
    call: () => api.getBillSelection({ year: 2026, ids: ["selected/bill"] }),
  },
  {
    name: "getBillCopyDraft",
    path: "/api/bills/selected%2Fbill/copy-draft?year=2026",
    method: "GET",
    call: () => api.getBillCopyDraft("selected/bill", 2026),
  },
  {
    name: "saveBillCancellation",
    path: "/api/bills/selected%2Fbill/cancel",
    method: "POST",
    call: () => api.saveBillCancellation("selected/bill", billCancel),
    body: billCancel,
  },
  {
    name: "createNextMonthBill",
    path: "/api/bills/selected%2Fbill/next-month",
    method: "POST",
    call: () =>
      api.createNextMonthBill("selected/bill", {
        year: 2026,
        expectedRevision: "bill-revision",
      }),
    body: { year: 2026, expectedRevision: "bill-revision" },
  },
  {
    name: "createCopiedBill",
    path: "/api/bills",
    method: "POST",
    call: () => api.createCopiedBill(copiedDraft),
    body: copiedDraft,
  },
  {
    name: "getBillReceipts",
    path: "/api/bills/selected%2Fbill/receipts?year=2026",
    method: "GET",
    call: () => api.getBillReceipts("selected/bill", 2026),
  },
  {
    name: "createBillReceipt",
    path: "/api/bills/selected%2Fbill/receipts",
    method: "POST",
    call: () => api.createBillReceipt("selected/bill", receiptDraft),
    body: receiptDraft,
  },
  {
    name: "saveBillReceiptCancellation",
    path: "/api/bills/selected%2Fbill/receipts/42/cancel",
    method: "POST",
    call: () =>
      api.saveBillReceiptCancellation("selected/bill", 42, billCancel),
    body: billCancel,
  },
  {
    name: "loadReceiptBatchSelection",
    path: "/api/bills/selection?year=2026&ids=selected%2Fbill",
    method: "GET",
    call: () =>
      api.loadReceiptBatchSelection({ year: 2026 }, ["selected/bill"]),
  },
  {
    name: "createBatchReceipts",
    path: "/api/receipts/batch",
    method: "POST",
    call: () => api.createBatchReceipts(batchDraft),
    body: batchDraft,
  },
  {
    name: "getBillingChecks",
    path: "/api/billing-checks?page=1&size=20&month=2026-11",
    method: "GET",
    call: () => api.getBillingChecks({ month: "2026-11", page: 1, size: 20 }),
  },
  {
    name: "getReceivables",
    path: "/api/receivables?page=1&size=20",
    method: "GET",
    call: () => api.getReceivables({ page: 1, size: 20 }),
  },
];
const financialCases: typeof cases = [
  {
    name: "getFinancialItems default",
    path: "/api/financial-items",
    method: "GET",
    call: () => api.getFinancialItems(),
  },
  {
    name: "getFinancialItems",
    path: "/api/financial-items?month=2026-11",
    method: "GET",
    call: () => api.getFinancialItems("2026-11"),
  },
  {
    name: "saveFinancialItems",
    path: "/api/financial-items",
    method: "PUT",
    call: () =>
      api.saveFinancialItems({
        month: "2026-11",
        expectedRevision: "financial-revision",
        items: { deleteIds: ["12"] },
      }),
    body: {
      month: "2026-11",
      expectedRevision: "financial-revision",
      items: { deleteIds: ["12"] },
    },
  },
  {
    name: "getFinancialYearStatus",
    path: "/api/financial-items/year-status?year=2026",
    method: "GET",
    call: () => api.getFinancialYearStatus("2026"),
  },
  {
    name: "createFinancialPreview",
    path: "/api/financial-items/preview",
    method: "POST",
    call: () =>
      api.createFinancialPreview({
        month: "2026-11",
        expectedRevision: "financial-revision",
        action: "LOAD_PREVIOUS",
      }),
    body: {
      month: "2026-11",
      expectedRevision: "financial-revision",
      action: "LOAD_PREVIOUS",
    },
  },
  {
    name: "createCorporateTaxDefaults",
    path: "/api/corporate-taxes/initialize",
    method: "POST",
    call: () => api.createCorporateTaxDefaults("2026-11"),
    body: { month: "2026-11" },
  },
  {
    name: "getCorporateTaxAllocation",
    path: "/api/corporate-taxes/2026/allocation",
    method: "GET",
    call: () => api.getCorporateTaxAllocation("2026"),
  },
  {
    name: "saveCorporateTaxPaymentConfirmation",
    path: "/api/corporate-taxes/2026/payments/MID/confirmation",
    method: "POST",
    call: () =>
      api.saveCorporateTaxPaymentConfirmation("2026", "MID", {
        month: "2026-11",
        expectedRevision: "payment-revision",
        paidDate: "2026-11-10",
        payAmount: "0",
      }),
    body: {
      month: "2026-11",
      expectedRevision: "payment-revision",
      paidDate: "2026-11-10",
      payAmount: "0",
    },
  },
  {
    name: "getFinancialContributions",
    path: "/api/financial-contributions?month=2026-11",
    method: "GET",
    call: () => api.getFinancialContributions("2026-11"),
  },
  {
    name: "getFinancialHistory",
    path: "/api/financial-history?month=2026-11&scope=TAX&page=2&size=20",
    method: "GET",
    call: () => api.getFinancialHistory("2026-11", "TAX", 2, 20),
  },
  {
    name: "getFinancialUploadResult",
    path: "/api/uploads/999999999999/result",
    method: "GET",
    call: () => api.getFinancialUploadResult("999999999999"),
  },
  {
    name: "getUploadIssues financial ID",
    path: "/api/uploads/999999999999/issues?page=2&size=20&severity=ERR",
    method: "GET",
    call: () => api.getUploadIssues("999999999999", "ERR", 2, 20),
  },
];
cases.push(
  ...projectCases,
  ...assignmentCases,
  ...billingCases,
  ...financialCases,
);
afterEach(() => vi.unstubAllGlobals());
describe("승인 API 등록", () => {
  it.each(["validate", "apply"] as const)(
    "B2 %s는 문자열 ID와 재무 revision을 보존한다",
    async (operation) => {
      const fetcher = vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: true,
            meta: {},
            data: { uploadId: "999999999999" },
          }),
        ),
      );
      vi.stubGlobal("fetch", fetcher);
      const file = new File(["workbook"], "financial.xlsx");
      const result =
        operation === "validate"
          ? await api.createB2Validation(file, "2026-11")
          : await api.saveB2Apply(
              file,
              "2026-11",
              "999999999999",
              "financial-revision",
            );
      expect(result.uploadId).toBe("999999999999");
      expect(fetcher).toHaveBeenCalledOnce();
      const [path, options] = fetcher.mock.calls[0] as [string, RequestInit];
      expect(path).toBe(`/api/excel/B2/${operation}`);
      expect(options.method).toBe("POST");
      expect(new Headers(options.headers).has("Content-Type")).toBe(false);
      const body = options.body as FormData;
      expect(body.get("file")).toBe(file);
      expect(body.get("month")).toBe("2026-11");
      expect(body.get("uploadId")).toBe(
        operation === "apply" ? "999999999999" : null,
      );
      expect(body.get("expectedRevision")).toBe(
        operation === "apply" ? "financial-revision" : null,
      );
      expect(body.get("userId")).toBeNull();
      expect(body.get("year")).toBeNull();
    },
  );
  it.each(["validate", "apply"] as const)(
    "B2 %s의 422 재무 진단을 평면 오류 그대로 보존한다",
    async (operation) => {
      const body = {
        success: false,
        meta: {},
        code: "REVALIDATION_FAILED",
        message: "재검사 오류",
        requestId: "financial-upload-request",
        details: {
          financial: { upload: { uploadId: "999999999999", canApply: false } },
        },
      };
      const fetcher = vi
        .fn()
        .mockResolvedValue(new Response(JSON.stringify(body), { status: 422 }));
      vi.stubGlobal("fetch", fetcher);
      const file = new File(["workbook"], "financial.xlsx");
      await expect(
        operation === "validate"
          ? api.createB2Validation(file, "2026-11")
          : api.saveB2Apply(
              file,
              "2026-11",
              "999999999999",
              "financial-revision",
            ),
      ).rejects.toMatchObject({ status: 422, body });
      expect(fetcher).toHaveBeenCalledOnce();
    },
  );
  it.each(["B1", "B5"] as const)(
    "%s 등록은 기존 multipart 문맥을 유지한다",
    async (form) => {
      const fetcher = vi.fn().mockImplementation(
        async () =>
          new Response(
            JSON.stringify({
              success: true,
              meta: {},
              data: { uploadId: 123 },
            }),
          ),
      );
      vi.stubGlobal("fetch", fetcher);
      const file = new File(["workbook"], "upload.xlsx");
      if (form === "B1") {
        await api.createB1Validation(file, "2026-11", 123);
        await api.createB1Apply(file, {
          month: "2026-11",
          uploadId: 123,
          expectedRevision: "revision",
          confirmFullReplacement: true,
        });
      } else {
        await api.createB5Validation(file, 2026, [], 123);
        await api.createB5Apply(file, 2026, [], 123, "preview");
      }
      for (const [index, operation] of ["validate", "apply"].entries()) {
        const [path, options] = fetcher.mock.calls[index] as [
          string,
          RequestInit,
        ];
        expect(path).toBe(`/api/excel/${form}/${operation}`);
        expect(options.method).toBe("POST");
        expect(new Headers(options.headers).has("Content-Type")).toBe(false);
        const body = options.body as FormData;
        expect(body.get("file")).toBe(file);
        expect(body.get("uploadId")).toBe("123");
        if (form === "B1") {
          expect(body.get("month")).toBe("2026-11");
          expect(body.get("confirmFullReplacement")).toBe(
            index ? "true" : null,
          );
          expect(body.get("expectedRevision")).toBe(index ? "revision" : null);
        } else {
          expect(body.get("year")).toBe("2026");
          expect(body.get("withdrawalSelections")).toBeInstanceOf(Blob);
          expect(body.get("previewToken")).toBe(index ? "preview" : null);
        }
        expect(body.get("projectId")).toBeNull();
      }
    },
  );
  it.each([
    () =>
      api.createB1Validation(new File(["workbook"], "upload.xlsx"), "2026-11"),
    () =>
      api.createB1Apply(new File(["workbook"], "upload.xlsx"), {
        month: "2026-11",
        uploadId: 123,
        expectedRevision: "revision",
        confirmFullReplacement: true,
      }),
    () =>
      api.createB5Validation(new File(["workbook"], "upload.xlsx"), 2026, []),
    () =>
      api.createB5Apply(
        new File(["workbook"], "upload.xlsx"),
        2026,
        [],
        123,
        "preview",
      ),
  ])("새 업로드 등록은 422 진단과 문의번호를 유지한다", async (call) => {
    const body = {
      success: false,
      meta: {},
      code: "REVALIDATION_FAILED",
      message: "재검사 오류",
      requestId: "upload-request",
      details: { issueCount: 1 },
    };
    const fetcher = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(body), { status: 422 }));
    vi.stubGlobal("fetch", fetcher);
    await expect(call()).rejects.toMatchObject({ status: 422, body });
    expect(fetcher).toHaveBeenCalledOnce();
  });
  it.each([
    ...projectCases,
    ...assignmentCases,
    ...billingCases,
    ...financialCases,
  ])("$name은 평면 오류를 보존하고 재시도하지 않는다", async (test) => {
    const body = {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다시 조회해 주세요.",
      requestId: "project-request",
    };
    const fetcher = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(body), { status: 409 }));
    vi.stubGlobal("fetch", fetcher);
    await expect(test.call()).rejects.toMatchObject({ status: 409, body });
    expect(fetcher).toHaveBeenCalledOnce();
  });
  it.each(["B9", "B6"] as const)(
    "%s multipart 등록은 year와 업로드 문맥을 유지한다",
    async (form) => {
      const fetcher = vi.fn().mockImplementation(
        async () =>
          new Response(
            JSON.stringify({
              success: true,
              meta: {},
              data: { uploadId: 123 },
            }),
          ),
      );
      vi.stubGlobal("fetch", fetcher);
      const file = new File(["workbook"], "projects.xlsx");
      if (form === "B9") {
        await api.createB9Validation(file, 2026);
        await api.saveB9Apply(file, 123, 2026);
      } else {
        await api.createB6Validation(file, {
          year: 2026,
          projectId: "P/selected",
          step: "PLAN",
        });
        await api.saveB6Apply(
          file,
          123,
          { year: 2026, projectId: "P/selected", step: "PLAN" },
          "revision",
        );
      }
      for (const [index, operation] of ["validate", "apply"].entries()) {
        const [path, options] = fetcher.mock.calls[index] as [
          string,
          RequestInit,
        ];
        expect(path).toBe(`/api/excel/${form}/${operation}`);
        expect(options.method).toBe("POST");
        expect(new Headers(options.headers).has("Content-Type")).toBe(false);
        const body = options.body as FormData;
        expect(body.get("file")).toBe(file);
        expect(body.get("year")).toBe("2026");
        expect(body.get("uploadId")).toBe(index ? "123" : null);
        expect(body.get("projectId")).toBe(form === "B6" ? "P/selected" : null);
        expect(body.get("step")).toBe(form === "B6" ? "PLAN" : null);
        expect(body.get("expectedRevision")).toBe(
          form === "B6" && index ? "revision" : null,
        );
      }
    },
  );
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
      path: "/api/excel/B2/template?month=2026-11",
      call: () => api.getB2Template("2026-11"),
      filename: "B2_재무양식_20261110_120000.xlsx",
    },
    {
      path: "/api/uploads/999999999999/issues.xlsx?severity=WARN",
      call: () => api.getUploadIssuesXlsx("999999999999", "WARN"),
      filename: "B2_검사_999999999999_20261110_120000.xlsx",
    },
    {
      path: "/api/excel/B1/template",
      call: () => api.getPayrollTemplate(),
      filename: "payroll.xlsx",
    },
    {
      path: "/api/exports/master-data",
      call: () =>
        api.downloadBillingReview({
          view: "BILLING_CHECKS",
          month: "2026-11",
          amountUnit: "천원",
        }),
      filename: "청구누락점검_20261111_120000.xlsx",
    },
    {
      path: "/api/exports/master-data",
      call: () =>
        api.downloadBillingReview({ view: "RECEIVABLES", amountUnit: "원" }),
      filename: "미수알림_20261111_120000.xlsx",
    },
    {
      path: "/api/excel/B5/template",
      call: () => api.getB5Template(),
      filename: "assignments.xlsx",
    },
    {
      path: "/api/exports/master-data",
      call: () =>
        api.downloadAssignments(
          { year: 2026 },
          { id: "projectName", direction: "ASC" },
          "천원",
        ),
      filename: "assignments.xlsx",
    },
    {
      path: "/api/exports/master-data",
      call: () =>
        api.exportPayroll(
          { month: "2026-11", page: 1, size: 50 },
          ["payAmount"],
          "천원",
        ),
      filename: "payroll.xlsx",
    },
    {
      path: "/api/excel/B9/template",
      call: () => api.getB9Template(),
      filename: "projects.xlsx",
    },
    {
      path: "/api/excel/B6/template?projectId=P%2Fselected",
      call: () => api.getB6Template("P/selected"),
      filename: "distribution.xlsx",
    },
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
    () => api.getB2Template("2026-11"),
    () => api.getUploadIssuesXlsx("999999999999", "WARN"),
    () => api.getB7Template(),
    () =>
      api.downloadBillingReview({ view: "RECEIVABLES", amountUnit: "천원" }),
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
