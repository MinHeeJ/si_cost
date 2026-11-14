import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  ComparisonDifference,
  StaffingComparisonContent,
  StaffingComparisonDownload,
  StaffingPlanComparisonScreen,
} from "./StaffingPlanComparisonScreen";
import * as api from "./staffingComparisonApi";
import * as shared from "../shared/sharedApi";
vi.mock("./staffingComparisonApi");
vi.mock("../shared/sharedApi");
const row: api.ComparisonRow = {
  projectId: "P-101",
  projectName: "계약 전체 프로젝트",
  jobCode: "DEV",
  planMm: "6.00",
  actualMm: "7.00",
  differenceMm: "1.00",
};
const data: api.ComparisonPage = {
  year: 2026,
  items: [row],
  total: 57,
  page: 1,
  size: 50,
  totals: { planMm: "90.00", actualMm: "80.00", differenceMm: "-10.00" },
};
beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(window, "innerWidth", {
    value: 1280,
    configurable: true,
    writable: true,
  });
  vi.mocked(api.getStaffingComparison).mockResolvedValue(data);
  vi.mocked(api.comparisonProjects).mockResolvedValue([
    { id: row.projectId, name: row.projectName },
  ]);
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "DEV",
        name: "개발",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.mocked(shared.getPeriodContext).mockResolvedValue({
    currentDate: "2026-07-01",
    currentMonth: "2026-07",
    cashBaseMonth: "2025-01",
    timezone: "Asia/Seoul",
    monthClose: { month: "2026-07", status: "작업중" },
  });
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(api.downloadStaffingComparison).mockResolvedValue({
    blob: new Blob(["workbook"]),
    filename: "계획대비투입.xlsx",
  });
});
function content() {
  return render(
    <MemoryRouter>
      <StaffingComparisonContent year={2026} />
    </MemoryRouter>,
  );
}
it("renders real columns, full filtered totals rather than page sums, and existing project plan link", async () => {
  content();
  const link = await screen.findByRole("link", { name: row.projectName });
  expect(link.getAttribute("href")).toContain(
    "/UI-01?projectId=P-101&tab=plans&year=2026",
  );
  expect(screen.getByText("계획 MM")).toBeInTheDocument();
  expect(screen.getByText("실제·예정 MM")).toBeInTheDocument();
  expect(screen.getByText("필터 전체 합계")).toBeInTheDocument();
  expect(screen.getByText("90")).toBeInTheDocument();
  expect(screen.getByText("초과 1")).toHaveClass("sicost-tone-warn");
  expect(screen.getByText("부족 10")).toHaveClass("sicost-tone-bad");
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
});
it("draft filters apply only on query and reset clears them", async () => {
  content();
  await screen.findByRole("link", { name: row.projectName });
  const count = vi.mocked(api.getStaffingComparison).mock.calls.length;
  fireEvent.change(screen.getByLabelText("프로젝트"), {
    target: { value: row.projectId },
  });
  fireEvent.change(screen.getByLabelText("차이"), {
    target: { value: "SHORT" },
  });
  expect(api.getStaffingComparison).toHaveBeenCalledTimes(count);
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() =>
    expect(api.getStaffingComparison).toHaveBeenLastCalledWith(
      expect.objectContaining({
        projectId: row.projectId,
        difference: "SHORT",
        page: 1,
      }),
      expect.any(AbortSignal),
    ),
  );
  fireEvent.click(screen.getByRole("button", { name: "초기화" }));
  await waitFor(() =>
    expect(api.getStaffingComparison).toHaveBeenLastCalledWith(
      expect.objectContaining({ difference: "ALL", page: 1 }),
      expect.any(AbortSignal),
    ),
  );
  expect(screen.getByLabelText("프로젝트")).toHaveValue("");
});
it("export uses applied filter, not draft or pagination", async () => {
  content();
  await screen.findByRole("link", { name: row.projectName });
  fireEvent.change(screen.getByLabelText("차이"), {
    target: { value: "SHORT" },
  });
  fireEvent.click(screen.getByTestId("comparison-download-button"));
  await waitFor(() =>
    expect(api.downloadStaffingComparison).toHaveBeenCalledWith(
      { year: 2026, difference: "ALL" },
      "THOUSAND",
      expect.any(AbortSignal),
    ),
  );
  expect(api.saveStaffingComparison).toHaveBeenCalled();
});
it("pending source shows loading and prevents export", () => {
  vi.mocked(api.getStaffingComparison).mockReturnValue(new Promise(() => {}));
  content();
  expect(screen.getByTestId("comparison-download-button")).toBeDisabled();
  expect(screen.queryByText("필터 전체 합계")).not.toBeInTheDocument();
});
it("empty is explicit and still has authoritative zero totals", async () => {
  vi.mocked(api.getStaffingComparison).mockResolvedValue({
    ...data,
    items: [],
    total: 0,
    totals: { planMm: "0.00", actualMm: "0.00", differenceMm: "0.00" },
  });
  content();
  expect(
    await screen.findByText("조회된 자료가 없습니다."),
  ).toBeInTheDocument();
  expect(
    within(screen.getByTestId("staffing-comparison-table")).getByText("맞음"),
  ).toHaveClass("sicost-tone-ok");
});
it("request failure shows Korean field message and request id, retry gets real results", async () => {
  vi.mocked(api.getStaffingComparison).mockRejectedValueOnce(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_PERIOD",
      message: "연도를 확인해 주세요.",
      field: "year",
      requestId: "comparison-error",
    }),
  );
  content();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "연도를 확인해 주세요.",
  );
  expect(screen.getByRole("alert")).toHaveTextContent("comparison-error");
  expect(screen.queryByText("조회된 자료가 없습니다.")).not.toBeInTheDocument();
  expect(screen.getByTestId("comparison-download-button")).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(
    await screen.findByRole("link", { name: row.projectName }),
  ).toBeInTheDocument();
});
it("download failure never saves a file and leaves retry available", async () => {
  vi.mocked(api.downloadStaffingComparison).mockRejectedValue(
    new Error("접근기록 저장 실패"),
  );
  render(
    <StaffingComparisonDownload
      filter={{ year: 2026, difference: "ALL" }}
      amountUnit="WON"
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "접근기록 저장 실패",
  );
  expect(api.saveStaffingComparison).not.toHaveBeenCalled();
  expect(screen.getByRole("button", { name: "엑셀 다운로드" })).toBeEnabled();
});
it("mobile download guard never issues a request", () => {
  Object.defineProperty(window, "innerWidth", {
    value: 375,
    configurable: true,
  });
  render(
    <StaffingComparisonDownload
      filter={{ year: 2026, difference: "ALL" }}
      amountUnit="WON"
    />,
  );
  expect(screen.getByTestId("comparison-download")).toHaveClass(
    "staffing-comparison-download",
  );
  fireEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  expect(api.downloadStaffingComparison).not.toHaveBeenCalled();
});
it("cancel aborts download without success or file save", async () => {
  vi.mocked(api.downloadStaffingComparison).mockReturnValue(
    new Promise(() => {}),
  );
  render(
    <StaffingComparisonDownload
      filter={{ year: 2026, difference: "ALL" }}
      amountUnit="WON"
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "엑셀 다운로드" }));
  const signal = vi.mocked(api.downloadStaffingComparison).mock.calls[0][2];
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(signal.aborted).toBe(true);
  expect(api.saveStaffingComparison).not.toHaveBeenCalled();
});
it("difference badges use exact decimal sign including tiny nonzero and zero", () => {
  render(
    <>
      <ComparisonDifference value="-0.01" />
      <ComparisonDifference value="0.00" />
      <ComparisonDifference value="0.01" />
    </>,
  );
  expect(screen.getByText("부족 0.01")).toHaveClass("sicost-tone-bad");
  expect(screen.getByText("초과 0.01")).toHaveClass("sicost-tone-warn");
  expect(screen.getByText("맞음")).toHaveClass("sicost-tone-ok");
});
it("saved server period and subsequent shared period event load the right year", async () => {
  render(
    <MemoryRouter>
      <StaffingPlanComparisonScreen />
    </MemoryRouter>,
  );
  await screen.findByRole("link", { name: row.projectName });
  fireEvent(
    window,
    new CustomEvent("sicost:screen-period-changed", {
      detail: {
        screenId: "UI-11",
        value: { type: "YEAR", value: "2025" },
        context: await shared.getPeriodContext(),
      },
    }),
  );
  await waitFor(() =>
    expect(api.getStaffingComparison).toHaveBeenLastCalledWith(
      expect.objectContaining({ year: 2025 }),
      expect.any(AbortSignal),
    ),
  );
});
it("source option failure is not silently replaced with sample projects", async () => {
  vi.mocked(api.comparisonProjects).mockRejectedValue(
    new Error("프로젝트 원천 조회 실패"),
  );
  content();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "프로젝트 원천 조회 실패",
  );
  expect(
    within(screen.getByLabelText("프로젝트")).getAllByRole("option"),
  ).toHaveLength(1);
});
