import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { DeveloperAssignmentsPanel } from "./DeveloperAssignmentsPanel";
import {
  DeveloperSingleStatus,
  DeveloperStatusToolbar,
} from "./DeveloperStatusToolbar";
import { useDeveloperSelection } from "./useDeveloperSelection";
import * as api from "./peopleClient";
vi.mock("./peopleClient", async (original) => ({
  ...(await original<typeof api>()),
  getDeveloperAssignments: vi.fn(),
  getDeveloperSelection: vi.fn(),
  saveDeveloperBulkStatus: vi.fn(),
}));
const row = {
  id: "DEV-000901",
  revision: "current",
  name: "검증 개발자",
  useYn: "Y" as const,
};
const empty: api.DeveloperAssignmentsPage = {
  items: [],
  total: 0,
  page: 1,
  size: 50,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getDeveloperAssignments).mockResolvedValue(empty);
  vi.mocked(api.getDeveloperSelection).mockResolvedValue({ items: [] });
  vi.mocked(api.saveDeveloperBulkStatus).mockResolvedValue({
    applied: [{ id: row.id }],
    noChange: [],
    rejected: [],
    appliedCount: 1,
    noChangeCount: 0,
    rejectedCount: 0,
  });
});
it("history has loading then a real empty state, not a creation control", async () => {
  let resolve!: (value: api.DeveloperAssignmentsPage) => void;
  vi.mocked(api.getDeveloperAssignments).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  render(<DeveloperAssignmentsPanel developerId={row.id} />);
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  await act(async () => resolve(empty));
  expect(await screen.findByText("투입 이력이 없습니다.")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /등록|생성|삭제/ }),
  ).not.toBeInTheDocument();
});
it("history includes ended and cancelled source rows with distinct project IDs and pages", async () => {
  const item = {
    id: "A-901",
    projectId: "P-901",
    projectName: "동명 프로젝트",
    jobCode: "DEV",
    jobName: "개발",
    startDate: "2025-01-01",
    endDate: "2025-02-01",
    contractMm: "2.50",
    assignmentStatus: "CNCL",
  };
  vi.mocked(api.getDeveloperAssignments)
    .mockResolvedValueOnce({ ...empty, items: [item], total: 51 })
    .mockResolvedValueOnce({
      ...empty,
      page: 2,
      items: [
        { ...item, id: "A-902", projectId: "P-902", assignmentStatus: "ACT" },
      ],
      total: 51,
    });
  render(<DeveloperAssignmentsPanel developerId={row.id} />);
  await screen.findByText("P-901");
  expect(screen.getByText("취소")).toHaveClass("sicost-tone-bad");
  expect(screen.getByText("2.50")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await screen.findByText("P-902");
  expect(api.getDeveloperAssignments).toHaveBeenLastCalledWith(
    row.id,
    2,
    50,
    expect.any(AbortSignal),
  );
});
it("history error is not empty and retry performs only GET", async () => {
  vi.mocked(api.getDeveloperAssignments).mockRejectedValueOnce(
    new Error("투입 조회 실패"),
  );
  render(<DeveloperAssignmentsPanel developerId={row.id} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("투입 조회 실패");
  expect(screen.queryByText("투입 이력이 없습니다.")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("투입 이력이 없습니다.");
  expect(api.saveDeveloperBulkStatus).not.toHaveBeenCalled();
});
it("inactive assignment tab makes no request", () => {
  render(<DeveloperAssignmentsPanel developerId={row.id} active={false} />);
  expect(api.getDeveloperAssignments).not.toHaveBeenCalled();
});
it("history size change resets the page to one", async () => {
  render(<DeveloperAssignmentsPanel developerId={row.id} />);
  await screen.findByText("투입 이력이 없습니다.");
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "한 화면 건수" }),
    "20",
  );
  await waitFor(() =>
    expect(api.getDeveloperAssignments).toHaveBeenLastCalledWith(
      row.id,
      1,
      20,
      expect.any(AbortSignal),
    ),
  );
});
function Host({
  filters = {},
  page = 1,
  size = 50,
}: {
  filters?: api.DeveloperFilters;
  page?: number;
  size?: number;
}) {
  const selection = useDeveloperSelection(filters, size);
  return (
    <>
      <DeveloperStatusToolbar
        selection={selection}
        rows={[row]}
        total={123}
        onCommitted={vi.fn()}
        onRefresh={vi.fn()}
      />
      <span>페이지 {page}</span>
      <input
        aria-label="개발자 선택"
        type="checkbox"
        checked={selection.items.has(row.id)}
        onChange={(e) => selection.toggle(row, e.target.checked)}
      />
    </>
  );
}
it("select all freezes real IDs/revisions and retains across pages", async () => {
  const items = Array.from({ length: 123 }, (_, i) => ({
    id: `DEV-${String(901 + i).padStart(6, "0")}`,
    revision: `rev-${i}`,
  }));
  vi.mocked(api.getDeveloperSelection).mockResolvedValue({ items });
  const view = render(<Host />);
  await userEvent.click(
    screen.getByRole("button", { name: "조회된 123건 모두 선택" }),
  );
  await screen.findByText("선택 123건");
  await userEvent.click(screen.getByRole("checkbox", { name: "개발자 선택" }));
  expect(screen.getByText("선택 122건")).toBeInTheDocument();
  view.rerender(<Host page={2} />);
  expect(screen.getByText("선택 122건")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(api.saveDeveloperBulkStatus).toHaveBeenCalledWith("N", items.slice(1));
});
it.each(["filter", "sort", "size"])(
  "%s change clears selection",
  async (kind) => {
    const view = render(<Host />);
    await userEvent.click(screen.getByRole("checkbox"));
    expect(screen.getByText("선택 1건")).toBeInTheDocument();
    view.rerender(
      <Host
        filters={
          kind === "filter"
            ? { workStatus: "LEAVE" }
            : kind === "sort"
              ? { sort: "job" }
              : {}
        }
        size={kind === "size" ? 20 : 50}
      />,
    );
    expect(screen.getByText("선택 0건")).toBeInTheDocument();
  },
);
it("page selection uses the displayed row revisions", async () => {
  render(<Host />);
  await userEvent.click(
    screen.getByRole("button", { name: "페이지 전체 선택" }),
  );
  expect(screen.getByText("선택 1건")).toBeInTheDocument();
  expect(screen.getByRole("checkbox")).toBeChecked();
});
it("late full-selection result cannot restore an old filter", async () => {
  let resolve!: (value: { items: api.DeveloperSelectionItem[] }) => void;
  vi.mocked(api.getDeveloperSelection).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  const view = render(<Host />);
  await userEvent.click(
    screen.getByRole("button", { name: "조회된 123건 모두 선택" }),
  );
  view.rerender(<Host filters={{ includeStopped: true }} />);
  await act(async () => resolve({ items: [row] }));
  expect(screen.getByText("선택 0건")).toBeInTheDocument();
});
it("failed selection never claims targets selected", async () => {
  vi.mocked(api.getDeveloperSelection).mockRejectedValue(
    new Error("선택 조회 실패"),
  );
  render(<Host />);
  await userEvent.click(
    screen.getByRole("button", { name: "조회된 123건 모두 선택" }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("선택 조회 실패");
  expect(screen.getByText("선택 0건")).toBeInTheDocument();
});
it("single status confirms and cancellation preserves selection without writing", async () => {
  const committed = vi.fn();
  render(
    <DeveloperSingleStatus
      row={row}
      onCommitted={committed}
      onRefresh={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.saveDeveloperBulkStatus).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  await screen.findByText("적용 1건 · 변경 없음 0건 · 거부 0건");
  expect(api.saveDeveloperBulkStatus).toHaveBeenCalledWith("N", [
    { id: row.id, revision: row.revision },
  ]);
  expect(committed).toHaveBeenCalledTimes(1);
});
it("manual stop offers resume only and has no delete", () => {
  render(
    <DeveloperSingleStatus
      row={{ ...row, useYn: "N" }}
      onCommitted={vi.fn()}
      onRefresh={vi.fn()}
    />,
  );
  expect(screen.getByRole("button", { name: "다시 사용" })).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /사용 중지|삭제/ }),
  ).not.toBeInTheDocument();
});
it("network failure blocks resubmit until explicit reload with fresh data", async () => {
  vi.mocked(api.saveDeveloperBulkStatus).mockRejectedValue(
    new Error("통신 실패"),
  );
  const refresh = vi.fn();
  render(
    <DeveloperSingleStatus
      row={row}
      onCommitted={vi.fn()}
      onRefresh={refresh}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("통신 실패");
  expect(screen.getByRole("button", { name: "사용 중지" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(refresh).toHaveBeenCalledTimes(1);
  expect(api.saveDeveloperBulkStatus).toHaveBeenCalledTimes(1);
});
it("committed update refreshes immediately and refresh failure offers only requery", async () => {
  render(
    <DeveloperSingleStatus
      row={row}
      onCommitted={() => Promise.reject(new Error("reload"))}
      onRefresh={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "저장됐지만 다시 불러오지 못했습니다",
  );
  await userEvent.click(screen.getByRole("button", { name: "재조회" }));
  expect(api.saveDeveloperBulkStatus).toHaveBeenCalledTimes(1);
});
it("blocked result displays project identity, period and count with error tone", async () => {
  vi.mocked(api.saveDeveloperBulkStatus).mockResolvedValue({
    applied: [],
    noChange: [],
    rejected: [
      {
        id: row.id,
        code: "DEVELOPER_IN_USE",
        message: "예정 투입이 있습니다.",
        details: {
          referenceCount: 1,
          projects: [
            {
              id: "P-901",
              name: "동명 프로젝트",
              startDate: "2026-07-01",
              endDate: "2026-08-01",
            },
          ],
        },
      },
    ],
    appliedCount: 0,
    noChangeCount: 0,
    rejectedCount: 1,
  });
  render(
    <DeveloperSingleStatus
      row={row}
      onCommitted={vi.fn()}
      onRefresh={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByText("거부")).toHaveClass("sicost-tone-bad");
  expect(screen.getByText("차단 1건")).toBeInTheDocument();
  expect(screen.getByText(/동명 프로젝트 \(P-901\)/)).toHaveTextContent(
    "2026-07-01 ~ 2026-08-01",
  );
});
it("invalid result totals never report success", async () => {
  vi.mocked(api.saveDeveloperBulkStatus).mockResolvedValue({
    applied: [],
    noChange: [],
    rejected: [],
    appliedCount: 2,
    noChangeCount: 0,
    rejectedCount: 0,
  });
  const committed = vi.fn();
  render(
    <DeveloperSingleStatus
      row={row}
      onCommitted={committed}
      onRefresh={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "처리 결과를 확인할 수 없습니다",
  );
  expect(committed).not.toHaveBeenCalled();
});
