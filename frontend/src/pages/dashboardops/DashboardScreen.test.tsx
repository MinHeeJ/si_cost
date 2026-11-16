import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { DashboardScreen } from "./DashboardScreen";
import { SharedScreenContext } from "../shared/SharedScreenContext";
import { dashboardFixture } from "./dashboardFixture";
import * as api from "./dashboardApi";
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
vi.mock("./dashboardApi", () => ({
  getDashboard: vi.fn(),
  getDashboardExport: vi.fn(),
}));
let data: api.DashboardData;
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
      {location.search} <output data-testid="destination-state">{JSON.stringify(location.state)}</output>
      <button onClick={() => navigate(-1)}>뒤로</button>
    </p>
  );
}
function ready() {
  return render(
    <MemoryRouter initialEntries={["/UI-16"]}>
      <SharedScreenContext.Provider
        value={{
          menus: ["UI-08", "UI-13", "UI-14", "UI-04", "UI-21", "UI-28", "UI-18", "UI-10", "UI-12", "UI-05", "UI-01", "UI-31", "UI-29"].map(
            (screenId) => ({
              menuId: screenId,
              parentId: null,
              name: screenId,
              screenId,
              sortOrder: 1,
              icon: null,
              pcOnly: false,
              children: [],
            }),
          ),
          favorites: [],
          star: () => {},
          pending: false,
        }}
      >
        <Routes>
          <Route path="/UI-16" element={<DashboardScreen />} />
          {["UI-08", "UI-13", "UI-14", "UI-04", "UI-21", "UI-28", "UI-18", "UI-10", "UI-12", "UI-05", "UI-01", "UI-31", "UI-29"].map((id) => (
            <Route key={id} path={`/${id}`} element={<Destination />} />
          ))}
        </Routes>
      </SharedScreenContext.Provider>
    </MemoryRouter>,
  );
}
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
    "1유형 · 급함 1유형",
  );
  expect(screen.getByRole("img")).toHaveAccessibleName(
    "매출·매출원가 실선과 ALL 매출목표 점선",
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
  await screen.findByText(/선택월 Q 2026-06/);
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
  expect(screen.getByTestId("dashboard-actions")).toHaveTextContent("–유형");
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
  data.closeProgress.remainingChecks[0].status = value === null ? "UNKNOWN" : "WARN";
  data.closeProgress.remainingChecks[0].reason = value === null ? "원천 미확인" : "실제 0건";
  ready();
  const panel = await screen.findByTestId("dashboard-close");
  expect(panel).toHaveTextContent(value === null ? "MM_OVER –건" : "MM_OVER 0건");
  expect(panel).toHaveTextContent("5/6");
  expect(panel).toHaveTextContent(value === null ? "원천 미확인" : "실제 0건");
});
it("과거 선택월 카드 제목을 유지하며 빈 파이프라인에서도 이동한다", async () => {
  data.selectedMonth = "2026-06";
  ready();
  expect(await screen.findByTestId("dashboard-metric-sales")).toHaveTextContent("06월 매출");
  expect(screen.getByTestId("dashboard-pipeline")).toHaveTextContent("자세히");
});

const cardTargets = [
  ["sales", "UI-08"], ["operating-profit", "UI-08"], ["ytd-operating-profit", "UI-18"],
  ["utilization", "UI-28"], ["receivable", "UI-13"],
] as const;
it.each(cardTargets.flatMap(([card, target]) => [null, "0", "12.3"].map((value) => ({ card, target, value }))))(
  "$card 값 $value에서도 서버 목적지와 월 문맥을 보존하고 뒤로 돌아온다", async ({ card, target, value }) => {
    const index = cardTargets.findIndex(([name]) => name === card);
    data.metrics[index].value = value;
    ready();
    await userEvent.click(await screen.findByTestId(`dashboard-metric-${card}`));
    expect(screen.getByTestId("destination")).toHaveTextContent(`/${target}`);
    const state = JSON.parse(screen.getByTestId("destination-state").textContent!);
    expect(state).toMatchObject({ fromDashboard: true, month: "2026-07", year: "2026", linkKey: index === 4 ? null : "2026-07" });
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
])("%s 목적 화면·탭·필터 문맥", async (actionCode, screenId, section, filter) => {
  const cash = actionCode === "CASH_SHORTAGE";
  const month = cash ? "2027-01" : "2026-07";
  data.actions = [{ ...data.actions[0], actionCode: actionCode!, link: { screenId: screenId!, linkKey: month } }];
  ready();
  await userEvent.click(await screen.findByTestId(`dashboard-action-${actionCode!.toLowerCase().replaceAll("_", "-")}`));
  const state = JSON.parse(screen.getByTestId("destination-state").textContent!);
  expect(screen.getByTestId("destination")).toHaveTextContent(`/${screenId}`);
  expect(state).toEqual({ fromDashboard: true, linkKey: month, month, year: month.slice(0, 4), ...(section ? {section} : {}), ...(filter ? {filter} : {}) });
});
it("자금 월 이동은 불완전한 원천상세 요청을 만들지 않는다", async () => {
  ready();
  await userEvent.click(await screen.findByTestId("dashboard-cash-2027-02"));
  const state = JSON.parse(screen.getByTestId("destination-state").textContent!);
  expect(state).toMatchObject({fromDashboard: true, month: "2027-02", year: "2027"});
  expect(state).not.toHaveProperty("cashFlowSource");
});
it("등록되지 않은 대상과 0건 할일은 업무 화면으로 이동하지 않는다", async () => {
  data.metrics[0].link = { screenId: "UI-99", linkKey: "2026-07" };
  data.actions[0].count = 0;
  ready();
  await userEvent.click(await screen.findByTestId("dashboard-metric-sales"));
  expect(screen.getByRole("status")).toHaveTextContent("대상 화면을 사용할 수 없습니다.");
  expect(screen.queryByTestId("destination")).not.toBeInTheDocument();
  expect(screen.queryByTestId("dashboard-action-overdue-receivable")).not.toBeInTheDocument();
});
