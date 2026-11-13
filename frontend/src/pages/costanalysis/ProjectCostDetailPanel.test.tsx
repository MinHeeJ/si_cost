import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { ProjectCostDetailPanel } from "./ProjectCostDetailPanel";
import {
  ProjectCostSummaryPanel,
  ProjectCostEntryLink,
} from "./ProjectCostSummaryPanel";
import { ProjectCostSimulationPanel } from "./ProjectCostSimulationPanel";
import { ProjectCostDetailRoute } from "./ProjectCostDetailRoute";
import { ApiError } from "../../shared/api";
import * as api from "./projectCostApi";
vi.mock("./projectCostApi", async (original) => ({
  ...(await original<typeof import("./projectCostApi")>()),
  getProjectCostAnalysis: vi.fn(),
  getProjectCostMonths: vi.fn(),
  getProjectCostAssignments: vi.fn(),
  getProjectCostCauses: vi.fn(),
  createProjectCostSimulation: vi.fn(),
}));
const complete: api.Completeness = {
  costComplete: true,
  revenueComplete: true,
  estimated: false,
  issues: [],
  unknownProjectCount: 0,
  rangeStartMonth: "2025-12",
  rangeEndMonth: "2026-01",
  missingBeforeMonth: null,
};
function detail(
  type: api.CostRow["contractType"] = "TK",
  sourceStatus = "ING",
): api.Detail {
  return {
    projectId: "contract",
    month: "2026-01",
    baseDate: "2026-01-31",
    sourceStatus,
    row: {
      projectId: "contract",
      projectName: "계약 원가",
      contractType: type,
      status: "ING",
      actualMm: "2.00",
      cumulativeCost: "230",
      cumulativeRevenue: "287",
      cumulativeProfit: "57",
      cumulativeMargin: "19.9",
      budgetUsage: "28.8",
      forecastMargin: "67.0",
      monthlyMargin: null,
      targetGap: "47.0",
      targetMargin: "20.0",
      alerts: [],
      budgetWarning: false,
    },
    forecast: null,
    simulationDefaults: {
      enabled: true,
      reason: null,
      n: 1,
      c: "115",
      m: 2,
      r: "143",
    },
    revenueExplanation:
      "누적 매출·월별 배분 기준 매출(참고)·개인 단가·청구매출은 서로 다른 지표입니다.",
    completeness: complete,
  };
}
const monthly: api.Months = {
  projectId: "contract",
  month: "2026-01",
  totalCost: "230",
  completeness: complete,
  items: [
    {
      month: "2025-12",
      actualMm: "1.00",
      laborAllocated: "100",
      teamExpense: "7",
      projectExpense: "3",
      directExpense: "10",
      additionalDirectExpense: "5",
      totalCost: "115",
      revenue: "200",
      turnkeyReferenceRevenue: "0",
      referenceMargin: null,
      fixed: true,
      completeness: complete,
    },
  ],
};
const causes: api.Causes = {
  projectId: "contract",
  month: "2026-01",
  items: [
    {
      kind: "EXCESS_MM",
      amount: "172",
      formula: "계획 비례식",
      issues: [],
      targetScreenId: "UI-01",
      projectId: "contract",
    },
    {
      kind: "FREE_SUPPORT",
      amount: "100",
      formula: "FREE 노동비 합",
      issues: [],
      targetScreenId: "UI-04",
      projectId: "contract",
    },
    {
      kind: "OVER_UNIT_COST",
      amount: "20",
      formula: "행 개인단가 기준",
      issues: [],
      targetScreenId: "UI-32",
      projectId: "contract",
    },
  ],
  actualProgress: "25.0",
  progressExcess: "30",
  remainder: "-262",
  showUnexplained: false,
  highlight: true,
  message: "세 원인은 중첩될 수 있습니다.",
  issues: [],
};
const simulation: api.SimulationResult = {
  projectId: "contract",
  month: "2026-01",
  beforeMargin: "67.0",
  afterMargin: "66.0",
  differencePp: "-1.0",
  beforeTargetGap: "47.0",
  afterTargetGap: "46.0",
  virtualRevenue: "1000",
  virtualCost: "340",
  saved: false,
  message: "가상·저장되지 않음",
  completeness: complete,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getProjectCostAnalysis).mockResolvedValue(detail());
  vi.mocked(api.getProjectCostMonths).mockResolvedValue(monthly);
  vi.mocked(api.getProjectCostAssignments).mockResolvedValue({
    projectId: "contract",
    month: "2026-01",
    items: [],
    completeness: complete,
  });
  vi.mocked(api.getProjectCostCauses).mockResolvedValue(causes);
  vi.mocked(api.createProjectCostSimulation).mockResolvedValue(simulation);
});
function Location() {
  const location = useLocation();
  return (
    <output data-testid="location">
      {location.pathname}
      {location.search}
    </output>
  );
}
function panel(tab = "months", close = vi.fn()) {
  return render(
    <MemoryRouter
      initialEntries={[
        `/UI-04?month=2026-01&projectId=contract&costTab=${tab}`,
      ]}
    >
      <ProjectCostDetailPanel
        projectId="contract"
        month="2026-01"
        amountUnit="원"
        onClose={close}
      />
      <Location />
    </MemoryRouter>,
  );
}
it("renders the cross-year monthly source and preserves a zero reference instead of recalculating", async () => {
  panel();
  const table = await screen.findByTestId("cost-months");
  expect(within(table).getByText("2025-12")).toBeInTheDocument();
  expect(within(table).getByText("0")).toBeInTheDocument();
  for (const label of [
    "월",
    "MM",
    "노동비",
    "팀비",
    "경비",
    "추가경비",
    "합계",
    "배분 기준 매출(참고)",
  ])
    expect(within(table).getByText(label)).toBeInTheDocument();
  expect(api.getProjectCostMonths).toHaveBeenCalledWith("contract", "2026-01");
});
it("restores the causes tab, preserves negative remainder, and routes to project plans with context", async () => {
  panel("causes");
  await screen.findByTestId("cost-causes");
  expect(screen.getByText(/나머지 −262/)).toBeInTheDocument();
  expect(
    screen.queryByText("위 세 가지로 설명되지 않습니다"),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("link", { name: "인원 계획" }));
  expect(screen.getByTestId("location")).toHaveTextContent(
    "/UI-01?projectId=contract&tab=plans",
  );
});
it("shows positive unexplained and warning tone from the server only", async () => {
  vi.mocked(api.getProjectCostCauses).mockResolvedValue({
    ...causes,
    remainder: "1",
    showUnexplained: true,
    message: "위 세 가지로 설명되지 않습니다",
  });
  panel("causes");
  expect(await screen.findByText("위 세 가지로 설명되지 않습니다")).toHaveClass(
    "sicost-tone-bad",
  );
});
it("free support opens the assignment tab without changing month or project", async () => {
  panel("causes");
  fireEvent.click(await screen.findByTestId("cost-free-assignments"));
  await screen.findByTestId("cost-assignments");
  expect(screen.getByTestId("location")).toHaveTextContent(
    "month=2026-01&projectId=contract&costTab=assignments",
  );
  expect(api.getProjectCostAssignments).toHaveBeenCalledWith(
    "contract",
    "2026-01",
  );
  expect(screen.getByText("조회된 자료가 없습니다.")).toBeInTheDocument();
});
it("failed tab fetch displays the request id and explicit retry", async () => {
  vi.mocked(api.getProjectCostMonths).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "ERROR",
      message: "조회 실패",
      requestId: "cost-receipt",
    }),
  );
  panel();
  expect(await screen.findByRole("alert")).toHaveTextContent("cost-receipt");
  fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByTestId("cost-months");
});
it("unknown figures keep null display and source reasons", async () => {
  const value = detail();
  value.row.cumulativeCost = null;
  value.completeness = {
    ...complete,
    costComplete: false,
    issues: [
      {
        code: "TB_HR_MON_PAY",
        month: "2026-01",
        personId: "actual",
        assignmentId: null,
        metric: "cumulativeCost",
        message: "급여 원천 없음",
      },
    ],
  };
  vi.mocked(api.getProjectCostAnalysis).mockResolvedValue(value);
  panel();
  expect(await screen.findByText(/급여 원천 없음/)).toBeInTheDocument();
  expect(screen.getByText(/미확인 금액은/)).toBeInTheDocument();
});
it("simulation sends exact decimal strings and only approved fields, then displays the server result", async () => {
  render(<ProjectCostSimulationPanel detail={detail()} />);
  fireEvent.change(screen.getByLabelText("1인 월 원가 (원)"), {
    target: { value: "10" },
  });
  fireEvent.click(screen.getByRole("button", { name: "가상계산" }));
  expect(await screen.findByTestId("cost-simulation-result")).toHaveTextContent(
    "66.0%",
  );
  expect(api.createProjectCostSimulation).toHaveBeenCalledWith("contract", {
    month: "2026-01",
    n: 1,
    c: "10",
    m: 2,
  });
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
});
it("simulation validates each integer and amount without coercion", async () => {
  render(<ProjectCostSimulationPanel detail={detail()} />);
  fireEvent.change(screen.getByTestId("cost-simulation-n"), {
    target: { value: "1.5" },
  });
  fireEvent.change(screen.getByTestId("cost-simulation-m"), {
    target: { value: "0" },
  });
  fireEvent.change(screen.getByTestId("cost-simulation-c"), {
    target: { value: "1e3" },
  });
  fireEvent.click(screen.getByRole("button", { name: "가상계산" }));
  expect(await screen.findAllByRole("alert")).toHaveLength(3);
  expect(api.createProjectCostSimulation).not.toHaveBeenCalled();
});
it("simulation serializes repeated form submissions until the response arrives", async () => {
  let resolve!: (value: api.SimulationResult) => void;
  vi.mocked(api.createProjectCostSimulation).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  render(<ProjectCostSimulationPanel detail={detail()} />);
  const form = screen
    .getByRole("button", { name: "가상계산" })
    .closest("form")!;
  fireEvent.submit(form);
  fireEvent.submit(form);
  expect(api.createProjectCostSimulation).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("button", { name: "가상계산" })).toBeDisabled();
  resolve(simulation);
  await screen.findByTestId("cost-simulation-result");
  expect(screen.getByRole("button", { name: "가상계산" })).toBeEnabled();
});
it("a disabled simulation rejects direct form submission as well as button clicks", () => {
  const value = detail();
  value.simulationDefaults.enabled = false;
  render(<ProjectCostSimulationPanel detail={value} />);
  fireEvent.submit(
    screen.getByRole("button", { name: "가상계산" }).closest("form")!,
  );
  expect(api.createProjectCostSimulation).not.toHaveBeenCalled();
});
it("simulation removes stale results and retains draft on server field failure", async () => {
  vi.mocked(api.createProjectCostSimulation).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_INPUT",
      message: "가상 비용 유효 범위",
      field: "n",
      requestId: "invalid-cost",
    }),
  );
  render(<ProjectCostSimulationPanel detail={detail()} />);
  fireEvent.change(screen.getByTestId("cost-simulation-n"), {
    target: { value: "-2" },
  });
  fireEvent.click(screen.getByRole("button", { name: "가상계산" }));
  await screen.findByText(/문의 번호: invalid-cost/);
  expect(screen.getByTestId("cost-simulation-n")).toHaveValue("-2");
  expect(
    screen.queryByTestId("cost-simulation-result"),
  ).not.toBeInTheDocument();
});
it("simulation null defaults stay blank and disabled state explains why", () => {
  const value = detail();
  value.simulationDefaults = {
    ...value.simulationDefaults,
    enabled: false,
    reason: "단가 없음",
    c: null,
    r: null,
  };
  render(<ProjectCostSimulationPanel detail={value} />);
  expect(screen.getByTestId("cost-simulation-c")).toHaveValue("");
  expect(screen.getByRole("button", { name: "가상계산" })).toBeDisabled();
  expect(screen.getByText("단가 없음")).toBeInTheDocument();
});
it("monthly simulation exposes r and one-month explanation and cancel resets without API writes", () => {
  render(<ProjectCostSimulationPanel detail={detail("MO")} />);
  expect(screen.getByText(/한 달 기준/)).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("cost-simulation-r"), {
    target: { value: "999" },
  });
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(screen.getByTestId("cost-simulation-r")).toHaveValue("143");
  expect(api.createProjectCostSimulation).not.toHaveBeenCalled();
});
it("closing an uncalculated simulation requires confirmation", async () => {
  const close = vi.fn();
  panel("simulation", close);
  await screen.findByTestId("cost-simulation-c");
  fireEvent.change(screen.getByTestId("cost-simulation-c"), {
    target: { value: "10" },
  });
  fireEvent.click(screen.getByRole("button", { name: "닫기" }));
  expect(close).not.toHaveBeenCalled();
  fireEvent.click(await screen.findByRole("button", { name: "계속 작성" }));
  expect(screen.getByTestId("cost-simulation-c")).toHaveValue("10");
  fireEvent.click(screen.getByRole("button", { name: "닫기" }));
  fireEvent.click(await screen.findByRole("button", { name: "나가기" }));
  expect(close).toHaveBeenCalledOnce();
});
it("project summary uses the exact same response and current-month entry", async () => {
  render(
    <MemoryRouter>
      <ProjectCostSummaryPanel projectId="contract" />
      <Location />
    </MemoryRouter>,
  );
  await screen.findByText("프로젝트 원가 · 2026-01");
  expect(api.getProjectCostAnalysis).toHaveBeenCalledWith(
    "contract",
    undefined,
  );
  fireEvent.click(screen.getByRole("link", { name: "원가 분석" }));
  expect(screen.getByTestId("location")).toHaveTextContent(
    "/UI-04?projectId=contract&month=2026-01&costTab=months",
  );
});
it.each([
  ["IN", "ING", "연구개발비"],
  ["TK", "PRE", "선투입 원가"],
  ["TK", "LOSE", "실주 보존 이력"],
] as const)("summary distinguishes %s/%s", async (type, status, label) => {
  vi.mocked(api.getProjectCostAnalysis).mockResolvedValue(detail(type, status));
  render(
    <MemoryRouter>
      <ProjectCostSummaryPanel projectId="contract" month="2026-01" />
    </MemoryRouter>,
  );
  expect(await screen.findByText(`${label} · 2026-01`)).toBeInTheDocument();
});
it("board cost action keeps the server current month and PRE notice", () => {
  render(
    <MemoryRouter initialEntries={["/UI-31?light=위험"]}>
      <ProjectCostEntryLink
        projectId="contract"
        month="2026-01"
        sourceStatus="PRE"
      />
      <Location />
    </MemoryRouter>,
  );
  expect(screen.getByTestId("cost-entry-board")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-31-B138-COST",
  );
  fireEvent.click(screen.getByRole("link", { name: "원가 분석" }));
  expect(screen.getByTestId("location")).toHaveTextContent(
    "/UI-04?projectId=contract&month=2026-01",
  );
});
it("route registration adapter reopens and closes the selected project without losing list parameters", async () => {
  render(
    <MemoryRouter
      initialEntries={["/UI-04?projectId=contract&month=2026-01&page=2"]}
    >
      <ProjectCostDetailRoute />
      <Location />
    </MemoryRouter>,
  );
  await screen.findByTestId("cost-months");
  fireEvent.click(screen.getByRole("button", { name: "닫기" }));
  expect(screen.getByTestId("location")).toHaveTextContent(
    "/UI-04?month=2026-01&page=2",
  );
});
