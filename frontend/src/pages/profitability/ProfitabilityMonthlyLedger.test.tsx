import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { PreferencesProvider } from "../me/usePreferences";
import type { PreferenceApi } from "../me/preferencesApi";
import * as shared from "../shared/sharedApi";
import * as clients from "../clientsops/clientActionsApi";
import * as parts from "../salespartsops/partStandardsApi";
import * as people from "../developers/peopleClient";
import { DeveloperDrawer } from "../developers/DeveloperDrawer";
import { IndividualProfitabilityScreen } from "./IndividualProfitabilityScreen";
import {
  ProfitabilityMonthlyLedger,
  monthlyCostKey,
} from "./ProfitabilityMonthlyLedger";
import * as api from "./individualProfitabilityApi";

vi.mock("./individualProfitabilityApi", async (original) => ({
  ...(await original<typeof import("./individualProfitabilityApi")>()),
  getIndividualProfitability: vi.fn(),
  getIndividualProfitabilityPerson: vi.fn(),
}));
vi.mock("../shared/sharedApi");
vi.mock("../clientsops/clientActionsApi");
vi.mock("../salespartsops/partStandardsApi");
vi.mock("../developers/peopleClient");
const query = api.defaultProfitabilityQuery();
function person(id: string): api.ProfitabilityPerson {
  return {
    personId: id,
    name: `인력 ${id}`,
    managementNo: `관리-${id}`,
    companyName: null,
    companyId: null,
    personPartId: null,
    personPartName: null,
    position: null,
    employeeType: "FT",
    hireDate: null,
    jobCode: "DEV",
    gradeCode: null,
    metrics: {
      revenue: "900",
      pay: "500",
      allowance: "0",
      companyInsurance: "0",
      retirementReserve: "0",
      personalCost: "500",
      teamExpense: "20",
      projectExpense: "10",
      totalCost: "530",
      profit: "370",
      selfInsurance: null,
      withholdingTax: null,
      incomeTax: null,
      netPay: null,
      mm: "1.00",
      profitRate: "41.1",
    },
    utilization: "100.0",
    workedMonths: 2,
    employmentMonths: 2,
    utilizedMonths: 2,
    turnkeyOnlyExcluded: false,
    monthBasis: [],
    completeness: {
      costComplete: true,
      revenueComplete: true,
      estimated: false,
      allocationComplete: true,
      issues: [],
    },
  };
}
function response(id: string, q = query): api.IndividualProfitabilityData {
  return {
    items: [person(id)],
    total: 51,
    page: q.page,
    size: q.size,
    context: {
      period: {
        year: 2026,
        baseMonth: "202601",
        fromMonth: "202601",
        toMonth: "202602",
        calculatedAt: "2026-02-28T10:00:00+09:00",
        noData: false,
      },
      view: q.view,
      includeTurnkey: q.includeTurnkey,
    },
    summary: {
      companyHeadcount: 1,
      companyLeaveCount: 0,
      averageHeadcount: "1.0",
      averageUtilizedHeadcount: "1.0",
      averageEarnedHeadcount: "1.0",
      companyUtilization: "100.0",
      companyEarnedRate: "100.0",
      averageRevenue: "900",
      averageCost: "530",
      averageProfit: "370",
      confirmedLossCount: 0,
      unknownCostCount: 0,
      unknownRevenueCount: 0,
      validRevenuePersonCount: 1,
    },
    totals: person(id).metrics,
    contributions: [],
    unknownCount: 0,
    estimatedCount: 0,
    calculationBasis: [],
  };
}
function row(
  id: string,
  assignmentId: string,
  overrides: Partial<api.MonthlyCostRow> = {},
): api.MonthlyCostRow {
  return {
    personId: id,
    projectId: "owned-project",
    assignmentId,
    month: "202601",
    assignmentMm: "0.40",
    freeMm: "0.00",
    personalRevenue: "900",
    laborAllocated: "500",
    teamExpense: "20",
    projectExpense: "10",
    directExpense: "30",
    totalCost: "530",
    profit: "370",
    costClass: "ALLOC",
    contractType: "MO",
    projectSalesPartId: null,
    fixed: "Y",
    projectName: "원본 프로젝트",
    assignmentJobCode: "DEV",
    inputType: "PAID",
    profitRate: "41.1",
    estimated: false,
    costComplete: true,
    revenueComplete: true,
    rowGenerated: false,
    regUserId: null,
    regDttm: null,
    modUserId: null,
    modDttm: null,
    ...overrides,
  };
}
function detail(
  id: string,
  rows = [row(id, "owned-b"), row(id, "owned-a")],
): api.IndividualProfitabilityPersonData {
  return {
    person: person(id),
    context: response(id).context,
    months: [
      {
        personId: id,
        month: "202601",
        rows,
        laborSource: {
          personId: id,
          month: "202601",
          employeeType: "FT",
          directClass: "DRCT",
          source: "마감 사본",
          laborCost: "1000",
          pay: "1000",
          allowance: "0",
          companyInsurance: "0",
          retirementReserve: "0",
          selfInsurance: null,
          withholdingTax: null,
          incomeTax: null,
          netPay: null,
          estimated: false,
        },
        completeness: person(id).completeness,
        knownLaborAllocated: "1000",
        knownPersonalRevenue: "1800",
        assignmentMm: "0.80",
        idleRowGenerated: false,
        indirectRowGenerated: false,
      },
    ],
    issues: [],
    calculationBasis: [],
  };
}
function developer(id: string): people.DeveloperDetail {
  return {
    id,
    managementNo: `관리-${id}`,
    name: `인력 ${id}`,
    companyId: null,
    companyName: null,
    employeeType: "FT",
    grade: null,
    job: "개발",
    status: "WORK",
    useYn: "Y",
    retireDate: null,
    retirementScheduled: false,
    currentProjects: [],
    currentProjectLabel: null,
    withdrawalDate: null,
    monthlyMm: "0.00",
    mmOver: "1.00",
    warnings: [],
    monthlyRate: null,
    revision: "owned-revision",
    gradeCode: null,
    jobCode: "DEV",
    subJobCode: null,
    positionCode: null,
    directClass: "DRCT",
    partId: "owned-part",
    hireDate: null,
    homeAreaCode: null,
    homeAddress: null,
    monthlyPay: null,
    skills: [],
    addressPath: { items: [], homeAddress: null },
    priceHistory: [],
    editPrice: null,
  };
}
function mount() {
  const preferences: PreferenceApi = {
    getPreferences: async () => ({
      items: [
        {
          type: "PAGE_SIZE",
          key: "*",
          value: 50,
          revision: null,
          stored: false,
        },
        {
          type: "AMT_UNIT",
          key: "*",
          value: "원",
          revision: null,
          stored: false,
        },
      ],
    }),
    putPreference: vi.fn(),
    deletePreference: vi.fn(),
  };
  return render(
    <PreferencesProvider api={preferences}>
      <IndividualProfitabilityScreen />
    </PreferencesProvider>,
  );
}
beforeEach(() => {
  vi.resetAllMocks();
  window.innerWidth = 1280;
  vi.mocked(api.getIndividualProfitability).mockImplementation(async (q) =>
    response(q.page === 1 ? "first" : "second", q),
  );
  vi.mocked(api.getIndividualProfitabilityPerson).mockImplementation(
    async (id) => detail(id),
  );
  vi.mocked(people.getDeveloper).mockImplementation(async (id) =>
    developer(id),
  );
  vi.mocked(people.getPeopleCodes).mockResolvedValue({ items: [] });
  vi.mocked(shared.getSharedCodes).mockResolvedValue({ items: [] });
  vi.mocked(clients.getClientOptions).mockResolvedValue({ items: [] });
  vi.mocked(parts.getSalesPartOptions).mockResolvedValue({ items: [] });
});
it("월 복합PK의 같은 프로젝트 두 투입을 정렬하고 원본 금액/원가/노동비를 복제 없이 보여준다", async () => {
  render(
    <ProfitabilityMonthlyLedger personId="owned" query={query} unit="원" jobLabels={{ DEV: "개발 직무" }} />,
  );
  const table = await screen.findByTestId(
    /^profitability-ledger-table-[a-f0-9-]+$/,
  );
  const rows = within(table).getAllByRole("row").slice(1);
  expect(rows).toHaveLength(2);
  expect(rows[0]).toHaveTextContent("2026-01");
  expect(rows[0].querySelector('[data-column="project"]')).toHaveTextContent("└ 2026-01 원본 프로젝트 · 개발 직무");
  expect(rows[0]).toHaveTextContent("owned-a");
  expect(rows[1]).toHaveTextContent("owned-b");
  for (const item of rows) {
    expect(
      item.querySelector('[data-column="laborAllocated"]'),
    ).toHaveTextContent("500");
    expect(item.querySelector('[data-column="teamExpense"]')).toHaveTextContent(
      "20",
    );
    expect(
      item.querySelector('[data-column="projectExpense"]'),
    ).toHaveTextContent("10");
    expect(item.querySelector('[data-column="totalCost"]')).toHaveTextContent(
      "530",
    );
    expect(item.querySelector('[data-column="profit"]')).toHaveTextContent(
      "370",
    );
  }
  expect(
    within(table).queryByRole("columnheader", { name: /급여|공제/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.getAllByTestId(/^profitability-labor-source-.*-202601$/),
  ).toHaveLength(1);
  expect(monthlyCostKey(row("owned", "owned-a"))).not.toBe(
    monthlyCostKey(row("owned", "owned-b")),
  );
  expect(monthlyCostKey(row("owned", "owned-a"))).not.toBe(
    monthlyCostKey(row("owned", "owned-a", { month: "202602" })),
  );
});
it("평균과 누계 상세는 같은 월별 원본이며 IDLE 남는몫/실주/내부/무상/PRE를 구별한다", async () => {
  const rows = [
    row("owned", "paid"),
    row("owned", "IDLE", {
      projectId: "IDLE",
      projectName: null,
      costClass: "IDLE",
      personalRevenue: "0",
      totalCost: "500",
      profit: "-500",
      teamExpense: "0",
      projectExpense: "0",
      profitRate: null,
    }),
    row("owned", "lost", { costClass: "IDLE" }),
    row("owned", "internal", { costClass: "RND" }),
    row("owned", "free", { inputType: "FREE" }),
    row("owned", "pre", { costClass: "PRE" }),
  ];
  vi.mocked(api.getIndividualProfitabilityPerson).mockResolvedValue(
    detail("owned", rows),
  );
  const view = render(
    <ProfitabilityMonthlyLedger personId="owned" query={query} unit="원" />,
  );
  const before = (
    await screen.findByTestId(/^profitability-ledger-table-[a-f0-9-]+$/)
  ).textContent;
  view.rerender(
    <ProfitabilityMonthlyLedger
      personId="owned"
      query={{ ...query, view: "MONTHLY_AVERAGE" }}
      unit="원"
    />,
  );
  await waitFor(() =>
    expect(api.getIndividualProfitabilityPerson).toHaveBeenLastCalledWith(
      "owned",
      expect.objectContaining({ view: "MONTHLY_AVERAGE" }),
      expect.any(AbortSignal),
    ),
  );
  expect(
    (await screen.findByTestId(/^profitability-ledger-table-[a-f0-9-]+$/))
      .textContent,
  ).toBe(before);
  expect(
    screen.getByText("월별 원본 원장 · 월평균 전 금액"),
  ).toBeInTheDocument();
  expect(screen.getByText(/대기 · 남는 몫/)).toBeInTheDocument();
  expect(screen.getByText("└ 2026-01 남는 몫(대기 원가)")).toBeInTheDocument();
  expect(screen.getByText(/실주 대기/)).toBeInTheDocument();
  expect(screen.getByText(/선투입\(PRE\)/)).toBeInTheDocument();
  expect(screen.getByText("내부")).toBeInTheDocument();
  expect(screen.getByText("무상지원")).toBeInTheDocument();
  expect(screen.getByText("−500")).toBeInTheDocument();
});
it("급여 구성과 계산 도움말은 상세 응답의 동일 출처를 사람월 한 번만 표시한다", async () => {
  const data = detail("owned");
  data.months[0].laborSource.allowance = "123";
  data.months[0].laborSource.companyInsurance = "456";
  data.months[0].laborSource.netPay = "789";
  data.calculationBasis = ["상세 서버의 급여·분모·턴키 근거"];
  vi.mocked(api.getIndividualProfitabilityPerson).mockResolvedValue(data);
  render(
    <ProfitabilityMonthlyLedger personId="owned" query={query} unit="원" />,
  );
  const source = (
    await screen.findAllByTestId(/^profitability-labor-source-.*-202601$/)
  )[0];
  expect(source).toHaveTextContent("수당 123");
  expect(source).toHaveTextContent("회사보험 456");
  expect(source).toHaveTextContent("실지급/공급가 789");
  expect(
    screen.getAllByTestId(/^profitability-labor-source-.*-202601$/),
  ).toHaveLength(1);
  await userEvent.click(screen.getByText("이 월원장의 서버 계산 근거"));
  expect(screen.getByText(data.calculationBasis[0])).toBeVisible();
  expect(people.getDeveloper).not.toHaveBeenCalled();
});
it("현재페이지만 모두 펼치며 단일투입도 펼치고 도움말/열설정/접기는 개발자 상세를 열지 않는다", async () => {
  vi.mocked(api.getIndividualProfitabilityPerson).mockImplementation(
    async (id) => detail(id, [row(id, "only")]),
  );
  mount();
  await screen.findByText("인력 first (관리-first)");
  await userEvent.click(screen.getByTestId("profitability-expand-page"));
  await screen.findByTestId(/^profitability-ledger-table-[a-f0-9-]+$/);
  expect(api.getIndividualProfitabilityPerson).toHaveBeenCalledWith(
    "first",
    expect.objectContaining({ page: 1 }),
    expect.any(AbortSignal),
  );
  expect(api.getIndividualProfitabilityPerson).not.toHaveBeenCalledWith(
    "second",
    expect.anything(),
    expect.anything(),
  );
  await userEvent.click(screen.getByText("금액·분류 안내"));
  await userEvent.click(
    screen.getByTestId("profitability-table-columns-button"),
  );
  expect(people.getDeveloper).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByTestId("profitability-collapse-all"));
  expect(
    screen.queryByTestId(/^profitability-monthly-ledger-/),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("profitability-table-next-page"));
  await screen.findByText("인력 second (관리-second)");
  await userEvent.click(screen.getByTestId("profitability-expand-page"));
  await waitFor(() =>
    expect(api.getIndividualProfitabilityPerson).toHaveBeenLastCalledWith(
      "second",
      expect.objectContaining({ page: 2 }),
      expect.any(AbortSignal),
    ),
  );
  expect(screen.getAllByTestId(/^profitability-monthly-ledger-/)).toHaveLength(
    1,
  );
  expect(people.getDeveloper).not.toHaveBeenCalled();
});
it("이름/부모행은 기존 상세를 열고 닫은 뒤 페이지/조건/스크롤/펼침/조회횟수를 유지한다", async () => {
  mount();
  await screen.findByText("인력 first (관리-first)");
  await userEvent.click(screen.getByTestId("profitability-table-next-page"));
  await screen.findByText("인력 second (관리-second)");
  await userEvent.click(
    screen.getByRole("button", { name: "인력 second 펼치기" }),
  );
  await screen.findByTestId(/^profitability-ledger-table-[a-f0-9-]+$/);
  const table = screen.getByTestId("profitability-table");
  const viewport = table.querySelector<HTMLElement>(".sicost-table-viewport")!;
  viewport.scrollLeft = 180;
  viewport.scrollTop = 90;
  const calls = vi.mocked(api.getIndividualProfitability).mock.calls.length;
  const ledgerCalls = vi.mocked(api.getIndividualProfitabilityPerson).mock.calls
    .length;
  await userEvent.click(
    screen.getByRole("button", { name: "인력 second (관리-second)" }),
  );
  await screen.findByText("인력 second", { selector: "h2" });
  expect(people.getDeveloper).toHaveBeenLastCalledWith("second");
  expect(screen.queryByTestId("developer-edit-button")).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("detail-drawer-close"));
  expect(screen.getByTestId("profitability-table")).toBe(table);
  expect(viewport.scrollLeft).toBe(180);
  expect(viewport.scrollTop).toBe(90);
  expect(
    screen.getByRole("button", { name: "인력 second 접기" }),
  ).toHaveAttribute("aria-expanded", "true");
  expect(
    screen.getByText("2", { selector: '[aria-current="page"]' }),
  ).toBeInTheDocument();
  expect(api.getIndividualProfitability).toHaveBeenCalledTimes(calls);
  expect(api.getIndividualProfitabilityPerson).toHaveBeenCalledTimes(
    ledgerCalls,
  );
  await userEvent.click(screen.getByTestId("profitability-table-row-second"));
  await screen.findByTestId("developer-drawer");
  expect(people.getDeveloper).toHaveBeenCalledTimes(2);
});
it("동시에 펼친 두 DEV_ID는 독립 키/원장을 가지며 표식도 충돌하지 않는다", async () => {
  vi.mocked(api.getIndividualProfitability).mockImplementation(async (q) => ({
    ...response("first", q),
    items: [person("first"), person("second")],
    total: 2,
  }));
  mount();
  await screen.findByText("인력 second (관리-second)");
  await userEvent.click(screen.getByTestId("profitability-expand-page"));
  await waitFor(() =>
    expect(
      screen.getAllByTestId(/^profitability-ledger-table-[a-f0-9-]+$/),
    ).toHaveLength(2),
  );
  expect(api.getIndividualProfitabilityPerson).toHaveBeenCalledWith(
    "first",
    expect.anything(),
    expect.any(AbortSignal),
  );
  expect(api.getIndividualProfitabilityPerson).toHaveBeenCalledWith(
    "second",
    expect.anything(),
    expect.any(AbortSignal),
  );
  const ids = screen
    .getAllByTestId(/^profitability-monthly-ledger-/)
    .flatMap((root) => [
      root.getAttribute("data-testid"),
      ...Array.from(root.querySelectorAll("[data-testid]"), (node) =>
        node.getAttribute("data-testid"),
      ),
    ]);
  expect(new Set(ids).size).toBe(ids.length);
  await userEvent.click(
    screen.getByRole("button", { name: "인력 first 접기" }),
  );
  expect(screen.getAllByTestId(/^profitability-monthly-ledger-/)).toHaveLength(
    1,
  );
  expect(
    screen.getByRole("button", { name: "인력 second 접기" }),
  ).toHaveAttribute("aria-expanded", "true");
});
it("기존 상세 호출자는 수정 콜백과 기존 탭을 유지한다", async () => {
  const edit = vi.fn();
  render(<DeveloperDrawer id="owned" onClose={vi.fn()} onEdit={edit} />);
  await screen.findByText("인력 owned", { selector: "h2" });
  expect(
    screen.getByRole("tab", { name: "근무 상태 이력" }),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByTestId("developer-edit-button"));
  expect(edit).toHaveBeenCalledWith(developer("owned"));
});
it("미확인/빈원장과 조회실패를 구별하고 동일문맥 재시도하며 늦은 이전응답은 무시한다", async () => {
  vi.mocked(api.getIndividualProfitabilityPerson).mockRejectedValueOnce(
    new Error("원본 조회 실패"),
  );
  const view = render(
    <ProfitabilityMonthlyLedger personId="owned" query={query} />,
  );
  await screen.findByText("원본 조회 실패");
  expect(screen.queryByText(/개인 원가 자료 없음/)).not.toBeInTheDocument();
  const unknown = detail("owned", []);
  unknown.months[0].completeness = {
    ...person("owned").completeness,
    costComplete: false,
  };
  unknown.issues = [
    {
      personId: "owned",
      month: "202601",
      message: "마감 사본 누락",
      source: "TB_CO_MON_COST",
      relatedScreen: "UI-03",
      sourceAmount: null,
    },
  ];
  vi.mocked(api.getIndividualProfitabilityPerson).mockResolvedValueOnce(
    unknown,
  );
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText(/마감 사본 누락/);
  expect(screen.getByText(/원가 미확인/)).toBeInTheDocument();
  let resolveOld!: (value: api.IndividualProfitabilityPersonData) => void;
  vi.mocked(api.getIndividualProfitabilityPerson).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        resolveOld = resolve;
      }),
  );
  view.rerender(<ProfitabilityMonthlyLedger personId="old" query={query} />);
  await waitFor(() =>
    expect(api.getIndividualProfitabilityPerson).toHaveBeenLastCalledWith(
      "old",
      query,
      expect.any(AbortSignal),
    ),
  );
  vi.mocked(api.getIndividualProfitabilityPerson).mockResolvedValueOnce(
    detail("new", [row("new", "new-row")]),
  );
  view.rerender(<ProfitabilityMonthlyLedger personId="new" query={query} />);
  await screen.findByText("new-row");
  await act(async () => resolveOld(detail("old", [row("old", "old-row")])));
  expect(screen.queryByText("old-row")).not.toBeInTheDocument();
});
it("보기 전환은 새 문맥으로 재조회하고 펼침 버튼은 상세 호출이 없다", async () => {
  mount();
  await screen.findByText("인력 first (관리-first)");
  await userEvent.click(
    screen.getByRole("button", { name: "인력 first 펼치기" }),
  );
  await screen.findByTestId(/^profitability-ledger-table-[a-f0-9-]+$/);
  fireEvent.click(screen.getByRole("button", { name: "월 평균" }));
  await waitFor(() =>
    expect(
      screen.queryByTestId(/^profitability-monthly-ledger-/),
    ).not.toBeInTheDocument(),
  );
  await screen.findByRole("columnheader", { name: "월급여" });
  await userEvent.click(
    screen.getByRole("button", { name: "인력 first 펼치기" }),
  );
  await waitFor(() =>
    expect(api.getIndividualProfitabilityPerson).toHaveBeenLastCalledWith(
      "first",
      expect.objectContaining({ view: "MONTHLY_AVERAGE" }),
      expect.any(AbortSignal),
    ),
  );
  expect(people.getDeveloper).not.toHaveBeenCalled();
});
