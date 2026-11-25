import { stubBusinessFetch } from "../../shared/testSupport/businessFixtures";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { FinancialItemsEditor } from "./FinancialItemsScreen";
import { financialEditorFixture } from "./financialFixtures";
import type { FinancialEditor } from "./financialApi";
import * as imports from "../b2/financialImportApi";

const fetcher = vi.fn<typeof fetch>();
let data: FinancialEditor;
const response = (value: unknown) =>
  new Response(JSON.stringify({ success: true, meta: {}, data: value }));
const failure = (
  message: string,
  code = "VALIDATION_ERROR",
  details?: unknown,
) =>
  new Response(
    JSON.stringify({
      success: false,
      meta: {},
      code,
      message,
      requestId: "finance-trace",
      details,
    }),
    { status: 400 },
  );
beforeEach(() => {
  vi.resetAllMocks();
  stubBusinessFetch(fetcher);
  data = financialEditorFixture();
  fetcher.mockImplementation(async (url, options) => {
    const path = String(url);
    if (path.startsWith("/api/codes/"))
      return response({ items: [{ code: "RENT", name: "임대료" }] });
    if (path.startsWith("/api/financial-history?"))
      return response({ items: [], total: 0, page: 1, size: 50 });
    if (path.startsWith("/api/financial-items/year-status"))
      return response({
        year: data.month.slice(0, 4),
        months: Array.from({ length: 12 }, (_, index) => {
          const month = `${data.month.slice(0, 4)}-${String(index + 1).padStart(2, "0")}`;
          return {
            month,
            closeStatus: "WORK",
            itemCount: month <= data.today.slice(0, 7) ? 0 : null,
            selectable: month <= data.today.slice(0, 7),
            editable: true,
          };
        }),
      });
    if (path === "/api/corporate-taxes/initialize")
      return response({ changed: false });
    if (path === "/api/financial-items/preview")
      return response({
        month: data.month,
        revision: data.revision,
        addedDrafts: [],
        copiedCount: 0,
        allocation: data.allocation,
        contributions: data.contributions,
        cardMonth: data.cardMonth,
        automaticTaxAmounts: {},
        message: "계산했습니다.",
      });
    if (path === "/api/financial-items" && options?.method === "PUT")
      return response({
        changed: true,
        revision: "next",
        message: "저장했습니다.",
        editor: data,
        createdCards: [],
      });
    if (path.startsWith("/api/financial-items")) {
      const month = new URL(path, "https://test.invalid").searchParams.get(
        "month",
      );
      return response(month ? { ...data, month } : data);
    }
    return response({ items: [] });
  });
});
afterEach(() => vi.unstubAllGlobals());
async function ready() {
  render(<FinancialItemsEditor />);
  await screen.findByTestId("financial-items-panel");
}
function writes() {
  return fetcher.mock.calls.filter(
    ([url, options]) =>
      String(url) === "/api/financial-items" && options?.method === "PUT",
  );
}
async function addItem() {
  await userEvent.click(screen.getByTestId("financial-items-add"));
  await userEvent.type(
    screen.getByTestId("financial-items-new-1-item-name"),
    "임대료",
  );
  await userEvent.type(
    screen.getByTestId("financial-items-new-1-amount"),
    "1,000 원",
  );
}

it("keeps only previous-month and upload title actions, with the template inside the unchanged upload dialog", async () => {
  const template = vi
    .spyOn(imports, "getB2Template")
    .mockRejectedValue(new Error("양식 조회 실패"));
  await ready();
  const title = within(
    screen
      .getByTestId("financial-load-previous")
      .closest(".sicost-title-action-group") as HTMLElement,
  );
  expect(
    title.getAllByRole("button").map((button) => button.textContent),
  ).toEqual(["지난달 항목 불러오기", "엑셀 업로드"]);
  expect(
    title.queryByRole("button", { name: /변경 이력|양식 내려받기/ }),
  ).not.toBeInTheDocument();
  await userEvent.click(title.getByRole("button", { name: "엑셀 업로드" }));
  const dialog = await screen.findByRole("dialog", {
    name: "엑셀 업로드 · 재무 항목",
  });
  expect(
    within(dialog).getByTestId("financial-upload-month"),
  ).toHaveTextContent(data.month);
  await userEvent.click(
    within(dialog).getByRole("button", { name: "양식 내려받기" }),
  );
  expect(await within(dialog).findByRole("alert")).toHaveTextContent(
    "양식 조회 실패",
  );
  expect(template).toHaveBeenCalledTimes(1);
  expect(template).toHaveBeenCalledWith(data.month);
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});

