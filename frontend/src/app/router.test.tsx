import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApplicationRoutes, applicationRoutes } from "./router";
import { installBusinessSession } from "../shared/testSupport/businessFixtures";
import { screenRegistry } from "../pages/shared/screenRegistry";
import * as shared from "../pages/shared/sharedApi";
import * as roles from "../pages/roles/rolesApi";
import * as managedMenus from "../pages/menumanagement/menuManagementApi";
import * as permissionSettings from "../pages/currentuserops/permissionSettingsApi";
import * as prefs from "../pages/me/preferencesApi";
import * as codes from "../pages/codegroups/codeGroupsApi";
import * as mappings from "../pages/codemappings/codeMappingsApi";
import * as standards from "../pages/operatingstandards/operatingStandardsApi";
import * as clients from "../pages/clients/clientsApi";
import * as users from "../pages/businessusers/businessUsersApi";
import * as parts from "../pages/salesparts/salesPartsApi";
import * as partStandards from "../pages/salespartsops/partStandardsApi";
import * as b7 from "../pages/b7/b7Api";
import * as exports from "../pages/exportsops/masterDataExportApi";
import * as actions from "../pages/clientsops/clientActionsApi";
import * as developers from "../pages/developers/peopleClient";
import * as candidates from "../pages/candidates/peopleClient";
import * as summary from "../pages/candidatesops/candidateSummaryApi";
import * as areas from "../shared/areaClient";
import * as projects from "../pages/projects/projectsApi";
import * as projectParts from "../pages/parts/projectPartsApi";
import * as distribution from "../pages/distribution/distributionApi";
import * as assignments from "../pages/assignmentsops/assignmentActionsApi";
import * as bills from "../pages/bills/billsApi";
import * as financial from "../pages/financialitems/financialApi";
import * as cashFlow from "../pages/cashflowops/cashFlowApi";
import * as monthClose from "../pages/monthclosesops/monthCloseApi";
import * as corporateTax from "../pages/corporatetaxesops/corporateTaxApi";
import * as analysis from "../pages/apiops/projectAnalysisApi";
import * as costDetail from "../pages/costanalysis/projectCostApi";
import * as progress from "../pages/progress/projectProgressApi";
import * as issues from "../pages/issues/projectIssuesApi";
import * as settlements from "../pages/salespartsettlements/salesPartSettlementsApi";
import * as renegotiations from "../pages/apiops/rateRenegotiationsApi";
import * as recommendations from "../pages/workforcerecommendationsops/recommendationBrowseApi";
import * as recommendationActions from "../pages/candidates/recommendationActionsApi";
import * as withdrawals from "../pages/apiops/withdrawalsApi";
import { financialEditorFixture } from "../pages/financialitems/financialFixtures";
import { clientFixture, pageFixture } from "../pages/clients/clientsFixtures";
import { dashboardFixture } from "../pages/dashboardops/dashboardFixture";
import * as dashboard from "../pages/dashboardops/dashboardApi";
vi.mock("../pages/dashboardops/dashboardApi");

vi.mock("../pages/financialitems/financialApi", async (original) => ({
  ...(await original<typeof import("../pages/financialitems/financialApi")>()),
  getFinancialItems: vi.fn(),
  saveFinancialItems: vi.fn(),
  getFinancialYearStatus: vi.fn(),
}));
vi.mock("../pages/corporatetaxesops/corporateTaxApi", async (original) => ({
  ...(await original<
    typeof import("../pages/corporatetaxesops/corporateTaxApi")
  >()),
  createCorporateTaxDefaults: vi.fn(),
}));
vi.mock("../pages/clients/clientsApi");
vi.mock("../pages/cashflowops/cashFlowApi", async (original) => ({
  ...(await original<typeof import("../pages/cashflowops/cashFlowApi")>()),
  getCashFlow: vi.fn(),
}));
vi.mock("../pages/monthclosesops/monthCloseApi", async (original) => ({
  ...(await original<typeof import("../pages/monthclosesops/monthCloseApi")>()),
  getMonthCloses: vi.fn(),
}));
vi.mock("../pages/workforcerecommendationsops/recommendationBrowseApi");
vi.mock("../pages/candidates/recommendationActionsApi", async (original) => ({
  ...(await original<
    typeof import("../pages/candidates/recommendationActionsApi")
  >()),
  createWorkforceRecommendationExclusion: vi.fn(),
  createWorkforceRecommendationTentativeAssignment: vi.fn(),
}));
vi.mock("../pages/apiops/withdrawalsApi");
vi.mock("../pages/salespartsettlements/salesPartSettlementsApi");
vi.mock("../pages/apiops/rateRenegotiationsApi", async (original) => ({
  ...(await original<typeof import("../pages/apiops/rateRenegotiationsApi")>()),
  getRateRenegotiations: vi.fn(),
  downloadRenegotiations: vi.fn(),
}));
vi.mock("../pages/apiops/projectAnalysisApi", async (original) => ({
  ...(await original<typeof import("../pages/apiops/projectAnalysisApi")>()),
  getProjectCosts: vi.fn(),
  getProjectStatusBoard: vi.fn(),
}));
vi.mock("../pages/costanalysis/projectCostApi");
vi.mock("../pages/progress/projectProgressApi");
vi.mock("../pages/issues/projectIssuesApi");
vi.mock("../pages/bills/billsApi");
vi.mock("../pages/assignmentsops/assignmentActionsApi");
vi.mock("../pages/projects/projectsApi");
vi.mock("../pages/parts/projectPartsApi");
vi.mock("../pages/distribution/distributionApi");
vi.mock("../pages/developers/peopleClient");
vi.mock("../pages/candidates/peopleClient");
vi.mock("../pages/candidatesops/candidateSummaryApi");
vi.mock("../shared/areaClient");
vi.mock("../pages/businessusers/businessUsersApi");
vi.mock("../pages/salesparts/salesPartsApi");
vi.mock("../pages/salespartsops/partStandardsApi");
vi.mock("../pages/b7/b7Api");
vi.mock("../pages/clientsops/clientActionsApi");
vi.mock("../pages/exportsops/masterDataExportApi", async (original) => ({
  ...(await original<
    typeof import("../pages/exportsops/masterDataExportApi")
  >()),
  createMasterDataExport: vi.fn(),
  saveMasterDataDownload: vi.fn(),
}));

vi.mock("../pages/shared/sharedApi");
vi.mock("../pages/roles/rolesApi", async (original) => ({
  ...(await original<typeof import("../pages/roles/rolesApi")>()),
  getRoles: vi.fn(),
  getRoleMenus: vi.fn(),
}));
vi.mock("../pages/menumanagement/menuManagementApi", async (original) => ({
  ...(await original<
    typeof import("../pages/menumanagement/menuManagementApi")
  >()),
  getMenuManagement: vi.fn(),
}));
vi.mock("../pages/currentuserops/permissionSettingsApi");
vi.mock("../pages/me/preferencesApi", async (original) => ({
  ...(await original<typeof import("../pages/me/preferencesApi")>()),
  getPreferences: vi.fn(),
  putPreference: vi.fn(),
  deletePreference: vi.fn(),
  preferencesApi: {
    getPreferences: (...args: []) => prefs.getPreferences(...args),
    putPreference: (...args: Parameters<typeof prefs.putPreference>) =>
      prefs.putPreference(...args),
    deletePreference: (...args: Parameters<typeof prefs.deletePreference>) =>
      prefs.deletePreference(...args),
  },
}));
vi.mock("../pages/codegroups/codeGroupsApi", async (original) => ({
  ...(await original<typeof import("../pages/codegroups/codeGroupsApi")>()),
  getCodeGroups: vi.fn(),
  getGroupCodes: vi.fn(),
  saveGroupCodes: vi.fn(),
}));
vi.mock("../pages/codemappings/codeMappingsApi", async (original) => ({
  ...(await original<typeof import("../pages/codemappings/codeMappingsApi")>()),
  getCodeMappings: vi.fn(),
  getMappingGroups: vi.fn(),
}));
vi.mock(
  "../pages/operatingstandards/operatingStandardsApi",
  async (original) => ({
    ...(await original<
      typeof import("../pages/operatingstandards/operatingStandardsApi")
    >()),
    getOperatingStandards: vi.fn(),
    getStandardsPeriodContext: vi.fn(),
    getStandardsMonthClose: vi.fn(),
  }),
);

