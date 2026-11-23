import { useState } from "react";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { FinancialTaxPanel, taxExplanation } from "./FinancialTaxPanel";
import { financialEditorFixture } from "./financialFixtures";
import type { FinancialDraft } from "./useFinancialEditor";
import type {
  CorporateTaxAllocation,
  CorporateTaxPayment,
} from "../corporatetaxesops/corporateTaxApi";

const audit = {
  regUserId: "test",
  regDttm: "2026-01-01T00:00:00",
  modUserId: null,
  modDttm: null,
};
const allocation: CorporateTaxAllocation = {
  year: "2026",
  estimatedTax: "100",
  closedTotal: "200",
  remainingAmount: "-100",
  openMonthCount: 2,
  verified: true,
  warnings: [],
  months: [
    {
      month: "2026-10",
      closeStatus: "CLOSE",
      amount: "200",
      verified: true,
      fixed: "Y",
      migrated: null,
    },
    {
      month: "2026-11",
      closeStatus: "WORK",
      amount: "-50",
      verified: true,
      fixed: "N",
      migrated: null,
    },
  ],
};
const payment = (
  payType: "MID" | "CNFM",
  changes: Partial<CorporateTaxPayment> = {},
): CorporateTaxPayment => ({
  year: "2026",
  payType,
  dueDate: payType === "MID" ? "2026-08-31" : "2027-03-31",
  payAmount: null,
  paidDate: null,
  payYn: "N",
  displayAmount: payType === "MID" ? "0" : "100",
  automaticAmount: payType === "MID" ? "0" : "100",
  manuallyEdited: false,
  status: "확인 필요",
  confirmationRevision: "revision",
  audit,
  ...changes,
});
function show(value = allocation, payments: CorporateTaxPayment[] = []) {
  const onPayment = vi.fn();
  const editor = financialEditorFixture({
    month: "2026-11",
    corporateTax: { year: "2026", estimatedTax: "100", audit },
    taxPayments: payments,
  });
  function Harness() {
    const [draft, setDraft] = useState<FinancialDraft | undefined>({
      items: [],
      adjustments: [],
      transfers: [],
      loans: [],
      cards: [],
      estimatedTax: "100",
      taxSchedules: payments.map((p) => ({
        year: p.year,
        payType: p.payType,
        dueDate: p.dueDate,
        payAmount: p.payAmount,
      })),
    });
    return (
      <FinancialTaxPanel
        editor={editor}
        draft={draft!}
        allocation={value}
        disabled={false}
        unit="원"
        violations={[]}
        setDraft={setDraft}
        onPayment={onPayment}
      />
    );
  }
  render(<Harness />);
  return onPayment;
}
afterEach(cleanup);
it("displays server-returned negative monthly tax without blocking annual editing", async () => {
  show();
  expect(screen.getByTestId("financial-tax-current")).toHaveTextContent("−50");
  expect(
    screen.getByTestId("financial-tax-negative-warning"),
  ).toHaveTextContent("음수(되돌림)");
  expect(screen.getByTestId("financial-tax-explanation")).toHaveTextContent(
    "마감된 1개월 반영액 200원",
  );
  expect(screen.getByTestId("financial-estimated-tax")).toBeEnabled();
  await userEvent.type(screen.getByTestId("financial-estimated-tax"), "0");
  expect(screen.getByTestId("financial-tax-unsaved")).toHaveTextContent(
    "저장 전입니다.",
  );
  expect(
    screen.queryByTestId("financial-tax-allocation-table"),
  ).not.toBeInTheDocument();
  await userEvent.click(
    screen.getByRole("button", { name: "월별 배분 펼치기" }),
  );
  expect(
    screen.getByTestId("financial-tax-allocation-table"),
  ).toHaveTextContent("−50");
  await userEvent.click(screen.getByRole("button", { name: "접기" }));
  expect(
    screen.queryByTestId("financial-tax-allocation-table"),
  ).not.toBeInTheDocument();
});
it("lists the actual missing closed months and locks annual editing", () => {
  show({
    ...allocation,
    verified: false,
    remainingAmount: null,
    months: allocation.months.map((row) => ({
      ...row,
      amount: null,
      verified: false,
    })),
  });
  expect(screen.getByTestId("financial-tax-current")).toHaveTextContent("–");
  expect(screen.getByTestId("financial-estimated-tax")).toBeDisabled();
  expect(screen.getByTestId("financial-tax-lock-reason")).toHaveTextContent(
    "마감된 달(10월)의 법인세 자료가 없어 계산할 수 없습니다.",
  );
});
it("renders payment order, server automatic amount and restoration without a client calculation", async () => {
  const onPayment = show(allocation, [payment("MID"), payment("CNFM")]);
  const table = screen.getByTestId("financial-tax-payments-table");
  expect(
    within(table)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["납부일", "구분", "금액", "상태", "납부 확인"]);
  expect(screen.getByPlaceholderText("세무사 안내 금액")).toHaveValue("");
  expect(screen.queryByText("자동 계산값")).not.toBeInTheDocument();
  const amount = screen.getByTestId("financial-tax-2026-cnfm-amount");
  expect(amount).toHaveValue("100");
  await userEvent.clear(amount);
  await userEvent.type(amount, "80");
  expect(screen.getByText(/고침 · 자동/)).toHaveTextContent("100원");
  await userEvent.click(screen.getByRole("button", { name: "자동 계산으로" }));
  expect(amount).toHaveValue("100");
  expect(screen.getByTestId("financial-tax-2026-mid-confirm")).toHaveClass(
    "sicost-primary",
  );
  await userEvent.click(screen.getByTestId("financial-tax-2026-mid-confirm"));
  expect(onPayment).toHaveBeenCalledOnce();
});
it("keeps confirmed payment date and amount read-only and labels closed allocation", () => {
  show(allocation, [
    payment("CNFM", {
      payYn: "Y",
      payAmount: "100",
      paidDate: "2027-03-30",
      status: "납부",
    }),
  ]);
  expect(
    screen.queryByTestId("financial-tax-2026-cnfm-amount"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("financial-tax-2026-cnfm-date"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("financial-tax-2026-cnfm-confirm"),
  ).not.toBeInTheDocument();
  expect(screen.getByText("2027-03-30 납부")).toBeVisible();
  expect(taxExplanation(allocation, "2026-10")).toBe("마감 확정");
});
