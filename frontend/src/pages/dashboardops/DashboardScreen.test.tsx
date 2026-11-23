import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { DashboardScreen } from "./DashboardScreen";
import { SharedScreenContext } from "../shared/SharedScreenContext";
import { dashboardFixture } from "./dashboardFixture";
import * as api from "./dashboardApi";
import { dashboardMessage } from "./dashboardPresentation";
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
vi.mock("./dashboardApi", () => ({
  getDashboard: vi.fn(),
  getDashboardExport: vi.fn(),
}));
let data: ReturnType<typeof dashboardFixture>;
beforeEach(() => {
  vi.resetAllMocks();
  window.innerWidth = 1280;
  data = dashboardFixture();
  vi.mocked(api.getDashboard).mockImplementation(async () => data);
  vi.mocked(api.getDashboardExport).mockResolvedValue();
});
afterEach(() => vi.unstubAllGlobals());
function Destination() {
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <p data-testid="destination">
      {location.pathname}
      {location.search}{" "}
      <output data-testid="destination-state">
        {JSON.stringify(location.state)}
      </output>
      <button onClick={() => navigate(-1)}>뒤로</button>
    </p>
  );
}
function ready() {
  return render(
    <MemoryRouter initialEntries={["/UI-16"]}>
      <SharedScreenContext.Provider
        value={{
          menus: [
            "UI-07",
            "UI-08",
            "UI-13",
            "UI-14",
            "UI-04",
            "UI-21",
            "UI-28",
            "UI-18",
            "UI-10",
            "UI-12",
            "UI-05",
            "UI-01",
            "UI-31",
            "UI-29",
          ].map((screenId) => ({
            menuId: screenId,
            parentId: null,
            name: screenId,
            screenId,
            sortOrder: 1,
            icon: null,
            pcOnly: false,
            children: [],
          })),
          favorites: [],
          star: () => {},
          pending: false,
        }}
      >
        <Routes>
          <Route path="/UI-16" element={<DashboardScreen />} />
          {[
            "UI-07",
            "UI-08",
            "UI-13",
            "UI-14",
            "UI-04",
            "UI-21",
            "UI-28",
            "UI-18",
            "UI-10",
            "UI-12",
            "UI-05",
            "UI-01",
            "UI-31",
            "UI-29",
          ].map((id) => (
            <Route key={id} path={`/${id}`} element={<Destination />} />
          ))}
        </Routes>
      </SharedScreenContext.Provider>
    </MemoryRouter>,
  );
}
it("keeps restricted and hidden dashboard consumers mounted when private sections are omitted", async () => {
  const restricted: api.DashboardData = {
    ...data,
    scope: "PART",
    closeProgress: undefined,
    projectAlerts: undefined,
    salesCostTrend: undefined,
    salesParts: undefined,
    cashForecast: undefined,
    metrics: data.metrics.filter((card) =>
      ["UTILIZATION", "RECEIVABLE"].includes(card.metricCode),
    ),
  };
  vi.mocked(api.getDashboard).mockResolvedValue(restricted);
  ready();
  await screen.findByTestId("dashboard-metric-utilization");
  expect(screen.getByText(/^허용된 담당 범위 · 단위:/)).toBeVisible();
  for (const id of ["dashboard-trend", "dashboard-parts", "dashboard-cash"])
    expect(screen.queryByTestId(id)).not.toBeInTheDocument();
  expect(screen.getByTestId("dashboard-actions")).toBeVisible();
});
it("renders the five summaries and all twelve, six and three rows without computing financial values in the browser", async () => {
  ready();
  const metrics = await screen.findByTestId("dashboard-metric-sales");
  expect(metrics).toHaveTextContent("−1.001 천원");
  expect(screen.getByTestId("dashboard-metric-utilization")).toHaveTextContent(
    "0.0%",
  );
  expect(screen.getByTestId("dashboard-metric-sales")).toHaveTextContent(
    "전월 대비 –",
  );
  await userEvent.click(screen.getByTestId("dashboard-trend-details"));
  expect(
    within(
      screen.getByRole("table", { name: "매출·원가 추이 숫자표" }),
    ).getAllByRole("row"),
  ).toHaveLength(4);
  await userEvent.click(screen.getByTestId("dashboard-trend-all"));
  expect(
    within(
      screen.getByRole("table", { name: "매출·원가 추이 숫자표" }),
    ).getAllByRole("row"),
  ).toHaveLength(13);
  expect(
    within(
      screen.getByRole("table", { name: "인력 수급 예측 숫자표" }),
    ).getAllByRole("row"),
  ).toHaveLength(7);
  expect(screen.getByTestId("dashboard-trend-2026-08")).toHaveTextContent("–");
  expect(screen.getByTestId("dashboard-cash-2027-02")).toHaveTextContent(
    "월말 잔액 –",
  );
  expect(screen.getByTestId("dashboard-actions")).toHaveTextContent(
    "전체 1급함 1",
  );
  expect(screen.getByRole("img")).toHaveAccessibleName(
    "매출·매출원가 실선과 회사 전체 매출목표 점선",
  );
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
});
it("keeps source Q separate from current D/M and downloads the displayed Q", async () => {
  data.selectedMonth = "2026-06";
  data.selectedMonthCloseStatus = "CLOSE";
  data.closeProgress.snapshot = true;
  ready();
  await screen.findByText(/선택월 2026-06/);
  expect(screen.getByText(/현재 미수·할일/)).toHaveTextContent("2026-07");
  expect(screen.getByTestId("dashboard-close")).toHaveTextContent(
    "당시 마감 사본",
  );
  await userEvent.click(screen.getByTestId("dashboard-export"));
  await waitFor(() =>
    expect(api.getDashboardExport).toHaveBeenCalledWith("2026-06"),
  );
  expect(api.getDashboardExport).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("status")).toHaveTextContent("다운로드가 완료");
});
it("shows loading and prevents export until the read completes", async () => {
  let done!: (value: api.DashboardData) => void;
  vi.mocked(api.getDashboard).mockReturnValue(
    new Promise((resolve) => {
      done = resolve;
    }),
  );
  ready();
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  expect(screen.getByTestId("dashboard-export")).toBeDisabled();
  await act(async () => done(data));
  expect(
    await screen.findByTestId("dashboard-metric-sales"),
  ).toBeInTheDocument();
});
it("distinguishes successful empty actions from unknown or failed actions", async () => {
  data.actions = [];
  data.actionTypeCount = 0;
  data.urgentActionTypeCount = 0;
  data.actionsBasis.status = "EMPTY";
  ready();
  expect(await screen.findByText("확인할 일이 없습니다")).toBeInTheDocument();
});
it("preserves partial failures and nulls instead of presenting an empty success or replacing other sections with zeros", async () => {
  data.actions = [];
  data.actionsBasis = {
    ...data.actionsBasis,
    status: "FAILED",
    reasons: ["청구 원천 조회 실패"],
  };
  data.actionTypeCount = null;
  data.urgentActionTypeCount = null;
  data.cashBasis = {
    ...data.cashBasis,
    status: "UNCONFIRMED",
    reasons: ["자금 기준 미확인"],
  };
  ready();
  expect(await screen.findByText("청구 원천 조회 실패")).toBeInTheDocument();
  expect(screen.queryByText("확인할 일이 없습니다")).not.toBeInTheDocument();
  expect(screen.getByTestId("dashboard-actions")).toHaveTextContent("전체 –");
  expect(screen.getByTestId("dashboard-cash")).toHaveTextContent(
    "자금 기준 미확인",
  );
  expect(screen.getByTestId("dashboard-metric-sales")).toHaveTextContent(
    "−1.001",
  );
});
it("renders urgency and all alert severity tones from server state without changing the budget threshold", async () => {
  data.projectAlerts.push(
    { ...data.projectAlerts[0], alertType: "LOW_MARGIN", projectId: "00002" },
    {
      ...data.projectAlerts[0],
      alertType: "BUDGET_WARNING",
      projectId: "00003",
    },
  );
  ready();
  expect(
    await screen.findByTestId("dashboard-action-overdue-receivable"),
  ).toHaveClass("urgent");
  expect(screen.getByText("예산 초과")).toHaveClass("sicost-tone-bad");
  expect(screen.getByText("저마진")).toHaveClass("sicost-tone-warn");
  expect(screen.getByText("예산 주의")).toHaveClass("sicost-tone-warn");
});
it("reloads failed reads only on request and leaves export errors visible without a success notice", async () => {
  vi.mocked(api.getDashboard).mockRejectedValueOnce(
    new Error("조회에 실패했습니다."),
  );
  ready();
  expect(await screen.findByRole("alert")).toHaveTextContent("조회에 실패");
  await userEvent.click(screen.getByTestId("dashboard-retry"));
  await screen.findByTestId("dashboard-metric-sales");
  vi.mocked(api.getDashboardExport).mockRejectedValue(
    new Error("접근기록 저장 실패"),
  );
  await userEvent.click(screen.getByTestId("dashboard-export"));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "접근기록 저장 실패",
  );
  expect(
    screen.queryByText("엑셀 다운로드가 완료됐습니다."),
  ).not.toBeInTheDocument();
});
it("deduplicates concurrent downloads", async () => {
  let finish!: () => void;
  vi.mocked(api.getDashboardExport).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  ready();
  await screen.findByTestId("dashboard-metric-sales");
  fireEvent.click(screen.getByTestId("dashboard-export"));
  fireEvent.click(screen.getByTestId("dashboard-export"));
  expect(api.getDashboardExport).toHaveBeenCalledTimes(1);
  await act(async () => finish());
});
it("keeps mobile downloads hidden without introducing role-based 403 controls", async () => {
  window.innerWidth = 600;
  ready();
  await screen.findByTestId("dashboard-metric-sales");
  expect(screen.queryByTestId("dashboard-export")).not.toBeInTheDocument();
  expect(screen.getByTestId("dashboard-metric-receivable")).toBeEnabled();
});
it("uses the existing period event, ignores other screens and refreshes after business commits", async () => {
  ready();
  await screen.findByTestId("dashboard-metric-sales");
  act(() =>
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-16",
          value: { type: "YEAR_MONTH", value: "2026-06" },
        },
      }),
    ),
  );
  await waitFor(() =>
    expect(api.getDashboard).toHaveBeenLastCalledWith(
      "2026-06",
      expect.any(AbortSignal),
    ),
  );
  act(() => window.dispatchEvent(new CustomEvent("sicost:bills-committed")));
  await waitFor(() => expect(api.getDashboard).toHaveBeenCalledTimes(3));
});
it("opens the actual receivable surface with the overdue filter rather than modifying period preferences", async () => {
  ready();
  await userEvent.click(
    await screen.findByTestId("dashboard-action-overdue-receivable"),
  );
  expect(screen.getByTestId("destination")).toHaveTextContent("/UI-13");
  expect(screen.getByTestId("destination")).toHaveTextContent(
    '"filter":"overdue"',
  );
});
it("opens the selected project cost detail by its real identifier", async () => {
  ready();
  await userEvent.click(
    await screen.findByTestId("dashboard-alert-00001-budget-exceeded"),
  );
  expect(screen.getByTestId("destination")).toHaveTextContent(
    "/UI-04?detailProjectId=00001&month=2026-07",
  );
});
it("rejects an external target even when the API supplies it as a link", async () => {
  data.metrics[0].link = { screenId: "https://evil.invalid", linkKey: null };
  ready();
  await userEvent.click(await screen.findByTestId("dashboard-metric-sales"));
  expect(screen.getByRole("status")).toHaveTextContent(
    "대상 화면을 사용할 수 없습니다.",
  );
  expect(screen.queryByTestId("destination")).not.toBeInTheDocument();
});

