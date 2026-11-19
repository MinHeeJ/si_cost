import {
  render,
  screen,
  waitFor,
  within,
  fireEvent,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { SalesOpportunityForm } from "./SalesOpportunityForm";
import { ApiError } from "../../shared/api";
import * as api from "./salesOpportunityApi";
vi.mock("./salesOpportunityApi");
vi.mock("../salesopportunitiesops/SalesQuotePanel", () => ({
  SalesQuotePanel: () => <p>견적 점검</p>,
  SalesCommandError: ({ error }: { error: Error }) => (
    <p role="alert">{error.message}</p>
  ),
}));
const close = vi.fn(),
  saved = vi.fn();
const result = {
  changed: true,
  revision: "new",
  warnings: [],
  opportunityId: "O-0101",
};
function detail(): api.Opportunity {
  return {
    id: "O-0101",
    name: "기회 원본",
    customerId: "C-a",
    partId: "PART-a",
    stage: "FIND",
    probability: "10",
    manualProbability: false,
    amount: "10000000",
    startMonth: "2026-08",
    contractType: null,
    months: null,
    nextTask: "할 일",
    nextDue: "2020-01-01",
    workplace: "서울",
    memo: null,
    status: "ING",
    revision: "full-revision",
    needs: [
      {
        needSn: 7,
        jobCode: "BE",
        gradeCode: null,
        count: 2,
        startMonth: "2026-08",
        months: 6,
        monthlyMm: "0.5",
        expectedRate: null,
      },
    ],
    tentativeAssignments: [
      {
        id: "12",
        developerId: "D-a",
        needSn: 7,
        startMonth: "2026-08",
        status: "ACT",
      },
    ],
    approvalHistory: [],
    quote: {} as api.Opportunity["quote"],
    approvalStatus: "WAIT",
    approvedProfitRate: null,
    projectId: null,
    resultDate: null,
    lossReasonCode: null,
    lossMemo: null,
  };
}
function mount(value?: api.Opportunity) {
  render(
    <MemoryRouter>
      <SalesOpportunityForm detail={value} onClose={close} onSaved={saved} />
    </MemoryRouter>,
  );
}
async function ready() {
  await waitFor(() =>
    expect(screen.getByTestId("opportunity-form-save")).toBeEnabled(),
  );
}
beforeEach(() => {
  vi.resetAllMocks();
  saved.mockResolvedValue(undefined);
  vi.mocked(api.getClientOptions).mockResolvedValue({
    items: [{ id: "C-a", name: "고객사", type: "CUST" }],
  });
  vi.mocked(api.getSalesPartOptions).mockResolvedValue({
    items: [
      { id: "PART-a", name: "영업파트", hqYn: "N", firstEffectiveMonth: null },
    ],
  });
  const code = {
    code: "BE",
    name: "백엔드",
    addVal1: null,
    addVal2: "10000000",
    addVal3: null,
    codeVal: null,
    unitNm: null,
  };
  vi.mocked(api.getSharedCodes).mockImplementation(async (group) => ({
    items: group === "JOB" ? [code] : [{ ...code, code: "MD", name: "중급" }],
  }));
  vi.mocked(api.getPeriodContext).mockResolvedValue({
    currentDate: "2026-07-15",
    currentMonth: "2026-07",
    cashBaseMonth: "2026-01",
    timezone: "Asia/Seoul",
    monthClose: { month: "2026-07", status: "WORK" },
  });
  vi.mocked(api.getSharedParameter).mockResolvedValue({
    key: "STEP_PRBL_1",
    value: "10",
    unitNm: "%",
    effectiveMonth: "2026-01",
  });
  vi.mocked(api.createSalesOpportunity).mockResolvedValue(result);
  vi.mocked(api.putSalesOpportunity).mockResolvedValue(result);
  vi.mocked(api.getDevelopers).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 20,
  });
});
it("new form uses DB month and middle grade while independent needs start with six and monthly MM one", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("opportunity-months")).toHaveValue("6");
  expect(screen.getByTestId("opportunity-start-month")).toHaveValue("2026-07");
  expect(screen.getByLabelText("1번째 줄 등급")).toHaveValue("MD");
  expect(screen.getByLabelText("1번째 줄 월MM")).toHaveValue("1.00");
  fireEvent.change(screen.getByTestId("opportunity-months"), {
    target: { value: "12" },
  });
  expect(screen.getByLabelText("1번째 줄 개월")).toHaveValue("6");
  await userEvent.click(screen.getByRole("button", { name: "행 추가" }));
  expect(screen.getByLabelText("2번째 줄 개월")).toHaveValue("12");
});
it("edit preserves existing needSn nullable grade/months/rate and sends separate PUT with full revision", async () => {
  mount(detail());
  await ready();
  expect(screen.getByTestId("opportunity-name")).toHaveValue("기회 원본");
  expect(screen.getByTestId("opportunity-months")).toHaveValue("");
  expect(screen.getByTestId("opportunity-next-due")).toHaveClass(
    "sales-opportunity-overdue",
  );
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.putSalesOpportunity).toHaveBeenCalledWith(
      "O-0101",
      expect.objectContaining({
        expectedRevision: "full-revision",
        months: null,
        needs: [
          expect.objectContaining({
            needSn: 7,
            gradeCode: null,
            expectedRate: null,
          }),
        ],
      }),
    ),
  );
  expect(api.createSalesOpportunity).not.toHaveBeenCalled();
  expect(saved).toHaveBeenCalledWith(result);
});
it("cancellation and dirty continue editing never send writes or release tentative", async () => {
  mount(detail());
  await ready();
  await userEvent.type(screen.getByTestId("opportunity-name"), " 변경");
  await userEvent.click(screen.getByRole("button", { name: "해제" }));
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(screen.getByRole("dialog")).toHaveTextContent("저장하지 않은 내용");
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(close).not.toHaveBeenCalled();
  expect(screen.getByTestId("opportunity-name")).toHaveValue("기회 원본 변경");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(close).toHaveBeenCalledOnce();
  expect(api.putSalesOpportunity).not.toHaveBeenCalled();
});
it("field validation keeps deadline without task and focuses field", async () => {
  mount(detail());
  await ready();
  fireEvent.change(screen.getByTestId("opportunity-next-task"), {
    target: { value: "" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(screen.getByRole("alert")).toHaveTextContent("할 일을 입력");
  expect(api.putSalesOpportunity).not.toHaveBeenCalled();
});
it("actual form sanitizes money, probability and monthly MM immediately", async () => {
  mount(detail());
  await ready();
  fireEvent.change(screen.getByTestId("opportunity-amount"), {
    target: { value: "12a,3b4" },
  });
  fireEvent.change(screen.getByTestId("opportunity-probability"), {
    target: { value: "5x0" },
  });
  fireEvent.change(screen.getByLabelText("1번째 줄 월MM"), {
    target: { value: "0.a5" },
  });
  expect(screen.getByTestId("opportunity-amount")).toHaveValue("12,34");
  expect(screen.getByTestId("opportunity-probability")).toHaveValue("50");
  expect(screen.getByLabelText("1번째 줄 월MM")).toHaveValue("0.5");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(api.putSalesOpportunity).toHaveBeenCalledWith(
    "O-0101",
    expect.objectContaining({ amount: "1234", probability: "50" }),
  );
});
it("server row errors preserve input and do not retry mutation", async () => {
  vi.mocked(api.putSalesOpportunity).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "1번째 줄의 예상단가 칸을 확인해 주세요.",
      field: "needs[0].expectedRate",
      requestId: "row-request",
    }),
  );
  mount(detail());
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findAllByRole("alert")).not.toHaveLength(0);
  expect(screen.getByTestId("opportunity-name")).toHaveValue("기회 원본");
  expect(screen.getByLabelText("1번째 줄 예상단가")).toHaveFocus();
  expect(api.putSalesOpportunity).toHaveBeenCalledOnce();
});
it("committed refresh failure offers read retry but disables second save", async () => {
  saved.mockRejectedValue(new Error("조회 실패"));
  mount(detail());
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(
    await screen.findByText("저장됐지만 다시 불러오지 못했습니다"),
  ).toBeInTheDocument();
  expect(screen.getByTestId("opportunity-form-save")).toBeDisabled();
  await userEvent.click(screen.getByTestId("opportunity-refresh-retry"));
  expect(api.putSalesOpportunity).toHaveBeenCalledOnce();
  expect(saved).toHaveBeenCalledTimes(2);
});
it("missing lookup shows error and retry without invented options", async () => {
  vi.mocked(api.getClientOptions).mockRejectedValueOnce(
    new Error("고객 조회 실패"),
  );
  mount();
  expect(await screen.findByRole("alert")).toHaveTextContent("고객 조회 실패");
  expect(screen.getByTestId("opportunity-form-save")).toBeDisabled();
  await userEvent.click(screen.getByTestId("opportunity-lookup-retry"));
  await ready();
});
it("inactive existing references remain visible but selectable options are API backed", async () => {
  vi.mocked(api.getClientOptions).mockResolvedValue({ items: [] });
  vi.mocked(api.getSalesPartOptions).mockResolvedValue({ items: [] });
  mount(detail());
  await ready();
  expect(
    screen.getByRole("option", { name: "C-a (기존 중지 참조)" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("option", { name: "PART-a (기존 중지 참조)" }),
  ).toBeInTheDocument();
});
it("developer picker loads real direct active list and empty state; selection is only a local draft", async () => {
  mount(detail());
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "개발자 선택" }));
  const dialog = screen.getByRole("dialog");
  expect(
    await within(dialog).findByText("선택할 개발자가 없습니다."),
  ).toBeInTheDocument();
  expect(api.getDevelopers).toHaveBeenCalledWith(
    expect.objectContaining({
      directClass: "DRCT",
      includeStopped: false,
      page: 1,
    }),
  );
  await userEvent.click(
    within(dialog).getByRole("button", { name: "선택 완료" }),
  );
  expect(api.putSalesOpportunity).not.toHaveBeenCalled();
});

