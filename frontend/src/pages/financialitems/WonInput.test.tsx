import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { WonInput, normalizeFinancialWon } from "./WonInput";
import { FinancialAmount } from "./FinancialAmount";

it.each(["1.5", "1e3", "1E3", "1000000000000000", "1가", "", "-1"])(
  "rejects invalid unsigned won %s without rounding",
  (value) => {
    expect(() => normalizeFinancialWon(value)).toThrow();
  },
);
it("normalizes only spaces, commas and trailing won and preserves exact integer bounds", () => {
  expect(normalizeFinancialWon(" 999,999,999,999,999 원 ")).toBe(
    "999999999999999",
  );
  expect(normalizeFinancialWon("0000")).toBe("0");
  expect(normalizeFinancialWon("-1", true)).toBe("-1");
  expect(() => normalizeFinancialWon("0", true)).toThrow();
  expect(() => normalizeFinancialWon("0", false, true)).toThrow();
});
it("keeps invalid raw input visible and reports aria-invalid rather than coercing", () => {
  const change = vi.fn();
  const { rerender } = render(
    <WonInput aria-label="원" value="" onValueChange={change} />,
  );
  fireEvent.change(screen.getByLabelText("원"), { target: { value: "1e3" } });
  expect(change).toHaveBeenCalledWith("1e3");
  rerender(<WonInput aria-label="원" value="1e3" onValueChange={change} />);
  expect(screen.getByLabelText("원")).toHaveAttribute("aria-invalid", "true");
});
it("formats big aggregate and signed units with exact digits and no business arithmetic", () => {
  const { rerender } = render(
    <FinancialAmount value="1999999999999998" unit="백만원" />,
  );
  expect(screen.getByText("1,999,999,999.999998")).toBeInTheDocument();
  rerender(<FinancialAmount value="-1" unit="백만원" />);
  expect(screen.getByText("−0.000001")).toHaveClass("financial-negative");
  rerender(<FinancialAmount value="1000000" unit="백만원" />);
  expect(screen.getByText("1")).toBeInTheDocument();
  rerender(<FinancialAmount value={null} />);
  expect(screen.getByText("미확인")).toBeInTheDocument();
});
