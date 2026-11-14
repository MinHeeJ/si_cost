import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { SalesForecastPanel, SalesForecastScreen } from "./SalesForecastScreen";
import { SalesForecastDownloadPanel } from "./SalesForecastDownloadPanel";
import * as api from "./salesForecastApi";
import { ApiError } from "../../shared/api";

vi.mock("./salesForecastApi", async (original) => ({
  ...(await original<typeof import("./salesForecastApi")>()),
  getSalesOpportunityForecast: vi.fn(),
  getSalesOpportunityForecastExport: vi.fn(),
  saveSalesForecastDownload: vi.fn(),
}));
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
function response(): api.SalesForecast {
  const months = [
    "2026-08",
    "2026-09",
    "2026-10",
    "2026-11",
    "2026-12",
    "2027-01",
  ];
  return {
    from: "2026-08",
    months: 6,
    summary: {
      sixMonthRevenue: "9007199254740993",
      opportunityRevenue: "100",
      allWonRevenue: "200",
      averageDemand: "1.1",
      averageSupply: "0.5",
      hiringJobCount: 1,
    },
    revenueMonths: months.map((month) => ({
      month,
      projectRevenue: "9007199254740993",
      opportunityRevenue: "100",
      totalRevenue: "9007199254741093",
      allWonRevenue: "9007199254741193",
    })),
    revenueSources: [
      {
        sourceType: "PROJECT",
        sourceId: "P-live",
        name: "유상 프로젝트",
        contractType: "MO",
        month: "2026-08",
        amount: "9007199254740993",
        allWonAmount: null,
        basis: "05 해당월 유상매출 · 철수일까지",
      },
    ],
    workforce: {
      from: "2026-08",
      months: 6,
      includeUnfilled: true,
      basisDate: "2026-07-15",
      standardMonth: "2026-07",
      totals: months.map((month) => ({
        month,
        supply: "0.5",
        opportunityDemand: "1.0",
        unfilledDemand: "0.1",
        demand: "1.1",
        difference: "-0.6",
        rawSupply: "0.5",
        rawOpportunityDemand: "1",
        rawUnfilledDemand: "0.1",
        status: "SHORT",
      })),
      jobDiagnostics: [],
      hiring: [
        {
          jobCode: "BE",
          firstShortageMonth: "2026-08",
          maximumShortage: "0.6",
          recommendedStartMonth: "2026-06",
          startNow: true,
          candidateCount: 3,
        },
      ],
      issues: [],
      lastMonthSupply: "0.5",
      lastMonthDemand: "1.1",
      mostShortageJobCode: "BE",
      maximumShortage: "0.6",
    },
  };
}
function Location() {
  const location = useLocation();
  return (
    <output data-testid="location-context">
      {location.pathname} {JSON.stringify(location.state)}
    </output>
  );
}
function mount() {
  return render(
    <MemoryRouter>
      <SalesForecastScreen />
      <Location />
    </MemoryRouter>,
  );
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  );
  vi.mocked(api.getSalesOpportunityForecast).mockResolvedValue(response());
  vi.mocked(api.getSalesOpportunityForecastExport).mockResolvedValue({
    blob: new Blob(["xlsx"]),
    filename: "영업예측_2026_20260715_090000.xlsx",
  });
});
it("renders the forecast screen, four authoritative cards, six month tables and charts without period or write controls", async () => {
  mount();
  expect(
    await screen.findByText(
      "범위 2026-08 ~ 2027-01 · 기준일 2026-07-15 · 기준월 2026-07",
    ),
  ).toBeInTheDocument();
  expect(screen.getByTestId("forecast-revenue-card")).toHaveTextContent(
    "9,007,199,254,740.993",
  );
  expect(screen.getByTestId("forecast-people-card")).toHaveTextContent(
    "1.1 / 0.5",
  );
  expect(
    screen.getByRole("img", { name: "월별 예상매출" }),
  ).toBeInTheDocument();
  expect(screen.getByRole("img", { name: "수요·공급" })).toBeInTheDocument();
  expect(
    screen.getByText(/실제 손익·청구·미수에 저장하지 않습니다/),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(screen.getAllByRole("combobox")).toHaveLength(1);
  expect(api.getSalesOpportunityForecast).toHaveBeenCalledWith();
});
it("changes only exact string formatting and passes the selected unit to the download", async () => {
  mount();
  await screen.findByTestId("forecast-revenue-card");
  await userEvent.selectOptions(screen.getByLabelText("금액 단위"), "WON");
  expect(screen.getByTestId("forecast-revenue-card")).toHaveTextContent(
    "9,007,199,254,740,993",
  );
  expect(api.getSalesOpportunityForecast).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  await waitFor(() =>
    expect(api.getSalesOpportunityForecastExport).toHaveBeenCalledWith("WON"),
  );
  expect(api.saveSalesForecastDownload).toHaveBeenCalledWith(
    expect.objectContaining({ filename: "영업예측_2026_20260715_090000.xlsx" }),
  );
});
it("candidate action navigates with the exact row's primary JOB filter", async () => {
  mount();
  await userEvent.click(await screen.findByRole("link", { name: "3명 보기" }));
  expect(screen.getByTestId("location-context")).toHaveTextContent(
    '/UI-33 {"jobCode":"BE"}',
  );
});
it("loading is distinct from an empty forecast", async () => {
  let resolve!: (value: api.SalesForecast) => void;
  vi.mocked(api.getSalesOpportunityForecast).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  mount();
  expect(screen.getByText("예측을 불러오는 중입니다.")).toBeInTheDocument();
  const empty = response();
  empty.revenueSources = [];
  empty.workforce.hiring = [];
  empty.summary.hiringJobCount = 0;
  await act(async () => {
    resolve(empty);
  });
  expect(
    screen.getByText("예측 대상 프로젝트·기회가 없습니다."),
  ).toBeInTheDocument();
  expect(
    screen.getByText("채용 검토가 필요한 직무가 없습니다."),
  ).toBeInTheDocument();
  expect(screen.getAllByRole("button", { name: "조건 초기화" })).toHaveLength(2);
});
it("failed reads expose request id, retry only the GET, and recover", async () => {
  vi.mocked(api.getSalesOpportunityForecast).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "forecast-read",
    }),
  );
  mount();
  expect(await screen.findByRole("alert")).toHaveTextContent("forecast-read");
  await userEvent.click(screen.getByRole("button", { name: "재조회" }));
  await screen.findByTestId("forecast-revenue-card");
  expect(api.getSalesOpportunityForecast).toHaveBeenCalledTimes(2);
  expect(api.getSalesOpportunityForecastExport).not.toHaveBeenCalled();
});
it("unknown metrics render dashes and reasons, not zero or a shortage-free claim", async () => {
  const value = response();
  value.summary.averageSupply = null;
  value.workforce.hiring = [];
  value.workforce.totals[0].supply = null;
  value.workforce.totals[0].difference = null;
  value.workforce.totals[0].status = "UNKNOWN";
  value.workforce.issues = [
    {
      field: "EXT_PRBL_H",
      needSn: null,
      reason: "연장 확률 기준 미확인",
      status: "UNKNOWN",
    },
  ];
  vi.mocked(api.getSalesOpportunityForecast).mockResolvedValue(value);
  mount();
  expect(await screen.findByTestId("forecast-people-card")).toHaveTextContent(
    "1.1 / –",
  );
  expect(
    screen.getByText("원천·기준 미확인 — 0과 구분합니다."),
  ).toBeInTheDocument();
  expect(
    screen.getByText("EXT_PRBL_H: 연장 확률 기준 미확인"),
  ).toBeInTheDocument();
  expect(
    screen.queryByText("채용 검토가 필요한 직무가 없습니다."),
  ).not.toBeInTheDocument();
});
it("preserves server shortage, surplus and neutral state tokens", async () => {
  const value = response();
  value.workforce.totals[1].status = "SURPLUS";
  value.workforce.totals[1].difference = "1.1";
  value.workforce.totals[2].status = "BALANCED";
  value.workforce.totals[2].difference = "-0.4";
  vi.mocked(api.getSalesOpportunityForecast).mockResolvedValue(value);
  mount();
  await screen.findByTestId("forecast-people-card");
  const row = screen.getByRole("rowheader", { name: "차이" }).closest("tr")!;
  const cells = within(row).getAllByRole("cell");
  expect(cells[0]).toHaveClass("sales-forecast-short");
  expect(cells[1]).toHaveClass("sales-forecast-surplus");
  expect(cells[2]).toHaveClass("sales-forecast-balanced");
});
it("the two assigned export screen identifiers locate real forecast and download regions", async () => {
  mount();
  await screen.findByTestId("forecast-revenue-card");
  expect(screen.getByTestId("sales-forecast-screen")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-19-B140-EXPORT",
  );
  expect(screen.getByTestId("forecast-download-panel")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-32-B140-EXPORT",
  );
});
it("mobile does not offer or request downloads", async () => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  );
  render(<SalesForecastDownloadPanel amountUnit="THOUSAND" />);
  expect(
    screen.queryByRole("button", { name: "엑셀 다운로드" }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("note")).toHaveTextContent("PC");
  expect(api.getSalesOpportunityForecastExport).not.toHaveBeenCalled();
});
it("download errors retain the screen, report request id and do not save a file", async () => {
  vi.mocked(api.getSalesOpportunityForecastExport).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "접근기록 실패",
      requestId: "forecast-export",
    }),
  );
  mount();
  await screen.findByTestId("forecast-revenue-card");
  await userEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("forecast-export");
  expect(api.saveSalesForecastDownload).not.toHaveBeenCalled();
  expect(screen.getByTestId("forecast-revenue-card")).toBeInTheDocument();
});
it("does not transmit twice while file generation is pending", async () => {
  let resolve!: (value: { blob: Blob; filename: string }) => void;
  vi.mocked(api.getSalesOpportunityForecastExport).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  render(<SalesForecastDownloadPanel amountUnit="THOUSAND" />);
  await userEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  expect(screen.getByRole("button", { name: "파일 생성 중…" })).toBeDisabled();
  expect(api.getSalesOpportunityForecastExport).toHaveBeenCalledTimes(1);
  await act(async () => {
    resolve({
      blob: new Blob(["xlsx"]),
      filename: "영업예측_2026_20260715_090000.xlsx",
    });
  });
  expect(screen.getByRole("status")).toHaveTextContent("다운로드했습니다.");
});

it.each(["sicost:sales-committed", "sicost:bills-committed", "sicost:assignments-committed"])("원천 변경 %s 뒤 예측을 재조회한다", async (name) => {
  mount(); await screen.findByTestId("forecast-revenue-card");
  const before = vi.mocked(api.getSalesOpportunityForecast).mock.calls.length;
  act(() => window.dispatchEvent(new Event(name)));
  await waitFor(() => expect(api.getSalesOpportunityForecast).toHaveBeenCalledTimes(before + 1));
});
