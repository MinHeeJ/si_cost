import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { Link, MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
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
vi.mock("../shared/ScreenPeriodBar", () => ({
  ScreenPeriodBar: () => <div>월 선택</div>,
}));
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
      },
    ],
    calculationNotice: "일정·원가 신호는 1차 경보입니다.",
  };
}
const mount = (ui: React.ReactElement, path = "/UI-04") =>
  render(<MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>);
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
    await screen.findByRole("button", { name: "시험 프로젝트" });
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
    await screen.findByRole("button", { name: "시험 프로젝트" });
  });
  it("renders server filter-wide five cards, unknowns and full unbounded budget percentage", async () => {
    mount(<ProjectCostAnalysis />);
    await screen.findByRole("button", { name: "시험 프로젝트" });
    expect(
      screen.getByTestId("analysis-summary-cumulative-revenue"),
    ).toHaveTextContent("–");
    expect(
      screen.getByTestId("analysis-summary-low-margin-count"),
    ).toHaveTextContent("2");
    expect(screen.getByText("소진율 125.0%")).toBeInTheDocument();
    expect(screen.getByText("계획 없음")).toBeInTheDocument();
    expect(screen.getByText("예산 주의")).toHaveClass("sicost-tone-warn");
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
    await screen.findByRole("button", { name: "시험 프로젝트" });
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
    await screen.findByRole("button", { name: "시험 프로젝트" });
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
    await screen.findByRole("button", { name: "시험 프로젝트" });
  });
  it("empty response renders empty state instead of synthetic projects", async () => {
    vi.mocked(api.getProjectCosts).mockResolvedValue({
      ...costs(),
      items: [],
      total: 0,
    });
    mount(<ProjectCostAnalysis />);
    await screen.findByText("조건에 맞는 프로젝트가 없습니다.");
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
      await screen.findByRole("button", { name: "시험 프로젝트" }),
    );
    expect(screen.getByTestId("detail")).toHaveTextContent("owned 2026-06");
    fireEvent.click(screen.getByText("상세 취소"));
    expect(screen.queryByTestId("detail")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "시험 프로젝트" }),
    ).toBeInTheDocument();
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
    await screen.findByRole("button", { name: "시험 프로젝트" });
    expect(
      within(screen.getByTestId("project-cost-table")).getByText("내부"),
    ).toHaveClass("sicost-tone-neutral");
    expect(screen.queryByText("예산 주의")).not.toBeInTheDocument();
  });
});
describe("프로젝트 현황판", () => {
  it("renders all signal reasons with distinct severities, all PMs, unknown and off-page stale banner", async () => {
    mount(<ProjectStatusBoard />, "/UI-31");
    await screen.findByText("진행 프로젝트");
    expect(screen.getByText("일정 늦음 10.0")).toHaveClass("sicost-tone-bad");
    expect(screen.getByText("메모경과 21")).toHaveClass("sicost-tone-warn");
    expect(screen.getByText("담당 하나, 담당 둘")).toBeInTheDocument();

    expect(screen.getByText("원가 미확인")).toBeInTheDocument();
    expect(screen.getByTestId("status-stale-banner")).toHaveTextContent(
      "다른 페이지 미갱신 (30일)",
    );
    expect(screen.queryByText("월 선택")).not.toBeInTheDocument();
  });
  it("issues tab leaves summary unchanged and passes only board filters to its owner", async () => {
    const renderIssues = vi.fn(() => <p>이슈 목록 연결</p>);
    mount(<ProjectStatusBoard renderIssues={renderIssues} />, "/UI-31");
    await screen.findByText("진행 프로젝트");
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
    await screen.findByText("진행 프로젝트");
    expect(screen.getByText(/선투입은 일반 원가 목록/)).toBeInTheDocument();
    expect(screen.queryByTestId("status-cost-owned")).not.toBeInTheDocument();
    expect(screen.getByTestId("status-project-owned")).toHaveAttribute(
      "href",
      expect.stringContaining("tab=progress"),
    );
  });
  it("part options use actual HQ id and submitted light filter is server-driven", async () => {
    mount(<ProjectStatusBoard />, "/UI-31");
    await screen.findByText("진행 프로젝트");
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
    await screen.findByText("조건에 맞는 진행 프로젝트가 없습니다.");
  });
});