it.each(["먼저 저장", "변경 버리기"])(
  "upload still waits for dirty changes: cancel then %s",
  async (action) => {
    await ready();
    await addItem();
    await userEvent.click(screen.getByTestId("financial-upload-button"));
    const confirmation = await screen.findByRole("dialog", {
      name: "저장 전 변경을 먼저 처리해 주세요",
    });
    expect(screen.getByTestId("financial-upload-button")).toBeDisabled();
    expect(screen.queryByTestId("b7-file-input")).not.toBeInTheDocument();
    await userEvent.click(
      within(confirmation).getByRole("button", { name: "취소" }),
    );
    expect(screen.getByTestId("financial-items-new-1-item-name")).toHaveValue(
      "임대료",
    );
    expect(screen.queryByTestId("b7-file-input")).not.toBeInTheDocument();
    expect(writes()).toHaveLength(0);
    await userEvent.click(screen.getByTestId("financial-upload-button"));
    await userEvent.click(
      within(await screen.findByRole("dialog")).getByRole("button", {
        name: action,
      }),
    );
    await screen.findByRole("dialog", { name: "엑셀 업로드 · 재무 항목" });
    expect(screen.getByTestId("b7-file-input")).toBeVisible();
    expect(writes()).toHaveLength(action === "먼저 저장" ? 1 : 0);
  },
);

it.each([
  { label: "editable", overrides: {} },
  {
    label: "closed",
    overrides: { editable: false, closeStatus: "CLOSE" as const },
  },
  {
    label: "past year",
    overrides: {
      editable: false,
      month: "2025-12",
      readOnlyReason: "지난 연도는 조회 전용입니다.",
    },
  },
])(
  "$label month always has one initially collapsed bottom history panel",
  async ({ overrides }) => {
    data = financialEditorFixture(overrides);
    await ready();
    const panel = screen.getByTestId("financial-history-panel");
    const toggle = within(panel).getByRole("button", { name: "변경 이력" });
    expect(screen.getAllByTestId("financial-history-toggle")).toHaveLength(1);
    expect(panel.closest(".sicost-title-action-group")).toBeNull();
    expect(panel).toBeVisible();
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(within(panel).queryByRole("table")).not.toBeInTheDocument();
    expect(
      fetcher.mock.calls.filter(([url]) =>
        String(url).startsWith("/api/financial-history?"),
      ),
    ).toHaveLength(0);
    if (!data.editable) {
      expect(
        screen.queryByTestId("financial-load-previous"),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTestId("financial-upload-button"),
      ).not.toBeInTheDocument();
    }
    await userEvent.click(toggle);
    expect(await within(panel).findByRole("table")).toBeVisible();
    await within(panel).findByText("해당 범위의 변경 이력이 없습니다.");
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(
      fetcher.mock.calls.some(
        ([url]) =>
          String(url) ===
          `/api/financial-history?month=${data.month}&scope=MONTH&page=1&size=50`,
      ),
    ).toBe(true);
    await userEvent.click(toggle);
    expect(within(panel).queryByRole("table")).not.toBeInTheDocument();
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(toggle);
    await within(panel).findByRole("table");
    await userEvent.click(within(panel).getByRole("button", { name: "닫기" }));
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(toggle).toHaveFocus();
    expect(panel).toBeVisible();
    await userEvent.click(screen.getByTestId("financial-tab-cash"));
    expect(panel).toBeVisible();
    const ids = Array.from(
      screen.getByTestId("financial-editor").querySelectorAll("[data-testid]"),
      (element) => element.getAttribute("data-testid"),
    );
    expect(new Set(ids).size).toBe(ids.length);
    expect(writes()).toHaveLength(0);
  },
);

