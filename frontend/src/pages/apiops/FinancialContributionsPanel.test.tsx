import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { FinancialContributionsPanel } from "./FinancialContributionsPanel";
import type { FinancialContributions } from "./financialReadApi";

const data: FinancialContributions = {
  month: "2026-07",
  sgaAmount: "1999999999999998",
  sgaVatAmount: "2",
  nonOperatingIncome: "3",
  nonOperatingIncomeVat: "4",
  nonOperatingExpense: "5",
  nonOperatingExpenseVat: "6",
  adjustmentProfit: "-7",
  cashFinancialExpense: "8",
  cashMiscIncome: "9",
  cashCardExpense: "10",
  cashTransferExpense: "11",
  cashUnpaidLabor: "12",
  transferTotal: "23",
  cashLoanIn: "13",
  cashLoanOut: "14",
  cashLoanNet: "-1",
  corporateTaxAllocation: null,
  cashTaxPayment: "0",
  verified: false,
  warnings: ["연간 예상 법인세가 없습니다."],
};
const response = (value = data) =>
  new Response(JSON.stringify({ success: true, meta: {}, data: value }));
const fetcher = vi.fn<typeof fetch>();
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("fetch", fetcher);
});
afterEach(() => vi.unstubAllGlobals());
function amount(label: string) {
  return within(screen.getByText(label).closest("tr")!).getAllByRole("cell")[1];
}

it("SCR-UI-10-CONTRIBUTIONS reads saved sources and displays separate profit, VAT, cash and server totals", async () => {
  fetcher.mockImplementation(async () => response());
  render(<FinancialContributionsPanel month="2026-07" unit="원" />);
  await screen.findByText("판관비");
  expect(String(fetcher.mock.lastCall?.[0])).toBe(
    "/api/financial-contributions?month=2026-07",
  );
  expect(screen.getByTestId("financial-contributions-panel")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-10-CONTRIBUTIONS",
  );
  expect(amount("판관비")).toHaveTextContent("1,999,999,999,999,998");
  expect(
    within(screen.getByText("판관비").closest("tr")!).getAllByRole("cell")[2],
  ).toHaveTextContent("2");
  expect(amount("계좌이체 전체합계")).toHaveTextContent("23");
  expect(amount("대여금 순액")).toHaveTextContent("−1");
  expect(within(amount("대여금 순액")).getByText("−1")).toHaveClass(
    "financial-read-negative",
  );
  expect(amount("법인세(월할)")).toHaveTextContent("–");
  expect(amount("법인세 납부")).toHaveTextContent("0");
  expect(screen.getByText("미확인")).toHaveClass("sicost-tone-warn");
  expect(screen.getByText("연간 예상 법인세가 없습니다.")).toHaveClass(
    "sicost-tone-warn",
  );
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("display units move exact decimal digits without rounding, mutating raw values or refetching", async () => {
  fetcher.mockImplementation(async () => response());
  const { rerender } = render(
    <FinancialContributionsPanel month="2026-07" unit="천원" />,
  );
  await screen.findByText("판관비");
  expect(amount("판관비")).toHaveTextContent("1,999,999,999,999.998");
  expect(amount("대여금 순액")).toHaveTextContent("−0.001");
  rerender(<FinancialContributionsPanel month="2026-07" unit="백만원" />);
  expect(amount("판관비")).toHaveTextContent("1,999,999,999.999998");
  expect(amount("대여금 순액")).toHaveTextContent("−0.000001");
  expect(data.sgaAmount).toBe("1999999999999998");
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it("loading and read failures show no fabricated zeroes; retry sends only GET", async () => {
  let resolve!: (response: Response) => void;
  fetcher
    .mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    )
    .mockImplementation(async () => response());
  render(<FinancialContributionsPanel month="2026-07" />);
  expect(screen.getAllByRole("status")).toHaveLength(2);
  expect(screen.queryByText("판관비")).not.toBeInTheDocument();
  await act(async () =>
    resolve(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "기여값 조회 실패",
          requestId: "contribution-trace",
        }),
        { status: 500 },
      ),
    ),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "contribution-trace",
  );
  expect(fetcher).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("판관비");
  expect(
    fetcher.mock.calls.every(
      ([, options]) => !options?.method && !options?.body,
    ),
  ).toBe(true);
});
it("an empty source snapshot displays actual API zeroes, not empty or missing tax markers", async () => {
  const empty = Object.fromEntries(
    Object.entries(data).map(([key, value]) => [
      key,
      typeof value === "string" && /^-?\d+$/.test(value) ? "0" : value,
    ]),
  ) as FinancialContributions;
  fetcher.mockImplementation(async () =>
    response({
      ...empty,
      corporateTaxAllocation: "0",
      verified: true,
      warnings: [],
    }),
  );
  render(<FinancialContributionsPanel month="2026-07" unit="원" />);
  await screen.findByText("판관비");
  expect(amount("법인세(월할)")).toHaveTextContent("0");
  expect(amount("판관비")).toHaveTextContent("0");
  expect(screen.getByText("계산")).toHaveClass("sicost-tone-neutral");
  expect(screen.queryByText("미확인")).not.toBeInTheDocument();
});
it("period or commit refresh cancels old reads so a late response cannot replace new values", async () => {
  let resolve!: (response: Response) => void;
  fetcher
    .mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    )
    .mockImplementation(async () =>
      response({ ...data, month: "2026-06", sgaAmount: "42" }),
    );
  const { rerender, unmount } = render(
    <FinancialContributionsPanel month="2026-07" refreshKey={1} unit="원" />,
  );
  const oldSignal = fetcher.mock.lastCall?.[1]?.signal;
  rerender(
    <FinancialContributionsPanel month="2026-06" refreshKey={2} unit="원" />,
  );
  await screen.findByText("판관비");
  expect(oldSignal?.aborted).toBe(true);
  await act(async () => resolve(response()));
  expect(amount("판관비")).toHaveTextContent("42");
  rerender(
    <FinancialContributionsPanel month="2026-06" refreshKey={3} unit="원" />,
  );
  await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(3));
  unmount();
  expect(fetcher.mock.lastCall?.[1]?.signal?.aborted).toBe(true);
});
