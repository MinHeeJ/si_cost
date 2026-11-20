import { act, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import { DashboardScreen } from "../dashboardops/DashboardScreen";
import { dashboardFixture } from "../dashboardops/dashboardFixture";
import type { DashboardData } from "../dashboardops/dashboardApi";
import userEvent from "@testing-library/user-event";
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
let data: DashboardData;
const paths: string[] = [];
beforeEach(() => {
  paths.length = 0;
  data = dashboardFixture();
  vi.stubGlobal(
    "fetch",
    vi.fn(async (path: string) => {
      paths.push(path);
      return new Response(JSON.stringify({ success: true, meta: {}, data }));
    }),
  );
});
afterEach(() => vi.unstubAllGlobals());
function draw() {
  return render(
    <MemoryRouter initialEntries={["/UI-16"]}>
      <DashboardScreen />
    </MemoryRouter>,
  );
}
it("binds the existing UI-16 host to the public dashboard envelope and renders all sections", async () => {
  draw();
  await screen.findByTestId("dashboard-metric-sales");
  expect(paths).toEqual(["/api/dashboard"]);
  expect(screen.getByTestId("dashboard-metric-sales")).toHaveTextContent(
    "−1.001",
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
  for (const name of [
    "dashboard-actions",
    "dashboard-close",
    "dashboard-alerts",
    "dashboard-supply",
    "dashboard-parts",
    "dashboard-pipeline",
    "dashboard-cash",
  ])
    expect(screen.getByTestId(name)).toBeInTheDocument();
});
it("sends only selected month after the established period event without copying identity or calculating money", async () => {
  draw();
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
    expect(paths).toEqual(["/api/dashboard", "/api/dashboard?month=2026-06"]),
  );
});
it("keeps failed source labels and null amounts separate from real zero and empty success", async () => {
  data.metrics[0].value = null;
  data.metrics[0].basis = {
    ...data.metrics[0].basis,
    status: "UNCONFIRMED",
    reasons: ["선택월 손익 미확인"],
  };
  data.metrics[1].value = "0";
  data.actions = [];
  data.actionTypeCount = null;
  data.urgentActionTypeCount = null;
  data.actionsBasis = {
    ...data.actionsBasis,
    status: "FAILED",
    reasons: ["원천 조회에 실패했습니다."],
  };
  draw();
  expect(await screen.findByTestId("dashboard-metric-sales")).toHaveTextContent(
    "– 천원",
  );
  expect(
    screen.getByTestId("dashboard-metric-operating-profit"),
  ).toHaveTextContent("0 천원");
  expect(screen.getByText("선택월 손익 미확인")).toBeInTheDocument();
  expect(screen.queryByText("확인할 일이 없습니다")).not.toBeInTheDocument();
});