it("loads server current month without preferences and renders empty states, shared tables and future disabled", async () => {
  await ready();
  expect(
    fetcher.mock.calls.some(([url]) => String(url) === "/api/financial-items"),
  ).toBe(true);
  expect(
    fetcher.mock.calls.some(([url]) => String(url).includes("preferences")),
  ).toBe(false);
  expect(screen.getByTestId("financial-month-2026-07")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  expect(screen.getByTestId("financial-month-2026-08")).toBeDisabled();
  expect(screen.getAllByText("등록된 항목이 없습니다.").length).toBeGreaterThan(
    0,
  );
  expect(screen.getByTestId("financial-save")).toBeEnabled();
  expect(screen.getByTestId("financial-adjustments-add")).toBeDisabled();
  await userEvent.click(screen.getByTestId("financial-save"));
  expect(await screen.findByText("바뀐 내용이 없습니다.")).toHaveClass(
    "sicost-toast",
  );
  expect(writes()).toHaveLength(0);
});
it("shows repeated identical notices outside the editor after the toast expires", async () => {
  await ready();
  vi.useFakeTimers();
  try {
    fireEvent.click(screen.getByTestId("financial-save"));
    const toast = screen.getByText("바뀐 내용이 없습니다.");
    expect(toast.closest(".sicost-toast-stack")?.parentElement).toBe(document.body);
    expect(screen.getByTestId("financial-editor")).not.toContainElement(toast);
    act(() => vi.advanceTimersByTime(3500));
    expect(screen.queryByText("바뀐 내용이 없습니다.")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("financial-save"));
    expect(screen.getByText("바뀐 내용이 없습니다.")).toHaveClass("sicost-tone-ok");
    act(() => vi.advanceTimersByTime(3500));
    expect(screen.queryByText("바뀐 내용이 없습니다.")).not.toBeInTheDocument();
    expect(writes()).toHaveLength(0);
  } finally {
    vi.useRealTimers();
  }
});
it("retains both tab drafts and sends exactly one atomic add/update/delete request without audit fields", async () => {
  await ready();
  await addItem();
  await userEvent.click(screen.getByTestId("financial-tab-cash"));
  await userEvent.click(screen.getByTestId("financial-loans-add"));
  await userEvent.type(screen.getByTestId("financial-loans-new-2-amount"), "2");
  await userEvent.click(screen.getByTestId("financial-tab-financial"));
  expect(screen.getByTestId("financial-items-new-1-item-name")).toHaveValue(
    "임대료",
  );
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  const body = JSON.parse(String(writes()[0][1]?.body));
  expect(body.expectedRevision).toBe("revision-current");
  expect(body.items.add[0]).toMatchObject({
    amount: "1000",
    itemName: "임대료",
    sgaItemCode: "RENT",
  });
  expect(body.loans.add[0]).toMatchObject({ amount: "2", direction: "IN" });
  expect(JSON.stringify(body)).not.toMatch(/regUserId|audit|autoFill|uploadId/);
  await screen.findByText("저장했습니다. 대여금도 함께 반영했습니다.");
});
it("cancel restores saved rows and does not regenerate automatic drafts or send writes", async () => {
  data = financialEditorFixture({
    automaticDrafts: [
      {
        sourceMonth: "2026-06",
        sourceItemSn: "2",
        finClass: "SGA",
        itemName: "반복",
        amount: "10",
        vatAmount: "0",
        ioDate: null,
        sgaItemCode: null,
        repeat: "Y",
        remark: null,
        autoFill: "Y",
        errors: [],
      },
    ],
  });
  await ready();
  expect(screen.getByTestId("financial-items-repeat-0-item-name")).toHaveValue(
    "반복",
  );
  await userEvent.click(screen.getByTestId("financial-cancel"));
  expect(screen.getByRole("dialog")).toHaveTextContent(
    "저장 안 한 내용을 모두 되돌릴까요?",
  );
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "되돌리기",
    }),
  );
  expect(
    screen.queryByTestId("financial-items-repeat-0-item-name"),
  ).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
  expect(await screen.findByText("변경을 취소했습니다.")).toHaveClass(
    "sicost-toast",
  );
});
it("read-only close locks inputs and hides upload, all add, deletion and save actions without inventing a role denial", async () => {
  data = financialEditorFixture({
    editable: false,
    closeStatus: "CLOSE",
    readOnlyReason: "마감된 월은 읽기 전용입니다.",
    items: [
      {
        itemSn: "1",
        finClass: "SGA",
        itemName: "경비",
        amount: "10",
        vatAmount: "0",
        ioDate: null,
        repeat: "N",
        sgaItemCode: null,
        remark: null,
      },
    ],
  });
  await ready();
  expect(screen.getByTestId("financial-read-only")).toHaveTextContent(
    "2026-07는 마감되었습니다.",
  );
  expect(screen.getByTestId("financial-items-1-amount")).toBeDisabled();
  expect(
    screen.queryByTestId("financial-upload-button"),
  ).not.toBeInTheDocument();
  expect(screen.queryByTestId("financial-items-add")).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("financial-items-1-delete"),
  ).not.toBeInTheDocument();
  expect(screen.queryByTestId("financial-save")).not.toBeInTheDocument();
  expect(
    fetcher.mock.calls.some(([url]) => String(url).includes("initialize")),
  ).toBe(false);
});
it("initial read failure exposes retry and no fabricated rows", async () => {
  const implementation = fetcher.getMockImplementation()!;
  let failed = false;
  fetcher.mockImplementation(async (url, options) => {
    if (!failed && String(url) === "/api/financial-items") {
      failed = true;
      return failure("조회 실패");
    }
    return implementation(url, options);
  });
  render(<FinancialItemsEditor />);
  expect(await screen.findByRole("alert")).toHaveTextContent("조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByTestId("financial-items-panel");
  expect(writes()).toHaveLength(0);
});
it("invalid decimals and missing required fields retain drafts without sending a write", async () => {
  await ready();
  await userEvent.click(screen.getByTestId("financial-items-add"));
  await userEvent.type(
    screen.getByTestId("financial-items-new-1-item-name"),
    "기타",
  );
  await userEvent.type(
    screen.getByTestId("financial-items-new-1-amount"),
    "1e3",
  );
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() =>
    expect(
      screen
        .getAllByRole("alert")
        .some((node) => node.textContent?.includes("원 정수")),
    ).toBe(true),
  );
  expect(writes()).toHaveLength(0);
  expect(screen.getByTestId("financial-items-new-1-amount")).toHaveValue("1e3");
});
it("hidden-tab server row errors reveal that tab and keep all inputs", async () => {
  await ready();
  await addItem();
  const implementation = fetcher.getMockImplementation()!;
  fetcher.mockImplementation(async (url, options) =>
    options?.method === "PUT"
      ? failure("대여금 오류", "VALIDATION_ERROR", {
          financial: {
            violations: [
              {
                tab: "CARD_TRANSFER_LOAN",
                collection: "loans",
                rowId: "row",
                field: "amount",
                message: "필수 금액",
              },
            ],
          },
        })
      : implementation(url, options),
  );
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() =>
    expect(screen.getByTestId("financial-tab-cash")).toHaveAttribute(
      "aria-pressed",
      "true",
    ),
  );
  expect(
    screen
      .getAllByRole("alert")
      .some((node) => node.textContent?.includes("finance-trace")),
  ).toBe(true);
  const toast = screen.getByText("대여금 오류", { selector: ".sicost-toast" });
  expect(toast).toHaveClass("sicost-toast", "sicost-tone-bad");
  expect(toast.closest(".sicost-toast-stack")?.parentElement).toBe(document.body);
  expect(screen.getByTestId("financial-editor")).not.toContainElement(toast);
  await userEvent.click(screen.getByTestId("financial-tab-financial"));
  expect(screen.getByTestId("financial-items-new-1-amount")).toHaveValue(
    "1,000 원",
  );
});
it("period movement offers stay, discard and save, with failed save leaving period unchanged", async () => {
  await ready();
  await addItem();
  await userEvent.click(screen.getByTestId("financial-month-2026-06"));
  expect(await screen.findByRole("dialog")).toHaveTextContent(
    "저장 안 한 내용이 있습니다",
  );
  await userEvent.click(screen.getByRole("button", { name: "계속 편집" }));
  expect(screen.getByTestId("financial-month-2026-07")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  const implementation = fetcher.getMockImplementation()!;
  fetcher.mockImplementation(async (url, options) =>
    options?.method === "PUT"
      ? failure("저장 실패")
      : implementation(url, options),
  );
  await userEvent.click(screen.getByTestId("financial-month-2026-06"));
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "저장 후 이동",
    }),
  );
  await within(screen.getByRole("dialog")).findByText("저장 실패");
  expect(screen.getByTestId("financial-month-2026-07")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await userEvent.click(screen.getByTestId("financial-navigation-discard"));
  await waitFor(() =>
    expect(screen.getByTestId("financial-month-2026-06")).toHaveAttribute(
      "aria-pressed",
      "true",
    ),
  );
});
it("shell navigation and tab-close events are deferred until a successful resolution", async () => {
  await ready();
  await addItem();
  const proceed = vi.fn();
  let allowed = true;
  act(() => {
    allowed = window.dispatchEvent(
      new CustomEvent("sicost:screen-navigation-request", {
        cancelable: true,
        detail: { screenId: "UI-10", label: "작업 탭 닫기", proceed },
      }),
    );
  });
  expect(allowed).toBe(false);
  expect(proceed).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("financial-navigation-discard"));
  await waitFor(() => expect(proceed).toHaveBeenCalledTimes(1));
  expect(writes()).toHaveLength(0);
});
it("native beforeunload uses the browser warning only for actual changes", async () => {
  await ready();
  const clean = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(clean);
  expect(clean.defaultPrevented).toBe(false);
  await addItem();
  const dirty = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(dirty);
  expect(dirty.defaultPrevented).toBe(true);
});
it("card same sum direct input uses MANUAL, and explicit AUTO removes the amount property", async () => {
  data = financialEditorFixture({
    cards: [
      { cardId: "K-01", cardName: "법인카드", last4: "0001", useYn: "Y" },
    ],
    cardPayments: [{ cardId: "K-01", amount: "10" }],
    cardMonth: {
      exists: true,
      cardSumAmount: "10",
      cashAmount: "10",
      editYn: "N",
      difference: "0",
    },
  });
  await ready();
  await userEvent.click(screen.getByTestId("financial-tab-cash"));
  fireEvent.change(screen.getByTestId("financial-card-cash-override"), {
    target: { value: "010" },
  });
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(String(writes()[0][1]?.body)).cards.cashOverride).toEqual({
    mode: "MANUAL",
    amount: "10",
  });
  await userEvent.click(screen.getByTestId("financial-card-auto"));
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(2));
  expect(JSON.parse(String(writes()[1][1]?.body)).cards.cashOverride).toEqual({
    mode: "AUTO",
  });
});
it("stopped cards hide zero rows by default, show existing positive and restore without changing historical amounts", async () => {
  data = financialEditorFixture({
    cards: [
      { cardId: "K-01", cardName: "숨은 카드", last4: "0001", useYn: "N" },
      { cardId: "K-02", cardName: "기존 양수", last4: "0002", useYn: "N" },
    ],
    cardPayments: [{ cardId: "K-02", amount: "10" }],
  });
  await ready();
  await userEvent.click(screen.getByTestId("financial-tab-cash"));
  expect(screen.queryByText("숨은 카드")).not.toBeInTheDocument();
  expect(screen.getByText("기존 양수")).toBeInTheDocument();
  await userEvent.click(screen.getByTestId("financial-show-stopped"));
  await userEvent.click(screen.getByTestId("financial-card-K-01-state"));
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(String(writes()[0][1]?.body)).cards).toMatchObject({
    states: [{ cardId: "K-01", useYn: "Y" }],
    payments: [],
  });
});
it("released adjustment target is visible but immutable except explicit delete", async () => {
  data = financialEditorFixture({
    adjustments: [
      {
        adjustmentId: "1",
        targetMonth: "2026-06",
        itemCode: "COST",
        amount: "-10",
        reason: "차액",
      },
    ],
  });
  await ready();
  expect(
    screen.getByTestId("financial-adjustments-1-target-month"),
  ).toBeDisabled();
  expect(screen.getByTestId("financial-adjustments-1-amount")).toBeDisabled();
  expect(screen.getByTestId("financial-adjustments-1-delete")).toBeEnabled();
  await userEvent.click(screen.getByTestId("financial-adjustments-1-delete"));
  expect(
    screen.getByTestId("financial-adjustments-1-amount"),
  ).toBeInTheDocument();
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }),
  );
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(
    JSON.parse(String(writes()[0][1]?.body)).adjustments.deleteIds,
  ).toEqual(["1"]);
});
it("refresh-only failure after commit never retries a write", async () => {
  await ready();
  await addItem();
  const implementation = fetcher.getMockImplementation()!;
  let committed = false;
  fetcher.mockImplementation(async (url, options) => {
    if (options?.method === "PUT") {
      committed = true;
      return implementation(url, options);
    }
    if (committed && String(url).startsWith("/api/financial-items?"))
      return failure("再조회 실패");
    return implementation(url, options);
  });
  await userEvent.click(screen.getByTestId("financial-save"));
  await screen.findByTestId("financial-refresh-only");
  await userEvent.click(screen.getByTestId("financial-refresh-only"));
  expect(writes()).toHaveLength(1);
});
it("confirmed tax rows have no editable schedule and status badges retain distinct colours", async () => {
  data = financialEditorFixture({
    taxPayments: [
      {
        year: "2026",
        payType: "MID",
        dueDate: "2026-07-01",
        paidDate: "2026-07-02",
        payAmount: "0",
        payYn: "Y",
        displayAmount: "0",
        automaticAmount: "0",
        manuallyEdited: true,
        status: "납부",
        confirmationRevision: "payment",
        audit: {
          regUserId: "admin",
          regDttm: "2026-07-01T09:00:00",
          modUserId: null,
          modDttm: null,
        },
      },
    ],
  });
  await ready();
  expect(
    screen.queryByTestId("financial-tax-2026-mid-amount"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("financial-tax-2026-mid-confirm"),
  ).not.toBeInTheDocument();
  expect(screen.getByText("납부")).toHaveClass("corporate-tax-ok");
});

it("reveals a required field on another tab, focuses it, and distinguishes zero from blank VAT", async () => {
  await ready();
  await userEvent.click(screen.getByTestId("financial-items-add"));
  await userEvent.click(screen.getByTestId("financial-tab-cash"));
  await userEvent.click(screen.getByTestId("financial-save"));
  const name = screen.getByTestId("financial-items-new-1-item-name");
  await waitFor(() => expect(name).toHaveFocus());
  expect(name).toHaveAttribute("aria-invalid", "true");
  expect(screen.getByTestId("financial-tab-financial")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  expect(
    screen.getAllByText("세부 항목을 입력해 주세요.").length,
  ).toBeGreaterThan(0);
  expect(writes()).toHaveLength(0);
  await userEvent.type(name, "직접 입력");
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() =>
    expect(screen.getByTestId("financial-items-new-1-amount")).toHaveFocus(),
  );
  expect(writes()).toHaveLength(0);
  fireEvent.change(screen.getByTestId("financial-items-new-1-amount"), {
    target: { value: "0" },
  });
  fireEvent.change(screen.getByTestId("financial-items-new-1-vat-amount"), {
    target: { value: "" },
  });
  expect(screen.getByTestId("financial-items-new-1-vat-amount")).toHaveValue(
    "0",
  );
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(String(writes()[0][1]?.body)).items.add[0]).toMatchObject({
    amount: "0",
    vatAmount: "0",
  });
});

