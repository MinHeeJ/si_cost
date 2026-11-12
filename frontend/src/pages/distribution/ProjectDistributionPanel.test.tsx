import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { ProjectDistributionPanel } from "./ProjectDistributionPanel";
import { formatDistributionAmount } from "./MonthlyMatrixTable";
import * as api from "./distributionApi";
import * as issues from "../uploadsops/uploadIssuesApi";
vi.mock("./distributionApi");
vi.mock("../uploadsops/uploadIssuesApi");
const project: api.DistributionProject = {
  id: "P-DIST",
  name: "검증 프로젝트",
  customerId: "C-DIST",
  contractType: "TK",
  status: "ING",
  startDate: "2026-06-01",
  endDate: "2026-07-31",
  workplace: null,
  feature: null,
  estimateAmount: null,
  originalContractAmount: "101",
  contractAmount: "101",
  budgetAmount: null,
  teamExpenseAmount: "0",
  projectExpenseAmount: "0",
  budgetManual: "N",
  lossDate: null,
  lossReason: null,
  lossMemo: null,
  opportunityId: null,
};
const data: api.ProjectDistributionData = {
  project,
  viewedStep: "EST",
  selectedStep: "PLAN",
  months: ["2026-06", "2026-07"],
  revision: "aggregate-revision",
  rows: [
    {
      rowSn: 1,
      rowType: "DEV",
      developerId: "D-DIST",
      managementNo: "M-DIST",
      name: "검증 개발자",
      itemName: null,
      jobNames: ["PM", "백엔드"],
      useYn: "N",
      cells: [
        { month: "2026-06", amount: "100" },
        { month: "2026-07", amount: "0" },
      ],
      totalAmount: "100",
    },
  ],
  monthlyTotals: [
    { month: "2026-06", amount: "100" },
    { month: "2026-07", amount: "0" },
  ],
  totalAmount: "100",
};
const comparison: api.ProjectDistributionComparison = {
  selectedStep: "PLAN",
  contractAmount: "101",
  totalAmount: "0",
  differenceAmount: "101",
  mismatchCount: 1,
  items: [
    {
      developerId: "D-DIST",
      managementNo: "M-DIST",
      name: "검증 개발자",
      month: "2026-06",
      type: "NO_ASSIGNMENT",
      amount: "100",
      actualMm: "0.00",
    },
  ],
  warnings: ["계약 차이 경고", "배정·배분표 불일치 1건"],
};
const upload: api.ProjectUploadRecord = {
  uploadId: 71,
  step: "EST",
  fileName: "첫파일.xlsx",
  actor: "등록 처리자",
  registeredAt: "2026-06-15 12:00:00",
  status: "CHECKED",
  appliedAt: null,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getProjectDistribution).mockImplementation(
    async (_id, step) => ({ ...data, viewedStep: step }),
  );
  vi.mocked(api.getProjectDistributionComparison).mockResolvedValue(comparison);
  vi.mocked(api.getProjectDistributionUploads).mockResolvedValue({
    items: [upload, { ...upload, uploadId: 70, status: "APPLIED" }],
  });
  vi.mocked(issues.getUploadIssues).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(api.getDistributionUploadResult).mockResolvedValue({
    uploadId: 71,
    formType: "B6",
    status: "CHECKED",
    projectId: project.id,
    step: "EST",
    summary: {
      totalRows: 1,
      successRows: 1,
      warningRows: 0,
      errorRows: 0,
      fileWarningCount: 0,
      tableWarningCount: 1,
    },
    results: [],
    revision: null,
    changed: false,
  });
});
function show(
  props: Partial<React.ComponentProps<typeof ProjectDistributionPanel>> = {},
) {
  const onBack = vi.fn(),
    onUpload = vi.fn();
  render(
    <ProjectDistributionPanel
      projectId={project.id}
      customerName="검증 고객사"
      onBack={onBack}
      onUpload={onUpload}
      {...props}
    />,
  );
  return { onBack, onUpload };
}
it("renders real header matrix readonly cells and independent calculation stage", async () => {
  show();
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  await screen.findByText("검증 고객사", { exact: false });
  expect(screen.getByTestId("distribution-project-header")).toHaveTextContent(
    "2026-06-01 ~ 2026-07-31",
  );
  expect(screen.getByRole("tab", { name: "EST 견적" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(screen.getByText("PLAN", { exact: true })).toHaveClass(
    "sicost-tone-info",
  );
  expect(screen.getByTestId("distribution-row-1")).toHaveTextContent(
    "PM·백엔드",
  );
  expect(screen.getByText("중지")).toHaveClass("sicost-tone-lock");
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "행 추가" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByTestId("distribution-comparison-banner"),
  ).toHaveTextContent("불일치 1건");
  expect(
    screen.getByRole("heading", { name: "배정·배분표 불일치 1건" }),
  ).toBeInTheDocument();
});
it("viewing another stage fetches only GET data and never switches calculation stage", async () => {
  show();
  await screen.findByRole("tab", { name: "PLAN 실제 투입계획" });
  fireEvent.click(screen.getByRole("tab", { name: "PLAN 실제 투입계획" }));
  await waitFor(() =>
    expect(api.getProjectDistribution).toHaveBeenLastCalledWith(
      project.id,
      "PLAN",
    ),
  );
  expect(await screen.findByRole("tabpanel")).toHaveAttribute(
    "aria-label",
    "PLAN 배분표",
  );
});
it("uses exact decimal unit conversion without changing API data", async () => {
  const change = vi.fn();
  show({ onAmountUnitChange: change });
  await screen.findByTestId("distribution-unit");
  fireEvent.change(screen.getByLabelText("배분표 금액 단위"), {
    target: { value: "원" },
  });
  expect(change).toHaveBeenCalledWith("원");
  expect(screen.getByTestId("distribution-row-1")).toHaveTextContent("100");
  expect(api.getProjectDistribution).toHaveBeenCalledTimes(1);
  expect(formatDistributionAmount("1999999999999998", "천원")).toBe(
    "1,999,999,999,999.998",
  );
  expect(formatDistributionAmount("-1", "백만원")).toBe("−0.000001");
  expect(formatDistributionAmount("0", "원")).toBe("0");
  expect(formatDistributionAmount(null, "원")).toBe("–");
});
it("empty table comparison and upload history have distinct empty states", async () => {
  vi.mocked(api.getProjectDistribution).mockResolvedValue({
    ...data,
    rows: [],
    selectedStep: null,
    totalAmount: "0",
  });
  vi.mocked(api.getProjectDistributionComparison).mockResolvedValue({
    ...comparison,
    items: [],
    mismatchCount: 0,
    warnings: [],
  });
  vi.mocked(api.getProjectDistributionUploads).mockResolvedValue({ items: [] });
  show();
  expect(
    await screen.findByText("이 단계에 등록된 배분표가 없습니다."),
  ).toBeInTheDocument();
  expect(screen.getByText("업로드 기록이 없습니다.")).toBeInTheDocument();
  expect(
    screen.getByText("배정·배분표 불일치가 없습니다."),
  ).toBeInTheDocument();
  expect(screen.getByText("미등록")).toHaveClass("sicost-tone-neutral");
});
it("failed request retains request id with manual retry and no automatic retries", async () => {
  vi.mocked(api.getProjectDistribution).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "request-dist",
    }),
  );
  show();
  expect(await screen.findByRole("alert")).toHaveTextContent("request-dist");
  expect(api.getProjectDistribution).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByTestId("distribution-matrix");
  expect(api.getProjectDistribution).toHaveBeenCalledTimes(2);
});
it.each(["END", "LOSE"] as const)(
  "%s project cannot upload but can view or download",
  async (status) => {
    vi.mocked(api.getProjectDistribution).mockResolvedValue({
      ...data,
      project: { ...project, status },
    });
    show();
    await screen.findByText(
      "조회 전용입니다. 배분표는 열람·다운로드할 수 있습니다.",
    );
    expect(screen.getByRole("button", { name: "엑셀 업로드" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "배분표 다운로드" }),
    ).toBeEnabled();
    expect(screen.getByRole("button", { name: "양식 받기" })).toBeEnabled();
  },
);
it("past selected year disables upload without changing lifecycle", async () => {
  show({ readOnly: true });
  await screen.findByTestId("distribution-matrix");
  expect(screen.getByRole("button", { name: "엑셀 업로드" })).toBeDisabled();
  expect(screen.getByTestId("distribution-project-header")).toHaveTextContent(
    "진행",
  );
});
it("uploads delegate to the existing B6 workflow with viewed step and aggregate revision; apply refreshes all reads", async () => {
  const { onUpload } = show();
  await screen.findByTestId("distribution-matrix");
  fireEvent.click(screen.getByRole("button", { name: "엑셀 업로드" }));
  expect(onUpload).toHaveBeenCalledWith(
    expect.objectContaining({
      projectId: project.id,
      step: "EST",
      expectedRevision: data.revision,
    }),
  );
  onUpload.mock.calls[0][0].onApplied();
  await waitFor(() =>
    expect(api.getProjectDistributionUploads).toHaveBeenCalledTimes(2),
  );
  expect(api.getProjectDistributionComparison).toHaveBeenCalledTimes(2);
});
it("list button restores the parent list instead of navigating to a new URL", async () => {
  const { onBack } = show();
  await screen.findByTestId("distribution-matrix");
  fireEvent.click(screen.getByRole("button", { name: "목록" }));
  expect(onBack).toHaveBeenCalledOnce();
});
it("template excludes year and export uses viewed stage and selected unit", async () => {
  show();
  await screen.findByTestId("distribution-matrix");
  fireEvent.click(screen.getByRole("button", { name: "양식 받기" }));
  await waitFor(() =>
    expect(api.downloadDistributionTemplate).toHaveBeenCalledWith(project.id),
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "배분표 다운로드" }),
    ).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole("button", { name: "배분표 다운로드" }));
  await waitFor(() =>
    expect(api.downloadDistribution).toHaveBeenCalledWith(
      project.id,
      "EST",
      "천원",
    ),
  );
});
it("historical stage and both status tones are preserved and result is queried by actual upload id", async () => {
  show();
  await screen.findByTestId("distribution-result-71");
  expect(screen.getByText("검사 완료")).toHaveClass("sicost-tone-info");
  expect(screen.getByText("반영 완료")).toHaveClass("sicost-tone-ok");
  fireEvent.click(screen.getByTestId("distribution-result-71"));
  const dialog = await screen.findByRole("dialog", {
    name: "배분표 업로드 결과",
  });
  await waitFor(() =>
    expect(api.getDistributionUploadResult).toHaveBeenCalledWith(71),
  );
  expect(
    await within(dialog).findByText("계산표 경고 1건"),
  ).toBeInTheDocument();
  expect(issues.getUploadIssues).toHaveBeenCalledWith(71, "", 1, 50);
  fireEvent.click(within(dialog).getAllByRole("button", { name: "닫기" })[0]);
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("result retrieval failure does not mutate or retry the recorded upload", async () => {
  vi.mocked(api.getDistributionUploadResult).mockRejectedValue(
    new Error("결과 조회 실패"),
  );
  show();
  await screen.findByTestId("distribution-result-71");
  fireEvent.click(screen.getByTestId("distribution-result-71"));
  expect(await screen.findByRole("alert")).toHaveTextContent("결과 조회 실패");
  expect(api.getDistributionUploadResult).toHaveBeenCalledTimes(1);
});
it.each(["comparison", "uploads"] as const)(
  "%s failure is not displayed as an empty successful read and manual retry refreshes all sources",
  async (source) => {
    const error = new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "관련 자료 조회 실패",
      requestId: `distribution-${source}`,
    });
    if (source === "comparison")
      vi.mocked(api.getProjectDistributionComparison).mockRejectedValueOnce(
        error,
      );
    else
      vi.mocked(api.getProjectDistributionUploads).mockRejectedValueOnce(error);
    show();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      `distribution-${source}`,
    );
    expect(
      screen.queryByText("업로드 기록이 없습니다."),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText("배정·배분표 불일치가 없습니다."),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "엑셀 업로드" })).toBeDisabled();
    expect(api.getProjectDistributionComparison).toHaveBeenCalledTimes(1);
    expect(api.getProjectDistributionUploads).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByTestId("distribution-matrix");
    expect(
      screen.getByTestId("distribution-comparison-banner"),
    ).toHaveTextContent("불일치 1건");
    expect(
      screen.getByTestId("distribution-upload-row-71"),
    ).toBeInTheDocument();
    expect(api.getProjectDistribution).toHaveBeenCalledTimes(2);
    expect(api.getProjectDistributionComparison).toHaveBeenCalledTimes(2);
    expect(api.getProjectDistributionUploads).toHaveBeenCalledTimes(2);
  },
);
