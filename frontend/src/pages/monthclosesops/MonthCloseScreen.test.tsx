import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import {
  MonthCloseScreen,
  ClosePreviewDialog,
  ReopenDialog,
} from "./MonthCloseScreen";
import * as api from "./monthCloseApi";
import { ApiError } from "../../shared/api";
vi.mock("./monthCloseApi");
vi.mock("../shared/sharedApi", () => ({
  getPeriodContext: vi
    .fn()
    .mockResolvedValue({ currentMonth: "2026-07", cashBaseMonth: "2026-01" }),
}));
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
const checks: api.CloseCheck[] = [
  "PAY",
  "MM_OVER",
  "BILL",
  "FI",
  "SETL",
  "BILL_MISS",
].map((code, i) => ({
  code,
  name: [
    "급여 업로드",
    "MM 초과자",
    "번호 미입력",
    "재무 항목",
    "영업 파트 정산",
    "청구 누락 점검",
  ][i],
  status: i === 4 ? "WARN" : "OK",
  count: i === 4 ? 1 : 0,
  description: "전체 원천 점검",
  checkedAt: null,
  linkScreenId: i === 0 ? null : "UI-10",
  linkMonth: "2026-01",
  linkFilter: "",
}));
function data(): api.MonthCloses {
  return {
    month: "2026-01",
    status: "WORK",
    closedBy: null,
    closedAt: null,
    nextCloseMonth: "2026-01",
    latestCloseMonth: null,
    managementAllowed: true,
    canCheck: true,
    canClose: true,
    canReopen: false,
    revision: "current",
    checks,
    checkMode: "CURRENT",
    warningCount: 1,
    history: { items: [], total: 0, page: 1, size: 50, hasMore: false },
    issues: [],
  };
}
function preview(): api.ClosePreview {
  return {
    month: "2026-01",
    revision: "preview",
    canClose: true,
    checks,
    warningCount: 1,
    remainingCheckNames: ["영업 파트 정산"],
    parts: [],
    eligiblePartCount: 1,
    autoConfirmCount: 1,
    snapshots: [
      { table: "TB_CO_MON_PL", rowCount: 1, preservedIndependentFixedCount: 0 },
    ],
    usesEstimatedPayroll: true,
    cashImpact: {
      excludedUnpaidBills: "7",
      excludedUnconfirmedTaxes: null,
      incomeBefore: "10",
      incomeAfter: "3",
      taxPaymentBefore: null,
      taxPaymentAfter: "0",
      closingBalanceBefore: null,
      closingBalanceAfter: null,
    },
    blockingIssues: [],
  };
}
function reopen(): api.ReopenPreview {
  return {
    month: "2026-01",
    revision: "reopen",
    canReopen: true,
    closedBy: "server",
    closedAt: "2026-01-31 12:00:00",
    snapshots: [],
    settlementsPreserved: true,
    adjustments: [
      {
        adjustmentId: "later",
        targetMonth: "2026-01",
        applyMonth: "2026-06",
        amount: "19",
        reason: "늦은 조정",
      },
    ],
    adjustmentCount: 1,
    adjustmentAmount: "19",
    adjustmentMonths: ["2026-06"],
    blockingIssues: [],
  };
}
const committed: api.CloseCommit = {
  month: "2026-01",
  status: "CLOSE",
  revision: "saved",
  operationId: "a49a6b49-3f16-4bd7-a317-af1da2ef44d8",
  history: {
    month: "2026-01",
    historySequence: 1,
    action: "CLOSE",
    actorId: "server",
    occurredAt: "2026-07-01 12:00:00",
    reason: null,
    remainingChecks: "영업 파트 정산",
  },
  replayed: false,
  snapshots: [],
  settlementsPreserved: true,
  invalidatedQueries: [],
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.getMonthCloses).mockResolvedValue(data());
  vi.mocked(api.createMonthClosePreview).mockResolvedValue(preview());
  vi.mocked(api.createMonthCloseReopenPreview).mockResolvedValue(reopen());
  vi.mocked(api.saveMonthClose).mockResolvedValue(committed);
  vi.mocked(api.saveMonthCloseReopen).mockResolvedValue({
    ...committed,
    status: "WORK",
  });
});
async function mount() {
  render(
    <MemoryRouter>
      <MonthCloseScreen />
    </MemoryRouter>,
  );
  act(() =>
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-14",
          value: { type: "YEAR_MONTH", value: "2026-01" },
          context: { currentMonth: "2026-07", cashBaseMonth: "2026-01" },
        },
      }),
    ),
  );
  await screen.findByText("마감 점검");
}
it("renders exact six cards, current calculation and empty yearly history without a payroll action", async () => {
  await mount();
  expect(
    screen.getAllByTestId(
      /^month-close-check-(pay|mm-over|bill|fi|setl|bill-miss)$/,
    ),
  ).toHaveLength(6);
  expect(
    within(screen.getByTestId("month-close-check-pay")).queryByRole("button"),
  ).not.toBeInTheDocument();
  expect(
    screen.getByText("선택연도 마감 이력이 없습니다."),
  ).toBeInTheDocument();
  expect(api.getMonthCloses).toHaveBeenCalledWith("2026-01", 1, 50);
});
it("preview cancel never sends a write or acceptance", async () => {
  await mount();
  await userEvent.click(screen.getByRole("button", { name: "마감하기" }));
  expect(await screen.findByRole("dialog")).toHaveTextContent(
    "예정급여 사용: 예",
  );
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.saveMonthClose).not.toHaveBeenCalled();
  expect(api.saveMonthCloseChecks).not.toHaveBeenCalled();
});
it("requires explicit WARN consent and sends only the immutable preview revision and a new UUID", async () => {
  await mount();
  await userEvent.click(screen.getByRole("button", { name: "마감하기" }));
  await screen.findByRole("dialog");
  expect(screen.getByRole("button", { name: "확인 후 마감" })).toBeDisabled();
  await userEvent.click(screen.getByRole("checkbox"));
  await userEvent.click(screen.getByRole("button", { name: "확인 후 마감" }));
  await waitFor(() => expect(api.saveMonthClose).toHaveBeenCalledOnce());
  expect(api.saveMonthClose).toHaveBeenCalledWith("2026-01", {
    expectedRevision: "preview",
    operationId: expect.stringMatching(/^[0-9a-f-]{36}$/),
    acceptWarnings: true,
  });
  expect(api.publishMonthCloseCommit).toHaveBeenCalledWith("2026-01");
});
it("blocking source problem stays outside the six checks and disables close", async () => {
  const p = preview();
  p.canClose = false;
  p.blockingIssues = [
    {
      code: "AMOUNT_UNCONFIRMED",
      message: "필수 단가 미확인",
      month: p.month,
      sourceId: "part",
    },
  ];
  render(
    <ClosePreviewDialog
      preview={p}
      pending={false}
      onConfirm={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  expect(screen.getByRole("alert")).toHaveTextContent("필수 단가 미확인");
  await userEvent.click(screen.getByRole("checkbox"));
  expect(screen.getByRole("button", { name: "확인 후 마감" })).toBeDisabled();
  expect(screen.getAllByTestId(/^month-close-preview-check-/)).toHaveLength(6);
});
it("reopen requires a full reason and explicit acknowledgement of late adjustments", async () => {
  const confirm = vi.fn().mockResolvedValue(undefined);
  render(
    <ReopenDialog
      preview={reopen()}
      pending={false}
      onConfirm={confirm}
      onCancel={vi.fn()}
    />,
  );
  expect(screen.getByText("2026-06")).toBeInTheDocument();
  const reason = screen.getByRole("textbox");
  await userEvent.type(reason, " ");
  await userEvent.click(screen.getByRole("checkbox"));
  expect(screen.getByRole("button", { name: "확인 후 해제" })).toBeDisabled();
  await userEvent.clear(reason);
  await userEvent.type(reason, " 정정 ");
  await userEvent.click(screen.getByRole("button", { name: "확인 후 해제" }));
  expect(confirm).toHaveBeenCalledWith("정정", true);
});
it("does not truncate a 501-character reason into a valid request", async () => {
  render(
    <ReopenDialog
      preview={{ ...reopen(), adjustmentCount: 0 }}
      pending={false}
      onConfirm={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  await userEvent.type(screen.getByRole("textbox"), "가".repeat(501));
  expect(screen.getByRole("button", { name: "확인 후 해제" })).toBeDisabled();
  expect(screen.getByRole("textbox")).toHaveValue("가".repeat(501));
});
it("CLOSE hides recheck and close and only allows latest reopen", async () => {
  vi.mocked(api.getMonthCloses).mockResolvedValue({
    ...data(),
    status: "CLOSE",
    checkMode: "CLOSE_SNAPSHOT",
    canCheck: false,
    canClose: false,
    canReopen: false,
    closedBy: "original",
    closedAt: "2026-01-31 10:00:00",
  });
  await mount();
  expect(
    screen.queryByRole("button", { name: "다시 점검" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "마감하기" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "마감 해제" }),
  ).not.toBeInTheDocument();
  expect(screen.getByText(/마감 당시 6점검/)).toBeInTheDocument();
});
it("management-denied state prevents every command even in an otherwise open month", async () => {
  vi.mocked(api.getMonthCloses).mockResolvedValue({
    ...data(),
    managementAllowed: false,
  });
  await mount();
  expect(screen.getByRole("button", { name: "마감하기" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "다시 점검" })).toBeDisabled();
});
it("separates committed write from failed refresh and permits GET-only recovery", async () => {
  await mount();
  vi.mocked(api.getMonthCloses).mockRejectedValue(new Error("조회 실패"));
  vi.mocked(api.saveMonthCloseChecks).mockResolvedValue({
    month: "2026-01",
    status: "WORK",
    revision: "new",
    checks,
    checkedAt: "2026-01-01 00:00:00",
  });
  await userEvent.click(screen.getByRole("button", { name: "다시 점검" }));
  expect(
    await screen.findByText(
      "저장됐지만 다시 불러오지 못했습니다. 다시 조회해 주세요.",
    ),
  ).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "다시 점검" })).toBeDisabled();
  vi.mocked(api.getMonthCloses).mockResolvedValue(data());
  await userEvent.click(screen.getByTestId("month-close-reload-button"));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "다시 점검" })).toBeEnabled(),
  );
  expect(api.saveMonthCloseChecks).toHaveBeenCalledOnce();
});
it("prevents continuous clicks while saving and does not automatically replay network failures", async () => {
  await mount();
  let reject!: (e: Error) => void;
  vi.mocked(api.saveMonthCloseChecks).mockImplementation(
    () =>
      new Promise((_, r) => {
        reject = r;
      }),
  );
  await userEvent.dblClick(screen.getByRole("button", { name: "다시 점검" }));
  expect(api.saveMonthCloseChecks).toHaveBeenCalledOnce();
  await act(async () => reject(new Error("네트워크 실패")));
  expect(api.saveMonthCloseChecks).toHaveBeenCalledOnce();
  expect(screen.getByRole("button", { name: "다시 점검" })).toBeDisabled();
});
it("rollback failure keeps prior values and never publishes success", async () => {
  await mount();
  vi.mocked(api.saveMonthCloseChecks).mockRejectedValue(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "저장 실패",
      requestId: "req-failure",
    }),
  );
  await userEvent.click(screen.getByRole("button", { name: "다시 점검" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("req-failure");
  expect(screen.getByText("미마감")).toBeInTheDocument();
  expect(api.publishMonthCloseCommit).not.toHaveBeenCalled();
  expect(screen.queryByText("저장했습니다.")).not.toBeInTheDocument();
});
it("pending dialog blocks cancellation and edits", async () => {
  const cancel = vi.fn();
  render(
    <ReopenDialog
      preview={reopen()}
      pending
      onConfirm={vi.fn()}
      onCancel={cancel}
    />,
  );
  expect(screen.getByRole("textbox")).toBeDisabled();
  expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
  await userEvent.keyboard("{Escape}");
  expect(cancel).not.toHaveBeenCalled();
});
it("binds latest reopen preview and confirmation to the selected month and immutable revision", async () => {
  vi.mocked(api.getMonthCloses).mockResolvedValue({
    ...data(),
    status: "CLOSE",
    checkMode: "CLOSE_SNAPSHOT",
    canCheck: false,
    canClose: false,
    canReopen: true,
  });
  await mount();
  expect(screen.getByTestId("month-close-screen")).toHaveAttribute(
    "data-screen-id",
    "UI-14",
  );
  await userEvent.click(screen.getByTestId("month-close-reopen-button"));
  const dialog = await screen.findByTestId("month-close-reopen-dialog");
  expect(api.createMonthCloseReopenPreview).toHaveBeenCalledWith(
    "2026-01",
    "current",
  );
  await userEvent.type(within(dialog).getByRole("textbox"), " 자료 정정 ");
  await userEvent.click(within(dialog).getByRole("checkbox"));
  await userEvent.click(screen.getByTestId("month-close-reopen-submit-button"));
  await waitFor(() => expect(api.saveMonthCloseReopen).toHaveBeenCalledOnce());
  expect(api.saveMonthCloseReopen).toHaveBeenCalledWith("2026-01", {
    expectedRevision: "reopen",
    operationId: expect.stringMatching(/^[0-9a-f-]{36}$/),
    reason: "자료 정정",
    acceptAdjustments: true,
  });
  expect(api.publishMonthCloseCommit).toHaveBeenCalledWith("2026-01");
});
it("keeps underlying checks and preview cards independently addressable and restores trigger focus on cancel", async () => {
  await mount();
  const button = screen.getByTestId("month-close-close-button");
  await userEvent.click(button);
  const dialog = await screen.findByTestId("month-close-preview-dialog");
  expect(
    screen.getAllByTestId(
      /^month-close-check-(pay|mm-over|bill|fi|setl|bill-miss)$/,
    ),
  ).toHaveLength(6);
  expect(
    within(dialog).getAllByTestId(/^month-close-preview-check-/),
  ).toHaveLength(6);
  expect(screen.getByTestId("month-close-submit-button")).toHaveAttribute(
    "data-action-id",
    "B143-14-CLOSE-SUBMIT",
  );
  await userEvent.click(screen.getByTestId("month-close-cancel-button"));
  expect(button).toHaveFocus();
  expect(api.saveMonthClose).not.toHaveBeenCalled();
});
it("stale preview requires a GET before any new command and never silently confirms", async () => {
  await mount();
  vi.mocked(api.createMonthClosePreview).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "원천이 변경되었습니다.",
      requestId: "stale-preview",
    }),
  );
  await userEvent.click(screen.getByTestId("month-close-close-button"));
  expect(await screen.findByRole("alert")).toHaveTextContent("stale-preview");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByTestId("month-close-close-button")).toBeDisabled();
  expect(api.saveMonthClose).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("month-close-reload-button"));
  await waitFor(() =>
    expect(screen.getByTestId("month-close-close-button")).toBeEnabled(),
  );
});