it("automatically uses server preview for table totals without saving or adding a computed tax item", async () => {
  const implementation = fetcher.getMockImplementation()!;
  fetcher.mockImplementation(async (url, options) =>
    String(url) === "/api/financial-items/preview"
      ? response({
          allocation: data.allocation,
          cardMonth: data.cardMonth,
          automaticTaxAmounts: {},
          addedDrafts: [],
          contributions: { ...data.contributions, sgaAmount: "4321" },
        })
      : implementation(url, options),
  );
  await ready();
  await userEvent.selectOptions(
    screen.getByTestId("financial-display-unit"),
    "원",
  );
  await addItem();
  await waitFor(() =>
    expect(
      screen.getByTestId("financial-items-table-row-total-sga"),
    ).toHaveTextContent("4,321"),
  );
  expect(screen.queryByTestId("financial-calculate")).not.toBeInTheDocument();
  expect(
    screen.getByTestId("financial-items-table-row-tax-calculated"),
  ).not.toHaveTextContent("저장 전");
  expect(
    screen.queryByTestId("financial-items-tax-calculated-delete"),
  ).not.toBeInTheDocument();
  const preview = fetcher.mock.calls.find(
    ([url]) => String(url) === "/api/financial-items/preview",
  )!;
  expect(JSON.parse(String(preview[1]?.body)).items.add).toHaveLength(1);
  expect(writes()).toHaveLength(0);
  expect(screen.getByTestId("financial-tax-allocation-toggle")).toHaveAttribute(
    "aria-expanded",
    "false",
  );
  await userEvent.click(screen.getByTestId("financial-tax-allocation-toggle"));
  expect(screen.getByTestId("financial-tax-allocation-table")).toBeVisible();
});