it.each([null, 0])("마감점검 미확인과 실제0을 구별한다: %s", async (value) => {
  data.closeProgress.passedCheckCount = 5;
  data.closeProgress.remainingChecks[0].count = value;
  data.closeProgress.remainingChecks[0].status =
    value === null ? "UNKNOWN" : "WARN";
  data.closeProgress.remainingChecks[0].reason =
    value === null ? "원천 미확인" : "실제 0건";
  ready();
  const panel = await screen.findByTestId("dashboard-close");
  expect(panel).toHaveTextContent(
    value === null ? "MM 초과 –건" : "MM 초과 0건",
  );
  expect(panel).toHaveTextContent("점검 항목 6개 중 5개 통과");
  expect(panel).toHaveTextContent(value === null ? "원천 미확인" : "실제 0건");
});
it("과거 선택월 카드 제목을 유지하며 빈 파이프라인에서도 이동한다", async () => {
  data.selectedMonth = "2026-06";
  ready();
  expect(await screen.findByTestId("dashboard-metric-sales")).toHaveTextContent(
    "06월 매출",
  );
  expect(screen.getByTestId("dashboard-pipeline")).toHaveTextContent("자세히");
});

it("영업파트별 현황에서 선택월을 유지하며 UI-07로 이동한다", async () => {
  data.selectedMonth = "2026-06";
  ready();
  const parts = await screen.findByTestId("dashboard-parts");
  expect(within(parts).getByRole("heading")).toHaveTextContent("6월 영업파트별 현황");
  await userEvent.click(within(parts).getByRole("button", { name: "자세히" }));
  expect(screen.getByTestId("destination")).toHaveTextContent("/UI-07");
  expect(JSON.parse(screen.getByTestId("destination-state").textContent!)).toMatchObject({
    fromDashboard: true,
    month: "2026-06",
    year: "2026",
  });
});

