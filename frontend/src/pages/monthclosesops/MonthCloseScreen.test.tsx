import {
  act,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
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
function renderMonthClose() {
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
}
async function mount() {
  renderMonthClose();
  await screen.findByText("마감 점검");
}
it("shows the shared loading state until the first result is available", async () => {
  let resolve!: (result: api.MonthCloses) => void;
  vi.mocked(api.getMonthCloses).mockImplementationOnce(
    () => new Promise((done) => { resolve = done; }),
  );
  renderMonthClose();
  const loading = await screen.findByTestId("month-close-loading");
  expect(loading).toHaveRole("status");
  expect(loading).toHaveTextContent("월 마감 자료를 불러오는 중입니다.");
  expect(screen.queryByRole("table", { name: "마감 이력" })).not.toBeInTheDocument();
  await act(async () => resolve(data()));
  expect(loading).not.toBeInTheDocument();
  expect(screen.getByRole("table", { name: "마감 이력" })).toHaveTextContent(
    "선택연도 마감 이력이 없습니다.",
  );
});
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
it("shows the check instructions from the heading tooltip", async () => {
  await mount();
  const help = screen.getByRole("button", { name: "마감 점검 도움말" });
  await userEvent.click(help);
  const tooltip = screen.getByRole("tooltip");
  expect(tooltip).toHaveTextContent("선택한 달의 급여, 투입, 청구");
  expect(tooltip).toHaveTextContent("[다시 점검]을 누르면 점검 결과를 저장");
  await userEvent.keyboard("{Escape}");
  expect(tooltip).not.toBeInTheDocument();
});
it("refreshes in the background on window focus without showing the loading state", async () => {
  await mount();
  let resolve!: (result: api.MonthCloses) => void;
  vi.mocked(api.getMonthCloses).mockImplementation(
    () => new Promise((done) => { resolve = done; }),
  );
  act(() => {
    window.dispatchEvent(new Event("focus"));
    window.dispatchEvent(new Event("focus"));
  });
  expect(api.getMonthCloses).toHaveBeenCalledTimes(2);
  expect(screen.queryByTestId("month-close-loading")).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "마감 점검" })).toBeVisible();
  await act(async () => resolve({ ...data(), nextCloseMonth: "2026-02" }));
  expect(screen.getByRole("group", { name: "조회 기간 상태" })).toHaveTextContent("2026-02");
  expect(screen.queryByTestId("month-close-loading")).not.toBeInTheDocument();
});
it("uses the server history count and returns to page one when page size changes", async () => {
  const entries = Array.from({ length: 51 }, (_, index) => ({
    ...committed.history,
    historySequence: index + 1,
  }));
  vi.mocked(api.getMonthCloses).mockImplementation(async (_month, page = 1, size = 50) => {
    const start = (page - 1) * size;
    return {
      ...data(),
      history: {
        items: entries.slice(start, start + size),
        total: entries.length,
        page,
        size,
        hasMore: start + size < entries.length,
      },
    };
  });
  await mount();
  const navigation = screen.getByRole("navigation", { name: "페이지" });
  expect(within(navigation).getByText("1-50 / 51건")).toBeInTheDocument();
  await userEvent.click(within(navigation).getByRole("button", { name: "다음 페이지" }));
  await screen.findByText("51-51 / 51건");
  expect(api.getMonthCloses).toHaveBeenLastCalledWith("2026-01", 2, 50);
  await userEvent.selectOptions(screen.getByRole("combobox", { name: "한 화면 건수" }), "20");
  await screen.findByText("1-20 / 51건");
  expect(api.getMonthCloses).toHaveBeenLastCalledWith("2026-01", 1, 20);
});
it("opens the shared warning dialog from the check heading", async () => {
  await mount();
  const title = screen.getByRole("heading", { name: "마감 점검" });
  const warning = screen.getByRole("button", { name: "확인 필요 1건" });
  expect(title.closest("header")).toContainElement(warning);
  await userEvent.click(warning);
  const dialog = screen.getByRole("dialog", { name: "확인 필요 1건" });
  expect(within(dialog).getByRole("heading", { name: "2026-01" })).toBeVisible();
  expect(within(dialog).getByText("영업 파트 정산 · 전체 원천 점검")).toBeVisible();
  expect(within(dialog).queryByText(/급여 업로드/)).not.toBeInTheDocument();
  await userEvent.keyboard("{Escape}");
  expect(dialog).not.toBeInTheDocument();
});
it("omits the warning button when all checks are normal", async () => {
  vi.mocked(api.getMonthCloses).mockResolvedValue({
    ...data(),
    warningCount: 0,
    checks: checks.map((check) => ({ ...check, status: "OK" })),
  });
  await mount();
  expect(screen.queryByRole("button", { name: /확인 필요 .*건/ })).not.toBeInTheDocument();
});
it("preserves the closed-month notice without the pre-close warning button", async () => {
  vi.mocked(api.getMonthCloses).mockResolvedValue({
    ...data(),
    status: "CLOSE",
    closedBy: "마감자",
    closedAt: "2026-02-01",
  });
  await mount();
  expect(
    screen.queryByRole("button", { name: /확인 필요 .*건/ }),
  ).not.toBeInTheDocument();
  expect(screen.getByText(/2026-01는 마감되었습니다/)).toHaveTextContent(
    "고칠 내용은 다음 달 전월 조정으로 입력하세요.",
  );
});
it("preview cancel never sends a write or acceptance", async () => {
  await mount();
  await userEvent.click(screen.getByRole("button", { name: "마감하기" }));
  expect(await screen.findByRole("dialog")).toHaveTextContent(
    "마감하면 이 달 자료는 고칠 수 없습니다.",
  );
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.saveMonthClose).not.toHaveBeenCalled();
  expect(api.saveMonthCloseChecks).not.toHaveBeenCalled();
});
it("requires explicit WARN consent and sends only the immutable preview revision and a new UUID", async () => {
  await mount();
  await userEvent.click(screen.getByRole("button", { name: "마감하기" }));
  await screen.findByRole("dialog");
  expect(screen.getByTestId("month-close-submit-button")).toBeDisabled();
  await userEvent.click(screen.getByRole("checkbox"));
  await userEvent.click(screen.getByTestId("month-close-submit-button"));
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
  expect(screen.getByTestId("month-close-submit-button")).toBeDisabled();
  expect(screen.queryAllByTestId(/^month-close-preview-check-/)).toHaveLength(
    0,
  );
  expect(screen.getByRole("alert")).toHaveClass("sicost-field-error");
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
  expect(screen.getByRole("button", { name: "마감 해제" })).toBeDisabled();
  await userEvent.clear(reason);
  await userEvent.type(reason, " 정정 ");
  await userEvent.click(screen.getByRole("button", { name: "마감 해제" }));
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
  const user = userEvent.setup();
  await user.click(screen.getByRole("textbox"));
  await user.paste("가".repeat(501));
  expect(screen.getByRole("button", { name: "마감 해제" })).toBeDisabled();
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
  expect(
    screen.getByText("마감 당시 남은 항목은 마감 이력에서 볼 수 있습니다."),
  ).toBeVisible();
  expect(screen.queryByText("전월 조정 입력")).not.toBeInTheDocument();
  expect(screen.getAllByText("확인 완료")).toHaveLength(6);
  expect(screen.queryAllByText("확인하기")).toHaveLength(0);
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
  expect(await screen.findByText(/req-failure/)).toHaveTextContent("저장 실패");
  expect(screen.getByText("미마감")).toBeInTheDocument();
  expect(api.publishMonthCloseCommit).not.toHaveBeenCalled();
  expect(screen.queryByText("저장했습니다.")).not.toBeInTheDocument();
});
it("uses plain close sentences, conditional acceptance and named automatic settlements", async () => {
  render(
    <ClosePreviewDialog
      preview={{
        ...preview(),
        parts: [
          {
            partId: "PART-private",
            partName: "디지털사업파트",
            status: "WORK",
            willAutoConfirm: true,
            issues: [],
          },
        ],
      }}
      pending={false}
      onConfirm={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  const dialog = screen.getByTestId("month-close-preview-dialog");
  expect(dialog).toHaveTextContent("2026-01를 마감할까요?");
  expect(dialog).toHaveTextContent("마감하면 이 달 자료는 고칠 수 없습니다.");
  expect(dialog).toHaveTextContent(
    "확인할 항목 1개(영업 파트 정산)가 남아 있습니다. 남은 항목은 마감 이력에 함께 남습니다.",
  );
  expect(dialog).toHaveTextContent(
    "확정하지 않은 영업 파트 정산 1개(디지털사업파트)는 마감과 함께 자동 확정됩니다(마감 뒤 틀린 금액은 다음 달에 반영).",
  );
  expect(dialog.textContent).not.toMatch(/TB_|WARN|AUTO|CNFM|WORK|PART-|UI-\d/);
});
it("does not request warning acceptance when all checks are normal", async () => {
  const confirm = vi.fn().mockResolvedValue(undefined);
  render(
    <ClosePreviewDialog
      preview={{
        ...preview(),
        warningCount: 0,
        remainingCheckNames: [],
        parts: [],
      }}
      pending={false}
      onConfirm={confirm}
      onCancel={vi.fn()}
    />,
  );
  expect(screen.getByText("점검 항목이 모두 정상입니다.")).toBeVisible();
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  expect(screen.queryByText(/자동 확정됩니다/)).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("month-close-submit-button"));
  expect(confirm).toHaveBeenCalledWith(false);
});
it("places a plain blocking reason before the close explanation", () => {
  const message =
    "디지털사업파트의 2026-01 기준이 두 줄 겹칩니다. 거래처·영업 파트에서 기준을 확인해 주세요.";
  render(
    <ClosePreviewDialog
      preview={{
        ...preview(),
        canClose: false,
        blockingIssues: [
          {
            code: "PART_STANDARD_UNKNOWN",
            message,
            month: "2026-01",
            sourceType: "TB_BS_PART_STD",
            sourceId: "PART-private",
          },
        ],
      }}
      pending={false}
      onConfirm={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  const panel = screen.getByTestId("month-close-preview-panel");
  expect(panel.firstElementChild).toHaveTextContent(message);
  expect(panel.firstElementChild).toHaveClass("sicost-field-error");
  expect(screen.getByTestId("month-close-submit-button")).toBeDisabled();
  expect(panel.textContent).not.toMatch(/TB_|WARN|AUTO|CNFM|WORK|PART-|UI-\d/);
});
it("formats reopen adjustments without IDs or implementation language", () => {
  const value = reopen();
  value.adjustments[0] = {
    ...value.adjustments[0],
    adjustmentId: "PART-private",
    amount: "3000000",
  };
  value.adjustmentAmount = "3000000";
  render(
    <ReopenDialog
      preview={value}
      pending={false}
      onConfirm={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  const dialog = screen.getByTestId("month-close-reopen-dialog");
  expect(dialog).toHaveTextContent("2026-01 마감을 해제할까요?");
  expect(dialog).toHaveTextContent("1건(3,000,000원, 2026-06 반영)");
  expect(
    within(dialog)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["반영 월", "금액", "사유"]);
  expect(within(dialog).getByRole("cell", { name: "3,000,000" })).toBeVisible();
  expect(dialog.textContent).not.toMatch(/TB_|WARN|AUTO|CNFM|WORK|PART-|UI-\d/);
});
it.each([
  {
    canReopen: false,
    latestCloseMonth: "2026-01",
    hint: "마감 해제는 시스템 관리자만 할 수 있습니다",
  },
  {
    canReopen: true,
    latestCloseMonth: "2026-02",
    hint: "가장 최근에 마감한 달만 해제할 수 있습니다",
  },
])(
  "hides reopening for finance or a non-latest month: $latestCloseMonth / $canReopen",
  async (value) => {
    vi.mocked(api.getMonthCloses).mockResolvedValue({
      ...data(),
      status: "CLOSE",
      ...value,
    });
    await mount();
    expect(
      screen.queryByTestId("month-close-reopen-button"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("month-close-closed-notice")).toHaveTextContent(
      value.hint,
    );
    expect(
      screen.queryByRole("button", { name: "전월 조정 입력" }),
    ).not.toBeInTheDocument();
  },
);
it.each(["close", "reopen"] as const)(
  "closes the %s dialog at commit and updates state, notice, buttons and history after the immediate GET",
  async (kind) => {
    const closed: api.MonthCloses = {
      ...data(),
      status: "CLOSE",
      latestCloseMonth: "2026-01",
      canReopen: true,
      canCheck: false,
      canClose: false,
      closedByName: "재무담당",
      closedAt: "2026-02-01 10:00:00",
    };
    vi.mocked(api.getMonthCloses).mockResolvedValue(
      kind === "close" ? data() : closed,
    );
    await mount();
    await userEvent.click(
      screen.getByTestId(
        kind === "close"
          ? "month-close-close-button"
          : "month-close-reopen-button",
      ),
    );
    const dialog = await screen.findByTestId(
      kind === "close"
        ? "month-close-preview-dialog"
        : "month-close-reopen-dialog",
    );
    if (kind === "reopen")
      await userEvent.type(
        within(dialog).getByRole("textbox"),
        "청구 누락 반영",
      );
    await userEvent.click(within(dialog).getByRole("checkbox"));
    let resolve!: (result: api.MonthCloses) => void;
    vi.mocked(api.getMonthCloses).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    await userEvent.click(
      screen.getByTestId(
        kind === "close"
          ? "month-close-submit-button"
          : "month-close-reopen-submit-button",
      ),
    );
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
    expect(screen.getByTestId("month-close-loading")).toHaveTextContent(
      "불러오는 중",
    );
    // The shared period bar republishes the selected month after commit invalidation.
    // It must not clear data or invalidate this in-flight GET for the same month.
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
    const result = kind === "close" ? closed : data();
    const action = kind === "close" ? "CLOSE" : "OPEN";
    await act(async () =>
      resolve({
        ...result,
        history: {
          ...result.history,
          total: 1,
          items: [
            {
              ...committed.history,
              action,
              actorName: "재무담당",
              reason: kind === "reopen" ? "청구 누락 반영" : null,
            },
          ],
        },
      }),
    );
    expect(screen.queryByTestId("month-close-loading")).not.toBeInTheDocument();
    expect(api.publishMonthCloseCommit).toHaveBeenCalledWith("2026-01");
    expect(
      screen.getByTestId("month-close-history-row-2026-01-1"),
    ).toHaveTextContent("재무담당");
    if (kind === "close") {
      expect(screen.getByTestId("month-close-closed-notice")).toHaveTextContent(
        "재무담당",
      );
      expect(screen.getByTestId("month-close-reopen-button")).toBeEnabled();
      expect(
        screen.queryByTestId("month-close-close-button"),
      ).not.toBeInTheDocument();
    } else {
      expect(
        screen.queryByTestId("month-close-closed-notice"),
      ).not.toBeInTheDocument();
      expect(screen.getByTestId("month-close-close-button")).toBeEnabled();
      expect(
        screen.getByTestId("month-close-history-row-2026-01-1"),
      ).toHaveTextContent("청구 누락 반영");
    }
  },
);
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
    latestCloseMonth: "2026-01",
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
it("keeps six checks on the screen only and restores trigger focus on cancel", async () => {
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
    within(dialog).queryAllByTestId(/^month-close-preview-check-/),
  ).toHaveLength(0);
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
  expect(await screen.findByText(/stale-preview/)).toHaveTextContent(
    "원천이 변경되었습니다.",
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByTestId("month-close-close-button")).toBeDisabled();
  expect(api.saveMonthClose).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("month-close-reload-button"));
  await waitFor(() =>
    expect(screen.getByTestId("month-close-close-button")).toBeEnabled(),
  );
});

function LinkedLocation() {
  const location = useLocation();
  return <p data-testid="linked-context">{JSON.stringify(location.state)}</p>;
}
it("allows past operating-year MM read-only navigation without changing destination PERIOD", async () => {
  vi.mocked(api.getMonthCloses).mockResolvedValue({
    ...data(),
    checks: checks.map((c) =>
      c.code === "MM_OVER" ? { ...c, linkScreenId: "UI-12" } : c,
    ),
  });
  render(
    <MemoryRouter initialEntries={["/UI-14"]}>
      <Routes>
        <Route path="/UI-14" element={<MonthCloseScreen />} />
        <Route path="/UI-12" element={<LinkedLocation />} />
      </Routes>
    </MemoryRouter>,
  );
  act(() =>
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: {
          screenId: "UI-14",
          value: { type: "YEAR_MONTH", value: "2026-01" },
          context: { currentMonth: "2027-07", cashBaseMonth: "2026-01" },
        },
      }),
    ),
  );
  await userEvent.click(await screen.findByTestId("month-close-link-mm-over"));
  expect(await screen.findByTestId("linked-context")).toHaveTextContent(
    '"month":"2026-01"',
  );
  expect(screen.getByTestId("linked-context")).toHaveTextContent(
    '"fromMonthClose":true',
  );
});