it("previews previous month first, cancels without rows, and only applies after confirmation", async () => {
  const implementation = fetcher.getMockImplementation()!;
  fetcher.mockImplementation(async (url, options) =>
    String(url) === "/api/financial-items/preview"
      ? response({
          allocation: data.allocation,
          contributions: data.contributions,
          cardMonth: data.cardMonth,
          addedDrafts: [
            {
              sourceMonth: "2026-06",
              sourceItemSn: "9",
              finClass: "SGA",
              itemName: "통신비",
              amount: "99",
              vatAmount: "0",
              repeat: "Y",
              ioDate: "2026-07-05",
            },
          ],
        })
      : implementation(url, options),
  );
  await ready();
  await userEvent.click(screen.getByTestId("financial-load-previous"));
  const dialog = await screen.findByRole("dialog");
  expect(dialog).toHaveTextContent("2026-06 항목을 불러올까요?");
  expect(dialog).toHaveTextContent("1개(통신비)");
  expect(screen.queryByDisplayValue("통신비")).not.toBeInTheDocument();
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(screen.queryByDisplayValue("통신비")).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("financial-load-previous"));
  await userEvent.click(
    within(await screen.findByRole("dialog")).getByRole("button", {
      name: "불러오기",
    }),
  );
  expect(screen.getByDisplayValue("통신비")).toBeInTheDocument();
  expect(screen.getByText("1개 항목을 불러왔습니다.")).toHaveClass(
    "sicost-toast",
  );
  expect(writes()).toHaveLength(0);
});

