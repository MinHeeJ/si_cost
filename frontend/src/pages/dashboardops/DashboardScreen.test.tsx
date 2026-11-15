import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
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
  return (
    <p data-testid="destination">
      {location.pathname}
      {location.search} {JSON.stringify(location.state)}
    </p>
  );
}
function ready() {
  return render(
    <MemoryRouter initialEntries={["/UI-16"]}>
      <SharedScreenContext.Provider
        value={{
          menus: ["UI-08", "UI-13", "UI-14", "UI-04", "UI-21"].map(
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
          {["UI-08", "UI-13", "UI-14", "UI-04", "UI-21"].map((id) => (
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
