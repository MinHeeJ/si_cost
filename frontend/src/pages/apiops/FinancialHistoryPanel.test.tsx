import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { FinancialHistoryPanel } from "./FinancialHistoryPanel";
import type {
  FinancialHistoryEntry,
  FinancialHistoryPage,
} from "./financialReadApi";

const row: FinancialHistoryEntry = {
  historyId: "9007199254740993",
  targetTable: "TB_FI_ITEM",
  targetKey: "202607:1",
  changeType: "DEL",
  field: "ITEM_NM",
  beforeValue: "삭제 전 항목",
  afterValue: null,
  reason: "정정 사유",
  userId: "admin",
  occurredAt: "2026-07-15 09:01:02",
};
const page = (
  items: FinancialHistoryEntry[] = [],
  total = items.length,
): FinancialHistoryPage => ({ items, total, page: 1, size: 50 });
const response = (data: FinancialHistoryPage) =>
  new Response(JSON.stringify({ success: true, meta: {}, data }));
const fetcher = vi.fn<typeof fetch>();
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("fetch", fetcher);
});
afterEach(() => vi.unstubAllGlobals());

it("SCR-UI-10-HISTORY-CLOSE: initially collapsed, opens and closes without saving, restores trigger focus", async () => {
  fetcher.mockImplementation(async () => response(page([row])));
  render(<FinancialHistoryPanel month="2026-07" />);
  expect(fetcher).not.toHaveBeenCalled();
  expect(screen.getByTestId("financial-history-panel")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-10-HISTORY-CLOSE",
  );
  await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
  await screen.findByText("삭제 전 항목");
  expect(screen.getByTestId("financial-history-panel")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-10-HISTORY-OPEN",
  );
  expect(String(fetcher.mock.lastCall?.[0])).toBe(
    "/api/financial-history?month=2026-07&scope=MONTH&page=1&size=50",
  );
  await userEvent.click(screen.getByRole("button", { name: "닫기" }));
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "변경 이력" })).toHaveFocus();
  expect(fetcher.mock.lastCall?.[1]?.signal?.aborted).toBe(true);
  expect(
    fetcher.mock.calls.every(
      ([, options]) => !options?.method && !options?.body,
    ),
  ).toBe(true);
});
it("SCR-UI-10-HISTORY-OPEN: scope and paging use server total, size or scope resets to page one", async () => {
  fetcher.mockImplementation(async () => response(page([row], 101)));
  render(<FinancialHistoryPanel month="2026-07" />);
  await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
  await screen.findByText("삭제 전 항목");
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(String(fetcher.mock.lastCall?.[0])).toContain("page=2&size=50"),
  );
  await waitFor(() =>
    expect(screen.getByLabelText("한 화면 건수")).toBeEnabled(),
  );
  await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
  await waitFor(() =>
    expect(String(fetcher.mock.lastCall?.[0])).toContain("page=1&size=20"),
  );
  await userEvent.selectOptions(screen.getByLabelText("범위"), "TAX");
  await waitFor(() =>
    expect(String(fetcher.mock.lastCall?.[0])).toContain(
      "scope=TAX&page=1&size=20",
    ),
  );
  await userEvent.selectOptions(screen.getByLabelText("범위"), "CARDS");
  await waitFor(() =>
    expect(String(fetcher.mock.lastCall?.[0])).toContain(
      "scope=CARDS&page=1&size=20",
    ),
  );
});
it("preserves field-level old/new/null, composite key, seconds, long text and each badge tone", async () => {
  const long = "<script>삭제 전 원문</script>".repeat(15);
  const rows = (["NEW", "UPD", "DEL", "STOP", "RESUME"] as const).map(
    (changeType, i) => ({
      ...row,
      historyId: String(i + 1),
      changeType,
      beforeValue: i === 2 ? long : String(i),
      targetKey: i === 0 ? "2026:CNFM" : row.targetKey,
    }),
  );
  fetcher.mockImplementation(async () => response(page(rows)));
  const { container } = render(<FinancialHistoryPanel month="2026-07" dense />);
  await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
  expect(await screen.findByText("등록")).toHaveClass("sicost-tone-ok");
  expect(screen.getByText("변경")).toHaveClass("sicost-tone-info");
  expect(screen.getByText("삭제")).toHaveClass("sicost-tone-bad");
  expect(screen.getByText("중지")).toHaveClass("sicost-tone-lock");
  expect(screen.getByText("재개")).toHaveClass("sicost-tone-ok");
  expect(screen.getByText("2026:CNFM")).toBeInTheDocument();
  expect(screen.getAllByText("2026-07-15 09:01:02")).toHaveLength(5);
  await userEvent.click(screen.getByText("긴 값 펼침"));
  expect(screen.getByText(long).closest("details")).toHaveAttribute("open");
  expect(container.querySelector("script")).toBeNull();
  expect(screen.getByRole("table")).toHaveClass("sicost-dense");
  const cells = within(screen.getAllByRole("row")[1]).getAllByRole("cell");
  expect(cells[6]).toHaveTextContent("–");
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
});
it("loading does not claim empty, failed request shows trace and retries read only", async () => {
  let resolve!: (response: Response) => void;
  fetcher
    .mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    )
    .mockImplementation(async () => response(page()));
  render(<FinancialHistoryPanel month="2026-07" />);
  await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  expect(
    screen.queryByText("해당 범위의 변경 이력이 없습니다."),
  ).not.toBeInTheDocument();
  await act(async () =>
    resolve(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "이력 조회 실패",
          requestId: "finance-trace",
        }),
        { status: 500 },
      ),
    ),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("finance-trace");
  expect(fetcher).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("해당 범위의 변경 이력이 없습니다.");
  expect(fetcher).toHaveBeenCalledTimes(2);
});
it("collapse aborts pending request and late response cannot populate reopened history", async () => {
  let resolve!: (response: Response) => void;
  fetcher
    .mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    )
    .mockImplementation(async () => response(page()));
  render(<FinancialHistoryPanel month="2026-07" />);
  await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
  const oldSignal = fetcher.mock.lastCall?.[1]?.signal;
  await userEvent.click(screen.getByRole("button", { name: "접기" }));
  expect(oldSignal?.aborted).toBe(true);
  await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
  await screen.findByText("해당 범위의 변경 이력이 없습니다.");
  await act(async () => resolve(response(page([row]))));
  expect(screen.queryByText("삭제 전 항목")).not.toBeInTheDocument();
});
it("new period and post-commit refresh cancel stale reads and retain the open region", async () => {
  fetcher.mockImplementation(async () => response(page()));
  const { rerender } = render(
    <FinancialHistoryPanel month="2026-07" refreshKey={1} />,
  );
  await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
  await screen.findByText("해당 범위의 변경 이력이 없습니다.");
  const oldSignal = fetcher.mock.lastCall?.[1]?.signal;
  rerender(<FinancialHistoryPanel month="2026-06" refreshKey={2} />);
  await waitFor(() =>
    expect(String(fetcher.mock.lastCall?.[0])).toContain("month=2026-06"),
  );
  expect(oldSignal?.aborted).toBe(true);
  await screen.findByText("해당 범위의 변경 이력이 없습니다.");
  rerender(<FinancialHistoryPanel month="2026-06" refreshKey={3} />);
  await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(3));
});
