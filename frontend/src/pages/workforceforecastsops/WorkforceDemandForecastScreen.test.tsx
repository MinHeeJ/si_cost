import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import {
  WorkforceDemandForecastScreen,
  WorkforceForecastPanel,
} from "./WorkforceDemandForecastScreen";
import * as api from "./workforceForecastApi";
import * as shared from "../shared/sharedApi";
import { ApiError } from "../../shared/api";

vi.mock("./workforceForecastApi", async (original) => ({
  ...(await original<typeof import("./workforceForecastApi")>()),
  getWorkforceForecasts: vi.fn(),
  getWorkforceForecastExport: vi.fn(),
  saveWorkforceForecastDownload: vi.fn(),
}));
vi.mock("../shared/sharedApi", async (original) => ({
  ...(await original<typeof import("../shared/sharedApi")>()),
  getPeriodContext: vi.fn(),
  getSharedCodes: vi.fn(),
  getSharedPreferences: vi.fn(),
  putSharedPreference: vi.fn(),
}));
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
const query = { from: "2026-10", months: 3, includeUnfilled: true };
function result(): api.WorkforceForecast {
  return {
    ...query,
    basisDate: "2026-10-12",
    standardMonth: "2026-10",
    totals: ["2026-10", "2026-11", "2026-12"].map((month) => ({
      month,
      supply: "2.0",
      demand: "2.6",
      opportunityDemand: "2.1",
      unfilledDemand: "0.5",
      difference: "-0.6",
      rawSupply: "2",
      rawOpportunityDemand: "2.05",
      rawUnfilledDemand: "0.5",
      status: "SHORT",
    })),
    jobDiagnostics: [],
    hiring: [],
    issues: [],
    lastMonthSupply: "2.0",
    lastMonthDemand: "2.6",
    mostShortageJobCode: "BE",
    maximumShortage: "0.6",
  };
}
function mount() {
  return render(
    <MemoryRouter>
      <WorkforceDemandForecastScreen />
    </MemoryRouter>,
  );
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: [{ code: "BE", name: "백엔드 개발" }] as shared.SharedCodeLookup[],
  });
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  );
  vi.mocked(shared.getPeriodContext).mockResolvedValue({
    currentDate: "2026-10-12",
    currentMonth: "2026-10",
    cashBaseMonth: "2025-01",
    timezone: "Asia/Seoul",
    monthClose: { month: "2026-10", status: "작업중" },
  });
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(shared.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, revision: "next", stored: true },
    }),
  );
  vi.mocked(api.getWorkforceForecasts).mockResolvedValue(result());
  vi.mocked(api.getWorkforceForecastExport).mockResolvedValue({
    blob: new Blob(["xlsx"]),
    filename: "인력수요예측_2026_20261012_090000.xlsx",
  });
});
it("loads server year remaining months and displays three authoritative table rows, cards and chart", async () => {
  mount();
  await screen.findByTestId("workforce-supply-card");
  expect(api.getWorkforceForecasts).toHaveBeenCalledWith(query);
  expect(screen.getByLabelText("예측 연도")).toHaveTextContent("2026년2027년");
  expect(screen.getByTestId("workforce-demand-card")).toHaveTextContent(
    "2.6명",
  );
  expect(screen.getByTestId("workforce-demand-card")).toHaveTextContent(
    "미충원 0.5명",
  );
  await waitFor(() =>
    expect(screen.getByTestId("workforce-shortage-card")).toHaveTextContent(
      "백엔드 개발",
    ),
  );
  expect(
    screen.getByRole("img", { name: "월별 가용 vs 필요" }),
  ).toBeInTheDocument();
  expect(screen.getAllByRole("row")).toHaveLength(4);
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByText(/PM이 인원 계획을 실제에 맞게 줄입니다/),
  ).toBeInTheDocument();
});
it("next year saves existing preference then requests exactly January through December", async () => {
  mount();
  await screen.findByTestId("workforce-supply-card");
  await userEvent.selectOptions(screen.getByLabelText("예측 연도"), "2027");
  await waitFor(() =>
    expect(api.getWorkforceForecasts).toHaveBeenCalledWith({
      from: "2027-01",
      months: 12,
      includeUnfilled: true,
    }),
  );
  expect(shared.putSharedPreference).toHaveBeenCalledWith(
    "PERIOD",
    "UI-17",
    { type: "YEAR", value: "2027" },
    null,
  );
});
it("stored next year is restored, while past years are not offered", async () => {
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({
    items: [
      {
        type: "PERIOD",
        key: "UI-17",
        value: { type: "YEAR", value: "2027" },
        revision: "r",
        stored: true,
      },
    ],
  });
  mount();
  await screen.findByTestId("workforce-supply-card");
  expect(api.getWorkforceForecasts).toHaveBeenCalledWith({
    from: "2027-01",
    months: 12,
    includeUnfilled: true,
  });
  expect(
    screen.queryByRole("option", { name: "2025년" }),
  ).not.toBeInTheDocument();
});
it("failed period save preserves old period and does not request another forecast", async () => {
  vi.mocked(shared.putSharedPreference).mockRejectedValueOnce(
    new Error("기간 저장 실패"),
  );
  mount();
  await screen.findByTestId("workforce-supply-card");
  await userEvent.selectOptions(screen.getByLabelText("예측 연도"), "2027");
  expect(await screen.findByRole("alert")).toHaveTextContent("기간 저장 실패");
  expect(screen.getByLabelText("예측 연도")).toHaveValue("2026");
  expect(api.getWorkforceForecasts).toHaveBeenCalledTimes(1);
});
it("invalid stored historical year falls back without overwriting the preference", async () => {
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({
    items: [
      {
        type: "PERIOD",
        key: "UI-17",
        value: { type: "YEAR", value: "2025" },
        revision: "r",
        stored: true,
      },
    ],
  });
  mount();
  await screen.findByTestId("workforce-supply-card");
  expect(screen.getByRole("alert")).toHaveTextContent(
    "저장된 설정은 변경하지 않았습니다",
  );
  expect(shared.putSharedPreference).not.toHaveBeenCalled();
});
it("period failure is distinct from empty data and has a retry", async () => {
  vi.mocked(shared.getPeriodContext).mockRejectedValueOnce(
    new Error("기간 연결 실패"),
  );
  mount();
  expect(await screen.findByRole("alert")).toHaveTextContent("기간 연결 실패");
  expect(api.getWorkforceForecasts).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "기간 재조회" }));
  await screen.findByTestId("workforce-supply-card");
});
it("keeps loading separate from a successful empty list", async () => {
  let resolve!: (data: api.WorkforceForecast) => void;
  vi.mocked(api.getWorkforceForecasts).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  render(<WorkforceForecastPanel query={query} />);
  expect(screen.getByRole("status")).toHaveTextContent("예측을 불러오는 중");
  await act(async () => {
    resolve({
      ...result(),
      totals: [],
      mostShortageJobCode: null,
      maximumShortage: null,
    });
  });
  expect(
    screen.getByText("선택 기간에 예측 자료가 없습니다."),
  ).toBeInTheDocument();
  expect(screen.getByTestId("workforce-shortage-card")).toHaveTextContent(
    "부족 없음",
  );
  await userEvent.click(screen.getByRole("button", { name: "조건 초기화" }));
  expect(api.getWorkforceForecasts).toHaveBeenCalledTimes(2);
});
it("failed request exposes request ID and retries only the read", async () => {
  vi.mocked(api.getWorkforceForecasts).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "workforce-read",
    }),
  );
  render(<WorkforceForecastPanel query={query} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("workforce-read");
  await userEvent.click(screen.getByRole("button", { name: "재조회" }));
  await screen.findByTestId("workforce-supply-card");
  expect(api.getWorkforceForecastExport).not.toHaveBeenCalled();
});
it("unknown sources show dashes and reasons rather than zero or no shortage", async () => {
  const value = result();
  value.lastMonthSupply = null;
  value.mostShortageJobCode = null;
  value.issues = [
    {
      field: "EXT_PRBL_H",
      needSn: null,
      reason: "연장확률 미확인",
      status: "UNKNOWN",
    },
  ];
  value.totals[0] = {
    ...value.totals[0],
    supply: null,
    difference: null,
    status: "UNKNOWN",
  };
  vi.mocked(api.getWorkforceForecasts).mockResolvedValue(value);
  render(<WorkforceForecastPanel query={query} />);
  expect(await screen.findByTestId("workforce-supply-card")).toHaveTextContent(
    "–명",
  );
  expect(screen.getByText("EXT_PRBL_H: 연장확률 미확인")).toBeInTheDocument();
  expect(screen.getByTestId("workforce-shortage-card")).toHaveTextContent(
    "미확인",
  );
  expect(
    screen.queryByText("채용 검토가 필요한 직무가 없습니다."),
  ).not.toBeInTheDocument();
});
it("uses server strict-boundary statuses and formats shortage, surplus and exact zero without business recalculation", async () => {
  const value = result();
  value.totals[1] = {
    ...value.totals[1],
    difference: "1.1",
    status: "SURPLUS",
  };
  value.totals[2] = {
    ...value.totals[2],
    difference: "-0.4",
    status: "BALANCED",
  };
  vi.mocked(api.getWorkforceForecasts).mockResolvedValue(value);
  render(<WorkforceForecastPanel query={query} />);
  await screen.findByTestId("workforce-supply-card");
  const cells = within(
    screen.getByTestId("workforce-row-difference"),
  ).getAllByRole("cell");
  expect(cells[0]).toHaveClass("workforce-forecast-short");
  expect(cells[0]).toHaveTextContent("0.6명 부족");
  expect(cells[1]).toHaveClass("workforce-forecast-surplus");
  expect(cells[1]).toHaveTextContent("1.1명 남음");
  expect(cells[2]).toHaveClass("workforce-forecast-balanced");
});
it("downloads exact selected filters and unit and does not recompute the table", async () => {
  render(<WorkforceForecastPanel query={query} />);
  await screen.findByTestId("workforce-supply-card");
  expect(screen.queryByLabelText("금액 단위")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  await waitFor(() =>
    expect(api.getWorkforceForecastExport).toHaveBeenCalledWith(query, "WON"),
  );
  expect(api.saveWorkforceForecastDownload).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("status")).toHaveTextContent("다운로드했습니다");
});
it("mobile has no download action", async () => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  );
  render(<WorkforceForecastPanel query={query} />);
  await screen.findByTestId("workforce-supply-card");
  expect(
    screen.queryByRole("button", { name: "엑셀 다운로드" }),
  ).not.toBeInTheDocument();
  expect(api.getWorkforceForecastExport).not.toHaveBeenCalled();
});
it("download failure retains data and exposes request ID, never saves a file", async () => {
  vi.mocked(api.getWorkforceForecastExport).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "접근기록 실패",
      requestId: "workforce-export",
    }),
  );
  render(<WorkforceForecastPanel query={query} />);
  await screen.findByTestId("workforce-supply-card");
  await userEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "workforce-export",
  );
  expect(api.saveWorkforceForecastDownload).not.toHaveBeenCalled();
  expect(screen.getByTestId("workforce-demand-card")).toBeInTheDocument();
});
it("blocks duplicate downloads while generation is pending", async () => {
  let resolve!: (value: { blob: Blob; filename: string }) => void;
  vi.mocked(api.getWorkforceForecastExport).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  render(<WorkforceForecastPanel query={query} />);
  await screen.findByTestId("workforce-supply-card");
  await userEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  expect(screen.getByRole("button", { name: "파일 생성 중…" })).toBeDisabled();
  expect(api.getWorkforceForecastExport).toHaveBeenCalledTimes(1);
  await act(async () => {
    resolve({
      blob: new Blob(["xlsx"]),
      filename: "인력수요예측_2026_20261012_090000.xlsx",
    });
  });
});
it("source mutations refresh the mounted projection", async () => {
  render(<WorkforceForecastPanel query={query} />);
  await screen.findByTestId("workforce-supply-card");
  act(() => {
    window.dispatchEvent(new Event("sicost:sales-committed"));
  });
  await waitFor(() =>
    expect(api.getWorkforceForecasts).toHaveBeenCalledTimes(2),
  );
});