it("shows no-previous notification and keeps the table unchanged", async () => {
  await ready();
  await userEvent.click(screen.getByTestId("financial-load-previous"));
  expect(await screen.findByText("불러올 항목이 없습니다.")).toHaveClass(
    "sicost-toast",
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});

it("carries a closed month into a new adjustment in the first editable month", async () => {
  data = financialEditorFixture({
    editable: false,
    closeStatus: "CLOSE",
    month: "2026-06",
    firstEditableMonth: "2026-07",
  });
  const implementation = fetcher.getMockImplementation()!;
  fetcher.mockImplementation(async (url, options) =>
    String(url) === "/api/financial-items?month=2026-07"
      ? response(
          financialEditorFixture({ adjustmentTargetMonths: ["2026-06"] }),
        )
      : implementation(url, options),
  );
  await ready();
  await userEvent.click(screen.getByTestId("financial-open-adjustment"));
  expect(
    await screen.findByTestId("financial-adjustments-new-1-target-month"),
  ).toHaveValue("2026-06");
  expect(screen.getByTestId("financial-month-2026-07")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  expect(screen.getByTestId("financial-tab-financial")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  expect(writes()).toHaveLength(0);
});

it("shows actual missing tax months and locks annual editing, retaining unknown current tax", async () => {
  data.allocation.months = [
    {
      month: "2026-01",
      closeStatus: "CLOSE",
      amount: null,
      verified: false,
      fixed: null,
      migrated: null,
    },
    {
      month: "2026-03",
      closeStatus: "CLOSE",
      amount: null,
      verified: false,
      fixed: null,
      migrated: null,
    },
  ];
  await ready();
  expect(screen.getByTestId("financial-tax-lock-reason")).toHaveTextContent(
    "1월·3월",
  );
  expect(screen.getByTestId("financial-estimated-tax")).toBeDisabled();
  expect(screen.getByTestId("financial-tax-current")).toHaveTextContent("–");
});

it("rejects a blank card amount with focus but accepts explicit zero", async () => {
  await ready();
  await userEvent.click(screen.getByTestId("financial-tab-cash"));
  await userEvent.click(screen.getByTestId("financial-card-add"));
  await userEvent.type(
    screen.getByTestId("financial-card-new-card-1-card-name"),
    "시험 카드",
  );
  await userEvent.type(
    screen.getByTestId("financial-card-new-card-1-last4"),
    "1234",
  );
  const amount = screen.getByTestId("financial-card-new-card-1-amount");
  await userEvent.clear(amount);
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(amount).toHaveFocus());
  expect(amount).toHaveAttribute("aria-invalid", "true");
  expect(writes()).toHaveLength(0);
  await userEvent.type(amount, "0");
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(String(writes()[0][1]?.body)).cards.add).toEqual([
    { clientKey: "new-card-1", cardName: "시험 카드", last4: "1234", amount: "0" },
  ]);
});
it("adds an editable card row from the header and validates its fields at save", async () => {
  await ready();
  await userEvent.click(screen.getByTestId("financial-tab-cash"));
  const panel = screen.getByTestId("financial-cards-panel");
  const table = screen.getByTestId("financial-cards-table");
  const add = within(panel).getByRole("button", { name: "카드 추가" });
  expect(add.closest("header")).not.toBeNull();
  expect(within(table).queryByRole("button", { name: "카드 추가" })).toBeNull();
  expect(screen.queryByRole("textbox", { name: "새 카드 이름" })).toBeNull();

  await userEvent.click(add);
  const row = screen.getByTestId("financial-cards-table-row-new-card-1");
  const name = within(row).getByRole("textbox", { name: "새 카드 이름" });
  const last4 = within(row).getByRole("textbox", { name: "새 카드 끝 4자리" });
  expect(name).toHaveFocus();
  expect(name).toHaveValue("");
  expect(last4).toHaveValue("");
  expect(row).toHaveTextContent("새 카드");
  expect(row).toHaveTextContent("저장 전");
  expect(screen.getByTestId("financial-tab-cash")).toHaveTextContent("저장 전");
  expect(writes()).toHaveLength(0);

  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(name).toHaveFocus());
  expect(name).toHaveAttribute("aria-invalid", "true");
  expect(
    screen.getAllByText("카드 이름을 입력해 주세요.").length,
  ).toBeGreaterThan(0);
  await userEvent.type(name, "영업 4팀 카드");
  await userEvent.type(last4, "12ab");
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(last4).toHaveFocus());
  expect(last4).toHaveAttribute("aria-invalid", "true");
  expect(
    screen.getAllByText("끝 4자리는 숫자 4개를 입력해 주세요.").length,
  ).toBeGreaterThan(0);
  fireEvent.change(last4, {
    target: { value: "1234" },
  });
  await userEvent.click(add);
  expect(screen.getByTestId("financial-card-new-card-2-card-name")).toHaveFocus();
  await userEvent.click(screen.getByTestId("financial-card-new-card-2-state"));
  expect(screen.queryByTestId("financial-cards-table-row-new-card-2")).toBeNull();
  expect(name).toHaveValue("영업 4팀 카드");
  expect(last4).toHaveValue("1234");
  expect(
    screen.getByTestId("financial-cards-table-row-card-total"),
  ).toHaveTextContent("1장");
  expect(
    screen.getByTestId("financial-transfers-table-row-total-transfers"),
  ).toHaveTextContent("0건");
  expect(writes()).toHaveLength(0);
});
