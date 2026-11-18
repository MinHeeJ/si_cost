import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { afterEach, expect, it, vi } from "vitest";
import { AnalysisTable } from "./AnalysisTable";
import { PROJECTS_COMMITTED } from "../projects/projectsApi";
const originalWidth = window.innerWidth;
afterEach(() => {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: originalWidth,
  });
});
it("767px는 서버20건 카드와 더보기를 쓰고 commit 후 첫페이지를 다시 조회한다", async () => {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 767,
  });
  function Harness() {
    const [page, setPage] = useState(1),
      [size, setSize] = useState<20 | 50 | 100>(50);
    const rows = Array.from({ length: page === 1 ? size : 1 }, (_, i) => ({
      id: (page - 1) * size + i + 1,
    }));
    return (
      <AnalysisTable
        rows={rows}
        columns={[
          {
            id: "projectName",
            label: "프로젝트",
            render: (r) => `프로젝트 ${r.id}`,
          },
        ]}
        rowKey={(r) => r.id}
        page={page}
        size={size}
        total={21}
        onSizeChange={(v) => {
          setSize(v);
          setPage(1);
        }}
        onPageChange={setPage}
      />
    );
  }
  render(<Harness />);
  await screen.findByText("프로젝트 20");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "20개 더보기" }));
  await screen.findByText("프로젝트 21");
  expect(screen.getByText("프로젝트 1")).toBeInTheDocument();
  fireEvent(window, new CustomEvent(PROJECTS_COMMITTED));
  await waitFor(() =>
    expect(screen.queryByText("프로젝트 21")).not.toBeInTheDocument(),
  );
  fireEvent(window, new Event("resize"));
});
it("768px는 기존 DataTable의 열 머리 정렬과 페이지 크기를 유지한다", () => {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 768,
  });
  render(
    <AnalysisTable
      rows={[{ id: 1 }]}
      columns={[
        {
          id: "projectName",
          label: "프로젝트",
          render: () => "기존표",
          sortable: true,
        },
      ]}
      rowKey={(r) => r.id}
      size={50}
    />,
  );
  expect(screen.getByRole("table")).toBeInTheDocument();
  expect(screen.getByText("기존표")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "20개 더보기" }),
  ).not.toBeInTheDocument();
});
it("명시적으로 활성화한 모바일 카드만 클릭·Enter로 상세를 연다", async () => {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: 767,
  });
  const open = vi.fn();
  const row = { id: "project" };
  const { rerender } = render(
    <AnalysisTable
      rows={[row]}
      columns={[
        { id: "projectName", label: "프로젝트", render: () => "원가 프로젝트" },
      ]}
      rowKey={(r) => r.id}
      size={20}
      onRowAction={open}
      mobileRowAction
    />,
  );
  const card = (await screen.findByText("원가 프로젝트")).closest("article")!;
  expect(card).toHaveAttribute("tabindex", "0");
  fireEvent.click(card);
  fireEvent.keyDown(card, { key: "Enter" });
  expect(open).toHaveBeenNthCalledWith(1, row);
  expect(open).toHaveBeenNthCalledWith(2, row);
  rerender(
    <AnalysisTable
      rows={[row]}
      columns={[
        { id: "projectName", label: "프로젝트", render: () => "현황 프로젝트" },
      ]}
      rowKey={(r) => r.id}
      size={20}
      onRowAction={open}
    />,
  );
  const unchanged = (await screen.findByText("현황 프로젝트")).closest(
    "article",
  )!;
  expect(unchanged).not.toHaveAttribute("tabindex");
  fireEvent.click(unchanged);
  fireEvent.keyDown(unchanged, { key: "Enter" });
  expect(open).toHaveBeenCalledTimes(2);
});