const cardTargets = [
  ["sales", "UI-08"],
  ["operating-profit", "UI-08"],
  ["ytd-operating-profit", "UI-18"],
  ["utilization", "UI-28"],
  ["receivable", "UI-13"],
] as const;
const actionCodes = [
  "OVERDUE_RECEIVABLE",
  "CORPORATE_TAX_DUE",
  "CASH_SHORTAGE",
  "STAFF_SHORTAGE",
  "MM_OVER",
  "PRECONTRACT_ASSIGNMENT",
  "WITHDRAWAL_DUE",
  "SALES_PART_MISSING",
  "PROJECT_RISK",
  "ISSUE_OVERDUE",
  "WEEK_MEMO_STALE",
  "BILLING_MISSING",
];
it.each([0, 5, 6, 12])(
  "확인할 일 %s유형의 요약·전체목록·닫기 초점을 보존한다",
  async (size) => {
    data.actions = actionCodes.slice(0, size).map((actionCode, index) => ({
      ...data.actions[0],
      actionCode,
      urgent: index >= size - Math.min(size, 4),
    }));
    data.actionTypeCount = size;
    data.urgentActionTypeCount = Math.min(size, 4);
    ready();
    const panel = await screen.findByTestId("dashboard-actions");
    expect(panel).toHaveTextContent(`전체 ${size}`);
    expect(panel).toHaveTextContent(`급함 ${Math.min(size, 4)}`);
    expect(within(panel).queryAllByTestId(/^dashboard-action-/)).toHaveLength(
      Math.min(size, 5),
    );
    const expected = [...data.actions].sort(
      (a, b) => Number(b.urgent) - Number(a.urgent),
    );
    expect(
      within(panel)
        .queryAllByTestId(/^dashboard-action-/)
        .map((node) => node.dataset.testid),
    ).toEqual(
      expected
        .slice(0, 5)
        .map(
          (action) =>
            `dashboard-action-${action.actionCode.toLowerCase().replaceAll("_", "-")}`,
        ),
    );
    if (size <= 5) {
      expect(
        screen.queryByTestId("dashboard-actions-all"),
      ).not.toBeInTheDocument();
      if (size === 0) expect(panel).toHaveTextContent("확인할 일이 없습니다");
      return;
    }
    const open = screen.getByTestId("dashboard-actions-all");
    await userEvent.click(open);
    expect(
      within(screen.getByRole("dialog")).getAllByTestId(
        /^dashboard-modal-action-/,
      ),
    ).toHaveLength(size);
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(open).toHaveFocus();
    await userEvent.click(open);
    await userEvent.click(screen.getByTestId("dashboard-actions-close"));
    expect(open).toHaveFocus();
  },
);
it("전체 목록의 선택월 청구 누락은 카드와 같은 탭·월로 이동한다", async () => {
  const template = data.actions[0];
  data.actions = actionCodes.map((actionCode) => ({
    ...template,
    actionCode,
    urgent: false,
    link: { screenId: "UI-13", linkKey: "checks" },
    basis: { ...template.basis, basisMonth: "2026-06" },
  }));
  ready();
  await userEvent.click(await screen.findByTestId("dashboard-actions-all"));
  await userEvent.click(
    screen.getByTestId("dashboard-modal-action-billing-missing"),
  );
  expect(screen.getByTestId("destination")).toHaveTextContent("/UI-13");
  expect(
    JSON.parse(screen.getByTestId("destination-state").textContent!),
  ).toMatchObject({ month: "2026-06", section: "checks", linkKey: "checks" });
});
it("실제 점검 수만큼 구간을 만들고 통과한 구간만 채운다", async () => {
  ready();
  const progress = await screen.findByRole("progressbar", {
    name: "마감 점검 진행",
  });
  expect(progress).toHaveAttribute(
    "aria-valuemax",
    String(data.closeProgress.totalCheckCount),
  );
  expect(progress.children).toHaveLength(data.closeProgress.totalCheckCount);
  expect(progress.querySelectorAll(".passed")).toHaveLength(
    data.closeProgress.passedCheckCount!,
  );
});
it("로딩 스켈레톤은 장식이며 초점 가능한 요소를 만들지 않는다", () => {
  vi.mocked(api.getDashboard).mockReturnValue(new Promise(() => {}));
  ready();
  const skeleton = screen.getByTestId("dashboard-skeleton");
  expect(skeleton).toHaveAttribute("aria-hidden", "true");
  expect(
    skeleton.querySelector("button,input,a,select,textarea,[tabindex]"),
  ).toBeNull();
});
it("연도를 넘는 최근 3개월과 전체 기간은 원본 값·화면 단위를 보존한다", async () => {
  data.selectedMonth = "2027-01";
  data.salesCostTrend = ["2026-10", "2026-11", "2026-12", "2027-01"].map(
    (month, index) => ({
      ...data.salesCostTrend![0],
      month,
      salesAmount: index === 2 ? null : data.salesCostTrend![0].salesAmount,
    }),
  );
  ready();
  await userEvent.click(await screen.findByTestId("dashboard-trend-details"));
  const table = screen.getByRole("table", { name: "매출·원가 추이 숫자표" });
  expect(within(table).getAllByRole("row")).toHaveLength(4);
  expect(table).not.toHaveTextContent("2026-10");
  expect(table).toHaveTextContent("2026-11");
  expect(table).toHaveTextContent("2027-01");
  expect(screen.getByTestId("dashboard-trend-2026-12")).toHaveTextContent("–");
  expect(screen.getByTestId("dashboard-trend")).toHaveTextContent("단위: 천원");
  expect(screen.getByTestId("dashboard-trend")).not.toHaveTextContent("백만원");
  await userEvent.click(screen.getByTestId("dashboard-trend-all"));
  expect(within(table).getAllByRole("row")).toHaveLength(5);
  expect(screen.getByTestId("dashboard-metric-sales")).toHaveTextContent(
    "01월 매출",
  );
  expect(api.getDashboard).toHaveBeenCalledTimes(1);
});
it("인쇄 이벤트와 복귀 포커스는 현재 표시 결과를 추가 조회하지 않는다", async () => {
  let clock = 100_000;
  const now = vi.spyOn(Date, "now").mockImplementation(() => clock);
  try {
    ready();
    await screen.findByTestId("dashboard-metric-sales");
    await waitFor(() =>
      expect(screen.getByTestId("dashboard-screen")).toHaveAttribute(
        "aria-busy",
        "false",
      ),
    );
    const before = cardTargets.map(
      ([id]) => screen.getByTestId(`dashboard-metric-${id}`).textContent,
    );
    clock += 31_000;
    act(() => {
      window.dispatchEvent(new Event("beforeprint"));
      window.dispatchEvent(new Event("focus"));
    });
    clock += 60_000;
    act(() => {
      window.dispatchEvent(new Event("afterprint"));
      window.dispatchEvent(new Event("focus"));
    });
    expect(api.getDashboard).toHaveBeenCalledTimes(1);
    expect(
      cardTargets.map(
        ([id]) => screen.getByTestId(`dashboard-metric-${id}`).textContent,
      ),
    ).toEqual(before);
    expect(screen.getByTestId("dashboard-metric-receivable")).toHaveTextContent(
      "기준일 2026-07-15",
    );
  } finally {
    now.mockRestore();
  }
});
it("모든 위젯·배지·툴팁에서 내부 원천을 제거하고 실제 기준월과 업무 이름을 보존한다", async () => {
  data.selectedMonth = "2026-06";
  data.salesCostTrendBasis = {
    ...data.salesCostTrendBasis,
    basisMonth: "2026-06",
    sourceService: "OperatingProfit",
  };
  data.supplyBasis = {
    ...data.supplyBasis,
    basisMonth: "2026-07",
    sourceService: "WorkforceForecast/SalesPipeline",
  };
  data.pipeline.basis = {
    ...data.pipeline.basis,
    basisMonth: "2026-08",
    sourceService: "WorkforceForecast/SalesPipeline",
  };
  data.actionsBasis = {
    ...data.actionsBasis,
    sourceService:
      "Receivables/CorporateTax/CashFlow/Workforce/Project/MonthClose",
  };
  data.closeProgress.remainingChecks = [
    "BILL",
    "SETL",
    "BILL_MISS",
    "NEW_CHECK",
  ].map((checkCode, i) => ({
    checkCode,
    status: "WARN",
    count: i + 1,
    reason: "상세 상태 확인",
  }));
  data.projectAlerts[0].projectName = "API/ALL Project";
  data.pipeline.startingSoon = [
    {
      opportunityId: "opportunity",
      opportunityName: "SalesPipeline/ALL 기회",
      startMonth: "2026-08",
      expectedAmount: "1001",
      winProbabilityRate: "50",
      link: { screenId: "UI-29", linkKey: null },
    },
  ];
  ready();
  const root = await screen.findByTestId("dashboard-screen");
  await screen.findByTestId("dashboard-trend");
  expect(screen.getByTestId("dashboard-trend")).toHaveTextContent(
    "조회 기준월 2026-06",
  );
  expect(screen.getByTestId("dashboard-supply")).not.toHaveTextContent(
    "예측 기준월",
  );
  expect(screen.getByTestId("dashboard-alerts")).not.toHaveTextContent(
    "기준월",
  );
  expect(screen.getByTestId("dashboard-pipeline")).not.toHaveTextContent(
    "집계 기준월",
  );
  expect(root).toHaveTextContent("세금계산서 번호 미입력 1건");
  expect(root).toHaveTextContent("영업 파트 정산 2건");
  expect(root).toHaveTextContent("청구 누락 점검 3건");
  expect(root).toHaveTextContent("마감 점검 4건");
  expect(root).not.toHaveTextContent(
    /OperatingProfit|Receivables|NEW_CHECK|BILL_MISS|SETL|·\s*·|·\s*$/,
  );
  expect(root).toHaveTextContent("월단위 마진율");
  expect(root).toHaveTextContent("API/ALL Project");
  expect(root).toHaveTextContent("SalesPipeline/ALL 기회");
  for (const id of ["dashboard-close", "dashboard-parts", "dashboard-cash"])
    expect(screen.getByTestId(id).querySelector(".dashboard-basis")).toBeNull();
  await userEvent.click(screen.getByTestId("dashboard-basis-help"));
  expect(screen.getByRole("tooltip")).not.toHaveTextContent(
    /OperatingProfit|회사 손익|sourceService/,
  );
  expect(root.querySelector('[title*="OperatingProfit"]')).toBeNull();
});
it.each(["FAILED", "UNCONFIRMED", "EMPTY"] as const)(
  "%s 상태에서도 진단 식별자 대신 안내와 상태를 표시한다",
  async (status) => {
    data.actionsBasis = {
      ...data.actionsBasis,
      status,
      reasons: ["OperatingProfit /api/dashboard 조회 실패"],
    };
    data.cashForecast![0].unconfirmedReasons = [
      "저장 REAL_PAY_AMT · 현재구분 OS 예정",
      "NEW_INTERNAL_CODE",
    ];
    ready();
    await screen.findByTestId("dashboard-actions");
    expect(screen.getByTestId("dashboard-actions")).not.toHaveTextContent(
      /OperatingProfit|\/api\//,
    );
    expect(screen.getByTestId("dashboard-actions")).toHaveTextContent(
      "자료 상태를 확인해 주세요.",
    );
    expect(screen.getByTestId("dashboard-cash")).toHaveTextContent(
      "저장 실지급액 · 현재구분 외주 예정",
    );
    expect(screen.getByTestId("dashboard-cash")).not.toHaveTextContent(
      /REAL_PAY_AMT|NEW_INTERNAL_CODE/,
    );
  },
);
it("조회 오류의 요청 번호·API 경로를 화면에 내보내지 않는다", async () => {
  const { ApiError } = await import("../../shared/api");
  vi.mocked(api.getDashboard).mockRejectedValue(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "FAILED",
      message: "OperatingProfit /api/dashboard",
      requestId: "internal-request-id",
    }),
  );
  ready();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "대시보드를 불러오지 못했습니다.",
  );
  expect(screen.getByRole("alert")).not.toHaveTextContent(
    /internal-request-id|OperatingProfit|\/api\//,
  );
});
it("시스템 계산 설명만 번역하며 업무 약어와 기간은 유지한다", () => {
  expect(dashboardMessage("AMT + VAT_AMT · IO_DT 없으면 FIN_YM")).toBe(
    "금액 + 부가세 · 입출금일 없으면 재무 기준월",
  );
  expect(dashboardMessage("MM 초과 2명 · 2026-07")).toBe(
    "MM 초과 2명 · 2026-07",
  );
  expect(dashboardMessage("현재구분 CT 예정")).toBe("현재구분 계약직 예정");
  expect(dashboardMessage("신규 점검 FUTURE 결과")).toBe(
    "자료 상태를 확인해 주세요.",
  );
});
it.each(
  cardTargets.flatMap(([card, target]) =>
    [null, "0", "12.3"].map((value) => ({ card, target, value })),
  ),
)(
  "$card 값 $value에서도 서버 목적지와 월 문맥을 보존하고 뒤로 돌아온다",
  async ({ card, target, value }) => {
    const index = cardTargets.findIndex(([name]) => name === card);
    data.metrics[index].value = value;
    ready();
    await userEvent.click(
      await screen.findByTestId(`dashboard-metric-${card}`),
    );
    expect(screen.getByTestId("destination")).toHaveTextContent(`/${target}`);
    const state = JSON.parse(
      screen.getByTestId("destination-state").textContent!,
    );
    expect(state).toMatchObject({
      fromDashboard: true,
      month: "2026-07",
      year: "2026",
      linkKey: index === 4 ? null : "2026-07",
    });
    if (index === 4) expect(state.section).toBe("receivables");
    await userEvent.click(screen.getByRole("button", { name: "뒤로" }));
    expect(await screen.findByTestId(`dashboard-metric-${card}`)).toBeVisible();
  },
);
it.each([
  ["OVERDUE_RECEIVABLE", "UI-13", "receivables", "overdue"],
  ["CORPORATE_TAX_DUE", "UI-10", "tax", undefined],
  ["CASH_SHORTAGE", "UI-21", undefined, undefined],
  ["STAFF_SHORTAGE", "UI-28", "supply", undefined],
  ["MM_OVER", "UI-12", undefined, "MM_OVER"],
  ["PRECONTRACT_ASSIGNMENT", "UI-12", undefined, undefined],
  ["WITHDRAWAL_DUE", "UI-05", undefined, undefined],
  ["SALES_PART_MISSING", "UI-01", undefined, undefined],
  ["PROJECT_RISK", "UI-31", undefined, undefined],
  ["ISSUE_OVERDUE", "UI-31", undefined, undefined],
  ["WEEK_MEMO_STALE", "UI-31", undefined, undefined],
  ["BILLING_MISSING", "UI-13", "checks", undefined],
  ["QUOTE_APPROVAL_WAIT", "UI-29", undefined, undefined],
])(
  "%s 목적 화면·탭·필터 문맥",
  async (actionCode, screenId, section, filter) => {
    const cash = actionCode === "CASH_SHORTAGE";
    const month = cash ? "2027-01" : "2026-07";
    data.actions = [
      {
        ...data.actions[0],
        actionCode: actionCode!,
        link: { screenId: screenId!, linkKey: month },
      },
    ];
    ready();
    await userEvent.click(
      await screen.findByTestId(
        `dashboard-action-${actionCode!.toLowerCase().replaceAll("_", "-")}`,
      ),
    );
    const state = JSON.parse(
      screen.getByTestId("destination-state").textContent!,
    );
    expect(screen.getByTestId("destination")).toHaveTextContent(`/${screenId}`);
    expect(state).toEqual({
      fromDashboard: true,
      linkKey: month,
      month,
      year: month.slice(0, 4),
      ...(section ? { section } : {}),
      ...(filter ? { filter } : {}),
    });
  },
);
it("자금 월 이동은 불완전한 원천상세 요청을 만들지 않는다", async () => {
  ready();
  await userEvent.click(await screen.findByTestId("dashboard-cash-2027-02"));
  const state = JSON.parse(
    screen.getByTestId("destination-state").textContent!,
  );
  expect(state).toMatchObject({
    fromDashboard: true,
    month: "2027-02",
    year: "2027",
  });
  expect(state).not.toHaveProperty("cashFlowSource");
});
it("등록되지 않은 대상과 0건 할일은 업무 화면으로 이동하지 않는다", async () => {
  data.metrics[0].link = { screenId: "UI-99", linkKey: "2026-07" };
  data.actions[0].count = 0;
  ready();
  await userEvent.click(await screen.findByTestId("dashboard-metric-sales"));
  expect(screen.getByRole("status")).toHaveTextContent(
    "대상 화면을 사용할 수 없습니다.",
  );
  expect(screen.queryByTestId("destination")).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("dashboard-action-overdue-receivable"),
  ).not.toBeInTheDocument();
});
it("retains cards through throttled focus refresh, rejects duplicate/hidden requests and offers retry after failure", async () => {
  let clock = 100_000;
  const now = vi.spyOn(Date, "now").mockImplementation(() => clock);
  const visibility = vi
    .spyOn(document, "visibilityState", "get")
    .mockReturnValue("visible");
  try {
    ready();
    const card = await screen.findByTestId("dashboard-metric-sales");
    await waitFor(() =>
      expect(
        screen.queryByText("대시보드를 불러오는 중입니다."),
      ).not.toBeInTheDocument(),
    );
    act(() => window.dispatchEvent(new Event("focus")));
    expect(api.getDashboard).toHaveBeenCalledTimes(1);
    clock += 31_000;
    visibility.mockReturnValue("hidden");
    act(() => window.dispatchEvent(new Event("focus")));
    expect(api.getDashboard).toHaveBeenCalledTimes(1);
    visibility.mockReturnValue("visible");
    let fail!: (reason: Error) => void;
    vi.mocked(api.getDashboard).mockImplementationOnce(
      () =>
        new Promise((_, reject) => {
          fail = reject;
        }),
    );
    act(() => window.dispatchEvent(new Event("focus")));
    await waitFor(() => expect(api.getDashboard).toHaveBeenCalledTimes(2));
    expect(screen.getByTestId("dashboard-metric-sales")).toBe(card);
    expect(screen.getByText("갱신 중…")).toBeVisible();
    act(() => {
      window.dispatchEvent(new Event("focus"));
      window.dispatchEvent(new Event("focus"));
    });
    expect(api.getDashboard).toHaveBeenCalledTimes(2);
    await act(async () => fail(new Error("temporary network failure")));
    expect(screen.getByTestId("dashboard-metric-sales")).toBe(card);
    expect(screen.getByText(/갱신 실패/)).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await waitFor(() => expect(api.getDashboard).toHaveBeenCalledTimes(3));
    await waitFor(() =>
      expect(screen.queryByText(/갱신 실패/)).not.toBeInTheDocument(),
    );
    expect(screen.getByTestId("dashboard-metric-sales")).toBe(card);
  } finally {
    now.mockRestore();
    visibility.mockRestore();
  }
});
it("clears cached business cards when a refresh loses authorization", async () => {
  ready();
  await screen.findByTestId("dashboard-metric-sales");
  vi.mocked(api.getDashboard).mockRejectedValueOnce(
    new (await import("../../shared/api")).ApiError(403, {
      success: false,
      meta: {},
      code: "FORBIDDEN",
      message: "권한이 변경됐습니다.",
      requestId: "revoked",
    }),
  );
  act(() => window.dispatchEvent(new CustomEvent("sicost:bills-committed")));
  await waitFor(() =>
    expect(
      screen.queryByTestId("dashboard-metric-sales"),
    ).not.toBeInTheDocument(),
  );
  expect(screen.getByText("권한이 변경됐습니다.")).toBeVisible();
});

