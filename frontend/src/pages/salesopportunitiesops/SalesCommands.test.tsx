import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { SalesOpportunityActions } from "./SalesOpportunityActions";
import { SalesQuotePanel } from "./SalesQuotePanel";
import { SalesPipelinePanel } from "./SalesPipelineScreen";
import { ApiError } from "../../shared/api";
import * as api from "./salesCommandsApi";

vi.mock("./salesCommandsApi");
vi.mock("../forecast/SalesForecastScreen", () => ({
  SalesForecastPanel: () => <p>매출·인력 예측 결과</p>,
}));
function quote(): api.Quote {
  return {
    rows: [
      {
        needSn: 1,
        jobCode: "BE",
        gradeCode: "MD",
        quantityMm: "1.00",
        effectiveRate: "10000000",
        unitMonthlyCost: "9000000",
        revenue: "10000000",
        cost: "9000000",
        minimumRate: "9000000",
        targetRate: "11250000",
        costBasisMonth: "2026-06",
        cohort: "GRADE",
        cohortCount: 1,
        fallbackUsed: true,
        complete: true,
        unknownReason: null,
      },
    ],
    issues: [],
    revenue: "10000000",
    cost: "9000000",
    rawProfitRate: "10.00",
    storedProfitRate: "10.00",
    minimumAmount: "9000000",
    targetAmount: "12000000",
    targetUnavailableReason: null,
    judgement: "APPROVAL_REQUIRED",
    standardMonth: "2026-07",
    approvalThreshold: "15",
    reapprovalGap: "0.5",
    targetMargin: "20",
    formula: "(견적매출−원가)/견적매출×100",
  };
}
function opportunity(): api.Opportunity {
  return {
    id: "O-0101",
    name: "기회 계약 검토",
    customerId: "C-live",
    partId: "PART-live",
    stage: "PROP",
    probability: "30.00",
    manualProbability: false,
    amount: "10000000",
    startMonth: "2026-08",
    contractType: null,
    months: 6,
    workplace: "서울",
    memo: "원본 비고",
    status: "ING",
    revision: "revision-live",
    needs: [
      {
        needSn: 1,
        jobCode: "BE",
        gradeCode: null,
        count: 1,
        startMonth: "2026-08",
        months: 6,
        monthlyMm: "1.00",
        expectedRate: null,
      },
    ],
    quote: quote(),
    approvalStatus: "WAIT",
    approvedProfitRate: null,
    projectId: null,
    resultDate: null,
    lossReasonCode: null,
    lossMemo: null,
    nextTask: null,
    nextDue: null,
    tentativeAssignments: [],
    approvalHistory: [],
  };
}
const refresh = vi.fn();
const saved = {
  changed: true,
  revision: "new-revision",
  warnings: [],
  opportunityId: "O-0101",
};
function mount(o = opportunity()) {
  return render(
    <MemoryRouter>
      <SalesOpportunityActions opportunity={o} refresh={refresh} />
    </MemoryRouter>,
  );
}
beforeEach(() => {
  vi.resetAllMocks();
  refresh.mockResolvedValue(undefined);
  vi.mocked(api.createSalesOpportunityApproval).mockResolvedValue(saved);
  vi.mocked(api.saveSalesOpportunityStage).mockResolvedValue(saved);
  vi.mocked(api.saveSalesOpportunityStop).mockResolvedValue(saved);
  vi.mocked(api.saveSalesOpportunityResume).mockResolvedValue(saved);
  vi.mocked(api.createSalesOpportunityLoss).mockResolvedValue(saved);
  vi.mocked(api.createSalesOpportunityWin).mockResolvedValue({
    ...saved,
    projectId: "P-live",
  });
  vi.mocked(api.getLossReasons).mockResolvedValue({
    items: [
      {
        code: "PRICE",
        name: "가격",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  );
});
it("WAIT hides upward entry and win but temporary admin can approve and reject", () => {
  mount();
  expect(screen.getByRole("button", { name: "승인" })).toBeEnabled();
  expect(screen.getByRole("button", { name: "반려" })).toBeEnabled();
  expect(screen.getByRole("button", { name: "수주 처리" })).toBeDisabled();
  expect(screen.getByRole("option", { name: "협상" })).toBeDisabled();
});
it("unknown is not zero and prevents approval", () => {
  const o = opportunity();
  o.quote.storedProfitRate = null;
  o.quote.judgement = "UNKNOWN";
  mount(o);
  expect(screen.getByRole("button", { name: "승인" })).toBeDisabled();
  expect(
    screen.getByText(/견적 원가 미확인 또는 승인 WAIT/),
  ).toBeInTheDocument();
});
it("rejection retains full optional reason, submits once, and refreshes only after commit", async () => {
  const o = opportunity();
  mount(o);
  await userEvent.click(screen.getByRole("button", { name: "반려" }));
  const input = screen.getByLabelText("반려 사유 (선택, 300자)");
  await userEvent.type(input, "조건 확인 필요");
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  await waitFor(() =>
    expect(api.createSalesOpportunityApproval).toHaveBeenCalledWith(
      o,
      "REJ",
      "조건 확인 필요",
    ),
  );
  expect(refresh).toHaveBeenCalledTimes(1);
});
it("approval confirmation cancellation writes nothing", async () => {
  mount();
  await userEvent.click(screen.getByRole("button", { name: "승인" }));
  expect(screen.getByRole("dialog")).toHaveTextContent(
    "현재 저장견적률 10.00%",
  );
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.createSalesOpportunityApproval).not.toHaveBeenCalled();
});
it("overlong reason shows field error without truncating or posting", async () => {
  mount();
  await userEvent.click(screen.getByRole("button", { name: "반려" }));
  await userEvent.type(
    screen.getByLabelText("반려 사유 (선택, 300자)"),
    "가".repeat(301),
  );
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("300자 이하");
  expect(screen.getByLabelText("반려 사유 (선택, 300자)")).toHaveValue(
    "가".repeat(301),
  );
  expect(api.createSalesOpportunityApproval).not.toHaveBeenCalled();
});
it("QUOTE_CHANGED keeps dialog and input, never automatically retries mutation", async () => {
  vi.mocked(api.createSalesOpportunityApproval).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "QUOTE_CHANGED",
      message: "견적을 갱신해 주세요",
      requestId: "quote-trace",
    }),
  );
  mount();
  await userEvent.click(screen.getByRole("button", { name: "반려" }));
  await userEvent.type(
    screen.getByLabelText("반려 사유 (선택, 300자)"),
    "보존",
  );
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("quote-trace");
  expect(screen.getByLabelText("반려 사유 (선택, 300자)")).toHaveValue("보존");
  expect(api.createSalesOpportunityApproval).toHaveBeenCalledTimes(1);
  expect(refresh).not.toHaveBeenCalled();
});
it("postcommit refresh failure does not repost and offers read retry through parent", async () => {
  refresh.mockRejectedValue(new Error("읽기 실패"));
  mount();
  await userEvent.click(screen.getByRole("button", { name: "삭제" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByRole("status")).toHaveTextContent(
    "저장은 됐지만 다시 불러오지 못했습니다",
  );
  expect(api.saveSalesOpportunityStop).toHaveBeenCalledTimes(1);
});
it("win uses source defaults and readonly identity; strict dates are field validated", async () => {
  const o = opportunity();
  o.approvalStatus = "OK";
  mount(o);
  await userEvent.click(screen.getByRole("button", { name: "수주 처리" }));
  expect(screen.getByLabelText("계약금액")).toHaveValue("10000000");
  expect(screen.getByLabelText("계약유형")).toHaveValue("TK");
  expect(screen.getByLabelText("시작일")).toHaveValue("2026-08-01");
  expect(screen.getByLabelText("종료일")).toHaveValue("2027-01-31");
  expect(screen.queryByLabelText("고객")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "수주 확정" }));
  await waitFor(() =>
    expect(api.createSalesOpportunityWin).toHaveBeenCalledWith(o, {
      contractAmount: "10000000",
      contractType: "TK",
      startDate: "2026-08-01",
      endDate: "2027-01-31",
      projectStatus: "ING",
    }),
  );
  expect(
    await screen.findByRole("link", { name: "프로젝트 P-live 보기" }),
  ).toHaveAttribute("href", "/UI-01");
});
it("lose loads existing reason codes; missing required reason blocks submit", async () => {
  mount();
  await userEvent.click(screen.getByRole("button", { name: "실주 처리" }));
  await screen.findByRole("option", { name: "가격" });
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(api.createSalesOpportunityLoss).not.toHaveBeenCalled();
  expect(screen.getByRole("alert")).toHaveTextContent("실주 사유");
  await userEvent.selectOptions(screen.getByLabelText("실주 사유"), "PRICE");
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  await waitFor(() =>
    expect(api.createSalesOpportunityLoss).toHaveBeenCalledWith(
      expect.objectContaining({ id: "O-0101" }),
      "PRICE",
      null,
    ),
  );
});
it.each(["WIN", "LOSE"] as const)("%s is readonly without resume", (status) => {
  const o = opportunity();
  o.status = status;
  mount(o);
  expect(
    screen.queryByRole("button", { name: "다시 사용" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "삭제" }),
  ).not.toBeInTheDocument();
  expect(screen.getByText(/종료 기회는 읽기 전용/)).toBeInTheDocument();
});
it("only STOP exposes resume with no old reservation restoration", async () => {
  const o = opportunity();
  o.status = "STOP";
  mount(o);
  await userEvent.click(screen.getByRole("button", { name: "다시 사용" }));
  expect(screen.getByRole("dialog")).toHaveTextContent(
    "해제한 가배정은 복원하지 않습니다",
  );
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  await waitFor(() =>
    expect(api.saveSalesOpportunityResume).toHaveBeenCalledWith(o),
  );
});
it("manual probability equal to default is explicitly manual and default button explicitly clears it", async () => {
  mount();
  await userEvent.click(screen.getByRole("button", { name: "확률 직접 변경" }));
  expect(api.saveSalesOpportunityStage).toHaveBeenLastCalledWith(
    expect.anything(),
    { stage: "PROP", probability: "30.00", manualProbability: true },
  );
  await userEvent.click(screen.getByRole("button", { name: "기본 확률 사용" }));
  expect(api.saveSalesOpportunityStage).toHaveBeenLastCalledWith(
    expect.anything(),
    { stage: "PROP", manualProbability: false },
  );
});
it("quote preview uses unsaved needs and shows authoritative cohort and incomplete states", async () => {
  const q = quote();
  q.targetAmount = null;
  q.targetUnavailableReason = "목표율 100%";
  q.issues = [
    { field: "needs", needSn: 1, reason: "미완료 행", status: "INCOMPLETE" },
  ];
  vi.mocked(api.createSalesOpportunityQuotePreview).mockResolvedValue(q);
  const needs = [{ jobCode: "BE" }];
  render(<SalesQuotePanel needs={needs} />);
  await userEvent.click(screen.getByRole("button", { name: "견적 다시 점검" }));
  expect(await screen.findByText("목표율 100%")).toBeInTheDocument();
  expect(screen.getByTestId("quote-row-1")).toHaveTextContent(
    "2026-06 / 등급 전체 / 1명 / 보완",
  );
  expect(api.createSalesOpportunityQuotePreview).toHaveBeenCalledWith(needs);
});
it("pipeline displays loading, empty, failure and refresh without mutation", async () => {
  vi.mocked(api.getSalesOpportunities).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  render(
    <MemoryRouter>
      <SalesPipelinePanel />
    </MemoryRouter>,
  );
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  expect(await screen.findByRole("alert")).toHaveTextContent("조회 실패");
  vi.mocked(api.getSalesOpportunities).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
    stageTotals: [],
    summary: {
      opportunityCount: 0,
      staleCount: 0,
      amount: "0",
      weightedAmount: "0",
      weightedPeople: "0",
    },
  });
  await userEvent.click(screen.getByRole("button", { name: "재조회" }));
  await waitFor(() => expect(screen.getAllByText("기회 없음")).toHaveLength(4));
  expect(api.saveSalesOpportunityStage).not.toHaveBeenCalled();
});
