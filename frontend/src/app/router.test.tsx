import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApplicationRoutes, applicationRoutes } from "./router";
import { screenRegistry } from "../pages/shared/screenRegistry";
import * as shared from "../pages/shared/sharedApi";
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
import { clientFixture, pageFixture } from "../pages/clients/clientsFixtures";

vi.mock("../pages/clients/clientsApi");
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
const open = (path = "/UI-27") =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <ApplicationRoutes />
    </MemoryRouter>,
  );
beforeEach(() => {
  vi.resetAllMocks();
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
  vi.mocked(users.getBusinessRoleOptions).mockResolvedValue({
    items: [],
    roleOptions: [
      { roleId: "PM", name: "프로젝트 담당" },
      { roleId: "SALES_LDR", name: "영업 파트장" },
    ],
  });
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
  it("UI-13 직접 진입은 기존 청구 화면과 올해 조회 문맥을 유지한다", async () => {
    open("/UI-13");
    await screen.findAllByText("조회된 청구가 없습니다.");
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
    await screen.findAllByText("조회된 청구가 없습니다.");
    expect(bills.getBills).toHaveBeenCalledTimes(2);
  });
  it("배정의 편집과 탭 닫기 취소를 기존 셸에 연결한다", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    open("/UI-12");
    await screen.findByText("조회된 투입 배정이 없습니다.");
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
    await userEvent.click(screen.getByTestId("assignments-refresh"));
    await screen.findByText("조회된 투입 배정이 없습니다.");
    expect(assignments.getAssignments).toHaveBeenCalledTimes(2);
  });
  it("프로젝트 편집을 보존하고 셸 탭 닫기 취소에 dirty를 전달한다", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    open("/UI-01");
    await userEvent.click(await screen.findByTestId("project-create"));
    const name = await screen.findByLabelText(/프로젝트명/);
    fireEvent.change(name, { target: { value: "보존 프로젝트" } });
    await userEvent.click(screen.getByRole("button", { name: "DB UI-16" }));
    await userEvent.click(screen.getByRole("button", { name: /DB UI-01$/ }));
    expect(name).toHaveValue("보존 프로젝트");
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-01 탭 닫기" }),
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
    await screen.findByText("조회된 자료가 없습니다.");
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
        await screen.findByText("조회된 투입 배정이 없습니다.");
        expect(assignments.getAssignments).toHaveBeenCalledWith(
          expect.objectContaining({ year: 2026 }),
        );
      } else if (id === "UI-13") {
        expect(await screen.findByTestId("bills-screen")).toBeVisible();
        await screen.findAllByText("조회된 청구가 없습니다.");
        expect(bills.getBills).toHaveBeenCalledWith(
          expect.objectContaining({ year: 2026 }),
        );
      } else if (id === "UI-02" || id === "UI-33") {
        expect(
          await screen.findByTestId(
            id === "UI-02" ? "developers-screen" : "candidates-screen",
          ),
        ).toBeVisible();
        expect(
          id === "UI-02" ? developers.getDevelopers : candidates.getCandidates,
        ).toHaveBeenCalled();
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
  });
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
  it("거래처의 B7 업로드와 양식 다운로드를 기존 화면에 연결한다", async () => {
    open("/UI-15");
    await screen.findByText(clientFixture.name);
    await userEvent.click(screen.getByTestId("clients-template"));
    await waitFor(() => expect(b7.saveDownload).toHaveBeenCalled());
    await userEvent.click(screen.getByTestId("clients-upload"));
    expect(await screen.findByTestId("b7-upload-dialog")).toBeInTheDocument();
    expect(
      applicationRoutes.some((route) => route.path?.includes("upload")),
    ).toBe(false);
    expect(
      screen.queryByText("이 화면은 다음 단계에서 만듭니다."),
    ).not.toBeVisible();
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
    await userEvent.click(screen.getByTestId("client-status-n"));
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
      const name = screen.getByLabelText(/^이름/);
      fireEvent.change(name, { target: { value: "보존 인력" } });
      await userEvent.click(screen.getByRole("button", { name: "DB UI-16" }));
      if (id === "UI-33")
        expect(
          screen.queryByRole("dialog", { name: "외부 인력 후보 등록" }),
        ).not.toBeInTheDocument();
      await userEvent.click(
        screen.getByRole("button", { name: new RegExp(`DB ${id}$`) }),
      );
      expect(name).toHaveValue("보존 인력");
      await userEvent.click(
        screen.getByRole("button", { name: `DB ${id} 탭 닫기` }),
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