const context = {
  currentDate: "2026-11-10",
  currentMonth: "2026-11",
  timezone: "Asia/Seoul",
  cashBaseMonth: "2026-01",
  monthClose: { month: "2026-11", status: "작업중" },
};
const project: projects.Project = {
  id: "P-0042",
  name: "연결 검증 프로젝트",
  customerId: null,
  contractType: "TK",
  status: "PRE",
  startDate: "2026-01-01",
  endDate: "2027-12-31",
  workplace: null,
  feature: null,
  estimateAmount: null,
  originalContractAmount: "100",
  contractAmount: "100",
  budgetAmount: "80",
  teamExpenseAmount: "0",
  projectExpenseAmount: "0",
  budgetManual: "N",
  lossDate: null,
  lossReason: null,
  lossMemo: null,
  opportunityId: null,
};
const projectDetail: projects.ProjectDetail = {
  project,
  revision: "project-revision",
  effectiveStatus: "PRE",
  readOnly: false,
  parts: [],
  pm: [],
  plans: [],
  assignments: [],
  history: [],
  actualMmTotal: "0.00",
  directExpenseTotal: "0",
};
const menus: shared.MenuNode[] = [
  {
    menuId: "SYSTEM",
    parentId: null,
    name: "시스템",
    screenId: null,
    sortOrder: 1,
    icon: null,
    pcOnly: false,
    children: Object.keys(screenRegistry).map((id, index) => ({
      menuId: `M${index}`,
      parentId: "SYSTEM",
      name: `DB ${id}`,
      screenId: id,
      sortOrder: index,
      icon: null,
      pcOnly: ["UI-10", "UI-14", "UI-23", "UI-24", "UI-25", "UI-27"].includes(
        id,
      ),
      children: [],
    })),
  },
];
let preferences: prefs.Preference[];
const recommendationTarget: recommendations.RecommendationTarget = {
  targetType: "OPPORTUNITY",
  targetId: "O-0042",
  name: "연결 검증 기회",
  contractTypeCode: "MO",
  projectIdAfterWin: null,
  contractStartDate: null,
  contractEndDate: null,
  needs: ["7", "19"].map((needSn) => ({
    needSn,
    jobCode: "BE",
    gradeCode: "SR",
    needCount: 2,
    startMonth: "2026-11",
    periodMonths: 3,
    monthlyMm: "1.00",
    activeCount: 0,
    remainingCount: 2,
    revision: `need-${needSn}`,
  })),
  defaults: {
    jobCode: "BE",
    startMonth: "2026-11",
    requestedGradeCode: "SR",
    needSn: "7",
  },
  revision: "target-original",
};
const recommendationResult: recommendations.RecommendationSnapshot = {
  rcmdId: "42",
  recommendationType: "AI",
  sourceAssignmentId: null,
  executedAt: "2026-11-10 09:00:00",
  executedBy: "admin",
  appliedCondition: {
    targetType: "OPPORTUNITY",
    targetId: recommendationTarget.targetId,
    jobCode: "BE",
    startMonth: "2026-11",
    requestedGradeCode: "SR",
    needSn: "7",
  },
  candidates: [
    {
      rank: 1,
      developer: {
        developerId: "D-0042",
        detailId: "D-0042",
        name: "연결 검증 인력",
        managementNumber: "00042",
        jobCode: "BE",
        subJobCode: null,
        gradeCode: "SR",
        employeeTypeCode: "FT",
      },
      opportunityId: recommendationTarget.targetId,
      needSn: "7",
      jobCode: "BE",
      startMonth: "2026-11",
      requestedGradeCode: "SR",
      totalScore: "87.5",
      scoreItems: [
        {
          itemCode: "JOB",
          baseScore: "100",
          weight: "20",
          score: "20",
          basis: "직무 일치",
        },
      ],
      scoreDetail: { v: 1 },
      reason: "직무가 일치합니다.",
      otherTentativeAssignments: [],
      nextPosition: null,
      revision: "choice-original",
      targetRevision: "target-original",
      candidateRevision: "candidate-original",
      selection: {
        actionCode: null,
        exclusionReasonCode: null,
        actedBy: null,
        actedAt: null,
        assignmentId: null,
        tentativeAssignmentId: null,
        finalOpportunityId: null,
        finalNeedSn: null,
      },
    },
  ],
  message: "추천했습니다.",
  revision: "round-original",
};
const group: codes.CodeGroup = {
  groupId: "EDUCATION",
  name: "교육",
  sysCodeYn: "N",
  valCodeYn: "N",
  useYn: "Y",
  sortOrder: 1,
  codeCount: 1,
};
const row: codes.Code = {
  code: "A",
  name: "기존 이름",
  sortOrder: 1,
  useYn: "Y",
  addVal1: null,
  addVal2: null,
  addVal3: null,
  codeVal: null,
  unitNm: null,
};
const columnViews = {
  codes: {
    order: ["code", "name", "addVal1", "addVal2", "sortOrder", "useYn"],
    hidden: [],
  },
  valueCodes: { order: ["code", "name", "codeVal", "unitNm"], hidden: [] },
  mappings: { order: ["sourceText", "groupId", "code"], hidden: [] },
  standards: {
    order: ["key", "name", "value", "unitNm", "effectiveMonth", "description"],
    hidden: [],
  },
  history: {
    order: [
      "occurredAt",
      "target",
      "groupId",
      "entityKey",
      "type",
      "item",
      "before",
      "after",
      "actor",
    ],
    hidden: [],
  },
};
const costColumns = [
  "projectName",
  "contractType",
  "status",
  "originalContractAmount",
  "contractAmount",
  "planMm",
  "actualMm",
  "cumulativeRevenue",
  "cumulativeCost",
  "cumulativeProfit",
  "cumulativeMargin",
  "forecastMargin",
  "targetGap",
  "budgetUsage",
  "pricePerMm",
  "costPerMm",
  "profitPerMm",
  "freeMm",
  "freeLaborCost",
  "alerts",
];
const boardColumns = [
  "light",
  "projectName",
  "pm",
  "currentStep",
  "planProgress",
  "actualProgress",
  "costUsage",
  "costProgressGap",
  "openIssueCount",
  "overdueIssueCount",
  "lastUpdatedDate",
  "elapsedDays",
];
const issueColumns = [
  "importance",
  "title",
  "projectName",
  "action",
  "registeredBy",
  "dueDate",
  "status",
  "registeredAt",
  "modifiedBy",
  "modifiedAt",
];
const open = (path = "/UI-27") =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <ApplicationRoutes />
    </MemoryRouter>,
  );
