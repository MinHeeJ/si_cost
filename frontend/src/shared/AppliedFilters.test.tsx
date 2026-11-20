import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { FilterBar, countAppliedFilters } from "./FilterBar";

it("counts only supplied condition keys, excluding defaults and paging/sort", () => {
  expect(countAppliedFilters({ page: 2, size: 100, sort: "name" }, { q: "", status: "", includeStopped: false })).toBe(0);
  expect(countAppliedFilters({ q: "이름", status: "ING", page: 2 }, { q: "", status: "", includeStopped: false })).toBe(2);
  expect(countAppliedFilters({ difference: "ALL", projectId: undefined }, { difference: "ALL", projectId: "" })).toBe(0);
  expect(countAppliedFilters({ period: "SOON", extension: "H", job: "" }, { period: "ALL", extension: "ALL", job: "" })).toBe(2);
});

it("opts in to an applied count without changing other filter bars", () => {
  const reset = vi.fn();
  const { rerender } = render(<FilterBar testId="filters" onSubmit={vi.fn()} onReset={reset} appliedCount={2}><input data-testid="query" /></FilterBar>);
  expect(screen.getByTestId("filters-toggle")).toHaveTextContent("[조건 (2)]");
  fireEvent.click(screen.getByRole("button", { name: "초기화" }));
  expect(reset).toHaveBeenCalledOnce();
  rerender(<FilterBar testId="filters" onSubmit={vi.fn()} onReset={reset}><input data-testid="query" /></FilterBar>);
  expect(screen.getByTestId("filters-toggle")).toHaveTextContent(/^조건$/);
  expect(screen.getByTestId("filters-toggle")).not.toHaveStyle({ display: "inline-flex" });
});
