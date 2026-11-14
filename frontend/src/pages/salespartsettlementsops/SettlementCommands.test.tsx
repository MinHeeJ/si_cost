import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import type { SettlementResult } from "../salespartsettlements/salesPartSettlementsApi";
import * as reads from "../salespartsettlements/salesPartSettlementsApi";
import * as api from "./settlementCommandsApi";
import { SettlementCardInput } from "./SettlementCardInput";
import { SettlementTrendPanel } from "./SettlementTrendPanel";
vi.mock("./settlementCommandsApi", async (original) => ({
  ...(await original<typeof api>()),
  saveSalesPartSettlementCard: vi.fn(),
  createSalesPartSettlementConfirmation: vi.fn(),
  createSalesPartSettlementCancellation: vi.fn(),
  getSalesPartSettlementTrend: vi.fn(),
}));
vi.mock("../salespartsettlements/salesPartSettlementsApi", () => ({
  getSalesPartSettlement: vi.fn(),
}));
function response(): SettlementResult {
  const totals = {
    personCount: 61,
    deficitPersonCount: 2,
    mm: "1.00",
    unitPrice: "100",
    payAmount: "10",
    salesExpenseAmount: "2",
    retirementAmount: "0",
    costAmount: "12",
    profitAmount: "88",
    personProfitShareAmount: "35",
    cardAmount: "5",
    partProfitAmount: "30",
  };
  return {
    partId: "part",
    month: "2026-07",
    status: "WORK",
    standard: {
      leaderId: null,
      leaderName: null,
      distributionRate: "40",
      salesExpenseAmount: "2",
      effectiveMonth: "2026-01",
      valid: true,
    },
    cardAmount: "5",
    revision: "revision",
    confirmedBy: null,
    confirmedByName: null,
    confirmedAt: null,
    lastCancellationReason: null,
    readOnly: false,
    readOnlyReasons: [],
    canSaveCard: true,
    canConfirm: true,
    canCancel: false,
    summary: totals,
    totals,
    items: [],
    pagination: { page: 1, size: 50, total: 61 },
    issues: [],
  };
}
let refresh: ReturnType<typeof vi.fn>,
  view: ReturnType<typeof vi.fn>,
  guard: (proceed: () => void) => void;