beforeEach(() => {
  vi.resetAllMocks();
  installBusinessSession();
  vi.mocked(roles.getRoles).mockResolvedValue({
    items: [],
    developmentMode: true,
    accessEnforcement: false,
    settingsRevision: "settings-r",
  });
  vi.mocked(roles.getRoleMenus).mockResolvedValue({ items: [] });
  vi.mocked(managedMenus.getMenuManagement).mockResolvedValue({
    items: [],
    revision: "menus-r",
    developmentMode: true,
    accessEnforcement: false,
  });
  vi.mocked(
    permissionSettings.getCurrentUserPermissionSettings,
  ).mockResolvedValue({
    userId: "admin",
    roleIds: ["ADMIN"],
    menuPermissions: [],
    sensitiveView: false,
    rowScope: { all: true, partIds: [], projectIds: [] },
    assignedParts: [],
    assignedProjects: [],
    developmentMode: true,
    accessEnforcement: false,
    settingsRevision: "settings-r",
  });
  vi.mocked(dashboard.getDashboard).mockImplementation(async (month) => ({
    ...dashboardFixture(),
    selectedMonth: month ?? context.currentMonth,
  }));
  vi.mocked(cashFlow.getCashFlow).mockRejectedValue(
    new Error("자금수지 원천 조회 실패"),
  );
  vi.mocked(monthClose.getMonthCloses).mockRejectedValue(
    new Error("월 마감 원천 조회 실패"),
  );
  vi.mocked(
    recommendations.getWorkforceRecommendationTargets,
  ).mockResolvedValue({
    items: [recommendationTarget],
    firstTargetId: recommendationTarget.targetId,
    serverDate: context.currentDate,
    currentMonth: context.currentMonth,
    baseMonth: context.cashBaseMonth,
    revision: "targets-original",
  });
  vi.mocked(recommendations.requestTargetRecommendation).mockResolvedValue(
    recommendationResult,
  );
  vi.mocked(recommendations.getRecommendationHistorySnapshot).mockResolvedValue(
    recommendationResult,
  );
  vi.mocked(
    recommendations.getWorkforceRecommendationHistory,
  ).mockResolvedValue({ items: [], totalCount: 0, page: 1, size: 50 });
  vi.mocked(
    recommendationActions.createWorkforceRecommendationTentativeAssignment,
  ).mockResolvedValue({
    rcmdId: "42",
    rank: 1,
    changed: true,
    revision: "selected",
    message: "가배정했습니다.",
    releasedTentativeAssignmentId: null,
    selection: {
      ...recommendationResult.candidates[0].selection,
      actionCode: "TENT",
      tentativeAssignmentId: "52",
      finalOpportunityId: recommendationTarget.targetId,
      finalNeedSn: "19",
    },
  });
  vi.mocked(withdrawals.getWithdrawals).mockImplementation(async (query) => ({
    items: [],
    year: query.year ?? 2026,
    page: query.page,
    size: query.size,
    totalCount: 0,
    serverDate: context.currentDate,
    baseMonth: context.cashBaseMonth,
    guidance: {
      visible: false,
      withinThreeMonthsPeople: 0,
      withoutNextAssignmentPeople: 0,
      withMatchingPositionsPeople: 0,
      message: "",
    },
  }));
  vi.mocked(settlements.getSalesPartSettlements).mockResolvedValue({
    month: context.currentMonth,
    baseMonth: context.cashBaseMonth,
    currentMonth: context.currentMonth,
    parts: [],
    eligibleCount: 0,
    confirmedCount: 0,
    unconfirmedCount: 0,
    issues: [],
  });
  vi.mocked(renegotiations.getRateRenegotiations).mockResolvedValue({
    baseMonth: context.currentMonth,
    calculatedAt: "2026-11-10T09:00:00+09:00",
    appliedIncreaseRate: "5",
    targetMarginRate: null,
    summary: {
      personCount: 0,
      assignmentCount: 0,
      weightedMarginRate: null,
      annualGain: "0",
      targetAnnualGain: null,
    },
    items: [],
    pagination: { page: 1, size: 50, total: 0 },
    unevaluatedCount: 0,
    unevaluatedAssignments: [],
    issues: [],
  });
  const completeness: analysis.Completeness = {
    costComplete: true,
    revenueComplete: true,
    estimated: false,
    issues: [],
    unknownProjectCount: 0,
    rangeStartMonth: "2026-01",
    rangeEndMonth: "2026-11",
    missingBeforeMonth: null,
  };
  vi.mocked(analysis.getProjectCosts).mockResolvedValue({
    month: context.currentMonth,
    baseDate: "2026-11-30",
    items: [],
    total: 0,
    page: 1,
    size: 50,
    summary: { completeness },
  });
  vi.mocked(analysis.getProjectStatusBoard).mockResolvedValue({
    baseDate: context.currentDate,
    currentMonth: context.currentMonth,
    items: [],
    total: 0,
    page: 1,
    size: 50,
    summary: { projectCount: 0, openIssueCount: 0 },
    staleProjects: [],
    calculationNotice: "일정·원가 신호는 1차 경보입니다.",
  });
  vi.mocked(costDetail.getProjectCostAnalysis).mockRejectedValue(
    new Error("상세 원가 조회 실패"),
  );
  vi.mocked(progress.getProjectProgress).mockRejectedValue(
    new Error("진행 조회 실패"),
  );
  vi.mocked(issues.getProjectIssues).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
    openCount: 0,
    baseDate: context.currentDate,
  });
  vi.mocked(issues.getProjectStatusIssues).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
    openCount: 0,
    baseDate: context.currentDate,
  });
  vi.mocked(financial.getFinancialItems).mockImplementation(async (month) =>
    financialEditorFixture({
      month: month ?? context.currentMonth,
      today: context.currentDate,
      cashBaseMonth: context.cashBaseMonth,
    }),
  );
  vi.mocked(corporateTax.createCorporateTaxDefaults).mockResolvedValue({
    changed: false,
    corporateTax: {
      year: "2026",
      estimatedTax: "0",
      audit: {
        regUserId: "admin",
        regDttm: "2026-11-10 09:00:00",
        modUserId: null,
        modDttm: null,
      },
    },
    taxPayments: [],
    revision: "revision-current",
  });
  vi.mocked(financial.getFinancialYearStatus).mockImplementation(
    async (year) => ({
      year,
      months: Array.from({ length: 12 }, (_, index) => {
        const month = `${year}-${String(index + 1).padStart(2, "0")}`;
        return {
          month,
          closeStatus: "WORK",
          itemCount: month <= context.currentMonth ? 0 : null,
          selectable: month <= context.currentMonth,
          editable: month <= context.currentMonth,
        };
      }),
    }),
  );
  vi.mocked(financial.saveFinancialItems).mockResolvedValue({
    changed: true,
    revision: "revision-saved",
    message: "저장했습니다.",
    editor: financialEditorFixture({
      month: context.currentMonth,
      today: context.currentDate,
    }),
    createdCards: [],
  });
  vi.mocked(bills.getBills).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
    totals: {
      supplyAmount: "0",
      vatAmount: "0",
      totalAmount: "0",
      receiptAmount: "0",
      unpaidAmount: "0",
    },
  });
  vi.mocked(bills.getSummary).mockResolvedValue({
    year: 2026,
    includeCarryover: true,
    billed: { amount: "0", count: 0, scope: "선택연도" },
    currentMonth: { amount: "0", count: 0, scope: "이번달" },
    unpaid: { amount: "0", count: 0, scope: "이월 포함" },
    lateCount: 0,
    lateScope: "이월 포함",
  });
  vi.mocked(bills.getProjectOptions).mockResolvedValue({ items: [] });
  vi.mocked(assignments.getAssignments).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(assignments.projectOptions).mockResolvedValue({ items: [] });
  vi.mocked(assignments.developerOptions).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  });
  vi.mocked(developers.getDevelopers).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(developers.getPeopleCodes).mockResolvedValue({ items: [] });
  vi.mocked(candidates.getCandidates).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(candidates.getCandidateCodes).mockResolvedValue({ items: [] });
  vi.mocked(candidates.getCandidateDate).mockResolvedValue({
    currentDate: context.currentDate,
  });
  vi.mocked(summary.getCandidateSummary).mockResolvedValue({
    total: 0,
    inProgress: 0,
    interviewScheduled: 0,
    availableByNextMonthStart: 0,
    overdueContact: 0,
  });
  vi.mocked(areas.getAreaSidos).mockResolvedValue({ items: [] });
  vi.mocked(actions.getClientOptions).mockResolvedValue({ items: [] });
  vi.mocked(partStandards.getSalesPartOptions).mockResolvedValue({ items: [] });
  preferences = Object.entries({
    FAV: [],
    RECENT: [],
    MENU_FOLD: false,
    PAGE_SIZE: 50,
    DENSE: "넓게",
    AMT_UNIT: "천원",
  }).map(([type, value]) => ({
    type: type as prefs.PreferenceType,
    key: "*",
    value,
    revision: null,
    stored: false,
  }));
  preferences.push({
    type: "COLS",
    key: "UI-27",
    value: columnViews,
    revision: null,
    stored: false,
  });
  preferences.push(
    {
      type: "COLS",
      key: "UI-04",
      value: { projectCost: { order: costColumns, hidden: [] } },
      revision: null,
      stored: false,
    },
    {
      type: "COLS",
      key: "UI-31",
      value: {
        statusBoard: { order: boardColumns, hidden: [] },
        issues: { order: issueColumns, hidden: [] },
      },
      revision: null,
      stored: false,
    },
  );
  vi.mocked(prefs.getPreferences).mockImplementation(async () => ({
    items: preferences,
  }));
  vi.mocked(prefs.putPreference).mockImplementation(
    async (type, key, value) => {
      const preference = { type, key, value, revision: "saved", stored: true };
      preferences = preferences.map((item) =>
        item.type === type && item.key === key ? preference : item,
      );
      return { changed: true, preference };
    },
  );
  vi.mocked(shared.getSharedPreferences).mockImplementation(async () => ({
    items: preferences,
  }));
  vi.mocked(shared.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, revision: "saved", stored: true },
    }),
  );
  vi.mocked(shared.getMenus).mockResolvedValue({
    items: menus,
    diagnostics: [],
  });
  vi.mocked(shared.getCurrentUser).mockResolvedValue({
    userId: "admin",
    name: "임시 관리자",
    roleIds: ["ADMIN"],
    developmentMode: true,
  });
  vi.mocked(shared.getPeriodContext).mockResolvedValue(context);
  vi.mocked(shared.getSharedCodes).mockResolvedValue({ items: [] });
  vi.mocked(projects.getProjects).mockImplementation(async (filters) => ({
    items: [],
    total: 0,
    unassignedCount: 0,
    page: filters.page,
    size: filters.size,
    year: filters.year ?? 2026,
  }));
  vi.mocked(projects.getProject).mockResolvedValue(projectDetail);
  vi.mocked(shared.getMonthClose).mockResolvedValue({
    items: [context.monthClose],
  });
  vi.mocked(shared.getCommonCodeHistory).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(codes.getCodeGroups).mockResolvedValue({ items: [group] });
  vi.mocked(codes.getGroupCodes).mockResolvedValue({
    items: [row],
    total: 1,
    page: 1,
    size: 50,
    groupRevision: "original",
  });
  vi.mocked(mappings.getMappingGroups).mockResolvedValue({ items: [group] });
  vi.mocked(mappings.getCodeMappings).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(standards.getOperatingStandards).mockResolvedValue({ items: [] });
  vi.mocked(standards.getStandardsPeriodContext).mockResolvedValue(context);
  vi.mocked(standards.getStandardsMonthClose).mockResolvedValue({ items: [] });
  vi.mocked(clients.getClients).mockResolvedValue(pageFixture);
  vi.mocked(clients.getClient).mockResolvedValue(clientFixture);
  vi.mocked(clients.getClientCodes).mockResolvedValue({ items: [] });
  vi.mocked(users.getBusinessUsers).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(users.getManagedUserRoleOptions).mockResolvedValue([
    { roleId: "PM", name: "프로젝트 담당" },
    { roleId: "SALES_LDR", name: "영업 파트장" },
  ]);
  vi.mocked(parts.getSalesParts).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(parts.getSalesPartDefaults).mockResolvedValue({
    effectiveMonth: "2026-11",
    minMonth: "2026-01",
    profitRate: "40",
    salesExpenseAmount: "1000",
    defaultErrors: [],
  });
  vi.mocked(b7.getB7Template).mockResolvedValue({
    blob: new Blob(["xlsx"]),
    filename: "template.xlsx",
  });
  vi.mocked(exports.createMasterDataExport).mockResolvedValue({
    blob: new Blob(["xlsx"]),
    filename: "거래처_20261111_120000.xlsx",
  });
});
describe("공식 진입점 합성", () => {
  // 앱 셸의 다단계 상호작용은 통합 실행 부하에서 5초를 넘을 수 있어 이 시나리오만 여유를 둔다.
  it("UI-30은 현재 자리와 원래 revision으로 행동하고 조회 실패 뒤 GET만 재시도한다", async () => {
    open("/UI-30");
    await screen.findByTestId("recommendation-candidates");
    expect(
      screen.queryByRole("combobox", { name: "기간" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "엑셀 다운로드" }),
    ).not.toBeInTheDocument();
    await userEvent.selectOptions(
      screen.getByTestId("recommendation-need"),
      "19",
    );
    fireEvent.change(screen.getByTestId("recommendation-start-month"), {
      target: { value: "2027-01" },
    });
    vi.mocked(
      recommendations.getRecommendationHistorySnapshot,
    ).mockRejectedValueOnce(new Error("사본 조회 실패"));
    await userEvent.click(screen.getByTestId("recommendation-assign-1"));
    await screen.findByText("저장됐지만 다시 불러오지 못했습니다");
    expect(
      recommendationActions.createWorkforceRecommendationTentativeAssignment,
    ).toHaveBeenCalledWith("42", 1, {
      needSn: "19",
      expectedRevision: "choice-original",
      targetRevision: "target-original",
      candidateRevision: "candidate-original",
      confirmedWarnings: [],
    });
    await userEvent.click(screen.getByTestId("recommendation-refresh"));
    await waitFor(() =>
      expect(
        recommendations.getRecommendationHistorySnapshot,
      ).toHaveBeenCalledTimes(2),
    );
    expect(
      recommendationActions.createWorkforceRecommendationTentativeAssignment,
    ).toHaveBeenCalledOnce();
    expect(recommendations.requestTargetRecommendation).toHaveBeenCalledOnce();
    expect(screen.getByTestId("recommendation-card-1")).toHaveTextContent(
      "2026-11",
    );
  }, 10000);
  it("UI-30의 기록은 기존 drawer에서 GET만 사용하고 닫아도 추천을 재실행하지 않는다", async () => {
    open("/UI-30");
    await screen.findByTestId("recommendation-candidates");
    await userEvent.click(screen.getByTestId("recommendation-history-open"));
    const dialog = await screen.findByRole("dialog", { name: "추천 기록" });
    expect(
      await within(dialog).findByText("추천 기록이 없습니다."),
    ).toBeVisible();
    await userEvent.click(screen.getByTestId("recommendation-history-close"));
    expect(
      screen.queryByRole("dialog", { name: "추천 기록" }),
    ).not.toBeInTheDocument();
    expect(recommendations.requestTargetRecommendation).toHaveBeenCalledOnce();
    expect(
      applicationRoutes.filter((route) => route.path === "/UI-30"),
    ).toHaveLength(1);
    expect(
      applicationRoutes.some((route) => route.path?.includes("UI-30-")),
    ).toBe(false);
  });
  it("UI-30 대상 조회 오류는 후보 행동을 막고 GET 복구 후에만 첫 추천을 실행한다", async () => {
    vi.mocked(
      recommendations.getWorkforceRecommendationTargets,
    ).mockRejectedValueOnce(new Error("대상 연결 실패"));
    open("/UI-30");
    await screen.findByText("대상 연결 실패");
    expect(
      screen.queryByTestId("recommendation-assign-1"),
    ).not.toBeInTheDocument();
    expect(recommendations.requestTargetRecommendation).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByTestId("recommendation-assign-1");
    expect(recommendations.requestTargetRecommendation).toHaveBeenCalledOnce();
  });
  it("UI-30 진행 대상 없음은 기록 접근을 유지하고 후보나 생성 요청을 만들지 않는다", async () => {
    vi.mocked(
      recommendations.getWorkforceRecommendationTargets,
    ).mockResolvedValue({
      items: [],
      firstTargetId: null,
      serverDate: context.currentDate,
      currentMonth: context.currentMonth,
      baseMonth: context.cashBaseMonth,
      revision: "empty-targets",
    });
    open("/UI-30");
    await screen.findByText("진행 중인 추천 대상이 없습니다.");
    expect(screen.getByTestId("recommendation-history-open")).toBeEnabled();
    expect(
      screen.queryByTestId("recommendation-candidates"),
    ).not.toBeInTheDocument();
    expect(recommendations.requestTargetRecommendation).not.toHaveBeenCalled();
  });
  it("UI-05 기간 변경은 기존 철수 목록을 선택 연도로 GET하고 추천을 기록하지 않는다", async () => {
    open("/UI-05");
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    await userEvent.selectOptions(
      within(screen.getByRole("region", { name: "화면 기간" })).getByLabelText(
        "기간",
      ),
      "2027",
    );
    await waitFor(() =>
      expect(withdrawals.getWithdrawals).toHaveBeenLastCalledWith(
        expect.objectContaining({ year: 2027 }),
      ),
    );
    expect(withdrawals.createNextPositionRecommendation).not.toHaveBeenCalled();
  });
  it.each([
    ["/UI-04", "projectCost", "유형", "contractType", costColumns],
    ["/UI-31", "statusBoard", "PM", "pm", boardColumns],
    ["/UI-31?view=issues", "issues", "조치", "action", issueColumns],
  ] as const)(
    "%s 열 설정은 전체순서와 다른 뷰를 보존한다",
    async (path, view, label, hidden, order) => {
      open(path);
      await userEvent.click(
        await screen.findByRole("button", { name: "열 설정" }),
      );
      const dialog = screen.getByRole("dialog", { name: "현재 표 열 설정" });
      expect(within(dialog).getByLabelText("프로젝트 숨김")).toBeDisabled();
      if (view === "issues")
        expect(within(dialog).getByLabelText("제목 숨김")).toBeDisabled();
      await userEvent.click(within(dialog).getByLabelText(`${label} 숨김`));
      await userEvent.click(
        within(dialog).getByRole("button", { name: "적용" }),
      );
      await waitFor(() =>
        expect(prefs.putPreference).toHaveBeenCalledWith(
          "COLS",
          view === "projectCost" ? "UI-04" : "UI-31",
          expect.objectContaining({ [view]: { order, hidden: [hidden] } }),
          null,
        ),
      );
      if (view !== "projectCost") {
        const call = vi
          .mocked(prefs.putPreference)
          .mock.calls.find(([type]) => type === "COLS")!;
        expect(call[2]).toMatchObject(
          view === "issues"
            ? { statusBoard: { order: boardColumns, hidden: [] } }
            : { issues: { order: issueColumns, hidden: [] } },
        );
      }
      await waitFor(() =>
        expect(
          screen.queryByRole("dialog", { name: "현재 표 열 설정" }),
        ).not.toBeInTheDocument(),
      );
    },
  );
  it.each(["/UI-04", "/UI-31"])(
    "%s 열 설정 취소는 저장하지 않는다",
    async (path) => {
      open(path);
      await userEvent.click(
        await screen.findByRole("button", { name: "열 설정" }),
      );
      await userEvent.click(
        screen.getByLabelText(path === "/UI-04" ? "유형 숨김" : "PM 숨김"),
      );
      await userEvent.click(screen.getByRole("button", { name: "취소" }));
      expect(
        vi
          .mocked(prefs.putPreference)
          .mock.calls.filter(([type]) => type === "COLS"),
      ).toHaveLength(0);
    },
  );
  it("원가 열 설정 실패는 초안을 보존하고 자동 재시도하지 않는다", async () => {
    open("/UI-04");
    await userEvent.click(await screen.findByTestId("project-cost-columns"));
    await userEvent.click(screen.getByLabelText("유형 숨김"));
    vi.mocked(prefs.putPreference).mockRejectedValueOnce(
      new Error("열 저장 실패"),
    );
    await userEvent.click(screen.getByRole("button", { name: "적용" }));
    await screen.findAllByText("열 저장 실패");
    expect(screen.getByLabelText("유형 숨김")).toBeChecked();
    expect(
      vi
        .mocked(prefs.putPreference)
        .mock.calls.filter(([type]) => type === "COLS"),
    ).toHaveLength(1);
  });
  it("UI-04 상세 직접 진입은 월과 선택 식별자로 원가 패널을 연결한다", async () => {
    open("/UI-04?month=2026-10&detailProjectId=P-0042&page=2");
    await screen.findByTestId("project-cost-detail");
    await screen.findByText("상세 원가 조회 실패");
    expect(costDetail.getProjectCostAnalysis).toHaveBeenCalledWith(
      "P-0042",
      "2026-10",
    );
    await userEvent.click(
      within(screen.getByTestId("project-cost-detail")).getByRole("button", {
        name: "닫기",
      }),
    );
    expect(screen.queryByTestId("project-cost-detail")).not.toBeInTheDocument();
    expect(analysis.getProjectCosts).toHaveBeenCalledWith(
      expect.objectContaining({ month: "2026-10", page: 2 }),
      expect.any(AbortSignal),
    );
  });
  it("UI-01 진행 직접 진입은 기존 네 탭을 유지하고 진행 조회 오류를 표시한다", async () => {
    open("/UI-01?projectId=P-0042&tab=progress");
    await screen.findByText("진행 조회 실패");
    expect(progress.getProjectProgress).toHaveBeenCalledWith("P-0042", 2026);
    for (const label of [
      "기본 정보",
      "인원 계획",
      "투입 인력",
      "계약 변경 이력",
      "진행 현황",
      "이슈",
    ])
      expect(
        screen.getByRole("tab", { name: label === "이슈" ? /^이슈/ : label }),
      ).toBeVisible();
    expect(screen.getByRole("tab", { name: "진행 현황" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });
  it("다른 화면으로 이동하면 프로젝트 상세 패널을 숨기고 복귀 시 유지한다", async () => {
    open("/UI-01?projectId=P-0042&tab=progress");
    await screen.findByText("진행 조회 실패");
    expect(screen.getByTestId("project-detail")).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "DB UI-16" }));
    expect(screen.getByTestId("project-detail")).not.toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: /DB UI-01$/ }));
    expect(screen.getByTestId("project-detail")).toBeVisible();
  });
  it("UI-01 이슈 직접 진입은 같은 프로젝트와 연도로 이슈 편집을 연결한다", async () => {
    open("/UI-01?projectId=P-0042&tab=issues");
    await screen.findByTestId("project-issues-panel");
    await waitFor(() =>
      expect(issues.getProjectIssues).toHaveBeenCalledWith(
        "P-0042",
        expect.objectContaining({ status: "OPEN_ACTIVE" }),
      ),
    );
    expect(screen.getByRole("tab", { name: /^이슈/ })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    await waitFor(() =>
      expect(screen.getByTestId("issue-create")).toBeEnabled(),
    );
  });
  it("UI-31 이슈 탭은 기간 선택 없이 조회와 공통 열 설정을 연결한다", async () => {
    open("/UI-31?view=issues&light=위험");
    await screen.findByTestId("project-issues-panel");
    await waitFor(() =>
      expect(issues.getProjectStatusIssues).toHaveBeenCalled(),
    );
    expect(screen.queryByLabelText("기간")).not.toBeVisible();
    expect(analysis.getProjectStatusBoard).toHaveBeenCalledWith(
      expect.objectContaining({ light: "위험" }),
      expect.any(AbortSignal),
    );
    expect(screen.getByTestId("issue-create")).toBeVisible();
  });
  it("재무 직접 진입은 저장된 기간 대신 서버 현재월과 기존 프레임을 사용한다", async () => {
    preferences.push({
      type: "PERIOD",
      key: "UI-10",
      value: { type: "YEAR_MONTH", value: "2025-12" },
      revision: "stored-period",
      stored: true,
    });
    open("/UI-10");
    await screen.findByTestId("financial-items-panel");
    expect(screen.getByLabelText("선택월")).toHaveValue(context.currentMonth);
    expect(financial.getFinancialItems).toHaveBeenCalledWith(
      undefined,
      expect.any(AbortSignal),
    );
    expect(screen.getAllByRole("heading", { name: "DB UI-10" })).toHaveLength(
      1,
    );
    expect(
      screen
        .getByTestId("financial-editor")
        .closest("[data-screen-id='SCR-UI-10']"),
    ).not.toBeNull();
    expect(screen.getByTestId("financial-month-2026-12")).toBeDisabled();
    expect(
      applicationRoutes.filter((route) => route.path === "/UI-10"),
    ).toHaveLength(1);
    expect(
      applicationRoutes.some((route) => route.path?.includes("UI-10-")),
    ).toBe(false);
  });
  it("재무 편집 중 셸 이동의 계속 편집은 초안과 화면을 유지한다", async () => {
    const confirm = vi.spyOn(window, "confirm");
    open("/UI-10");
    await userEvent.click(await screen.findByTestId("financial-items-add"));
    await userEvent.click(screen.getByRole("button", { name: "DB UI-16" }));
    await userEvent.click(
      within(await screen.findByRole("dialog")).getByRole("button", {
        name: "계속 편집",
      }),
    );
    expect(screen.getByTestId("financial-items-new-1-item-name")).toBeVisible();
    expect(screen.getByRole("heading", { name: "DB UI-10" })).toBeVisible();
    expect(financial.saveFinancialItems).not.toHaveBeenCalled();
    expect(confirm).not.toHaveBeenCalled();
    confirm.mockRestore();
  });
  it("재무 작업 탭 닫기의 버리고 이동은 쓰기 없이 대시보드로 돌아간다", async () => {
    open("/UI-10");
    await userEvent.click(await screen.findByTestId("financial-items-add"));
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-10 탭 닫기" }),
    );
    await screen.findByRole("dialog");
    await userEvent.click(screen.getByTestId("financial-navigation-discard"));
    await screen.findByRole("heading", { name: "DB UI-16" });
    expect(
      screen.queryByRole("button", { name: "DB UI-10 탭 닫기" }),
    ).not.toBeInTheDocument();
    expect(financial.saveFinancialItems).not.toHaveBeenCalled();
    const navigation = within(
      screen.getByRole("complementary", { name: "주 메뉴" }),
    ).getByRole("navigation");
    await userEvent.click(
      within(navigation).getByRole("link", { name: /DB UI-10/ }),
    );
    await screen.findByTestId("financial-items-panel");
    expect(
      screen.queryByTestId("financial-items-new-1-item-name"),
    ).not.toBeInTheDocument();
  });
  it("재무 저장 후 이동은 두 탭 변경을 한 번 저장한 뒤 셸 이동을 허용한다", async () => {
    open("/UI-10");
    await userEvent.click(await screen.findByTestId("financial-items-add"));
    fireEvent.change(screen.getByTestId("financial-items-new-1-item-name"), {
      target: { value: "경비" },
    });
    fireEvent.change(screen.getByTestId("financial-items-new-1-amount"), {
      target: { value: "0" },
    });
    await userEvent.click(screen.getByTestId("financial-tab-cash"));
    await userEvent.click(screen.getByTestId("financial-loans-add"));
    fireEvent.change(screen.getByTestId("financial-loans-new-2-amount"), {
      target: { value: "1" },
    });
    await userEvent.click(screen.getByRole("button", { name: "DB UI-16" }));
    await userEvent.click(
      within(await screen.findByRole("dialog")).getByRole("button", {
        name: "저장 후 이동",
      }),
    );
    await screen.findByRole("heading", { name: "DB UI-16" });
    expect(financial.saveFinancialItems).toHaveBeenCalledOnce();
    expect(financial.saveFinancialItems).toHaveBeenCalledWith(
      expect.objectContaining({
        month: context.currentMonth,
        expectedRevision: "revision-current",
        items: expect.objectContaining({
          add: [expect.objectContaining({ itemName: "경비", amount: "0" })],
        }),
        loans: expect.objectContaining({
          add: [expect.objectContaining({ amount: "1" })],
        }),
      }),
    );
  });
  it("재무 저장 실패는 셸 이동을 막고 입력과 세 선택을 보존한다", async () => {
    vi.mocked(financial.saveFinancialItems).mockRejectedValueOnce(
      new Error("재무 저장 실패"),
    );
    open("/UI-10");
    await userEvent.click(await screen.findByTestId("financial-items-add"));
    fireEvent.change(screen.getByTestId("financial-items-new-1-item-name"), {
      target: { value: "보존 경비" },
    });
    fireEvent.change(screen.getByTestId("financial-items-new-1-amount"), {
      target: { value: "1" },
    });
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-10 탭 닫기" }),
    );
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(
      within(dialog).getByRole("button", { name: "저장 후 이동" }),
    );
    await within(dialog).findByText("재무 저장 실패");
    expect(screen.getByRole("heading", { name: "DB UI-10" })).toBeVisible();
    expect(screen.getByTestId("financial-items-new-1-item-name")).toHaveValue(
      "보존 경비",
    );
    expect(financial.saveFinancialItems).toHaveBeenCalledOnce();
    await userEvent.click(
      within(dialog).getByRole("button", { name: "계속 편집" }),
    );
  });
  it("등록된 재무 조회 오류는 재조회로 복구하며 업무 저장을 호출하지 않는다", async () => {
    vi.mocked(financial.getFinancialItems).mockRejectedValueOnce(
      new Error("재무 조회 실패"),
    );
    open("/UI-10");
    await screen.findByText("재무 조회 실패");
    await userEvent.click(
      within(screen.getByTestId("financial-editor")).getByRole("button", {
        name: "다시 조회",
      }),
    );
    await screen.findByTestId("financial-items-panel");
    expect(financial.saveFinancialItems).not.toHaveBeenCalled();
  });
  it("마감 재무 화면은 읽기 전용을 유지하고 변경 작업을 숨긴다", async () => {
    vi.mocked(financial.getFinancialItems).mockResolvedValue(
      financialEditorFixture({
        editable: false,
        closeStatus: "CLOSE",
        readOnlyReason: "마감된 월입니다.",
      }),
    );
    open("/UI-10");
    await screen.findByText(
      /는 마감되었습니다\. 고칠 내용은 다음 달 전월 조정으로 입력하세요\./,
    );
    expect(screen.queryByTestId("financial-save")).not.toBeInTheDocument();
    expect(screen.queryByTestId("financial-items-add")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("financial-upload-button"),
    ).not.toBeInTheDocument();
    expect(corporateTax.createCorporateTaxDefaults).not.toHaveBeenCalled();
  });
  it("UI-13 직접 진입은 기존 청구 화면과 올해 조회 문맥을 유지한다", async () => {
    open("/UI-13");
    await screen.findAllByText(
      "조회된 청구가 없습니다.",
      {},
      { timeout: 5000 },
    );
    expect(screen.getByTestId("bills-screen")).toHaveAttribute(
      "data-screen-id",
      "SCR-UI-13",
    );
    expect(bills.getBills).toHaveBeenCalledWith(
      expect.objectContaining({ year: 2026, includeCarryover: true }),
    );
    expect(screen.getAllByTestId("bills-screen")).toHaveLength(1);
  });
  it("청구 편집 dirty를 셸에 전달하여 탭 닫기 취소 시 입력을 보존한다", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    open("/UI-13");
    await userEvent.click(await screen.findByTestId("bill-create"));
    fireEvent.change(await screen.findByTestId("bill-supply-amount"), {
      target: { value: "1234" },
    });
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-13 탭 닫기" }),
    );
    expect(confirm).toHaveBeenCalledOnce();
    expect(screen.getByTestId("bill-supply-amount")).toHaveValue("1234");
    confirm.mockRestore();
  });
  it("등록된 청구 조회 실패는 기존 재조회 동작으로 복구한다", async () => {
    vi.mocked(bills.getBills).mockRejectedValueOnce(
      new Error("청구 조회 실패"),
    );
    open("/UI-13");
    await screen.findByText("청구 조회 실패");
    await userEvent.click(screen.getByTestId("bill-refresh"));
    await screen.findAllByText(
      "조회된 청구가 없습니다.",
      {},
      { timeout: 5000 },
    );
    expect(bills.getBills).toHaveBeenCalledTimes(2);
  });
  it("배정의 편집과 탭 닫기 취소를 기존 셸에 연결한다", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    open("/UI-12");
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    await userEvent.click(screen.getByTestId("assignment-new"));
    fireEvent.change(await screen.findByTestId("assignment-project-search"), {
      target: { value: "보존할 검색" },
    });
    await userEvent.click(screen.getByRole("button", { name: "DB UI-16" }));
    await userEvent.click(screen.getByRole("button", { name: /DB UI-12$/ }));
    expect(screen.getByTestId("assignment-project-search")).toHaveValue(
      "보존할 검색",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-12 탭 닫기" }),
    );
    expect(confirm).toHaveBeenCalledOnce();
    expect(screen.getByTestId("assignment-project-search")).toHaveValue(
      "보존할 검색",
    );
    confirm.mockRestore();
  });
  it("배정 조회 실패는 등록된 업무 화면에서 재조회한다", async () => {
    vi.mocked(assignments.getAssignments).mockRejectedValueOnce(
      new Error("배정 조회 실패"),
    );
    open("/UI-12");
    await screen.findByText("배정 조회 실패");
    await userEvent.click(
      within(
        screen
          .getByText("배정 조회 실패")
          .closest("[role=alert]") as HTMLElement,
      ).getByRole("button", { name: "다시 조회" }),
    );
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    expect(assignments.getAssignments).toHaveBeenCalledTimes(2);
  });
  it("프로젝트 편집을 보존하고 셸 탭 닫기 취소에 dirty를 전달한다", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    open("/UI-01");
    await userEvent.click(await screen.findByTestId("project-create"));
    const name = await screen.findByTestId("project-name");
    fireEvent.change(name, { target: { value: "보존 프로젝트" } });
    const tabs = within(screen.getByRole("navigation", { name: "열린 화면" }));
    await userEvent.click(tabs.getByRole("button", { name: "DB UI-16" }));
    await userEvent.click(tabs.getByRole("button", { name: /DB UI-01$/ }));
    expect(name).toHaveValue("보존 프로젝트");
    await userEvent.click(
      tabs.getByRole("button", { name: "DB UI-01 탭 닫기" }),
    );
    expect(confirm).toHaveBeenCalledOnce();
    expect(name).toHaveValue("보존 프로젝트");
    confirm.mockRestore();
  });
  it("프로젝트 목록 오류는 실제 화면의 재조회로 복구한다", async () => {
    vi.mocked(projects.getProjects).mockRejectedValueOnce(
      new Error("프로젝트 통신 오류"),
    );
    open("/UI-01");
    await screen.findByText("프로젝트 통신 오류");
    await userEvent.click(
      within(screen.getByTestId("projects-screen")).getByRole("button", {
        name: "다시 조회",
      }),
    );
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    expect(projects.getProjects).toHaveBeenCalledTimes(2);
  });
  it("프로젝트 파트 변경과 PRE 전환을 병합된 상세 동작에 연결한다", async () => {
    vi.mocked(projects.getProjects).mockResolvedValue({
      items: [
        {
          ...project,
          customerName: null,
          partName: null,
          personCount: 0,
          distribution: null,
        },
      ],
      total: 1,
      unassignedCount: 1,
      page: 1,
      size: 50,
      year: 2026,
    });
    vi.mocked(projectParts.getProjectParts).mockResolvedValue({
      items: [],
      replacementTargets: [],
      retainedBasePart: null,
      minimumMonth: "2026-11",
      maximumMonth: "2027-12",
      revision: "parts-revision",
    });
    open("/UI-01");
    await userEvent.click(await screen.findByTestId("project-open-p-0042"));
    await userEvent.click(await screen.findByTestId("project-part-change"));
    expect(
      await screen.findByTestId("project-part-dialog"),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(projectParts.getProjectParts).toHaveBeenCalledWith(project.id),
    );
    await userEvent.click(
      within(screen.getByRole("dialog", { name: "영업 파트 변경" })).getByRole(
        "button",
        { name: "취소" },
      ),
    );
    await waitFor(() =>
      expect(screen.getByTestId("project-progress-button")).toBeEnabled(),
    );
    await userEvent.click(screen.getByTestId("project-progress-button"));
    expect(
      await screen.findByTestId("project-progress-dialog"),
    ).toHaveTextContent(project.id);
  });
  it("B6는 기존 주소의 배분표에서 선택한 단계와 계약기간으로 연다", async () => {
    vi.mocked(projects.getProjects).mockResolvedValue({
      items: [
        {
          ...project,
          customerName: null,
          partName: null,
          personCount: 0,
          distribution: null,
        },
      ],
      total: 1,
      unassignedCount: 1,
      page: 1,
      size: 50,
      year: 2026,
    });
    vi.mocked(distribution.getProjectDistribution).mockResolvedValue({
      project,
      viewedStep: "EST",
      selectedStep: null,
      months: [],
      rows: [],
      monthlyTotals: [],
      totalAmount: "0",
      revision: "distribution-revision",
    });
    vi.mocked(distribution.getProjectDistributionComparison).mockResolvedValue({
      selectedStep: null,
      contractAmount: "100",
      totalAmount: "0",
      differenceAmount: "-100",
      mismatchCount: 0,
      items: [],
      warnings: [],
    });
    vi.mocked(distribution.getProjectDistributionUploads).mockResolvedValue({
      items: [],
    });
    open("/UI-01");
    await userEvent.click(
      await screen.findByTestId("project-distribution-p-0042"),
    );
    await waitFor(() =>
      expect(screen.getByTestId("distribution-upload")).toBeEnabled(),
    );
    await userEvent.click(screen.getByTestId("distribution-upload"));
    expect(await screen.findByTestId("b6-upload-context")).toHaveTextContent(
      project.startDate,
    );
    expect(screen.getByTestId("b6-upload-context")).toHaveTextContent(
      project.endDate,
    );
    expect(screen.getByTestId("b6-step-select")).toHaveValue("EST");
    expect(screen.getByTestId("b6-upload-context")).toHaveTextContent(
      "선택연도 2026",
    );
    expect(applicationRoutes.some((route) => route.path?.includes("B6"))).toBe(
      false,
    );
  });
  it.each(Object.keys(screenRegistry))(
    "%s는 등록된 실제 화면으로 진입한다",
    async (id) => {
      open("/" + id);
      await screen.findByRole("heading", { name: `DB ${id}` });
      expect(applicationRoutes.some((route) => route.path === "/" + id)).toBe(
        true,
      );
      if (id === "UI-27") {
        expect(await screen.findByLabelText("A 이름")).toBeInTheDocument();
        expect(
          screen.getAllByRole("navigation", { name: "공통코드 하위 탭" }),
        ).toHaveLength(1);
        expect(
          screen.queryByText("화면 구성 요소가 아직 연결되지 않았습니다."),
        ).not.toBeInTheDocument();
      } else if (id === "UI-16") {
        await waitFor(() =>
          expect(screen.getByTestId("dashboard-metric-sales")).toBeVisible(),
        );
        expect(dashboard.getDashboard).toHaveBeenCalled();
      } else if (id === "UI-15") {
        expect(await screen.findByTestId("clients-screen")).toBeInTheDocument();
        expect(clients.getClients).toHaveBeenCalled();
      } else if (id === "UI-01") {
        expect(await screen.findByTestId("projects-screen")).toBeVisible();
        await waitFor(() =>
          expect(projects.getProjects).toHaveBeenCalledWith(
            expect.objectContaining({ year: 2026 }),
          ),
        );
      } else if (id === "UI-23") {
        expect(
          await screen.findByTestId("business-users-screen"),
        ).toBeInTheDocument();
        expect(users.getBusinessUsers).toHaveBeenCalled();
      } else if (id === "UI-12") {
        expect(
          await screen.findByTestId("staffing-assignments-screen"),
        ).toBeVisible();
        await screen.findByText("조건에 맞는 결과가 없습니다.");
        expect(assignments.getAssignments).toHaveBeenCalledWith(
          expect.objectContaining({ year: 2026 }),
        );
      } else if (id === "UI-13") {
        expect(await screen.findByTestId("bills-screen")).toBeVisible();
        await screen.findAllByText(
          "조회된 청구가 없습니다.",
          {},
          { timeout: 5000 },
        );
        expect(bills.getBills).toHaveBeenCalledWith(
          expect.objectContaining({ year: 2026 }),
        );
      } else if (id === "UI-10") {
        expect(
          await screen.findByTestId("financial-items-panel"),
        ).toBeVisible();
        expect(financial.getFinancialItems).toHaveBeenCalled();
      } else if (id === "UI-03") {
        expect(
          await screen.findByTestId("profitability-overview"),
        ).toBeVisible();
        expect(
          screen.getByRole("textbox", { name: "이름 검색" }),
        ).toBeVisible();
        expect(screen.getByTestId("profitability-search-button")).toBeVisible();
      } else if (id === "UI-04" || id === "UI-31") {
        expect(
          await screen.findByTestId(
            id === "UI-04" ? "project-cost-analysis" : "project-status-board",
          ),
        ).toBeVisible();
        await screen.findByText(
          id === "UI-04"
            ? "조건에 맞는 프로젝트가 없습니다."
            : "조건에 맞는 결과가 없습니다.",
        );
      } else if (id === "UI-19") {
        expect(
          await screen.findByTestId("sales-part-settlement-screen"),
        ).toBeVisible();
        await screen.findByText(
          "맡은 영업 파트가 없습니다. 본사(직접 영업)는 파트 이익금 정산 대상이 아닙니다.",
        );
        expect(settlements.getSalesPartSettlements).toHaveBeenCalledWith(
          undefined,
          expect.any(AbortSignal),
        );
        expect(screenRegistry[id].periodType).toBe("YEAR_MONTH");
      } else if (id === "UI-32") {
        expect(await screen.findByTestId("renegotiation-screen")).toBeVisible();
        await screen.findByText("조건에 맞는 재협상 대상이 없습니다");
        expect(renegotiations.getRateRenegotiations).toHaveBeenCalledWith(
          renegotiations.defaultRenegotiationQuery(),
          expect.any(AbortSignal),
        );
        expect(screenRegistry[id].periodType).toBe("NONE");
      } else if (id === "UI-02" || id === "UI-33") {
        expect(
          await screen.findByTestId(
            id === "UI-02" ? "developers-screen" : "candidates-screen",
          ),
        ).toBeVisible();
        expect(
          id === "UI-02" ? developers.getDevelopers : candidates.getCandidates,
        ).toHaveBeenCalled();
      } else if (id === "UI-05") {
        expect(
          await screen.findByTestId("withdrawal-list-screen"),
        ).toBeVisible();
        await screen.findByText("조건에 맞는 결과가 없습니다.");
        expect(withdrawals.getWithdrawals).toHaveBeenCalledWith(
          expect.objectContaining({ year: 2026 }),
        );
        expect(screenRegistry[id].periodType).toBe("YEAR");
      } else if (id === "UI-30") {
        expect(
          await screen.findByTestId("recommendation-candidates"),
        ).toBeVisible();
        expect(screen.getByTestId("recommendation-assign-1")).toBeEnabled();
        expect(
          recommendations.requestTargetRecommendation,
        ).toHaveBeenCalledOnce();
        expect(screenRegistry[id].periodType).toBe("NONE");
      } else if (id === "UI-21" || id === "UI-14") {
        expect(
          await screen.findByTestId(
            id === "UI-21" ? "cash-flow-screen" : "month-close-screen",
          ),
        ).toBeVisible();
        await screen.findByText(
          id === "UI-21" ? "자금수지 원천 조회 실패" : "월 마감 원천 조회 실패",
        );
        expect(
          id === "UI-21" ? cashFlow.getCashFlow : monthClose.getMonthCloses,
        ).toHaveBeenCalled();
        expect(screenRegistry[id].periodType).toBe(
          id === "UI-21" ? "YEAR" : "YEAR_MONTH",
        );
        expect(
          screen
            .queryAllByText("이 화면은 다음 단계에서 만듭니다.")
            .some((node) => !node.closest("[hidden]")),
        ).toBe(false);
      } else if (["UI-11", "UI-17", "UI-28", "UI-29"].includes(id)) {
        const screens: Record<string, string> = {
          "UI-11": "staffing-comparison-screen",
          "UI-17": "workforce-forecast-screen",
          "UI-28": "workforce-operations-screen",
          "UI-29": "sales-pipeline-screen",
        };
        expect(await screen.findByTestId(screens[id])).toBeVisible();
        expect(
          screen
            .queryAllByText("이 화면은 다음 단계에서 만듭니다.")
            .some((node) => !node.closest("[hidden]")),
        ).toBe(false);
      } else if (["UI-07", "UI-08", "UI-09", "UI-18"].includes(id)) {
        const financialScreens: Record<string, string> = {
          "UI-07": "profit-structure-screen",
          "UI-08": "operating-profit-screen",
          "UI-09": "net-profit-screen",
          "UI-18": "business-plan-performance",
        };
        expect(await screen.findByTestId(financialScreens[id])).toBeVisible();
        expect(
          screen
            .queryAllByText("이 화면은 다음 단계에서 만듭니다.")
            .some((node) => !node.closest("[hidden]")),
        ).toBe(false);
      } else if (id === "UI-24" || id === "UI-25") {
        expect(
          await screen.findByTestId(
            id === "UI-24" ? "roles-screen" : "menus-screen",
          ),
        ).toBeVisible();
        if (id === "UI-24") expect(roles.getRoleMenus).toHaveBeenCalled();
        else expect(managedMenus.getMenuManagement).toHaveBeenCalled();
        if (id === "UI-24") {
          expect(roles.getRoles).toHaveBeenCalled();
          expect(
            await screen.findByTestId("permission-settings-panel"),
          ).toBeVisible();
          expect(
            permissionSettings.getCurrentUserPermissionSettings,
          ).toHaveBeenCalled();
        }
        expect(
          screen.queryByText("이 화면은 다음 단계에서 만듭니다."),
        ).not.toBeInTheDocument();
      } else if (id === "UI-26") {
        expect(
          await screen.findByRole("region", { name: "AI 대화" }),
        ).toBeVisible();
        expect(
          screen
            .queryAllByText("이 화면은 다음 단계에서 만듭니다.")
            .some((node) => !node.closest("[hidden]")),
        ).toBe(false);
      } else
        expect(
          screen
            .getAllByText("이 화면은 다음 단계에서 만듭니다.")
            .some((node) => !node.closest("[hidden]")),
        ).toBe(true);
    },
  );
  it.each(["/", "/#UI-01", "/UI-20", "/unknown"])(
    "%s는 DB 메뉴에 맞춰 복구한다",
    async (path) => {
      open(path);
      await screen.findByRole("heading", {
        name: path.includes("#UI-01") ? "DB UI-01" : "DB UI-16",
      });
      expect(applicationRoutes.some((route) => route.path === "/UI-20")).toBe(
        false,
      );
    },
  );
  it("UI-27을 열면 최근 본 화면만 조용히 저장하고 '저장했습니다.'를 띄우지 않는다", async () => {
    open();
    await screen.findByLabelText("A 이름");
    await waitFor(() =>
      expect(
        vi
          .mocked(prefs.putPreference)
          .mock.calls.some(([type]) => type === "RECENT"),
      ).toBe(true),
    );
    await waitFor(() => expect(prefs.getPreferences).toHaveBeenCalled());
    expect(screen.queryByText("저장했습니다.")).not.toBeInTheDocument();
  });
  it("UI-27 네 탭의 실제 기능 본문을 연결하고 추가 route를 만들지 않는다", async () => {
    open();
    await screen.findByLabelText("A 이름");
    const tabs = screen.getByRole("navigation", { name: "공통코드 하위 탭" });
    await userEvent.click(
      within(tabs).getByRole("button", { name: "엑셀 표기 대응" }),
    );
    await screen.findByText("등록된 표기가 없습니다.");
    expect(mappings.getCodeMappings).toHaveBeenCalled();
    await userEvent.click(
      within(tabs).getByRole("button", { name: "운영 기준값" }),
    );
    await waitFor(() =>
      expect(standards.getOperatingStandards).toHaveBeenCalled(),
    );
    await userEvent.click(
      within(tabs).getByRole("button", { name: "변경 이력" }),
    );
    await screen.findByText("조회된 변경 이력이 없습니다.");
    expect(screen.getAllByRole("heading", { name: "DB UI-27" })).toHaveLength(
      1,
    );
  });
  it("일반 화면 전환은 편집을 보존하고 닫기 취소도 입력을 보존한다", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    open();
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "보존 입력" },
    });
    await userEvent.click(screen.getByRole("button", { name: "DB UI-16" }));
    await userEvent.click(screen.getByRole("button", { name: /DB UI-27$/ }));
    expect(screen.getByLabelText("A 이름")).toHaveValue("보존 입력");
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-27 탭 닫기" }),
    );
    expect(confirm).toHaveBeenCalled();
    expect(screen.getByLabelText("A 이름")).toHaveValue("보존 입력");
    confirm.mockRestore();
  });
  // 폐기·대시보드 이동·재진입까지 검증하므로 이 시나리오에만 통합 실행 시간 여유를 둔다.
  it("임시 ADMIN은 메뉴와 코드 CTA를 사용하고 폐기 후 재진입은 깨끗한 화면이다", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
    open();
    const input = await screen.findByLabelText("A 이름");
    const sidebar = within(
      within(screen.getByRole("complementary", { name: "주 메뉴" })).getByRole(
        "navigation",
      ),
    );
    expect(sidebar.getByRole("link", { name: /DB UI-27/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "그룹 추가" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "코드 추가" })).toBeEnabled();
    fireEvent.change(input, { target: { value: "폐기" } });
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-27 탭 닫기" }),
    );
    await screen.findByRole("heading", { name: "DB UI-16" });
    expect(confirm).toHaveBeenCalledTimes(1);
    await userEvent.click(sidebar.getByRole("link", { name: /DB UI-27/ }));
    expect(await screen.findByLabelText("A 이름")).toHaveValue("기존 이름");
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-27 탭 닫기" }),
    );
    expect(confirm).toHaveBeenCalledTimes(1);
  }, 10000);
  it("설정 모달과 편집 표는 같은 저장 결과를 즉시 사용한다", async () => {
    open();
    await screen.findByLabelText("A 이름");
    await userEvent.click(screen.getByRole("button", { name: "임시 관리자" }));
    await userEvent.click(screen.getByRole("button", { name: "화면 설정" }));
    const dialog = screen.getByRole("dialog", { name: "화면 설정" });
    fireEvent.change(within(dialog).getByLabelText("한 화면 건수"), {
      target: { value: "20" },
    });
    await waitFor(() =>
      expect(prefs.putPreference).toHaveBeenCalledWith(
        "PAGE_SIZE",
        "*",
        20,
        null,
      ),
    );
    await waitFor(() =>
      expect(codes.getGroupCodes).toHaveBeenLastCalledWith("EDUCATION", 1, 20),
    );
    expect(screen.getAllByRole("dialog")).toHaveLength(1);
  });
  it("열 설정은 표시 중 모드의 전체 정의를 쓰고 작업열은 제외한다", async () => {
    open();
    await screen.findByLabelText("A 이름");
    await userEvent.click(screen.getByRole("button", { name: "열 설정" }));
    const dialog = screen.getByRole("dialog", { name: "현재 표 열 설정" });
    expect(within(dialog).getByLabelText("코드 숨김")).toBeDisabled();
    expect(within(dialog).getByLabelText("추가값 2 숨김")).toBeInTheDocument();
    expect(
      within(dialog).queryByLabelText("삭제 숨김"),
    ).not.toBeInTheDocument();
    fireEvent.click(within(dialog).getByLabelText("순서 숨김"));
    await userEvent.click(within(dialog).getByRole("button", { name: "적용" }));
    await waitFor(() =>
      expect(
        screen.queryByRole("columnheader", { name: "순서" }),
      ).not.toBeInTheDocument(),
    );
  });
  it("설정 통신 오류는 기존 표와 입력을 보존하며 자동 재저장하지 않는다", async () => {
    open();
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "보존 입력" },
    });
    vi.mocked(prefs.putPreference).mockRejectedValueOnce(
      new Error("설정 통신 오류"),
    );
    fireEvent.change(screen.getByLabelText("한 화면 건수"), {
      target: { value: "20" },
    });
    await screen.findByText("설정 통신 오류");
    expect(screen.getByLabelText("한 화면 건수")).toHaveValue("50");
    expect(screen.getByLabelText("A 이름")).toHaveValue("보존 입력");
    expect(prefs.putPreference).toHaveBeenCalledTimes(2);
  });
  it("commit 이벤트는 열린 이력을 재조회한다", async () => {
    open();
    await screen.findByLabelText("A 이름");
    await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
    await screen.findByText("조회된 변경 이력이 없습니다.");
    const reads = vi.mocked(shared.getCommonCodeHistory).mock.calls.length;
    act(() =>
      window.dispatchEvent(new CustomEvent(mappings.CODE_MAPPINGS_COMMITTED)),
    );
    await waitFor(() =>
      expect(shared.getCommonCodeHistory).toHaveBeenCalledTimes(reads + 1),
    );
  });
  it("CASH commit만 열린 기간 정보를 재조회한다", async () => {
    open("/UI-16");
    await screen.findByLabelText("기간");
    const reads = vi.mocked(shared.getPeriodContext).mock.calls.length;
    act(() => standards.notifyOperatingStandardsCommitted(false));
    expect(shared.getPeriodContext).toHaveBeenCalledTimes(reads);
    act(() => standards.notifyOperatingStandardsCommitted(true));
    await waitFor(() =>
      expect(shared.getPeriodContext).toHaveBeenCalledTimes(reads + 1),
    );
  });
  it("거래처와 영업 파트에서 같은 화면 안내를 열고 거래처에서 B7 업로드를 연다", async () => {
    open("/UI-15");
    await screen.findByText(clientFixture.name);
    const guide = screen.getByRole("button", { name: "화면 안내" });
    await userEvent.click(guide);
    const dialog = screen.getByRole("dialog", { name: /화면 안내/ });
    expect(dialog).toHaveTextContent("구분·상호·사업자등록번호");
    expect(dialog).toHaveTextContent("세금계산서 이메일");
    expect(dialog).toHaveTextContent("은행·계좌번호·예금주");
    expect(dialog).toHaveTextContent("영업 파트 기준 적용");
    const guideText = dialog.textContent;
    await userEvent.keyboard("{Escape}");
    await userEvent.click(screen.getByTestId("parts-workspace-tab"));
    expect(screen.getByRole("button", { name: "화면 안내" })).toBe(guide);
    await userEvent.click(guide);
    expect(screen.getByRole("dialog", { name: /화면 안내/ }).textContent).toBe(guideText);
    await userEvent.keyboard("{Escape}");
    await userEvent.click(screen.getByTestId("clients-workspace-tab"));
    await userEvent.click(screen.getByTestId("clients-template"));
    await waitFor(() => expect(b7.saveDownload).toHaveBeenCalled());
    await userEvent.click(screen.getByTestId("clients-upload"));
    expect(await screen.findByTestId("b7-upload-dialog")).toBeInTheDocument();
    expect(
      applicationRoutes.some((route) => route.path?.includes("upload")),
    ).toBe(false);
    expect(
      screen
        .queryAllByText("이 화면은 다음 단계에서 만듭니다.")
        .some((node) => !node.closest("[hidden]")),
    ).toBe(false);
  });
  it("거래처 다운로드는 현재 필터와 저장된 열 순서·숨김을 연결한다", async () => {
    preferences.push({
      type: "COLS",
      key: "UI-15",
      value: {
        clients: {
          order: [...exports.clientExportColumns].reverse(),
          hidden: ["phone"],
        },
      },
      revision: null,
      stored: false,
    });
    open("/UI-15");
    await screen.findByText(clientFixture.name);
    await userEvent.click(screen.getByTestId("clients-export"));
    await waitFor(() =>
      expect(exports.createMasterDataExport).toHaveBeenCalledWith(
        expect.objectContaining({
          view: "CLIENTS",
          filters: expect.objectContaining({ useYn: "Y" }),
          columns: [...exports.clientExportColumns]
            .reverse()
            .filter((id) => id !== "phone"),
          amountUnit: "천원",
        }),
      ),
    );
    expect(exports.saveMasterDataDownload).toHaveBeenCalled();
  });
  it("선택한 거래처 revision을 일괄 상태 작업에 넘긴다", async () => {
    vi.mocked(actions.saveClientBulkStatus).mockResolvedValue({
      items: [
        { id: clientFixture.id, name: clientFixture.name, result: "noChange" },
      ],
      applied: 0,
      noChange: 1,
      rejected: 0,
    });
    open("/UI-15");
    await screen.findByText(clientFixture.name);
    await userEvent.click(
      screen.getByRole("checkbox", { name: `${clientFixture.id} 선택` }),
    );
    await userEvent.click(await screen.findByTestId("client-status-n"));
    await userEvent.click(
      within(
        screen.getByRole("dialog", { name: "거래처 사용 중지" }),
      ).getByRole("button", { name: "확인" }),
    );
    await waitFor(() =>
      expect(actions.saveClientBulkStatus).toHaveBeenCalledWith("N", [
        { id: clientFixture.id, expectedRevision: clientFixture.revision },
      ]),
    );
  });
  it("사용자 편집은 화면 전환과 탭 닫기 취소에서 보존된다", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    open("/UI-23");
    await screen.findByText("조회된 사용자가 없습니다.");
    await userEvent.click(screen.getByTestId("business-user-register"));
    fireEvent.change(screen.getByTestId("business-user-name"), {
      target: { value: "보존 사용자" },
    });
    await userEvent.click(screen.getByRole("button", { name: "DB UI-16" }));
    await userEvent.click(screen.getByRole("button", { name: /DB UI-23$/ }));
    expect(screen.getByTestId("business-user-name")).toHaveValue("보존 사용자");
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-23 탭 닫기" }),
    );
    expect(confirm).toHaveBeenCalled();
    expect(screen.getByTestId("business-user-name")).toHaveValue("보존 사용자");
    confirm.mockRestore();
  });
  it("파트장 선택의 사용자 등록 컨텍스트는 SALES_LDR 기본값과 복귀 경로를 보존한다", async () => {
    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: "/UI-23",
            state: {
              businessUserRegistration: true,
              qualificationPurpose: "PART_LEADER",
              returnTo: "/UI-15",
            },
          },
        ]}
      >
        <ApplicationRoutes />
      </MemoryRouter>,
    );
    const dialog = await screen.findByRole("dialog", { name: "사용자 등록" });
    expect(await within(dialog).findByLabelText("영업 파트장")).toBeChecked();
    expect(within(dialog).getByLabelText("프로젝트 담당")).not.toBeChecked();
    await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
    await screen.findByRole("heading", { name: "DB UI-15" });
    expect(
      screen.queryByRole("dialog", { name: "사용자 등록" }),
    ).not.toBeInTheDocument();
  });
  it("영업 파트 상세의 기준 변경을 병합된 편집기에 연결한다", async () => {
    const standard = {
      effectiveMonth: "2026-01",
      endMonth: null,
      leaderUserId: "admin",
      leaderName: "담당자",
      profitRate: "40",
      salesExpenseAmount: "1000",
      status: "CURRENT" as const,
    };
    const part = {
      id: "PART-123",
      name: "영업 테스트",
      useYn: "Y" as const,
      currentMonth: "2026-11",
      currentStandard: standard,
      scheduledChange: null,
      personCount: 0,
      projectCount: 0,
      revision: "revision",
    };
    const detail = {
      part,
      standards: [standard],
      minMonth: "2026-01",
      revision: "revision",
    };
    vi.mocked(parts.getSalesParts).mockResolvedValue({
      items: [part],
      total: 1,
      page: 1,
      size: 50,
    });
    vi.mocked(parts.getSalesPart).mockResolvedValue(detail);
    vi.mocked(partStandards.getPartStandardDetail).mockResolvedValue(detail);
    vi.mocked(partStandards.getSalesPartDefaults).mockResolvedValue({
      effectiveMonth: "2026-11",
      minMonth: "2026-01",
      profitRate: "40",
      salesExpenseAmount: "1000",
      defaultErrors: [],
    });
    open("/UI-15");
    await screen.findByText(clientFixture.name);
    await userEvent.click(screen.getByTestId("parts-workspace-tab"));
    await userEvent.click(
      await screen.findByTestId("sales-part-open-part-123"),
    );
    await userEvent.click(
      await screen.findByTestId("sales-part-standard-change"),
    );
    expect(
      await screen.findByTestId("part-standard-editor"),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(partStandards.getPartStandardDetail).toHaveBeenCalledWith(part.id),
    );
    expect(screen.getByLabelText(/적용 시작월/)).toHaveValue("2026-11");
  });
  it("거래처 계좌 원문은 다른 업무 화면으로 이동하면 버린다", async () => {
    vi.mocked(clients.getClientAccount).mockResolvedValue({
      accountNumber: "001-2345678",
    });
    open("/UI-15");
    await screen.findByText(clientFixture.name);
    await userEvent.click(
      screen.getByTestId(`client-${clientFixture.id}-open`),
    );
    await userEvent.click(await screen.findByTestId("client-account-show"));
    expect(
      await screen.findByTestId("client-account-number"),
    ).toHaveTextContent("001-2345678");
    await userEvent.click(screen.getByRole("button", { name: "DB UI-16" }));
    expect(screen.queryByTestId("client-drawer")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /DB UI-15$/ }));
    expect(
      await screen.findByTestId("client-account-show"),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId("client-account-number"),
    ).not.toBeInTheDocument();
  });
  it("메뉴 오류는 업무 본문 없이 재시도를 제공한다", async () => {
    vi.mocked(shared.getMenus).mockRejectedValueOnce(
      new Error("메뉴 통신 오류"),
    );
    open();
    await screen.findByText("메뉴 통신 오류");
    expect(codes.getCodeGroups).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByLabelText("A 이름");
  });
  it.each(["UI-02", "UI-33"] as const)(
    "%s 편집은 화면 전환과 닫기 취소에서 보존된다",
    async (id) => {
      const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
      open(`/${id}`);
      await userEvent.click(
        await screen.findByTestId(
          id === "UI-02" ? "developer-create-button" : "candidate-create",
        ),
      );
      // 등록 폼은 공통 모달이라 뒤의 목록 필터에도 같은 이름표가 있다: 모달 안으로 범위를 좁힌다.
      const name = within(screen.getByRole("dialog")).getByLabelText(/^이름/);
      fireEvent.change(name, { target: { value: "보존 인력" } });
      const tabs = within(
        screen.getByRole("navigation", { name: "열린 화면" }),
      );
      await userEvent.click(tabs.getByRole("button", { name: "DB UI-16" }));
      if (id === "UI-33")
        expect(
          screen.queryByRole("dialog", { name: "외부 인력 후보 등록" }),
        ).not.toBeInTheDocument();
      await userEvent.click(
        tabs.getByRole("button", { name: new RegExp(`DB ${id}$`) }),
      );
      expect(name).toHaveValue("보존 인력");
      await userEvent.click(
        tabs.getByRole("button", { name: `DB ${id} 탭 닫기` }),
      );
      expect(confirm).toHaveBeenCalledOnce();
      expect(name).toHaveValue("보존 인력");
      confirm.mockRestore();
    },
  );
  it.each(["UI-02", "UI-33"] as const)(
    "%s 열·밀도·페이지 설정을 업무 화면에 연결한다",
    async (id) => {
      const isDeveloper = id === "UI-02";
      const order = isDeveloper
        ? [
            "name",
            "companyName",
            "employeeType",
            "grade",
            "job",
            "status",
            "currentProjects",
            "withdrawalDate",
            "monthlyRate",
          ]
        : [
            "name",
            "employeeType",
            "job",
            "grade",
            "skills",
            "homeAddress",
            "desiredRate",
            "availableDate",
            "contactStatus",
            "lastContactDate",
            "source",
          ];
      preferences.push({
        type: "COLS",
        key: id,
        value: {
          [isDeveloper ? "developers" : "candidates"]: { order, hidden: [] },
        },
        revision: null,
        stored: false,
      });
      preferences.find((p) => p.type === "PAGE_SIZE")!.value = 20;
      open(`/${id}`);
      await screen.findByTestId(
        isDeveloper ? "developers-screen" : "candidates-screen",
      );
      await waitFor(() =>
        expect(
          isDeveloper ? developers.getDevelopers : candidates.getCandidates,
        ).toHaveBeenCalledWith(
          expect.objectContaining({ size: 20 }),
          ...(isDeveloper ? [] : [expect.any(AbortSignal)]),
        ),
      );
      await userEvent.click(
        screen.getByTestId(
          isDeveloper ? "developer-columns-button" : "candidate-columns",
        ),
      );
      const dialog = screen.getByRole("dialog", { name: "현재 표 열 설정" });
      expect(within(dialog).getByLabelText("이름 숨김")).toBeDisabled();
      fireEvent.click(within(dialog).getByLabelText("등급 숨김"));
      await userEvent.click(
        within(dialog).getByRole("button", { name: "적용" }),
      );
      await waitFor(() =>
        expect(
          screen.queryByRole("columnheader", { name: "등급" }),
        ).not.toBeInTheDocument(),
      );
      expect(prefs.putPreference).toHaveBeenCalledWith(
        "COLS",
        id,
        {
          [isDeveloper ? "developers" : "candidates"]: {
            order,
            hidden: ["grade"],
          },
        },
        null,
      );
      await userEvent.click(
        screen.getByTestId(
          isDeveloper ? "developer-density-button" : "candidate-density",
        ),
      );
      await waitFor(() =>
        expect(prefs.putPreference).toHaveBeenCalledWith(
          "DENSE",
          "*",
          "좁게",
          null,
        ),
      );
    },
  );
});