it("shows the design's provisional month notice once and keeps detailed number sources accessible", async () => {
  data.selectedMonth = data.currentMonth = "2026-07";
  data.lastClosedMonth = "2026-06";
  data.provisional = true;
  ready();
  await screen.findByText("7월 매출·이익");
  const bar = screen.getByRole("group", { name: "조회 기간 상태" });
  expect(bar).toHaveTextContent("마감 완료6월까지");
  expect(within(bar).getAllByText("잠정")).toHaveLength(1);
  expect(within(bar).queryByText("작업중")).not.toBeInTheDocument();
  expect(bar).toHaveTextContent("마감 전이라 바뀔 수 있습니다");
  expect(within(bar).queryByText("오늘 기준")).not.toBeInTheDocument();
  await userEvent.click(within(bar).getByTestId("dashboard-basis-help"));
  expect(screen.getByRole("tooltip")).toHaveTextContent(
    "이번 달 매출 · 2026-07",
  );
  expect(screen.getByRole("tooltip")).not.toHaveTextContent("회사 손익");
  expect(screen.getByRole("tooltip")).toHaveTextContent("조회 시각");
});

it("distinguishes a closed historical month from panels based on today", async () => {
  data.selectedMonth = "2026-06";
  data.selectedMonthCloseStatus = "CLOSE";
  data.provisional = false;
  data.lastClosedMonth = "2026-06";
  ready();
  await screen.findByText("6월 숫자는 마감된 확정치입니다");
  const bar = screen.getByRole("group", { name: "조회 기간 상태" });
  expect(within(bar).getByText("확정")).toBeInTheDocument();
  expect(within(bar).queryByText("잠정")).not.toBeInTheDocument();
  expect(bar).toHaveTextContent("오늘 기준");
  expect(bar).toHaveTextContent("청구 누락 의심만 고른 달");
});