function mount(data = response()) {
  return render(
    <MemoryRouter>
      <SettlementCardInput
        data={data}
        refresh={refresh}
        onGuard={(next) => {
          guard = next;
        }}
        onView={view}
        trend={false}
      />
    </MemoryRouter>,
  );
}
beforeEach(() => {
  vi.resetAllMocks();
  refresh = vi.fn();
  view = vi.fn();
  vi.mocked(reads.getSalesPartSettlement).mockResolvedValue(response());
  vi.mocked(api.saveSalesPartSettlementCard).mockResolvedValue(
    {} as api.SettlementMutation,
  );
  vi.mocked(api.createSalesPartSettlementConfirmation).mockResolvedValue(
    {} as api.SettlementMutation,
  );
  vi.mocked(api.createSalesPartSettlementCancellation).mockResolvedValue(
    {} as api.SettlementMutation,
  );
});
it.each(["-1", "1.5", "1,00", "1원", "1 000", "1000000000000000"])(
  "strict parser rejects %s without a mutation",
  async (value) => {
    mount();
    await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
    await userEvent.type(screen.getByLabelText("법인카드 사용액"), value);
    expect(screen.getByRole("button", { name: "카드 저장" })).toBeDisabled();
    expect(api.saveSalesPartSettlementCard).not.toHaveBeenCalled();
  },
);
it("saves blank as zero directly with exact natural identity payload", async () => {
  mount();
  await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
  await userEvent.click(screen.getByRole("button", { name: "카드 저장" }));
  await waitFor(() =>
    expect(api.saveSalesPartSettlementCard).toHaveBeenCalledWith("part", {
      month: "2026-07",
      cardAmount: "0",
      expectedRevision: "revision",
    }),
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("draft preview uses exact integers beyond browser Number precision and never saves on typing", async () => {
  const data = response();
  data.summary = {
    ...data.summary,
    personProfitShareAmount: "9007199254740993",
  };
  mount(data);
  await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
  await userEvent.type(screen.getByLabelText("법인카드 사용액"), "9");
  expect(screen.getByTestId("settlement-card-draft")).toHaveTextContent(
    "저장 전 · 예상 파트 이익금 9,007,199,254,740,984 원",
  );
  expect(api.saveSalesPartSettlementCard).not.toHaveBeenCalled();
  expect(api.createSalesPartSettlementConfirmation).not.toHaveBeenCalled();
});
it("unknown source totals stay unknown in the unsaved preview", async () => {
  const data = response();
  data.summary = { ...data.summary, personProfitShareAmount: null };
  mount(data);
  await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
  expect(screen.getByTestId("settlement-card-draft")).toHaveTextContent(
    "예상 파트 이익금 – 원",
  );
});
it("confirmation previews draft with whole-population counts and no preceding card PUT", async () => {
  mount();
  await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
  await userEvent.type(screen.getByLabelText("법인카드 사용액"), "9");
  await userEvent.click(screen.getByRole("button", { name: "정산 확정" }));
  expect(screen.getByText(/전체 61명 · 적자 2명/)).toBeVisible();
  expect(screen.getByText(/최종 파트 이익금 26 원/)).toBeVisible();
  await userEvent.click(screen.getByRole("button", { name: /^확정$/ }));
  await waitFor(() =>
    expect(api.createSalesPartSettlementConfirmation).toHaveBeenCalledWith(
      "part",
      { month: "2026-07", cardAmount: "9", expectedRevision: "revision" },
    ),
  );
  expect(api.saveSalesPartSettlementCard).not.toHaveBeenCalled();
});
it("modal Escape keeps draft and returns focus without any write", async () => {
  mount();
  await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
  await userEvent.type(screen.getByLabelText("법인카드 사용액"), "9");
  await userEvent.click(screen.getByRole("button", { name: "정산 확정" }));
  await userEvent.keyboard("{Escape}");
  expect(screen.getByLabelText("법인카드 사용액")).toHaveValue("9");
  expect(screen.getByRole("button", { name: "정산 확정" })).toHaveFocus();
  expect(api.createSalesPartSettlementConfirmation).not.toHaveBeenCalled();
});
it("dirty stay causes zero navigation and discard navigates without saving", async () => {
  mount();
  await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
  await userEvent.type(screen.getByLabelText("법인카드 사용액"), "9");
  const proceed = vi.fn();
  act(() => guard(proceed));
  await screen.findByRole("dialog");
  await userEvent.click(screen.getByRole("button", { name: "계속 편집" }));
  expect(proceed).not.toHaveBeenCalled();
  expect(screen.getByLabelText("법인카드 사용액")).toHaveValue("9");
  act(() => guard(proceed));
  await userEvent.click(await screen.findByRole("button", { name: "버리기" }));
  await waitFor(() => expect(proceed).toHaveBeenCalledOnce());
  expect(api.saveSalesPartSettlementCard).not.toHaveBeenCalled();
});
it("failed dirty save preserves draft and destination and does not navigate", async () => {
  vi.mocked(api.saveSalesPartSettlementCard).mockRejectedValue(
    new Error("실패"),
  );
  mount();
  await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
  await userEvent.type(screen.getByLabelText("법인카드 사용액"), "9");
  const proceed = vi.fn();
  act(() => guard(proceed));
  await userEvent.click(
    await screen.findByRole("button", { name: "저장 후 이동" }),
  );
  await screen.findAllByText("실패");
  expect(proceed).not.toHaveBeenCalled();
  expect(screen.getByLabelText("법인카드 사용액")).toHaveValue("9");
  expect(screen.getByRole("dialog")).toBeVisible();
});
it("save then move requires successful GET readback", async () => {
  mount();
  await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
  await userEvent.type(screen.getByLabelText("법인카드 사용액"), "9");
  const proceed = vi.fn();
  act(() => guard(proceed));
  await userEvent.click(
    await screen.findByRole("button", { name: "저장 후 이동" }),
  );
  await waitFor(() => expect(proceed).toHaveBeenCalledOnce());
  expect(reads.getSalesPartSettlement).toHaveBeenCalled();
});
it("month and work-tab navigation events use same three-choice guard", async () => {
  mount();
  await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
  await userEvent.type(screen.getByLabelText("법인카드 사용액"), "9");
  const proceed = vi.fn();
  const event = new CustomEvent("sicost:screen-period-changing", {
    cancelable: true,
    detail: { screenId: "UI-19", proceed },
  });
  act(() => window.dispatchEvent(event));
  expect(event.defaultPrevented).toBe(true);
  await userEvent.click(
    await screen.findByRole("button", { name: "계속 편집" }),
  );
  expect(proceed).not.toHaveBeenCalled();
});
it("native beforeunload protects a dirty draft only", async () => {
  mount();
  const clean = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(clean);
  expect(clean.defaultPrevented).toBe(false);
  await userEvent.clear(screen.getByLabelText("법인카드 사용액"));
  const dirty = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(dirty);
  expect(dirty.defaultPrevented).toBe(true);
});
it("committed refresh failure only retries GET and never replays mutation", async () => {
  vi.mocked(reads.getSalesPartSettlement).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  mount();
  await userEvent.click(screen.getByRole("button", { name: "카드 저장" }));
  await screen.findAllByText("저장됐지만 다시 불러오지 못했습니다");
  expect(screen.getByRole("button", { name: "카드 저장" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() => expect(refresh).toHaveBeenCalled());
  expect(api.saveSalesPartSettlementCard).toHaveBeenCalledTimes(1);
  expect(reads.getSalesPartSettlement).toHaveBeenCalledTimes(2);
});
it("locked snapshot has read-only card and hides save/confirm/cancel when CLOSE", () => {
  mount({
    ...response(),
    status: "AUTO",
    canSaveCard: false,
    canConfirm: false,
    canCancel: false,
  });
  expect(screen.queryByRole("textbox", {name: "법인카드 사용액"})).not.toBeInTheDocument();
  expect(screen.getByText("법인카드 사용액 5")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "카드 저장" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "확정 취소" }),
  ).not.toBeInTheDocument();
});
it("released AUTO opens cancellation and validates reason without truncation", async () => {
  mount({
    ...response(),
    status: "AUTO",
    canSaveCard: false,
    canConfirm: false,
    canCancel: true,
  });
  await userEvent.click(screen.getByRole("button", { name: "확정 취소" }));
  const reason = screen.getByLabelText("취소 사유");
  const submit = within(screen.getByRole("dialog")).getByRole("button", {
    name: /^확정 취소$/,
  });
  expect(submit).toBeDisabled();
  await userEvent.type(reason, "가".repeat(501));
  expect(submit).toBeDisabled();
  await userEvent.clear(reason);
  await userEvent.type(reason, " 정산 재검토 ");
  await userEvent.click(submit);
  await waitFor(() =>
    expect(api.createSalesPartSettlementCancellation).toHaveBeenCalledWith(
      "part",
      { month: "2026-07", reason: "정산 재검토", expectedRevision: "revision" },
    ),
  );
});
it("trend renders five rows, null dashes, all-month totals and errors with GET retry", async () => {
  const metrics = (
    ["MM", "UNIT_PRICE", "COST", "PROFIT", "PROFIT_SHARE"] as const
  ).map((metric) => ({
    metric,
    months: [
      {
        month: "2026-01",
        value: null,
        employeeType: null,
        source: "BEFORE_BASE" as const,
        issues: [],
      },
      {
        month: "2026-02",
        value: "1",
        employeeType: "FT",
        source: "CNFM" as const,
        issues: [],
      },
    ],
    total: "1",
    remark: null,
  }));
  vi.mocked(api.getSalesPartSettlementTrend)
    .mockRejectedValueOnce(new Error("추이 실패"))
    .mockResolvedValue({
      partId: "part",
      month: "2026-02",
      months: ["2026-01", "2026-02"],
      items: [
        {
          developerId: "past",
          name: "과거 인력",
          managementNo: "number",
          metrics,
        },
      ],
      pagination: { page: 1, size: 50, total: 1 },
      monthlyTotals: [
        {
          month: "2026-01",
          personProfitShareAmount: null,
          cardAmount: null,
          partProfitAmount: null,
          issues: [],
        },
        {
          month: "2026-02",
          personProfitShareAmount: "35",
          cardAmount: "5",
          partProfitAmount: "30",
          issues: [],
        },
      ],
      periodPartProfitAmount: "30",
      issues: [],
    });
  render(<SettlementTrendPanel partId="part" month="2026-02" />);
  await screen.findByText("추이 실패");
  await userEvent.click(screen.getByRole("button", { name: /다시/ }));
  await screen.findByText("과거 인력");
  expect(screen.getAllByTestId(/^settlement-trend-past-/)).toHaveLength(5);
  expect(screen.getByText("카드는 월 1회 차감")).toBeVisible();
  expect(screen.getAllByText("–").length).toBeGreaterThan(0);
});
