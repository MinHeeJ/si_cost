import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { FinancialYearStatusPanel } from "./FinancialYearStatusPanel";
import {
  FinancialPreviewPanel,
  type FinancialPreviewPanelProps,
} from "./FinancialPreviewPanel";
import * as api from "./financialPreviewApi";
vi.mock("./financialPreviewApi", async (original) => ({
  ...(await original<typeof api>()),
  getFinancialYearStatus: vi.fn(),
  createFinancialPreview: vi.fn(),
}));
const year: api.FinancialYearStatus = {
  year: "2026",
  today: "2026-07-15",
  cashBaseMonth: "2025-01",
  months: Array.from({ length: 12 }, (_, index) => ({
    month: `2026-${String(index + 1).padStart(2, "0")}`,
    closeStatus: index === 0 ? "CLOSE" : "WORK",
    itemCount: index < 7 ? index : null,
    selectable: index < 7,
    editable: index > 0 && index < 7,
  })),
};
const contributions: api.FinancialContributions = {
  sgaAmount: "999999999999999",
  sgaVatAmount: "1",
  nonOperatingIncome: "2",
  nonOperatingIncomeVat: "0",
  nonOperatingExpense: "3",
  nonOperatingExpenseVat: "0",
  adjustmentProfit: "-5",
  cashFinancialExpense: "4",
  cashMiscIncome: "2",
  cashCardExpense: "8",
  cashTransferExpense: "13",
  cashUnpaidLabor: "17",
  transferTotal: "30",
  cashLoanIn: "19",
  cashLoanOut: "23",
  cashLoanNet: "-4",
  month: "2026-07",
  corporateTaxAllocation: null,
  cashTaxPayment: "0",
  verified: false,
  warnings: ["연간 예상 법인세가 없습니다."],
};
const preview: api.FinancialPreviewResult = {
  month: "2026-07",
  revision: "before",
  addedDrafts: [],
  copiedCount: 0,
  message: "불러올 항목이 없습니다",
  allocation: {
    year: "2026",
    estimatedTax: null,
    closedTotal: "0",
    remainingAmount: null,
    openMonthCount: 12,
    months: [
      {
        month: "2026-07",
        closeStatus: "WORK",
        amount: null,
        verified: false,
        fixed: null,
        migrated: null,
      },
    ],
    verified: false,
    warnings: ["연간 예상 법인세가 없습니다."],
  },
  contributions,
};
const draft: api.FinancialDraftItem = {
  sourceMonth: "2026-06",
  sourceItemSn: "99999",
  finClass: "SGA",
  sgaItemCode: null,
  itemName: "사용자가 저장한 항목",
  amount: "999999999999999",
  vatAmount: "0",
  ioDate: null,
  repeat: "N",
  remark: null,
  autoFill: "N",
  errors: [],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getFinancialYearStatus).mockResolvedValue(year);
  vi.mocked(api.createFinancialPreview).mockResolvedValue(preview);
});
function draw(overrides: Partial<FinancialPreviewPanelProps> = {}) {
  const props: FinancialPreviewPanelProps = {
    month: "2026-07",
    revision: "before",
    changes: {},
    editable: true,
    onAddDrafts: vi.fn(),
    ...overrides,
  };
  return { props, ...render(<FinancialPreviewPanel {...props} />) };
}
async function ready() {
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "지난달 항목 불러오기" }),
    ).not.toBeDisabled(),
  );
}
it("YEAR-LOAD는 12개월 저장 건수·마감·미래 –를 표시하고 controlled 월만 선택한다", async () => {
  const change = vi.fn();
  render(
    <FinancialYearStatusPanel selectedMonth="2026-07" onMonthChange={change} />,
  );
  const strip = await screen.findByRole("group", { name: "12개월 현황" });
  expect(within(strip).getAllByRole("button")).toHaveLength(12);
  expect(screen.getByTestId("financial-month-2026-01")).toHaveTextContent(
    "마감 · 0건",
  );
  expect(screen.getByTestId("financial-month-2026-08")).toBeDisabled();
  expect(screen.getByTestId("financial-month-2026-08")).toHaveTextContent("–");
  await userEvent.click(screen.getByTestId("financial-month-2026-06"));
  expect(change).toHaveBeenCalledWith("2026-06");
  expect(screen.getByTestId("financial-month-2026-07")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
});
it("이전 해는 12월로 부모의 비동기 미저장 guard에 위임하고 다음해는 금지한다", async () => {
  const change = vi.fn().mockResolvedValue(false);
  render(
    <FinancialYearStatusPanel selectedMonth="2026-07" onMonthChange={change} />,
  );
  await screen.findByRole("group", { name: "12개월 현황" });
  await userEvent.click(screen.getByRole("button", { name: "이전 해" }));
  expect(change).toHaveBeenCalledWith("2025-12");
  expect(screen.getByRole("heading", { name: "2026년" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "다음 해" })).toBeDisabled();
});
it("과거에서 올해로 이동하면 서버 현재월을 선택하고 시작연도 이전 이동은 막는다", async () => {
  vi.mocked(api.getFinancialYearStatus).mockResolvedValue({
    ...year,
    year: "2025",
    months: year.months.map((m) => ({
      ...m,
      month: m.month.replace("2026", "2025"),
      selectable: true,
      editable: false,
    })),
  });
  const change = vi.fn();
  render(
    <FinancialYearStatusPanel selectedMonth="2025-12" onMonthChange={change} />,
  );
  await screen.findByRole("group", { name: "12개월 현황" });
  expect(screen.getByRole("button", { name: "이전 해" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다음 해" }));
  expect(change).toHaveBeenCalledWith("2026-07");
});
it("현황 로딩은 임의 0건을 보여주지 않는다", () => {
  vi.mocked(api.getFinancialYearStatus).mockReturnValue(new Promise(() => {}));
  render(
    <FinancialYearStatusPanel
      selectedMonth="2026-07"
      onMonthChange={vi.fn()}
    />,
  );
  expect(screen.getByRole("status")).toHaveTextContent("현황을 불러오는 중");
  expect(screen.queryByText(/0건/)).not.toBeInTheDocument();
});
it("현황 실패는 문의번호와 재조회를 제공한다", async () => {
  vi.mocked(api.getFinancialYearStatus).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "현황 조회 실패",
      requestId: "year-error",
    }),
  );
  render(
    <FinancialYearStatusPanel
      selectedMonth="2026-07"
      onMonthChange={vi.fn()}
    />,
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("year-error");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByRole("group", { name: "12개월 현황" });
});
it("현황 재조회 신호는 선택월을 바꾸지 않고 저장건수만 갱신한다", async () => {
  const change = vi.fn();
  const view = render(
    <FinancialYearStatusPanel
      selectedMonth="2026-07"
      onMonthChange={change}
      refreshKey={1}
    />,
  );
  await screen.findByRole("group", { name: "12개월 현황" });
  view.rerender(
    <FinancialYearStatusPanel
      selectedMonth="2026-07"
      onMonthChange={change}
      refreshKey={2}
    />,
  );
  await waitFor(() =>
    expect(api.getFinancialYearStatus).toHaveBeenCalledTimes(2),
  );
  expect(change).not.toHaveBeenCalled();
});
it("초안 CALCULATE는 최신revision과 두 탭 명시변경을 같이 보내며 정확한 합계만 표시한다", async () => {
  const changes: api.FinancialDraftChanges = {
    items: {
      add: [{ finClass: "SGA", itemName: "항목", amount: "999999999999999" }],
    },
    transfers: {
      add: [
        {
          date: "2026-07-01",
          classCode: "EXP",
          itemName: "이체",
          amount: "13",
        },
      ],
    },
  };
  draw({ changes });
  await ready();
  expect(api.createFinancialPreview).toHaveBeenCalledWith(
    {
      ...changes,
      month: "2026-07",
      expectedRevision: "before",
      action: "CALCULATE",
    },
    expect.any(AbortSignal),
  );
  expect(screen.getByText("999,999,999,999.999")).toBeInTheDocument();
  expect(screen.getByText("연간 예상 법인세가 없습니다.")).toBeInTheDocument();
  expect(screen.getByTestId("financial-allocation-2026-07")).toHaveTextContent(
    "미확인",
  );
});
it("미리보기 실패는 이전 합계를 최신처럼 남기지 않고 초안을 바꾸지 않는다", async () => {
  const { props, rerender } = draw();
  await ready();
  expect(screen.getByText("999,999,999,999.999")).toBeInTheDocument();
  vi.mocked(api.createFinancialPreview).mockRejectedValueOnce(
    new Error("계산 실패"),
  );
  rerender(
    <FinancialPreviewPanel {...props} changes={{ estimatedTax: "9" }} />,
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("계산 실패");
  expect(screen.queryByText("999,999,999,999.999")).not.toBeInTheDocument();
  expect(props.onAddDrafts).not.toHaveBeenCalled();
});
it("LOAD_PREVIOUS는 응답 초안만 저장전 항목으로 전달하고 서버출처·감사값을 payload에 복제하지 않는다", async () => {
  const { props } = draw();
  await ready();
  vi.mocked(api.createFinancialPreview).mockResolvedValueOnce({
    ...preview,
    addedDrafts: [draft],
    copiedCount: 1,
    message: "지난달 항목을 불러왔습니다.",
  });
  await userEvent.click(
    screen.getByRole("button", { name: "지난달 항목 불러오기" }),
  );
  await waitFor(() => expect(props.onAddDrafts).toHaveBeenCalledOnce());
  const addition = api.financialPreviousAddition(draft);
  expect(props.onAddDrafts).toHaveBeenCalledWith([addition], [draft]);
  expect(addition).not.toHaveProperty("autoFill");
  expect(addition).not.toHaveProperty("sourceItemSn");
  expect(addition.copySource).toEqual({
    month: "2026-06",
    itemSn: "99999",
    mode: "PREVIOUS",
  });
});
it("불러올 대상이 없으면 기존 초안을 유지하고 안내만 표시한다", async () => {
  const { props } = draw();
  await ready();
  await userEvent.click(
    screen.getByRole("button", { name: "지난달 항목 불러오기" }),
  );
  await screen.findByText("불러올 항목이 없습니다");
  expect(props.onAddDrafts).not.toHaveBeenCalled();
});
it.each([
  "마감한 월은 변경할 수 없습니다.",
  "운영 시작 전 읽기 전용입니다.",
  "지난 연도는 조회만 가능합니다.",
])("잠금 상태 %s는 계산과 불러오기 요청을 하지 않는다", (reason) => {
  draw({ editable: false, readOnlyReason: reason });
  expect(screen.getByText(reason)).toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: "지난달 항목 불러오기" }),
  ).toBeDisabled();
  expect(api.createFinancialPreview).not.toHaveBeenCalled();
});
it("숨은탭 필드 오류를 부모에 전달하며 실패한 원본 초안을 유지한다", async () => {
  const error = new ApiError(400, {
    success: false,
    meta: {},
    code: "VALIDATION_ERROR",
    field: "transfers.amount",
    message: "이체 금액 확인",
    requestId: "preview-field",
  });
  vi.mocked(api.createFinancialPreview).mockRejectedValueOnce(error);
  const onValidationError = vi.fn();
  draw({ onValidationError });
  expect(await screen.findByRole("alert")).toHaveTextContent("preview-field");
  expect(onValidationError).toHaveBeenCalledWith(error);
});
it("카드 탭은 서버 전체 이체·대여금 순액을 표시하고 단위만 바꾼다", async () => {
  const { props, rerender } = draw({ tab: "CARD_TRANSFER_LOAN", unit: "원" });
  await ready();
  expect(screen.getByText("계좌이체 전체 합계")).toBeInTheDocument();
  expect(screen.getByText("−4")).toHaveClass("financial-negative");
  expect(screen.queryByTestId("financial-allocation")).not.toBeInTheDocument();
  rerender(<FinancialPreviewPanel {...props} unit="백만원" />);
  expect(screen.getByText("−0.000004")).toBeInTheDocument();
  expect(api.createFinancialPreview).toHaveBeenCalledTimes(1);
});
it("오래된 계산응답은 최신 월을 덮어쓰지 않는다", async () => {
  let resolve!: (value: api.FinancialPreviewResult) => void;
  vi.mocked(api.createFinancialPreview).mockReturnValueOnce(
    new Promise((r) => {
      resolve = r;
    }),
  );
  const calculated = vi.fn();
  const { props, rerender } = draw({ onCalculated: calculated });
  rerender(<FinancialPreviewPanel {...props} month="2026-06" />);
  await ready();
  await act(async () => resolve({ ...preview, message: "오래된 응답" }));
  expect(calculated).toHaveBeenCalledTimes(1);
  expect(screen.queryByText("오래된 응답")).not.toBeInTheDocument();
});
it("불러오기 진행 중 중복클릭은 추가요청이 없다", async () => {
  draw();
  await ready();
  vi.mocked(api.createFinancialPreview).mockReturnValueOnce(
    new Promise(() => {}),
  );
  const button = screen.getByRole("button", { name: "지난달 항목 불러오기" });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(api.createFinancialPreview).toHaveBeenCalledTimes(2);
  expect(button).toBeDisabled();
});
it.each([
  ["0", "0"],
  ["1234500", "1,234.5"],
  ["999999999999999", "999,999,999,999.999"],
])("원표시 %s는 float 변환 없이 %s", (value, expected) => {
  expect(api.formatFinancialWon(value)).toBe(expected);
});