it("저장 버튼은 공통 파란색·흰색 버튼 스타일을 사용한다", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("opportunity-form-save")).toHaveClass(
    "sicost-primary",
  );
});
it("빈 필수 칸은 칸 이름이 든 원문 형식 문구를 보이고 「필수 입력입니다.」를 쓰지 않는다", async () => {
  mount();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  for (const text of [
    "기회명을 입력해 주세요.",
    "고객사를 골라 주세요.",
    "영업 파트를 골라 주세요.",
    "예상 금액을 입력해 주세요.",
  ])
    expect(screen.getByText(text)).toBeInTheDocument();
  expect(screen.queryByText("필수 입력입니다.")).not.toBeInTheDocument();
  expect(api.createSalesOpportunity).not.toHaveBeenCalled();
});
it("칸 아래에 보이는 서버 거절 문구는 위쪽 오류 띠에 다시 나오지 않는다", async () => {
  vi.mocked(api.putSalesOpportunity).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "고객사를 입력해 주세요.",
      field: "customerId",
      requestId: "field-request",
    }),
  );
  mount(detail());
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findAllByText("고객사를 입력해 주세요.")).toHaveLength(1);
});
it("칸에 붙지 않는 서버 거절은 위쪽 오류 띠에 그대로 나온다", async () => {
  vi.mocked(api.putSalesOpportunity).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다른 사용자가 먼저 고쳤습니다.",
      requestId: "stale-request",
    }),
  );
  mount(detail());
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(
    await screen.findByText("다른 사용자가 먼저 고쳤습니다."),
  ).toBeInTheDocument();
});