it("does not invent a closed month and includes the year for a previous year's close", async () => {
  data.lastClosedMonth = null;
  const view = ready();
  await screen.findByText("없음");
  view.unmount();
  data.lastClosedMonth = "2025-12";
  ready();
  expect(await screen.findByText("2025년 12월까지")).toBeInTheDocument();
});

it.each(["success", "failure"])(
  "hides previous-month cards during a different-month request and after %s",
  async (outcome) => {
    ready();
    await screen.findByTestId("dashboard-metric-sales");
    let resolve!: (value: api.DashboardData) => void;
    let reject!: (reason: Error) => void;
    vi.mocked(api.getDashboard).mockImplementationOnce(
      () =>
        new Promise((ok, fail) => {
          resolve = ok;
          reject = fail;
        }),
    );
    act(() =>
      window.dispatchEvent(
        new CustomEvent("sicost:screen-period-changed", {
          detail: {
            screenId: "UI-16",
            value: { type: "YEAR_MONTH", value: "2026-06" },
          },
        }),
      ),
    );
    await waitFor(() =>
      expect(api.getDashboard).toHaveBeenLastCalledWith(
        "2026-06",
        expect.any(AbortSignal),
      ),
    );
    expect(
      screen.queryByTestId("dashboard-metric-sales"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("dashboard-export")).toBeDisabled();
    expect(screen.queryByText(/선택월 2026-07/)).not.toBeInTheDocument();
    if (outcome === "success") {
      await act(async () => resolve({ ...data, selectedMonth: "2026-06" }));
      expect(screen.getByTestId("dashboard-metric-sales")).toBeInTheDocument();
      expect(screen.getByText(/선택월 2026-06/)).toBeInTheDocument();
    } else {
      await act(async () => reject(new Error("다른 월 조회 실패")));
      expect(screen.getByText("다른 월 조회 실패")).toBeVisible();
      expect(
        screen.queryByTestId("dashboard-metric-sales"),
      ).not.toBeInTheDocument();
      expect(screen.getByTestId("dashboard-export")).toBeDisabled();
      await userEvent.click(screen.getByTestId("dashboard-retry"));
      await waitFor(() => expect(api.getDashboard).toHaveBeenCalledTimes(3));
      expect(api.getDashboard).toHaveBeenLastCalledWith(
        "2026-06",
        expect.any(AbortSignal),
      );
    }
  },
);
