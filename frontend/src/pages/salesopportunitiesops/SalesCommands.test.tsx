import {
  act,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { SalesOpportunityActions } from "./SalesOpportunityActions";
import { SalesQuotePanel } from "./SalesQuotePanel";
import { SalesPipelinePanel } from "./SalesPipelineScreen";
import { ApiError } from "../../shared/api";
import * as api from "./salesCommandsApi";
import { getSharedCodes } from "../shared/sharedApi";
import * as developers from "../developers/peopleClient";
vi.mock("../developers/peopleClient");

vi.mock("./salesCommandsApi");
vi.mock("../shared/sharedApi", async (original) => ({
  ...(await original<typeof import("../shared/sharedApi")>()),
  getSharedCodes: vi.fn(),
}));
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
it("카드의 인력 추천은 첫 직무·기회 시작월 query를 전달하고 상세를 열지 않는다", async () => {
  const o = opportunity();
  vi.mocked(api.getSalesOpportunities).mockResolvedValue({
    items: [o],
    total: 1,
    page: 1,
    size: 50,
    stageTotals: [],
    summary: {
      opportunityCount: 1,
      staleCount: 0,
      amount: o.amount,
      weightedAmount: "0",
      weightedPeople: "0",
    },
  });
  render(
    <MemoryRouter>
      <SalesPipelinePanel />
    </MemoryRouter>,
  );
  const link = await screen.findByTestId("opportunity-recommend-o-0101");
  expect(link).toHaveAccessibleName("인력 추천으로 이동");
  const url = new URL(link.getAttribute("href")!, "https://test.invalid");
  expect(Object.fromEntries(url.searchParams)).toEqual({
    targetType: "OPPORTUNITY",
    targetId: "O-0101",
    jobCode: "BE",
    startMonth: "2026-08",
  });
  await userEvent.click(link);
  expect(api.getSalesOpportunity).not.toHaveBeenCalled();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("종료 탭의 수주 프로젝트 링크는 해당 프로젝트 query를 보존한다", async () => {
  const o = { ...opportunity(), status: "WIN" as const, projectId: "P-900009" };
  vi.mocked(api.getSalesOpportunities).mockResolvedValue({
    items: [o],
    total: 1,
    page: 1,
    size: 50,
    stageTotals: [],
    summary: {
      opportunityCount: 1,
      staleCount: 0,
      amount: o.amount,
      weightedAmount: "0",
      weightedPeople: "0",
    },
  });
  render(
    <MemoryRouter>
      <SalesPipelinePanel />
    </MemoryRouter>,
  );
  await userEvent.click(screen.getByRole("tab", { name: "종료 · 수주·실주" }));
  expect(await screen.findByTestId("closed-project-o-0101")).toHaveAttribute(
    "href",
    "/UI-01?projectId=P-900009",
  );
});
it.each(["단가가 원가보다 낮음", null])(
  "반려 띠는 가장 최근 반려 날짜와 선택 사유를 표시한다: %s",
  (reason) => {
    const o = opportunity();
    o.approvalStatus = "REJ";
    o.approvalHistory = [
      {
        histSn: 1,
        action: "REJ",
        profitRate: "10",
        reason: "이전 사유",
        registeredBy: "sales",
        registeredAt: "2026-11-15T09:00:00",
      },
      {
        histSn: 3,
        action: "REJ",
        profitRate: "10",
        reason,
        registeredBy: "sales",
        registeredAt: "2026-11-16T09:00:00",
      },
    ];
    mount(o);
    const notice = within(screen.getByTestId("quote-rejection-banner"));
    expect(notice.getByText("견적 반려 (11-16)")).toBeVisible();
    expect(notice.queryByText("사유: 이전 사유")).not.toBeInTheDocument();
    if (reason) expect(notice.getByText(`사유: ${reason}`)).toBeVisible();
    else expect(notice.queryByText(/^사유:/)).not.toBeInTheDocument();
  },
);
it("필요 인력 표와 현재 가배정 정보를 표시하고 이동 이벤트 후 이전 기회에서 제거한다", async () => {
  const o = opportunity();
  o.needs[0] = { ...o.needs[0], needSn: 2, expectedRate: "8000000" };
  o.tentativeAssignments = [
    {
      id: "7",
      developerId: "D-3",
      status: "ACT",
      needSn: 2,
      startMonth: "2026-08",
      sourceScreenId: "UI-05",
    },
  ];
  vi.mocked(developers.getDeveloper).mockResolvedValue({
    name: "업로드03",
    managementNo: "0003",
  } as developers.DeveloperDetail);
  vi.mocked(api.getSalesOpportunity).mockImplementation(async () => o);
  vi.mocked(api.getSalesOpportunities).mockImplementation(async () => ({
    items: [o],
    total: 1,
    page: 1,
    size: 50,
    stageTotals: [],
    summary: {
      opportunityCount: 1,
      staleCount: 0,
      amount: o.amount,
      weightedAmount: "0",
      weightedPeople: "0",
    },
  }));
  render(
    <MemoryRouter>
      <SalesPipelinePanel />
    </MemoryRouter>,
  );
  const tentativeCount = await screen.findByTestId("opportunity-tentative-count-o-0101");
  expect(within(tentativeCount).getByText("1건")).toBeVisible();
  await userEvent.click(screen.getByTestId("opportunity-open-o-0101"));
  await userEvent.click(await screen.findByTestId("detail-tab-needs"));
  expect(screen.getByTestId("opportunity-needs-table")).toHaveTextContent(
    "8,000,000",
  );
  const table = screen.getByTestId("tentative-assignments-panel");
  await within(table).findByText("업로드03");
  expect(table).toHaveTextContent("0003");
  expect(table).toHaveTextContent("2. 백엔드 개발");
  expect(table).toHaveTextContent("UI-05");
  o.tentativeAssignments = [{ ...o.tentativeAssignments[0], status: "REL" }];
  act(() => {
    window.dispatchEvent(new Event("sicost:staffing-committed"));
  });
  await within(tentativeCount).findByText("0건");
  await within(table).findByText("가배정한 사람이 없습니다");
  expect(within(table).queryByText("업로드03")).not.toBeInTheDocument();
});
it("상태 버튼으로 견적 탭에 들어가 그 자리에서 승인하고 이력을 확인한다", async () => {
  const o = opportunity();
  const lookup = vi.mocked(getSharedCodes).getMockImplementation()!;
  vi.mocked(getSharedCodes).mockImplementation(async (group, ...args) => {
    if (group !== "APRV_STAT") return lookup(group, ...args);
    return { items: [
      { code: "WAIT", name: "승인 대기", addVal1: null, addVal2: null, addVal3: null, codeVal: null, unitNm: null },
    ] };
  });
  o.nextTask = "제안서 전달";
  o.approvalHistory = [{
    histSn: 1,
    action: "REQ",
    profitRate: "10.00",
    reason: "견적 검토 요청",
    registeredAt: "2026-11-26T08:00:00+09:00",
    registeredBy: "TEAM01",
  }];
  vi.mocked(api.getSalesOpportunities).mockResolvedValue({
    items: [o], total: 1, page: 1, size: 50, stageTotals: [],
    summary: { opportunityCount: 1, staleCount: 0, amount: o.amount,
      weightedAmount: "0", weightedPeople: "0" },
  });
  vi.mocked(api.getSalesOpportunity).mockResolvedValue(o);
  render(<MemoryRouter><SalesPipelinePanel /></MemoryRouter>);

  const opener = await screen.findByTestId("opportunity-open-o-0101");
  await userEvent.click(opener);
  const drawer = await screen.findByRole("dialog", { name: o.name });
  const detail = within(drawer);
  const header = drawer.querySelector("header")!;
  expect(within(header).getByRole("button", { name: "수정" })).toBeVisible();
  expect(within(header).getByRole("button", { name: "닫기" })).toBeVisible();
  const basic = detail.getByRole("tabpanel", { name: "기본 정보" });
  expect(within(basic).getByText("제안서 전달")).toBeVisible();
  expect(detail.queryByRole("button", { name: "실주 처리" })).not.toBeInTheDocument();

  await userEvent.click(await within(header).findByRole("button", { name: "승인 대기" }));
  const quoteTab = detail.getByRole("tab", { name: "견적 점검/승인 이력" });
  expect(quoteTab).toHaveFocus();
  const quotePanel = within(detail.getByRole("tabpanel", { name: "견적 점검/승인 이력" }));
  expect(within(quotePanel.getByRole("table", { name: "승인 이력" }))
    .getByRole("cell", { name: "견적 검토 요청" })).toBeVisible();
  const rejectButton = quotePanel.getByRole("button", { name: "반려" });
  expect(rejectButton).toBeEnabled();
  const quoteTable = within(quotePanel.getByTestId("sales-quote-panel")).getByRole("table");
  expect(rejectButton.compareDocumentPosition(quoteTable) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(quotePanel.queryByRole("button", { name: "실주 처리" })).not.toBeInTheDocument();

  await userEvent.keyboard("{ArrowRight}");
  expect(detail.getByRole("tab", { name: "기회 관리" })).toHaveFocus();
  const management = within(detail.getByRole("tabpanel", { name: "기회 관리" }));
  expect(management.getByRole("combobox", { name: "단계 선택" })).toBeVisible();
  expect(management.getByRole("button", { name: "기본 확률 사용" })).toBeEnabled();
  expect(management.getByRole("button", { name: "수주 처리" })).toBeDisabled();
  expect(management.queryByRole("button", { name: "승인" })).not.toBeInTheDocument();
  await userEvent.click(detail.getByRole("button", { name: "닫기" }));
  expect(opener).toHaveFocus();

  const cardStatus = screen.getByTestId("opportunity-approval-o-0101");
  await userEvent.click(cardStatus);
  const reopened = within(await screen.findByRole("dialog", { name: o.name }));
  expect(reopened.getByRole("tab", { name: "견적 점검/승인 이력" })).toHaveAttribute("aria-selected", "true");
  vi.mocked(api.createSalesOpportunityApproval).mockImplementationOnce(async () => {
    vi.mocked(api.getSalesOpportunity).mockResolvedValue({
      ...o,
      approvalStatus: "OK",
      approvalHistory: [...o.approvalHistory, { ...o.approvalHistory[0], histSn: 2, action: "OK", reason: null }],
    });
    return saved;
  });
  await userEvent.click(reopened.getByRole("button", { name: "승인" }));
  const approval = within(screen.getByRole("dialog", { name: "견적 승인" }));
  await userEvent.click(approval.getByRole("button", { name: "확인" }));
  expect(await reopened.findByTestId("approval-history-2")).toBeVisible();
  expect(reopened.getByText("저장했습니다.")).toBeVisible();
  expect(reopened.getByRole("tab", { name: "견적 점검/승인 이력" })).toHaveAttribute("aria-selected", "true");
  expect(api.createSalesOpportunityApproval).toHaveBeenCalledTimes(1);

});

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
  vi.mocked(getSharedCodes).mockImplementation(async (group) => ({
    items:
      group === "JOB"
        ? [
            {
              code: "BE",
              name: "백엔드 개발",
              addVal1: null,
              addVal2: null,
              addVal3: null,
              codeVal: null,
              unitNm: null,
            },
          ]
        : [],
  }));
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
    screen.getByText(/견적 원가 미확인 또는 승인 대기\/반려/),
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
  const confirm = within(
    screen.getByRole("dialog", { name: "이 기회를 삭제할까요?" }),
  ).getByRole("button", { name: "삭제" });
  expect(confirm).toHaveClass("sicost-danger");
  await userEvent.click(confirm);
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
  ).toHaveAttribute("href", "/UI-01?projectId=P-live");
});
it("lose loads existing reason codes; missing required reason blocks submit", async () => {
  mount();
  await userEvent.click(screen.getByRole("button", { name: "실주 처리" }));
  await screen.findByRole("option", { name: "가격" });
  const confirm = within(
    screen.getByRole("dialog", { name: "이 기회를 실주 처리할까요?" }),
  ).getByRole("button", { name: "실주 처리" });
  expect(confirm).toHaveClass("sicost-danger");
  await userEvent.click(confirm);
  expect(api.createSalesOpportunityLoss).not.toHaveBeenCalled();
  expect(screen.getByRole("alert")).toHaveTextContent("실주 사유");
  await userEvent.selectOptions(screen.getByLabelText("실주 사유"), "PRICE");
  await userEvent.click(confirm);
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

it("견적 미리보기 버튼은 등록/수정 폼 저장을 제출하지 않는다", async () => {
  vi.mocked(api.createSalesOpportunityQuotePreview).mockResolvedValue(quote());
  const submit = vi.fn((event: React.FormEvent) => event.preventDefault());
  render(
    <form onSubmit={submit}>
      <SalesQuotePanel needs={[{ jobCode: "BE" }]} />
    </form>,
  );
  await userEvent.click(screen.getByTestId("quote-preview-button"));
  await screen.findByTestId("quote-row-1");
  expect(submit).not.toHaveBeenCalled();
  expect(api.createSalesOpportunityQuotePreview).toHaveBeenCalledTimes(1);
});
it("후보 맞는 자리의 query 링크는 해당 기회 상세를 연다", async () => {
  const value = opportunity();
  vi.mocked(api.getSalesOpportunity).mockResolvedValue(value);
  vi.mocked(api.getSalesOpportunities).mockResolvedValue({
    items: [value],
    total: 1,
    page: 1,
    size: 50,
    stageTotals: [],
    summary: {
      opportunityCount: 1,
      staleCount: 0,
      amount: "10000000",
      weightedAmount: "3000000",
      weightedPeople: "0.3",
    },
  });
  render(
    <MemoryRouter initialEntries={["/UI-29?opportunityId=O-0101"]}>
      <SalesPipelinePanel />
    </MemoryRouter>,
  );
  expect(await screen.findByRole("dialog")).toBeInTheDocument();
  await waitFor(() =>
    expect(api.getSalesOpportunity).toHaveBeenCalledWith("O-0101"),
  );
  expect(
    await screen.findByTestId("opportunity-edit-button"),
  ).toBeInTheDocument();
});

it("수주 모달은 서버 견적 수량의 직무합계와 큰 원정수 차이를 정확히 표시한다", async () => {
  const o = opportunity();
  o.approvalStatus = "OK";
  o.amount = "999999999999999";
  o.quote.revenue = "900719925474099";
  o.quote.rows = [
    { ...o.quote.rows[0], quantityMm: "1.25" },
    { ...o.quote.rows[0], needSn: 2, quantityMm: "0.10" },
  ];
  mount(o);
  await userEvent.click(screen.getByTestId("win-open-button"));
  await waitFor(() =>
    expect(screen.getByTestId("win-plan-totals")).toHaveTextContent(
      "백엔드 개발: 1.35 MM",
    ),
  );
  expect(screen.getByTestId("win-amount-difference")).toHaveTextContent(
    "99,280,074,525,900 원",
  );
  await userEvent.clear(screen.getByTestId("win-amount-input"));
  await userEvent.type(screen.getByTestId("win-amount-input"), " 1,234,567 ");
  await userEvent.click(screen.getByRole("button", { name: "수주 확정" }));
  await waitFor(() =>
    expect(api.createSalesOpportunityWin).toHaveBeenCalledWith(
      o,
      expect.objectContaining({ contractAmount: "1234567" }),
    ),
  );
});
it("수주 중 견적 변경을 만나면 원인과 저장 경로를 한 번 안내하고 입력을 유지한다", async () => {
  const o = opportunity();
  o.approvalStatus = "OK";
  vi.mocked(api.createSalesOpportunityWin).mockRejectedValueOnce(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "QUOTE_CHANGED",
      message: "견적을 갱신해 주세요. 먼저 기회를 저장해 주세요.",
      requestId: "win-quote-trace",
    }),
  );
  mount(o);
  await userEvent.click(screen.getByTestId("win-open-button"));
  const dialog = within(screen.getByRole("dialog", { name: "수주 처리" }));
  const amount = dialog.getByTestId("win-amount-input");
  await userEvent.clear(amount);
  await userEvent.type(amount, "72000000");
  await userEvent.click(dialog.getByRole("button", { name: "수주 확정" }));

  const notice = await dialog.findByTestId("sales-quote-changed-notice");
  expect(dialog.getAllByRole("alert")).toEqual([notice]);
  expect(notice).toHaveTextContent("수주 처리 전에 견적을 다시 저장해 주세요");
  expect(notice).toHaveTextContent("저장된 견적과 현재 기준으로 다시 계산한 결과가 달라");
  expect(within(notice).getAllByRole("listitem")).toHaveLength(2);
  expect(notice).toHaveTextContent("요청 ID win-quote-trace");
  expect(screen.queryByText("견적을 갱신해 주세요. 먼저 기회를 저장해 주세요.")).not.toBeInTheDocument();
  expect(amount).toHaveValue("72000000");
  expect(api.createSalesOpportunityWin).toHaveBeenCalledTimes(1);
  expect(refresh).not.toHaveBeenCalled();
});

it.each(["12,34", "1e6", "1000000000000000", "0", "-1"])(
  "수주 금액 잘못된 구분/경계 %s는 저장하지 않는다",
  async (amount) => {
    const o = opportunity();
    o.approvalStatus = "OK";
    mount(o);
    await userEvent.click(screen.getByTestId("win-open-button"));
    await userEvent.clear(screen.getByTestId("win-amount-input"));
    await userEvent.type(screen.getByTestId("win-amount-input"), amount);
    await userEvent.click(screen.getByRole("button", { name: "수주 확정" }));
    expect(api.createSalesOpportunityWin).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("양수 원정수");
  },
);
it.each([
  ["1", "1"],
  ["999,999,999,999,999", "999999999999999"],
])(
  "수주 금액 양수 경계 %s는 정확한 원문자로 전송한다",
  async (amount, expected) => {
    const o = opportunity();
    o.approvalStatus = "OK";
    mount(o);
    await userEvent.click(screen.getByTestId("win-open-button"));
    await userEvent.clear(screen.getByTestId("win-amount-input"));
    await userEvent.type(screen.getByTestId("win-amount-input"), amount);
    await userEvent.click(screen.getByRole("button", { name: "수주 확정" }));
    await waitFor(() =>
      expect(api.createSalesOpportunityWin).toHaveBeenCalledWith(
        o,
        expect.objectContaining({ contractAmount: expected }),
      ),
    );
  },
);
