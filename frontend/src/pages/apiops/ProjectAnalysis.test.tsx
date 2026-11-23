import {
  businessCapabilities,
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import { Link, MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { PermissionContext } from "../../shared/permission/PermissionBoundary";
import * as api from "./projectAnalysisApi";
import * as shared from "../shared/sharedApi";
import * as parts from "../salespartsops/partStandardsApi";
import { ProjectCostAnalysis } from "./ProjectCostAnalysis";
import { ProjectStatusBoard } from "./ProjectStatusBoard";
vi.mock("./projectAnalysisApi", async (original) => ({
  ...(await original<typeof import("./projectAnalysisApi")>()),
  getProjectCosts: vi.fn(),
  getProjectStatusBoard: vi.fn(),
}));
vi.mock("../shared/sharedApi");
vi.mock("../salespartsops/partStandardsApi");
const completeness: api.Completeness = {
  costComplete: true,
  revenueComplete: true,
  estimated: false,
  issues: [],
  unknownProjectCount: 0,
  rangeStartMonth: "2025-01",
  rangeEndMonth: "2026-06",
  missingBeforeMonth: null,
};
function costs(): api.CostPage {
  return {
    month: "2026-06",
    baseDate: "2026-06-30",
    items: [
      {
        projectId: "owned",
        projectName: "시험 프로젝트",
        contractType: "MO",
        status: "ING",
        alerts: [],
        completeness,
        budgetWarning: true,
        primaryCause: null,
        actualMm: "1.00",
        cumulativeCost: "1000001",
        cumulativeRevenue: null,
        planMm: null,
        budgetUsage: "125.0",
      },
    ],
    total: 57,
    page: 1,
    size: 50,
    summary: {
      total: 57,
      cumulativeRevenue: null,
      cumulativeProfit: "-1000001",
      lowMarginCount: 2,
      budgetOverCount: 1,
      researchCost: "1000000",
      completeness,
    },
  };
}
function board(): api.BoardPage {
  return {
    baseDate: "2026-06-30",
    currentMonth: "2026-06",
    items: [
      {
        projectId: "owned",
        projectName: "진행 프로젝트",
        contractType: "TK",
        sourceStatus: "ING",
        light: "위험",
        reasons: [
          {
            kind: "일정 늦음",
            light: "위험",
            value: "10.0",
            threshold: "10.0",
            formula: "계획 − 실적",
          },
          {
            kind: "메모경과",
            light: "주의",
            value: "21",
            threshold: ">14",
            formula: "오늘 − 작성일",
          },
        ],
        costUnknown: true,
        pm: [
          { userId: "user1", userName: "담당 하나" },
          { userId: "user2", userName: "담당 둘" },
        ],
        currentStep: { stepName: "분석", status: "지연" },
        allStepsCompleted: false,
        stepsInitialized: true,
        planBasis: "단계 기준",
        completeness,
        planProgress: "20.0",
        actualProgress: "10.0",
        costUsage: null,
        costProgressGap: null,
        openIssueCount: 3,
        overdueIssueCount: 1,
        lastUpdatedDate: "2026-06-09",
        elapsedDays: 21,
        updateWarning: true,
      },
    ],
    total: 57,
    page: 1,
    size: 50,
    summary: {
      projectCount: 57,
      dangerCount: 4,
      warningCount: 2,
      normalCount: 51,
      openIssueCount: 9,
      openHighIssueCount: 2,
      overdueIssueCount: 1,
      staleProjectCount: 3,
      unknownCostProjectCount: 1,
    },
    staleProjects: [
      {
        projectId: "off-page",
        projectName: "다른 페이지 미갱신",
        elapsedDays: 30,
        lastUpdatedDate: "2026-05-31",
      },
    ],
    calculationNotice: "일정·원가 신호는 1차 경보입니다.",
  };
}
const mount = (ui: React.ReactElement, path = "/UI-04") =>
  render(<MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>);
function RetainedLocation() {
  const location = useLocation();
  return (
    <output data-testid="retained-location">
      {location.pathname}
      {location.search}
    </output>
  );
}
it.each(["cost", "board"] as const)(
  "retained %s settings cannot overwrite another active screen URL",
  async (kind) => {
    const view = (size: 20 | 50) => (
      <MemoryRouter initialEntries={["/UI-11"]}>
        {kind === "cost" ? (
          <ProjectCostAnalysis pageSize={size} />
        ) : (
          <ProjectStatusBoard pageSize={size} />
        )}
        <RetainedLocation />
      </MemoryRouter>
    );
    const { rerender } = render(view(20));
    const load =
      kind === "cost" ? api.getProjectCosts : api.getProjectStatusBoard;
    await waitFor(() => expect(load).toHaveBeenCalled());
    rerender(view(50));
    await waitFor(() =>
      expect(load).toHaveBeenLastCalledWith(
        expect.objectContaining({ size: 50 }),
        expect.any(AbortSignal),
      ),
    );
    expect(screen.getByTestId("retained-location")).toHaveTextContent(
      /^\/UI-11$/,
    );
  },
);
function CostNavigation() {
  const location = useLocation();
  return (
    <>
      <output data-testid="cost-location">{location.search}</output>
      <Link to="/UI-04?month=2026-05&detailProjectId=other&costTab=causes&page=2">
        다른 원가 상세
      </Link>
    </>
  );
}
it.each(["cost", "board"] as const)(
  "late %s page-size saves cannot navigate away from a newly active screen",
  async (kind) => {
    let finish!: () => void;
    const save = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve;
        }),
    );
    render(
      <MemoryRouter initialEntries={[kind === "cost" ? "/UI-04" : "/UI-31"]}>
        {kind === "cost" ? (
          <ProjectCostAnalysis onPageSizeChange={save} />
        ) : (
          <ProjectStatusBoard onPageSizeChange={save} />
        )}
        <Link to="/UI-12">다른 업무로 이동</Link>
        <RetainedLocation />
      </MemoryRouter>,
    );
    const load =
      kind === "cost" ? api.getProjectCosts : api.getProjectStatusBoard;
    await waitFor(() => expect(load).toHaveBeenCalled());
    fireEvent.change(
      await screen.findByRole("combobox", { name: "한 화면 건수" }),
      { target: { value: "100" } },
    );
    await waitFor(() => expect(save).toHaveBeenCalledWith(100));
    fireEvent.click(screen.getByRole("link", { name: "다른 업무로 이동" }));
    await waitFor(() =>
      expect(screen.getByTestId("retained-location")).toHaveTextContent(
        /^\/UI-12$/,
      ),
    );
    await act(async () => finish());
    await waitFor(() =>
      expect(load).toHaveBeenLastCalledWith(
        expect.objectContaining({ size: 100 }),
        expect.any(AbortSignal),
      ),
    );
    expect(screen.getByTestId("retained-location")).toHaveTextContent(
      /^\/UI-12$/,
    );
  },
);
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getProjectCosts).mockResolvedValue(costs());
  vi.mocked(api.getProjectStatusBoard).mockResolvedValue(board());
  vi.mocked(shared.getPeriodContext).mockResolvedValue({
    currentDate: "2026-06-30",
    currentMonth: "2026-06",
    timezone: "Asia/Seoul",
    cashBaseMonth: "2025-01",
    monthClose: { month: "2026-06", status: "작업중" },
  });
  vi.mocked(parts.getSalesPartOptions).mockResolvedValue({
    items: [
      { id: "actual-hq", name: "본사", hqYn: "Y", firstEffectiveMonth: null },
    ],
  });
});
describe("프로젝트 원가 목록", () => {
  it("네 지표 설명은 hover·focus로 열리고 정렬 요청과 함께 유지된다", async () => {
    mount(<ProjectCostAnalysis />);
    await screen.findByTestId("project-cost-open-owned");
    const descriptions = [
      [
        "cumulativeRevenue",
        "턴키는 진행기준(원가÷예산), 월단위·파견은 귀속월 매출 누계",
      ],
      [
        "cumulativeCost",
        "인건비 배분 원가 + 직접 경비(팀비·프로젝트경비·추가 직접 경비)",
      ],
      [
        "forecastMargin",
        "턴키: (현재 계약금액 − 원가 누계 − 남은 기간 예상 원가) ÷ 현재 계약금액. 월단위·파견: 당월 마진율",
      ],
      [
        "budgetUsage",
        "원가 누계 ÷ 예산(프로젝트별 입력값, 기본값 계약금액 × (1 − 목표 마진율))",
      ],
    ];
    for (const [id, description] of descriptions) {
      const info = screen.getByTestId(
        `project-cost-help-${id.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`,
      );
      fireEvent.mouseEnter(info);
      expect(screen.getByRole("tooltip")).toHaveTextContent(description);
      fireEvent.mouseLeave(info);
      expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
      fireEvent.focus(info);
      expect(screen.getByRole("tooltip")).toHaveTextContent(description);
      fireEvent.keyDown(info, { key: "Escape" });
      expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
      if (id !== "forecastMargin") {
        fireEvent.click(screen.getByTestId(`sort-${id}`));
        await waitFor(() =>
          expect(api.getProjectCosts).toHaveBeenCalledWith(
            expect.objectContaining({ sort: id, direction: "ASC" }),
            expect.any(AbortSignal),
          ),
        );
      }
    }
  });
  it.each([
    ["11.35", "11.35%p"],
    ["-3.2", "−3.2%p"],
    ["0.00", "0%p"],
    [null, "–"],
  ])(
    "목표 대비 %s의 수치와 음수색을 유지하며 단위만 붙인다",
    async (gap, text) => {
      const data = costs();
      data.items[0].targetGap = gap;
      vi.mocked(api.getProjectCosts).mockResolvedValue(data);
      mount(<ProjectCostAnalysis />);
      const table = await screen.findByTestId("project-cost-table");
      await waitFor(() =>
        expect(
          table.querySelector('[data-column="targetGap"]'),
        ).toHaveTextContent(text),
      );
      expect(
        within(table).getByRole("columnheader", { name: "목표 대비" }),
      ).toBeInTheDocument();
      const value = table.querySelector('[data-column="targetGap"] span');
      if (gap?.startsWith("-")) expect(value).toHaveClass("analysis-negative");
      else expect(value).not.toHaveClass("analysis-negative");
    },
  );
  it("단위·엑셀은 요약 위에 두고 열 설정은 요약 아래·필터 위에 배치한다", async () => {
    const openColumns = vi.fn();
    mount(
      <ProjectCostAnalysis
        renderColumns={() => <button onClick={openColumns}>열 설정</button>}
        renderExport={() => <button>엑셀</button>}
      />,
    );
    const summary = await screen.findByTestId("analysis-summary-root");
    const columns = screen.getByRole("button", { name: "열 설정" });
    const before = (first: Element, second: Element) =>
      expect(
        first.compareDocumentPosition(second) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    before(screen.getByTestId("project-cost-unit"), summary);
    before(screen.getByRole("button", { name: "엑셀" }), summary);
    before(summary, columns);
    before(columns, screen.getByTestId("project-cost-filters"));
    expect(columns.parentElement).toHaveClass("project-analysis-toolbar");
    fireEvent.click(columns);
    expect(openColumns).toHaveBeenCalledOnce();
  });
  it("공통 월 선택에서 이전 달을 고르면 저장하고 집계 기간을 갱신한다", async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    vi.mocked(api.getProjectCosts).mockImplementation(async (query) => {
      const data = costs();
      if (query.month === "2026-05") {
        data.month = "2026-05";
        data.summary.completeness.rangeEndMonth = "2026-05";
      }
      return data;
    });
    mount(
      <ProjectCostAnalysis onMonthChange={save} />,
      "/UI-04?month=2026-06&projectId=owned&page=2",
    );
    const selector = await screen.findByRole("combobox", { name: "기간" });
    const status = await screen.findByRole("group", { name: "조회 기간 상태" });
    expect(selector).toHaveValue("2026-06");
    expect(screen.queryByText("작업중")).not.toBeInTheDocument();
    expect(status).toHaveTextContent("2025-01 ~ 2026-06");
    fireEvent.click(screen.getByRole("button", { name: "이전 기간" }));
    await waitFor(() => expect(selector).toHaveValue("2026-05"));
    expect(save).toHaveBeenCalledWith("2026-05");
    expect(
      await screen.findByRole("group", { name: "조회 기간 상태" }),
    ).toHaveTextContent("2025-01 ~ 2026-05");
    expect(api.getProjectCosts).toHaveBeenCalledWith(
      { month: "2026-05", page: 1 },
      expect.any(AbortSignal),
    );
  });
  it("URL 상세 변경과 월 이동을 같은 화면 인스턴스에 반영한다", async () => {
    mount(
      <>
        <ProjectCostAnalysis
          renderDetail={(id, month, close) => (
            <section data-testid="detail">
              {id} {month}
              <button onClick={close}>상세 취소</button>
            </section>
          )}
        />
        <CostNavigation />
      </>,
      "/UI-04?month=2026-06&projectId=owned&costTab=months",
    );
    expect(screen.getByTestId("detail")).toHaveTextContent("owned 2026-06");
    fireEvent.click(screen.getByRole("link", { name: "다른 원가 상세" }));
    expect(screen.getByTestId("detail")).toHaveTextContent("other 2026-05");
    await waitFor(() =>
      expect(api.getProjectCosts).toHaveBeenCalledWith(
        { month: "2026-05", page: 2 },
        expect.any(AbortSignal),
      ),
    );
    fireEvent.click(screen.getByRole("button", { name: "상세 취소" }));
    expect(screen.queryByTestId("detail")).not.toBeInTheDocument();
    expect(screen.getByTestId("cost-location")).toHaveTextContent(
      "?month=2026-05&page=2",
    );
  });
  it("기존 projectId/costTab 직접 진입을 닫을 때 재진입 표지를 제거하고 필터는 보존한다", async () => {
    mount(
      <>
        <ProjectCostAnalysis
          renderDetail={(_id, _month, close) => (
            <button onClick={close}>상세 취소</button>
          )}
        />
        <CostNavigation />
      </>,
      "/UI-04?month=2026-06&projectId=owned&costTab=causes&status=ING&page=2",
    );
    fireEvent.click(screen.getByRole("button", { name: "상세 취소" }));
    expect(screen.getByTestId("cost-location")).toHaveTextContent(
      "?month=2026-06&projectId=owned&status=ING&page=2",
    );
    expect(
      screen.queryByRole("button", { name: "상세 취소" }),
    ).not.toBeInTheDocument();
    await screen.findByTestId("project-cost-open-owned");
  });
  it("목록에서 선택한 단위를 원가 상세에 그대로 전달한다", async () => {
    mount(
      <ProjectCostAnalysis
        renderDetail={(_id, _month, _close, unit) => (
          <section data-testid="detail-unit">{unit}</section>
        )}
      />,
      "/UI-04?month=2026-06&detailProjectId=owned",
    );
    expect(screen.getByTestId("detail-unit")).toHaveTextContent("천원");
    fireEvent.change(screen.getByTestId("project-cost-unit"), {
      target: { value: "원" },
    });
    expect(screen.getByTestId("detail-unit")).toHaveTextContent("원");
    await screen.findByTestId("project-cost-open-owned");
  });
  it("renders server filter-wide five cards, unknowns and full unbounded budget percentage", async () => {
    mount(<ProjectCostAnalysis />);
    await screen.findByTestId("project-cost-open-owned");
    expect(
      screen.getByTestId("analysis-summary-cumulative-revenue"),
    ).toHaveTextContent("–");
    expect(
      screen.getByTestId("analysis-summary-low-margin-count"),
    ).toHaveTextContent("2");
    expect(screen.getByText("소진율 125.0%")).toBeInTheDocument();
    expect(screen.getByText("계획 없음")).toBeInTheDocument();
    expect(screen.getByText("예산 주의")).toHaveClass("sicost-tone-warn");
    expect(
      screen
        .getByTestId("analysis-summary-cumulative-profit")
        .querySelector("strong"),
    ).toHaveClass("analysis-negative");
  });
  it("formats per-MM amounts in the selected unit without scaling MM or rates", async () => {
    const data = costs();
    Object.assign(data.items[0], {
      pricePerMm: "2000001",
      costPerMm: "1000002",
      profitPerMm: "999999",
      actualMm: "2.50",
      monthlyMargin: "12.5",
    });
    vi.mocked(api.getProjectCosts).mockResolvedValue(data);
    mount(<ProjectCostAnalysis />);
    await screen.findByTestId("project-cost-open-owned");
    const table = within(screen.getByTestId("project-cost-table"));
    expect(table.getByText("2,000.001")).toBeInTheDocument();
    expect(table.getByText("1,000.002")).toBeInTheDocument();
    expect(table.getByText("999.999")).toBeInTheDocument();
    expect(table.getByText("2.5")).toBeInTheDocument();
    expect(table.getByText("12.5%")).toBeInTheDocument();
    fireEvent.change(screen.getByTestId("project-cost-unit"), {
      target: { value: "원" },
    });
    expect(table.getByText("2,000,001")).toBeInTheDocument();
    expect(table.getByText("2.5")).toBeInTheDocument();
    expect(table.getByText("12.5%")).toBeInTheDocument();
  });
  it("applies drafted filters only on search and resets page and server defaults", async () => {
    mount(<ProjectCostAnalysis />);
    await screen.findByTestId("project-cost-open-owned");
    fireEvent.change(screen.getByTestId("project-cost-type"), {
      target: { value: "TK" },
    });
    fireEvent.click(screen.getByTestId("project-cost-alert"));
    fireEvent.click(screen.getByTestId("project-cost-search"));
    await waitFor(() =>
      expect(api.getProjectCosts).toHaveBeenCalledWith(
        expect.objectContaining({
          contractType: "TK",
          alertOnly: true,
          page: 1,
        }),
        expect.any(AbortSignal),
      ),
    );
    fireEvent.click(screen.getByTestId("project-cost-reset"));
    await waitFor(() =>
      expect(api.getProjectCosts).toHaveBeenCalledWith(
        { month: "2026-06", page: 1, size: 50 },
        expect.any(AbortSignal),
      ),
    );
  });
  it("keeps API error field and request id, supports retry without clearing input", async () => {
    vi.mocked(api.getProjectCosts).mockRejectedValueOnce(
      new ApiError(400, {
        success: false,
        meta: {},
        code: "INVALID_INPUT",
        message: "월 조건을 확인하세요",
        field: "month",
        requestId: "analysis-test",
      }),
    );
    mount(<ProjectCostAnalysis />);
    await screen.findByText(/문의 번호: analysis-test/);
    expect(screen.getByText("month: 월 조건을 확인하세요")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByTestId("project-cost-open-owned");
  });
  it("empty response renders empty state instead of synthetic projects", async () => {
    vi.mocked(api.getProjectCosts).mockResolvedValue({
      ...costs(),
      items: [],
      total: 0,
    });
    mount(<ProjectCostAnalysis />);
    await screen.findByText("조건에 맞는 프로젝트가 없습니다.");
    const reset = screen.getByRole("button", { name: "조건 초기화" });
    expect(reset.closest("td")).not.toBeNull();
    fireEvent.click(reset);
    await waitFor(() =>
      expect(api.getProjectCosts).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1, size: 50 }),
        expect.any(AbortSignal),
      ),
    );
  });
  it("opens selected real identity through the supplied detail and closes without losing month", async () => {
    mount(
      <ProjectCostAnalysis
        renderDetail={(id, month, close) => (
          <section data-testid="detail">
            {id} {month}
            <button onClick={close}>상세 취소</button>
          </section>
        )}
      />,
      "/UI-04?month=2026-06",
    );
    fireEvent.click(
      (await screen.findByTestId("project-cost-open-owned"))
        .closest("tr")!
        .querySelector('[data-column="cumulativeCost"]')!,
    );
    expect(screen.getByTestId("detail")).toHaveTextContent("owned 2026-06");
    fireEvent.click(screen.getByText("상세 취소"));
    expect(screen.queryByTestId("detail")).not.toBeInTheDocument();
    expect(screen.getByTestId("project-cost-open-owned")).toBeInTheDocument();
  });
  it("행 Enter는 상세를 열고 프로젝트명은 일반 글자이며 안내를 표시한다", async () => {
    mount(
      <ProjectCostAnalysis
        renderDetail={(id) => <section data-testid="row-detail">{id}</section>}
      />,
    );
    const name = await screen.findByTestId("project-cost-open-owned");
    expect(name.tagName).toBe("SPAN");
    const row = name.closest("tr")!;
    expect(row).toHaveAttribute("tabindex", "0");
    expect(
      within(screen.getByRole("group", { name: "조회 기간 상태" })).getByText(
        "행을 누르면 월별 원가·시뮬레이션을 볼 수 있습니다.",
      ),
    ).toBeInTheDocument();
    fireEvent.keyDown(row, { key: "Enter" });
    expect(screen.getByTestId("row-detail")).toHaveTextContent("owned");
  });
  it("internal row leaves financial cells empty without changing research cost", async () => {
    const data = costs();
    data.items[0] = {
      ...data.items[0],
      contractType: "IN",
      cumulativeCost: "1000001",
      budgetUsage: null,
      budgetWarning: null,
    };
    vi.mocked(api.getProjectCosts).mockResolvedValue(data);
    mount(<ProjectCostAnalysis />);
    await screen.findByTestId("project-cost-open-owned");
    expect(
      within(screen.getByTestId("project-cost-table")).getByText("내부"),
    ).toHaveClass("sicost-tone-neutral");
    expect(screen.queryByText("예산 주의")).not.toBeInTheDocument();
  });
});
describe("프로젝트 현황판", () => {
  it("현재 단계 셀은 진행 현황으로 이동하고 원가 링크는 자기 경로를 유지한다", async () => {
    mount(
      <>
        <ProjectStatusBoard />
        <RetainedLocation />
      </>,
      "/UI-31?light=위험",
    );
    const row = await screen.findByTestId("status-board-table-row-owned");
    expect(screen.getByText("총 57건")).toBeInTheDocument();
    fireEvent.click(row.querySelector('[data-column="currentStep"]')!);
    expect(screen.getByTestId("retained-location")).toHaveTextContent(
      "/UI-01?projectId=owned&tab=progress",
    );
    fireEvent.click(screen.getByTestId("status-cost-owned"));
    expect(screen.getByTestId("retained-location")).toHaveTextContent(
      "/UI-04?",
    );
    expect(screen.getByTestId("retained-location")).not.toHaveTextContent(
      "tab=progress",
    );
  });
  it("메모 없는 이유 배지·띠·마지막 업데이트를 같은 표현으로 표시한다", async () => {
    const data = board();
    data.items[0] = {
      ...data.items[0],
      lastUpdatedDate: null,
      elapsedDays: 79,
      reasons: [
        {
          kind: "주간 메모 없음",
          light: "주의",
          value: "79",
          threshold: ">14",
          formula: "계약 시작 기준",
        },
      ],
    };
    data.staleProjects = [
      {
        projectId: "owned",
        projectName: "진행 프로젝트",
        elapsedDays: 79,
        lastUpdatedDate: null,
      },
    ];
    vi.mocked(api.getProjectStatusBoard).mockResolvedValue(data);
    mount(<ProjectStatusBoard />, "/UI-31");
    const row = await screen.findByTestId("status-board-table-row-owned");
    expect(
      within(row).getAllByText("주간 메모 없음(계약 시작 79일)"),
    ).toHaveLength(2);
    expect(screen.getByTestId("status-stale-banner")).toHaveTextContent(
      "주간 메모 없음(계약 시작 79일)",
    );
    expect(screen.queryByText(/메모경과/)).not.toBeInTheDocument();
  });
  it("renders all signal reasons with distinct severities, all PMs, unknown and off-page stale banner", async () => {
    mount(<ProjectStatusBoard />, "/UI-31");
    await screen.findByTestId("status-board-table-row-owned");
    expect(screen.getByTestId("project-status-board")).not.toHaveClass(
      "project-cost-analysis",
    );
    expect(screen.getByText("일정 늦음 10.0")).toHaveClass("sicost-tone-bad");
    expect(screen.getByText("메모경과 21")).toHaveClass("sicost-tone-warn");
    expect(screen.getByText("담당 하나, 담당 둘")).toBeInTheDocument();

    expect(screen.getByText("원가 미확인")).toBeInTheDocument();
    expect(screen.getByTestId("status-stale-banner")).toHaveTextContent(
      "다른 페이지 미갱신 (메모경과 30)",
    );
    expect(screen.queryByText("월 선택")).not.toBeInTheDocument();
  });
  it("issues tab leaves summary unchanged and passes only board filters to its owner", async () => {
    const renderIssues = vi.fn(() => <p>이슈 목록 연결</p>);
    mount(<ProjectStatusBoard renderIssues={renderIssues} />, "/UI-31");
    await screen.findByTestId("status-board-table-row-owned");
    fireEvent.click(screen.getByTestId("status-issues-tab"));
    expect(screen.getByText("이슈 목록 연결")).toBeInTheDocument();
    expect(
      screen.getByTestId("analysis-summary-danger-count"),
    ).toHaveTextContent("4");
    expect(renderIssues).toHaveBeenCalledWith({}, expect.any(Function));
  });
  it("PRE has progress link and explanation, but no misleading empty cost-list link", async () => {
    const data = board();
    data.items[0] = { ...data.items[0], sourceStatus: "PRE" };
    vi.mocked(api.getProjectStatusBoard).mockResolvedValue(data);
    mount(<ProjectStatusBoard />, "/UI-31");
    await screen.findByTestId("status-board-table-row-owned");
    expect(screen.getByText(/선투입은 일반 원가 목록/)).toBeInTheDocument();
    expect(screen.queryByTestId("status-cost-owned")).not.toBeInTheDocument();
    expect(screen.getByTestId("status-project-owned")).toHaveAttribute(
      "href",
      expect.stringContaining("tab=progress"),
    );
  });
  it("UI-01과 UI-04 조회권한이 없으면 현황은 표시하고 상세·원가 링크는 숨긴다", async () => {
    const data = board();
    data.items[0] = { ...data.items[0], currentStep: null };
    vi.mocked(api.getProjectStatusBoard).mockResolvedValue(data);
    mount(
      <PermissionContext.Provider
        value={{ ...businessCapabilities, screens: { "UI-31": ["INQ"] } }}
      >
        <ProjectStatusBoard />
      </PermissionContext.Provider>,
      "/UI-31",
    );
    await screen.findByTestId("status-board-table-row-owned");
    expect(screen.getByText("기본 단계 미생성")).toBeInTheDocument();
    expect(
      screen.queryByTestId("status-project-owned"),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId("status-stage-owned")).not.toBeInTheDocument();
    expect(screen.queryByTestId("status-cost-owned")).not.toBeInTheDocument();
  });
  it("part options use actual HQ id and submitted light filter is server-driven", async () => {
    mount(<ProjectStatusBoard />, "/UI-31");
    await screen.findByTestId("status-board-table-row-owned");
    await screen.findByText("본사 (본사 직접 영업)");
    fireEvent.change(screen.getByTestId("status-part"), {
      target: { value: "actual-hq" },
    });
    fireEvent.change(screen.getByTestId("status-light"), {
      target: { value: "주의" },
    });
    fireEvent.click(screen.getByTestId("status-search"));
    await waitFor(() =>
      expect(api.getProjectStatusBoard).toHaveBeenCalledWith(
        { partId: "actual-hq", light: "주의", page: 1 },
        expect.any(AbortSignal),
      ),
    );
  });
  it("failed request exposes retry and empty response exposes empty state", async () => {
    vi.mocked(api.getProjectStatusBoard).mockRejectedValueOnce(
      new Error("현황 조회 실패"),
    );
    mount(<ProjectStatusBoard />, "/UI-31");
    await screen.findByText("현황 조회 실패");
    vi.mocked(api.getProjectStatusBoard).mockResolvedValue({
      ...board(),
      items: [],
      total: 0,
    });
    fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    expect(
      screen.getByRole("button", { name: "조건 초기화" }).closest("td"),
    ).not.toBeNull();
  });
});

