import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { PayrollActions, PayrollDialog } from "./PayrollDialog";
import { DeveloperPoolScreen } from "../developers/DeveloperPoolScreen";
import { ApiError } from "../../shared/api";
import * as api from "./payrollApi";
import * as shared from "../shared/sharedApi";
import * as people from "../developers/peopleClient";
import * as issues from "../uploadsops/uploadIssuesApi";
vi.mock("./payrollApi");
vi.mock("../shared/sharedApi");
vi.mock("../developers/peopleClient");
vi.mock("../uploadsops/uploadIssuesApi");
const row: api.PayrollRow = {
  month: "2026-07",
  developerId: "D-PAY",
  managementNo: "M-PAY",
  developerName: "저장급여인력",
  employeeType: "FT",
  directClass: "INDRCT",
  payAmount: "999999999999999",
  allowanceAmount: "0",
  companyInsuranceAmount: "0",
  selfInsuranceAmount: "100",
  retirementReserveAmount: "0",
  incomeTaxAmount: "0",
  withholdingTaxAmount: "0",
  realPayAmount: "-1",
  vendorSupplyAmount: "0",
  uploadId: 71,
};
const summary = {
  totalRows: 1,
  successRows: 1,
  warningRows: 0,
  errorRows: 0,
  fileWarningCount: 0,
  tableWarningCount: 0,
};
const period: shared.PeriodContext = {
  currentDate: "2026-07-15",
  currentMonth: "2026-07",
  timezone: "Asia/Seoul",
  cashBaseMonth: "2026-01",
  monthClose: { month: "2026-07", status: "작업중" },
};
beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  });
  window.scrollTo = vi.fn();
  vi.mocked(shared.getPeriodContext).mockResolvedValue(period);
  vi.mocked(shared.getMonthClose).mockResolvedValue({ items: [] });
  vi.mocked(shared.getSharedCodes).mockImplementation(async (group) => ({
    items: (group === "EMP_TYPE"
      ? [
          { code: "FT", name: "정규직" },
          { code: "OS", name: "외주" },
        ]
      : [{ code: "INDRCT", name: "간접" }]
    ).map((r) => ({
      ...r,
      addVal1: null,
      addVal2: null,
      addVal3: null,
      codeVal: null,
      unitNm: null,
    })),
  }));
  vi.mocked(api.getPayroll).mockImplementation(async (query) => ({
    ...query,
    items: [row],
    total: 101,
    revision: "whole-month",
  }));
  vi.mocked(api.getPayrollUploads).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(people.getDevelopers).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(people.getPeopleCodes).mockResolvedValue({ items: [] });
  vi.mocked(issues.getUploadIssues).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
});
async function draw() {
  render(<PayrollDialog onClose={vi.fn()} />);
  await screen.findByText(row.developerName);
  await waitFor(() =>
    expect(screen.queryByTestId("payroll-read-only")).not.toBeInTheDocument(),
  );
}
it("저장 구분과 입력·계산값은 readonly, 기본천원·정밀도·음수·NULL 표시", async () => {
  await draw();
  expect(screen.getByText("정규직")).toHaveClass("sicost-tone-neutral");
  expect(screen.getByText("간접")).toBeInTheDocument();
  expect(screen.queryByLabelText("직원구분")).not.toBeInTheDocument();
  expect(screen.getByText("999,999,999,999.999")).toBeInTheDocument();
  expect(screen.getByText("−0.001")).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByLabelText("단위"), "백만원");
  expect(screen.getByText("999,999,999.999999")).toBeInTheDocument();
  expect(screen.getByText("−0.000001")).toBeInTheDocument();
  expect(api.getPayroll).toHaveBeenCalledOnce();
  vi.mocked(api.getPayroll).mockResolvedValue({
    month: row.month,
    items: [{ ...row, employeeType: "OS", realPayAmount: null }],
    total: 1,
    page: 1,
    size: 50,
    revision: "same",
  });
  await userEvent.click(screen.getByTestId("payroll-search-button"));
  expect(await screen.findByText("외주")).toBeInTheDocument();
  expect(screen.getByText("–")).toBeInTheDocument();
});
it("Enter 검색·크기 변경·월 변경은 page1, 급여창 월만 변경", async () => {
  await draw();
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(api.getPayroll).toHaveBeenLastCalledWith({
      month: "2026-07",
      q: "",
      page: 2,
      size: 50,
    }),
  );
  await userEvent.type(screen.getByLabelText("이름/관리번호"), " 관리번호 ");
  fireEvent.keyDown(screen.getByLabelText("이름/관리번호"), { key: "Enter" });
  await waitFor(() =>
    expect(api.getPayroll).toHaveBeenLastCalledWith({
      month: "2026-07",
      q: "관리번호",
      page: 1,
      size: 50,
    }),
  );
  await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
  await waitFor(() =>
    expect(api.getPayroll).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, size: 20 }),
    ),
  );
  fireEvent.change(screen.getByLabelText("연월"), {
    target: { value: "2026-06" },
  });
  await waitFor(() =>
    expect(api.getPayroll).toHaveBeenLastCalledWith(
      expect.objectContaining({ month: "2026-06", page: 1 }),
    ),
  );
  await userEvent.click(screen.getByTestId("payroll-reset"));
  await waitFor(() =>
    expect(api.getPayroll).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "", page: 1 }),
    ),
  );
});
it("서버조회 로딩·빈월·오류와 requestId 재조회 구분", async () => {
  vi.mocked(api.getPayroll).mockImplementationOnce(() => new Promise(() => {}));
  render(<PayrollDialog onClose={vi.fn()} />);
  expect(await screen.findByText("불러오는 중입니다.")).toBeInTheDocument();
  vi.mocked(api.getPayroll).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "pay-read",
    }),
  );
  await userEvent.click(screen.getByTestId("payroll-search-button"));
  expect(await screen.findByText(/문의 번호: pay-read/)).toBeInTheDocument();
  expect(screen.queryByText("선택월 급여 없음")).not.toBeInTheDocument();
  vi.mocked(api.getPayroll).mockResolvedValue({
    month: "2026-07",
    items: [],
    total: 0,
    page: 1,
    size: 50,
    revision: "empty-revision",
  });
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(await screen.findByText("선택월 급여 없음")).toBeInTheDocument();
});
it.each([
  ["2025-12", "지난 연도"],
  ["2026-08", "미래월"],
])("%s는 조회만 허용하고 검사 비활성", async (month, message) => {
  await draw();
  fireEvent.change(screen.getByLabelText("연월"), { target: { value: month } });
  await screen.findByText(new RegExp(message));
  await userEvent.click(screen.getByTestId("payroll-upload-open"));
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
  expect(api.createB1Validation).not.toHaveBeenCalled();
});
it("이관월은 보험·직원구분 입력 없이 조회만", async () => {
  vi.mocked(shared.getPeriodContext).mockResolvedValue({
    ...period,
    cashBaseMonth: "2026-07",
  });
  await draw();
  fireEvent.change(screen.getByLabelText("연월"), {
    target: { value: "2026-06" },
  });
  await screen.findByText("이관된 월은 조회만 할 수 있습니다.");
  await userEvent.click(screen.getByTestId("payroll-upload-open"));
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
});
it("실제 마감만 보호하며 상태조회 실패는 fail closed·재조회", async () => {
  vi.mocked(shared.getMonthClose).mockResolvedValue({
    items: [{ month: "2026-07", status: "마감" }],
  });
  render(<PayrollDialog onClose={vi.fn()} />);
  await screen.findByText("마감된 월은 조회만 할 수 있습니다.");
  await userEvent.click(screen.getByTestId("payroll-upload-open"));
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
});
it("마감 조회 실패에는 검사를 막고 임의 허용하지 않는다", async () => {
  vi.mocked(shared.getMonthClose).mockRejectedValue(
    new Error("마감 조회 실패"),
  );
  render(<PayrollDialog onClose={vi.fn()} />);
  await screen.findByText("마감 조회 실패");
  await userEvent.click(screen.getByTestId("payroll-upload-open"));
  expect(screen.getByRole("button", { name: "검사" })).toBeDisabled();
});
it("월 업로드 기록은 상태색·건수·진단 조회, CHECKED는 파일 재선택 안내", async () => {
  vi.mocked(api.getPayrollUploads).mockResolvedValue({
    items: [
      {
        uploadId: 71,
        month: "2026-07",
        fileName: "payroll.xlsx",
        actor: "admin",
        registeredAt: "2026-07-15T09:00:00",
        status: "CHECKED",
        summary,
        appliedAt: null,
      },
      {
        uploadId: 72,
        month: "2026-07",
        fileName: "applied.xlsx",
        actor: "admin",
        registeredAt: "2026-07-14T09:00:00",
        status: "APPLIED",
        summary,
        appliedAt: "2026-07-14T09:01:00",
      },
    ],
    total: 2,
    page: 1,
    size: 50,
  });
  vi.mocked(api.getPayrollResult).mockResolvedValue({
    uploadId: 71,
    month: "2026-07",
    formType: "B1",
    screenId: "UI-02",
    status: "CHECKED",
    summary,
    results: [],
    changed: false,
    revision: "whole",
  });
  await draw();
  await userEvent.click(screen.getByTestId("payroll-history-tab"));
  expect(await screen.findByText("payroll.xlsx")).toBeInTheDocument();
  expect(api.getPayrollUploads).toHaveBeenCalledWith("2026-07", 1, 50);
  expect(screen.getByText("검사 완료")).toHaveClass("sicost-tone-info");
  expect(screen.getByText("반영 완료")).toHaveClass("sicost-tone-ok");
  await userEvent.click(screen.getByTestId("payroll-result-open-71"));
  expect(
    await screen.findByText(/파일을 다시 선택하고 검사/),
  ).toBeInTheDocument();
  expect(api.getPayrollResult).toHaveBeenCalledWith(71);
  expect(api.createB1Apply).not.toHaveBeenCalled();
});
it("업로드 기록 빈목록·실패·재조회 구분", async () => {
  vi.mocked(api.getPayrollUploads).mockRejectedValueOnce(
    new Error("기록 실패"),
  );
  await draw();
  await userEvent.click(screen.getByTestId("payroll-history-tab"));
  await screen.findByText("기록 실패");
  await userEvent.click(screen.getByTestId("payroll-history-refresh"));
  expect(await screen.findByText("선택월 기록 없음")).toBeInTheDocument();
});
it("다운로드는 선택월·검색·표시단위 전달, 페이지와 원금 변경 없음", async () => {
  vi.mocked(api.exportPayroll).mockResolvedValue({
    blob: new Blob(),
    filename: "payroll.xlsx",
  });
  await draw();
  await userEvent.selectOptions(screen.getByLabelText("단위"), "원");
  await userEvent.click(screen.getByTestId("payroll-export"));
  await waitFor(() =>
    expect(api.exportPayroll).toHaveBeenCalledWith(
      { month: "2026-07", q: "", page: 1, size: 50 },
      expect.arrayContaining(["employeeType", "realPayAmount"]),
      "원",
    ),
  );
  expect(api.saveDownload).toHaveBeenCalledOnce();
  expect(row.payAmount).toBe("999999999999999");
});
it("모바일은 월내역만 조회하고 업로드·다운로드를 숨긴다", async () => {
  vi.mocked(window.matchMedia).mockReturnValue({
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  } as unknown as MediaQueryList);
  await draw();
  expect(screen.queryByTestId("payroll-upload-open")).not.toBeInTheDocument();
  expect(screen.queryByTestId("payroll-export")).not.toBeInTheDocument();
  expect(screen.getByText(/급여 업로드·다운로드는 PC/)).toBeInTheDocument();
});
it("기존 개발자/B8 버튼 보존·급여 닫기 후 필터와 호출 포커스 유지", async () => {
  render(<DeveloperPoolScreen />);
  await screen.findByText("조회된 자료가 없습니다.");
  expect(screen.getByTestId("b8-upload-button")).toBeInTheDocument();
  expect(screen.getByTestId("b8-template-button")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("developer-search-input"), {
    target: { value: "개발자조건" },
  });
  await userEvent.click(screen.getByTestId("developer-search-button"));
  await userEvent.click(screen.getByTestId("payroll-open"));
  await screen.findByText(row.developerName);
  const dialog = screen.getByRole("dialog", { name: "급여 (B1) / UI-02" });
  await userEvent.click(within(dialog).getByRole("button", { name: "닫기" }));
  expect(screen.getByTestId("developer-search-input")).toHaveValue(
    "개발자조건",
  );
  expect(screen.getByTestId("payroll-open")).toHaveFocus();
  expect(people.getDevelopers).toHaveBeenCalledTimes(2);
});
it("기간 조회 실패는 오류와 재조회 제공, 업무 API를 임의 월로 호출하지 않음", async () => {
  vi.mocked(shared.getPeriodContext).mockRejectedValueOnce(
    new Error("기간 실패"),
  );
  render(<PayrollActions />);
  await userEvent.click(screen.getByTestId("payroll-open"));
  await screen.findByText("기간 실패");
  expect(api.getPayroll).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText(row.developerName);
});