it("원가 단위와 페이지 크기는 공통 저장 콜백을 거친 후 조회에 반영한다", async () => {
  const unit = vi.fn().mockResolvedValue(undefined),
    size = vi.fn().mockResolvedValue(undefined);
  mount(
    <ProjectCostAnalysis
      amountUnit="백만원"
      pageSize={20}
      onAmountUnitChange={unit}
      onPageSizeChange={size}
    />,
  );
  await screen.findByTestId("project-cost-unit");
  expect(screen.getByTestId("project-cost-unit")).toHaveValue("백만원");
  fireEvent.change(screen.getByTestId("project-cost-unit"), {
    target: { value: "원" },
  });
  await waitFor(() => expect(unit).toHaveBeenCalledWith("원"));
  await waitFor(() =>
    expect(screen.getByTestId("project-cost-unit")).toHaveValue("원"),
  );
  const pageSize = screen.getByRole("combobox", { name: "한 화면 건수" });
  fireEvent.change(pageSize, { target: { value: "100" } });
  await waitFor(() => expect(size).toHaveBeenCalledWith(100));
  await waitFor(() =>
    expect(api.getProjectCosts).toHaveBeenLastCalledWith(
      expect.objectContaining({ size: 100, page: 1 }),
      expect.any(AbortSignal),
    ),
  );
});
